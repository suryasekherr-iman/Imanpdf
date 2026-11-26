package com.iman.pdf

import android.util.Log

/** Records an exception that is handled by falling back to a default value. */
fun logSwallowed(where: String, e: Throwable) {
    Log.w("Iman", "$where failed: ${e.javaClass.simpleName}: ${e.message}", e)
}
