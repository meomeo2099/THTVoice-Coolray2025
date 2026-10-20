package com.voicegeely.app.voice

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * Best-effort MediaSession metadata reader.
 *
 * IHU builds that grant MEDIA_CONTENT_CONTROL can expose active controllers
 * directly. If that permission/API is unavailable, this adapter returns null and
 * media commands safely remain AcceptedUnverified instead of claiming success.
 */
class AndroidMediaSessionSnapshotProvider(
    context: Context
) : MediaSessionSnapshotProvider {
    private val mediaSessionManager =
        context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    override fun current(): MediaSessionSnapshot? {
        val manager = mediaSessionManager ?: return null
        val controllers = try {
            manager.getActiveSessions(null)
        } catch (_: SecurityException) {
            return null
        } catch (_: Throwable) {
            return null
        }

        return controllers
            .sortedByDescending { playbackPriority(it.playbackState?.state) }
            .asSequence()
            .mapNotNull(::snapshotOf)
            .firstOrNull { it.trackFingerprint() != null }
    }

    private fun snapshotOf(controller: MediaController): MediaSessionSnapshot? {
        val packageName = controller.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) return null

        val metadata = try {
            controller.metadata
        } catch (_: Throwable) {
            null
        }

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L }

        return MediaSessionSnapshot(
            packageName = packageName,
            sessionKey = "$packageName:${controller.sessionToken.hashCode()}",
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString(),
            artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString(),
            album = metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString(),
            durationMs = duration
        )
    }

    private fun playbackPriority(state: Int?): Int = when (state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING -> 2
        PlaybackState.STATE_PAUSED -> 1
        else -> 0
    }
}
