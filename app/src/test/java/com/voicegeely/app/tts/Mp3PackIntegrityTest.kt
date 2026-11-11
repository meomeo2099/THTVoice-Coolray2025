package com.voicegeely.app.tts

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class Mp3PackIntegrityTest {
    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    @Test fun unchangedBytesMatch() {
        assertTrue(Mp3PackIntegrity.matches("abc".byteInputStream(), abcSha, 3))
    }
    @Test fun damagedFileWithSameSizeMustBeDownloadedAgain() {
        assertFalse(Mp3PackIntegrity.matches("abd".byteInputStream(), abcSha, 3))
    }
    @Test fun truncatedFileMustBeDownloadedAgain() {
        assertFalse(Mp3PackIntegrity.matches("ab".byteInputStream(), abcSha, 3))
    }
    @Test fun oversizedFileIsRejectedAndClosed() {
        var closed = false
        val input = object : ByteArrayInputStream("abc".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        assertFalse(Mp3PackIntegrity.matches(input, abcSha, 2))
        assertTrue(closed)
    }
}
