package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpokenTermsTest {
    private fun say(s: String) = SpokenTerms.forSpeech(s)

    @Test fun brandAndSystemWords() {
        assertEquals("Cần cập nhật tê hát tê voi.", say("Cần cập nhật THT Voice."))
        assertEquals("tê hát tê chia màn hình đang bận, thử lại sau giây lát.", say("THT Split đang bận, thử lại sau giây lát."))
        assertEquals("hệ thống không cho phép đọc trạng thái Wi-Fi.", say("Android không cho phép đọc trạng thái Wi-Fi."))
        assertEquals("hệ thống xe chưa sẵn sàng.", say("VHAL chưa sẵn sàng."))
        assertEquals("Lệnh này không thuộc nhóm điều hòa.", say("Lệnh này không thuộc nhóm HVAC."))
        assertEquals("Không đọc được trạng thái hỗ trợ lái.", say("Không đọc được trạng thái ADAS."))
        assertEquals("Lỗi khi ghi đèn pha thông minh.", say("Lỗi khi ghi Intelligent High Beam."))
        assertEquals("Chỉ đổi độ sáng khi xe đang đỗ ở số pê.", say("Chỉ đổi độ sáng khi xe đang đỗ ở số P."))
        assertEquals("Xe đang ở 30 ki lô mét trên giờ.", say("Xe đang ở 30 km/h."))
        assertEquals("Front cốp trước chỉ nhận lệnh mở cốp trước.".replace("Front ", ""), say("Front trunk executor chỉ nhận lệnh mở cốp trước."))
    }

    @Test fun wordBoundedOnly() {
        assertEquals("Đã mở youtube.", say("Đã mở youtube."))
        assertEquals("Đã bật điều hòa.", say("Đã bật điều hòa."))
        assertFalse("THTX is not THT", say("THTX").contains("tê hát tê"))
        assertEquals("Xe pháp", say("Xe pháp"))
    }
}

class SpokenTermsDiagnosticsTest {
    @org.junit.Test fun developerWordsSpokenInVietnamese() {
        org.junit.Assert.assertEquals("Không gọi được giao tiếp đọc chế độ âm thanh cảnh báo.",
            SpokenTerms.forSpeech("Không gọi được API đọc AVAS mode."))
        org.junit.Assert.assertEquals("màn hình chế độ trả về dữ liệu trống.", SpokenTerms.forSpeech("Scene provider trả về cursor null."))
        org.junit.Assert.assertEquals("lệnh và phản hồi đèn pha thông minh không nhất quán: lệnh=3 phản hồi=3.",
            SpokenTerms.forSpeech("Control/echo Intelligent High Beam không nhất quán: control=3 echo=3."))
    }
}
