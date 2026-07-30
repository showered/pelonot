package com.pelonot.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.RideIntent
import com.pelonot.ui.theme.PelonotTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Hosts the floating ride HUD in a `WindowManager` overlay so it can sit on
 * top of a third-party video app.
 *
 * Note the Compose content is themed with `darkTheme = true` and dynamic
 * colour off, regardless of the app's own setting: the HUD is translucent over
 * arbitrary video, and a light scheme is unreadable there.
 */
class HudOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

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
        x = INITIAL_X
        y = INITIAL_Y
    }

    val isShowing: Boolean get() = composeView != null

    /**
     * Adds the overlay. No-op when already visible or when the
     * "display over other apps" permission has not been granted.
     */
    fun show(
        ftp: Double,
        intent: RideIntent = RideIntent.DEFAULT,
        elapsedSecondsFlow: StateFlow<Int>? = null,
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        if (composeView != null) return

        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot show HUD")
            return
        }

        val owner = OverlayLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            setContent {
                val sensorRepository = ServiceLocator.sensorRepository
                val reading by sensorRepository.sensorReading.collectAsStateWithLifecycle()
                val elapsedSeconds by (elapsedSecondsFlow ?: ZERO_ELAPSED)
                    .collectAsStateWithLifecycle()

                PelonotTheme(darkTheme = true, useDynamicColor = false) {
                    HudOverlayMain(
                        cadence = reading.cadenceRpm,
                        resistance = reading.resistancePercent,
                        power = reading.powerWatts,
                        heartRate = reading.heartRateBpm,
                        elapsedSeconds = elapsedSeconds,
                        ftp = ftp,
                        intent = intent,
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
                        onDrag = ::moveBy
                    )
                }
            }
        }

        try {
            windowManager.addView(view, layoutParams)
            composeView = view
            lifecycleOwner = owner
            Log.d(TAG, "HUD overlay attached")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach HUD overlay", e)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val view = composeView ?: return
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    fun hide() {
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "Overlay was already detached", it) }
            // Disposing the composition and retiring the lifecycle releases the
            // ViewModelStore. The previous version removed the view but left the
            // lifecycle at RESUMED forever, so every ride leaked its composition.
            view.disposeComposition()
        }
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView = null
        lifecycleOwner = null
    }

    /**
     * Minimal owner implementation. A `WindowManager` overlay has no Activity
     * behind it, so Compose needs a lifecycle, a saved-state registry and a
     * ViewModel store supplied by hand.
     */
    private class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        override val viewModelStore = ViewModelStore()

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
            if (event == Lifecycle.Event.ON_DESTROY) viewModelStore.clear()
        }

        fun performRestore(savedState: Bundle?) =
            savedStateRegistryController.performRestore(savedState)
    }

    private companion object {
        const val TAG = "HudOverlayManager"
        const val INITIAL_X = 200
        const val INITIAL_Y = 200

        val ZERO_ELAPSED: StateFlow<Int> =
            kotlinx.coroutines.flow.MutableStateFlow(0)
    }
}
