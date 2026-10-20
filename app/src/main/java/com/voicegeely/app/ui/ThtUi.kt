package com.voicegeely.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.tht.voice.R

/**
 * Shared visual kit for the approved dark automotive design (hero banners, glowing cards,
 * icon badges, status chips, segmented choices, toggle rows). Every screen builds from these
 * pieces so colours, radii and spacing stay consistent.
 */
object ThtUi {
    // Palette
    val BG = Color.rgb(5, 11, 20)
    val RAIL = Color.rgb(6, 13, 24)
    val PANEL = Color.rgb(9, 20, 34)
    val PANEL_2 = Color.rgb(12, 27, 45)
    val BORDER = Color.rgb(28, 52, 78)
    val ACCENT = Color.rgb(0, 214, 255)
    val ACCENT_2 = Color.rgb(35, 132, 255)
    val ACCENT_DARK = Color.rgb(0, 96, 150)
    val SELECTED_FILL = Color.rgb(8, 58, 110)
    val TEXT = Color.rgb(232, 240, 250)
    val TEXT_2 = Color.rgb(150, 170, 192)
    val TEXT_3 = Color.rgb(104, 126, 150)
    val OK = Color.rgb(46, 214, 128)
    val WARN = Color.rgb(255, 190, 90)
    val ERR = Color.rgb(255, 92, 110)

    /** Colour pairs for icon badges and tinted cards. */
    enum class Tone(val start: Int, val end: Int, val card: Int, val border: Int) {
        BLUE(Color.rgb(24, 120, 255), Color.rgb(0, 200, 255), Color.rgb(9, 30, 60), Color.rgb(34, 110, 200)),
        PURPLE(Color.rgb(120, 70, 255), Color.rgb(170, 110, 255), Color.rgb(24, 20, 58), Color.rgb(96, 70, 190)),
        PINK(Color.rgb(255, 60, 120), Color.rgb(255, 110, 150), Color.rgb(46, 16, 36), Color.rgb(170, 50, 90)),
        TEAL(Color.rgb(0, 190, 170), Color.rgb(40, 230, 200), Color.rgb(8, 38, 44), Color.rgb(20, 130, 120)),
        ORANGE(Color.rgb(255, 150, 20), Color.rgb(255, 200, 70), Color.rgb(46, 30, 12), Color.rgb(170, 110, 30)),
        RED(Color.rgb(240, 50, 80), Color.rgb(255, 100, 120), Color.rgb(48, 14, 26), Color.rgb(170, 40, 70))
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    fun dpf(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    fun panel(context: Context, fill: Int = PANEL, stroke: Int = BORDER, radiusDp: Float = 18f, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dpf(context, radiusDp)
            if (stroke != Color.TRANSPARENT) setStroke(dp(context, strokeDp), stroke)
        }

    fun gradient(
        context: Context,
        colors: IntArray,
        radiusDp: Float = 16f,
        stroke: Int = Color.TRANSPARENT,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable = GradientDrawable(orientation, colors).apply {
        cornerRadius = dpf(context, radiusDp)
        if (stroke != Color.TRANSPARENT) setStroke(dp(context, 1), stroke)
    }

    /** Selected pill: deep blue gradient with a bright cyan outline, like the approved mockups. */
    fun selectedPill(context: Context, radiusDp: Float = 14f): Drawable =
        gradient(context, intArrayOf(Color.rgb(10, 70, 150), Color.rgb(8, 44, 100)), radiusDp, ACCENT_2)

    fun idlePill(context: Context, radiusDp: Float = 14f): Drawable = panel(context, PANEL_2, BORDER, radiusDp)

    fun primaryFill(context: Context, radiusDp: Float = 16f): Drawable =
        gradient(context, intArrayOf(Color.rgb(20, 110, 255), Color.rgb(0, 170, 255)), radiusDp, Color.rgb(90, 190, 255))

    fun icon(context: Context, res: Int, tint: Int = ACCENT, sizeDp: Int = 24): ImageView = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    /** Rounded-square gradient badge with a white glyph (feature and quick-action cards). */
    fun badge(context: Context, res: Int, tone: Tone, sizeDp: Int = 64, radiusDp: Float = 16f): FrameLayout =
        FrameLayout(context).apply {
            background = gradient(context, intArrayOf(tone.start, tone.end), radiusDp, orientation = GradientDrawable.Orientation.TL_BR)
            addView(ImageView(context).apply {
                setImageResource(res)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }, FrameLayout.LayoutParams(dp(context, (sizeDp * 0.5f).toInt()), dp(context, (sizeDp * 0.5f).toInt()), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float = 14f,
        color: Int = TEXT,
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
        gravity: Int = Gravity.START
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        this.gravity = gravity
        if (maxLines != Int.MAX_VALUE) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
        includeFontPadding = false
    }

    fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    fun matchWrap(context: Context, top: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, top) }
    fun weight(weight: Float, heightPx: Int = ViewGroup.LayoutParams.MATCH_PARENT) = LinearLayout.LayoutParams(0, heightPx, weight)

    fun vertical(context: Context, gravity: Int = Gravity.START): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        this.gravity = gravity
    }

    fun horizontal(context: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    /**
     * Hero banner: night-drive artwork on the right, a dark fade on the left for the text column.
     * [extra] (optional) is placed under the body text, e.g. the "Nói Hey THT" pill or toggles.
     */
    fun hero(
        context: Context,
        title: String,
        subtitle: String,
        body: String,
        heightDp: Int,
        titleGradient: Boolean = true,
        extra: View? = null,
        side: View? = null
    ): FrameLayout {
        val frame = FrameLayout(context).apply {
            background = panel(context, Color.rgb(4, 12, 26), BORDER, 20f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        frame.addView(ImageView(context).apply {
            setImageResource(R.drawable.tht_hero_night)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply {
            marginStart = dp(context, 300)
        })
        frame.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(4, 12, 26), Color.rgb(4, 12, 26), Color.argb(170, 4, 12, 26), Color.argb(0, 4, 12, 26))
            )
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val column = vertical(context, Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(context, 40), dp(context, 18), dp(context, 24), dp(context, 18))
        }
        column.addView(text(context, title, 46f, if (titleGradient) ACCENT else Color.WHITE, bold = true, maxLines = 1).apply {
            if (titleGradient) {
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, paint.measureText(title), 0f,
                    intArrayOf(Color.rgb(0, 230, 255), Color.rgb(60, 140, 255)), null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        })
        if (subtitle.isNotBlank()) column.addView(text(context, subtitle, 22f, Color.WHITE, bold = true, maxLines = 1), matchWrap(context, 8))
        if (body.isNotBlank()) column.addView(text(context, body, 15f, Color.rgb(200, 214, 230), maxLines = 3), LinearLayout.LayoutParams(
            dp(context, 560), ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 10) })
        extra?.let { column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 16) }) }
        frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        side?.let {
            frame.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp))
        return frame
    }

    /** Section title with a cyan icon, e.g. "Thiết lập popup". */
    fun sectionTitle(context: Context, iconRes: Int?, title: String, sizeSp: Float = 20f, trailing: View? = null): LinearLayout =
        horizontal(context).apply {
            iconRes?.let { addView(icon(context, it, ACCENT, 26).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(context, 12) }) }
            addView(text(context, title, sizeSp, Color.WHITE, bold = true, maxLines = 1), weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
            trailing?.let { addView(it) }
        }

    /** Green/amber/grey status chip ("Đang hoạt động", "Chưa sẵn sàng", "Đã tắt"). */
    fun chip(context: Context, label: String, state: ChipState): TextView = text(context, label, 12f, state.color, maxLines = 1).apply {
        gravity = Gravity.CENTER
        setPadding(dp(context, 10), dp(context, 5), dp(context, 12), dp(context, 5))
        background = panel(context, Color.argb(40, Color.red(state.color), Color.green(state.color), Color.blue(state.color)),
            Color.argb(120, Color.red(state.color), Color.green(state.color), Color.blue(state.color)), 12f)
        setCompoundDrawablesRelativeWithIntrinsicBounds(dot(context, state.color), null, null, null)
        compoundDrawablePadding = dp(context, 6)
    }

    enum class ChipState(val color: Int) { OK(Color.rgb(46, 214, 128)), WARN(Color.rgb(255, 190, 90)), OFF(Color.rgb(130, 146, 166)), ERR(Color.rgb(255, 92, 110)) }

    fun dot(context: Context, color: Int, sizeDp: Int = 8): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(dp(context, sizeDp), dp(context, sizeDp))
    }

    /** Card with a gradient badge, title, subtitle and chevron; whole card is clickable. */
    fun navCard(
        context: Context,
        iconRes: Int,
        tone: Tone,
        title: String,
        subtitle: String,
        badgeDp: Int = 64,
        onClick: (() -> Unit)?
    ): LinearLayout = horizontal(context).apply {
        setPadding(dp(context, 16), dp(context, 12), dp(context, 12), dp(context, 12))
        background = gradient(context, intArrayOf(tone.card, PANEL), 18f, tone.border)
        addView(badge(context, iconRes, tone, badgeDp))
        val texts = vertical(context, Gravity.CENTER_VERTICAL)
        texts.addView(text(context, title, 17f, Color.WHITE, bold = true, maxLines = 1))
        texts.addView(text(context, subtitle, 13f, TEXT_2, maxLines = 2), matchWrap(context, 4))
        addView(texts, weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14); marginEnd = dp(context, 6) })
        if (onClick != null) {
            addView(icon(context, R.drawable.ic_ui_chevron, TEXT_2, 24))
            isClickable = true
            isFocusable = true
            foreground = rippleForeground(context)
            setOnClickListener { onClick() }
        }
    }

    fun rippleForeground(context: Context): Drawable? = runCatching {
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = attrs.getDrawable(0)
        attrs.recycle()
        d
    }.getOrNull()

    /** Segmented choice: one pill per option, the selected one glows. */
    fun segmented(
        context: Context,
        labels: List<String>,
        selected: Int,
        minWidthDp: Int = 0,
        heightDp: Int = 48,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val row = horizontal(context, Gravity.CENTER_VERTICAL or Gravity.END)
        val buttons = mutableListOf<TextView>()
        fun paint(index: Int) {
            buttons.forEachIndexed { i, b ->
                val on = i == index
                b.background = if (on) selectedPill(context, 12f) else idlePill(context, 12f)
                b.setTextColor(if (on) Color.WHITE else TEXT_2)
                b.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
        labels.forEachIndexed { index, label ->
            val b = text(context, label, 14f, TEXT_2, maxLines = 1, gravity = Gravity.CENTER).apply {
                setPadding(dp(context, 14), 0, dp(context, 14), 0)
                minWidth = dp(context, minWidthDp)
                isClickable = true
                setOnClickListener { paint(index); onSelect(index) }
            }
            buttons += b
            row.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, heightDp)).apply {
                if (index > 0) marginStart = dp(context, 8)
            })
        }
        paint(selected)
        return row
    }

    /** Styled switch (cyan track when on). */
    fun switch(context: Context, checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(context).apply {
        isChecked = checked
        showText = false
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(170, 184, 200)))
        trackTintList = ColorStateList(states, intArrayOf(ACCENT_2, Color.rgb(50, 66, 86)))
        scaleX = 1.35f
        scaleY = 1.35f
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    /** Settings row: icon, title + subtitle, control on the right. */
    fun settingRow(context: Context, iconRes: Int, title: String, subtitle: String, control: View): LinearLayout =
        horizontal(context).apply {
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            background = panel(context, PANEL_2, Color.rgb(22, 42, 64), 14f)
            addView(icon(context, iconRes, ACCENT, 30))
            val texts = vertical(context, Gravity.CENTER_VERTICAL)
            texts.addView(text(context, title, 16f, Color.WHITE, bold = true, maxLines = 1))
            texts.addView(text(context, subtitle, 12.5f, TEXT_2, maxLines = 2), matchWrap(context, 3))
            addView(texts, weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 16); marginEnd = dp(context, 12) })
            addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            minimumHeight = dp(context, 78)
        }

    /** Big action button with icon + title + subtitle (e.g. "Lưu / Áp dụng các thiết lập"). */
    fun actionButton(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String?,
        primary: Boolean,
        onClick: () -> Unit
    ): LinearLayout = horizontal(context, Gravity.CENTER).apply {
        setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8))
        background = if (primary) primaryFill(context) else panel(context, Color.rgb(8, 22, 40), Color.rgb(40, 110, 190), 16f)
        addView(icon(context, iconRes, if (primary) Color.WHITE else ACCENT, 28))
        val texts = vertical(context, Gravity.CENTER_VERTICAL)
        texts.addView(text(context, title, 17f, Color.WHITE, bold = true, maxLines = 1))
        subtitle?.let { texts.addView(text(context, it, 12.5f, if (primary) Color.rgb(220, 236, 255) else TEXT_2, maxLines = 1), matchWrap(context, 2)) }
        addView(texts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 16) })
        isClickable = true
        isFocusable = true
        foreground = rippleForeground(context)
        setOnClickListener { onClick() }
    }

    /** Compact button for cards ("Mở", "Mặc định"). [highlighted] marks the current default. */
    fun smallButton(context: Context, iconRes: Int?, label: String, style: SmallStyle, onClick: () -> Unit): TextView =
        text(context, label, 14f, Color.WHITE, bold = style != SmallStyle.OUTLINE, maxLines = 1, gravity = Gravity.CENTER).apply {
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            background = when (style) {
                SmallStyle.PRIMARY -> primaryFill(context, 12f)
                SmallStyle.HIGHLIGHT -> gradient(context, intArrayOf(Color.rgb(0, 150, 110), Color.rgb(0, 190, 150)), 12f, OK)
                SmallStyle.OUTLINE -> panel(context, Color.rgb(10, 24, 42), Color.rgb(50, 80, 120), 12f)
            }
            setTextColor(if (style == SmallStyle.OUTLINE) Color.rgb(200, 216, 234) else Color.WHITE)
            iconRes?.let { res ->
                val d = context.getDrawable(res)?.mutate()
                d?.setTint(if (style == SmallStyle.OUTLINE) Color.rgb(200, 216, 234) else Color.WHITE)
                d?.setBounds(0, 0, dp(context, 18), dp(context, 18))
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = dp(context, 6)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    enum class SmallStyle { PRIMARY, HIGHLIGHT, OUTLINE }

    /** Themed single-choice picker (e.g. default media / map app). */
    fun showChoice(
        context: Context,
        iconRes: Int,
        title: String,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = vertical(context).apply {
            setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 18))
            background = gradient(context, intArrayOf(Color.rgb(10, 26, 46), Color.rgb(6, 14, 26)), 22f, Color.rgb(40, 110, 190),
                GradientDrawable.Orientation.TOP_BOTTOM)
        }
        root.addView(sectionTitle(context, iconRes, title, 21f))
        val list = vertical(context)
        options.forEachIndexed { index, label ->
            val on = index == selected
            list.addView(horizontal(context).apply {
                setPadding(dp(context, 16), 0, dp(context, 16), 0)
                background = if (on) selectedPill(context, 12f) else idlePill(context, 12f)
                addView(text(context, label, 16f, if (on) Color.WHITE else TEXT, bold = on, maxLines = 1), weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
                if (on) addView(icon(context, R.drawable.ic_ui_check, ACCENT, 22))
                isClickable = true
                setOnClickListener { dialog.dismiss(); onPick(index) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 54)).apply { topMargin = dp(context, 8) })
        }
        val maxPx = (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST))
            }
        }
        scroll.addView(list)
        root.addView(scroll, matchWrap(context, 8))
        root.addView(smallButton(context, null, "Đóng", SmallStyle.OUTLINE) { dialog.dismiss() },
            LinearLayout.LayoutParams(dp(context, 150), dp(context, 48)).apply { topMargin = dp(context, 14); gravity = Gravity.END })
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val metrics = context.resources.displayMetrics
            setLayout((metrics.widthPixels * 0.42f).toInt().coerceAtLeast(dp(context, 340)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }

    /** Themed dialog matching the app (replaces the stock white AlertDialog). */
    fun showDialog(
        context: Context,
        iconRes: Int,
        title: String,
        body: CharSequence,
        primaryLabel: String = "Đóng",
        onPrimary: (() -> Unit)? = null,
        secondaryLabel: String? = null,
        onSecondary: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = vertical(context).apply {
            setPadding(dp(context, 28), dp(context, 24), dp(context, 28), dp(context, 20))
            background = gradient(context, intArrayOf(Color.rgb(10, 26, 46), Color.rgb(6, 14, 26)), 22f, Color.rgb(40, 110, 190),
                GradientDrawable.Orientation.TOP_BOTTOM)
        }
        root.addView(sectionTitle(context, iconRes, title, 22f))
        val maxBodyPx = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxBodyPx, View.MeasureSpec.AT_MOST))
            }
        }
        scroll.addView(text(context, body, 15f, Color.rgb(210, 222, 236)).apply { setLineSpacing(0f, 1.25f) })
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 16) })
        val buttons = horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
        secondaryLabel?.let { label ->
            buttons.addView(smallButton(context, null, label, SmallStyle.OUTLINE) { dialog.dismiss(); onSecondary?.invoke() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 50)).apply { marginEnd = dp(context, 12) })
        }
        buttons.addView(smallButton(context, null, primaryLabel, SmallStyle.PRIMARY) { dialog.dismiss(); onPrimary?.invoke() },
            LinearLayout.LayoutParams(dp(context, 160), dp(context, 50)))
        root.addView(buttons, matchWrap(context, 16))
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val metrics = context.resources.displayMetrics
            setLayout((metrics.widthPixels * 0.62f).toInt().coerceAtLeast(dp(context, 360)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }

    /** About → Hỗ trợ: Facebook button + Zalo add-friend QR. */
    fun showSupportContact(context: Context, onFacebook: () -> Unit): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = vertical(context).apply {
            setPadding(dp(context, 28), dp(context, 24), dp(context, 28), dp(context, 20))
            background = gradient(context, intArrayOf(Color.rgb(10, 26, 46), Color.rgb(6, 14, 26)), 22f, Color.rgb(40, 110, 190),
                GradientDrawable.Orientation.TOP_BOTTOM)
        }
        root.addView(sectionTitle(context, R.drawable.ic_ui_chat, "Hỗ trợ", 22f))
        root.addView(smallButton(context, R.drawable.ic_ui_facebook, "Nhắn Facebook", SmallStyle.PRIMARY) { dialog.dismiss(); onFacebook() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 54)).apply { topMargin = dp(context, 16) })
        root.addView(text(context, "Quét mã để kết bạn Zalo", 15f, Color.rgb(210, 222, 236)).apply { gravity = Gravity.CENTER },
            matchWrap(context, 16))
        val side = (minOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels) * 0.42f).toInt()
        root.addView(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.tht_zalo_qr)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            contentDescription = "Mã QR Zalo"
        }, LinearLayout.LayoutParams(side, side).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(context, 10) })
        root.addView(smallButton(context, null, "Đóng", SmallStyle.OUTLINE) { dialog.dismiss() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50)).apply { topMargin = dp(context, 16) })
        dialog.setContentView(ScrollView(context).apply { addView(root) })
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((context.resources.displayMetrics.widthPixels * 0.5f).toInt().coerceAtLeast(dp(context, 340)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }
}
