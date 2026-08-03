package com.pelonot.ui.navigation

import android.net.Uri
import com.pelonot.domain.model.RideIntent

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

    data object Ride : Destination(
        "ride?$ARG_CLASS_ID={$ARG_CLASS_ID}&$ARG_INTENT_ID={$ARG_INTENT_ID}" +
            "&$ARG_RESUME_ID={$ARG_RESUME_ID}"
    ) {
        fun of(classId: String?, intentId: String) = buildString {
            append("ride?")
            append("$ARG_CLASS_ID=${classId?.let(Uri::encode).orEmpty()}")
            append("&$ARG_INTENT_ID=$intentId")
        }

        /**
         * Re-entering a ride the app was killed in the middle of (8.3d).
         *
         * [classId] is carried **for the screen's own titles only** — it comes
         * off the interrupted row, so it is the class the ride was actually
         * started with. Everything the ride is *recorded* against — the intent
         * and the FTP — is read back off that row by the service rather than
         * passed through here, because a route built now would be carrying the
         * rider's *current* values and a breakthrough accepted since the crash
         * would rescore the ride (7.8).
         *
         * Without the class id the screen has no `ClassPlan` and calls a
         * 13-interval class a free ride in its own subtitle, which is how this
         * argument was found.
         */
        fun resuming(workoutId: String, classId: String?) = buildString {
            append("ride?")
            append("$ARG_CLASS_ID=${classId?.let(Uri::encode).orEmpty()}")
            append("&$ARG_INTENT_ID=${RideIntent.DEFAULT.id}")
            append("&$ARG_RESUME_ID=${Uri.encode(workoutId)}")
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
     * How much and how often (16.3.2, 16.3.5), reached from the dashboard's
     * progress section. No argument, for the same reason [FtpProgress] has
     * none: it is the selected rider's riding and there is no view of anybody
     * else's.
     */
    data object Riding : Destination("riding")

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

        /** 8.3d — set only when re-entering a ride rather than starting one. */
        const val ARG_RESUME_ID = "resumeId"
    }
}
