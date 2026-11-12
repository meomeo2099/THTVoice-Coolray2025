package com.voicegeely.app.voice

import org.junit.Assert.*
import org.junit.Test

class RegionalVehiclePhrasesTest {
    @Test fun unifiedWindowAliasesKeepRowsSidesAndPercentages() {
        val driver = listOf("tay", "nái", "lài", "lãi", "lải", "lại", "lấy", "nãi")
        val passenger = listOf("phủ", "phú", "phù", "phũ", "fụ", "fu", "phu", "bụ", "vụ")
        for (word in driver) assertSameMeaning("hạ kính sau bên $word 50 phần trăm", "hạ kính sau bên lái 50 phần trăm")
        for (word in passenger) assertSameMeaning("đóng kính sau bên $word", "đóng kính sau bên phụ")
        for (word in listOf("seo", "xau", "xao", "sao")) assertSameMeaning("hạ kính $word bên tay", "hạ kính sau bên lái")
        for (word in listOf("chước", "trướt", "trức", "chướt", "trưới")) assertSameMeaning("hạ kính phía $word bên phụ", "hạ kính trước bên phụ")
        for (word in listOf("chái", "chải", "trãi", "trải", "chại")) assertSameMeaning("hạ kính sau bên $word", "hạ kính sau bên trái")
        for (word in listOf("phãi", "phái", "fải", "phai", "phại")) assertSameMeaning("hạ kính sau bên $word", "hạ kính sau bên phải")
        assertSameMeaning("hạ kính seo bên tay phụ", "hạ kính sau bên phụ")
        assertSameMeaning("hạ kính xau bên tài phủ", "hạ kính sau bên phụ")
    }

    @Test fun circulationFanAndDemistWordsShareOneCommandPipeline() {
        for (word in listOf("treo", "chong", "trông")) assertSameMeaning("lấy gió $word", "lấy gió trong")
        for (word in listOf("ngoai", "ngoải", "ngoãi", "ngòai")) assertSameMeaning("lấy gió $word", "lấy gió ngoài")
        for (word in listOf("sái", "sáy")) {
            assertSameMeaning("$word kính seo", "sấy kính sau")
        }
        for (word in listOf("quạc", "quặc")) assertSameMeaning("chỉnh $word mức ba", "chỉnh quạt mức ba")
    }

    @Test fun namesAndUntargetedWindowsAreNeverGuessed() {
        for (text in listOf("đi đến đường tay", "mở ứng dụng seo", "tìm bài hát lấy gió treo", "đóng kính lại", "lấy gió trong", "đi đến đường sao", "mở ứng dụng quạc", "đi đến tiệm sái", "cầm tay phải"))
            assertEquals(text, text, VoiceTextNormalizer.normalize(text))
        assertTrue(VoiceParseChain.parse("đóng kính lại")?.command !is VoiceCommand.Window)
        assertTrue(VoiceParseChain.parse("hạ kính sau bên không rõ")?.command !is VoiceCommand.Window)
    }

    private fun assertSameMeaning(heard: String, canonical: String) {
        val expected = VoiceParseChain.parse(canonical)?.command
        assertNotNull(canonical, expected)
        assertEquals(heard, expected, VoiceParseChain.parse(heard)?.command)
    }
}
