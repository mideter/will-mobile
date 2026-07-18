package com.will.app

import android.content.Context
import java.security.SecureRandom

/**
 * Как `will::DeviceTokenStore`: load-or-create opaque hex device token
 * (`DeviceToken::generate` — 32 hex chars from two uint64).
 */
object DeviceTokenStore {

    private const val PREFS_NAME = "will_device_token"
    private const val KEY_TOKEN = "token"

    /** `DeviceToken::MinLength` / `MaxLength` */
    const val MIN_LENGTH = 32
    const val MAX_LENGTH = 128

    fun loadOrCreate(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (existing != null && isValid(existing)) {
            return existing
        }
        val generated = generate()
        prefs.edit().putString(KEY_TOKEN, generated).apply()
        return generated
    }

    fun isValid(token: String): Boolean {
        if (token.length !in MIN_LENGTH..MAX_LENGTH) return false
        return token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    /** Как `DeviceToken::generate`: `{:016x}{:016x}`. */
    fun generate(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return buildString(32) {
            for (b in bytes) {
                append("%02x".format(b.toInt() and 0xFF))
            }
        }
    }
}
