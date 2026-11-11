package com.voicegeely.app.tts

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** One speech worker with no queue. A stuck native voice cannot retain the recognition session. */
class BoundedVoiceReply(
    private val timeoutMs: Long = 10_000L,
    private val onFailure: (String) -> Unit = {}
) : AutoCloseable {
    private val worker = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>(),
        { action -> Thread(action, "THTVoice-Reply").apply { isDaemon = true } }
    )

    fun speak(action: (cancelled: () -> Boolean) -> Boolean): Boolean {
        val cancelled = AtomicBoolean(false)
        val task = FutureTask { action { cancelled.get() || Thread.currentThread().isInterrupted } }
        return try {
            worker.execute(task)
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            onFailure("TIMEOUT")
            false
        } catch (_: RejectedExecutionException) {
            onFailure("BUSY")
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (error: ExecutionException) {
            onFailure(error.cause?.javaClass?.simpleName ?: "FAILED")
            false
        } finally {
            cancelled.set(true)
            task.cancel(true)
        }
    }

    override fun close() { worker.shutdownNow() }
}
