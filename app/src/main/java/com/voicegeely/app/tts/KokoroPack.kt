package com.voicegeely.app.tts

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The heavy Kokoro files (model, voices, eSpeak data) can be delivered as a separate
 * "voice pack" instead of inside the APK (owner decision 29/09/2026). The APK keeps the small
 * files (tokens, lexicon, config, voice map, SOURCE) plus PACK.txt, which pins the exact pack
 * (file name, SHA-256, size) built together with this APK.
 */
object KokoroPack {
    const val MANIFEST = "PACK.txt"
    const val STAMP = ".pack-stamp"

    /** Files that only come from the pack. */
    val HEAVY_FILES = listOf(OfflineTtsModelSpec.MODEL, OfflineTtsModelSpec.VOICES)
    const val HEAVY_DIR = OfflineTtsModelSpec.ESPEAK_DATA

    data class Info(val file: String, val sha256: String, val sizeBytes: Long, val revision: String) {
        val sizeMb: Long get() = (sizeBytes + 1024 * 1024 - 1) / (1024 * 1024)
    }

    fun parse(text: String): Info? {
        val map = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val file = map["file"]?.takeIf { FILE_NAME.matches(it) } ?: return null
        val sha = map["sha256"]?.lowercase()?.takeIf { SHA.matches(it) } ?: return null
        val size = map["size"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Info(file, sha, size, map["revision"].orEmpty())
    }

    fun sha256(file: File): String = FileInputStream(file).use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Only the expected pack entries may be written; anything else (or a path escape) fails. */
    fun isAllowedEntry(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.split('/').any { it == ".." }) return false
        return name in HEAVY_FILES || name == "$HEAVY_DIR/" || name.startsWith("$HEAVY_DIR/") || name == "PACK_SOURCE.txt"
    }

    sealed class InstallResult {
        object Installed : InstallResult()
        data class Failed(val reason: String) : InstallResult()
    }

    /**
     * Verifies [zip] against [info] and installs the heavy files into [targetDir], replacing an
     * older pack. Extraction goes to a staging folder first, so a bad or interrupted pack never
     * leaves a half-installed voice behind.
     */
    fun installFromZip(zip: File, info: Info, targetDir: File, shouldContinue: () -> Boolean = { true }): InstallResult {
        if (!zip.isFile) return InstallResult.Failed("Không tìm thấy tệp giọng đọc.")
        if (zip.length() != info.sizeBytes) return InstallResult.Failed("Kích thước tệp giọng đọc không khớp (${zip.length()} ≠ ${info.sizeBytes}).")
        val actual = sha256(zip)
        if (actual != info.sha256) return InstallResult.Failed("Mã SHA-256 của tệp giọng đọc không khớp với bản cài này.")

        val parent = targetDir.parentFile ?: return InstallResult.Failed("Thư mục cài không hợp lệ.")
        val staging = File(parent, targetDir.name + ".pack-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            ZipInputStream(FileInputStream(zip)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (!shouldContinue()) return InstallResult.Failed("Đã hủy cài giọng đọc.")
                    if (!isAllowedEntry(entry.name)) return InstallResult.Failed("Tệp giọng đọc chứa mục không hợp lệ: ${entry.name}")
                    val out = File(staging, entry.name)
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        return InstallResult.Failed("Tệp giọng đọc chứa đường dẫn không hợp lệ.")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it, 1 shl 16) }
                    }
                }
            }
            val espeak = File(staging, HEAVY_DIR)
            if (HEAVY_FILES.any { !File(staging, it).isFile || File(staging, it).length() == 0L } ||
                !espeak.isDirectory || espeak.list().isNullOrEmpty()
            ) {
                return InstallResult.Failed("Tệp giọng đọc không đầy đủ.")
            }

            targetDir.mkdirs()
            File(targetDir, STAMP).delete()
            HEAVY_FILES.forEach { File(targetDir, it).delete() }
            File(targetDir, HEAVY_DIR).deleteRecursively()
            HEAVY_FILES.forEach { name ->
                if (!File(staging, name).renameTo(File(targetDir, name))) {
                    File(staging, name).copyTo(File(targetDir, name), overwrite = true)
                }
            }
            if (!File(staging, HEAVY_DIR).renameTo(File(targetDir, HEAVY_DIR))) {
                File(staging, HEAVY_DIR).copyRecursively(File(targetDir, HEAVY_DIR), overwrite = true)
            }
            File(targetDir, STAMP).writeText(info.sha256)
            return InstallResult.Installed
        } catch (t: Throwable) {
            return InstallResult.Failed(t.message ?: "Không giải nén được tệp giọng đọc.")
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * The GitHub Actions artifact download wraps the pack in another zip
     * (THTVoice-kokoro-vi-voice-pack.zip → THTVoice-kokoro-vi-<rev>.zip + SHA256.txt).
     * When the picked file is that wrapper, copy the inner pack out to [dest].
     */
    fun extractFromWrapper(outer: File, info: Info, dest: File): Boolean = try {
        var found = false
        ZipInputStream(FileInputStream(outer)).use { zin ->
            while (!found) {
                val entry = zin.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == info.file) {
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 16)
                        var total = 0L
                        while (true) {
                            val n = zin.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > info.sizeBytes) return false
                            out.write(buffer, 0, n)
                        }
                    }
                    found = true
                }
            }
        }
        found && dest.length() == info.sizeBytes
    } catch (_: Throwable) {
        false
    }

    /** True when [dir] holds the heavy files of exactly [info]. */
    fun isInstalled(dir: File, info: Info): Boolean {
        val stamp = runCatching { File(dir, STAMP).readText().trim() }.getOrNull()
        if (stamp != info.sha256) return false
        val espeak = File(dir, HEAVY_DIR)
        return HEAVY_FILES.all { File(dir, it).isFile && File(dir, it).length() > 0L } && espeak.isDirectory && !espeak.list().isNullOrEmpty()
    }

    fun remove(dir: File) {
        File(dir, STAMP).delete()
        HEAVY_FILES.forEach { File(dir, it).delete() }
        File(dir, HEAVY_DIR).deleteRecursively()
    }

    private val FILE_NAME = Regex("^[A-Za-z0-9._-]{1,120}\\.zip$")
    private val SHA = Regex("^[0-9a-f]{64}$")
}
