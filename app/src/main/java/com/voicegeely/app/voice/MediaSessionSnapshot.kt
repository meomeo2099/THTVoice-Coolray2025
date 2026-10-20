package com.voicegeely.app.voice

/**
 * Small framework-free snapshot of one Android media session.
 *
 * [sessionKey] is supplied by the Android adapter and must stay stable while the
 * same MediaSession is alive. Track verification deliberately requires the same
 * package + session so a different app becoming active cannot create a false
 * NEXT/PREVIOUS success.
 */
data class MediaSessionSnapshot(
    val packageName: String,
    val sessionKey: String,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null
) {
    fun trackFingerprint(): String? {
        val normalizedMediaId = mediaId.normalizedMetadata()
        val normalizedTitle = title.normalizedMetadata()

        // Be conservative: artist/album/duration alone are not unique enough to
        // prove that the track changed.
        if (normalizedMediaId == null && normalizedTitle == null) return null

        return listOf(
            normalizedMediaId.orEmpty(),
            normalizedTitle.orEmpty(),
            artist.normalizedMetadata().orEmpty(),
            album.normalizedMetadata().orEmpty(),
            durationMs?.takeIf { it > 0L }?.toString().orEmpty()
        ).joinToString("\u001F")
    }

    fun displayLabel(): String? = title.normalizedMetadata() ?: mediaId.normalizedMetadata()

    private fun String?.normalizedMetadata(): String? = this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }
}

/** Pure policy for deciding whether NEXT/PREVIOUS is proven by metadata read-back. */
object MediaTrackChangeVerifier {
    fun changed(before: MediaSessionSnapshot?, after: MediaSessionSnapshot?): Boolean {
        if (before == null || after == null) return false
        if (before.packageName.isBlank() || before.packageName != after.packageName) return false
        if (before.sessionKey.isBlank() || before.sessionKey != after.sessionKey) return false

        val beforeFingerprint = before.trackFingerprint() ?: return false
        val afterFingerprint = after.trackFingerprint() ?: return false
        return beforeFingerprint != afterFingerprint
    }
}

/** Boundary used by the Android backend so verification policy remains unit-testable. */
fun interface MediaSessionSnapshotProvider {
    fun current(): MediaSessionSnapshot?
}
