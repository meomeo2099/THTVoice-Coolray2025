package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVoiceParserTest {
    @Test
    fun openMusicUsesSharedDefaultWhenProviderNotSpoken() {
        val parsed = MediaVoiceParser.parse("mở nhạc")
        assertTrue(parsed?.command is VoiceCommand.OpenMedia)
        val command = parsed!!.command as VoiceCommand.OpenMedia
        assertEquals(MediaContentType.MUSIC, command.kind)
        assertEquals(MediaProvider.DEFAULT, command.provider)
    }

    @Test
    fun openVideoUsesSharedDefaultWhenProviderNotSpoken() {
        val command = MediaVoiceParser.parse("mở video")!!.command as VoiceCommand.OpenMedia
        assertEquals(MediaContentType.VIDEO, command.kind)
        assertEquals(MediaProvider.DEFAULT, command.provider)
    }

    @Test
    fun explicitRevancedOverridesDefault() {
        val command = MediaVoiceParser.parse("mở nhạc trên revanced")!!.command as VoiceCommand.OpenMedia
        assertEquals(MediaContentType.MUSIC, command.kind)
        assertEquals(MediaProvider.REVANCED, command.provider)
    }

    @Test
    fun explicitMorpheAppOpenIsVideo() {
        val command = MediaVoiceParser.parse("mở morphe")!!.command as VoiceCommand.OpenMedia
        assertEquals(MediaContentType.VIDEO, command.kind)
        assertEquals(MediaProvider.MORPHE, command.provider)
    }

    @Test
    fun musicQueryWithoutProviderUsesDefault() {
        val command = MediaVoiceParser.parse("mở nhạc sơn tùng")!!.command as VoiceCommand.SearchMedia
        assertEquals(MediaContentType.MUSIC, command.kind)
        assertEquals(MediaProvider.DEFAULT, command.provider)
        assertEquals("sơn tùng", command.query)
    }

    @Test
    fun providerSearchKeepsExplicitYoutube() {
        val command = MediaVoiceParser.parse("tìm sơn tùng trên youtube")!!.command as VoiceCommand.SearchMedia
        assertEquals(MediaContentType.VIDEO, command.kind)
        assertEquals(MediaProvider.YOUTUBE, command.provider)
        assertEquals("sơn tùng", command.query)
    }

    @Test
    fun appPipQuestionUsesReadOnlyWindowingCommandBeforeMediaRouting() {
        assertEquals(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.YOUTUBE),
            MediaVoiceParser.parse("YouTube có hỗ trợ PiP không")?.command
        )
    }

    @Test
    fun unsupportedPipMutationNeverFallsThroughToMediaSearchOrOpen() {
        assertNull(MediaVoiceParser.parse("mở YouTube bằng PiP"))
        assertNull(MediaVoiceParser.parse("bật PiP YouTube"))
    }
}
