package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.MediaAppSettings

/**
 * Resolves the single user-facing default media preference into an ordered
 * provider list. A provider explicitly spoken by the user is strict; fallback
 * is used only for saved/default selection.
 */
class MediaPreferenceResolver(
    private val selectedApp: () -> DefaultMediaApp
) {
    constructor(settings: MediaAppSettings) : this({ settings.defaultMediaApp })

    fun providerOrder(
        kind: MediaContentType,
        explicit: MediaProvider = MediaProvider.DEFAULT
    ): List<MediaProvider> {
        // "Mở YouTube": the saved default first when it is a YouTube client (Vanced, ReVanced…),
        // then the owner's fallback order YouTube → Vanced → ReVanced → Morphe → SmartTube.
        if (explicit == MediaProvider.YOUTUBE) {
            val preferred = selectedProvider()?.takeIf { it in YOUTUBE_FAMILY }
            return (listOfNotNull(preferred) + YOUTUBE_FAMILY).distinct()
        }
        if (explicit != MediaProvider.DEFAULT) return listOf(explicit)

        val auto = autoOrder(kind)
        val selected = selectedProvider()
        return if (selected == null) auto else (listOf(selected) + auto).distinct()
    }

    private fun selectedProvider(): MediaProvider? {
        return when (selectedApp()) {
            DefaultMediaApp.AUTO -> null
            DefaultMediaApp.YOUTUBE -> MediaProvider.YOUTUBE
            DefaultMediaApp.VANCED -> MediaProvider.VANCED
            DefaultMediaApp.REVANCED -> MediaProvider.REVANCED
            DefaultMediaApp.MORPHE -> MediaProvider.MORPHE
            DefaultMediaApp.SMARTTUBE -> MediaProvider.SMARTTUBE
            DefaultMediaApp.YOUTUBE_MUSIC -> MediaProvider.YOUTUBE_MUSIC
            DefaultMediaApp.ZING_MP3 -> MediaProvider.ZING_MP3
            DefaultMediaApp.SPOTIFY -> MediaProvider.SPOTIFY
        }
    }

    private fun autoOrder(kind: MediaContentType): List<MediaProvider> = when (kind) {
        MediaContentType.VIDEO -> listOf(
            MediaProvider.YOUTUBE,
            MediaProvider.VANCED,
            MediaProvider.REVANCED,
            MediaProvider.MORPHE,
            MediaProvider.SMARTTUBE
        )
        MediaContentType.MUSIC -> listOf(
            MediaProvider.YOUTUBE_MUSIC,
            MediaProvider.ZING_MP3,
            MediaProvider.SPOTIFY,
            MediaProvider.YOUTUBE,
            MediaProvider.VANCED,
            MediaProvider.REVANCED,
            MediaProvider.MORPHE,
            MediaProvider.SMARTTUBE
        )
    }

    companion object {
        val YOUTUBE_FAMILY = listOf(
            MediaProvider.YOUTUBE,
            MediaProvider.VANCED,
            MediaProvider.REVANCED,
            MediaProvider.MORPHE,
            MediaProvider.SMARTTUBE
        )
    }
}
