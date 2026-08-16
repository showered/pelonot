package com.pelonot.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The runtime side of `POST_NOTIFICATIONS`.
 *
 * The permission has been in the manifest since the ride notification was
 * written and was requested by nothing, so on API 33+ the notification was
 * never posted at all — and that notification is the only way back into a ride
 * once the Activity is gone (11.1a.5). The bike's own tablet is Android 11 and
 * so never showed the defect; `targetSdk 34` means every other device does.
 */
object NotificationPermission {

    /** The permission to ask for, or null where the platform grants it at install time. */
    val runtimePermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun isGranted(context: Context): Boolean {
        val permission = runtimePermission ?: return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Asks for notification permission once, the first time a rider starts a class.
 *
 * In-context rather than at launch: the only notification this app posts is the
 * ongoing ride, so a rider who has never started one has nothing to say yes to.
 *
 * **In the countdown, not in the ride (20.4.8).** This used to be composed on
 * the far side of the countdown's own `return`, so the earliest it could
 * possibly fire was after the class had started — and it was measured landing
 * over the ride screen **21 seconds into a rider's first ever class**, with
 * them pedalling. That is exactly the fault 11.6.14 named for the overlay
 * permission, and its answer is the one to copy: the ten seconds a rider spends
 * clipping in is when a question can be asked, and the count is held while it
 * is outstanding.
 *
 * [deferred] holds the request back while another dialog is on screen — the
 * overlay prompt asks for something the rider can actually see the point of,
 * and two system dialogs stacked on the first ten seconds of a class is how
 * both get dismissed unread. [onAsking] reports the platform dialog going up
 * and coming down, so the caller can hold its clock for the same reason.
 *
 * A denial is not retried and not reported: the ride itself is unaffected, and
 * the platform stops showing the dialog after two refusals anyway. Which is
 * also why this is composed **once** for the whole of a ride rather than in
 * each phase of it — a second call site would spend the rider's second refusal
 * on the same class.
 */
@Composable
fun RequestRideNotificationPermission(
    deferred: Boolean = false,
    onAsking: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val asking by rememberUpdatedState(onAsking)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { asking(false) }

    LaunchedEffect(deferred) {
        val permission = NotificationPermission.runtimePermission
        if (!asked && !deferred && permission != null && !NotificationPermission.isGranted(context)) {
            asked = true
            asking(true)
            launcher.launch(permission)
        }
    }
}
