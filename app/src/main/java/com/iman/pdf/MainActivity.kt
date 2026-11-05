package com.iman.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iman.pdf.data.AppLock
import com.iman.pdf.data.UserPrefs
import com.iman.pdf.ui.AppSplash
import com.iman.pdf.ui.LockScreen
import com.iman.pdf.ui.MainScreen
import com.iman.pdf.ui.theme.ImanTheme
import com.iman.pdf.ui.theme.ThemeMode
import kotlinx.coroutines.delay

private const val RELOCK_AFTER_MS = 30_000L

class MainActivity : FragmentActivity() {
    /** Deletes temp files older than 24 hours that earlier sessions left in the cache. */
    private fun cleanOldCache() {
        val dir = cacheDir
        Thread {
            val limit = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            try {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.lastModified() < limit) {
                        f.delete()
                    }
                }
                java.io.File(dir, "scans").listFiles()?.forEach { f ->
                    if (f.isFile && f.lastModified() < limit) {
                        f.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        AppLock.applySecure(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanOldCache()
        val prefs = UserPrefs(applicationContext)
        val incoming: Uri? =
            if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        val showSplashFirst = savedInstanceState == null && incoming == null

        setContent {
            val themeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val mode = try {
                ThemeMode.valueOf(themeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
            ImanTheme(mode = mode) {
                val context = LocalContext.current
                var splash by rememberSaveable { mutableStateOf(showSplashFirst) }
                var locked by rememberSaveable { mutableStateOf(AppLock.isEnabled(context)) }

                LaunchedEffect(Unit) {
                    if (splash) {
                        delay(600)
                        splash = false
                    }
                }

                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    var stoppedAt = 0L
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                            Lifecycle.Event.ON_START -> {
                                val away = stoppedAt != 0L &&
                                    SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS
                                if (away && AppLock.isEnabled(context)) {
                                    locked = true
                                }
                                stoppedAt = 0L
                            }
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (splash) {
                    AppSplash()
                } else {
                    Box {
                        MainScreen(incomingUri = incoming)
                        if (locked) {
                            LockScreen(onUnlocked = { locked = false })
                        }
                    }
                }
            }
        }
    }
}
