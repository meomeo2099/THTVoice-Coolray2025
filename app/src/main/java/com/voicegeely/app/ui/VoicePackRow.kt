package com.voicegeely.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.tht.voice.R
import com.voicegeely.app.tts.OfflineTtsModelInstaller
import com.voicegeely.app.tts.TtsPackManager
import com.voicegeely.app.ui.ThtUi.dp

/**
 * Settings row for the downloadable Kokoro voice pack: status, progress bar with percent,
 * "Tải về" / "Cài từ tệp" / "Hủy" / "Xóa". Hidden when the APK already bundles the voice.
 */
object VoicePackRow {
    fun build(
        context: Context,
        onPickFile: () -> Unit,
        onInstalled: () -> Unit
    ): View? {
        val pack = OfflineTtsModelInstaller.packInfo(context) ?: return null
        val status = ThtUi.text(context, "", 12.5f, ThtUi.TEXT_2, maxLines = 2)
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ThtUi.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(40, 60, 86))
            visibility = View.GONE
        }
        val buttons = ThtUi.horizontal(context)

        val texts = ThtUi.vertical(context)
        texts.addView(ThtUi.text(context, "Gói giọng đọc Ngọc Huyền / Diễm Trinh", 16f, Color.WHITE, bold = true, maxLines = 1))
        texts.addView(status, ThtUi.matchWrap(context, 3))
        texts.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 10)).apply { topMargin = dp(context, 6) })

        val row = ThtUi.horizontal(context).apply {
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            background = ThtUi.panel(context, ThtUi.PANEL_2, Color.rgb(22, 42, 64), 14f)
            addView(ThtUi.icon(context, R.drawable.ic_ui_download, ThtUi.ACCENT, 30))
            addView(texts, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 16); marginEnd = dp(context, 12) })
            addView(buttons)
            minimumHeight = dp(context, 78)
        }

        fun button(label: String, style: ThtUi.SmallStyle, action: () -> Unit) {
            buttons.addView(ThtUi.smallButton(context, null, label, style, action),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 44)).apply {
                    if (buttons.childCount > 0) marginStart = dp(context, 8)
                })
        }

        lateinit var render: (TtsPackManager.State) -> Unit
        render = { state ->
            buttons.removeAllViews()
            val installed = TtsPackManager.isInstalled(context)
            when (state) {
                is TtsPackManager.State.Running -> {
                    status.text = "${state.stage} ${state.percent}%"
                    status.setTextColor(ThtUi.ACCENT)
                    progress.visibility = View.VISIBLE
                    progress.progress = state.percent
                    button("Hủy", ThtUi.SmallStyle.OUTLINE) { TtsPackManager.cancel() }
                }
                else -> {
                    progress.visibility = View.GONE
                    status.setTextColor(ThtUi.TEXT_2)
                    status.text = when {
                        installed -> "Đã cài • khoảng ${pack.sizeMb} MB"
                        state is TtsPackManager.State.Failed -> state.message
                        else -> "Chưa tải • khoảng ${pack.sizeMb} MB (chỉ tải một lần)"
                    }
                    if (state is TtsPackManager.State.Failed && !installed) status.setTextColor(ThtUi.WARN)
                    if (installed) {
                        status.setTextColor(ThtUi.OK)
                        button("Xóa", ThtUi.SmallStyle.OUTLINE) {
                            TtsPackManager.remove(context)
                            onInstalled()
                        }
                    } else {
                        button("Tải về", ThtUi.SmallStyle.PRIMARY) { TtsPackManager.download(context, onInstalled) }
                        button("Cài từ tệp", ThtUi.SmallStyle.OUTLINE) { onPickFile() }
                    }
                }
            }
        }
        val listener: (TtsPackManager.State) -> Unit = { render(it) }
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { TtsPackManager.addListener(listener); render(TtsPackManager.state) }
            override fun onViewDetachedFromWindow(v: View) { TtsPackManager.removeListener(listener) }
        })
        render(TtsPackManager.state)
        return row
    }
}
