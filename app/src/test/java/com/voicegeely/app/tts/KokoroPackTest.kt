package com.voicegeely.app.tts

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroPackTest {
    private fun tmpDir(): File = File.createTempFile("tht-pack", "").apply { delete(); mkdirs(); deleteOnExit() }

    private fun zip(dir: File, entries: Map<String, String>): File {
        val file = File(dir, "pack.zip")
        ZipOutputStream(file.outputStream()).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return file
    }

    private val goodEntries = linkedMapOf(
        "kokoro_vi.onnx" to "model",
        "voices.bin" to "voices",
        "espeak-ng-data/" to "",
        "espeak-ng-data/phontab" to "data",
        "PACK_SOURCE.txt" to "revision=abc"
    )

    private fun info(file: File) = KokoroPack.Info("THTVoice-kokoro-vi-abc.zip", KokoroPack.sha256(file), file.length(), "abc")

    @Test
    fun manifestParses() {
        val sha = "a".repeat(64)
        val info = KokoroPack.parse("file=THTVoice-kokoro-vi-9f210d62.zip\nsha256=$sha\nsize=341000000\nrevision=9f210d62\n")!!
        assertEquals("THTVoice-kokoro-vi-9f210d62.zip", info.file)
        assertEquals(sha, info.sha256)
        assertEquals(326L, info.sizeMb)
        assertNull(KokoroPack.parse("file=../evil.zip\nsha256=$sha\nsize=1"))
        assertNull(KokoroPack.parse("file=a.zip\nsha256=xyz\nsize=1"))
    }

    @Test
    fun validPackInstallsAndIsDetected() {
        val dir = tmpDir()
        val z = zip(dir, goodEntries)
        val target = File(dir, "kokoro_vi")
        val info = info(z)
        assertEquals(KokoroPack.InstallResult.Installed, KokoroPack.installFromZip(z, info, target))
        assertTrue(KokoroPack.isInstalled(target, info))
        assertEquals("model", File(target, "kokoro_vi.onnx").readText())
        assertFalse(File(dir, "kokoro_vi.pack-staging").exists())
        KokoroPack.remove(target)
        assertFalse(KokoroPack.isInstalled(target, info))
    }

    @Test
    fun wrongShaOrSizeIsRejectedWithoutTouchingTarget() {
        val dir = tmpDir()
        val z = zip(dir, goodEntries)
        val target = File(dir, "kokoro_vi")
        val good = info(z)
        assertTrue(KokoroPack.installFromZip(z, good.copy(sha256 = "0".repeat(64)), target) is KokoroPack.InstallResult.Failed)
        assertTrue(KokoroPack.installFromZip(z, good.copy(sizeBytes = good.sizeBytes + 1), target) is KokoroPack.InstallResult.Failed)
        assertFalse(File(target, "kokoro_vi.onnx").exists())
    }

    @Test
    fun zipSlipAndUnexpectedEntriesAreRejected() {
        assertFalse(KokoroPack.isAllowedEntry("../kokoro_vi.onnx"))
        assertFalse(KokoroPack.isAllowedEntry("espeak-ng-data/../../x"))
        assertFalse(KokoroPack.isAllowedEntry("/etc/passwd"))
        assertFalse(KokoroPack.isAllowedEntry("lexicon-vi.txt"))
        assertTrue(KokoroPack.isAllowedEntry("espeak-ng-data/voices/vi"))
        val dir = tmpDir()
        val z = zip(dir, goodEntries + ("../evil.txt" to "x"))
        val result = KokoroPack.installFromZip(z, info(z), File(dir, "kokoro_vi"))
        assertTrue(result is KokoroPack.InstallResult.Failed)
        assertFalse(File(dir, "evil.txt").exists())
    }

    @Test
    fun incompletePackIsRejected() {
        val dir = tmpDir()
        val z = zip(dir, mapOf("kokoro_vi.onnx" to "model"))
        assertTrue(KokoroPack.installFromZip(z, info(z), File(dir, "kokoro_vi")) is KokoroPack.InstallResult.Failed)
    }

    @Test
    fun packInsideGithubArtifactWrapperIsExtracted() {
        val dir = tmpDir()
        val inner = zip(dir, goodEntries)
        val info = info(inner).copy(file = "THTVoice-kokoro-vi-abc.zip")
        val wrapper = File(dir, "THTVoice-kokoro-vi-voice-pack.zip")
        ZipOutputStream(wrapper.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("SHA256.txt")); z.write("x".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry(info.file)); z.write(inner.readBytes()); z.closeEntry()
        }
        val out = File(dir, "extracted.zip")
        assertTrue(KokoroPack.extractFromWrapper(wrapper, info, out))
        assertEquals(KokoroPack.InstallResult.Installed, KokoroPack.installFromZip(out, info, File(dir, "kokoro_vi")))
        assertFalse(KokoroPack.extractFromWrapper(wrapper, info.copy(file = "other.zip"), File(dir, "none.zip")))
    }
}
