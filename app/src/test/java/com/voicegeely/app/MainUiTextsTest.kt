package com.voicegeely.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiTextsTest {
    private val texts = listOf(MainUiTexts.CHANGELOG_TEXT, MainUiTexts.MANUAL_TEXT)

    @Test
    fun changelogCoversCurrentFeatureFamilies() {
        listOf("Hey THT", "Ngọc Huyền", "Diễm Trinh", "gương", "điều hòa tiết kiệm", "Mở mọi ứng dụng", "tự co giãn").forEach {
            assertTrue(it, MainUiTexts.CHANGELOG_TEXT.contains(it))
        }
    }

    @Test
    fun manualExplainsWakeParkedOnlyAndVerifiedResults() {
        listOf("từ đánh thức", "nút Voice", "số P", "Đã…").forEach { assertTrue(it, MainUiTexts.MANUAL_TEXT.contains(it)) }
    }

    @Test
    fun userFacingTextsCarryNoCarBrand() {
        texts.forEach { text -> assertFalse(text, text.contains("Geely", ignoreCase = true)) }
    }
}
