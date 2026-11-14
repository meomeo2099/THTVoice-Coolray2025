package com.voicegeely.app.ui

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.tht.voice.R
import com.voicegeely.app.VoiceAssistantService
import com.voicegeely.app.VoiceServiceEventBus
import com.voicegeely.app.kws.WakeEnrollment
import com.voicegeely.app.kws.WakePhrase
import com.voicegeely.app.kws.WakeVoices
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.ThtUi.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Nghe lệnh → "Dạy từ gọi" (R03 wake enrollment): start a training (optionally with a
 * name, optionally with music playing), see the trained voices, forget one or all. Up to 4 voices.
 */
class WakeTrainingSection(private val context: Context, private val prefs: UserVoicePreferences) {
    private val voices = WakeVoices(context)
    private var activeDialog: Dialog? = null
    private val container = ThtUi.vertical(context).apply {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { render() }
            override fun onViewDetachedFromWindow(v: View) { activeDialog?.dismiss() }
        })
    }

    fun view(): View { render(); return container }

    private fun render() {
        container.removeAllViews()
        val profile = prefs.wakePhrase
        val trained = voices.all()
        val subtitle = when {
            profile == WakePhrase.HEY_THT -> "Chọn từ gọi Hey Ge-ly hoặc Hello Ge-ly (và bấm Lưu) để dạy."
            trained.isEmpty() -> "Nói “${profile.displayLabel}” ${WakeEnrollment.TAKES} lần khi xe đỗ để app nhận giọng bạn tốt hơn, kể cả gọi nhỏ. Tối đa ${WakeVoices.MAX} người."
            else -> "Đã dạy ${trained.size}/${WakeVoices.MAX} giọng. Từ gọi tự dùng các giọng này."
        }
        val buttons = ThtUi.horizontal(context).apply {
            addView(ThtUi.smallButton(context, null, "Bắt đầu", ThtUi.SmallStyle.PRIMARY) { askName(music = false) },
                LinearLayout.LayoutParams(dp(context, 110), dp(context, 46)))
            addView(ThtUi.smallButton(context, null, "Khi có nhạc", ThtUi.SmallStyle.OUTLINE) { askName(music = true) },
                LinearLayout.LayoutParams(dp(context, 130), dp(context, 46)).apply { marginStart = dp(context, 8) })
        }
        container.addView(ThtUi.settingRow(context, R.drawable.ic_ui_mic, "Dạy từ gọi", subtitle, buttons), ThtUi.matchWrap(context))
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        trained.forEach { v ->
            val title = (v.name.ifBlank { "Giọng chính" }) + " · " + v.profile.displayLabel
            val sub = "Dạy ngày ${fmt.format(Date(v.createdMs))}${if (v.music) " · có nhạc" else ""} · ${v.lines.size} cách nghe"
            container.addView(ThtUi.settingRow(context, R.drawable.ic_ui_check_circle, title, sub,
                ThtUi.smallButton(context, null, "Quên", ThtUi.SmallStyle.OUTLINE) {
                    ThtUi.showChoice(context, R.drawable.ic_ui_close, "Quên giọng ${v.name.ifBlank { "chính" }}?", listOf("Quên giọng này", "Không"), 1) {
                        if (it == 0) { voices.forget(v.id); notifyService(); render() }
                    }
                }), ThtUi.matchWrap(context, 8))
        }
        if (trained.isNotEmpty()) container.addView(ThtUi.smallButton(context, null, "Quên tất cả giọng", ThtUi.SmallStyle.OUTLINE) {
            ThtUi.showChoice(context, R.drawable.ic_ui_close, "Quên tất cả giọng đã dạy?", listOf("Quên tất cả", "Không"), 1) {
                if (it == 0) { voices.forgetAll(); notifyService(); render() }
            }
        }, ThtUi.matchWrap(context, 8))

    }

    private fun notifyService() = send(Intent(context, VoiceAssistantService::class.java).apply { action = VoiceAssistantService.ACTION_SETTINGS_CHANGED })

    private fun send(intent: Intent): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        true
    }.getOrDefault(false)

    private fun askName(music: Boolean) {
        val d = Dialog(context)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val name = EditText(context).apply {
            hint = "Tên người nói (không bắt buộc)"; setSingleLine(true); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(Color.WHITE); setHintTextColor(ThtUi.TEXT_3)
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            background = ThtUi.panel(context, Color.rgb(6, 18, 34), ThtUi.ACCENT_2, 12f)
        }
        val tips = if (music) "Bật nhạc ở mức bạn hay nghe. Sau mỗi tiếng bíp, nói “${prefs.wakePhrase.displayLabel}” rõ ràng (${WakeEnrollment.TAKES} lần)."
            else "Đỗ xe, tắt nhạc. Sau mỗi tiếng bíp, nói “${prefs.wakePhrase.displayLabel}” như bình thường bạn gọi (${WakeEnrollment.TAKES} lần)."
        val root = ThtUi.vertical(context).apply {
            setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 18))
            background = ThtUi.panel(context, Color.rgb(8, 22, 40), Color.rgb(40, 110, 190), 20f)
            addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_mic, if (music) "Dạy từ gọi khi có nhạc" else "Dạy từ gọi", 20f))
            addView(ThtUi.text(context, tips, 15f, ThtUi.TEXT_2), ThtUi.matchWrap(context, 10))
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50)).apply { topMargin = dp(context, 12) })
            addView(ThtUi.horizontal(context).apply {
                addView(ThtUi.smallButton(context, null, "Hủy", ThtUi.SmallStyle.OUTLINE) { d.dismiss() }, LinearLayout.LayoutParams(0, dp(context, 50), 1f))
                addView(ThtUi.smallButton(context, null, "Bắt đầu", ThtUi.SmallStyle.PRIMARY) {
                    d.dismiss(); start(name.text.toString(), music)
                }, LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginStart = dp(context, 10) })
            }, ThtUi.matchWrap(context, 14))
        }
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((context.resources.displayMetrics.widthPixels * 0.5f).toInt().coerceAtLeast(dp(context, 340)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        d.show()
    }

    private fun start(name: String, music: Boolean) {
        activeDialog?.dismiss()
        var finished = false
        val d = Dialog(context)
        activeDialog = d
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val big = TextView(context).apply { textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; text = "Chuẩn bị…" }
        val small = ThtUi.text(context, "Giữ yên lặng, chờ tiếng bíp.", 15f, ThtUi.TEXT_2, gravity = Gravity.CENTER)
        val close = ThtUi.smallButton(context, null, "Hủy", ThtUi.SmallStyle.OUTLINE) { d.dismiss() }
        val root = ThtUi.vertical(context, Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 18))
            background = ThtUi.panel(context, Color.rgb(8, 22, 40), Color.rgb(40, 110, 190), 20f)
            addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_mic, "Dạy từ gọi", 20f), ThtUi.matchWrap(context))
            addView(big, ThtUi.matchWrap(context, 16))
            addView(small, ThtUi.matchWrap(context, 8))
            addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50)).apply { topMargin = dp(context, 16) })
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(VoiceAssistantService.EXTRA_WAKE_TRAIN_STATE) ?: return
                val text = intent.getStringExtra(VoiceAssistantService.EXTRA_WAKE_TRAIN_TEXT).orEmpty()
                when (state) {
                    "recording" -> { big.text = text; small.text = "Nói sau tiếng bíp, rồi chờ lần tiếp theo." }
                    "processing", "started" -> { big.text = text; small.text = "" }
                    "done" -> { finished = true; close.text = "Đóng"; big.text = "✓ $text"; small.text = "Từ gọi sẽ dùng giọng này từ bây giờ."; close.visibility = View.VISIBLE; render() }
                    "failed" -> { finished = true; close.text = "Đóng"; big.text = "Chưa xong"; small.text = text; close.visibility = View.VISIBLE }
                }
            }
        }
        VoiceServiceEventBus.registerReceiver(receiver, IntentFilter(VoiceAssistantService.ACTION_WAKE_TRAIN_STATUS))
        val timeout = Runnable {
            if (!finished) {
                finished = true
                send(Intent(context, VoiceAssistantService::class.java).apply { action = VoiceAssistantService.ACTION_WAKE_TRAIN_CANCEL })
                big.text = "Chưa xong"
                small.text = "Đã hết thời gian học giọng. Hãy đóng và thử lại."
                close.text = "Đóng"
                VoiceServiceEventBus.unregisterReceiver(receiver)
            }
        }
        d.setOnDismissListener {
            root.removeCallbacks(timeout)
            VoiceServiceEventBus.unregisterReceiver(receiver)
            if (!finished) send(Intent(context, VoiceAssistantService::class.java).apply { action = VoiceAssistantService.ACTION_WAKE_TRAIN_CANCEL })
            if (activeDialog === d) activeDialog = null
        }
        d.setCancelable(false)
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((context.resources.displayMetrics.widthPixels * 0.5f).toInt().coerceAtLeast(dp(context, 340)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        d.show()
        val started = send(Intent(context, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_WAKE_TRAIN
            putExtra(VoiceAssistantService.EXTRA_WAKE_TRAIN_NAME, name)
            putExtra(VoiceAssistantService.EXTRA_WAKE_TRAIN_MUSIC, music)
        })
        if (started) root.postDelayed(timeout, 60_000) else {
            finished = true
            big.text = "Chưa xong"
            small.text = "Không khởi động được học giọng. Hãy thử lại."
            close.text = "Đóng"
        }
    }
}
