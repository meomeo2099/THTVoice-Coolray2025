package com.voicegeely.app

/**
 * Who answers the steering-wheel voice key while a phone is projected (owner decision 03/10/2026).
 *
 * Settings → "Nút voice khi dùng Android Auto / CarPlay":
 * - "Luôn dùng THT Voice" (default): THT Voice always takes the key.
 * - off: while Android Auto / CarPlay is running over USB, the key is left to the phone's
 *   assistant. With no phone projected THT Voice still takes it, so the "connect your phone with
 *   a USB cable" prompt never comes back. When projection cannot be detected, THT Voice keeps it.
 */
object VoiceKeyProjectionPolicy {
    /** USB vendor/product of a connected device (the head unit is the USB host). */
    data class UsbId(val vendorId: Int, val productId: Int)

    private const val APPLE_VENDOR_ID = 0x05AC
    private const val GOOGLE_VENDOR_ID = 0x18D1
    /** Android Open Accessory mode (Android Auto over USB switches the phone into it). */
    private val AOA_PRODUCT_IDS = 0x2D00..0x2D05

    fun isProjectionPhone(id: UsbId): Boolean =
        id.vendorId == APPLE_VENDOR_ID ||
            (id.vendorId == GOOGLE_VENDOR_ID && id.productId in AOA_PRODUCT_IDS)

    /** True = do not take the key; let the phone projection answer it. */
    fun yieldToProjection(alwaysThtVoice: Boolean, connected: List<UsbId>): Boolean =
        !alwaysThtVoice && connected.any(::isProjectionPhone)
}
