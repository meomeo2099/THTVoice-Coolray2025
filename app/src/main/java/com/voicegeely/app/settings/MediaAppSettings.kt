package com.voicegeely.app.settings

import android.content.Context

/** One user-facing default for both music and video commands. */
enum class DefaultMediaApp {
    AUTO,
    YOUTUBE,
    VANCED,
    REVANCED,
    MORPHE,
    SMARTTUBE,
    YOUTUBE_MUSIC,
    ZING_MP3,
    SPOTIFY
}

enum class MediaContentKind { MUSIC, VIDEO }

class MediaAppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultMediaApp: DefaultMediaApp
        get() = prefs.getString(KEY_DEFAULT_MEDIA_APP, DefaultMediaApp.AUTO.name)
            ?.let { value -> runCatching { DefaultMediaApp.valueOf(value) }.getOrNull() }
            ?: DefaultMediaApp.AUTO
        set(value) = prefs.edit().putString(KEY_DEFAULT_MEDIA_APP, value.name).apply()

    companion object {
        private const val PREFS_NAME = "tht_voice_media_settings"
        private const val KEY_DEFAULT_MEDIA_APP = "default_media_app"
    }
}
