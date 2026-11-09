package app.lifedashboard.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verschlüsselung mit Passwort (für Backup und Geräte-Abgleich).
 *
 * Format: „LDB1“ + Salt (16) + IV (12) + AES-256-GCM( gzip( Daten ) ).
 * Schlüssel per PBKDF2-HMAC-SHA256 (210.000 Runden). GCM erkennt jede Veränderung
 * und jedes falsche Passwort – manipulierte Daten werden nie eingelesen.
 */
object PasswordCrypto {
    class WrongPasswordException : Exception("Falsches Passwort oder beschädigte Datei.")

    private val MAGIC = "LDB1".toByteArray()
    private const val ROUNDS = 210_000

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(zipped)
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size < 32 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) throw WrongPasswordException()
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val zipped = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                doFinal(data.copyOfRange(32, data.size))
            }
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
        return GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }
    }
}
