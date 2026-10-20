package com.voicegeely.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.util.Log
import com.voicegeely.app.VoiceKeyDiagnostics.Kind
import com.voicegeely.app.VoiceKeyDiagnostics.Listener

/** Reads who else handles the voice key; analysis and wording live in [VoiceKeyDiagnostics]. */
object VoiceKeyProbe {
    private const val TAG = "VoiceKeyProbe"

    fun read(context: Context): VoiceKeyDiagnostics.Report {
        val pm = context.packageManager
        val listeners = buildList {
            VoiceKeyActions.ALL.forEach { action ->
                addAll(query(pm) { pm.queryBroadcastReceivers(Intent(action), 0) }
                    .map { it.toListener(pm, Kind.VOICE_KEY_RECEIVER) })
            }
            addAll(query(pm) { pm.queryIntentActivities(Intent(Intent.ACTION_ASSIST), 0) }
                .map { it.toListener(pm, Kind.ASSIST_ACTIVITY) })
            addAll(query(pm) { pm.queryIntentActivities(Intent(Intent.ACTION_VOICE_COMMAND), 0) }
                .map { it.toListener(pm, Kind.VOICE_COMMAND_ACTIVITY) })
        }
        val report = VoiceKeyDiagnostics.Report(
            selfPackage = context.packageName,
            listeners = listeners,
            defaultAssistant = secure(context, "assistant"),
            voiceInteractionService = secure(context, "voice_interaction_service")
        )
        listeners.forEach { Log.i(TAG, "${it.kind} ${it.packageName}/${it.className} prio=${it.priority} label=${it.label}") }
        Log.i(TAG, "assistant=${report.defaultAssistant} vis=${report.voiceInteractionService}")
        return report
    }

    private fun query(pm: PackageManager, block: () -> List<ResolveInfo>): List<ResolveInfo> =
        runCatching(block).getOrElse { Log.w(TAG, "query failed", it); emptyList() }

    private fun ResolveInfo.toListener(pm: PackageManager, kind: Kind): Listener {
        val info = activityInfo
        val label = runCatching { info.applicationInfo.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: info.packageName
        return Listener(info.packageName, info.name, label, kind, priority)
    }

    private fun secure(context: Context, key: String): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()?.takeIf { it.isNotBlank() }
}
