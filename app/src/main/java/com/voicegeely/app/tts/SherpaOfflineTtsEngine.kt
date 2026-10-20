package com.voicegeely.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.voicegeely.app.settings.TtsVoice
import java.io.File

/** CPU-only Kokoro Vietnamese synthesis for command acknowledgements/results. */
class SherpaOfflineTtsEngine(
    private val context: Context,
    private val mutedSpeechGuard: MutedSpeechGuard = MutedSpeechGuard(AndroidMutedSpeechAudio(context))
) {
    data class InitResult(
        val ready: Boolean,
        val voice: TtsVoice,
        val detail: String? = null
    )

    private var tts: OfflineTts? = null
    private var textFrontend: KokoroVietnameseTextFrontend? = null
    @Volatile private var audioTrack: AudioTrack? = null
    /** Set by [interrupt] (another thread) to cut the current reply short. */
    @Volatile private var interrupted = false
    private var activeVoice: TtsVoice? = null
    /** [S03] Identifies the installed voice pack so a new pack never plays old cached audio. */
    private var packStamp: String = ""
    private val phraseCache = TtsPhraseCache(File(context.cacheDir, PHRASE_CACHE_DIR))
    @Volatile var lastFromCache = false
        private set
    @Volatile var lastDetail: String? = null
        private set

    val isReady: Boolean get() = tts != null && textFrontend != null

    @Synchronized
    fun initialize(voice: TtsVoice): InitResult {
        val speakerId = OfflineTtsModelSpec.speakerId(voice)
        tts?.let { existing ->
            return try {
                if (textFrontend != null && existing.numSpeakers() > speakerId) {
                    activeVoice = voice
                    lastDetail = null
                    InitResult(true, voice)
                } else {
                    releaseTtsOnly()
                    InitResult(false, voice, "Gói giọng đọc không có giọng ${OfflineTtsModelSpec.descriptor(voice).displayName}.")
                }
            } catch (t: Throwable) {
                releaseTtsOnly()
                InitResult(false, voice, t.message ?: "Không đọc được dữ liệu giọng.")
            }
        }

        val installed = OfflineTtsModelInstaller.ensureInstalled(context, voice)
        val files = installed.files
        if (!installed.available || files == null) {
            lastDetail = installed.detail
            return InitResult(false, voice, installed.detail)
        }

        val modelDir = installed.modelDir
        return try {
            val frontend = KokoroVietnameseTextFrontend.fromLexicon(File(modelDir, files.lexicon))
            // Sherpa only uses the lexicon for CJK text; feed it one-character aliases (see frontend).
            val sherpaLexicon = File(modelDir, SHERPA_LEXICON)
            writeIfChanged(sherpaLexicon, frontend.sherpaLexicon())
            val sherpaTokens = File(modelDir, SHERPA_TOKENS)
            writeIfChanged(sherpaTokens, KokoroVietnameseTextFrontend.sherpaTokens(File(modelDir, files.tokens).readText()))
            val kokoro = OfflineTtsKokoroModelConfig(
                model = File(modelDir, files.model).absolutePath,
                voices = File(modelDir, files.voices).absolutePath,
                tokens = sherpaTokens.absolutePath,
                dataDir = File(modelDir, files.dataDir).absolutePath,
                lexicon = sherpaLexicon.absolutePath,
                // Empty lang: Sherpa then uses the model's metadata voice, but only for non-CJK
                // text; the aliased text never reaches eSpeak.
                lang = "",
                lengthScale = 1.0f
            )
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = kokoro,
                    numThreads = THREADS,
                    debug = false,
                    provider = "cpu"
                )
            )
            val engine = OfflineTts(assetManager = null, config = config)
            val speakerCount = engine.numSpeakers()
            if (speakerCount <= speakerId || speakerCount < REQUIRED_SPEAKERS) {
                try { engine.release() } catch (_: Throwable) {}
                throw IllegalStateException("Kokoro speaker contract invalid: count=$speakerCount required=$REQUIRED_SPEAKERS")
            }
            tts = engine
            textFrontend = frontend
            packStamp = listOf(files.model, files.voices, files.lexicon, files.tokens).joinToString("|") { name ->
                File(modelDir, name).let { "$name:${it.length()}:${it.lastModified()}" }
            }
            activeVoice = voice
            lastDetail = null
            InitResult(true, voice)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to initialize Kokoro Vietnamese TTS", t)
            releaseTtsOnly()
            lastDetail = t.message ?: "Không khởi động được giọng đọc THT."
            InitResult(false, voice, lastDetail)
        }
    }

    /** Generates and plays one short response synchronously to avoid overlap. */
    /**
     * Stops the reply being spoken now (the driver pressed the voice key again). Not synchronized:
     * [speak] holds the lock while playing. The interrupted [speak] returns true (not a failure),
     * so no fallback voice starts.
     */
    fun interrupt() {
        interrupted = true
        val track = audioTrack ?: return
        try { track.pause(); track.flush() } catch (_: Throwable) {}
    }

    @Synchronized
    fun speak(text: String, voice: TtsVoice): Boolean {
        interrupted = false
        if (text.isBlank()) return true
        if (tts == null || textFrontend == null || activeVoice != voice) {
            if (!initialize(voice).ready) return false
        }
        val engine = tts ?: return false
        val frontend = textFrontend ?: return false
        val prepared = frontend.prepare(text)
        val spoken = prepared.speakableText
        if (!prepared.covered) {
            // Speak the covered words instead of going silent; log what was skipped.
            Log.w(TAG, "Kokoro lexicon missing (skipped): ${prepared.missingWords.joinToString(", ")}")
            if (spoken.isBlank()) {
                lastDetail = "Giọng đọc THT chưa đọc được một số từ: ${prepared.missingWords.joinToString(", ")}"
                return false
            }
        }

        return try {
            val sid = OfflineTtsModelSpec.speakerId(voice)
            lastDetail = null
            val modelText = frontend.modelText(prepared)
            val cacheOn = runCatching { com.voicegeely.app.settings.UserVoicePreferences(context).ttsPhraseCacheEnabled }.getOrDefault(true)
            val cacheKey = if (cacheOn && phraseCache.cacheable(text)) phraseCache.key(modelText, voice.name, sid, SPEED, packStamp) else null
            val cached = cacheKey?.let { phraseCache.get(it) }
            lastFromCache = cached != null
            val clip = cached ?: run {
                val audio = engine.generate(modelText, sid = sid, speed = SPEED)
                if (audio.samples.isEmpty() || audio.sampleRate <= 0) { lastDetail = "Giọng đọc THT không đọc được câu này."; return false }
                TtsPhraseCache.Clip(TtsPhraseCache.toPcm16(TtsTailTrim.trim(audio.samples, audio.sampleRate)), audio.sampleRate)
                    .also { generated -> cacheKey?.let { phraseCache.put(it, generated) } }
            }
            if (interrupted) return true
            // "Tắt tiếng" mutes music only; the assistant must still be heard.
            mutedSpeechGuard.speak { playBlocking(clip.pcm16, clip.frames, clip.sampleRate) }
                .also { played -> if (played) lastDetail = null else if (lastDetail == null) lastDetail = "Không phát được âm thanh giọng đọc." }
        } catch (t: Throwable) {
            Log.e(TAG, "Kokoro Vietnamese generation failed", t)
            lastDetail = t.message ?: "Lỗi giọng đọc THT."
            false
        }
    }

    /**
     * Streams the synthesized PCM and waits until it has been played.
     *
     * The previous MODE_STATIC version checked `state == STATE_INITIALIZED` before writing, but
     * a static track stays in STATE_NO_STATIC_DATA until data is written, so every reply was
     * rejected and the assistant was silent ("Không phát được âm thanh giọng đọc").
     */
    private fun playBlocking(pcm: ByteArray, expectedFrames: Int, sampleRate: Int): Boolean {
        stopAudioTrack()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(if (minBuffer > 0) minBuffer else 0, sampleRate / 5 * 2)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to create TTS AudioTrack", t)
            lastDetail = "Không tạo được luồng âm thanh: ${t.message.orEmpty()}"
            return false
        }

        audioTrack = track
        return try {
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                lastDetail = "Luồng âm thanh chưa sẵn sàng (state=${track.state})."
                return false
            }
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                if (interrupted) return true
                val count = minOf(bufferBytes, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (interrupted) return true
                if (written <= 0) {
                    lastDetail = "Không ghi được âm thanh (mã $written)."
                    return false
                }
                offset += written
            }
            val expectedMs = ((expectedFrames.toDouble() / sampleRate) * 1000.0).toLong()
            val deadline = System.currentTimeMillis() + (expectedMs + PLAYBACK_GRACE_MS).coerceAtMost(MAX_WAIT_MS)
            while (
                !interrupted &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < expectedFrames &&
                System.currentTimeMillis() < deadline
            ) {
                try { Thread.sleep(20) } catch (_: InterruptedException) { break }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to play TTS audio", t)
            lastDetail = "Lỗi phát âm thanh: ${t.message.orEmpty()}"
            false
        } finally {
            stopAudioTrack()
        }
    }

    private fun stopAudioTrack() {
        val track = audioTrack ?: return
        try { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() } catch (_: Throwable) {}
        try { track.release() } catch (_: Throwable) {}
        audioTrack = null
    }

    private fun releaseTtsOnly() {
        stopAudioTrack()
        try { tts?.release() } catch (_: Throwable) {}
        tts = null
        textFrontend = null
        activeVoice = null
    }

    @Synchronized fun close() = releaseTtsOnly()

    companion object {
        const val PHRASE_CACHE_DIR = "tts-phrases"
        /** Settings → "Lưu sẵn câu trả lời" turned off: delete the stored replies. */
        fun clearPhraseCache(context: Context) = TtsPhraseCache(File(context.cacheDir, PHRASE_CACHE_DIR)).clear()
        fun phraseCacheBytes(context: Context): Long = TtsPhraseCache(File(context.cacheDir, PHRASE_CACHE_DIR)).sizeBytes()
        private const val TAG = "THTOfflineTts"
        private const val SHERPA_LEXICON = "lexicon-vi-sherpa.txt"
        private const val SHERPA_TOKENS = "tokens-sherpa.txt"

        private fun writeIfChanged(file: File, text: String) {
            if (!file.isFile || file.readText() != text) file.writeText(text)
        }
        private const val THREADS = 2
        private const val SPEED = 1.0f
        private const val REQUIRED_SPEAKERS = 2
        private const val PLAYBACK_GRACE_MS = 1200L
        private const val MAX_WAIT_MS = 12_000L
    }
}
