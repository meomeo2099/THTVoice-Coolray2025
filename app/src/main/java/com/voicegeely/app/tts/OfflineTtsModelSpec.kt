package com.voicegeely.app.tts

import com.voicegeely.app.settings.TtsVoice

/** Canonical shared Kokoro Vietnamese runtime used by both selectable voices. */
object OfflineTtsModelSpec {
    const val ASSET_ROOT = "models/tts/kokoro_vi"
    const val FILE_ROOT = "models/tts/kokoro_vi"

    const val MODEL = "kokoro_vi.onnx"
    const val VOICES = "voices.bin"
    const val TOKENS = "tokens.txt"
    const val LEXICON = "lexicon-vi.txt"
    const val CONFIG = "config.json"
    const val ESPEAK_DATA = "espeak-ng-data"
    const val VOICE_MAP = "voice-map.txt"
    const val SOURCE = "SOURCE.txt"

    data class VoiceDescriptor(
        val voice: TtsVoice,
        val id: String,
        val displayName: String,
        val speakerId: Int
    )

    data class Files(
        val model: String = MODEL,
        val voices: String = VOICES,
        val tokens: String = TOKENS,
        val lexicon: String = LEXICON,
        val config: String = CONFIG,
        val dataDir: String = ESPEAK_DATA
    )

    val voices: List<VoiceDescriptor> = listOf(
        VoiceDescriptor(TtsVoice.NGOC_HUYEN, "ngoc_huyen", "Ngọc Huyền – Nữ Bắc", speakerId = 0),
        VoiceDescriptor(TtsVoice.DIEM_TRINH, "diem_trinh", "Diễm Trinh – Nữ Nam", speakerId = 1)
    )

    fun descriptor(voice: TtsVoice): VoiceDescriptor = voices.first { it.voice == voice }
    fun speakerId(voice: TtsVoice): Int = descriptor(voice).speakerId

    @Suppress("UNUSED_PARAMETER")
    fun assetDir(voice: TtsVoice): String = ASSET_ROOT

    @Suppress("UNUSED_PARAMETER")
    fun fileDir(voice: TtsVoice): String = FILE_ROOT

    fun discover(fileNames: Collection<String>, hasEspeakData: Boolean): Files? {
        if (!hasEspeakData) return null
        val names = fileNames.map { it.substringAfterLast('/') }.toSet()
        val required = setOf(MODEL, VOICES, TOKENS, LEXICON, CONFIG, VOICE_MAP, SOURCE)
        if (!names.containsAll(required)) return null
        return Files()
    }
}
