package com.voicegeely.app.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Sherpa65mModelSpecTest {
    @Test
    fun discoversSimpleInt8Layout() {
        val files = Sherpa65mModelSpec.discover(
            listOf("encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt")
        )
        assertNotNull(files)
        assertEquals("encoder.int8.onnx", files!!.encoder)
        assertEquals("decoder.onnx", files.decoder)
        assertEquals("joiner.int8.onnx", files.joiner)
    }

    @Test
    fun discoversEpochAndChunkNamesWithoutHardCodingRelease() {
        val files = Sherpa65mModelSpec.discover(
            listOf(
                "encoder-epoch-20-avg-5-chunk-16-left-64.int8.onnx",
                "decoder-epoch-20-avg-5-chunk-16-left-64.onnx",
                "joiner-epoch-20-avg-5-chunk-16-left-64.int8.onnx",
                "tokens.txt"
            )
        )
        assertNotNull(files)
    }
}
