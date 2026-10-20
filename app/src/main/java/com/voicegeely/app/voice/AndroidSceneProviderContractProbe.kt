package com.voicegeely.app.voice

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Read-only probe for the OEM scene provider.
 *
 * It performs QUERY only. It never calls insert/update/delete/call and therefore cannot activate,
 * stop or otherwise mutate a scene. The result is diagnostic evidence for a later verified
 * integration step.
 */
class AndroidSceneProviderContractProbe(context: Context) {
    private val appContext = context.applicationContext

    fun probe(): SceneProviderSnapshot {
        val authority = Uri.parse(SceneProviderSnapshot.PROVIDER_URI).authority
            ?: return SceneProviderSnapshot(
                providerVisible = false,
                querySucceeded = false,
                detail = "Scene provider URI không hợp lệ."
            )
        val providerVisible = try {
            appContext.packageManager.resolveContentProvider(authority, 0) != null
        } catch (_: Throwable) {
            false
        }
        if (!providerVisible) {
            return SceneProviderSnapshot(providerVisible = false, querySucceeded = false)
        }

        return try {
            appContext.contentResolver.query(
                Uri.parse(SceneProviderSnapshot.PROVIDER_URI),
                null,
                null,
                null,
                null
            )?.use { cursor ->
                val columns = cursor.columnNames?.toList().orEmpty()
                val row = linkedMapOf<String, String?>()
                if (cursor.moveToFirst()) {
                    columns.forEachIndexed { index, name ->
                        row[name] = try {
                            if (cursor.isNull(index)) null else cursor.getString(index)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
                Log.i(TAG, "op=SCENE_PROVIDER_PROBE result=READ columns=${columns.joinToString(",")}")
                SceneProviderSnapshot(
                    providerVisible = true,
                    querySucceeded = true,
                    columns = columns,
                    firstRow = row
                )
            } ?: SceneProviderSnapshot(
                providerVisible = true,
                querySucceeded = false,
                detail = "Scene provider trả về cursor null."
            )
        } catch (_: SecurityException) {
            Log.w(TAG, "op=SCENE_PROVIDER_PROBE result=PERMISSION_DENIED")
            SceneProviderSnapshot(
                providerVisible = true,
                querySucceeded = false,
                detail = "Android không cho phép đọc scene provider."
            )
        } catch (t: Throwable) {
            Log.w(TAG, "op=SCENE_PROVIDER_PROBE result=FAILED type=${t.javaClass.simpleName}")
            SceneProviderSnapshot(
                providerVisible = true,
                querySucceeded = false,
                detail = "Không đọc được scene provider."
            )
        }
    }

    private companion object {
        const val TAG = "THTVoiceSceneProbe"
    }
}
