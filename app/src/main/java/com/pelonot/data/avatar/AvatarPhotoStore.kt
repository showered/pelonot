package com.pelonot.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.pelonot.domain.identity.AvatarPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A photograph on its way to being a rider's face (PLAN 20.2.4, 20.2.5).
 *
 * ### It re-encodes; it never copies
 *
 * That single sentence is 20.2.5. The item asks for EXIF to be stripped and the
 * orientation tag honoured before it goes, and the way to be *sure* of the first
 * half is never to have the metadata in hand: this reads the orientation, decodes
 * the pixels, and writes a new JPEG from the bitmap. `Bitmap.compress` emits no
 * EXIF at all, so the GPS coordinates of a rider's living room, the camera's
 * serial number and the moment the shutter fired are gone by construction rather
 * than by deletion. A copy with tags removed would be one forgotten tag away
 * from being wrong, and it would be wrong silently.
 *
 * It matters more than a local file suggests. The avatar is destined for the
 * cloud with the profile (20.2.7) and possibly for a friend's screen (17.5), and
 * a photograph is the one thing this app handles that a rider did not knowingly
 * type in.
 *
 * ### And it downscales, for a reason that is not disk space
 *
 * A 12 MP phone photograph is about 36 MB decoded, and the largest thing this
 * app ever draws it into is a 114 dp disc — 171 px on the bike's tablet. Loading
 * that to draw this would be an out-of-memory crash on the first screen anybody
 * sees, on a device with a fraction of a phone's heap. [EDGE_PX] is comfortably
 * above every surface that draws a face and small enough that the whole
 * household's photographs cost less than one ride's samples.
 *
 * ### What it deliberately does not do
 *
 * There is no cache and no in-memory bitmap held anywhere: the file is decoded
 * where it is drawn. A face is drawn a handful of times per screen from a file
 * of a few tens of kilobytes, and a cache here would be a second copy of the
 * truth — the shape that has cost this project a whole class of defect.
 */
class AvatarPhotoStore(private val context: Context) {

    /** Where a photograph lives, whether or not it is there. */
    fun fileFor(photo: AvatarPhoto): File = File(directory(), photo.fileName)

    /**
     * Whether the pixels are actually present.
     *
     * The column and the file can come apart — a database imported onto another
     * tablet (12.4.4) names a photograph that never travelled with it — so
     * anything drawing a face asks this rather than assuming. [Avatar] keeps the
     * rider's colour beside the photograph for exactly this answer.
     */
    fun exists(photo: AvatarPhoto): Boolean = fileFor(photo).isFile

    /**
     * The pixels, at roughly [targetPx] on a side, or null if the file is not
     * there or will not decode.
     *
     * **Decoded at the size it is about to be drawn**, which is the difference
     * between a household panel costing a few kilobytes and costing a megabyte
     * a row: [EDGE_PX] squared at four bytes is 1 MB, and the same face appears
     * on the selector, the greeting and the panel at three different sizes. The
     * caller remembers the result; nothing is cached here, for the reason in the
     * class KDoc.
     */
    fun decode(photo: AvatarPhoto, targetPx: Int): Bitmap? {
        val file = fileFor(photo)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
        }
        return runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
    }

    /**
     * Reads [source], writes a square JPEG for [localUserId], and returns its
     * name — or null if the picture could not be read.
     *
     * Null rather than an exception because every way this fails is the same
     * from the rider's side: the file was a format the platform will not decode,
     * the picker handed back a Uri whose permission has already lapsed, or the
     * image is corrupt. The caller says so and leaves the face as it was;
     * nothing is half-written, because the new name is only returned once the
     * bytes are on disk.
     *
     * **Nothing is deleted here, including the photograph this one replaces.**
     * The rider is still in a dialog with a Cancel button on it, and the column
     * still names the old picture; throwing it away at the moment of *choosing*
     * would make Cancel a lie. [forgetUnreferenced] collects both the old file
     * and this one, if the rider walks away.
     */
    suspend fun import(source: Uri, localUserId: Int): AvatarPhoto? =
        withContext(Dispatchers.IO) {
            val square = runCatching { decodeSquare(source) }
                .onFailure { Log.w(TAG, "could not read the chosen picture: ${it.javaClass.simpleName}") }
                .getOrNull() ?: return@withContext null

            val photo = freshNameFor(localUserId)
            val written = runCatching {
                fileFor(photo).outputStream().use { out ->
                    square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                }
            }.getOrDefault(false)
            square.recycle()

            if (!written) {
                fileFor(photo).delete()
                return@withContext null
            }
            photo
        }

    /**
     * Deletes every photograph no profile names — the whole of this feature's
     * housekeeping, in one sweep at launch (20.2.4).
     *
     * **It is one rule rather than three, and that is why it is a sweep.** Three
     * separate things leave a file behind and each would otherwise want its own
     * cleanup: replacing a picture, opening the dialog and cancelling after
     * choosing one, and removing a profile altogether. All three come out the
     * same way here — *the database is the list of what is wanted* — and the
     * third is the one a per-action delete would have missed for ever, because
     * `UserRepository.delete` knows nothing about files.
     *
     * The direction matters: it takes what is **referenced** and removes the
     * rest, rather than taking what looks stale and removing that. A file whose
     * name nothing in `profiles` mentions cannot be drawn by anything.
     */
    fun forgetUnreferenced(referenced: Set<String>): Int {
        val gone = directory().listFiles()
            ?.filter { it.name !in referenced }
            ?.count { it.delete() }
            ?: 0
        if (gone > 0) Log.i(TAG, "Removed $gone unused avatar photo(s)")
        return gone
    }

    private fun directory(): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * A name for [localUserId] that is not already taken.
     *
     * The clock has millisecond resolution and two imports inside one
     * millisecond would otherwise write over each other — which no rider can
     * do, since each one is a trip to another app, but a test can and did. The
     * whole point of the timestamp is that replacing a photograph is a *new*
     * file, so a name that can repeat gives up the property it exists for.
     */
    private fun freshNameFor(localUserId: Int): AvatarPhoto {
        var at = System.currentTimeMillis()
        var photo = AvatarPhoto.nameFor(localUserId, at)
        while (fileFor(photo).exists()) {
            at += 1
            photo = AvatarPhoto.nameFor(localUserId, at)
        }
        return photo
    }

    /**
     * The chosen picture, upright and square, at [EDGE_PX].
     *
     * Three steps and the order of them is load-bearing. The orientation tag is
     * read from its own stream **before** anything is decoded, because it is the
     * one piece of metadata that must survive the re-encode — a portrait
     * photograph written out without honouring it is a rider lying on their
     * side, which is 20.2.5's other half and the reason the item does not simply
     * say "strip EXIF". The rotation is applied next, and only then is the
     * square taken: cropping first would take the centre of a picture that is
     * not yet the right way up.
     */
    private fun decodeSquare(source: Uri): Bitmap? {
        val rotation = context.contentResolver.openInputStream(source)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decoded at the smallest power-of-two step that is still at least the
        // size we want, so a 12 MP photograph never exists in memory at 12 MP.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, EDGE_PX)
        }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val upright = decoded.uprighted(rotation)
        val square = upright.centreSquare()
        val scaled = Bitmap.createScaledBitmap(square, EDGE_PX, EDGE_PX, true)

        // `createBitmap` returns the source itself when it has nothing to do,
        // so each of these is only thrown away when it is genuinely a new one.
        if (square !== scaled) square.recycle()
        if (upright !== square && upright !== scaled) upright.recycle()
        if (decoded !== upright && decoded !== scaled) decoded.recycle()
        return scaled
    }

    /** The whole of the EXIF orientation tag, including the mirrored cases. */
    private fun Bitmap.uprighted(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    /**
     * The largest centred square.
     *
     * Centred rather than smart-cropped: a face detector is a large dependency
     * to make a decision the rider can make themselves by choosing a different
     * picture, and a crop that guesses wrong is harder to understand than one
     * that is obviously the middle.
     */
    private fun Bitmap.centreSquare(): Bitmap {
        val edge = minOf(width, height)
        return Bitmap.createBitmap(this, (width - edge) / 2, (height - edge) / 2, edge, edge)
    }

    /**
     * The largest power-of-two step that still leaves at least [targetPx] on the
     * short side — so the decode is never *below* what is wanted, which would
     * show as a soft face on the one screen where the face is the subject.
     */
    private fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    companion object {
        private const val TAG = "AvatarPhotoStore"

        /** Inside `filesDir`, so it is app-private and needs no permission. */
        private const val DIRECTORY = "avatars"

        /**
         * The stored edge, in pixels.
         *
         * The largest face this app draws is the profile tile's, which derives
         * 66–114 dp from the screen — 171 px at the bike's 240 dpi. 512 leaves
         * room for a denser screen and for the day a face is drawn larger,
         * without being a number chosen by doubling until it felt safe.
         */
        private const val EDGE_PX = 512

        /** Above this a JPEG stops getting visibly better and keeps growing. */
        private const val QUALITY = 90
    }
}
