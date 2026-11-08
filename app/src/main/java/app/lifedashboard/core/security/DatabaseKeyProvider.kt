package app.lifedashboard.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert das Passwort für die verschlüsselte Datenbank.
 *
 * Ablauf: Beim ersten Start wird ein zufälliges 256-Bit-Passwort erzeugt. Dieses wird mit einem
 * Schlüssel aus dem Android Keystore (Hardware-geschützt, verlässt nie das Gerät) verschlüsselt
 * und in einer Datei abgelegt, die nicht gesichert wird. Ohne dieses Gerät ist die Datenbank
 * nicht lesbar.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyFile: File get() = File(context.noBackupFilesDir, "db_key.bin")

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val file = keyFile
        if (file.exists()) return decrypt(file.readBytes())
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.writeBytes(encrypt(passphrase))
        return passphrase
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val ivSize = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivSize)
        val encrypted = data.copyOfRange(1 + ivSize, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lifedashboard_db_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
