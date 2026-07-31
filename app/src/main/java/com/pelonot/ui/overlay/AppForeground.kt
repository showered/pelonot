package com.pelonot.ui.overlay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import com.pelonot.MainActivity

/**
 * The door between the HUD and the full app (11.1a).
 *
 * The rider spends a class with Netflix in front and Pelonot's strip on one
 * edge. Before this existed there was no way back into the app except the
 * launcher, and no way back out to the HUD except leaving the app again — the
 * single journey a rider makes most often during a ride routed through the
 * home screen.
 *
 * **Why a service is allowed to do this at all.** Android 10 restricted
 * background activity starts, and holding a foreground service is *not* on the
 * exemption list. `SYSTEM_ALERT_WINDOW` is — and this app already requires it,
 * because it is the permission the HUD itself is drawn under. So the same grant
 * that makes the strip possible is the one that lets the strip open the app.
 * When it has not been granted there is no HUD either, the rider is on the ride
 * screen, and there is nothing to bring forward.
 */
object AppForeground {

    /**
     * Brings the app's existing task to the front, or launches it if the task
     * is gone.
     *
     * The intent deliberately mirrors what a launcher sends — `ACTION_MAIN`
     * with `CATEGORY_LAUNCHER` — because that is what resumes an existing task
     * where it was left. `FLAG_ACTIVITY_CLEAR_TOP` would instead tear the ride
     * screen down and rebuild it, which is the opposite of what a rider
     * glancing back at the app wants.
     */
    fun bringForward(context: Context) {
        val intent = Intent(context.applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { context.applicationContext.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not bring the app forward", it) }
    }

    /**
     * Sends the app to the back so whatever the rider was watching returns —
     * and with it the HUD, which stands down only while the ride screen is on
     * top.
     *
     * `moveTaskToBack` rather than `finish()`: the ride screen has to survive,
     * because the rider is coming back to it. Returns false when there is no
     * Activity to move, which is the [PelonotTheme]-from-a-Service case the
     * rest of the app guards against too.
     */
    fun sendToBack(context: Context): Boolean {
        val activity = context.findActivity() ?: run {
            Log.w(TAG, "No Activity in the context chain; cannot return to the HUD")
            return false
        }
        return activity.moveTaskToBack(true)
    }

    /** Compose hands out a wrapped context, so the Activity may be several deep. */
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val TAG = "AppForeground"
}
