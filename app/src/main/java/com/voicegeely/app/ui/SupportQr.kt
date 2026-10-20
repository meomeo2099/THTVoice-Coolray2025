package com.voicegeely.app.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Support QR as a VietQR (NAPAS 247, EMVCo) static transfer payload, rendered at runtime.
 * The previous bundled PNG was corrupt and showed an empty frame.
 */
object SupportQr {
    /** NAPAS bank identification number (BIN) of MBBank. */
    const val MBBANK_BIN = "970422"

    fun vietQrPayload(bankBin: String, account: String): String {
        val beneficiary = tlv("00", bankBin) + tlv("01", account)
        val merchant = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA")
        val body = tlv("00", "01") + tlv("01", "11") + tlv("38", merchant) + tlv("53", "704") + tlv("58", "VN") + "6304"
        return body + crc16(body)
    }

    fun matrix(payload: String, sizePx: Int): BitMatrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    )

    /** ARGB pixels (black on white) for Bitmap.createBitmap. */
    fun pixels(matrix: BitMatrix): IntArray {
        val w = matrix.width
        val h = matrix.height
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            out[y * w + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return out
    }

    internal fun tlv(tag: String, value: String): String = tag + value.length.toString().padStart(2, '0') + value

    /** CRC-16/CCITT-FALSE as required by EMVCo QR (poly 0x1021, init 0xFFFF). */
    internal fun crc16(data: String): String {
        var crc = 0xFFFF
        for (b in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
