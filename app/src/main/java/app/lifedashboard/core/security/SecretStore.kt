package app.lifedashboard.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert Geheimnisse (z. B. Backup-Passwort, später Etsy-Token) verschlüsselt mit einem
 * Schlüssel aus dem Android Keystore. Nichts davon landet im Code oder in Git.
 */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.noBackupFilesDir, "secrets").apply { mkdirs() }

    fun put(name: String, value: String?) {
        val file = File(dir, name)
        if (value == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        file.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(value.toByteArray()))
    }

    fun get(name: String): String? {
        val file = File(dir, name)
        if (!file.exists()) return null
        return runCatching {
            val data = file.readBytes()
            val ivSize = data[0].toInt()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(1, 1 + ivSize)))
            String(cipher.doFinal(data.copyOfRange(1 + ivSize, data.size)))
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "lifedashboard_secrets"
    }
}
