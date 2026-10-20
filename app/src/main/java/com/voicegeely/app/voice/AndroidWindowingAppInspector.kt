package com.voicegeely.app.voice

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Read-only PackageManager probe for target-app PiP preconditions.
 *
 * Public SDK data used here can prove only two things: the platform advertises
 * PiP and one of the target packages exposes a launch Activity visible to this
 * app. It does not start an Activity, change task/window state, inspect hidden
 * manifest flags, or claim the target can enter PiP.
 */
class AndroidWindowingAppInspector(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun inspectPip(packageCandidates: List<String>): AppPipCapability {
        val systemDeclaresPip = try {
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } catch (t: Throwable) {
            Log.w(TAG, "op=APP_PIP_PROBE result=SYSTEM_PROBE_FAILED type=${t.javaClass.simpleName}")
            false
        }

        if (!systemDeclaresPip) {
            return AppPipCapability(AppPipCapabilityState.SYSTEM_UNSUPPORTED)
        }

        for (packageName in packageCandidates.distinct()) {
            val launchIntent = try {
                packageManager.getLaunchIntentForPackage(packageName)
            } catch (t: Throwable) {
                Log.w(TAG, "op=APP_PIP_PROBE package=$packageName result=LAUNCH_QUERY_FAILED type=${t.javaClass.simpleName}")
                null
            } ?: continue

            val component = launchIntent.component
            val state = AppPipCapabilityClassifier.classify(
                systemDeclaresPip = true,
                appVisible = component != null
            )
            if (component == null) continue

            Log.i(
                TAG,
                "op=APP_PIP_PROBE package=$packageName activity=${component.className} " +
                    "state=$state runtimePip=UNVERIFIED"
            )
            return AppPipCapability(
                state = state,
                packageName = packageName,
                activityName = component.className
            )
        }

        Log.i(TAG, "op=APP_PIP_PROBE result=APP_NOT_VISIBLE candidates=${packageCandidates.size}")
        return AppPipCapability(
            AppPipCapabilityClassifier.classify(
                systemDeclaresPip = true,
                appVisible = false
            )
        )
    }

    private companion object {
        const val TAG = "THTVoiceWindowing"
    }
}
