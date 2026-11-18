package com.voicegeely.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.voicegeely.app.settings.TtsVoice
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class Mp3ReplyPlayer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val mutedSpeechGuard = MutedSpeechGuard(AndroidMutedSpeechAudio(context))
    // Player ownership and release are confined to main. The recognition worker waits off-main.
    private var player: MediaPlayer? = null
    private var waiting: CountDownLatch? = null
    private val playerCleanup = java.util.concurrent.atomic.AtomicInteger()

    private val playbackLock = Any()
    fun speak(text: String, voice: TtsVoice, cancelled: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        return synchronized(playbackLock) { speakLocked(text, voice, cancelled) }
    }

    private fun speakLocked(text: String, voice: TtsVoice, cancelled: () -> Boolean): Boolean {
        // A cancelled async MediaPlayer may still be retiring on main. Consume this request
        // until release completes, so neither a new PCM reply nor caller fallback overlaps it.
        if (playerCleanup.get() != 0) return true
        val generation = interrupted.get()
        val externalCancelled = cancelled
        val speechDeadline = android.os.SystemClock.elapsedRealtime() + 30_000
        val cancelled = { externalCancelled() || interrupted.get() != generation ||
            android.os.SystemClock.elapsedRealtime() >= speechDeadline }
        if (cancelled()) return true
        val pack = Mp3VoicePacks.load(context, voice) ?: return false
        val ids = Mp3ReplyText.select(text, pack.index)
            ?: return false
        if (cancelled()) return true
        // One continuous buffer (no gap between clips); per-clip players only if decoding fails.
        val joined = if (ids.size > 1) runCatching { joinClips(pack, ids, cancelled) }.getOrNull() else null
        if (cancelled()) return true
        if (joined != null) return mutedSpeechGuard.speak { playPcm(joined, cancelled) }
        return mutedSpeechGuard.speak {
            Mp3PlaybackSequence.play(ids, cancelled) { id ->
                val data = pack.clips[id]
                data != null && play(data, cancelled, pack.extension(id))
            }
        }
    }

    private val interrupted = java.util.concurrent.atomic.AtomicLong()
    private val decoded = object : LinkedHashMap<String, ClipDecoder.Pcm>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ClipDecoder.Pcm>?): Boolean = size > 120
    }
    private fun joinClips(pack: Mp3VoicePacks.Pack, ids: List<String>, cancelled: () -> Boolean): ClipDecoder.Pcm? {
        if (ids.size > 32) return null
        val pcms = mutableListOf<ClipDecoder.Pcm>()
        var total = 0
        val deadline = android.os.SystemClock.elapsedRealtime() + 8_000
        val stopped = { cancelled() || android.os.SystemClock.elapsedRealtime() >= deadline }
        for (id in ids) {
            if (stopped()) return null
            // THTVoice: keep recently decoded clips, so common replies do not decode again (faster reply).
            val key = "${pack.version}/$id"
            val pcm = synchronized(decoded) { decoded[key] }
                ?: (pack.clips[id]?.let { ClipDecoder.decode(it, stopped) } ?: return null)
                    .also { synchronized(decoded) { decoded[key] = it } }
            total += pcm.samples.size
            if (total > 2_000_000) return null
            pcms.add(pcm)
        }
        val rate = pcms.first().sampleRate
        if (pcms.any { it.sampleRate != rate }) return null
        val ends = ids.map { pack.isSentence(it) }
        return ClipDecoder.Pcm(PcmJoin.join(pcms.map { it.samples }, ends, rate), rate)
    }

    @Volatile private var track: android.media.AudioTrack? = null

    private fun playPcm(pcm: ClipDecoder.Pcm, cancelled: () -> Boolean): Boolean {
        val t = runCatching {
            android.media.AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.samples.size * 2)
                .build()
        }.getOrNull() ?: return false
        track = t
        try {
            if (cancelled()) return true
            if (t.write(pcm.samples, 0, pcm.samples.size) != pcm.samples.size) return false
            if (cancelled()) return true
            t.play()
            val end = android.os.SystemClock.elapsedRealtime() + pcm.samples.size * 1000L / pcm.sampleRate + 1_500
            while (android.os.SystemClock.elapsedRealtime() < end) {
                if (cancelled() || Thread.currentThread().isInterrupted) return true
                if ((t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) >= pcm.samples.size) return true
                Thread.sleep(25)
            }
            return false
        } catch (_: Throwable) {
            return false
        } finally {
            runCatching { t.stop() }; runCatching { t.release() }
            if (track === t) track = null
        }
    }

    fun interrupt() {
        interrupted.incrementAndGet()
        runCatching { track?.pause(); track?.flush() }
        if (Looper.myLooper() == Looper.getMainLooper()) finish()
        else main.post { finish() }
    }

    private fun finish() {
        runCatching { player?.release() }
        player = null
        waiting?.countDown()
        waiting = null
    }

    private fun play(data: ByteArray, cancelled: () -> Boolean, extension: String = "mp3"): Boolean {
        val file = File.createTempFile("reply-", ".$extension", context.cacheDir)
        val done = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val aborted = AtomicBoolean(false)
        playerCleanup.incrementAndGet()
        try {
            file.writeBytes(data)
            main.post {
                if (cancelled() || aborted.get()) {
                    done.countDown()
                    return@post
                }
                finish()
                waiting = done
                try {
                    val mp = MediaPlayer()
                    player = mp
                    mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    FileInputStream(file).use { mp.setDataSource(it.fd) }
                    mp.setOnPreparedListener {
                        if (player === it && !cancelled() && !aborted.get()) {
                            runCatching { it.start() }.onFailure { finish() }
                        } else if (player === it) finish()
                    }
                    mp.setOnCompletionListener { if (player === it) { success.set(true); finish() } }
                    mp.setOnErrorListener { source, _, _ -> if (player === source) finish(); true }
                    mp.prepareAsync()
                } catch (_: Exception) {
                    finish()
                }
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 8_000
            while (!done.await(25, TimeUnit.MILLISECONDS)) {
                if (cancelled()) return true
                if (android.os.SystemClock.elapsedRealtime() >= deadline) return false
            }
            return success.get() || cancelled()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return true
        } finally {
            aborted.set(true)
            main.post {
                if (waiting === done) finish()
                playerCleanup.decrementAndGet()
            }
            file.delete()
        }
    }
}
