package com.voicegeely.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainDestinationTest {
    @Test
    fun `navigation exposes exactly six approved destinations in order`() {
        assertEquals(
            listOf(
                "Trang chủ",
                "Trợ lý",
                "Ứng dụng",
                "Cài đặt",
                "Ủng hộ",
                "Giới thiệu"
            ),
            MainDestination.ordered.map { it.title }
        )
        assertEquals(6, MainDestination.ordered.size)
        assertEquals(6, MainDestination.ordered.distinct().size)
    }

    @Test
    fun `titles are unique and non blank`() {
        val titles = MainDestination.ordered.map { it.title }
        assertEquals(titles.size, titles.distinct().size)
        assertTrue(titles.all { it.isNotBlank() })
    }
}
