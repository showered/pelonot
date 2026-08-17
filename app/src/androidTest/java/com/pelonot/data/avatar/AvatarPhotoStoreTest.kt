package com.pelonot.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.domain.identity.AvatarPhoto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The half of 20.2.4 that cannot be tested on the JVM: reading and collecting
 * files, which still happens on the tablet whether or not this app is the one
 * that wrote them (PLAN 20.7.1).
 *
 * **The re-encode that used to be tested here is gone with the code it tested.**
 * `import` — decode, honour orientation, strip EXIF, write a fresh JPEG — left
 * this app on 18 August 2026 for the companion web app (20.7.1), and the
 * property those five assertions held (a photograph carries no GPS coordinates
 * or camera metadata into a rider's profile) now belongs to 20.7.3, asserted
 * against a browser's `canvas`/`toBlob` re-encode rather than `Bitmap.compress`.
 * What is left here is written directly to the files this store reads, since
 * nothing in this app can produce one any more.
 */
@RunWith(AndroidJUnit4::class)
class AvatarPhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AvatarPhotoStore(context)

    @Before
    @After
    fun clean() {
        store.forgetUnreferenced(emptySet())
    }

    /** A name in the column with no file behind it draws nothing, not a crash. */
    @Test
    fun aPhotographThatIsNotThereIsSimplyAbsent() {
        val missing = AvatarPhoto.nameFor(localUserId = 9, atEpochMs = 1)
        assertFalse(store.exists(missing))
        assertNull(store.decode(missing, targetPx = 64))
    }

    /** A photograph on disk is found and decoded at the size asked for. */
    @Test
    fun aPhotographThatIsThereDecodesAtTheSizeAsked() {
        val photo = writeSquareJpeg(localUserId = 1, edgePx = 400)

        assertTrue(store.exists(photo))
        val decoded = store.decode(photo, targetPx = 64)!!
        // `inSampleSize` only ever halves, so the result is not exactly 64 —
        // it is the smallest power-of-two step still at or above it.
        assertTrue("decoded at ${decoded.width}px for a 64px target", decoded.width in 64..128)
    }

    /**
     * The housekeeping, and the direction of it: what is **referenced** is kept
     * and everything else goes. Three different things used to leave a file
     * behind — replacing a picture, cancelling the dialog, removing a profile —
     * and this is the one rule that covered all three; only the third can still
     * happen (`UserRepository.delete`), but the sweep does not know that and
     * should not need to.
     */
    @Test
    fun everyPhotographNoProfileNamesIsCollected() {
        val first = writeSquareJpeg(localUserId = 1, atEpochMs = 1)
        val second = writeSquareJpeg(localUserId = 1, atEpochMs = 2)
        val other = writeSquareJpeg(localUserId = 2, atEpochMs = 1)

        val removed = store.forgetUnreferenced(setOf(second.fileName))
        assertEquals(2, removed)
        assertTrue(store.exists(second))
        assertFalse(store.exists(first))
        assertFalse("another rider's face was collected too", store.exists(other))
    }

    /** A JPEG written straight to where this store expects to find one. */
    private fun writeSquareJpeg(
        localUserId: Int,
        edgePx: Int = 64,
        atEpochMs: Long = System.nanoTime()
    ): AvatarPhoto {
        val photo = AvatarPhoto.nameFor(localUserId, atEpochMs)
        val bitmap = Bitmap.createBitmap(edgePx, edgePx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(20, 20, 200))
        store.fileFor(photo).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        bitmap.recycle()
        return photo
    }
}
