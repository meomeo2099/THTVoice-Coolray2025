package com.voicegeely.app

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log

/** Reads the phones currently plugged into the head unit's USB ports. Failure = none. */
object ProjectionDetector {
    fun connectedUsb(context: Context): List<VoiceKeyProjectionPolicy.UsbId> = runCatching {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        usb.deviceList.values.map { VoiceKeyProjectionPolicy.UsbId(it.vendorId, it.productId) }
    }.onFailure {
        Log.w("THTVoiceECARX", "usb_list_failed type=${it.javaClass.simpleName}")
    }.getOrDefault(emptyList())
}
