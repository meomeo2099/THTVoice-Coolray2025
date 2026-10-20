package com.voicegeely.app.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {
    fun compactRequestCode(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val material = listOf(
            androidId,
            Build.BOARD.orEmpty(),
            Build.HARDWARE.orEmpty(),
            Build.DEVICE.orEmpty(),
            context.packageName
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02X".format(it) }
    }

    fun formattedRequestCode(context: Context): String =
        compactRequestCode(context).chunked(4).joinToString("-")
}
