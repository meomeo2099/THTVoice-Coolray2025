package com.voicegeely.app.kws

import org.junit.Assert.*
import org.junit.Test

class WakeTrainingTest {
    @Test fun rejectedWakeHasNoUiOrMediaEffectsAndCooldownIsMediaOnly() {
        for (media in listOf(false, true)) {
            val effects = WakeSessionEffects(media)
            val calls = mutableListOf<String>()
            effects.finish({ calls += "ui" }, { calls += "release-or-pause" })
            assertTrue(calls.isEmpty())
            assertEquals(if (media) 2000L else 0L, effects.rejectionCooldownMs)
        }
    }
    @Test fun acceptedWakeActivatesAndCleansUpExactlyOnce() {
        val effects = WakeSessionEffects(true)
        val calls = mutableListOf<String>()
        repeat(2) { effects.activate({ calls += "ui" }, { calls += "acquire" }, { calls += "cue" }) }
        repeat(2) { effects.finish({ calls += "finish-ui" }, { calls += "release" }) }
        assertEquals(listOf("ui", "acquire", "cue", "finish-ui", "release"), calls)
    }

    @Test fun primitiveRingPreservesOrderAcrossWraparound() {
        val ring = WakePcmBuffer(5)
        ring.add(floatArrayOf(1f, 2f, 3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), ring.snapshot(), 0f)
        ring.add(floatArrayOf(4f, 5f, 6f, 7f))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f, 7f), ring.snapshot(), 0f)
        ring.add(floatArrayOf(8f))
        assertArrayEquals(floatArrayOf(4f, 5f, 6f, 7f, 8f), ring.snapshot(), 0f)
    }
    @Test fun primitiveRingKeepsExactTailOfOversizedChunk() {
        val ring = WakePcmBuffer(3)
        ring.add(floatArrayOf(9f))
        ring.add(floatArrayOf(1f, 2f, 3f, 4f, 5f))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f), ring.snapshot(), 0f)
        ring.add(floatArrayOf(6f, 7f, 8f))
        assertArrayEquals(floatArrayOf(6f, 7f, 8f), ring.snapshot(), 0f)
    }
    @Test fun primitiveRingSnapshotIsIndependentAndDrainAppendsCurrentFrame() {
        val ring = WakePcmBuffer(3)
        ring.add(floatArrayOf(1f, 2f, 3f, 4f))
        val snapshot = ring.snapshot()
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f, 6f), ring.drainWith(floatArrayOf(5f, 6f)), 0f)
        assertTrue(ring.snapshot().isEmpty())
        ring.add(floatArrayOf(7f))
        assertArrayEquals(floatArrayOf(2f, 3f, 4f), snapshot, 0f)
        ring.clear()
        assertArrayEquals(floatArrayOf(8f), ring.drainWith(floatArrayOf(8f)), 0f)
    }

    @Test fun learnedLinesNeverCrossProfile() {
        val lines = listOf(KeywordLines.Line(listOf("h", "a", "b"), 2f, .2f, "HEY_GEELY_USER"),
            KeywordLines.Line(listOf("h", "c", "d"), 2f, .2f, "HELLO_GEELY_USER"))
        assertEquals(1, KeywordLines.forProfile(lines, WakePhrase.HEY_GEELY).size)
        assertEquals("HEY_GEELY_USER", KeywordLines.forProfile(lines, WakePhrase.HEY_GEELY).single().label)
        assertTrue(KeywordLines.forProfile(lines, WakePhrase.HEY_THT).isEmpty())
    }
    @Test fun mediaNeverAcceptsBlankThtConfirmation() {
        assertEquals(WakeVerifier.Verdict.REJECT, WakeVerifier.verify(WakePhrase.HEY_THT, "", false))
        assertEquals(WakeVerifier.Verdict.REJECT, WakeVerifier.verify(WakePhrase.HEY_THT, "bai hat hom nay", false))
        assertEquals(WakeVerifier.Verdict.ACCEPT, WakeVerifier.verify(WakePhrase.HEY_THT, "hey tht", false))
    }
    @Test fun keywordLinesRoundTrip() {
        val l = KeywordLines.parse("h ēi g ī l ì :2.25 @HEY_GEELY")!!
        assertEquals(listOf("h", "ēi", "g", "ī", "l", "ì"), l.tokens)
        assertEquals(2.25f, l.boost!!, 0.001f)
        assertNull(l.threshold)
        assertEquals("HEY_GEELY", l.label)
        assertEquals("h ēi g ī l ì :2.25 @HEY_GEELY", l.format())
        assertNull(KeywordLines.parse("no label"))
        assertNull(KeywordLines.parse("@ONLY"))
        val two = KeywordLines.parseAll("a b @X\n\nc d :1.50 #0.20 @Y\n")
        assertEquals("a b @X/c d :1.50 #0.20 @Y", KeywordLines.streamKeywords(two))
        val len = KeywordLines.lenient(two, 0.10f, 0.5f, 2.5f)
        assertEquals(0.10f, len[0].threshold!!, 0.001f); assertEquals(3.0f, len[0].boost!!, 0.001f)
        assertEquals(0.10f, len[1].threshold!!, 0.001f); assertEquals(2.0f, len[1].boost!!, 0.001f)
    }

    @Test fun enrollmentKeepsLinesHeardInOtherTakes() {
        val takeA = listOf("h", "ēi", "j", "ī", "l", "ì")
        val takeB = listOf("h", "ēi", "g", "ī", "l", "ì")
        val odd = listOf("m", "ā")  // too short, ignored
        val decoded = listOf(takeA, takeA, takeB, odd, takeA)
        // The spotter "hears" a line in a take when the take decoded to the same tokens.
        val r = WakeEnrollment.learn(decoded, "HEY_GEELY_USER") { line, take -> decoded[take] == line.tokens }
        assertTrue(r is WakeEnrollment.Result.Learned)
        val learned = r as WakeEnrollment.Result.Learned
        assertEquals(listOf(takeA), learned.lines.map { it.tokens })   // takeB heard in no other take
        assertEquals("HEY_GEELY_USER", learned.lines[0].label)
        assertEquals(3, learned.takesHeard)
    }

    @Test fun enrollmentFailsWithoutEnoughSpeech() {
        val r = WakeEnrollment.learn(listOf(emptyList(), emptyList(), listOf("h", "ēi", "l")), "X") { _, _ -> true }
        assertTrue(r is WakeEnrollment.Result.Failed)
        val none = WakeEnrollment.learn(List(5) { listOf("a$it", "b", "c") }, "X") { _, _ -> false }
        assertTrue(none is WakeEnrollment.Result.Failed)
    }

    @Test fun firstSpeechChunkAfterCueIsRetained() {
        val seg = WakeTakeSegmenter(endSilenceMs = 400)
        val quiet = ShortArray(1280) { 20 }
        repeat(4) { seg.feed(quiet, quiet.size) }
        assertTrue(seg.finishCalibration())
        val firstSyllable = ShortArray(1280) { 7000 }
        val rest = ShortArray(1280) { 4000 }
        seg.feed(firstSyllable, firstSyllable.size)
        repeat(8) { seg.feed(rest, rest.size) }
        repeat(6) { seg.feed(quiet, quiet.size) }
        assertTrue(seg.done)
        assertTrue(seg.take().take(1280).all { it == 7000.toShort() })
    }
    @Test fun segmenterCutsOneTake() {
        val seg = WakeTakeSegmenter(maxSpeechMs = 2_500, endSilenceMs = 400, waitMs = 3_000)
        val quiet = ShortArray(1280) { if (it % 2 == 0) 20 else -20 }
        val loud = ShortArray(1280) { if (it % 2 == 0) 6000 else -6000 }
        repeat(5) { seg.feed(quiet, quiet.size) }      // calibration + lead-in
        repeat(10) { seg.feed(loud, loud.size) }       // 0.8 s of speech
        repeat(8) { seg.feed(quiet, quiet.size) }      // silence ends it
        assertTrue(seg.done); assertFalse(seg.timedOut)
        val take = seg.take()
        assertTrue(take.size >= 10 * 1280)
        assertTrue(take.size < 20 * 1280)
        val silent = WakeTakeSegmenter(waitMs = 1_000)
        repeat(20) { silent.feed(quiet, quiet.size) }
        assertTrue(silent.done); assertTrue(silent.timedOut); assertEquals(0, silent.take().size)
    }

    @Test fun mediaGateRules() {
        assertTrue(MediaWakeGate.acceptBlank(WakeSensitivity.HIGH, media = false))
        assertFalse(MediaWakeGate.acceptBlank(WakeSensitivity.HIGH, media = true))
        assertEquals(WakeSensitivity.NORMAL.gateMarginDb + 3.0, MediaWakeGate.gateMarginDb(WakeSensitivity.NORMAL, true), 0.0)
        assertEquals(WakeSensitivity.NORMAL.gateMarginDb, MediaWakeGate.gateMarginDb(WakeSensitivity.NORMAL, false), 0.0)
        assertEquals(2_000L, MediaWakeGate.refractoryAfterRejectMs(true))
        assertEquals(0L, MediaWakeGate.refractoryAfterRejectMs(false))
        assertFalse(MediaWakeGate.allowSecondChance(true))
        assertEquals(WakeVerifier.Verdict.REJECT, WakeVerifier.verify(WakePhrase.HEY_GEELY, "", acceptBlank = false))
        assertEquals(WakeVerifier.Verdict.ACCEPT, WakeVerifier.verify(WakePhrase.HEY_GEELY, "", acceptBlank = true))
    }

    @Test fun secondChanceOpensAfterWakeLengthBurst() {
        var now = 0L
        val sc = WakeSecondChance { now }
        fun chunk(speech: Boolean, ms: Long = 80) { sc.onChunk(speech, false, true).also { now += ms } }
        // 0.8 s burst, then silence -> window opens.
        repeat(10) { chunk(true) }
        var opened = false
        repeat(5) { if (sc.onChunk(false, false, true)) opened = true; now += 80 }
        assertTrue(opened); assertTrue(sc.active); assertEquals(1, sc.nearMisses)
        now += WakeSecondChance.WINDOW_MS
        assertFalse(sc.active)
        // A 3 s monologue is not a wake attempt.
        repeat(40) { chunk(true) }
        var long = false
        repeat(5) { if (sc.onChunk(false, false, true)) long = true; now += 80 }
        assertFalse(long)
        // Not allowed under media.
        repeat(10) { chunk(true) }
        var media = false
        repeat(5) { if (sc.onChunk(false, false, false)) media = true; now += 80 }
        assertFalse(media)
        // A detection closes the window.
        repeat(10) { chunk(true) }
        repeat(5) { sc.onChunk(false, false, true); now += 80 }
        assertTrue(sc.active)
        sc.onChunk(true, true, true)
        assertFalse(sc.active)
    }
}
