package com.voicegeely.app.asr

import android.content.Context
import java.io.File

/** Copies an optionally packaged Sherpa model from APK assets into app-private storage. */
object SherpaModelInstaller {
    data class Result(
        val modelDir: File,
        val available: Boolean,
        val copied: Boolean,
        val detail: String? = null
    )

    fun ensureInstalled(context: Context): Result {
        val app = context.applicationContext
        val assetDir = "models/${Sherpa65mModelSpec.MODEL_ID}"
        val targetDir = File(app.filesDir, assetDir)

        val existingNames = targetDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            .orEmpty()
        val existingComplete = Sherpa65mModelSpec.discover(existingNames) != null

        val assetNames = try {
            app.assets.list(assetDir)?.filter { it.isNotBlank() }.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val assetsComplete = Sherpa65mModelSpec.discover(assetNames) != null

        // A code-only APK may intentionally contain no model. In that case preserve any
        // already installed complete model rather than deleting/replacing it.
        if (!assetsComplete) {
            return if (existingComplete) {
                Result(targetDir, available = true, copied = false)
            } else {
                Result(
                    targetDir,
                    available = false,
                    copied = false,
                    detail = "Bản cài chưa có bộ nhận giọng nói."
                )
            }
        }

        val assetBackend = readAssetTextOrNull(app, "$assetDir/${SherpaAsrBackendSpec.MARKER_FILE}")
        val installedBackend = readFileTextOrNull(File(targetDir, SherpaAsrBackendSpec.MARKER_FILE))
        val assetSource = readAssetTextOrNull(app, "$assetDir/model-source.txt")
        val installedSource = readFileTextOrNull(File(targetDir, "model-source.txt"))

        val backendMatches = assetBackend == null || assetBackend == installedBackend
        val sourceMatches = assetSource == null || assetSource == installedSource
        if (existingComplete && backendMatches && sourceMatches) {
            return Result(targetDir, available = true, copied = false)
        }

        return try {
            val staging = File(targetDir.parentFile, "${targetDir.name}.staging")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            assetNames.forEach { name ->
                val target = File(staging, name)
                app.assets.open("$assetDir/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
            }

            val stagedNames = staging.listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                .orEmpty()
            if (Sherpa65mModelSpec.discover(stagedNames) == null) {
                staging.deleteRecursively()
                return Result(targetDir, false, true, "Model staging chưa đủ file bắt buộc.")
            }

            val backup = File(targetDir.parentFile, "${targetDir.name}.backup")
            if (backup.exists()) backup.deleteRecursively()
            if (targetDir.exists() && !targetDir.renameTo(backup)) {
                staging.deleteRecursively()
                throw IllegalStateException("Không thể tạo backup model đang dùng")
            }
            if (!staging.renameTo(targetDir)) {
                if (backup.exists()) backup.renameTo(targetDir)
                throw IllegalStateException("Không thể kích hoạt model mới")
            }
            backup.deleteRecursively()

            Result(
                targetDir,
                available = true,
                copied = true,
                detail = if (assetBackend != null) "Đã cài model ASR backend=$assetBackend" else "Đã cài model ASR mới"
            )
        } catch (t: Throwable) {
            Result(targetDir, false, false, t.message ?: "Không cài được bộ nhận giọng nói.")
        }
    }

    private fun readAssetTextOrNull(context: Context, path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText().trim() }.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    private fun readFileTextOrNull(file: File): String? = try {
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}
