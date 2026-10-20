package com.voicegeely.app.ui

enum class MainDestination(
    val title: String,
    val shortIcon: String
) {
    HOME("Trang chủ", "⌂"),
    ASSISTANT("Trợ lý", "●"),
    APPS("Ứng dụng", "▦"),
    SETTINGS("Cài đặt", "⚙"),
    SUPPORT("Ủng hộ", "♡"),
    ABOUT("Giới thiệu", "ⓘ");

    companion object {
        val ordered: List<MainDestination> = listOf(
            HOME,
            ASSISTANT,
            APPS,
            SETTINGS,
            SUPPORT,
            ABOUT
        )
    }
}
