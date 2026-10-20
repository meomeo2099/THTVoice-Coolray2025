package com.voicegeely.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTailTrimTest {
    private val rate = 24_000
    private fun ms(v: Int) = rate * v / 1000
    /** Segments of (durationMs, amplitude); amplitude 0 = silence. */
    private fun signal(vararg parts: Pair<Int, Float>): FloatArray {
        val out = ArrayList<Float>()
        parts.forEach { (d, a) -> repeat(ms(d)) { i -> out += if (a == 0f) 0f else a * kotlin.math.sin(i * 0.3f) } }
        return out.toFloatArray()
    }

    @Test fun shortBurstAfterPauseRemoved() {
        val s = signal(600 to 0.5f, 200 to 0f, 150 to 0.4f, 300 to 0f)
        val out = TtsTailTrim.trim(s, rate)
        assertTrue("cut near end of speech: ${out.size}", out.size <= ms(600 + TtsTailTrim.KEEP_AFTER_MS + 10))
        assertTrue(out.size >= ms(600))
    }

    @Test fun realLastWordAfterCommaKept() {
        val s = signal(600 to 0.5f, 200 to 0f, 300 to 0.4f, 100 to 0f)
        val out = TtsTailTrim.trim(s, rate)
        assertTrue(out.size >= ms(1100))
    }

    @Test fun shortPauseInsideSpeechKept() {
        val s = signal(600 to 0.5f, 100 to 0f, 150 to 0.4f, 100 to 0f)
        assertTrue(TtsTailTrim.trim(s, rate).size >= ms(850))
    }

    @Test fun trailingSilenceDroppedAndFadedOut() {
        val s = signal(500 to 0.5f, 800 to 0f)
        val out = TtsTailTrim.trim(s, rate)
        assertTrue(out.size <= ms(500 + TtsTailTrim.KEEP_AFTER_MS + 10))
        assertEquals(0f, out.last(), 1e-6f)
    }

    @Test fun speechBeforeTailUnchanged() {
        val s = signal(600 to 0.5f, 200 to 0f, 150 to 0.4f)
        val out = TtsTailTrim.trim(s, rate)
        for (i in 0 until ms(500)) assertEquals(s[i], out[i], 0f)
    }

    @Test fun emptyOrSilentSafe() {
        assertEquals(0, TtsTailTrim.trim(FloatArray(0), rate).size)
        val silent = FloatArray(1000)
        assertEquals(1000, TtsTailTrim.trim(silent, rate).size)
    }
}

/** Real Kokoro output from tools/tts-probe (12/10/2026): the extra burst at the end must go, words stay. */
class TtsTailTrimRealAudioTest {
    private fun wav(name: String): Pair<FloatArray, Int> {
        val b = (javaClass.classLoader.getResourceAsStream("tts/$name")
            ?: java.io.File("src/test/resources/tts/$name").inputStream()).readBytes()
        var i = 12; var rate = 0
        while (i < b.size - 8) {
            val id = String(b, i, 4)
            val len = (b[i + 4].toInt() and 0xff) or ((b[i + 5].toInt() and 0xff) shl 8) or ((b[i + 6].toInt() and 0xff) shl 16) or ((b[i + 7].toInt() and 0xff) shl 24)
            if (id == "fmt ") rate = (b[i + 12].toInt() and 0xff) or ((b[i + 13].toInt() and 0xff) shl 8) or ((b[i + 14].toInt() and 0xff) shl 16)
            if (id == "data") {
                val off = i + 8
                return FloatArray(len / 2) { k -> ((b[off + 2 * k].toInt() and 0xff) or (b[off + 2 * k + 1].toInt() shl 8)).toShort() / 32768f } to rate
            }
            i += 8 + len
        }
        error("no data chunk")
    }

    private fun check(name: String, speechEndMs: Int, burstStartMs: Int) {
        val (x, rate) = wav(name)
        val outMs = TtsTailTrim.trim(x, rate).size * 1000 / rate
        assertTrue("$name keeps the last word: $outMs", outMs >= speechEndMs)
        assertTrue("$name drops the end burst: $outMs", outMs < burstStartMs)
    }

    @Test fun daBatDieuHoa() = check("kokoro-da-bat-dieu-hoa.wav", 920, 1110)
    @Test fun daChinhNhietDo() = check("kokoro-da-chinh-nhiet-do.wav", 1110, 1350)

    @Test fun cleanEndingKeepsAllSpeech() {
        val (x, rate) = wav("kokoro-da-tat-dieu-hoa.wav")
        assertTrue(TtsTailTrim.trim(x, rate).size * 1000 / rate >= 1010)
    }
}
