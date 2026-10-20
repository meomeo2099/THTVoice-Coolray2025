package com.voicegeely.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTrackChangeVerifierTest {
    @Test
    fun confirmsChangedTrackInSamePackageAndSession() {
        val before = snapshot(mediaId = "track-1", title = "Bài một")
        val after = snapshot(mediaId = "track-2", title = "Bài hai")

        assertTrue(MediaTrackChangeVerifier.changed(before, after))
    }

    @Test
    fun unchangedMetadataDoesNotConfirm() {
        val before = snapshot(mediaId = "track-1", title = "Bài một")
        val after = snapshot(mediaId = "track-1", title = "Bài một")

        assertFalse(MediaTrackChangeVerifier.changed(before, after))
    }

    @Test
    fun differentPackageDoesNotConfirm() {
        val before = snapshot(mediaId = "track-1", title = "Bài một")
        val after = snapshot(
            packageName = "com.other.player",
            mediaId = "track-2",
            title = "Bài hai"
        )

        assertFalse(MediaTrackChangeVerifier.changed(before, after))
    }

    @Test
    fun differentSessionDoesNotConfirm() {
        val before = snapshot(mediaId = "track-1", title = "Bài một")
        val after = snapshot(
            sessionKey = "com.example.player:2",
            mediaId = "track-2",
            title = "Bài hai"
        )

        assertFalse(MediaTrackChangeVerifier.changed(before, after))
    }

    @Test
    fun artistAlbumAndDurationWithoutTrackIdentityAreNotEnough() {
        val before = snapshot(mediaId = null, title = null, artist = "Nghệ sĩ", durationMs = 180_000)
        val after = snapshot(mediaId = null, title = null, artist = "Nghệ sĩ khác", durationMs = 200_000)

        assertFalse(MediaTrackChangeVerifier.changed(before, after))
    }

    @Test
    fun titleCanProvideTrackIdentityWhenMediaIdIsMissing() {
        val before = snapshot(mediaId = null, title = "Bài một")
        val after = snapshot(mediaId = null, title = "Bài hai")

        assertTrue(MediaTrackChangeVerifier.changed(before, after))
    }

    private fun snapshot(
        packageName: String = "com.example.player",
        sessionKey: String = "com.example.player:1",
        mediaId: String?,
        title: String?,
        artist: String? = "Nghệ sĩ",
        album: String? = "Album",
        durationMs: Long? = 180_000
    ) = MediaSessionSnapshot(
        packageName = packageName,
        sessionKey = sessionKey,
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs
    )
}
