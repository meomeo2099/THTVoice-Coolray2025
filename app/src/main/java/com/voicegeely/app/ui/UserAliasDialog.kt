package com.voicegeely.app.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.tht.voice.R
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.voice.UserAliasBook
import com.voicegeely.app.ui.ThtUi.dp

/** [S05] Settings → "Tên gọi riêng": list, delete and add taught phrases. */
object UserAliasDialog {
    fun show(context: Context, prefs: UserVoicePreferences) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = ThtUi.vertical(context).apply {
            setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 18))
            background = ThtUi.gradient(context, intArrayOf(Color.rgb(10, 26, 46), Color.rgb(6, 14, 26)), 22f, Color.rgb(40, 110, 190),
                GradientDrawable.Orientation.TOP_BOTTOM)
        }
        root.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_chat, "Tên gọi riêng", 21f))
        root.addView(ThtUi.text(context,
            "Đặt câu nói của bạn cho một lệnh có sẵn (tối đa ${UserAliasBook.MAX_ALIASES}). Kịch bản: một câu chạy tối đa ${com.voicegeely.app.voice.MultiCommandPlan.MAX_STEPS} lệnh, nối bằng \"rồi\". Cũng có thể nói: \"Khi tôi nói đi làm thì bật điều hòa 24 độ rồi mở nhạc\" hoặc \"Quên tên gọi đi làm\". Lệnh có sẵn luôn được ưu tiên.",
            13f, ThtUi.TEXT_3), ThtUi.matchWrap(context, 8))

        val list = ThtUi.vertical(context)
        val maxListPx = (context.resources.displayMetrics.heightPixels * 0.35f).toInt()
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(maxListPx, android.view.View.MeasureSpec.AT_MOST))
            }
        }
        scroll.addView(list)
        root.addView(scroll, ThtUi.matchWrap(context, 12))

        fun refresh() {
            list.removeAllViews()
            val book = prefs.aliasBook()
            if (book.all().isEmpty()) {
                list.addView(ThtUi.text(context, "Chưa có tên gọi nào.", 14f, ThtUi.TEXT_3), ThtUi.matchWrap(context, 4))
            }
            book.all().forEach { alias ->
                val row = ThtUi.horizontal(context)
                row.addView(ThtUi.text(context, "\"${alias.phrase}\"  →  ${alias.target}", 15f, Color.rgb(210, 222, 236), maxLines = 2),
                    ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(ThtUi.smallButton(context, null, "Xoá", ThtUi.SmallStyle.OUTLINE) {
                    val b = prefs.aliasBook()
                    b.forget(alias.phrase)
                    prefs.userAliases = b.encode()
                    refresh()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 42)))
                list.addView(row, ThtUi.matchWrap(context, 6))
            }
        }
        refresh()

        fun input(hint: String) = EditText(context).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(ThtUi.TEXT_3)
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            background = ThtUi.panel(context, Color.rgb(6, 18, 34), ThtUi.ACCENT_2, 12f)
            minHeight = dp(context, 48)
        }
        val phraseInput = input("Câu bạn nói, ví dụ: gió bão")
        val targetInput = input("Lệnh, ví dụ: quạt mức 6 rồi mở nhạc")
        root.addView(phraseInput, ThtUi.matchWrap(context, 14))
        root.addView(targetInput, ThtUi.matchWrap(context, 8))

        val buttons = ThtUi.horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
        buttons.addView(ThtUi.smallButton(context, null, "Sao chép / gửi", ThtUi.SmallStyle.OUTLINE) {
            shareAliases(context, prefs)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 50)).apply { marginEnd = dp(context, 12) })
        buttons.addView(ThtUi.smallButton(context, null, "Thêm", ThtUi.SmallStyle.OUTLINE) {
            val book = prefs.aliasBook()
            val message = when (val r = book.teach(phraseInput.text.toString(), targetInput.text.toString())) {
                is UserAliasBook.TeachResult.Saved -> {
                    prefs.userAliases = book.encode()
                    phraseInput.setText(""); targetInput.setText("")
                    refresh()
                    if (r.replaced) "Đã cập nhật \"${r.alias.phrase}\"." else "Đã thêm \"${r.alias.phrase}\"."
                }
                is UserAliasBook.TeachResult.Refused -> r.reason
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 50)).apply { marginEnd = dp(context, 12) })
        buttons.addView(ThtUi.smallButton(context, null, "Đóng", ThtUi.SmallStyle.PRIMARY) { dialog.dismiss() },
            LinearLayout.LayoutParams(dp(context, 140), dp(context, 50)))
        root.addView(buttons, ThtUi.matchWrap(context, 14))

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val metrics = context.resources.displayMetrics
            setLayout((metrics.widthPixels * 0.62f).toInt().coerceAtLeast(dp(context, 360)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    /** Copies the taught phrases to the clipboard and, when a sharing app exists, opens the share sheet. */
    private fun shareAliases(context: Context, prefs: UserVoicePreferences) {
        val text = prefs.aliasBook().exportText()
        if (text.isEmpty()) {
            Toast.makeText(context, "Chưa có tên gọi nào để gửi.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(UserAliasBook.EXPORT_HEADER, text))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, UserAliasBook.EXPORT_HEADER)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val shared = runCatching {
            if (send.resolveActivity(context.packageManager) == null) return@runCatching false
            context.startActivity(Intent.createChooser(send, "Gửi tên gọi riêng").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!shared) Toast.makeText(context, "Đã sao chép danh sách tên gọi. Dán vào tin nhắn để gửi.", Toast.LENGTH_LONG).show()
    }
}
