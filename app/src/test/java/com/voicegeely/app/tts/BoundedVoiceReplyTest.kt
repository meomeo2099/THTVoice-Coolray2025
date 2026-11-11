package com.voicegeely.app.tts

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class BoundedVoiceReplyTest {
    @Test fun successfulReplyKeepsItsResult() {
        val player = BoundedVoiceReply()
        try {
            assertTrue(player.speak { cancelled -> !cancelled() })
        } finally { player.close() }
    }

    @Test fun nativeReplyIgnoringInterruptionCannotKeepCallerOrQueueMoreReplies() {
        val release = CountDownLatch(1)
        val statuses = CopyOnWriteArrayList<String>()
        val cancelledAfterTimeout = AtomicBoolean(false)
        val interrupted = CountDownLatch(1)
        val secondActionRan = AtomicBoolean(false)
        val player = BoundedVoiceReply(timeoutMs = 500L, onFailure = { statuses.add(it) })
        try {
            assertFalse(player.speak { cancelled ->
                while (release.count > 0) {
                    try { release.await() } catch (_: InterruptedException) {
                        cancelledAfterTimeout.set(cancelled())
                        interrupted.countDown()
                    }
                }
                false
            })
            assertFalse(player.speak { secondActionRan.set(true); true })
            assertEquals(listOf("TIMEOUT", "BUSY"), statuses.toList())
            assertFalse(secondActionRan.get())
            assertTrue(interrupted.await(1, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(cancelledAfterTimeout.get())
        } finally {
            release.countDown()
            player.close()
        }
    }

    @Test fun backendExceptionIsReturnedAsFailedReply() {
        val statuses = CopyOnWriteArrayList<String>()
        val player = BoundedVoiceReply(onFailure = { statuses.add(it) })
        try {
            assertFalse(player.speak { throw IllegalStateException("engine unavailable") })
            assertEquals(listOf("IllegalStateException"), statuses.toList())
        } finally { player.close() }
    }
}
