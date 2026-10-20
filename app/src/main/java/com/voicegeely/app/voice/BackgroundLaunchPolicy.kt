package com.voicegeely.app.voice

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Xiaomi/Redmi/POCO (MIUI, HyperOS) add their own permission "Hiển thị cửa sổ bật lên khi chạy
 * trong nền". While it is off, startActivity() from the Voice core silently does nothing when
 * another app is on screen: no exception, so the assistant used to say "Đã mở …" although nothing
 * opened. The head unit (Android 9, no such permission) is not affected.
 */
object BackgroundLaunchPolicy {
    private const val TAG = "THTVoiceLaunch"
    /** MIUI app-op "start activity in background". */
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    fun isXiaomi(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Redmi", ignoreCase = true) || Build.BRAND.equals("POCO", ignoreCase = true)

    /** false only when MIUI reports the permission as denied; null when unknown / not MIUI. */
    fun miuiBackgroundStartAllowed(context: Context): Boolean? {
        if (!isXiaomi()) return null
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val mode = method.invoke(ops, OP_BACKGROUND_START_ACTIVITY, Process.myUid(), context.packageName) as Int
            mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            Log.w(TAG, "miui_op_check_failed type=${t.javaClass.simpleName}")
            null
        }
    }

    /** An activity of THT Voice is visible, so Android/MIUI allow the launch anyway. */
    fun appInForeground(): Boolean = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (_: Throwable) {
        false
    }

    /** True when a launch from the background would be silently dropped by MIUI. */
    fun launchBlocked(context: Context): Boolean =
        miuiBackgroundStartAllowed(context) == false && !appInForeground()

    /** Opens the MIUI "Quyền khác" page for THT Voice, falling back to the app details page. */
    fun openPermissionSettings(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Throwable) {
                // next
            }
        }
        return false
    }
}
