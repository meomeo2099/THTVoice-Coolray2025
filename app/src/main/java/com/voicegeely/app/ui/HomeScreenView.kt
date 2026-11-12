package com.voicegeely.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tht.voice.R
import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.settings.MediaAppSettings
import com.voicegeely.app.settings.OverlayPosition
import com.voicegeely.app.settings.TtsVoice
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.ThtUi.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One tile of "Hệ thống giọng nói hiện tại": value text + status chip that re-render live. */
class HomeStatusTile(val root: View, private val value: TextView, private val chipHolder: FrameLayout) {
    fun render(valueText: String, chipText: String, state: ThtUi.ChipState) {
        value.text = valueText
        chipHolder.removeAllViews()
        chipHolder.addView(ThtUi.chip(root.context, chipText, state), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }
}

class HomeScreenBinding(
    val root: View,
    private val wakeHint: TextView,
    private val overallChip: FrameLayout,
    private val wake: HomeStatusTile,
    private val popup: HomeStatusTile,
    private val media: HomeStatusTile,
    private val navigation: HomeStatusTile,
    private val asr: HomeStatusTile,
    private val tts: HomeStatusTile,
    private val recentList: LinearLayout,
    private val popupStatus: TextView
) {
    fun render(
        runtime: AssistantRuntimeSnapshot,
        voicePreferences: UserVoicePreferences,
        mediaSettings: MediaAppSettings
    ) {
        val context = root.context
        val phrase = voicePreferences.wakePhrase.displayLabel
        wakeHint.text = if (voicePreferences.wakeWordEnabled) "Nói “$phrase” để bắt đầu" else "Bấm mic hoặc nút Voice để bắt đầu"

        wake.render(
            phrase,
            when {
                !voicePreferences.wakeWordEnabled -> "Đã tắt"
                runtime.kwsReady -> "Đang hoạt động"
                else -> "Chưa sẵn sàng"
            },
            when {
                !voicePreferences.wakeWordEnabled -> ThtUi.ChipState.OFF
                runtime.kwsReady -> ThtUi.ChipState.OK
                else -> ThtUi.ChipState.WARN
            }
        )
        popup.render(
            if (voicePreferences.overlayEnabled) "${overlayPositionLabel(voicePreferences.overlayPosition)} • tự ẩn ${voicePreferences.overlayAutoHideMs / 1000}s" else "Không hiển thị",
            if (voicePreferences.overlayEnabled) "Đang bật" else "Đã tắt",
            if (voicePreferences.overlayEnabled) ThtUi.ChipState.OK else ThtUi.ChipState.OFF
        )
        media.render(mediaLabel(mediaSettings.defaultMediaApp), "Mặc định", ThtUi.ChipState.OK)
        navigation.render(navigationLabel(voicePreferences.defaultNavigationApp), "Mặc định", ThtUi.ChipState.OK)
        asr.render(
            "Offline • tiếng Việt",
            if (runtime.asrReady) "Đang hoạt động" else "Chưa sẵn sàng",
            if (runtime.asrReady) ThtUi.ChipState.OK else ThtUi.ChipState.WARN
        )
        val voiceName = if (voicePreferences.ttsVoice == TtsVoice.DIEM_TRINH) "Diễm Trinh (giọng nữ)" else "Ngọc Huyền (giọng nữ)"
        when {
            !voicePreferences.voiceFeedbackEnabled -> tts.render("Tiếng bíp", "Giọng nói tắt", ThtUi.ChipState.OFF)
            runtime.ttsReady -> tts.render(voiceName, "Đang hoạt động", ThtUi.ChipState.OK)
            else -> tts.render(
                runtime.ttsDetail?.takeIf { it.isNotBlank() }?.take(TTS_DETAIL_MAX) ?: voiceName,
                "Chưa sẵn sàng",
                ThtUi.ChipState.WARN
            )
        }

        val allOk = runtime.asrReady && (!voicePreferences.wakeWordEnabled || runtime.kwsReady) &&
            (!voicePreferences.voiceFeedbackEnabled || runtime.ttsReady)
        overallChip.removeAllViews()
        overallChip.addView(ThtUi.chip(context, if (allOk) "Đang hoạt động" else "Cần kiểm tra", if (allOk) ThtUi.ChipState.OK else ThtUi.ChipState.WARN))

        popupStatus.text = if (voicePreferences.overlayEnabled) "Sẵn sàng" else "Đã tắt"
        popupStatus.setTextColor(if (voicePreferences.overlayEnabled) ThtUi.OK else ThtUi.TEXT_3)

        recentList.removeAllViews()
        if (runtime.history.isEmpty()) {
            recentList.addView(ThtUi.text(context, "Chưa có lịch sử thực hiện lệnh.", 14f, ThtUi.TEXT_2), ThtUi.matchWrap(context, 8))
        } else {
            val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            runtime.history.take(3).forEachIndexed { index, item ->
                if (index > 0) recentList.addView(View(context).apply { setBackgroundColor(ThtUi.BORDER) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
                recentList.addView(ThtUi.horizontal(context).apply {
                    setPadding(0, dp(context, 9), 0, dp(context, 9))
                    addView(ThtUi.icon(context, historyIcon(item.command), ThtUi.ACCENT, 22))
                    addView(ThtUi.text(context, item.command, 15f, Color.WHITE, maxLines = 1),
                        ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14) })
                    addView(ThtUi.text(context, formatter.format(Date(item.timestampMs)), 13f, ThtUi.TEXT_2, maxLines = 1))
                })
            }
        }
    }

    private fun historyIcon(command: String): Int {
        val c = command.lowercase()
        return when {
            listOf("nhiệt", "điều hòa", "quạt", "gió", "sấy").any { it in c } -> R.drawable.ic_ui_snow
            listOf("kính", "cửa sổ").any { it in c } -> R.drawable.ic_ui_window
            listOf("đường", "bản đồ", "map").any { it in c } -> R.drawable.ic_ui_place
            listOf("nhạc", "youtube", "bài").any { it in c } -> R.drawable.ic_ui_music
            c.startsWith("mở") -> R.drawable.ic_ui_apps
            else -> R.drawable.ic_ui_mic
        }
    }

    private fun overlayPositionLabel(position: OverlayPosition): String = when (position) {
        OverlayPosition.TOP -> "Trên"
        OverlayPosition.CENTER -> "Giữa"
        OverlayPosition.BOTTOM -> "Dưới"
    }

    private fun mediaLabel(app: DefaultMediaApp): String = when (app) {
        DefaultMediaApp.AUTO -> "Tự động"
        DefaultMediaApp.YOUTUBE -> "YouTube"
        DefaultMediaApp.VANCED -> "Vanced"
        DefaultMediaApp.REVANCED -> "ReVanced"
        DefaultMediaApp.MORPHE -> "Morphe"
        DefaultMediaApp.SMARTTUBE -> "SmartTube"
        DefaultMediaApp.YOUTUBE_MUSIC -> "YouTube Music"
        DefaultMediaApp.ZING_MP3 -> "Zing MP3"
        DefaultMediaApp.SPOTIFY -> "Spotify"
    }

    private fun navigationLabel(app: DefaultNavigationApp): String = when (app) {
        DefaultNavigationApp.AUTO -> "Tự động"
        DefaultNavigationApp.GOOGLE_MAPS -> "Google Maps"
        DefaultNavigationApp.VIETMAP -> "Vietmap Live"
        DefaultNavigationApp.WAZE -> "Waze"
    }
}

object HomeScreenView {
    fun build(
        context: Context,
        runtime: AssistantRuntimeSnapshot,
        voicePreferences: UserVoicePreferences,
        mediaSettings: MediaAppSettings,
        onAssistant: () -> Unit,
        onApps: () -> Unit,
        onSettings: () -> Unit,
        onSupport: () -> Unit
    ): HomeScreenBinding {
        val spec = ResponsiveContext.spec(context)
        val narrow = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
        val root = ThtUi.vertical(context)

        val wakeHint = ThtUi.text(context, "", 17f, Color.WHITE, maxLines = 1)
        val pill = ThtUi.horizontal(context).apply {
            setPadding(dp(context, 20), dp(context, 12), dp(context, 28), dp(context, 12))
            background = ThtUi.gradient(context, intArrayOf(android.graphics.Color.rgb(8, 40, 80), android.graphics.Color.rgb(6, 24, 50)), 30f, ThtUi.ACCENT_2)
            addView(ThtUi.icon(context, R.drawable.ic_ui_wave, ThtUi.ACCENT, 26))
            addView(wakeHint, ThtUi.wrap().apply { marginStart = dp(context, 14) })
            isClickable = true
            setOnClickListener { onAssistant() }
        }
        root.addView(ThtUi.hero(
            context,
            title = "Xin chào!",
            subtitle = "Chào mừng bạn đến với THT Voice",
            body = "Trợ lý giọng nói tiếng Việt giúp bạn điều khiển xe và mở ứng dụng nhanh chóng, an toàn và thuận tiện hơn trên mọi hành trình.",
            heightDp = spec.fitDp(320, 1f, 240, heightFraction = 0.3f),
            extra = pill
        ))

        root.addView(ThtUi.sectionTitle(context, null, "Thao tác nhanh", 21f), ThtUi.matchWrap(context, 18))
        val quick = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        listOf(
            ThtUi.navCard(context, R.drawable.ic_ui_mic, ThtUi.Tone.BLUE, "Mở Trợ lý", "Kích hoạt THT Voice bằng giọng nói", onClick = onAssistant),
            ThtUi.navCard(context, R.drawable.ic_ui_apps, ThtUi.Tone.PURPLE, "Mở Ứng dụng", "Mở nhanh các ứng dụng giải trí, bản đồ, ...", onClick = onApps),
            ThtUi.navCard(context, R.drawable.ic_ui_gear, ThtUi.Tone.TEAL, "Mở Cài đặt", "Tùy chỉnh các tính năng của THT Voice", onClick = onSettings),
            ThtUi.navCard(context, R.drawable.ic_ui_heart, ThtUi.Tone.PINK, "Ủng hộ THT Voice", "Đồng hành cùng chúng tôi phát triển tốt hơn", onClick = onSupport)
        ).forEachIndexed { index, card ->
            quick.addView(card, if (narrow) ThtUi.matchWrap(context, if (index > 0) 10 else 0)
            else ThtUi.weight(1f, dp(context, 104)).apply { if (index > 0) marginStart = dp(context, 14) })
        }
        root.addView(quick, ThtUi.matchWrap(context, 12))

        val overallChip = FrameLayout(context)
        root.addView(ThtUi.horizontal(context).apply {
            addView(ThtUi.text(context, "Hệ thống giọng nói hiện tại", 21f, Color.WHITE, bold = true, maxLines = 1))
            addView(overallChip, ThtUi.wrap().apply { marginStart = dp(context, 14) })
        }, ThtUi.matchWrap(context, 20))

        fun tile(iconRes: Int, title: String): HomeStatusTile {
            val value = ThtUi.text(context, "", 14.5f, Color.WHITE, maxLines = 2)
            val chipHolder = FrameLayout(context)
            val card = ThtUi.vertical(context).apply {
                setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12))
                background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 16f)
                addView(ThtUi.horizontal(context, Gravity.TOP).apply {
                    addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 30))
                    val t = ThtUi.vertical(context)
                    t.addView(ThtUi.text(context, title, 13.5f, ThtUi.TEXT_2, maxLines = 1))
                    t.addView(value, ThtUi.matchWrap(context, 4))
                    addView(t, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
                })
                addView(chipHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(context, 10)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            }
            return HomeStatusTile(card, value, chipHolder)
        }
        val wake = tile(R.drawable.ic_ui_mic, "Từ đánh thức")
        val popup = tile(R.drawable.ic_ui_chat, "Phản hồi popup")
        val media = tile(R.drawable.ic_ui_music, "Ứng dụng nhạc mặc định")
        val navigation = tile(R.drawable.ic_ui_place, "Bản đồ mặc định")
        val asr = tile(R.drawable.ic_ui_wave, "Nhận dạng giọng nói")
        val tts = tile(R.drawable.ic_ui_headphones, "Giọng trợ lý")
        val tiles = listOf(wake, popup, media, navigation, asr, tts)
        val perRow = if (narrow) 2 else 6
        tiles.chunked(perRow).forEachIndexed { rowIndex, chunk ->
            val row = ThtUi.horizontal(context, Gravity.TOP)
            chunk.forEachIndexed { index, t ->
                row.addView(t.root, ThtUi.weight(1f, ViewGroup.LayoutParams.MATCH_PARENT).apply { if (index > 0) marginStart = dp(context, 12) })
            }
            root.addView(row, ThtUi.matchWrap(context, if (rowIndex == 0) 12 else 10))
        }

        val bottom = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val recent = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 12))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        recent.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_history, "Hoạt động gần đây", 18f))
        val recentList = ThtUi.vertical(context)
        recent.addView(recentList, ThtUi.matchWrap(context, 6))

        val popupStatus = ThtUi.text(context, "", 14f, ThtUi.OK, maxLines = 1)
        val preview = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 16))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
            addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_chat, "Popup giọng nói thông minh", 18f, trailing = popupStatus))
            addView(PopupPreview.build(context, "THT Voice", "Tôi đang lắng nghe..."),
                ThtUi.matchWrap(context, 16).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        if (narrow) {
            bottom.addView(recent, ThtUi.matchWrap(context))
            bottom.addView(preview, ThtUi.matchWrap(context, 12))
        } else {
            bottom.addView(recent, ThtUi.weight(0.55f, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = dp(context, 14) })
            bottom.addView(preview, ThtUi.weight(0.45f, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(bottom, ThtUi.matchWrap(context, 16))

        return HomeScreenBinding(root, wakeHint, overallChip, wake, popup, media, navigation, asr, tts, recentList, popupStatus)
            .also { it.render(runtime, voicePreferences, mediaSettings) }
    }
}

/** Preview of the voice popup bar (mic circle, name, status, waveform). */
object PopupPreview {
    fun build(context: Context, title: String, status: String): View = ThtUi.horizontal(context).apply {
        setPadding(dp(context, 14), dp(context, 10), dp(context, 24), dp(context, 10))
        background = ThtUi.gradient(context, intArrayOf(android.graphics.Color.rgb(6, 20, 42), android.graphics.Color.rgb(8, 30, 60)), 40f, ThtUi.ACCENT)
        addView(FrameLayout(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.rgb(8, 40, 80))
                setStroke(dp(context, 2), ThtUi.ACCENT)
            }
            addView(ThtUi.icon(context, R.drawable.ic_ui_wave, ThtUi.ACCENT, 28), FrameLayout.LayoutParams(dp(context, 28), dp(context, 28), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(context, 58), dp(context, 58)))
        val t = ThtUi.vertical(context)
        t.addView(ThtUi.text(context, title, 17f, Color.WHITE, bold = true, maxLines = 1))
        t.addView(ThtUi.text(context, status, 14f, Color.rgb(200, 214, 230), maxLines = 1), ThtUi.matchWrap(context, 4))
        addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(context, 16); marginEnd = dp(context, 40)
        })
        addView(ThtUi.icon(context, R.drawable.ic_ui_wave, ThtUi.ACCENT, 34))
    }
}

private const val TTS_DETAIL_MAX = 60
