package com.pelonot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import kotlin.math.min

/**
 * A QR code, drawn rather than scanned (PLAN 15.6.6).
 *
 * Three things about drawing one on **this** screen in particular:
 *
 * - **It is read by a phone camera at about half a metre**, in a room whose
 *   lighting nobody chose. That argues for a generous quiet zone and a high
 *   error-correction level, both of which cost pixels and are worth it — a code
 *   that needs a second attempt to scan has already lost to typing eight
 *   characters.
 * - **The modules must land on whole pixels.** A QR scaled to an arbitrary size
 *   gets rows one pixel wider than their neighbours, which is exactly the kind
 *   of blur that makes a scan intermittent rather than failed — the worst
 *   failure mode, because it looks like the rider's fault.
 * - **Never on a dark background.** Scanners expect dark-on-light and many will
 *   not invert. This draws its own white field regardless of the app's theme,
 *   which is the one place in Pelonot where the theme is deliberately ignored.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(content) { encode(content) } ?: return

    Canvas(modifier) {
        val quietModules = QUIET_ZONE * 2
        val totalModules = matrix.width + quietModules

        // Floored to a whole number of pixels per module, then centred in
        // whatever is left over. A fractional module size is the blur above.
        val modulePx = floor(min(size.width, size.height) / totalModules)
        if (modulePx < 1f) return@Canvas

        val drawnPx = modulePx * totalModules
        val originX = (size.width - drawnPx) / 2f
        val originY = (size.height - drawnPx) / 2f

        drawRect(
            color = Color.White,
            topLeft = Offset(originX, originY),
            size = Size(drawnPx, drawnPx)
        )

        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (!matrix.get(column, row)) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(
                        originX + (column + QUIET_ZONE) * modulePx,
                        originY + (row + QUIET_ZONE) * modulePx
                    ),
                    size = Size(modulePx, modulePx)
                )
            }
        }
    }
}

/**
 * Null rather than a thrown exception when the content will not fit.
 *
 * The caller is a screen with a perfectly good fallback — the code in large
 * type and a URL to type — so a missing QR must degrade to that rather than
 * taking the sign-in flow down with it.
 */
private fun encode(content: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            // Q rather than the default M: a bike screen picks up dust and
            // fingerprints, and the extra redundancy costs a slightly denser
            // code rather than a larger one at these lengths.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            // The quiet zone is drawn above, at a width this composable
            // chooses, so ZXing must not add its own on top.
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
}.getOrNull()

/**
 * Four modules is the specification's minimum. It is not decoration: without
 * it a scanner cannot find the code's edge against whatever is behind it.
 */
private const val QUIET_ZONE = 4
