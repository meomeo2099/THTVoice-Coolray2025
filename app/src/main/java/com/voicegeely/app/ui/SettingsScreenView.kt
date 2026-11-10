package com.voicegeely.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.tht.voice.R
import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.MediaAppSettings
import com.voicegeely.app.settings.MicSensitivity
import com.voicegeely.app.settings.MusicWhileListening
import com.voicegeely.app.settings.OverlayPosition
import com.voicegeely.app.settings.TtsVoice
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.kws.WakePhrase
import com.voicegeely.app.ui.ThtUi.dp

/**
 * "Cài đặt": popup settings (left) and voice settings (right), popup preview and
 * Lưu / Khôi phục mặc định at the bottom. Values are applied when "Lưu" is pressed.
 * Default media / map apps are chosen on the "Ứng dụng" screen.
 */
object SettingsScreenView {
    fun build(
        context: Context,
        voicePreferences: UserVoicePreferences,
        mediaSettings: MediaAppSettings,
        onSaved: () -> Unit,
        onRestored: () -> Unit,
        extraVoiceRows: List<View> = emptyList()
    ): View {
        val spec = ResponsiveContext.spec(context)
        val single = spec.settingsColumns == 1
        val root = ThtUi.vertical(context)

        root.addView(ThtUi.hero(
            context,
            title = "Cài đặt",
            subtitle = "Tùy chỉnh THT Voice theo thói quen của bạn",
            body = "Thiết lập trải nghiệm giọng nói phù hợp với nhu cầu sử dụng, giúp bạn lái xe an toàn và thuận tiện hơn mỗi ngày.",
            heightDp = spec.fitDp(240, 1f, 190, heightFraction = 0.22f)
        ))

        // Working copy, written on "Lưu".
        var overlayEnabled = voicePreferences.overlayEnabled
        var overlayPosition = voicePreferences.overlayPosition
        var autoHide = voicePreferences.overlayAutoHideMs
        var drag = voicePreferences.overlayDragEnabled
        var wakeEnabled = voicePreferences.wakeWordEnabled
        var wakePhrase = voicePreferences.wakePhrase
        var feedback = voicePreferences.voiceFeedbackEnabled
        var musicMode = voicePreferences.musicWhileListening
        var keyAlwaysTht = voicePreferences.voiceKeyAlwaysTht
        var keyVehicleSignal = voicePreferences.voiceKeyVehicleSignal
        var phraseCache = voicePreferences.ttsPhraseCacheEnabled
        var airflowMinutes = voicePreferences.airflowCycleMinutes
        var echoCancel = voicePreferences.echoCancelWhilePlaying
        var ttsVoice = voicePreferences.ttsVoice
        var sensitivity = voicePreferences.micSensitivity
        var silence = voicePreferences.endpointSilenceMs
        var maxListen = voicePreferences.maxListenMs

        fun panel(iconRes: Int, title: String): LinearLayout = ThtUi.vertical(context).apply {
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 16))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
            addView(ThtUi.sectionTitle(context, iconRes, title, 21f).apply { setPadding(dp(context, 4), 0, 0, dp(context, 4)) })
        }
        fun LinearLayout.row(view: View) = addView(view, ThtUi.matchWrap(context, 10))

        val popup = panel(R.drawable.ic_ui_chat, "Thiết lập popup")
        popup.row(ThtUi.settingRow(context, R.drawable.ic_ui_monitor, "Hiển thị popup khi dùng Voice",
            "Hiển thị thanh popup khi nhận diện giọng nói hoặc đang xử lý lệnh.",
            ThtUi.switch(context, overlayEnabled) { overlayEnabled = it }))
        val positions = OverlayPosition.values().toList()
        popup.row(ThtUi.settingRow(context, R.drawable.ic_ui_place, "Vị trí popup", "Chọn vị trí hiển thị trên màn hình.",
            ThtUi.segmented(context, listOf("Trên", "Giữa", "Dưới"), positions.indexOf(overlayPosition).coerceAtLeast(0), 84) {
                overlayPosition = positions[it]
            }))
        val hideValues = listOf(1_000L, 2_000L, 3_000L)
        popup.row(ThtUi.settingRow(context, R.drawable.ic_ui_timer, "Tự ẩn sau", "Thời gian tự động ẩn popup sau khi hiển thị kết quả.",
            ThtUi.segmented(context, listOf("1 giây", "2 giây", "3 giây"), hideValues.indexOf(autoHide).let { if (it >= 0) it else 0 }, 84) {
                autoHide = hideValues[it]
            }))
        popup.row(ThtUi.settingRow(context, R.drawable.ic_ui_move, "Cho phép kéo popup", "Cho phép kéo thả popup đến vị trí khác trên màn hình.",
            ThtUi.switch(context, drag) { drag = it }))

        val voice = panel(R.drawable.ic_ui_mic, "Thiết lập giọng nói")
        val phrases = WakePhrase.values().toList()
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_chat, "Từ đánh thức (Wake word)", "Cụm từ kích hoạt THT Voice.",
            ThtUi.segmented(context, listOf("Tắt") + phrases.map { it.displayLabel },
                if (!wakeEnabled) 0 else phrases.indexOf(wakePhrase).coerceAtLeast(0) + 1) { index ->
                wakeEnabled = index > 0
                if (index > 0) wakePhrase = phrases[index - 1]
            }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_volume, "Phản hồi giọng nói",
            "THT Voice sẽ phản hồi bằng giọng nói sau khi xử lý lệnh; tắt thì dùng tiếng bíp.",
            ThtUi.switch(context, feedback) { feedback = it }))
        val musicModes = listOf(MusicWhileListening.DUCK, MusicWhileListening.PAUSE)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_music, "Nhạc khi nghe lệnh",
            "Giảm nhỏ nhạc, hoặc tạm dừng rồi tự phát lại khi xong (nhận lệnh rõ nhất).",
            ThtUi.segmented(context, listOf("Giảm nhạc", "Tạm dừng"), musicModes.indexOf(musicMode).coerceAtLeast(0), 100) {
                musicMode = musicModes[it]
            }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_car, "Nút voice khi dùng Android Auto / CarPlay",
            "Bật: nút voice luôn mở THT Voice. Tắt: khi đang kết nối Android Auto / CarPlay thì nút voice mở trợ lý điện thoại.",
            ThtUi.switch(context, keyAlwaysTht) { keyAlwaysTht = it }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_car, "Nhận nút voice khi chạy nền",
            "Bật THT Voice – Nút voice trong Trợ năng để nhận phím trên vô lăng khi đang dùng app khác.",
            ThtUi.smallButton(context, null, "Mở cài đặt", ThtUi.SmallStyle.OUTLINE) { openVoiceKeySettings(context) }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_mic, "Đặt THT Voice làm trợ lý mặc định",
            "Nếu bấm nút voice mà hiện thông báo cắm cáp USB: chọn THT Voice ở mục Trợ lý / Nhập liệu giọng nói.",
            ThtUi.smallButton(context, null, "Mở cài đặt", ThtUi.SmallStyle.OUTLINE) { openAssistantSettings(context) }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_chat, "Tên gọi riêng",
            "Đặt câu nói của bạn cho một lệnh, ví dụ \"gió bão\" = quạt mức 6.",
            ThtUi.smallButton(context, null, "Quản lý", ThtUi.SmallStyle.OUTLINE) { UserAliasDialog.show(context, voicePreferences) }))
        // Coolray: no vehicle voice-key signal mapping, so no switch for it.
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_car, "Kiểm tra nút voice",
            "Xem trạng thái nhận phím, tín hiệu đã nhận và các app cùng dùng nút voice.",
            ThtUi.smallButton(context, null, "Kiểm tra", ThtUi.SmallStyle.OUTLINE) { showVoiceKeyCheck(context) }))
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_mic, "Lọc tiếng nhạc khi nghe lệnh",
            "Chỉ bật khi đang phát nhạc hoặc đang đọc phản hồi, không dùng cho từ đánh thức. Tắt nếu micrô nghe kém.",
            ThtUi.switch(context, echoCancel) { echoCancel = it }))
        // THT Split API v2: token only needed when the two APKs are signed with different keys.
        val splitSubtitle = { if (voicePreferences.splitToken != null) "Đã lưu mã kết nối. Nói \"chia đôi màn hình\", \"thoát chia đôi\"."
            else "Trong THT Split: Kết nối THT Voice → bật nhận lệnh → sao chép token, rồi bấm Dán mã." }
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_mic, "Chia màn hình (THT Split)", splitSubtitle(),
            ThtUi.smallButton(context, null, "Dán mã", ThtUi.SmallStyle.OUTLINE) { pasteSplitToken(context, voicePreferences) }))
        val airflowValues = listOf(1, 2, 3, 5)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_wave, "Đảo hướng gió định kỳ",
            "Nói \"bật đảo hướng gió\" để luân phiên gió vào mặt và xuống chân theo thời gian này.",
            ThtUi.segmented(context, airflowValues.map { "$it phút" },
                airflowValues.indexOf(airflowMinutes).let { if (it >= 0) it else 0 }, 76) { airflowMinutes = airflowValues[it] }))
        val cacheMb = runCatching { com.voicegeely.app.tts.SherpaOfflineTtsEngine.phraseCacheBytes(context) }.getOrDefault(0L) / (1024.0 * 1024.0)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_headphones, "Lưu sẵn câu trả lời",
            "Câu đã đọc được lưu để lần sau trả lời nhanh hơn (tối đa 24 MB, đang dùng ${"%.1f".format(cacheMb)} MB). Tắt thì xoá hết và luôn tạo giọng mới.",
            ThtUi.switch(context, phraseCache) { phraseCache = it }))
        val voices = listOf(TtsVoice.NGOC_HUYEN, TtsVoice.DIEM_TRINH)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_headphones, "Giọng trợ lý", "Giọng đọc offline dùng để phản hồi.",
            ThtUi.segmented(context, listOf("Ngọc Huyền", "Diễm Trinh"), voices.indexOf(ttsVoice).coerceAtLeast(0), 110) { ttsVoice = voices[it] }))
        extraVoiceRows.forEach { voice.row(it) }
        val sensitivities = MicSensitivity.values().toList()
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_wave, "Độ nhạy mic", "Điều chỉnh độ nhạy nhận diện giọng nói.",
            ThtUi.segmented(context, listOf("Thấp", "Chuẩn", "Cao"), sensitivities.indexOf(sensitivity).coerceAtLeast(0), 90) {
                sensitivity = sensitivities[it]
            }))
        val silenceValues = listOf(500L, 700L, 900L, 1_200L)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_timer, "Ngắt câu sau im lặng", "Tự động kết thúc ghi âm khi bạn ngừng nói.",
            ThtUi.segmented(context, listOf("0,5 giây", "0,7 giây", "0,9 giây", "1,2 giây"),
                silenceValues.indexOf(silence).let { if (it >= 0) it else 0 }) { silence = silenceValues[it] }))
        val maxValues = listOf(6_000L, 8_000L, 10_000L, 12_000L)
        voice.row(ThtUi.settingRow(context, R.drawable.ic_ui_timer, "Thời gian nghe tối đa", "Thời gian ghi âm tối đa cho mỗi lần ra lệnh.",
            ThtUi.segmented(context, listOf("6 giây", "8 giây", "10 giây", "12 giây"),
                maxValues.indexOf(maxListen).let { if (it >= 0) it else 1 }) { maxListen = maxValues[it] }))

        val columns = LinearLayout(context).apply { orientation = if (single) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        if (single) {
            columns.addView(popup, ThtUi.matchWrap(context))
            columns.addView(voice, ThtUi.matchWrap(context, 14))
        } else {
            columns.addView(popup, ThtUi.weight(0.44f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 14) })
            columns.addView(voice, ThtUi.weight(0.56f, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(columns, ThtUi.matchWrap(context, 16))

        val bottom = LinearLayout(context).apply { orientation = if (single) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val preview = ThtUi.vertical(context).apply {
            setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 14))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
            addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_eye, "Xem trước popup giọng nói", 17f))
            addView(PopupPreview.build(context, "THT Voice", "Tôi đang lắng nghe..."),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(context, 10); gravity = Gravity.CENTER_HORIZONTAL
                })
        }
        val buttons = ThtUi.vertical(context)
        buttons.addView(ThtUi.actionButton(context, R.drawable.ic_ui_save, "Lưu", "Áp dụng các thiết lập", primary = true) {
            voicePreferences.overlayEnabled = overlayEnabled
            if (voicePreferences.overlayPosition != overlayPosition) voicePreferences.overlayPosition = overlayPosition
            voicePreferences.overlayAutoHideMs = autoHide
            voicePreferences.overlayDragEnabled = drag
            voicePreferences.wakeWordEnabled = wakeEnabled
            voicePreferences.wakePhrase = wakePhrase
            voicePreferences.voiceFeedbackEnabled = feedback
            voicePreferences.musicWhileListening = musicMode
            voicePreferences.voiceKeyAlwaysTht = keyAlwaysTht
            voicePreferences.voiceKeyVehicleSignal = keyVehicleSignal
            if (voicePreferences.ttsPhraseCacheEnabled && !phraseCache) {
                runCatching { com.voicegeely.app.tts.SherpaOfflineTtsEngine.clearPhraseCache(context) }
            }
            voicePreferences.ttsPhraseCacheEnabled = phraseCache
            voicePreferences.airflowCycleMinutes = airflowMinutes
            voicePreferences.echoCancelWhilePlaying = echoCancel
            voicePreferences.ttsVoice = ttsVoice
            voicePreferences.micSensitivity = sensitivity
            voicePreferences.endpointSilenceMs = silence
            voicePreferences.maxListenMs = maxListen
            onSaved()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 68)))
        buttons.addView(ThtUi.actionButton(context, R.drawable.ic_ui_refresh, "Khôi phục mặc định", "Đặt lại về thiết lập ban đầu", primary = false) {
            voicePreferences.resetToDefaults()
            mediaSettings.defaultMediaApp = DefaultMediaApp.AUTO
            onRestored()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 68)).apply { topMargin = dp(context, 10) })
        if (single) {
            bottom.addView(preview, ThtUi.matchWrap(context))
            bottom.addView(buttons, ThtUi.matchWrap(context, 12))
        } else {
            bottom.addView(preview, ThtUi.weight(0.68f, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = dp(context, 14) })
            bottom.addView(buttons, ThtUi.weight(0.32f, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(bottom, ThtUi.matchWrap(context, 14))
        return root
    }

    /** Reads the THT Split token from the clipboard; the token is never shown or logged. */
    private fun pasteSplitToken(context: Context, voicePreferences: UserVoicePreferences) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        val message = if (text != null && SPLIT_TOKEN.matches(text)) {
            voicePreferences.splitToken = text
            "Đã lưu mã kết nối THT Split."
        } else {
            "Chưa có mã hợp lệ trong bộ nhớ tạm. Sao chép token trong THT Split trước."
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private val SPLIT_TOKEN = Regex("[A-Za-z0-9_-]{64}")

    private fun showVoiceKeyCheck(context: Context) {
        val text = "Nhận phím khi chạy nền: ${com.voicegeely.app.VoiceKeyAccessibilityService.status(context)}\n\n" +
            com.voicegeely.app.VoiceKeyDiagnostics.format(com.voicegeely.app.VoiceKeyProbe.read(context)) +
            "\n\nTín hiệu nút voice gần đây:\n" + com.voicegeely.app.VoiceKeySignalLog.format()
        ThtUi.showDialog(context, R.drawable.ic_ui_car, "Kiểm tra nút voice", text,
            secondaryLabel = "Sao chép", onSecondary = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("THT Voice - nút voice", text))
                android.widget.Toast.makeText(context, "Đã sao chép.", android.widget.Toast.LENGTH_SHORT).show()
            })
    }

    private fun openVoiceKeySettings(context: Context) {
        val opened = runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) android.widget.Toast.makeText(context,
            "Không mở được Trợ năng. Vào Cài đặt Android → Trợ năng → THT Voice – Nút voice.",
            android.widget.Toast.LENGTH_LONG).show()
    }

    private fun openAssistantSettings(context: Context) {
        val actions = listOf(
            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS,
            android.provider.Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            val opened = runCatching {
                context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
        android.widget.Toast.makeText(context, "Không mở được cài đặt hệ thống trên màn hình này.", android.widget.Toast.LENGTH_LONG).show()
    }
}
