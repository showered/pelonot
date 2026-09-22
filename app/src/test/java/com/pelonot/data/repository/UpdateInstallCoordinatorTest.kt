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
        commit: suspend (File, () -> Unit) -> Unit = { _, beforeCommit -> beforeCommit() },
        ride: () -> Boolean = { false }
    ) = UpdateInstallCoordinator(download, commit, { true }, { error("Not requested") }, ride)

    @Test fun installErrorsAreVisibleAndCanBeRetried() = runTest {
        val file = File.createTempFile("update", ".apk")
        val subject = coordinator({ DownloadOutcome.Success(file) }, { _, _ -> error("Disk full") })
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
        val subject = coordinator({ downloads++; downloaded.await() }, { _, beforeCommit -> beforeCommit(); commits++ })
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
            { _, beforeCommit -> beforeCommit(); committed = true },
            { riding }
        )
        subject.install(manifest)
        assertFalse(committed)
        assertFalse(file.exists())
        assertTrue(subject.state.value is UpdateInstallState.Failed)
    }


    @Test fun aRideStartingWhileTheApkIsCopiedPreventsCommitAndAllowsRetry() = runTest {
        var riding = false
        var committed = false
        val copyStarted = CompletableDeferred<Unit>()
        val finishCopy = CompletableDeferred<Unit>()
        val files = mutableListOf<File>()
        val subject = coordinator(
            { DownloadOutcome.Success(File.createTempFile("update", ".apk").also(files::add)) },
            { _, beforeCommit ->
                copyStarted.complete(Unit)
                finishCopy.await()
                beforeCommit()
                committed = true
            },
            { riding }
        )
        val installation = launch { subject.install(manifest) }
        copyStarted.await()
        assertEquals(UpdateInstallState.Installing, subject.state.value)
        riding = true
        finishCopy.complete(Unit)
        installation.join()

        assertFalse(committed)
        assertFalse(files.single().exists())
        assertEquals(
            UpdateInstallState.Failed("Finish your ride before installing an update."),
            subject.state.value
        )
        riding = false
        subject.install(manifest)
        assertTrue(committed)
        assertEquals(UpdateInstallState.Installing, subject.state.value)
        assertTrue(files.none { it.exists() })
    }

    @Test fun cancellationWhilePreparingInstallationCleansUpAndAllowsRetry() = runTest {
        val copyStarted = CompletableDeferred<Unit>()
        val finishCopy = CompletableDeferred<Unit>()
        var committed = false
        val files = mutableListOf<File>()
        val subject = coordinator(
            { DownloadOutcome.Success(File.createTempFile("update", ".apk").also(files::add)) },
            { _, beforeCommit ->
                copyStarted.complete(Unit)
                finishCopy.await()
                beforeCommit()
                committed = true
            }
        )
        val installation = launch { subject.install(manifest) }
        copyStarted.await()
        installation.cancel()
        installation.join()
        assertFalse(committed)
        assertFalse(files.single().exists())
        assertEquals(UpdateInstallState.Idle, subject.state.value)

        finishCopy.complete(Unit)
        subject.install(manifest)
        assertTrue(committed)
        assertTrue(files.none { it.exists() })
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
