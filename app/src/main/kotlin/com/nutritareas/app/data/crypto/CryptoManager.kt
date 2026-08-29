package com.nutritareas.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (the Anthropic API key) at rest with a non-exportable AES-256-GCM key
 * held in the Android Keystore. androidx.security:security-crypto (EncryptedSharedPreferences)
 * never left alpha and was deprecated, so this talks to the platform Keystore directly instead.
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Encrypts [plainText] into a single Base64 string carrying the IV followed by the ciphertext. */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Reverses [encrypt]. Returns null instead of throwing if [payload] can't be decrypted. */
    fun decrypt(payload: String): String? = try {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        check(combined.size > IV_SIZE_BYTES)
        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherBytes = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    } catch (e: Exception) {
        // Key lost (app data cleared / keystore reset) or payload corrupted: treat as "no secret
        // stored" so callers fall back to asking the user to re-enter it, rather than crashing.
        null
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nutri_tareas_settings_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_SIZE_BYTES = 12
    }
}
