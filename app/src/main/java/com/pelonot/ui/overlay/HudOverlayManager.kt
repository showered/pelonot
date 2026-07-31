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
import com.pelonot.data.service.RideSnapshot
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.ui.theme.PelonotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hosts the floating ride HUD in a `WindowManager` overlay so it can sit on
 * top of whatever the rider is actually watching.
 *
 * The window is **full width and docked to one screen edge**, not a floating
 * card. That is the whole design: the rider has a film on, and the middle of
 * the screen has to stay clear. It also means the overlay only intercepts
 * touches within its own strip, so the video underneath stays fully usable.
 *
 * The Compose content is themed `darkTheme = true` with dynamic colour off
 * regardless of the app's own setting — the HUD is translucent over arbitrary
 * video, and a light scheme is unreadable there.
 */
class HudOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private val _dock = MutableStateFlow(HudDock.DEFAULT)
    val dock: StateFlow<HudDock> = _dock.asStateFlow()

    private val _collapsed = MutableStateFlow(false)
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    /** Notified when the rider drags the HUD to the other edge, so it persists. */
    var onDockChanged: ((HudDock) -> Unit)? = null

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        // Deliberately *not* LAYOUT_IN_SCREEN or LAYOUT_NO_LIMITS: those put
        // the strip underneath the status bar, where the class timeline ends up
        // behind the clock. Staying inside the decor area means the HUD docks
        // against whatever the app below it is already respecting.
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = gravityFor(HudDock.DEFAULT)
    }

    val isShowing: Boolean get() = composeView != null

    /**
     * Raises the overlay. No-op when already visible or when "display over
     * other apps" has not been granted — the caller is expected to have asked
     * for it, and a silent failure here is preferable to crashing a ride.
     */
    fun show(
        snapshotFlow: StateFlow<RideSnapshot>,
        coachStyleFlow: StateFlow<CoachStyle>,
        dock: HudDock = HudDock.DEFAULT,
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        if (composeView != null) return

        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot show HUD")
            return
        }

        _dock.value = dock
        layoutParams.gravity = gravityFor(dock)

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
                val snapshot by snapshotFlow.collectAsStateWithLifecycle()
                val coachStyle by coachStyleFlow.collectAsStateWithLifecycle()
                val currentDock by _dock.collectAsStateWithLifecycle()
                val isCollapsed by _collapsed.collectAsStateWithLifecycle()

                // Telemetry is collected separately from the ride snapshot: it
                // changes several times a second and the snapshot does not.
                val reading by ServiceLocator.sensorRepository.sensorReading
                    .collectAsStateWithLifecycle()

                PelonotTheme(darkTheme = true, useDynamicColor = false) {
                    HudOverlayMain(
                        snapshot = snapshot,
                        reading = reading,
                        dock = currentDock,
                        collapsed = isCollapsed,
                        coachStyle = coachStyle,
                        onToggleCollapsed = { _collapsed.value = !_collapsed.value },
                        onDockChange = ::moveTo,
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop
                    )
                }
            }
        }

        try {
            windowManager.addView(view, layoutParams)
            composeView = view
            lifecycleOwner = owner
            Log.d(TAG, "HUD overlay attached, docked $dock")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach HUD overlay", e)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    /** Snaps the HUD to the given screen edge. */
    fun moveTo(dock: HudDock) {
        if (_dock.value == dock) return
        _dock.value = dock
        layoutParams.gravity = gravityFor(dock)
        composeView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, layoutParams) }
                .onFailure { Log.w(TAG, "Could not re-dock the HUD", it) }
        }
        onDockChanged?.invoke(dock)
    }

    fun hide() {
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "Overlay was already detached", it) }
            // Disposing the composition and retiring the lifecycle releases the
            // ViewModelStore. An earlier version removed the view but left the
            // lifecycle at RESUMED forever, so every ride leaked its composition.
            view.disposeComposition()
        }
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView = null
        lifecycleOwner = null
        _collapsed.value = false
    }

    private fun gravityFor(dock: HudDock): Int = when (dock) {
        HudDock.Top -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        HudDock.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
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
    }
}
