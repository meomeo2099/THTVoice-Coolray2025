package com.voicegeely.app.settings

import android.content.Context
import com.voicegeely.app.asr.AsrEndpointConfig
import com.voicegeely.app.asr.AsrEndpointRuntime
import com.voicegeely.app.kws.WakePhrase

enum class TtsVoice {
    NGOC_HUYEN,
    DIEM_TRINH
}

enum class DefaultNavigationApp {
    AUTO,
    GOOGLE_MAPS,
    VIETMAP,
    WAZE
}

enum class MicSensitivity {
    LOW,
    NORMAL,
    HIGH
}

/** What happens to the music while the assistant listens and answers (owner request 01/10/2026). */
enum class MusicWhileListening {
    /** Turn the music down (default). */
    DUCK,
    /** Pause the music; the player resumes on its own when the assistant is done. */
    PAUSE
}

enum class OverlayPosition {
    TOP,
    CENTER,
    BOTTOM
}

class UserVoicePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        syncAsrEndpointRuntime()
    }

    /** Wake-word sensitivity (THTVoice): Cao = a quiet call still wakes, but more false wakes with music. */
    var wakeSensitivity: com.voicegeely.app.kws.WakeSensitivity
        get() = prefs.getString(KEY_WAKE_SENSITIVITY, com.voicegeely.app.kws.WakeSensitivity.NORMAL.name)
            ?.let { runCatching { com.voicegeely.app.kws.WakeSensitivity.valueOf(it) }.getOrNull() }
            ?: com.voicegeely.app.kws.WakeSensitivity.NORMAL
        set(value) = prefs.edit().putString(KEY_WAKE_SENSITIVITY, value.name).apply()

    /** Coolray 2025 quiet mic: amplify x2 before recognition (on by default, as before). */
    var micGainEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIC_GAIN, true)
        set(value) = prefs.edit().putBoolean(KEY_MIC_GAIN, value).apply()

    /** GTCRN noise reduction before recognition (on by default, as before). Off when it distorts speech. */
    var micDenoiseEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIC_DENOISE, true)
        set(value) = prefs.edit().putBoolean(KEY_MIC_DENOISE, value).apply()

    var voiceFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_FEEDBACK, value).apply()

    var musicWhileListening: MusicWhileListening
        get() = prefs.getString(KEY_MUSIC_WHILE_LISTENING, MusicWhileListening.DUCK.name)
            ?.let { runCatching { MusicWhileListening.valueOf(it) }.getOrNull() }
            ?: MusicWhileListening.DUCK
        set(value) = prefs.edit().putString(KEY_MUSIC_WHILE_LISTENING, value.name).apply()

    /** Voice key while Android Auto / CarPlay runs: true = always THT Voice (default). */
    var voiceKeyAlwaysTht: Boolean
        get() = prefs.getBoolean(KEY_VOICE_KEY_ALWAYS_THT, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_KEY_ALWAYS_THT, value).apply()

    /** [REF-02] Also open THT Voice from the car's voice-key signal (VHAL read callback). Off by default: unverified. */
    var voiceKeyVehicleSignal: Boolean
        get() = prefs.getBoolean(KEY_VOICE_KEY_VEHICLE_SIGNAL, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_KEY_VEHICLE_SIGNAL, value).apply()

    /** [S03] Settings → "Lưu sẵn câu trả lời": keep synthesized replies on disk (max 24 MB). */
    var ttsPhraseCacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_PHRASE_CACHE, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_PHRASE_CACHE, value).apply()

    /** [S05] Taught phrases, "phrase\ttarget" per line (see UserAliasBook). Local only. */
    var userAliases: String
        get() = prefs.getString(KEY_USER_ALIASES, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_ALIASES, value).apply()

    fun aliasBook(): com.voicegeely.app.voice.UserAliasBook =
        com.voicegeely.app.voice.UserAliasBook().apply { load(userAliases) }

    /** "Đảo hướng gió định kỳ": minutes between face ↔ feet swings when the command names none. */
    var airflowCycleMinutes: Int
        get() = prefs.getInt(KEY_AIRFLOW_CYCLE_MINUTES, 1).coerceIn(1, 10)
        set(value) = prefs.edit().putInt(KEY_AIRFLOW_CYCLE_MINUTES, value.coerceIn(1, 10)).apply()

    /** Token from THT Split → Kết nối THT Voice; only used when the signature permission is absent. Never logged. */
    var splitToken: String?
        get() = prefs.getString(KEY_SPLIT_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_SPLIT_TOKEN, value?.trim()?.takeIf { it.isNotEmpty() }).apply()

    /** "Lọc tiếng nhạc khi nghe lệnh": echo canceller only while music or a reply plays. */
    var echoCancelWhilePlaying: Boolean
        get() = prefs.getBoolean(KEY_ECHO_CANCEL, true)
        set(value) = prefs.edit().putBoolean(KEY_ECHO_CANCEL, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var wakePhrase: WakePhrase
        get() = prefs.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE.name)
            ?.let { runCatching { WakePhrase.valueOf(it) }.getOrNull() }
            ?: DEFAULT_WAKE_PHRASE
        set(value) = prefs.edit().putString(KEY_WAKE_PHRASE, value.name).apply()

    var ttsVoice: TtsVoice
        get() = prefs.getString(KEY_TTS_VOICE, TtsVoice.NGOC_HUYEN.name)
            ?.let { runCatching { TtsVoice.valueOf(it) }.getOrNull() }
            ?: TtsVoice.NGOC_HUYEN
        set(value) = prefs.edit().putString(KEY_TTS_VOICE, value.name).apply()

    var defaultNavigationApp: DefaultNavigationApp
        get() = prefs.getString(KEY_DEFAULT_NAV, DefaultNavigationApp.AUTO.name)
            ?.let { runCatching { DefaultNavigationApp.valueOf(it) }.getOrNull() }
            ?: DefaultNavigationApp.AUTO
        set(value) = prefs.edit().putString(KEY_DEFAULT_NAV, value.name).apply()

    var micSensitivity: MicSensitivity
        get() = prefs.getString(KEY_MIC_SENSITIVITY, MicSensitivity.NORMAL.name)
            ?.let { runCatching { MicSensitivity.valueOf(it) }.getOrNull() }
            ?: MicSensitivity.NORMAL
        set(value) {
            prefs.edit().putString(KEY_MIC_SENSITIVITY, value.name).apply()
            syncAsrEndpointRuntime()
        }

    var endpointSilenceMs: Long
        get() = prefs.getLong(KEY_ENDPOINT_SILENCE_MS, DEFAULT_ENDPOINT_SILENCE_MS)
            .coerceIn(AsrEndpointConfig.MIN_TRAILING_SILENCE_MS, AsrEndpointConfig.MAX_TRAILING_SILENCE_MS)
        set(value) {
            prefs.edit().putLong(
                KEY_ENDPOINT_SILENCE_MS,
                value.coerceIn(AsrEndpointConfig.MIN_TRAILING_SILENCE_MS, AsrEndpointConfig.MAX_TRAILING_SILENCE_MS)
            ).apply()
            syncAsrEndpointRuntime()
        }

    var maxListenMs: Long
        get() = prefs.getLong(KEY_MAX_LISTEN_MS, DEFAULT_MAX_LISTEN_MS)
            .coerceIn(AsrEndpointConfig.MIN_MAX_LISTEN_MS, AsrEndpointConfig.MAX_MAX_LISTEN_MS)
        set(value) {
            prefs.edit().putLong(
                KEY_MAX_LISTEN_MS,
                value.coerceIn(AsrEndpointConfig.MIN_MAX_LISTEN_MS, AsrEndpointConfig.MAX_MAX_LISTEN_MS)
            ).apply()
            syncAsrEndpointRuntime()
        }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var overlayPosition: OverlayPosition
        get() = prefs.getString(KEY_OVERLAY_POSITION, OverlayPosition.BOTTOM.name)
            ?.let { runCatching { OverlayPosition.valueOf(it) }.getOrNull() }
            ?: OverlayPosition.BOTTOM
        set(value) {
            prefs.edit()
                .putString(KEY_OVERLAY_POSITION, value.name)
                .remove(KEY_OVERLAY_CUSTOM_X)
                .remove(KEY_OVERLAY_CUSTOM_Y)
                .apply()
        }

    var overlayAutoHideMs: Long
        get() = prefs.getLong(KEY_OVERLAY_AUTO_HIDE_MS, DEFAULT_OVERLAY_AUTO_HIDE_MS)
            .coerceIn(MIN_OVERLAY_AUTO_HIDE_MS, MAX_OVERLAY_AUTO_HIDE_MS)
        set(value) = prefs.edit().putLong(
            KEY_OVERLAY_AUTO_HIDE_MS,
            value.coerceIn(MIN_OVERLAY_AUTO_HIDE_MS, MAX_OVERLAY_AUTO_HIDE_MS)
        ).apply()

    var overlayDragEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_DRAG_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_DRAG_ENABLED, value).apply()

    val overlayCustomPosition: Pair<Int, Int>?
        get() {
            if (!prefs.contains(KEY_OVERLAY_CUSTOM_X) || !prefs.contains(KEY_OVERLAY_CUSTOM_Y)) return null
            return prefs.getInt(KEY_OVERLAY_CUSTOM_X, 0) to prefs.getInt(KEY_OVERLAY_CUSTOM_Y, 0)
        }

    fun saveOverlayCustomPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_OVERLAY_CUSTOM_X, x.coerceAtLeast(0))
            .putInt(KEY_OVERLAY_CUSTOM_Y, y.coerceAtLeast(0))
            .apply()
    }

    fun clearOverlayCustomPosition() {
        prefs.edit().remove(KEY_OVERLAY_CUSTOM_X).remove(KEY_OVERLAY_CUSTOM_Y).apply()
    }

    fun resetToDefaults() {
        wakeWordEnabled = true
        wakePhrase = DEFAULT_WAKE_PHRASE
        voiceFeedbackEnabled = true
        musicWhileListening = MusicWhileListening.DUCK
        voiceKeyAlwaysTht = true
        voiceKeyVehicleSignal = false
        ttsPhraseCacheEnabled = true
        airflowCycleMinutes = 1
        echoCancelWhilePlaying = true
        micSensitivity = MicSensitivity.NORMAL
        endpointSilenceMs = DEFAULT_ENDPOINT_SILENCE_MS
        maxListenMs = DEFAULT_MAX_LISTEN_MS
        ttsVoice = TtsVoice.NGOC_HUYEN
        defaultNavigationApp = DefaultNavigationApp.AUTO
        overlayEnabled = true
        overlayAutoHideMs = DEFAULT_OVERLAY_AUTO_HIDE_MS
        overlayDragEnabled = true
        overlayPosition = OverlayPosition.BOTTOM
        clearOverlayCustomPosition()
    }

    fun asrEndpointConfig(): AsrEndpointConfig = AsrEndpointConfig(
        speechRmsThreshold = when (micSensitivity) {
            MicSensitivity.LOW -> 0.018f
            MicSensitivity.NORMAL -> AsrEndpointConfig.DEFAULT_SPEECH_RMS
            MicSensitivity.HIGH -> 0.008f
        },
        trailingSilenceMs = endpointSilenceMs,
        minSpeechMs = AsrEndpointConfig.DEFAULT_MIN_SPEECH_MS,
        maxListenMs = maxListenMs
    ).normalized()

    private fun syncAsrEndpointRuntime() {
        AsrEndpointRuntime.update(asrEndpointConfig())
    }

    companion object {
        /** Owner decision 03/10/2026: "Hey Geely" is the default wake phrase. */
        val DEFAULT_WAKE_PHRASE = WakePhrase.HEY_GEELY

        // App defaults (owner decision 01/10/2026): stop after 0,5 s of silence, listen at most
        // 8 s, popup hides after 1 s. Saved user choices are kept.
        const val DEFAULT_ENDPOINT_SILENCE_MS = 500L
        const val DEFAULT_MAX_LISTEN_MS = 8_000L
        const val DEFAULT_OVERLAY_AUTO_HIDE_MS = 1_000L
        const val MIN_OVERLAY_AUTO_HIDE_MS = 1_000L
        const val MAX_OVERLAY_AUTO_HIDE_MS = 3_000L

        private const val PREFS_NAME = "tht_voice_user_preferences"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
        private const val KEY_MIC_GAIN = "mic_gain_enabled"
        private const val KEY_WAKE_SENSITIVITY = "wake_sensitivity"
        private const val KEY_MIC_DENOISE = "mic_denoise_enabled"
        private const val KEY_MUSIC_WHILE_LISTENING = "music_while_listening"
        private const val KEY_VOICE_KEY_ALWAYS_THT = "voice_key_always_tht"
        private const val KEY_VOICE_KEY_VEHICLE_SIGNAL = "voice_key_vehicle_signal"
        private const val KEY_USER_ALIASES = "user_aliases"
        private const val KEY_TTS_PHRASE_CACHE = "tts_phrase_cache"
        private const val KEY_AIRFLOW_CYCLE_MINUTES = "airflow_cycle_minutes"
        private const val KEY_SPLIT_TOKEN = "split_voice_token"
        private const val KEY_ECHO_CANCEL = "echo_cancel_while_playing"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_DEFAULT_NAV = "default_navigation_app"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_ENDPOINT_SILENCE_MS = "endpoint_silence_ms"
        private const val KEY_MAX_LISTEN_MS = "max_listen_ms"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_OVERLAY_POSITION = "overlay_position"
        private const val KEY_OVERLAY_AUTO_HIDE_MS = "overlay_auto_hide_ms"
        private const val KEY_OVERLAY_DRAG_ENABLED = "overlay_drag_enabled"
        private const val KEY_OVERLAY_CUSTOM_X = "overlay_custom_x"
        private const val KEY_OVERLAY_CUSTOM_Y = "overlay_custom_y"
    }
}
