package com.voicegeely.app.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportQrTest {
    private val payload = SupportQr.vietQrPayload(SupportQr.MBBANK_BIN, SupportScreenView.ACCOUNT_NUMBER)

    @Test
    fun payloadIsVietQrWithMbBankAccountAndCrc() {
        assertTrue(payload.startsWith("000201010211"))
        assertTrue(payload.contains("0010A000000727"))
        assertTrue(payload.contains("0006970422"))
        assertTrue(payload.contains("01" + SupportScreenView.ACCOUNT_NUMBER.length.toString().padStart(2, '0') + SupportScreenView.ACCOUNT_NUMBER))
        assertTrue(payload.contains("5303704") && payload.contains("5802VN"))
        val body = payload.dropLast(4)
        assertEquals(SupportQr.crc16(body), payload.takeLast(4))
    }

    @Test
    fun crcMatchesKnownEmvcoCheckValue() {
        // CRC-16/CCITT-FALSE check value for "123456789".
        assertEquals("29B1", SupportQr.crc16("123456789"))
    }

    @Test
    fun renderedQrDecodesBackToThePayload() {
        val matrix = SupportQr.matrix(payload, 400)
        val pixels = SupportQr.pixels(matrix)
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.PURE_BARCODE to true, DecodeHintType.TRY_HARDER to true)
        )
        assertEquals(payload, decoded.text)
    }
}
