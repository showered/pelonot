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
import com.pelonot.data.repository.AppSettings
import com.pelonot.data.service.RideSnapshot
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.UnitSystem
import com.pelonot.ui.theme.PelonotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    /**
     * The class timeline, in a window of its own at the *other* screen edge
     * (11.1b.1).
     *
     * Two thin bands rather than one tall block, and this one is
     * `FLAG_NOT_TOUCHABLE` — it holds nothing the rider can press, so every tap
     * in it reaches the film underneath. The strip cannot make that promise;
     * it has a stop button on it.
     */
    private var timelineView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** Outlives the composition, so a preference write cannot be half-applied. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _dock = MutableStateFlow(HudDock.DEFAULT)
    val dock: StateFlow<HudDock> = _dock.asStateFlow()

    private val _collapsed = MutableStateFlow(false)
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    /**
     * Whether the volume sliders are showing (11.5.4).
     *
     * Not persisted, and closed again whenever the HUD comes down: the resting
     * strip is three big numbers and a countdown, and 11.5.5 only lets volume
     * onto it at all because this tablet offers nowhere else to change it.
     */
    private val _volumeOpen = MutableStateFlow(false)
    val volumeOpen: StateFlow<Boolean> = _volumeOpen.asStateFlow()

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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            // 19.1.1. The strip is up for exactly as long as a ride is running
            // and the rider is somewhere else, which makes it the right window
            // to hold the screen awake from. Netflix holds its own lock, which
            // is why this never showed up on the bike; a rider on the launcher,
            // on a podcast, or on anything that does not, watches the tablet
            // sleep mid-interval. Asserted on the strip and not the timeline
            // bar — one window claiming it is enough, and the timeline is the
            // one that can fail to attach.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = gravityFor(HudDock.DEFAULT)
    }

    private val timelineParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            // The whole reason this is a separate window: a bar the rider can
            // never press should never eat a press.
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = gravityFor(HudDock.DEFAULT.opposite())
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
        onOpenApp: () -> Unit = {},
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
                val isVolumeOpen by _volumeOpen.collectAsStateWithLifecycle()

                // 11.5.4: mid-ride is when a rider discovers the film is too
                // loud, and going to Settings means abandoning the ride screen
                // and the film together.
                val volumeController = ServiceLocator.volumeController
                val mediaVolume by volumeController.mediaVolume.collectAsStateWithLifecycle()
                val volumeError by volumeController.lastError.collectAsStateWithLifecycle()
                val coachVolume by ServiceLocator.settingsRepository.settings
                    .map { it.coachVolume }
                    .collectAsStateWithLifecycle(AppSettings.DEFAULT_COACH_VOLUME)

                // 11.1b.1. Collected rather than passed in at show(): a rider
                // who moves the slider in Settings during a ride sees the strip
                // change behind them, which is the only way to judge it.
                val opacity by ServiceLocator.settingsRepository.settings
                    .map { it.hudOpacity }
                    .collectAsStateWithLifecycle(HudOpacity.DEFAULT)

                // Telemetry is collected separately from the ride snapshot: it
                // changes several times a second and the snapshot does not.
                val reading by ServiceLocator.sensorRepository.displayReading
                    .collectAsStateWithLifecycle()

                // 13.5: the strip shows distance, so it reads the same
                // preference as every other surface rather than a second one.
                val units by ServiceLocator.settingsRepository.settings
                    .map { it.unitSystem }
                    .collectAsStateWithLifecycle(UnitSystem.DEFAULT)

                PelonotTheme(darkTheme = true, useDynamicColor = false, units = units) {
                    HudOverlayMain(
                        snapshot = snapshot,
                        reading = reading,
                        dock = currentDock,
                        collapsed = isCollapsed,
                        coachStyle = coachStyle,
                        onToggleCollapsed = { _collapsed.value = !_collapsed.value },
                        onDockChange = ::moveTo,
                        onOpenApp = onOpenApp,
                        // 11.6.10. Raise the flag, then bring the app forward:
                        // the ride screen is what comes up, and it opens the
                        // sheet. The ride is never ended to reach a setting.
                        onOpenSettings = {
                            RideSettingsRequest.request()
                            onOpenApp()
                        },
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
                        volumeOpen = isVolumeOpen,
                        mediaVolume = mediaVolume,
                        coachVolume = coachVolume,
                        volumeError = volumeError,
                        onToggleVolume = {
                            // Re-read on the way open: the level may have moved
                            // in Settings, or from another app, since last time.
                            if (!_volumeOpen.value) volumeController.refresh()
                            _volumeOpen.value = !_volumeOpen.value
                        },
                        // 11.5.9. Closed by a swipe towards the dock edge or by
                        // being left alone, neither of which is a toggle.
                        onCloseVolume = { _volumeOpen.value = false },
                        onMediaVolumeChange = volumeController::setMediaVolume,
                        onCoachVolumeChange = ::setCoachVolume,
                        opacity = opacity
                    )
                }
            }
        }

        val timeline = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            setContent {
                val snapshot by snapshotFlow.collectAsStateWithLifecycle()
                val opacity by ServiceLocator.settingsRepository.settings
                    .map { it.hudOpacity }
                    .collectAsStateWithLifecycle(HudOpacity.DEFAULT)

                PelonotTheme(darkTheme = true, useDynamicColor = false) {
                    HudTimelineBar(snapshot = snapshot, opacity = opacity)
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
            return
        }

        // Failing to attach the timeline must not take the ride controls down
        // with it: the strip is the part the rider needs.
        timelineParams.gravity = gravityFor(dock.opposite())
        runCatching { windowManager.addView(timeline, timelineParams) }
            .onSuccess { timelineView = timeline }
            .onFailure { Log.w(TAG, "Could not attach the timeline bar", it) }
    }

    /**
     * Writes the coach level straight through to the same preference Settings
     * uses, so the two sliders are one setting and not two (11.5.6).
     *
     * On [scope] rather than a composition-scoped one: a DataStore write must
     * not be cancelled by the strip being collapsed mid-drag.
     */
    private fun setCoachVolume(fraction: Float) {
        scope.launch { ServiceLocator.settingsRepository.setCoachVolume(fraction) }
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
        // The timeline always sits opposite, so re-docking swaps both.
        timelineParams.gravity = gravityFor(dock.opposite())
        timelineView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, timelineParams) }
                .onFailure { Log.w(TAG, "Could not re-dock the timeline bar", it) }
        }
        onDockChanged?.invoke(dock)
    }

    fun hide() {
        timelineView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "Timeline bar was already detached", it) }
            view.disposeComposition()
        }
        timelineView = null

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
        _volumeOpen.value = false
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
