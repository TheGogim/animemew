package com.mew.animemew.data.sync

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

// =========================================================
//  CryptoHelper — cifrado AES-256-GCM.
//
//  La clave se deriva con PBKDF2(password, salt, 100k iter).
//  El salt lo genera el server al registrar y se envía al
//  cliente. El password NUNCA sale del dispositivo.
//
//  Formato del ciphertext: base64( iv[12] + ciphertext + tag[16] )
// =========================================================

object CryptoHelper {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12       // bytes (recomendado para GCM)
    private const val GCM_TAG_LENGTH_BITS = 128 // bits

    /** Deriva una clave AES-256 a partir de password + salt (hex). */
    fun deriveKey(password: String, saltHex: String): SecretKey {
        val salt = saltHex.hexDecode()
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /** Cifra plaintext y devuelve base64( iv + ciphertext + tag ). */
    fun encrypt(plaintext: ByteArray, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Descifra base64( iv + ciphertext + tag ) → plaintext. */
    fun decrypt(b64: String, key: SecretKey): ByteArray {
        val combined = Base64.decode(b64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun String.hexDecode(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
