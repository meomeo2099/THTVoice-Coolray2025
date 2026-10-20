package com.voicegeely.app.update

import android.content.Context
import java.io.File

/** Persists only a verified/deferred update pointer in app-private preferences. */
class DeferredUpdateStore(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(apk: File, metadata: UpdateMetadata) {
        prefs.edit()
            .putString(KEY_APK_PATH, apk.absolutePath)
            .putString(KEY_PACKAGE, metadata.packageName)
            .putLong(KEY_VERSION_CODE, metadata.versionCode)
            .putString(KEY_VERSION_NAME, metadata.versionName)
            .putString(KEY_SHA256, metadata.sha256)
            .putString(KEY_URL, metadata.downloadUrl)
            .putLong(KEY_SIZE_BYTES, metadata.sizeBytes.coerceAtLeast(0L))
            .apply()
    }

    /** Cheap hint for UI lifecycle checks. [load] remains authoritative. */
    fun hasPending(): Boolean = prefs.contains(KEY_APK_PATH)

    fun load(): DeferredUpdate? {
        val path = prefs.getString(KEY_APK_PATH, null) ?: return null
        val packageName = prefs.getString(KEY_PACKAGE, null) ?: return clearAndNull()
        val versionCode = prefs.getLong(KEY_VERSION_CODE, -1L).takeIf { it >= 0L } ?: return clearAndNull()
        val sha256 = prefs.getString(KEY_SHA256, null) ?: return clearAndNull()
        val apk = File(path)
        if (!isInsideUpdateCache(apk) || !apk.isFile) return clearAndNull()
        return DeferredUpdate(
            apk = apk,
            metadata = UpdateMetadata(
                packageName = packageName,
                versionCode = versionCode,
                versionName = prefs.getString(KEY_VERSION_NAME, null),
                sha256 = sha256,
                downloadUrl = prefs.getString(KEY_URL, null),
                sizeBytes = prefs.getLong(KEY_SIZE_BYTES, 0L).coerceAtLeast(0L)
            )
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearAndDelete(apk: File?) {
        clear()
        if (apk == null) return
        try {
            if (isInsideUpdateCache(apk)) apk.delete()
        } catch (_: Throwable) {
            // Preference pointer is already cleared; cache cleanup is best effort.
        }
    }

    private fun clearAndNull(): DeferredUpdate? {
        clear()
        return null
    }

    private fun isInsideUpdateCache(file: File): Boolean = try {
        val root = File(context.cacheDir, "updates").canonicalFile
        val target = file.canonicalFile
        target.path.startsWith(root.path + File.separator)
    } catch (_: Throwable) {
        false
    }

    companion object {
        private const val PREFS = "tht_voice_deferred_update"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_PACKAGE = "package"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_URL = "url"
        private const val KEY_SIZE_BYTES = "size_bytes"
    }
}

data class DeferredUpdate(
    val apk: File,
    val metadata: UpdateMetadata
)
