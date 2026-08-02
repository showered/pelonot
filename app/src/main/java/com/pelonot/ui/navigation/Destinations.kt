package com.pelonot.ui.navigation

import android.net.Uri

/**
 * Every navigation destination, with its route pattern and a builder for
 * concrete routes.
 *
 * Routes used to be assembled inline as string literals at each call site
 * ("class_detail/${classTemplate.id}"), with argument names spelled out again
 * in the matching `composable(...)`. A class id containing a `/` or a space —
 * both legal in the seeded data — produced a route that silently matched
 * nothing.
 */
sealed class Destination(val route: String) {

    data object ProfileSelector : Destination("profile_selector")

    data object Dashboard : Destination("dashboard")

    data object ClassLibrary : Destination("class_library")

    data object Settings : Destination("settings")

    data object ClassDetail : Destination("class_detail/{$ARG_CLASS_ID}") {
        fun of(classId: String) = "class_detail/${Uri.encode(classId)}"
    }

    data object Ride : Destination("ride?$ARG_CLASS_ID={$ARG_CLASS_ID}&$ARG_INTENT_ID={$ARG_INTENT_ID}") {
        fun of(classId: String?, intentId: String) = buildString {
            append("ride?")
            append("$ARG_CLASS_ID=${classId?.let(Uri::encode).orEmpty()}")
            append("&$ARG_INTENT_ID=$intentId")
        }
    }

    data object PostRide : Destination("post_ride/{$ARG_WORKOUT_ID}") {
        fun of(workoutId: String) = "post_ride/${Uri.encode(workoutId)}"
    }

    data object History : Destination("history")

    /**
     * The FTP trend, reached from the dashboard's FTP card (16.3.1). No
     * argument: it is always the selected profile's, because FTP is a statement
     * about a person and there is no view of somebody else's.
     */
    data object FtpProgress : Destination("ftp_progress")

    /**
     * A finished ride, opened from history — distinct from [PostRide], which is
     * the same figures wrapped in the RPE prompt, the FTP breakthrough dialog
     * and the guest-filing flow. None of those belong on a ride from March.
     */
    data object RideDetail : Destination("ride_detail/{$ARG_WORKOUT_ID}") {
        fun of(workoutId: String) = "ride_detail/${Uri.encode(workoutId)}"
    }

    companion object {
        const val ARG_CLASS_ID = "classId"
        const val ARG_INTENT_ID = "intentId"
        const val ARG_WORKOUT_ID = "workoutId"
    }
}
