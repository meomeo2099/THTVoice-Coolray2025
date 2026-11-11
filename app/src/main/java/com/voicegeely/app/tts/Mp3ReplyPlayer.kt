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

    fun speak(text: String, voice: TtsVoice, cancelled: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        if (cancelled()) return true
        val pack = Mp3VoicePacks.load(context, voice) ?: return false
        val ids = Mp3ReplyText.select(text, pack.index)
            ?: return false
        if (cancelled()) return true
        return mutedSpeechGuard.speak {
            Mp3PlaybackSequence.play(ids, cancelled) { id ->
                val data = pack.clips[id]
                data != null && play(data, cancelled)
            }
        }
    }

    fun interrupt() {
        if (Looper.myLooper() == Looper.getMainLooper()) finish()
        else main.post { finish() }
    }

    private fun finish() {
        runCatching { player?.release() }
        player = null
        waiting?.countDown()
        waiting = null
    }

    private fun play(data: ByteArray, cancelled: () -> Boolean): Boolean {
        val file = File.createTempFile("reply-", ".mp3", context.cacheDir)
        val done = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val aborted = AtomicBoolean(false)
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
            return done.await(8, TimeUnit.SECONDS) && (success.get() || cancelled())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return true
        } finally {
            aborted.set(true)
            main.post { if (waiting === done) finish() }
            file.delete()
        }
    }
}
