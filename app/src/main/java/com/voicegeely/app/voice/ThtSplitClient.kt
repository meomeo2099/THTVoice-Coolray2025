package com.voicegeely.app.voice

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.voicegeely.app.settings.UserVoicePreferences
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android client for THT Split API v2 (com.tht.split, docs/SPLIT_SCREEN.md).
 *
 * Explicit broadcast to SignatureVoiceReceiver when Android granted CONTROL_SPLIT (same signer),
 * otherwise to VoiceReceiver with the token the user pasted. The reply comes back through a
 * one-shot PendingIntent to the non-exported [SplitResultReceiver]; only a requestId still waiting
 * here is accepted. The token is never logged or spoken.
 */
class ThtSplitClient(
    private val context: Context,
    private val tokenProvider: () -> String? = { UserVoicePreferences(context).splitToken },
    private val waitMs: Long = WAIT_MS
) : SplitScreenClient {

    override fun isSplitInstalled(): Boolean = splitPackage() != null

    /** Coolray 2025 Split (`com.tht.split.coolray`) first, then the shared `com.tht.split`. */
    private fun splitPackage(): String? = SPLIT_PACKAGES.firstOrNull { isPackageInstalled(it) }

    override fun isPackageInstalled(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun send(command: String, extras: Map<String, Any>): SplitReply {
        val splitPackage = splitPackage() ?: return SplitReply(SplitReply.SEND_FAILED, message = SplitScreenExecutor.NOT_INSTALLED)
        val signature = context.packageManager.checkPermission("$splitPackage$PERMISSION_SUFFIX", context.packageName) ==
            PackageManager.PERMISSION_GRANTED
        val token = if (signature) null else tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }
        if (!signature && token == null) return SplitReply(SplitReply.NO_AUTH)

        val requestId = UUID.randomUUID().toString()
        val waiter = Waiter()
        pending[requestId] = waiter
        val resultIntent = Intent(context, SplitResultReceiver::class.java)
            .setAction(RESULT_ACTION)
            .setData(Uri.parse("thtvoice://split/$requestId"))
        var flags = PendingIntent.FLAG_ONE_SHOT
        if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
        val result = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), resultIntent, flags)

        val request = Intent(COMMAND_ACTION)
            .setComponent(ComponentName(splitPackage, if (signature) SIGNATURE_RECEIVER else TOKEN_RECEIVER))
            .putExtra("requestId", requestId)
            .putExtra("result", result)
            .putExtra("command", command)
        extras.forEach { (key, value) ->
            when (value) {
                is Int -> request.putExtra(key, value)
                else -> request.putExtra(key, value.toString())
            }
        }
        token?.let { request.putExtra("token", it) }
        Log.i(TAG, "split request command=$command extras=${extras.keys} via=${if (signature) "signature" else "token"}")
        return try {
            context.sendBroadcast(request)
            val arrived = waiter.latch.await(waitMs, TimeUnit.MILLISECONDS)
            val reply = waiter.reply
            if (arrived && reply != null) reply else SplitReply(SplitReply.TIMEOUT)
        } catch (t: Throwable) {
            SplitReply(SplitReply.SEND_FAILED, message = t.message)
        } finally {
            pending.remove(requestId)
            // A late reply must not reach a request that already gave up.
            result.cancel()
        }.also { Log.i(TAG, "split reply command=$command status=${it.status} code=${it.code}") }
    }

    private class Waiter {
        val latch = CountDownLatch(1)
        @Volatile var reply: SplitReply? = null
    }

    companion object {
        private const val TAG = "ThtSplitClient"
        const val SPLIT_PACKAGE = "com.tht.split"
        const val SPLIT_COOLRAY_PACKAGE = "com.tht.split.coolray"
        val SPLIT_PACKAGES = listOf(SPLIT_COOLRAY_PACKAGE, SPLIT_PACKAGE)
        const val COMMAND_ACTION = "com.tht.split.COMMAND"
        const val SIGNATURE_RECEIVER = "com.tht.split.SignatureVoiceReceiver"
        const val TOKEN_RECEIVER = "com.tht.split.VoiceReceiver"
        /** Split declares `${applicationId}.permission.CONTROL_SPLIT`. */
        const val PERMISSION_SUFFIX = ".permission.CONTROL_SPLIT"
        const val RESULT_ACTION = "com.tht.voice.SPLIT_RESULT"
        /** Below the coordinator's 15 s command timeout; a slower first ADB link ends as TIMEOUT. */
        const val WAIT_MS = 12_000L

        private val pending = ConcurrentHashMap<String, Waiter>()
        private val requestCodes = AtomicInteger(4100)

        /** Called by [SplitResultReceiver]; ignores ids that are not waiting any more. */
        internal fun deliver(intent: Intent) {
            val id = intent.getStringExtra("requestId") ?: return
            val waiter = pending[id] ?: return
            waiter.reply = SplitReply(
                status = intent.getStringExtra("status") ?: SplitReply.ERROR,
                code = intent.getStringExtra("code"),
                message = intent.getStringExtra("message"),
                verified = intent.getBooleanExtra("verified", false)
            )
            waiter.latch.countDown()
        }
    }
}

/** Non-exported; reached only through the PendingIntent [ThtSplitClient] hands to THT Split. */
class SplitResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ThtSplitClient.RESULT_ACTION) return
        val id = intent.getStringExtra("requestId") ?: return
        if (intent.data?.lastPathSegment != id) return
        ThtSplitClient.deliver(intent)
    }
}
