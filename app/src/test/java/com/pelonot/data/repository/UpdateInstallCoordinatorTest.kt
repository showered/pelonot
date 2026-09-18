package com.pelonot.data.repository

import com.pelonot.domain.update.UpdateManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateInstallCoordinatorTest {
    private val manifest = UpdateManifest(2, "1.0.1", "Fixes", "https://example.org/app.apk", "a".repeat(64))

    private fun coordinator(
        download: suspend (UpdateManifest) -> DownloadOutcome,
        commit: suspend (File) -> Unit = {},
        ride: () -> Boolean = { false }
    ) = UpdateInstallCoordinator(download, commit, { true }, { error("Not requested") }, ride)

    @Test fun installErrorsAreVisibleAndCanBeRetried() = runTest {
        val file = File.createTempFile("update", ".apk")
        val subject = coordinator({ DownloadOutcome.Success(file) }, { error("Disk full") })
        subject.install(manifest)
        assertTrue(subject.state.value is UpdateInstallState.Failed)
        assertFalse(file.exists())
        subject.reset()
        assertEquals(UpdateInstallState.Idle, subject.state.value)
    }

    @Test fun simultaneousTapsDownloadAndCommitOnlyOnceAndWaitForTheSystem() = runTest {
        val downloaded = CompletableDeferred<DownloadOutcome>()
        var downloads = 0
        var commits = 0
        val file = File.createTempFile("update", ".apk")
        val subject = coordinator({ downloads++; downloaded.await() }, { commits++ })
        val first = launch { subject.install(manifest) }
        testScheduler.runCurrent()
        subject.install(manifest)
        downloaded.complete(DownloadOutcome.Success(file))
        first.join()
        assertEquals(1, downloads)
        assertEquals(1, commits)
        assertEquals(UpdateInstallState.Installing, subject.state.value)
        subject.install(manifest)
        assertEquals(1, downloads)
        subject.fail("Installation cancelled")
        assertTrue(subject.state.value is UpdateInstallState.Failed)
    }

    @Test fun aRideStartingDuringDownloadPreventsCommit() = runTest {
        var riding = false
        var committed = false
        val file = File.createTempFile("update", ".apk")
        val subject = coordinator(
            { riding = true; DownloadOutcome.Success(file) },
            { committed = true },
            { riding }
        )
        subject.install(manifest)
        assertFalse(committed)
        assertFalse(file.exists())
        assertTrue(subject.state.value is UpdateInstallState.Failed)
    }

    @Test fun anActiveRideNeverDownloads() = runTest {
        val subject = coordinator({ error("Must not download") }, ride = { true })
        subject.install(manifest)
        assertTrue(subject.state.value is UpdateInstallState.Failed)
    }

    @Test fun cancelledDownloadResetsAndPropagatesCancellation() = runTest {
        val subject = coordinator({ throw kotlinx.coroutines.CancellationException() })
        try {
            subject.install(manifest)
            fail("Cancellation swallowed")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(UpdateInstallState.Idle, subject.state.value)
        }
    }
}
