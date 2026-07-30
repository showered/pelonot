package com.pelonot.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.ui.theme.PelonotTheme

/**
 * Manages the floating HUD overlay using WindowManager.
 */
class HudOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = 200  // Moved from 100 to better center overlay
        y = 200  // Moved from 100 to better center overlay
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onStop: () -> Unit = {},
        ftp: Double = 200.0,
        elapsedSecondsFlow: kotlinx.coroutines.flow.StateFlow<Int>? = null
    ) {
        Log.d("HudOverlayManager", "show() called - composeView: $composeView")
        if (composeView != null) {
            Log.d("HudOverlayManager", "Overlay already showing - returning early")
            return
        }

        // Check if we have overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Log.e("HudOverlayManager", "SYSTEM_ALERT_WINDOW permission not granted - cannot show overlay!")
                return
            }
        }

        try {
            composeView = ComposeView(context).apply {
                Log.d("HudOverlayManager", "ComposeView created, attaching lifecycle")
                // Attach lifecycle owners so Compose works correctly in a Service/Window
                val lifecycleOwner = OverlayLifecycleOwner()
                lifecycleOwner.performRestore(null)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                })

                Log.d("HudOverlayManager", "Setting content with sensor data collection")
                setContent {
                    Log.d("HudOverlayManager", "Content being set, collecting sensor data")
                    val sensorRepository = SensorRepository.getInstance(context)
                    val reading by sensorRepository.sensorReading.collectAsState()

                    // Collect elapsed seconds from the provided flow, or default to 0
                    val elapsedSeconds by if (elapsedSecondsFlow != null) {
                        elapsedSecondsFlow.collectAsState()
                    } else {
                        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                    }

                    Log.d("HudOverlayManager", "Current reading: cadence=${reading.cadenceRpm}, resistance=${reading.resistancePercent}, power=${reading.powerWatts}, elapsed=$elapsedSeconds")

                    PelonotTheme {
                        HudOverlayMain(
                            cadence = reading.cadenceRpm,
                            resistance = reading.resistancePercent,
                            power = reading.powerWatts,
                            heartRate = reading.heartRateBpm,
                            elapsedSeconds = elapsedSeconds,
                            ftp = ftp,
                            onPause = onPause,
                            onResume = onResume,
                            onStop = onStop,
                            onDrag = { dx, dy ->
                                Log.d("HudOverlayManager", "Drag detected: dx=$dx, dy=$dy")
                                this@HudOverlayManager.layoutParams.x += dx.toInt()
                                this@HudOverlayManager.layoutParams.y += dy.toInt()
                                windowManager.updateViewLayout(composeView, layoutParams)
                            }
                        )
                    }
                }
            }

            Log.d("HudOverlayManager", "Adding view to WindowManager at x=${layoutParams.x}, y=${layoutParams.y}")
            windowManager.addView(composeView, layoutParams)
            Log.d("HudOverlayManager", "Overlay view added to WindowManager successfully!")
        } catch (e: Exception) {
            Log.e("HudOverlayManager", "Failed to show overlay: ${e.message}", e)
            composeView = null
        }
    }

    fun hide() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }

    /**
     * Minimal implementation of SavedStateRegistryOwner and LifecycleOwner for Overlay.
     */
    private class OverlayLifecycleOwner : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}
