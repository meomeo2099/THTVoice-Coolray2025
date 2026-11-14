package com.voicegeely.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.voicegeely.app.history.*

/** Read-only retained history. Owner must call destroy when its Activity is destroyed. */
class HistoryLogSection(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val list = ThtUi.vertical(context)
    private var snapshot = HistorySnapshot(emptyList(), emptyList())
    private var filter = HistoryFilter.PROBLEMS
    private var query = ""
    private var destroyed = false
    private var server: HistoryShareServer? = null
    private var dialog: Dialog? = null
    private val status = ThtUi.text(context, "Đang tải nhật ký…", 14f, ThtUi.TEXT_2)
    private val expiry = Runnable { dialog?.dismiss() }

    fun view(): View = ThtUi.vertical(context).apply {
        val choices = HistoryFilter.values()
        addView(android.widget.HorizontalScrollView(context).apply {
            addView(ThtUi.segmented(context, choices.map { it.label }, choices.indexOf(filter), 150) {
                filter = choices[it]; render()
            })
        }, ThtUi.matchWrap(context))
        addView(EditText(context).apply {
            hint = "Tìm câu lệnh, kết quả hoặc chẩn đoán"
            setTextColor(ThtUi.TEXT); setHintTextColor(ThtUi.TEXT_2)
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(240))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); render() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }, ThtUi.matchWrap(context, 8))
        addView(status, ThtUi.matchWrap(context, 8))
        addView(ThtUi.smallButton(context, null, "Làm mới", ThtUi.SmallStyle.OUTLINE) { refresh() }, ThtUi.matchWrap(context, 8))
        addView(ThtUi.smallButton(context, null, "Chia sẻ bộ lọc này qua QR (LAN, 5 phút)", ThtUi.SmallStyle.PRIMARY) { share() }, ThtUi.matchWrap(context, 8))
        addView(list, ThtUi.matchWrap(context, 10))
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { stopSharing() }
        })
        refresh()
    }

    private fun refresh() {
        val journal = VoiceHistory.journal
        if (journal == null) { status.text = "Nhật ký chưa sẵn sàng"; return }
        journal.browse { loaded -> main.post { if (!destroyed) { snapshot = loaded; render() } } }
    }

    private fun render() {
        if (destroyed) return
        val rows = HistoryReport.rows(snapshot, filter, query)
        status.text = "${rows.size} mục (tối đa ${HistoryReport.MAX_ROWS}) · ${VoiceHistory.journal?.storageStatus.orEmpty()}"
        list.removeAllViews()
        if (rows.isEmpty()) list.addView(ThtUi.text(context, "Không có mục phù hợp.", 15f, ThtUi.TEXT_2))
        val date = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.ROOT)
        rows.forEach { row ->
            list.addView(ThtUi.text(context, "${date.format(java.util.Date(row.timestampMs))} · ${row.outcome}\n${row.text}", 14f, ThtUi.TEXT).apply {
                setPadding(12, 10, 12, 10)
                setTextIsSelectable(true)
            }, ThtUi.matchWrap(context, 6))
        }
    }

    private fun share() {
        if (destroyed) return
        stopSharing()
        val share = HistoryShareServer(HistoryReport.export(HistoryReport.rows(snapshot, filter, query)))
        server = share
        val url = runCatching { share.start() }.getOrNull()
        if (url == null) { stopSharing(); status.text = "Cần kết nối Wi-Fi/LAN riêng để chia sẻ nhật ký."; return }
        try {
            val d = Dialog(context)
            dialog = d
            val content = ThtUi.vertical(context).apply {
                setPadding(24, 24, 24, 24)
                addView(ThtUi.text(context, "Quét bằng điện thoại cùng Wi-Fi. Liên kết tải bản nhật ký đã lọc, đã ẩn thông tin riêng tư. Chỉ mở trong 5 phút; đóng để dừng ngay.", 16f, ThtUi.TEXT_2))
                val matrix = SupportQr.matrix(url, 360)
                addView(ImageView(context).apply {
                    setImageBitmap(Bitmap.createBitmap(SupportQr.pixels(matrix), matrix.width, matrix.height, Bitmap.Config.ARGB_8888))
                    setBackgroundColor(Color.WHITE)
                    contentDescription = "Mã QR tải nhật ký"
                }, LinearLayout.LayoutParams(360, 360))
                addView(ThtUi.smallButton(context, null, "Đóng chia sẻ", ThtUi.SmallStyle.OUTLINE) { d.dismiss() })
            }
            d.setContentView(ScrollView(context).apply { addView(content) })
            d.setOnDismissListener { share.stop(); if (server === share) server = null; main.removeCallbacks(expiry) }
            d.show()
            main.postDelayed(expiry, 300_000)
        } catch (_: Exception) { stopSharing(); status.text = "Không thể hiển thị mã QR." }
    }

    private fun stopSharing() {
        main.removeCallbacks(expiry)
        server?.stop(); server = null
        dialog?.dismiss(); dialog = null
    }

    fun destroy() { destroyed = true; stopSharing(); main.removeCallbacksAndMessages(null) }
}
