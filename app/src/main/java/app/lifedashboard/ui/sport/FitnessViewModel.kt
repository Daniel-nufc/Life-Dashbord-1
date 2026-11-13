package app.lifedashboard.ui.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutSetEntity
import app.lifedashboard.data.planning.PlanningEngine
import app.lifedashboard.data.repository.PlannedTraining
import app.lifedashboard.data.repository.RoutineRepository
import app.lifedashboard.data.repository.SportRepository
import app.lifedashboard.data.repository.TrainingOverview
import app.lifedashboard.data.repository.TrainingRepository
import app.lifedashboard.data.repository.WorkoutWithSets
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.data.settings.TrainingSettings
import app.lifedashboard.data.sync.FitnessSnapshot
import app.lifedashboard.data.sync.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FitnessViewModel @Inject constructor(
    routines: RoutineRepository,
    private val sport: SportRepository,
    private val health: HealthRepository,
    private val training: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: PlanningEngine,
    progressRepository: app.lifedashboard.data.repository.ProgressRepository,
    private val strava: app.lifedashboard.data.sync.StravaRepository,
) : ViewModel() {

    data class StravaState(val connected: Boolean, val hasClient: Boolean, val lastSync: Long?, val busy: Boolean = false)

    private val _stravaBusy = MutableStateFlow(false)
    val stravaState: StateFlow<StravaState> = kotlinx.coroutines.flow.combine(settingsRepository.settings.map { it.stravaConnected }, _stravaBusy) { c, busy ->
        StravaState(c, strava.hasClient, strava.lastSyncMillis, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StravaState(false, false, null))

    /** Zugangsdaten speichern und den Link zum Erlauben liefern (öffnet Strava) */
    fun stravaAuthorize(clientId: String, clientSecret: String): android.net.Uri {
        if (clientId.isNotBlank() && clientSecret.isNotBlank()) strava.saveClient(clientId, clientSecret)
        return strava.authorizeUri()
    }

    /** Falls die Rückkehr nicht klappt: Link aus dem Browser einfügen */
    fun stravaPaste(link: String) = stravaRun { strava.handleRedirect(link).getOrThrow().let { "Strava verbunden – $it Trainings übernommen" } }
    fun stravaSync() = stravaRun { strava.sync(full = false).let { if (it > 0) "$it neue Trainings von Strava" else "Keine neuen Trainings auf Strava" } }
    fun stravaDisconnect() = stravaRun { strava.disconnect(); "Strava getrennt" }

    private fun stravaRun(block: suspend () -> String) {
        viewModelScope.launch {
            _stravaBusy.value = true
            _message.value = runCatching { block() }.getOrElse { "Strava: ${it.message}" }
            _stravaBusy.value = false
            loadOverview()
        }
    }


    /** Level, Wochenziele und Ironman-Weg */
    val progress: StateFlow<app.lifedashboard.planner.ProgressState?> = progressRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)


    enum class HealthState { UNKNOWN, NOT_AVAILABLE, NEEDS_PERMISSION, PARTIAL, READY, ERROR }

    private val _overview = MutableStateFlow<TrainingOverview?>(null)
    val overview: StateFlow<TrainingOverview?> = _overview.asStateFlow()
    private val _snapshot = MutableStateFlow<FitnessSnapshot?>(null)
    val snapshot: StateFlow<FitnessSnapshot?> = _snapshot.asStateFlow()
    private val _healthState = MutableStateFlow(HealthState.UNKNOWN)
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val settings: StateFlow<TrainingSettings> = settingsRepository.settings.map { it.training }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingSettings())
    val routines: StateFlow<List<RoutineBlockEntity>> = routines.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts: StateFlow<List<WorkoutWithSets>> = sport.workouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val healthPermissions: Set<String> get() = health.permissions

    fun refresh() {
        viewModelScope.launch {
            runCatching { training.syncFromHealth() }
            loadOverview()
            _healthState.value = try {
                when {
                    !health.isAvailable() -> HealthState.NOT_AVAILABLE
                    else -> {
                        val missing = health.missingPermissions()
                        when {
                            !health.hasBasicPermissions() -> HealthState.NEEDS_PERMISSION
                            else -> {
                                _snapshot.value = health.snapshot(7)
                                if (missing.isEmpty()) HealthState.READY else HealthState.PARTIAL
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                HealthState.ERROR
            }
        }
    }

    private suspend fun loadOverview() {
        runCatching { training.overview() }
            .onSuccess { _overview.value = it }
            .onFailure { _message.value = "Sport-Zeitfenster konnten nicht geladen werden (${it.javaClass.simpleName}: ${it.message})" }
    }

    fun syncNow() {
        viewModelScope.launch {
            val n = runCatching { training.syncFromHealth() }.getOrDefault(0)
            _message.value = if (n > 0) "$n Training(s) aus Health Connect übernommen ✓" else "Keine neuen Trainings in Health Connect gefunden."
            refresh()
        }
    }

    private fun windowAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
            loadOverview()
            runCatching { engine.replan(LocalDate.now()) }
        }
    }

    /** Zeitfenster genutzt – optional mit dem, was du gemacht hast. */
    fun done(t: PlannedTraining, what: WorkoutEntity?) = windowAction { training.markDone(t.session, what) }
    fun skip(t: PlannedTraining) = windowAction { training.skip(t.session) }
    fun undo(t: PlannedTraining) = windowAction { training.undo(t.session) }
    fun shorten(t: PlannedTraining) = windowAction { training.shorten(t.session) }

    fun saveSettings(t: TrainingSettings) = windowAction {
        settingsRepository.setTraining(t)
        engine.planDays(LocalDate.now(), 14)
    }

    fun save(workout: WorkoutEntity, sets: List<WorkoutSetEntity>) {
        viewModelScope.launch { sport.save(workout, sets); _overview.value = runCatching { training.overview() }.getOrNull() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { sport.delete(id); _overview.value = runCatching { training.overview() }.getOrNull() }
    }
}
