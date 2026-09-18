package com.pelonot.domain.update

import java.io.Reader

/** Stop reading at the limit, including when a server streams an endless response. */
fun readBoundedManifest(reader: Reader, maxChars: Int): String {
    require(maxChars > 0)
    val buffer = CharArray(maxChars + 1)
    var size = 0
    while (size < buffer.size) {
        val read = reader.read(buffer, size, buffer.size - size)
        if (read < 0) break
        size += read
    }
    require(size <= maxChars) { "Update manifest is too large" }
    return String(buffer, 0, size)
}
