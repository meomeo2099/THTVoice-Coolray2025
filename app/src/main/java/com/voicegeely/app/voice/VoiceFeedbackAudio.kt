package com.voicegeely.app.voice


/** PCM for each cue (16-bit mono), generated in code so no audio asset is needed. */
object FeedbackToneSynth {
    const val SAMPLE_RATE = 22_050

    /** (frequency Hz, duration ms) segments per cue; 0 Hz is a short gap. */
    /**
     * Three distinct earcons (owner request 29/09/2026):
     * - start listening: rising two notes;
     * - end of listening / done: falling two notes;
     * - error: three low pulses.
     */
    fun segments(cue: VoiceFeedbackCue): List<Pair<Int, Int>> = when (cue) {
        VoiceFeedbackCue.LISTENING -> listOf(784 to 90, 0 to 20, 1175 to 140)
        VoiceFeedbackCue.PROCESSING,
        VoiceFeedbackCue.SUCCESS -> listOf(1175 to 90, 0 to 20, 784 to 150)
        VoiceFeedbackCue.ERROR -> listOf(349 to 110, 0 to 60, 349 to 110, 0 to 60, 262 to 230)
    }

    fun pcm(cue: VoiceFeedbackCue, amplitude: Float = 0.45f): ShortArray {
        val out = ArrayList<Short>()
        for ((hz, ms) in segments(cue)) {
            val n = SAMPLE_RATE * ms / 1000
            val fade = minOf(n / 4, SAMPLE_RATE / 200) // ~5 ms fade in/out avoids clicks
            for (i in 0 until n) {
                val env = when {
                    hz == 0 -> 0.0
                    i < fade -> i.toDouble() / fade
                    i >= n - fade -> (n - i).toDouble() / fade
                    else -> 1.0
                }
                val v = if (hz == 0) 0.0 else Math.sin(2.0 * Math.PI * hz * i / SAMPLE_RATE) * env * amplitude
                out += (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }
}

/**
 * Plays the owner's sound files (res/raw); the synthesized tones are only a fallback.
 * Plays the cues through an AudioTrack on the media usage — the same audio path as the
 * assistant voice — instead of ToneGenerator, which is silent on some head units/phones.
 * Playback is asynchronous and never blocks or fails a vehicle command.
 */
class VoiceFeedbackAudio(
    private val mutedSpeechGuard: com.voicegeely.app.tts.MutedSpeechGuard? = null,
    private val context: android.content.Context? = null
) {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "THTVoice-Cue").apply { isDaemon = true }
    }

    fun play(cue: VoiceFeedbackCue) {
        try {
            worker.execute { playBlocking(cue) }
        } catch (_: Throwable) {
            // Audio feedback must never block or fail a vehicle command.
        }
    }

    /** Waits (bounded) until queued sounds have finished, e.g. before music comes back up. */
    fun awaitIdle(timeoutMs: Long = MAX_SOUND_MS) {
        try {
            worker.submit { }.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
        }
    }

    private fun playBlocking(cue: VoiceFeedbackCue) {
        val sound = VoiceFeedbackSound.forCue(cue)
        val played = try {
            if (mutedSpeechGuard != null) mutedSpeechGuard.speak { playSoundFile(sound) } else playSoundFile(sound)
        } catch (_: Throwable) {
            false
        }
        if (!played) playTone(cue)
    }

    /** Plays res/raw/<sound> to the end (bounded wait); false if the file cannot be played. */
    private fun playSoundFile(sound: VoiceFeedbackSound): Boolean {
        val ctx = context ?: return false
        // Direct R references keep the files through resource shrinking.
        val resId = when (sound) {
            VoiceFeedbackSound.START -> com.tht.voice.R.raw.tht_cue_start
            VoiceFeedbackSound.STOP -> com.tht.voice.R.raw.tht_cue_stop
            VoiceFeedbackSound.SUCCESS -> com.tht.voice.R.raw.tht_cue_success
        }
        var player: android.media.MediaPlayer? = null
        return try {
            val done = java.util.concurrent.CountDownLatch(1)
            player = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                ctx.resources.openRawResourceFd(resId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                setOnCompletionListener { done.countDown() }
                setOnErrorListener { _, _, _ -> done.countDown(); true }
                prepare()
                start()
            }
            done.await(MAX_SOUND_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { player?.release() } catch (_: Throwable) {}
        }
    }

    private fun playTone(cue: VoiceFeedbackCue) {
        val pcm = FeedbackToneSynth.pcm(cue)
        val play = {
            var track: android.media.AudioTrack? = null
            try {
                track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(FeedbackToneSynth.SAMPLE_RATE)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val ms = pcm.size * 1000L / FeedbackToneSynth.SAMPLE_RATE
                Thread.sleep(ms + 60)
            } catch (_: Throwable) {
            } finally {
                try { track?.stop() } catch (_: Throwable) {}
                try { track?.release() } catch (_: Throwable) {}
            }
        }
        try {
            if (mutedSpeechGuard != null) mutedSpeechGuard.speak(play) else play()
        } catch (_: Throwable) {
        }
    }

    fun close() {
        try { worker.shutdownNow() } catch (_: Throwable) {}
    }

    private companion object {
        const val MAX_SOUND_MS = 3_000L
    }
}
