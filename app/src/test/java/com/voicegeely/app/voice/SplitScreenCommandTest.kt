package com.voicegeely.app.voice

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitScreenCommandTest {
    private fun parse(text: String): VoiceCommand? = VoiceParseChain.parse(text)?.command

    @Test
    fun referencePhrasesParseToStartOrStop() {
        val file = listOf("../docs", "docs").map { File(it, "reference/voice/VOICE_PHRASES.csv") }.first { it.isFile }
        val rows = file.readLines(Charsets.UTF_8).filter { it.startsWith("set_split_screen,") }.map { it.trimEnd().split(',') }
        assertEquals(19, rows.size)
        rows.forEach { row ->
            val expected = if (row[1] == "target=LAST_PAIR") SplitAction.START else SplitAction.STOP
            assertEquals(row[2], VoiceCommand.SplitScreen(expected), parse(row[2]))
        }
    }

    @Test
    fun namedPairsAndLayoutCommands() {
        assertEquals(VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.VIETMAP, right = AppTarget.YOUTUBE),
            parse("chia vietmap với youtube"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.MAPS, right = AppTarget.YOUTUBE),
            parse("Chia màn hình Google Maps với YouTube"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.SWAP), parse("đổi bên"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 40), parse("bên trái bốn mươi phần trăm"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 40), parse("bên phải 60 phần trăm"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 50), parse("chia đều màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.REPLACE, side = SplitSide.RIGHT, app = AppTarget.SPOTIFY),
            parse("đổi bên phải sang Spotify"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, app = AppTarget.YOUTUBE), parse("YouTube toàn màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, side = SplitSide.LEFT), parse("bên trái toàn màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.RESTORE), parse("khôi phục chia màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.HIDE), parse("ẩn thanh chia màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.SHOW), parse("hiện thanh chia màn hình"))
        assertEquals(VoiceCommand.SplitScreen(SplitAction.STOP), parse("tắt chia màn hình"))
    }

    @Test
    fun regionalAndMisheardSpeech() {
        val start = VoiceCommand.SplitScreen(SplitAction.START)
        val stop = VoiceCommand.SplitScreen(SplitAction.STOP)
        mapOf(
            "bậc chia đôi màn hình" to start, "cho tui chia đôi màn hình" to start, "mở lại chia đôi màn hình đi" to start,
            "chia màn hình giùm cái" to start, "chia đôi màng hình" to start, "chia đôi mà hình" to start,
            "chí đôi màn hình" to start, "tách đôi màn hình" to start, "mơ chia đôi màn hình" to start,
            "tắc chia màn hình" to stop, "thoát chia đôi màn hình nha" to stop, "hủy chia màn hình dùm" to stop,
            "ra khỏi chia đôi màn hình" to stop,
            "đổi bên giùm tui" to VoiceCommand.SplitScreen(SplitAction.SWAP),
            "bên trái bốn chục phần trăm" to VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 40),
            "bên trái bốn mươi phần trăm nghen" to VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 40),
            "đổi bên phải qua spotify" to VoiceCommand.SplitScreen(SplitAction.REPLACE, side = SplitSide.RIGHT, app = AppTarget.SPOTIFY),
            "du túp toàn màn hình" to VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, app = AppTarget.YOUTUBE),
            "chia gu mát với diu túp giùm" to VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.MAPS, right = AppTarget.YOUTUBE),
            "chia việt máp với du túp" to VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.VIETMAP, right = AppTarget.YOUTUBE),
            "chia màn hình gú gồ mát với sờ pô ti phai" to VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.MAPS, right = AppTarget.SPOTIFY),
            "chia gu mát với tít tốc" to VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.MAPS, right = AppTarget.TIKTOK)
        ).forEach { (spoken, expected) -> assertEquals(spoken, expected, parse(spoken)) }
        // A question, not an order.
        assertTrue(parse("tắt chia màn hình hông") !is VoiceCommand.SplitScreen)
    }

    @Test
    fun nearbyCommandsAreNotSplit() {
        listOf("mở youtube", "tắt màn hình", "bật điều hòa", "chia sẻ vị trí", "nhiệt độ 22 độ",
            "chia con mèo với youtube", "kiểm tra chia đôi màn hình").forEach { phrase ->
            assertTrue(phrase, parse(phrase) !is VoiceCommand.SplitScreen)
        }
        assertNull(SplitScreenCommandParser.parse("chia youtube với youtube"))
    }

    private class FakeClient(
        var installed: Set<String> = setOf(ThtSplitClientIds.SPLIT, "vn.vietmap.live", "com.google.android.youtube", "com.spotify.music"),
        var reply: SplitReply = SplitReply(SplitReply.SUCCESS, message = "ok", verified = true),
        var throwOnSend: Boolean = false
    ) : SplitScreenClient {
        val sent = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun isSplitInstalled() = ThtSplitClientIds.SPLIT in installed
        override fun isPackageInstalled(packageName: String) = packageName in installed
        override fun send(command: String, extras: Map<String, Any>): SplitReply {
            if (throwOnSend) throw IllegalStateException("boom")
            sent += command to extras
            return reply
        }
    }

    private object ThtSplitClientIds { const val SPLIT = "com.tht.split" }

    @Test
    fun startSendsResolvedPackagesAndOnlySuccessIsConfirmed() {
        val client = FakeClient()
        val memory = SplitPairMemory()
        val executor = SplitScreenExecutor(client, memory)
        val result = executor.execute(VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.VIETMAP, right = AppTarget.YOUTUBE))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals("start" to mapOf<String, Any>("left" to "vn.vietmap.live", "right" to "com.google.android.youtube", "ratio" to 50), client.sent.single())
        // "YouTube toàn màn hình" now knows YouTube is on the right.
        executor.execute(VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, app = AppTarget.YOUTUBE))
        assertEquals("fullscreen" to mapOf<String, Any>("side" to "right"), client.sent.last())
        executor.execute(VoiceCommand.SplitScreen(SplitAction.SWAP))
        assertEquals("com.google.android.youtube", memory.left)

        client.reply = SplitReply(SplitReply.REQUESTED, code = "LAYOUT_UNVERIFIED", message = "Đã gửi, chưa khớp bố cục")
        assertEquals(CommandExecutionResult.AcceptedUnverified("Đã gửi, chưa khớp bố cục"),
            executor.execute(VoiceCommand.SplitScreen(SplitAction.STOP)))
        client.reply = SplitReply(SplitReply.TIMEOUT)
        assertTrue(executor.execute(VoiceCommand.SplitScreen(SplitAction.START)) is CommandExecutionResult.AcceptedUnverified)
        client.reply = SplitReply(SplitReply.ERROR, code = "BUSY")
        assertTrue(executor.execute(VoiceCommand.SplitScreen(SplitAction.START)) is CommandExecutionResult.Failed)
        client.reply = SplitReply(SplitReply.ERROR, code = "API_DISABLED", message = "API tắt")
        assertEquals(CommandExecutionResult.Rejected("API tắt"), executor.execute(VoiceCommand.SplitScreen(SplitAction.HIDE)))
        client.reply = SplitReply(SplitReply.NO_AUTH)
        assertEquals(CommandExecutionResult.Rejected(SplitScreenExecutor.NO_AUTH), executor.execute(VoiceCommand.SplitScreen(SplitAction.SHOW)))
    }

    @Test
    fun guardsBeforeSending() {
        val client = FakeClient()
        val executor = SplitScreenExecutor(client)
        assertEquals(CommandExecutionResult.Rejected(SplitScreenExecutor.SIDE_UNKNOWN),
            executor.execute(VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, app = AppTarget.YOUTUBE)))
        assertEquals(CommandExecutionResult.Rejected(SplitScreenExecutor.RATIO_RANGE),
            executor.execute(VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 20)))
        assertEquals(CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND),
            executor.execute(VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.NETFLIX, right = AppTarget.YOUTUBE)))
        assertTrue(client.sent.isEmpty())
        client.installed = emptySet()
        assertEquals(CommandExecutionResult.Rejected(SplitScreenExecutor.NOT_INSTALLED), executor.execute(VoiceCommand.SplitScreen(SplitAction.STOP)))
        client.installed = setOf(ThtSplitClientIds.SPLIT)
        client.throwOnSend = true
        assertTrue(executor.execute(VoiceCommand.SplitScreen(SplitAction.STOP)) is CommandExecutionResult.Failed)
    }

    @Test
    fun youtubeFollowsClientOrder() {
        val client = FakeClient(installed = setOf(ThtSplitClientIds.SPLIT, "app.revanced.android.youtube", "vn.vietmap.live"))
        SplitScreenExecutor(client).execute(VoiceCommand.SplitScreen(SplitAction.START, left = AppTarget.VIETMAP, right = AppTarget.YOUTUBE))
        assertEquals("app.revanced.android.youtube", client.sent.single().second["right"])
    }

    @Test
    fun routerSendsSplitToAndroidExecutorAndFormatterNeverSaysDoneUnlessConfirmed() = runBlocking {
        val command = VoiceCommand.SplitScreen(SplitAction.STOP)
        assertEquals("Đã tắt chia màn hình.", VoiceResponseFormatter.result(command, CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE)))
        val unverified = VoiceResponseFormatter.result(command, CommandExecutionResult.AcceptedUnverified("x"))
        assertTrue(unverified, !unverified.startsWith("Đã tắt"))
    }
}
