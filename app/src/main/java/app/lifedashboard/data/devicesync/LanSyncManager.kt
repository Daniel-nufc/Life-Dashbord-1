package app.lifedashboard.data.devicesync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import app.lifedashboard.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LanState(val running: Boolean = false, val peerName: String? = null, val message: String? = null)

/**
 * Austausch über eine Verbindung (für WLAN und Tests):
 * Anfragendes Gerät schickt seinen Stand, das andere führt zusammen und antwortet mit seinem Stand.
 * Alles AES-GCM-verschlüsselt mit dem Abgleich-Passwort – ohne Passwort kann niemand im WLAN
 * mitlesen oder etwas einschleusen.
 */
/** Etwas, das verschlüsselte Schnappschüsse liefern und einarbeiten kann (App oder Test). */
interface SyncPeer {
    suspend fun encodedSnapshot(): ByteArray
    suspend fun mergeEncoded(bytes: ByteArray): Pair<MergeResult, String>
}

object LanProtocol {
    suspend fun serve(socket: Socket, repo: SyncPeer): Pair<MergeResult, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { socket.use { s ->
        s.soTimeout = 30_000
        val incoming = SyncFrame.read(s.getInputStream()) ?: error("Leere Anfrage")
        val result = repo.mergeEncoded(incoming)
        SyncFrame.write(s.getOutputStream(), repo.encodedSnapshot())
        result
    } }

    suspend fun request(host: InetAddress, port: Int, repo: SyncPeer): Pair<MergeResult, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 5_000)
            s.soTimeout = 30_000
            SyncFrame.write(s.getOutputStream(), repo.encodedSnapshot())
            val reply = SyncFrame.read(s.getInputStream()) ?: error("Keine Antwort")
            repo.mergeEncoded(reply)
        }
    }
}

/**
 * WLAN-Direktabgleich: Solange die App offen ist, macht sich das Gerät im Heimnetz sichtbar
 * („_lifedash._tcp“) und sucht das andere. Gefunden → sofort abgleichen, nach jeder Änderung binnen Sekunden, sonst alle 30 Sekunden.
 * Kein Internet, keine Cloud – nur im selben WLAN.
 */
@Singleton
class LanSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DeviceSyncRepository,
    private val db: app.lifedashboard.data.db.AppDatabase,
    private val engine: app.lifedashboard.data.planning.PlanningEngine,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LanState())
    val state: StateFlow<LanState> = _state.asStateFlow()

    private val nsd by lazy { context.getSystemService(NsdManager::class.java) }
    private var server: ServerSocket? = null
    private var jobs = mutableListOf<Job>()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var peer: Pair<InetAddress, Int>? = null
    private val exchangeLock = Mutex()

    // ------------------------------------------------------------------ sofort nach jeder Änderung

    private var changeJob: Job? = null
    private val changeObserver = object : androidx.room.InvalidationTracker.Observer(SyncSchema.tables.map { it.name }.toTypedArray()) {
        override fun onInvalidated(tables: Set<String>) {
            // Änderungen, die gerade vom anderen Gerät kamen, nicht zurückschicken
            if (System.currentTimeMillis() - repo.lastMergeAt < 4_000) return
            changeJob?.cancel()
            changeJob = scope.launch(Dispatchers.IO) {
                delay(2_000) // kurz sammeln (z. B. Klausur + Themen)
                exchangeNow()
                if (repo.settings.first().fileUri != null) SyncWorker.runNow(context, delaySeconds = 8)
            }
        }
    }
    private var observing = false

    fun start() {
        if (_state.value.running) return
        scope.launch(Dispatchers.IO) {
            val s = repo.settings.first()
            if (!s.hasPassword) return@launch
            if (!observing) {
                runCatching { db.invalidationTracker.addObserver(changeObserver) }.onSuccess { observing = true }
            }
            if (!s.lanEnabled) return@launch
            runCatching { startInternal(repo.deviceId()) }.onFailure { e ->
                _state.value = LanState(message = "WLAN-Abgleich nicht möglich: ${e.message}")
            }
        }
    }

    private fun startInternal(myId: String) {
        _state.value = LanState(running = true, message = "Suche anderes Gerät im WLAN …")
        multicast = context.getSystemService(WifiManager::class.java)?.createMulticastLock("lifedash-sync")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val socket = ServerSocket(0)
        server = socket
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch {
                    runCatching { LanProtocol.serve(client, repo) }
                        .onSuccess { (r, from) -> done(r, from) }
                        .onFailure { e -> report("Abgleich mit Gerät im WLAN fehlgeschlagen: ${friendly(e)}") }
                }
            }
        }

        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = report("Im WLAN nicht sichtbar (Fehler $code)")
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = reg
        nsd.registerService(
            NsdServiceInfo().apply {
                serviceName = "$PREFIX$myId"
                serviceType = TYPE
                port = socket.localPort
            },
            NsdManager.PROTOCOL_DNS_SD, reg,
        )

        val disc = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = report("Suche im WLAN fehlgeschlagen (Fehler $code)")
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                val name = info.serviceName ?: return
                if (!name.startsWith(PREFIX)) return
                val otherId = name.removePrefix(PREFIX)
                if (otherId == myId) return
                @Suppress("DEPRECATION")
                nsd.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo, code: Int) = Unit
                        override fun onServiceResolved(i: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = i.host ?: return
                            peer = host to i.port
                            // Nur eines der beiden Geräte fragt an (das mit der kleineren ID) – sonst doppelt
                            if (myId < otherId) startPeerLoop()
                        }
                    },
                )
            }
        }
        discovery = disc
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, disc)
    }

    private var peerLoop: Job? = null

    private fun startPeerLoop() {
        if (peerLoop?.isActive == true) return
        peerLoop = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value.running) {
                exchangeNow()
                delay(30_000)
            }
        }.also { jobs += it }
    }

    /** Sofort mit dem gefundenen Gerät abgleichen (Knopf „Jetzt abgleichen“). */
    suspend fun exchangeNow(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) { exchangeOnIo() }

    // Netzwerk nie im Bildschirm-Thread (sonst NetworkOnMainThreadException)
    private suspend fun exchangeOnIo(): Boolean {
        val (host, port) = peer ?: return false
        exchangeLock.withLock {
            runCatching { LanProtocol.request(host, port, repo) }
                .onSuccess { (r, from) -> done(r, from) }
                .onFailure { e -> report("Abgleich im WLAN fehlgeschlagen: ${friendly(e)}") }
        }
        return true
    }

    private suspend fun done(r: MergeResult, from: String) {
        val msg = DeviceSyncRepository.describe(r, from)
        _state.value = _state.value.copy(peerName = from, message = msg)
        repo.recordLan(msg)
        if (r.changes > 0) {
            runCatching { engine.planDays(LocalDate.now(), 14) }
            app.lifedashboard.widget.updateWidgets(context)
        }
    }

    private fun report(msg: String) {
        _state.value = _state.value.copy(message = msg)
    }

    private fun friendly(e: Throwable) = when (e) {
        is app.lifedashboard.core.security.PasswordCrypto.WrongPasswordException -> "anderes Passwort auf dem anderen Gerät"
        is SyncVersionException -> "unterschiedliche App-Versionen"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun stop() {
        if (observing) {
            runCatching { db.invalidationTracker.removeObserver(changeObserver) }
            observing = false
        }
        changeJob = null
        if (!_state.value.running) return
        runCatching { registration?.let(nsd::unregisterService) }
        runCatching { discovery?.let(nsd::stopServiceDiscovery) }
        registration = null
        discovery = null
        runCatching { server?.close() }
        server = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        peerLoop = null
        peer = null
        runCatching { multicast?.release() }
        multicast = null
        _state.value = _state.value.copy(running = false)
    }

    fun restart() {
        stop()
        start()
    }

    private companion object {
        const val TYPE = "_lifedash._tcp."
        const val PREFIX = "LD-"
    }
}
