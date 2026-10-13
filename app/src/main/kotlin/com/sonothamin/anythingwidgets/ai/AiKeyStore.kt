package com.sonothamin.anythingwidgets.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saved AI keys, in priority order (first = tried first). Secrets are encrypted with an
 * Android Keystore key that never leaves the device, so they are unreadable in a backup
 * or a copied prefs file. After a restore onto a new device the key simply reads back empty.
 */
class AiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)

    @Serializable
    private data class Stored(val key: AiKey, val secret: String)

    fun load(): List<AiKey> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val stored = runCatching { json.decodeFromString<List<Stored>>(raw) }.getOrElse { return emptyList() }
        return stored.map { it.key.copy(apiKey = KeyCrypto.decrypt(it.secret).orEmpty()) }
    }

    fun save(keys: List<AiKey>) {
        val stored = keys.map { Stored(it.copy(apiKey = ""), KeyCrypto.encrypt(it.apiKey)) }
        prefs.edit().putString(KEY_LIST, json.encodeToString(stored)).apply()
    }

    fun hasUsableKey(): Boolean = load().any { it.usable }

    companion object {
        private const val KEY_LIST = "keys"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private object KeyCrypto {
    private const val ALIAS = "anythingwidgets_ai_keys"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }
}
