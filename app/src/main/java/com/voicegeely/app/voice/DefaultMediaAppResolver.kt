package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.MediaContentKind

/** Resolves one final ordered package list for merged music/video media setting. */
object DefaultMediaAppResolver {
    private val packages = mapOf(
        DefaultMediaApp.YOUTUBE to listOf("com.google.android.youtube"),
        DefaultMediaApp.VANCED to listOf("com.vanced.android.youtube"),
        DefaultMediaApp.REVANCED to listOf(
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.revanced.android.youtube"
        ),
        DefaultMediaApp.MORPHE to listOf("app.morphe.android.youtube"),
        DefaultMediaApp.SMARTTUBE to listOf(
            "org.smarttube.stable",
            "org.smarttube.beta",
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videomanager.tv"
        ),
        DefaultMediaApp.YOUTUBE_MUSIC to listOf(
            "com.google.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music",
            "anddea.youtube.music"
        ),
        DefaultMediaApp.ZING_MP3 to listOf("com.zing.mp3"),
        DefaultMediaApp.SPOTIFY to listOf("com.spotify.music")
    )

    fun packageCandidates(
        selected: DefaultMediaApp,
        contentKind: MediaContentKind
    ): List<String> {
        val preferred = if (selected == DefaultMediaApp.AUTO) emptyList() else packages[selected].orEmpty()
        val fallbackApps = when (contentKind) {
            MediaContentKind.VIDEO -> listOf(
                DefaultMediaApp.YOUTUBE,
                DefaultMediaApp.VANCED,
                DefaultMediaApp.REVANCED,
                DefaultMediaApp.MORPHE,
                DefaultMediaApp.SMARTTUBE
            )
            MediaContentKind.MUSIC -> listOf(
                DefaultMediaApp.YOUTUBE_MUSIC,
                DefaultMediaApp.ZING_MP3,
                DefaultMediaApp.SPOTIFY,
                DefaultMediaApp.YOUTUBE,
                DefaultMediaApp.VANCED,
                DefaultMediaApp.REVANCED,
                DefaultMediaApp.MORPHE,
                DefaultMediaApp.SMARTTUBE
            )
        }

        return (preferred + fallbackApps.flatMap { packages[it].orEmpty() }).distinct()
    }

    fun packagesFor(app: DefaultMediaApp): List<String> = packages[app].orEmpty()
}
