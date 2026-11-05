package com.iman.pdf.data

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the app-lock settings. The PIN is saved only as a salted SHA-256 hash. */
object AppLock {

    private const val PREFS = "iman_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_BIO = "biometric"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(salt, pin))
            .apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val saltText = p.getString(KEY_SALT, null) ?: return false
        val stored = p.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        return MessageDigest.isEqual(
            hash(salt, pin).toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8)
        )
    }

    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun biometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIO, false)

    fun setBiometric(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIO, on).apply()
    }

    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
}
