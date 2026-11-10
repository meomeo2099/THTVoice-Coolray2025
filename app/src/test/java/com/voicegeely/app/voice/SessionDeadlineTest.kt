package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDeadlineTest {
    private class Clock {
        data class Job(val at: Long, val action: () -> Unit, var canceled: Boolean = false)
        var now = 0L
        val jobs = mutableListOf<Job>()
        fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            val job = Job(now + delay, action)
            jobs += job
            return { job.canceled = true }
        }
        fun advance(ms: Long) {
            now += ms
            val due = jobs.filter { it.at <= now && !it.canceled }
            due.forEach { it.canceled = true; it.action() }
        }
    }

    @Test fun stalledWorkerTimesOutWithoutAnotherKeyPress() {
        val clock = Clock()
        val expired = mutableListOf<Long>()
        val timer = SessionDeadline(clock::schedule) { expired.add(it) }
        timer.start(1, SessionWatchdogPolicy.STUCK_AFTER_MS)
        clock.advance(SessionWatchdogPolicy.STUCK_AFTER_MS - 1)
        assertTrue(expired.isEmpty())
        clock.advance(1)
        assertEquals(listOf(1L), expired)
        clock.advance(100_000)
        assertEquals(listOf(1L), expired)
    }

    @Test fun completedSessionDoesNotTimeOut() {
        val clock = Clock()
        val expired = mutableListOf<Long>()
        val timer = SessionDeadline(clock::schedule) { expired.add(it) }
        timer.start(1, 100)
        clock.advance(99)
        timer.cancel()
        clock.advance(100)
        assertTrue(expired.isEmpty())
    }

    @Test fun queuedOldCallbackCannotCancelNewSession() {
        val clock = Clock()
        val expired = mutableListOf<Long>()
        val timer = SessionDeadline(clock::schedule) { expired.add(it) }
        timer.start(1, 100)
        val oldCallback = clock.jobs.single().action
        clock.advance(50)
        timer.start(2, 100)
        oldCallback() // Simulates a callback already dequeued when cancellation happened.
        assertTrue(expired.isEmpty())
        clock.advance(100)
        assertEquals(listOf(2L), expired)
    }

    @Test fun duplicateCallbackExpiresOnlyOnceAndCancelInvalidatesQueuedWork() {
        val clock = Clock()
        val expired = mutableListOf<Long>()
        val timer = SessionDeadline(clock::schedule) { expired.add(it) }
        timer.start(1, 100)
        val callback = clock.jobs.single().action
        callback(); callback()
        assertEquals(listOf(1L), expired)
        timer.start(2, 100)
        val pending = clock.jobs.last().action
        timer.cancel()
        pending()
        assertEquals(listOf(1L), expired)
    }
}
