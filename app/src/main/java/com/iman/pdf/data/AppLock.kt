package com.iman.pdf.data

import android.app.Activity
import android.content.Context
import android.view.WindowManager
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
    private const val KEY_FAILS = "fails"
    private const val KEY_LOCKED_UNTIL = "locked_until"
    private const val MAX_FAILS = 5
    private const val LOCK_MS = 30_000L

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

    /** Milliseconds left before another PIN try is allowed (0 = allowed now). Survives app restarts. */
    fun lockRemainingMs(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCKED_UNTIL, 0L)
        val left = until - System.currentTimeMillis()
        // A clock set far back must not give a huge wait.
        return if (left in 1..LOCK_MS) left else 0L
    }

    /** Saves a wrong try. After 5 wrong tries the lock starts. */
    fun registerFail(context: Context) {
        val p = prefs(context)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_FAILS) {
            p.edit().putInt(KEY_FAILS, 0)
                .putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCK_MS).apply()
        } else {
            p.edit().putInt(KEY_FAILS, fails).apply()
        }
    }

    fun clearFails(context: Context) {
        prefs(context).edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
    }

    /** Hides app content in Recent apps and blocks screenshots while the lock is ON. */
    fun applySecure(activity: Activity) {
        if (isEnabled(activity)) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
