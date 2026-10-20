package com.voicegeely.app.tts

import android.content.Context
import com.voicegeely.app.settings.TtsVoice
import java.io.File
import java.io.FileNotFoundException

/** Copies the shared Kokoro Vietnamese runtime from APK assets into app-private storage. */
object OfflineTtsModelInstaller {
    data class Result(
        val voice: TtsVoice,
        val modelDir: File,
        val files: OfflineTtsModelSpec.Files? = null,
        val available: Boolean,
        val copied: Boolean,
        val detail: String? = null,
        /** Set when this APK expects the downloadable voice pack and it is not installed yet. */
        val missingPack: KokoroPack.Info? = null
    )

    /** Voice pack pinned by this APK, or null when the model is bundled in the APK. */
    fun packInfo(context: Context): KokoroPack.Info? {
        val app = context.applicationContext
        val assetDir = OfflineTtsModelSpec.ASSET_ROOT
        val names = runCatching { app.assets.list(assetDir)?.toList().orEmpty() }.getOrDefault(emptyList())
        if (OfflineTtsModelSpec.MODEL in names) return null
        return runCatching {
            app.assets.open("$assetDir/${KokoroPack.MANIFEST}").bufferedReader().use { it.readText() }
        }.getOrNull()?.let(KokoroPack::parse)
    }

    fun installedDir(context: Context): File = File(context.applicationContext.filesDir, OfflineTtsModelSpec.FILE_ROOT)

    fun ensureInstalled(context: Context, voice: TtsVoice): Result {
        val app = context.applicationContext
        val targetDir = File(app.filesDir, OfflineTtsModelSpec.fileDir(voice))
        val assetDir = OfflineTtsModelSpec.assetDir(voice)
        val rootNames = try {
            app.assets.list(assetDir)?.filter { it.isNotBlank() }.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        if (rootNames.isEmpty()) return missing(voice, targetDir)

        val expectedStamp = expectedStamp(app, assetDir)
        if (OfflineTtsModelSpec.MODEL !in rootNames) {
            val pack = packInfo(app) ?: return missing(voice, targetDir)
            return ensurePackMode(app, voice, targetDir, assetDir, rootNames, pack, expectedStamp)
        }
        val installedStamp = runCatching { File(targetDir, TtsInstallStamp.FILE).readText() }.getOrNull()
        if (TtsInstallStamp.isCurrent(installedStamp, expectedStamp)) {
            discoverInstalled(targetDir)?.let { files ->
                return Result(voice, targetDir, files, available = true, copied = false)
            }
        }

        // Copy into a staging folder and swap it in only when complete, so an interrupted copy
        // or a copy from an older APK is never reused.
        val staging = File(targetDir.parentFile, targetDir.name + ".installing")
        return try {
            staging.deleteRecursively()
            staging.mkdirs()
            copyAssetsRecursively(app, assetDir, staging)
            if (discoverInstalled(staging) == null) {
                staging.deleteRecursively()
                return Result(
                    voice,
                    targetDir,
                    available = false,
                    copied = true,
                    detail = "Gói giọng đọc chưa đầy đủ, hãy tải hoặc cài lại."
                )
            }
            File(staging, TtsInstallStamp.FILE).writeText(expectedStamp)
            targetDir.deleteRecursively()
            if (!staging.renameTo(targetDir)) throw IllegalStateException("Không hoàn tất cài giọng đọc offline.")
            val files = discoverInstalled(targetDir)
                ?: throw IllegalStateException("Kokoro Vietnamese sau khi cài không đầy đủ.")
            Result(voice, targetDir, files, available = true, copied = true)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            Result(
                voice,
                targetDir,
                available = false,
                copied = false,
                detail = t.message ?: "Không cài được gói giọng đọc (kiểm tra dung lượng trống)."
            )
        }
    }

    /**
     * APK without the heavy files: copy the small files from the APK (once per install), then
     * require the downloaded voice pack that this APK pins in PACK.txt.
     */
    private fun ensurePackMode(
        app: Context,
        voice: TtsVoice,
        targetDir: File,
        assetDir: String,
        rootNames: List<String>,
        pack: KokoroPack.Info,
        expectedStamp: String
    ): Result {
        return try {
            targetDir.mkdirs()
            val stampFile = File(targetDir, TtsInstallStamp.FILE)
            val installedStamp = runCatching { stampFile.readText() }.getOrNull()
            if (!TtsInstallStamp.isCurrent(installedStamp, expectedStamp)) {
                rootNames.forEach { name ->
                    val child = "$assetDir/$name"
                    if (app.assets.list(child).isNullOrEmpty()) {
                        app.assets.open(child).use { input -> File(targetDir, name).outputStream().use { input.copyTo(it) } }
                    }
                }
                stampFile.writeText(expectedStamp)
            }
            if (!KokoroPack.isInstalled(targetDir, pack)) {
                return Result(
                    voice, targetDir, available = false, copied = false,
                    detail = "Chưa tải giọng đọc (khoảng ${pack.sizeMb} MB). Vào Cài đặt → Giọng trợ lý để tải.",
                    missingPack = pack
                )
            }
            val files = discoverInstalled(targetDir)
                ?: return Result(voice, targetDir, available = false, copied = false, detail = "Giọng đọc đã tải nhưng thiếu tệp; hãy tải lại.", missingPack = pack)
            Result(voice, targetDir, files, available = true, copied = false)
        } catch (t: Throwable) {
            Result(voice, targetDir, available = false, copied = false, detail = t.message ?: "Không chuẩn bị được giọng đọc offline.")
        }
    }

    private fun expectedStamp(context: Context, assetDir: String): String {
        val source = runCatching {
            context.assets.open("$assetDir/${OfflineTtsModelSpec.SOURCE}").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val updatedAt = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        return TtsInstallStamp.expected(source, updatedAt)
    }

    private fun missing(voice: TtsVoice, targetDir: File) = Result(
        voice = voice,
        modelDir = targetDir,
        available = false,
        copied = false,
        detail = "Chưa có gói giọng đọc cho ${OfflineTtsModelSpec.descriptor(voice).displayName}."
    )

    private fun discoverInstalled(dir: File): OfflineTtsModelSpec.Files? {
        if (!dir.isDirectory) return null
        val files = dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        val espeak = File(dir, OfflineTtsModelSpec.ESPEAK_DATA)
        val hasEspeakData = espeak.isDirectory && !espeak.list().isNullOrEmpty()
        return OfflineTtsModelSpec.discover(files, hasEspeakData)
    }

    private fun copyAssetsRecursively(context: Context, assetPath: String, destinationDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) throw FileNotFoundException("Không tìm thấy asset TTS: $assetPath")

        for (name in children) {
            val childAsset = "$assetPath/$name"
            val childDest = File(destinationDir, name)
            val nested = context.assets.list(childAsset).orEmpty()
            if (nested.isNotEmpty()) {
                childDest.mkdirs()
                copyAssetsRecursively(context, childAsset, childDest)
            } else {
                childDest.parentFile?.mkdirs()
                context.assets.open(childAsset).use { input ->
                    childDest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
