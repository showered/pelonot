package com.pelonot.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
        x = 100
        y = 100
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (composeView != null) return

        composeView = ComposeView(context).apply {
            // Attach lifecycle owners so Compose works correctly in a Service/Window
            val lifecycleOwner = OverlayLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            ViewTreeViewModelStoreOwner.set(this, object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })

            setContent {
                PelonotTheme {
                    HudOverlayContent(
                        onDrag = { dx, dy ->
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()
                            windowManager.updateViewLayout(this, layoutParams)
                        }
                    )
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
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

@Composable
fun HudOverlayContent(onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 250.dp, height = 150.dp)
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Text(text = "HUD Overlay Placeholder", color = Color.White)
    }
}
