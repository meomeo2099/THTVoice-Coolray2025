package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAliasExportTest {
    @Test fun exportListsEveryAliasReadably() {
        val book = UserAliasBook()
        assertEquals("", book.exportText())
        book.teach("gió bão", "quạt mức 6")
        book.teach("đi làm", "mở google map")
        assertEquals(
            "Tên gọi riêng THT Voice (2)\n\"gió bão\" → quạt mức 6\n\"đi làm\" → mở google map",
            book.exportText()
        )
    }
}
