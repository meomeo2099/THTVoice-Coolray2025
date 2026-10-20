package com.voicegeely.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Backup voice: the Android text-to-speech engine installed on the head unit/phone, used only
 * when the offline Kokoro voice is not ready or cannot say a reply. Requires a Vietnamese voice
 * in the system engine (e.g. Google TTS with the Vietnamese pack); otherwise it stays unavailable.
 */
class SystemTtsFallback(context: Context, private val mutedSpeechGuard: MutedSpeechGuard? = null) {
    @Volatile var isReady: Boolean = false
        private set
    @Volatile var detail: String = "Đang khởi tạo giọng hệ thống"
        private set

    private val pending = ConcurrentHashMap<String, CountDownLatch>()
    private val results = ConcurrentHashMap<String, Boolean>()
    private var tts: TextToSpeech? = null

    init {
        tts = runCatching {
            TextToSpeech(context.applicationContext) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    detail = "Không có trình đọc văn bản của hệ thống"
                    return@TextToSpeech
                }
                val vi = Locale("vi", "VN")
                val available = runCatching { engine.isLanguageAvailable(vi) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (available < TextToSpeech.LANG_AVAILABLE) {
                    detail = "Trình đọc hệ thống chưa có giọng tiếng Việt"
                    return@TextToSpeech
                }
                engine.language = vi
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) = finish(utteranceId, true)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) = finish(utteranceId, false)
                    override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId, false)
                    override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId, false)
                })
                isReady = true
                detail = "Giọng hệ thống Android (tiếng Việt)"
                Log.i(TAG, "system_tts_ready engine=${engine.defaultEngine}")
            }
        }.getOrNull()
    }

    private fun finish(id: String, ok: Boolean) {
        results[id] = ok
        pending.remove(id)?.countDown()
    }

    /** Speaks [text] and waits until it finished (bounded), so replies never overlap the mic. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (!isReady || engine == null || text.isBlank()) return false
        val id = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        pending[id] = latch
        val play = {
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
            if (queued) latch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)
            queued && results.remove(id) == true
        }
        return try {
            mutedSpeechGuard?.speak(play) ?: play()
        } catch (t: Throwable) {
            Log.w(TAG, "system_tts_failed type=${t.javaClass.simpleName}")
            false
        } finally {
            pending.remove(id)
        }
    }

    /** Stops the reply being spoken now (voice key pressed again); the waiting [speak] returns at once. */
    fun interrupt() {
        runCatching { tts?.stop() }
    }

    fun close() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "THTSystemTts"
        private const val MAX_WAIT_MS = 15_000L
    }
}
