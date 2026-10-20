package com.voicegeely.app.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePackageVerifierTest {
    @Test fun sha256MatchesKnownVector() {
        val file = File.createTempFile("thtvoice-update", ".apk")
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UpdatePackageVerifier.sha256(file)
            )
        } finally {
            file.delete()
        }
    }

    @Test fun sha256MetadataIsNormalizedButMalformedValuesFailClosed() {
        val upper = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        assertEquals(upper.lowercase(), UpdatePackageVerifier.normalizeSha256("  $upper  "))
        assertNull(UpdatePackageVerifier.normalizeSha256("abc"))
        assertNull(UpdatePackageVerifier.normalizeSha256("g".repeat(64)))
    }
}
