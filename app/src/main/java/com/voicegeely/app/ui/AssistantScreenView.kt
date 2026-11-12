package com.voicegeely.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tht.voice.R
import com.voicegeely.app.car.VehicleTelemetryRegistry
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.ThtUi.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssistantScreenBinding(
    val root: View,
    private val steps: List<Pair<FrameLayout, TextView>>,
    private val transcriptText: TextView,
    private val transcriptHint: TextView,
    private val resultText: TextView,
    private val resultHint: TextView,
    private val historyList: LinearLayout,
    private val systemRows: Map<String, Pair<TextView, View>>
) {
    fun render(state: AssistantRuntimeSnapshot) {
        val context = root.context
        val active = when (state.phase) {
            AssistantRuntimePhase.LISTENING -> 1
            AssistantRuntimePhase.PROCESSING -> 2
            AssistantRuntimePhase.RESULT -> 3
            else -> 0
        }
        steps.forEachIndexed { index, (circle, label) ->
            val on = index == active
            val done = index < active
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (on) Color.rgb(8, 50, 100) else Color.rgb(12, 26, 44))
                setStroke(dp(context, if (on) 3 else 1), if (on || done) ThtUi.ACCENT else ThtUi.BORDER)
            }
            (circle.getChildAt(0) as? android.widget.ImageView)?.imageTintList =
                android.content.res.ColorStateList.valueOf(if (on || done) ThtUi.ACCENT else ThtUi.TEXT_2)
            label.setTextColor(if (on) Color.WHITE else ThtUi.TEXT_2)
        }

        val heard = state.partialText.ifBlank { state.finalText }
        transcriptText.text = if (heard.isBlank()) "Nói một lệnh để bắt đầu" else "“$heard”"
        transcriptHint.text = when (state.phase) {
            AssistantRuntimePhase.LISTENING -> "Đang nhận giọng nói..."
            AssistantRuntimePhase.PROCESSING -> "Đang xử lý lệnh của bạn..."
            AssistantRuntimePhase.KWS_LISTENING -> "Đang chờ từ đánh thức"
            else -> "Lệnh gần nhất"
        }
        resultText.text = state.statusMessage.ifBlank { "—" }
        resultHint.text = when (state.phase) {
            AssistantRuntimePhase.RESULT -> "Kết quả từ Voice core (đã đọc lại trạng thái khi có)"
            AssistantRuntimePhase.ERROR -> "Cần kiểm tra"
            else -> "Trạng thái hiện tại"
        }

        historyList.removeAllViews()
        if (state.history.isEmpty()) {
            historyList.addView(ThtUi.text(context, "Chưa có lịch sử thực hiện lệnh.", 14f, ThtUi.TEXT_2), ThtUi.matchWrap(context, 8))
        } else {
            val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            state.history.take(5).forEach { item ->
                val (label, chipState) = when (item.outcome) {
                    "CONFIRMED" -> "Đã xác minh" to ThtUi.ChipState.OK
                    "ACCEPTED_UNVERIFIED", "INTERRUPTED" -> "Chưa xác nhận" to ThtUi.ChipState.WARN
                    "REJECTED", "FAILED" -> "Không thành công" to ThtUi.ChipState.ERR
                    "PENDING" -> "Đang xử lý" to ThtUi.ChipState.WARN
                    else -> outcome(item.result)
                }
                historyList.addView(ThtUi.horizontal(context).apply {
                    setPadding(0, dp(context, 7), 0, dp(context, 7))
                    addView(ThtUi.text(context, "“${item.command}”", 15f, Color.WHITE, maxLines = 1), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(ThtUi.text(context, formatter.format(Date(item.timestampMs)), 13f, ThtUi.TEXT_2, maxLines = 1),
                        ThtUi.wrap().apply { marginStart = dp(context, 10); marginEnd = dp(context, 10) })
                    addView(ThtUi.chip(context, label, chipState))
                })
            }
        }

        fun row(key: String, ready: Boolean, readyText: String = "Đang hoạt động", notReady: String = "Chưa sẵn sàng", off: Boolean = false) {
            val (text, dotView) = systemRows[key] ?: return
            val color = when {
                off -> ThtUi.TEXT_3
                ready -> ThtUi.OK
                else -> ThtUi.WARN
            }
            text.text = if (off) "Đã tắt" else if (ready) readyText else notReady
            text.setTextColor(color)
            dotView.background = ThtUi.dot(context, color, 10)
        }
        row("asr", state.asrReady)
        row("kws", state.kwsReady)
        row("mic", state.phase != AssistantRuntimePhase.ERROR, readyText = micLabel(state.phase), notReady = "Kiểm tra")
        val vehicle = VehicleTelemetryRegistry.snapshot()
        row("vhal", vehicle.state == com.voicegeely.app.car.VehicleTelemetryState.CONNECTED,
            readyText = vehicle.uiLabel, notReady = vehicle.uiLabel)
        row("tts", state.ttsReady)
    }

    private fun outcome(result: String): Pair<String, ThtUi.ChipState> = when {
        result.startsWith("Đã") -> "Thành công" to ThtUi.ChipState.OK
        result.startsWith("Đang") -> "Đang xử lý" to ThtUi.ChipState.WARN
        else -> "Không thành công" to ThtUi.ChipState.ERR
    }

    private fun micLabel(phase: AssistantRuntimePhase): String = when (phase) {
        AssistantRuntimePhase.KWS_LISTENING -> "Chờ từ đánh thức"
        AssistantRuntimePhase.LISTENING -> "Đang nghe"
        AssistantRuntimePhase.PROCESSING -> "Đã nhả mic"
        AssistantRuntimePhase.STARTING -> "Đang khởi tạo"
        else -> "Sẵn sàng"
    }
}

object AssistantScreenView {
    fun build(
        context: Context,
        initial: AssistantRuntimeSnapshot,
        voicePreferences: UserVoicePreferences,
        onListen: () -> Unit,
        onSettingsChanged: () -> Unit
    ): AssistantScreenBinding {
        val spec = ResponsiveContext.spec(context)
        val narrow = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
        val root = ThtUi.vertical(context)

        // Hero: toggles for the real voice settings + big glowing mic.
        val toggles = ThtUi.horizontal(context).apply {
            setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10))
            background = ThtUi.panel(context, Color.argb(200, 6, 20, 42), ThtUi.ACCENT_2, 18f)
        }
        fun toggle(title: String, subtitle: String, checked: Boolean, apply: (Boolean) -> Unit) {
            toggles.addView(ThtUi.horizontal(context).apply {
                addView(ThtUi.switch(context, checked) { apply(it); onSettingsChanged() })
                val t = ThtUi.vertical(context)
                t.addView(ThtUi.text(context, title, 15f, Color.WHITE, bold = true, maxLines = 1))
                t.addView(ThtUi.text(context, subtitle, 12f, ThtUi.TEXT_2, maxLines = 1), ThtUi.matchWrap(context, 2))
                addView(t, ThtUi.wrap().apply { marginStart = dp(context, 14) })
            }, ThtUi.wrap().apply { if (toggles.childCount > 0) marginStart = dp(context, 28) })
        }
        toggle(voicePreferences.wakePhrase.displayLabel, "Kích hoạt bằng “${voicePreferences.wakePhrase.displayLabel}”", voicePreferences.wakeWordEnabled) {
            voicePreferences.wakeWordEnabled = it
        }
        toggle("Phản hồi giọng nói", "Trợ lý đọc kết quả", voicePreferences.voiceFeedbackEnabled) { voicePreferences.voiceFeedbackEnabled = it }
        toggle("Popup Voice", "Hiện thanh trạng thái", voicePreferences.overlayEnabled) { voicePreferences.overlayEnabled = it }

        val micSize = spec.fitDp(190, 0.2f, 110, heightFraction = 0.2f)
        val mic = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.rgb(10, 60, 130), Color.rgb(4, 20, 50))
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(context, micSize / 2).toFloat()
                setStroke(dp(context, 4), ThtUi.ACCENT)
            }
            addView(ThtUi.icon(context, R.drawable.ic_ui_mic, Color.WHITE, micSize / 2),
                FrameLayout.LayoutParams(dp(context, micSize / 2), dp(context, micSize / 2), Gravity.CENTER))
            isClickable = true
            contentDescription = "Bấm để nói"
            setOnClickListener { onListen() }
            layoutParams = FrameLayout.LayoutParams(dp(context, micSize), dp(context, micSize))
            elevation = dp(context, 8).toFloat()
        }
        root.addView(ThtUi.hero(
            context,
            title = "Trợ lý THT Voice",
            subtitle = "Tôi luôn sẵn sàng lắng nghe",
            body = "Chỉ cần nói tự nhiên, tôi sẽ giúp bạn điều khiển xe, mở ứng dụng, giải trí và hơn thế nữa.",
            heightDp = spec.fitDp(340, 1f, 260, heightFraction = 0.32f),
            extra = toggles,
            side = if (narrow) null else FrameLayout(context).apply {
                addView(mic)
                setPadding(dp(context, 360), 0, 0, 0)
            }
        ))
        if (narrow) root.addView(mic.apply { layoutParams = LinearLayout.LayoutParams(dp(context, micSize), dp(context, micSize)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(context, 12)
        } })

        val body = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        root.addView(body, ThtUi.matchWrap(context, 16))

        // Left: status stepper + heard/result, then quick commands.
        val left = ThtUi.vertical(context)
        val statusCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 16))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        statusCard.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_list, "Trạng thái trợ lý", 19f))
        val stepRow = ThtUi.horizontal(context, Gravity.TOP)
        val steps = mutableListOf<Pair<FrameLayout, TextView>>()
        listOf(
            Triple(R.drawable.ic_ui_mic, "Sẵn sàng", "Chờ lệnh của bạn"),
            Triple(R.drawable.ic_ui_ear, "Đang nghe", "Đang nhận giọng nói"),
            Triple(R.drawable.ic_ui_gear, "Đang xử lý", "Phân tích và hiểu ý định"),
            Triple(R.drawable.ic_ui_check, "Hoàn tất", "Đã thực hiện xong")
        ).forEach { (iconRes, title, sub) ->
            val circle = FrameLayout(context).apply {
                addView(ThtUi.icon(context, iconRes, ThtUi.TEXT_2, 28), FrameLayout.LayoutParams(dp(context, 28), dp(context, 28), Gravity.CENTER))
            }
            val label = ThtUi.text(context, title, 15f, ThtUi.TEXT_2, bold = true, maxLines = 1, gravity = Gravity.CENTER)
            steps += circle to label
            stepRow.addView(ThtUi.vertical(context, Gravity.CENTER_HORIZONTAL).apply {
                addView(circle, LinearLayout.LayoutParams(dp(context, 62), dp(context, 62)))
                addView(label, ThtUi.matchWrap(context, 8))
                addView(ThtUi.text(context, sub, 12f, ThtUi.TEXT_3, maxLines = 1, gravity = Gravity.CENTER), ThtUi.matchWrap(context, 3))
            }, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        statusCard.addView(stepRow, ThtUi.matchWrap(context, 12))

        val flow = ThtUi.horizontal(context)
        val transcriptText = ThtUi.text(context, "", 20f, Color.WHITE, maxLines = 2)
        val transcriptHint = ThtUi.text(context, "", 13f, ThtUi.TEXT_2, maxLines = 1)
        val resultText = ThtUi.text(context, "", 18f, Color.rgb(90, 230, 160), bold = true, maxLines = 2)
        val resultHint = ThtUi.text(context, "", 13f, ThtUi.TEXT_2, maxLines = 1)
        fun box(iconRes: Int, title: String, main: TextView, hint: TextView, tone: Int): LinearLayout = ThtUi.vertical(context).apply {
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            background = ThtUi.panel(context, Color.rgb(8, 22, 40), tone, 14f)
            addView(ThtUi.sectionTitle(context, iconRes, title, 16f))
            addView(ThtUi.vertical(context).apply {
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                background = ThtUi.panel(context, Color.rgb(6, 16, 30), Color.rgb(30, 60, 96), 12f)
                addView(main)
                addView(hint, ThtUi.matchWrap(context, 6))
            }, ThtUi.matchWrap(context, 10))
        }
        flow.addView(box(R.drawable.ic_ui_wave, "Lệnh vừa nhận", transcriptText, transcriptHint, ThtUi.ACCENT_2), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
        flow.addView(ThtUi.icon(context, R.drawable.ic_ui_arrow, ThtUi.ACCENT, 28), ThtUi.wrap().apply { marginStart = dp(context, 10); marginEnd = dp(context, 10) })
        flow.addView(box(R.drawable.ic_ui_check_circle, "Kết quả thực hiện", resultText, resultHint, Color.rgb(30, 150, 110)), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
        statusCard.addView(flow, ThtUi.matchWrap(context, 16))
        left.addView(statusCard, ThtUi.matchWrap(context))

        val quickCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 16))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        quickCard.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_bolt, "Lệnh nhanh cho trợ lý", 19f))
        val quickRow = ThtUi.horizontal(context, Gravity.TOP)
        listOf(
            Triple(R.drawable.ic_ui_snow, "Điều hòa", listOf("Bật điều hòa", "Đặt 22 độ", "Tăng gió")),
            Triple(R.drawable.ic_ui_window, "Kính", listOf("Hạ kính bên lái", "Đóng tất cả kính", "Hé kính")),
            Triple(R.drawable.ic_ui_music, "Media", listOf("Mở YouTube", "Âm lượng 15", "Bài tiếp theo")),
            Triple(R.drawable.ic_ui_place, "Bản đồ", listOf("Mở bản đồ", "Chỉ đường về nhà", "Mở Vietmap")),
            Triple(R.drawable.ic_ui_car, "Xe", listOf("Pin còn bao nhiêu", "Nhiệt độ trong xe", "Chế độ Eco"))
        ).forEachIndexed { index, (iconRes, title, examples) ->
            quickRow.addView(ThtUi.vertical(context).apply {
                setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12))
                background = ThtUi.panel(context, Color.rgb(10, 28, 52), Color.rgb(34, 80, 140), 14f)
                addView(ThtUi.horizontal(context).apply {
                    addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 24))
                    addView(ThtUi.text(context, title, 15f, Color.WHITE, bold = true, maxLines = 1),
                        ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 10) })
                })
                examples.forEach { addView(ThtUi.text(context, "“$it”", 12.5f, ThtUi.TEXT_2, maxLines = 1), ThtUi.matchWrap(context, 6)) }
                isClickable = true
                setOnClickListener { onListen() }
            }, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (index > 0) marginStart = dp(context, 10) })
        }
        quickCard.addView(quickRow, ThtUi.matchWrap(context, 12))
        left.addView(quickCard, ThtUi.matchWrap(context, 14))

        // Right: history + system status.
        val right = ThtUi.vertical(context)
        val historyCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 12))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        historyCard.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_history, "Lịch sử lệnh gần đây", 19f))
        val historyList = ThtUi.vertical(context)
        historyCard.addView(historyList, ThtUi.matchWrap(context, 8))
        right.addView(historyCard, ThtUi.matchWrap(context))

        val systemCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 12))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        systemCard.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_monitor, "Trạng thái hệ thống", 19f))
        val systemRows = linkedMapOf<String, Pair<TextView, View>>()
        listOf(
            Triple("asr", R.drawable.ic_ui_wave, "Nhận dạng giọng nói"),
            Triple("kws", R.drawable.ic_ui_mic, "Từ đánh thức"),
            Triple("mic", R.drawable.ic_ui_ear, "Micrô"),
            Triple("vhal", R.drawable.ic_ui_car, "Kết nối xe"),
            Triple("tts", R.drawable.ic_ui_headphones, "Giọng trợ lý")
        ).forEach { (key, iconRes, title) ->
            val value = ThtUi.text(context, "", 14f, ThtUi.OK, maxLines = 1)
            val dotView = View(context)
            systemRows[key] = value to dotView
            systemCard.addView(ThtUi.horizontal(context).apply {
                setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                if (key == "vhal") {
                    minimumHeight = dp(context, 48)
                    contentDescription = "Kết nối xe. Chạm để xem chẩn đoán."
                    setOnClickListener { VehicleConnectionDiagnostics.show(context) }
                }
                background = ThtUi.panel(context, Color.rgb(8, 22, 40), Color.rgb(20, 40, 62), 10f)
                addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 22))
                addView(ThtUi.text(context, title, 15f, Color.WHITE, maxLines = 1),
                    ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
                addView(dotView, LinearLayout.LayoutParams(dp(context, 10), dp(context, 10)).apply { marginEnd = dp(context, 8) })
                addView(value)
            }, ThtUi.matchWrap(context, 7))
        }
        right.addView(systemCard, ThtUi.matchWrap(context, 14))

        if (narrow) {
            body.addView(left, ThtUi.matchWrap(context))
            body.addView(right, ThtUi.matchWrap(context, 14))
        } else {
            body.addView(left, ThtUi.weight(0.66f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 14) })
            body.addView(right, ThtUi.weight(0.34f, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        return AssistantScreenBinding(root, steps, transcriptText, transcriptHint, resultText, resultHint, historyList, systemRows)
            .also { it.render(initial) }
    }
}
