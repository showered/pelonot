package com.pelonot.domain.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class BoundedManifestTest {
    @Test fun acceptsTheExactLimit() {
        assertEquals("1234", readBoundedManifest(StringReader("1234"), 4))
    }

    @Test fun stopsAnEndlessResponseAfterTheLimit() {
        var read = 0
        val endless = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                buffer[offset] = 'x'
                read++
                return 1
            }
            override fun close() = Unit
        }
        try {
            readBoundedManifest(endless, 8)
            throw AssertionError("Oversized response accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals(9, read)
        }
    }
}
