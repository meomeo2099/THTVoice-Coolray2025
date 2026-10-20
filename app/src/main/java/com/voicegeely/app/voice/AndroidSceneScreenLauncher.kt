package com.voicegeely.app.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Opens the OEM rest / camping scene screen through its explicit activity
 * (base-platform OEM activity paths; not verified on Coolray). The result only confirms that
 * Android started the screen; scene state is never read or changed here.
 */
class AndroidSceneScreenLauncher(context: Context) {
    private val appContext = context.applicationContext

    fun open(scene: SceneType): AndroidBackendResult {
        val component = ComponentName(SCENE_PACKAGE, activityFor(scene))
        val label = labelFor(scene)
        val activity = try {
            appContext.packageManager.getActivityInfo(component, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.i(TAG, "op=SCENE_OPEN scene=$scene result=ACTIVITY_NOT_VISIBLE")
            return AndroidBackendResult(false, false, detail = "Xe này không có màn hình chế độ $label.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=SCENE_OPEN scene=$scene result=PROBE_FAILED type=${t.javaClass.simpleName}")
            return AndroidBackendResult(false, false, detail = "Không kiểm tra được màn hình chế độ $label.")
        }
        val permissionGranted = activity.permission.isNullOrBlank() ||
            appContext.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED
        if (!activity.exported || !activity.enabled || !permissionGranted) {
            Log.i(TAG, "op=SCENE_OPEN scene=$scene exported=${activity.exported} enabled=${activity.enabled} permissionGranted=$permissionGranted result=NOT_ALLOWED")
            return AndroidBackendResult(false, false, detail = "Ứng dụng chưa có quyền mở màn hình chế độ $label.")
        }
        return try {
            appContext.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i(TAG, "op=SCENE_OPEN scene=$scene result=STARTED")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = "Đã mở màn hình chế độ $label",
                detail = "Chỉ mở màn hình; chưa xác minh trạng thái chế độ $label."
            )
        } catch (_: SecurityException) {
            Log.w(TAG, "op=SCENE_OPEN scene=$scene result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép mở màn hình chế độ $label.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=SCENE_OPEN scene=$scene result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không mở được màn hình chế độ $label.")
        }
    }

    companion object {
        private const val TAG = "THTVoiceScene"
        const val SCENE_PACKAGE = "com.flyme.auto.scenedirector"

        fun activityFor(scene: SceneType): String = when (scene) {
            SceneType.REST -> "com.flyme.auto.scenedirector.activities.nap.RestActivity"
            SceneType.CAMPING -> "com.flyme.auto.scenedirector.activities.camping.CampingActivity"
        }

        fun labelFor(scene: SceneType): String = if (scene == SceneType.REST) "nghỉ" else "cắm trại"
    }
}
