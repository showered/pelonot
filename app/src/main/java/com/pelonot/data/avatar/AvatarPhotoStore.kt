package com.pelonot.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.pelonot.domain.identity.AvatarPhoto
import java.io.File

/**
 * A rider's photograph, on the tablet that draws it (PLAN 20.2.4, 20.7.1).
 *
 * ### It draws and it collects; it no longer makes one
 *
 * Choosing a photograph left this app on 18 August 2026 and belongs to the
 * companion web app now (20.7): a bike has no camera and therefore no camera
 * roll, so the gallery door this class used to open led nowhere. What is left is
 * the half a face still needs — where the file is, whether it is there, the
 * pixels at the size they are about to be drawn, and the sweep that removes
 * every file no profile names.
 *
 * **The re-encode that went with it is not lost, it has moved** — and it moved
 * somewhere it matters more. 20.2.5's rule was that the original bytes are never
 * copied: the pixels are decoded and a new image written, so a photograph's GPS
 * coordinates are gone by construction rather than by deletion. On the web the
 * same rule is what stops those coordinates being *uploaded*, which is 20.7.3.
 *
 * ### Decoded where it is drawn, at the size it is drawn
 *
 * The stored square is 512 px, comfortably above the largest face this app draws
 * (a 114 dp disc is 171 px at the bike's 240 dpi) — but 512² at four bytes is a
 * megabyte a row, and the same face appears on the selector, the greeting and
 * the household panel at three different sizes. So [decode] takes a target.
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
     * anything drawing a face asks this rather than assuming. `Avatar` keeps the
     * rider's colour beside the photograph for exactly this answer.
     */
    fun exists(photo: AvatarPhoto): Boolean = fileFor(photo).isFile

    /**
     * The pixels, at roughly [targetPx] on a side, or null if the file is not
     * there or will not decode.
     *
     * **Decoded at the size it is about to be drawn**, which is the difference
     * between a household panel costing a few kilobytes and costing a megabyte
     * a row: the stored 512 px square at four bytes is 1 MB, and the same face
     * appears on the selector, the greeting and the panel at three sizes. The
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
    }
}
