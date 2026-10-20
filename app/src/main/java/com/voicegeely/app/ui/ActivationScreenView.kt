package com.voicegeely.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.voicegeely.app.security.LicenseGateState
import com.voicegeely.app.security.LicenseManager
import com.tht.voice.R


enum class ActivationUiState {
    UNLICENSED,
    VERIFYING,
    ACTIVE,
    INVALID_CODE,
    EXPIRED,
    REMOTE_BLOCKED,
    BINDING_MISMATCH,
    CLOCK_INVALID,
    TAMPERED,
    LOCKED,
    NOTICE
}

class ActivationScreenBinding(
    val root: View,
    private val codeInput: EditText,
    private val stateTitle: TextView,
    private val stateMessage: TextView,
    private val activateButton: View,
    private val restoreButton: View? = null
) {
    fun activationCode(): String = codeInput.text?.toString().orEmpty()

    fun render(state: ActivationUiState, message: String = "") {
        val (title, fallback, color) = when (state) {
            ActivationUiState.UNLICENSED -> Triple("Chưa kích hoạt", "Nhập mã kích hoạt để sử dụng THT Voice.", COLOR_ACCENT)
            ActivationUiState.VERIFYING -> Triple("Đang xác thực", "Vui lòng chờ trong giây lát...", COLOR_ACCENT)
            ActivationUiState.ACTIVE -> Triple("Kích hoạt thành công", "Đang mở THT Voice...", COLOR_SUCCESS)
            ActivationUiState.INVALID_CODE -> Triple("Mã không hợp lệ", "Kiểm tra lại mã kích hoạt và thử lại.", COLOR_ERROR)
            ActivationUiState.EXPIRED -> Triple("License đã hết hạn", "Cần gia hạn trước khi tiếp tục sử dụng.", COLOR_ERROR)
            ActivationUiState.REMOTE_BLOCKED -> Triple("Thiết bị đã bị khóa", "License hiện không được phép sử dụng.", COLOR_ERROR)
            ActivationUiState.BINDING_MISMATCH -> Triple("License không thuộc thiết bị này", "Hãy dùng mã được cấp cho đúng thiết bị.", COLOR_ERROR)
            ActivationUiState.CLOCK_INVALID -> Triple("Thời gian thiết bị không hợp lệ", "Kiểm tra ngày giờ hệ thống rồi thử lại.", COLOR_ERROR)
            ActivationUiState.TAMPERED -> Triple("Bản cài không chính thức", "Bản THT Voice này đã bị chỉnh sửa hoặc ký lại nên không dùng được. Hãy gỡ ra và cài bản chính thức từ người bán.", COLOR_ERROR)
            ActivationUiState.LOCKED -> Triple("Tạm khóa nhập mã", "Nhập sai quá nhiều lần. Chờ hết thời gian khóa hoặc nhập mã mở khóa 8 số.", COLOR_WARN)
            ActivationUiState.NOTICE -> Triple("Thông báo", "", COLOR_ACCENT)
        }
        stateTitle.text = title
        stateTitle.setTextColor(color)
        stateMessage.text = message.ifBlank { fallback }
        activateButton.isEnabled = state != ActivationUiState.VERIFYING && state != ActivationUiState.ACTIVE
        activateButton.alpha = if (activateButton.isEnabled) 1f else 0.55f
        codeInput.isEnabled = state != ActivationUiState.VERIFYING && state != ActivationUiState.ACTIVE
        restoreButton?.let {
            it.isEnabled = activateButton.isEnabled
            it.alpha = activateButton.alpha
        }
    }

    fun clearCode() = codeInput.setText("")

    companion object {
        fun fromGateState(state: LicenseGateState): ActivationUiState = when (state) {
            LicenseGateState.UNLICENSED -> ActivationUiState.UNLICENSED
            LicenseGateState.ACTIVE -> ActivationUiState.ACTIVE
            LicenseGateState.EXPIRED -> ActivationUiState.EXPIRED
            LicenseGateState.REMOTE_BLOCKED -> ActivationUiState.REMOTE_BLOCKED
            LicenseGateState.BINDING_MISMATCH -> ActivationUiState.BINDING_MISMATCH
            LicenseGateState.CLOCK_INVALID -> ActivationUiState.CLOCK_INVALID
            LicenseGateState.TAMPERED -> ActivationUiState.TAMPERED
        }

        private val COLOR_ACCENT = Color.rgb(0, 226, 255)
        private val COLOR_SUCCESS = Color.rgb(86, 230, 167)
        private val COLOR_ERROR = Color.rgb(255, 106, 125)
        private val COLOR_WARN = Color.rgb(255, 184, 77)
    }
}

object ActivationScreenView {
    fun build(
        context: Context,
        requestCode: String,
        deviceLabel: String,
        vinLabel: String,
        initialState: ActivationUiState,
        onActivate: (String) -> Unit,
        onRestore: () -> Unit = {},
        onExit: () -> Unit
    ): ActivationScreenBinding {
        val plan = ActivationLayoutPlan.from(ResponsiveContext.spec(context))
        val root = LinearLayout(context).apply {
            orientation = if (plan.stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(dp(context, 28), dp(context, 24), dp(context, 28), dp(context, 24))
            setBackgroundColor(ThtUi.BG)
        }

        // Left: night-drive artwork with the new icon and the product promise.
        val hero = android.widget.FrameLayout(context).apply {
            background = ThtUi.panel(context, Color.rgb(4, 12, 26), ThtUi.BORDER, 22f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        if (plan.heroImageDp > 0) {
            hero.addView(ImageView(context).apply {
                setImageResource(com.tht.voice.R.drawable.tht_activation_art)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        hero.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 12, 26), Color.argb(170, 4, 12, 26), Color.argb(40, 4, 12, 26), Color.argb(0, 4, 12, 26))
            )
        }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val heroText = ThtUi.vertical(context).apply { setPadding(dp(context, 36), dp(context, 32), dp(context, 32), dp(context, 28)) }
        heroText.addView(ThtUi.horizontal(context).apply {
            addView(android.widget.FrameLayout(context).apply {
                background = ThtUi.panel(context, Color.TRANSPARENT, Color.TRANSPARENT, 18f)
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                addView(ImageView(context).apply { setImageResource(com.tht.voice.R.drawable.ic_launcher_background); scaleType = ImageView.ScaleType.CENTER_CROP },
                    android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(ImageView(context).apply { setImageResource(com.tht.voice.R.drawable.ic_launcher_foreground); scaleType = ImageView.ScaleType.CENTER_CROP },
                    android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(dp(context, 76), dp(context, 76)))
            addView(ThtUi.text(context, "THT Voice", 40f, Color.WHITE, bold = true, maxLines = 1), ThtUi.wrap().apply { marginStart = dp(context, 18) })
        })
        heroText.addView(ThtUi.text(context, "Trợ lý giọng nói tiếng Việt cho xe của bạn", 19f, Color.rgb(210, 224, 240), maxLines = 2), ThtUi.matchWrap(context, 14))
        listOf(
            R.drawable.ic_ui_wave to "Nhận giọng nói offline, không cần Internet",
            R.drawable.ic_ui_shield to "Lệnh xe có kiểm tra an toàn và đọc lại trạng thái",
            R.drawable.ic_ui_person to "Bản quyền gắn với thiết bị của bạn"
        ).forEachIndexed { index, (icon, line) ->
            heroText.addView(ThtUi.horizontal(context).apply {
                addView(ThtUi.icon(context, icon, ThtUi.ACCENT, 24))
                addView(ThtUi.text(context, line, 15f, Color.rgb(200, 214, 230), maxLines = 2), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
            }, ThtUi.matchWrap(context, if (index == 0) 22 else 12))
        }
        hero.addView(heroText, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.addView(hero, if (plan.stacked) ThtUi.matchWrap(context).apply { bottomMargin = dp(context, 14) }
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.44f).apply { marginEnd = dp(context, 18) })

        // Right: activation card.
        val card = ThtUi.vertical(context).apply {
            setPadding(dp(context, 30), dp(context, 24), dp(context, 30), dp(context, 22))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 22f)
        }
        root.addView(card, if (plan.stacked) ThtUi.matchWrap(context)
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f))
        card.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_shield, "Kích hoạt ứng dụng", 26f))
        card.addView(ThtUi.text(context, "Ứng dụng và trợ lý giọng nói chỉ hoạt động sau khi bản quyền hợp lệ.", 14.5f, ThtUi.TEXT_2, maxLines = 2),
            ThtUi.matchWrap(context, 8))

        val stateTitle = ThtUi.text(context, "", 17f, ThtUi.ACCENT, bold = true, maxLines = 1)
        val stateMessage = ThtUi.text(context, "", 13.5f, ThtUi.TEXT_2, maxLines = 2)
        card.addView(ThtUi.vertical(context).apply {
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            background = ThtUi.panel(context, ThtUi.PANEL_2, Color.rgb(30, 70, 120), 14f)
            addView(stateTitle)
            addView(stateMessage, ThtUi.matchWrap(context, 4))
        }, ThtUi.matchWrap(context, 16))

        fun infoRow(icon: Int, label: String, value: String, trailing: View? = null) = ThtUi.horizontal(context).apply {
            setPadding(dp(context, 16), dp(context, 10), dp(context, 12), dp(context, 10))
            background = ThtUi.panel(context, Color.rgb(8, 22, 40), Color.rgb(22, 42, 64), 12f)
            addView(ThtUi.icon(context, icon, ThtUi.ACCENT, 22))
            addView(ThtUi.text(context, label, 14f, ThtUi.TEXT_2, maxLines = 1), LinearLayout.LayoutParams(dp(context, 130), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
            addView(ThtUi.text(context, value, 15.5f, Color.WHITE, bold = true, maxLines = 1).apply { setTextIsSelectable(true) }, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
            trailing?.let { addView(it) }
        }
        card.addView(infoRow(R.drawable.ic_ui_car, "VIN", vinLabel.ifBlank { "Chưa khả dụng" }), ThtUi.matchWrap(context, 12))
        card.addView(infoRow(R.drawable.ic_ui_monitor, "Thiết bị", deviceLabel.ifBlank { "Thiết bị Android" }), ThtUi.matchWrap(context, 8))
        card.addView(infoRow(R.drawable.ic_ui_chip, "Mã thiết bị", requestCode,
            ThtUi.smallButton(context, R.drawable.ic_ui_copy, "Sao chép", ThtUi.SmallStyle.OUTLINE) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("THT Voice device code", requestCode))
                Toast.makeText(context, "Đã sao chép mã thiết bị", Toast.LENGTH_SHORT).show()
            }.apply { minHeight = dp(context, 40) }), ThtUi.matchWrap(context, 8))

        card.addView(ThtUi.text(context, "Mã kích hoạt", 14f, ThtUi.ACCENT, bold = true), ThtUi.matchWrap(context, 18))
        val codeInput = EditText(context).apply {
            // 20 characters (new-server codes are 20 digits; old 16-character codes still fit).
            hint = "XXXX-XXXX-XXXX-XXXX-XXXX"
            textSize = 20f
            setSingleLine(true)
            letterSpacing = 0.06f
            // Letters + digits (current server issues 16-character 0-9/A-F codes; the new server 20 digits).
            // VISIBLE_PASSWORD keeps the number row and turns off autocorrect/suggestions.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(24))
            setTextColor(Color.WHITE)
            setHintTextColor(ThtUi.TEXT_3)
            setPadding(dp(context, 18), 0, dp(context, 18), 0)
            background = ThtUi.panel(context, Color.rgb(6, 18, 34), ThtUi.ACCENT_2, 14f)
        }
        card.addView(codeInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 60)).apply { topMargin = dp(context, 8) })
        var editing = false
        codeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                val formatted = LicenseManager.formatCode(s?.toString().orEmpty())
                if (formatted != s?.toString().orEmpty()) {
                    editing = true
                    codeInput.setText(formatted)
                    codeInput.setSelection(formatted.length)
                    editing = false
                }
            }
        })


        val activateButton = ThtUi.actionButton(context, R.drawable.ic_ui_shield, "Kích hoạt", null, primary = true) {
            onActivate(codeInput.text?.toString().orEmpty())
        }
        card.addView(activateButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 62)).apply { topMargin = dp(context, 18) })
        val restoreButton = ThtUi.actionButton(context, R.drawable.ic_ui_refresh, "Khôi phục bản quyền", null, primary = false) { onRestore() }
        card.addView(ThtUi.horizontal(context).apply {
            addView(restoreButton, ThtUi.weight(1f, dp(context, 54)))
            addView(ThtUi.actionButton(context, R.drawable.ic_ui_close, "Thoát ứng dụng", null, primary = false) { onExit() },
                ThtUi.weight(1f, dp(context, 54)).apply { marginStart = dp(context, 10) })
        }, ThtUi.matchWrap(context, 10))
        card.addView(ThtUi.text(context,
            "Gửi mã thiết bị cho người bán để nhận mã kích hoạt (16 ký tự chữ và số, hoặc 20 số). Cài lại ứng dụng trên cùng thiết bị: bấm \"Khôi phục bản quyền\". " +
                "Nhập sai nhiều lần sẽ bị khóa tạm; mã mở khóa 8 số nhập vào cùng ô này.",
            12.5f, ThtUi.TEXT_3, maxLines = 4), ThtUi.matchWrap(context, 14))

        val screenRoot: View = if (plan.scroll) {
            ScrollView(context).apply {
                isFillViewport = true
                setBackgroundColor(ThtUi.BG)
                addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        } else root
        return ActivationScreenBinding(screenRoot, codeInput, stateTitle, stateMessage, activateButton, restoreButton).also {
            it.render(initialState)
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
