package com.voicegeely.app.tts

/** Pinned R6 media in the owner’s Coolray 2025 release; eligible only after licensed legacy metadata, never an auth/network failure.
 * The repository must be public for this compatibility path; private assets use the Worker. */
internal object M4aRelease {
    data class Asset(val size: Int, val sha256: String, val url: String)
    fun asset(north: Boolean): Asset {
        val slug = if (north) "ngoc-huyen" else "diem-trinh"
        return Asset(if (north) 8565972 else 8332663,
            if (north) "339f6da1b204e36a55f293b5c5e287c8c22c9e5d16be4ab11f22ecce967e2835"
            else "3544cb704b43d2514968c13566f72c0d3ab62a45083f66714921eaff9fa325d4",
            "https://github.com/meomeo2099/THTVoice-Coolray2025/releases/download/voice-mp3-packs/THTVoice-M4A-$slug-r6.zip")
    }
    fun legacyMetadata(schema: Int, format: String, sha: String, size: Int): Boolean =
        schema == 1 && (format.isEmpty() || format == "mp3") &&
            sha.matches(Regex("[0-9a-fA-F]{64}")) && size in 1..8*1024*1024
    fun usePublicFallback(status: Int, format: String?, licensed: Boolean): Boolean =
        licensed && ((status == 200 && (format.isNullOrEmpty() || format == "mp3")) || status == 404)
}
