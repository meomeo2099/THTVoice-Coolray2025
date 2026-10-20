package com.voicegeely.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.tht.voice.R
import com.voicegeely.app.ui.ThtUi.dp

/** "Ủng hộ" screen: artwork + thanks on the left, VietQR and copyable account on the right. */
object SupportScreenView {
    const val RECIPIENT = "TRAN HUU TAI"
    const val BANK = "MBBank"
    const val ACCOUNT_NUMBER = "67777777778"

    fun build(context: Context, onCopy: (label: String, value: String) -> Unit): View {
        val spec = ResponsiveContext.spec(context)
        val stacked = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
        val root = LinearLayout(context).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }

        // Left: artwork panel.
        val left = FrameLayout(context).apply {
            background = ThtUi.panel(context, Color.rgb(4, 12, 26), ThtUi.BORDER, 20f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        left.addView(ImageView(context).apply {
            setImageResource(R.drawable.tht_support_art)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        left.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 12, 26), Color.argb(200, 4, 12, 26), Color.argb(30, 4, 12, 26), Color.argb(180, 4, 12, 26))
            )
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val leftText = ThtUi.vertical(context).apply { setPadding(dp(context, 44), dp(context, 36), dp(context, 36), dp(context, 28)) }
        leftText.addView(ThtUi.text(context, "Ủng hộ", 50f, ThtUi.ACCENT, bold = true, maxLines = 1))
        leftText.addView(ThtUi.text(context, "phát triển THT Voice", 44f, ThtUi.ACCENT, bold = true, maxLines = 1))
        leftText.addView(ThtUi.text(
            context,
            "Sự ủng hộ của bạn là nguồn động lực to lớn giúp mình tiếp tục phát triển THT Voice, mang đến trải nghiệm lái xe thông minh, an toàn và gần gũi hơn với người Việt.",
            17f, Color.rgb(220, 230, 242), maxLines = 4
        ), LinearLayout.LayoutParams(dp(context, 560), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 16) })
        left.addView(leftText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val thanks = ThtUi.horizontal(context).apply {
            setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16))
            background = ThtUi.panel(context, Color.argb(215, 6, 18, 36), Color.rgb(40, 150, 220), 16f)
            addView(FrameLayout(context).apply {
                background = ThtUi.panel(context, Color.TRANSPARENT, ThtUi.ACCENT, 12f)
                addView(ThtUi.icon(context, R.drawable.ic_ui_heart, ThtUi.ACCENT, 26), FrameLayout.LayoutParams(dp(context, 26), dp(context, 26), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)))
            val t = ThtUi.vertical(context)
            t.addView(ThtUi.text(context, "Cảm ơn bạn đã luôn đồng hành cùng THT Voice!", 18f, Color.WHITE, bold = true, maxLines = 1))
            t.addView(ThtUi.text(context, "Mỗi sự ủng hộ, dù nhỏ bé, đều góp phần tạo nên những tính năng tốt hơn cho cộng đồng người dùng Việt Nam.",
                13.5f, ThtUi.TEXT_2, maxLines = 2), ThtUi.matchWrap(context, 6))
            addView(t, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 16) })
        }
        left.addView(thanks, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(context, 24), 0, dp(context, 24), dp(context, 24))
        })

        // Right: QR card.
        val right = ThtUi.vertical(context, Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 22))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 20f)
        }
        right.addView(ThtUi.text(context, "Quét mã QR để ủng hộ", 24f, Color.WHITE, bold = true, maxLines = 1, gravity = Gravity.CENTER),
            ThtUi.matchWrap(context))
        right.addView(ThtUi.text(context, "Sử dụng ứng dụng ngân hàng để quét mã QR bên dưới", 14f, ThtUi.TEXT_2, maxLines = 2, gravity = Gravity.CENTER),
            ThtUi.matchWrap(context, 6))

        val qrSize = spec.fitDp(330, 0.28f, 200, heightFraction = 0.36f)
        val qrFrame = FrameLayout(context).apply {
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = ThtUi.panel(context, Color.TRANSPARENT, ThtUi.ACCENT, 14f, strokeDp = 2)
        }
        val qrImage = ImageView(context).apply {
            background = ThtUi.panel(context, Color.WHITE, Color.WHITE, 10f)
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Mã QR $BANK $ACCOUNT_NUMBER"
            runCatching {
                val matrix = SupportQr.matrix(SupportQr.vietQrPayload(SupportQr.MBBANK_BIN, ACCOUNT_NUMBER), 600)
                val bmp = Bitmap.createBitmap(SupportQr.pixels(matrix), matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                setImageBitmap(bmp)
            }
        }
        qrFrame.addView(qrImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        right.addView(qrFrame, LinearLayout.LayoutParams(dp(context, qrSize), dp(context, qrSize)).apply { topMargin = dp(context, 16) })

        val info = ThtUi.vertical(context).apply {
            background = ThtUi.panel(context, ThtUi.PANEL_2, ThtUi.BORDER, 14f)
        }
        listOf(
            Triple(R.drawable.ic_ui_person, "Tên người nhận", RECIPIENT),
            Triple(R.drawable.ic_ui_bank, "Ngân hàng", BANK),
            Triple(R.drawable.ic_ui_card, "Số tài khoản", ACCOUNT_NUMBER)
        ).forEachIndexed { index, (iconRes, label, value) ->
            if (index > 0) info.addView(View(context).apply { setBackgroundColor(ThtUi.BORDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
            info.addView(ThtUi.horizontal(context).apply {
                setPadding(dp(context, 18), dp(context, 12), dp(context, 12), dp(context, 12))
                addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 26))
                addView(ThtUi.text(context, label, 15f, ThtUi.TEXT_2, maxLines = 1),
                    LinearLayout.LayoutParams(dp(context, 150), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14) })
                addView(ThtUi.text(context, value, 19f, Color.WHITE, bold = true, maxLines = 1), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(ThtUi.icon(context, R.drawable.ic_ui_copy, ThtUi.ACCENT, 24).apply {
                    isClickable = true
                    contentDescription = "Sao chép $label"
                    setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2))
                    setOnClickListener { onCopy(label, value) }
                })
            })
        }
        right.addView(info, ThtUi.matchWrap(context, 18))
        right.addView(ThtUi.actionButton(context, R.drawable.ic_ui_copy, "Sao chép STK", null, primary = true) {
            onCopy("Số tài khoản", ACCOUNT_NUMBER)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 66)).apply { topMargin = dp(context, 16) })

        if (stacked) {
            root.addView(left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 520)))
            root.addView(right, ThtUi.matchWrap(context, 16))
        } else {
            root.addView(left, ThtUi.weight(0.62f).apply { marginEnd = dp(context, 16) })
            root.addView(right, ThtUi.weight(0.38f))
        }
        return root
    }
}
