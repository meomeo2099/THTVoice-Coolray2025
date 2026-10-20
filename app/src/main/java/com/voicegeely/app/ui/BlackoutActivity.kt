package com.voicegeely.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.voicegeely.app.voice.AndroidBackendResult
import com.voicegeely.app.voice.VerificationSource
import java.lang.ref.WeakReference

/**
 * Full-screen black cover ("tắt màn hình" on a head unit that cannot power the panel
 * off from a normal app). Any touch or the voice command "bật màn hình" removes it.
 */
class BlackoutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val cover = View(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        setContentView(cover)
        current = WeakReference(this)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: WeakReference<BlackoutActivity>? = null

        fun set(context: Context, on: Boolean): AndroidBackendResult = try {
            if (on) {
                context.applicationContext.startActivity(
                    Intent(context.applicationContext, BlackoutActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } else {
                current?.get()?.let { activity -> activity.runOnUiThread { activity.finish() } }
            }
            AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, actualValue = if (on) "on" else "off")
        } catch (_: Throwable) {
            AndroidBackendResult(false, false, detail = "Không đổi được màn hình đen.")
        }
    }
}
