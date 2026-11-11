package com.voicegeely.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.tht.voice.R
import com.voicegeely.app.settings.TtsVoice
import com.voicegeely.app.tts.Mp3VoicePacks
import com.voicegeely.app.ui.ThtUi.dp

internal object Mp3VoiceSettingsRow {
    fun build(
        context: Context,
        initial: TtsVoice,
        onSelect: (TtsVoice) -> Unit,
        onPreview: (TtsVoice) -> Unit,
        onLicenseRequired: () -> Unit
    ): View {
        val voices = listOf(TtsVoice.NGOC_HUYEN, TtsVoice.DIEM_TRINH)
        var selected = initial
        val status = ThtUi.text(context, "Đang đọc gói đã cài…", 14f, ThtUi.TEXT_2)
        val activityHandler = Handler(Looper.getMainLooper())
        var loading = false
        val updateStatus = ThtUi.text(context, "", 13f, ThtUi.TEXT_2).apply { visibility = View.GONE }
        var statusRequest = 0
        fun refreshInstalled() {
            val request = ++statusRequest
            val voice = selected
            status.text = "Đang đọc gói đã cài…"
            Thread {
                val installed = Mp3VoicePacks.status(context.applicationContext, voice)
                activityHandler.post { if (selected == voice && request == statusRequest) status.text = installed }
            }.apply { name = "THTVoice-Mp3Status"; isDaemon = true }.start()
        }
        val root = ThtUi.vertical(context)
        root.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
        root.background = ThtUi.panel(context, ThtUi.PANEL_2, ThtUi.BORDER, 14f)
        root.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_headphones, "Giọng phản hồi MP3", 17f))
        root.addView(ThtUi.segmented(context, listOf("Ngọc Huyền", "Diễm Trinh"), voices.indexOf(selected).coerceAtLeast(0), 108) { index ->
            selected = voices[index]
            onSelect(selected)
            updateStatus.visibility = View.GONE
            refreshInstalled()
        }, ThtUi.matchWrap(context, 10))
        root.addView(status, ThtUi.matchWrap(context, 10))
        root.addView(updateStatus, ThtUi.matchWrap(context, 6))
        val actions = LinearLayout(context).apply {
            orientation = if (ResponsiveContext.spec(context).settingsColumns == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        val preview = ThtUi.smallButton(context, R.drawable.ic_ui_play, "Nghe thử", ThtUi.SmallStyle.PRIMARY) { onPreview(selected) }
        val update = ThtUi.smallButton(context, null, "Tải gói", ThtUi.SmallStyle.OUTLINE) {
            if (!loading) {
                loading = true
                val voice = selected
                updateStatus.visibility = View.VISIBLE
                updateStatus.text = "Đang tải và kiểm tra gói…"
                Thread {
                    val result = runCatching { Mp3VoicePacks.update(context.applicationContext, voice) }
                    activityHandler.post {
                        loading = false
                        val error = result.exceptionOrNull()
                        updateStatus.text = result.getOrNull() ?: error?.message?.take(120) ?: "Không tải được gói giọng."
                        updateStatus.visibility = if (selected == voice) View.VISIBLE else View.GONE
                        refreshInstalled()
                        if (root.isAttachedToWindow && selected == voice && error is Mp3VoicePacks.LicenseRequired) {
                            onLicenseRequired()
                        }
                    }
                }.apply { name = "THTVoice-Mp3Update"; isDaemon = true }.start()
            }
        }
        listOf(preview, update).forEachIndexed { i, button ->
            button.minHeight = dp(context, 48)
            val params = if (actions.orientation == LinearLayout.VERTICAL) ThtUi.matchWrap(context, if (i == 0) 0 else 8)
                else ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = dp(context, 8) }
            actions.addView(button, params)
        }
        root.addView(actions, ThtUi.matchWrap(context, 10))
        root.addView(ThtUi.text(context, "Bấm Lưu để dùng giọng đã chọn cho các lệnh.", 13f, ThtUi.TEXT_2),
            ThtUi.matchWrap(context, 8))
        refreshInstalled()
        return root
    }
}
