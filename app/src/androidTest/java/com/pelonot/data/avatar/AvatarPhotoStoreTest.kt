package com.pelonot.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.domain.identity.AvatarPhoto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The half of 20.2.4 that cannot be tested on the JVM, and it is the half that
 * matters (PLAN 20.2.5).
 *
 * A photograph carrying its GPS coordinates into a rider's profile is a defect
 * nobody would ever see — the picture looks right, the app works, and the
 * coordinates travel with it to the cloud (20.2.7) and possibly to a friend's
 * screen (17.5). So it is asserted rather than reasoned about, against a source
 * file with real tags on it.
 */
@RunWith(AndroidJUnit4::class)
class AvatarPhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AvatarPhotoStore(context)
    private val sources = mutableListOf<File>()

    @Before
    @After
    fun clean() {
        store.forgetUnreferenced(emptySet())
        sources.forEach { it.delete() }
        sources.clear()
    }

    /**
     * **The whole of 20.2.5 in one assertion pair.** The source carries GPS, a
     * make and a model; the stored copy carries none of them, because it is a
     * re-encode of the pixels and never a copy of the file.
     */
    @Test
    fun anImportedPhotographKeepsNoMetadata() = runBlocking {
        val source = sourceImage(width = 800, height = 600) { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "51/1,30/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "0/1,7/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            exif.setAttribute(ExifInterface.TAG_MAKE, "Pelonot")
            exif.setAttribute(ExifInterface.TAG_MODEL, "A phone somebody owns")
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:08:17 09:14:00")
        }

        val photo = store.import(Uri.fromFile(source), localUserId = 1)
        assertNotNull("the picture was not imported at all", photo)

        val written = ExifInterface(store.fileFor(photo!!).path)
        assertNull("latitude survived", written.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("longitude survived", written.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("the camera survived", written.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("the model survived", written.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(
            "the moment it was taken survived",
            written.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        )
        assertNull("a location was reported", written.latLong)
    }

    /**
     * The other half of 20.2.5, and the reason the item does not simply say
     * *strip EXIF*: the orientation tag has to be **honoured** on the way out,
     * or a portrait photograph becomes a rider lying on their side.
     *
     * The source is red down its left edge. Rotated 90° clockwise, red is along
     * the top — which is what the stored square must show, with nothing left in
     * the file to rotate it by afterwards.
     */
    @Test
    fun theOrientationTagIsAppliedAndThenThrownAway() = runBlocking {
        val source = sourceImage(width = 600, height = 600, redEdge = true) { exif ->
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
        }

        val photo = store.import(Uri.fromFile(source), localUserId = 1)!!
        val stored = BitmapFactory.decodeFile(store.fileFor(photo).path)

        val topMiddle = stored.getPixel(stored.width / 2, 2)
        val leftMiddle = stored.getPixel(2, stored.height / 2)
        assertTrue("the rotation was not applied", Color.red(topMiddle) > 200)
        assertTrue("the rotation was not applied", Color.red(leftMiddle) < 200)

        // And nothing is left in the file to rotate it a second time.
        //
        // **`ORIENTATION_UNDEFINED`, not "no attribute"** — worth writing down,
        // because two earlier versions of this line asserted the wrong thing.
        // `ExifInterface` on a JPEG with no metadata at all answers `0` for
        // this tag, through both `getAttribute` and `getAttributeInt`, so
        // "absent" is not observable through this API and the honest assertion
        // is that whatever is there does not turn the picture.
        val orientation = ExifInterface(store.fileFor(photo).path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(
            "the stored picture would be turned again, by $orientation",
            orientation == ExifInterface.ORIENTATION_UNDEFINED ||
                orientation == ExifInterface.ORIENTATION_NORMAL
        )
    }

    /**
     * Square, and small enough that a household's faces cost less than one
     * ride's samples — the reason being memory rather than disk (a 12 MP
     * photograph is 36 MB decoded, on a tablet with a fraction of a phone's
     * heap).
     */
    @Test
    fun anImportedPhotographIsASmallSquare() = runBlocking {
        val source = sourceImage(width = 1600, height = 900)
        val photo = store.import(Uri.fromFile(source), localUserId = 1)!!

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.fileFor(photo).path, bounds)
        assertEquals(bounds.outWidth, bounds.outHeight)
        assertTrue("stored at ${bounds.outWidth}px", bounds.outWidth <= 512)
        assertTrue(
            "a face costs ${store.fileFor(photo).length()} bytes",
            store.fileFor(photo).length() < 200_000
        )
    }

    /** A file that is not an image is a null, not a crash and not a face. */
    @Test
    fun somethingThatIsNotAPictureIsRefused() = runBlocking {
        val notAnImage = File(context.cacheDir, "not-a-picture.jpg").apply {
            writeText("this is not a photograph")
        }
        sources += notAnImage
        assertNull(store.import(Uri.fromFile(notAnImage), localUserId = 1))
    }

    /**
     * The housekeeping, and the direction of it: what is **referenced** is kept
     * and everything else goes. Three different things leave a file behind —
     * replacing a picture, cancelling the dialog, removing a profile — and this
     * is the one rule that covers all three.
     */
    @Test
    fun everyPhotographNoProfileNamesIsCollected() = runBlocking {
        val source = sourceImage(width = 400, height = 400)
        val first = store.import(Uri.fromFile(source), localUserId = 1)!!
        val second = store.import(Uri.fromFile(source), localUserId = 1)!!
        val other = store.import(Uri.fromFile(source), localUserId = 2)!!

        // Replacing a picture does not delete the old one at the moment of
        // choosing, because the rider is still in a dialog with a Cancel on it.
        assertTrue(store.exists(first))

        val removed = store.forgetUnreferenced(setOf(second.fileName))
        assertEquals(2, removed)
        assertTrue(store.exists(second))
        assertFalse(store.exists(first))
        assertFalse("another rider's face was collected too", store.exists(other))
    }

    /** A name in the column with no file behind it draws nothing, not a crash. */
    @Test
    fun aPhotographThatIsNotThereIsSimplyAbsent() {
        val missing = AvatarPhoto.nameFor(localUserId = 9, atEpochMs = 1)
        assertFalse(store.exists(missing))
        assertNull(store.decode(missing, targetPx = 64))
    }

    /**
     * A JPEG in the cache directory, optionally with a red left edge and
     * whatever tags the caller wants written onto it.
     */
    private fun sourceImage(
        width: Int,
        height: Int,
        redEdge: Boolean = false,
        tags: (ExifInterface) -> Unit = {}
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(20, 20, 200))
            if (redEdge) {
                drawRect(
                    0f, 0f, width * 0.2f, height.toFloat(),
                    Paint().apply { color = Color.rgb(230, 20, 20) }
                )
            }
        }
        val file = File(context.cacheDir, "source-${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.path).also(tags).saveAttributes()
        sources += file
        return file
    }
}
