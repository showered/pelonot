package com.pelonot.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Where `PackageInstaller` reports back on a session committed by
 * [UpdateInstaller] (PLAN 30.4).
 *
 * A commit is asynchronous: the system needs to raise its own confirmation UI
 * before an install can proceed, and it does that by handing back an
 * [Intent] rather than doing it itself, because the caller may — as here —
 * have gone away from the foreground by the time the answer arrives.
 * [Intent.EXTRA_INTENT] is that confirmation UI, and starting it with
 * `FLAG_ACTIVITY_NEW_TASK` is this receiver's whole job.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = com.pelonot.di.ServiceLocator.updateInstallCoordinator
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent == null) {
                    coordinator.fail("Couldn't open the installer. Try again.")
                    return
                }
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent.let {
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        coordinator.fail("Couldn't open the installer. Try again.")
                        Log.w(TAG, "Could not raise the install confirmation: ${e.message}")
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> coordinator.reset()
            PackageInstaller.STATUS_FAILURE_ABORTED -> coordinator.fail("Installation cancelled. You can try again when you're ready.")
            else -> {
                coordinator.fail("The update couldn't replace this copy of Pelonot. Try again, or ask whoever installed it.")
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Update install did not succeed (status $status): $message")
            }
        }
    }

    private companion object {
        const val TAG = "PelonotUpdate"
    }
}
