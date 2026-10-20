package com.voicegeely.app.ui.apps

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.tht.voice.R
import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.settings.MediaAppSettings
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.ResponsiveContext
import com.voicegeely.app.ui.ResponsiveLayout
import com.voicegeely.app.ui.ThtUi
import com.voicegeely.app.ui.ThtUi.dp

/**
 * "Ứng dụng": search, category pills, sectioned app cards (Mở / Mặc định), current defaults
 * with "Thay đổi", quick voice tries, refresh and save. The card of the current default media
 * or map app shows a lit "Mặc định" button; others show "Đặt mặc định".
 */
object AppsScreenView {
    fun build(
        context: Context,
        mediaSettings: MediaAppSettings,
        voicePreferences: UserVoicePreferences,
        onVoiceTest: (String) -> Unit,
        onSaved: () -> Unit = {}
    ): View {
        val spec = ResponsiveContext.spec(context)
        val narrow = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
        val repository = InstalledAppRepository(context)
        val favoritesStore = AppFavoritesStore(context)
        var apps = repository.load()
        var selectedCategory = AppUiCategory.ALL
        var query = ""

        val root = ThtUi.vertical(context)
        val body = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        root.addView(body, ThtUi.matchWrap(context))

        val main = ThtUi.vertical(context)
        val side = ThtUi.vertical(context)
        if (narrow) {
            body.addView(main, ThtUi.matchWrap(context))
            body.addView(side, ThtUi.matchWrap(context, 14))
        } else {
            body.addView(main, ThtUi.weight(0.74f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 16) })
            body.addView(side, ThtUi.weight(0.26f, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Search.
        val search = EditText(context).apply {
            hint = "Tìm ứng dụng... (ví dụ: YouTube, Maps, Zing MP3...)"
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(ThtUi.TEXT_3)
            background = null
        }
        main.addView(ThtUi.horizontal(context).apply {
            setPadding(dp(context, 20), 0, dp(context, 20), 0)
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 30f)
            addView(ThtUi.icon(context, R.drawable.ic_ui_search, ThtUi.TEXT_2, 26))
            addView(search, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 60)))

        // Category pills.
        val categoryRow = ThtUi.horizontal(context)
        val categoryButtons = linkedMapOf<AppUiCategory, LinearLayout>()
        listOf(
            Triple(AppUiCategory.ALL, R.drawable.ic_ui_apps, "Tất cả"),
            Triple(AppUiCategory.FAVORITES, R.drawable.ic_ui_heart, "Yêu thích"),
            Triple(AppUiCategory.MEDIA, R.drawable.ic_ui_music, "Media"),
            Triple(AppUiCategory.MAP, R.drawable.ic_ui_place, "Bản đồ"),
            Triple(AppUiCategory.OTHER, R.drawable.ic_ui_more, "Khác")
        ).forEachIndexed { index, (category, iconRes, label) ->
            val pill = ThtUi.horizontal(context, Gravity.CENTER).apply {
                setPadding(dp(context, 22), 0, dp(context, 22), 0)
                addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 22))
                addView(ThtUi.text(context, label, 15f, Color.WHITE, maxLines = 1), ThtUi.wrap().apply { marginStart = dp(context, 10) })
                isClickable = true
            }
            categoryButtons[category] = pill
            categoryRow.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 50)).apply {
                if (index > 0) marginStart = dp(context, 12)
            })
        }
        main.addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(categoryRow)
        }, ThtUi.matchWrap(context, 16))

        val sections = ThtUi.vertical(context)
        main.addView(sections, ThtUi.matchWrap(context, 8))

        // Side: current defaults + quick voice tries.
        val defaultsCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        }
        side.addView(defaultsCard, ThtUi.matchWrap(context))
        val quickCard = ThtUi.vertical(context).apply {
            setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
            background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
            addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_mic, "Thử nhanh với giọng nói", 19f))
            listOf(
                "Mở YouTube" to "Mở ứng dụng YouTube (hoặc Vanced, ReVanced… nếu không có)",
                "Mở bản đồ" to "Mở ứng dụng bản đồ mặc định",
                "Chỉ đường về nhà" to "Mở bản đồ và chỉ đường về nhà",
                "Mở Zing MP3" to "Phát nhạc trên Zing MP3"
            ).forEach { (phrase, hint) ->
                addView(ThtUi.horizontal(context).apply {
                    setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12))
                    background = ThtUi.panel(context, Color.rgb(8, 22, 40), Color.rgb(28, 56, 90), 14f)
                    addView(ThtUi.icon(context, R.drawable.ic_ui_wave, ThtUi.ACCENT, 30))
                    val t = ThtUi.vertical(context)
                    t.addView(ThtUi.text(context, "“$phrase”", 16f, Color.WHITE, bold = true, maxLines = 1))
                    t.addView(ThtUi.text(context, hint, 12.5f, ThtUi.TEXT_2, maxLines = 2), ThtUi.matchWrap(context, 3))
                    addView(t, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14) })
                    isClickable = true
                    foreground = ThtUi.rippleForeground(context)
                    setOnClickListener { onVoiceTest(phrase.lowercase()) }
                }, ThtUi.matchWrap(context, 10))
            }
        }
        side.addView(quickCard, ThtUi.matchWrap(context, 14))

        // Bottom actions.
        val bottom = ThtUi.horizontal(context)
        root.addView(bottom, ThtUi.matchWrap(context, 16))

        val columns = if (narrow) 2 else ((spec.contentWidthDp * 0.74f) / 230f).toInt().coerceIn(3, 6)
        lateinit var renderAll: () -> Unit

        fun isCurrentDefault(app: LauncherAppEntry): Boolean =
            (app.defaultMedia != null && app.defaultMedia == mediaSettings.defaultMediaApp) ||
                (app.defaultNavigation != null && app.defaultNavigation == voicePreferences.defaultNavigationApp)

        fun appCard(app: LauncherAppEntry, favorite: Boolean): View {
            val card = ThtUi.vertical(context).apply {
                setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
                background = ThtUi.panel(context, ThtUi.PANEL, if (isCurrentDefault(app)) ThtUi.OK else ThtUi.BORDER, 16f)
            }
            card.addView(ThtUi.horizontal(context).apply {
                addView(android.widget.FrameLayout(context).apply {
                    addView(ImageView(context).apply {
                        runCatching { setImageDrawable(context.packageManager.getApplicationIcon(app.packageName)) }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = app.label
                    }, android.widget.FrameLayout.LayoutParams(dp(context, 50), dp(context, 50)))
                    // Patched YouTube clients often reuse YouTube's own launcher icon; a short tag
                    // tells them apart without redrawing any third-party logo.
                    variantTag(app)?.let { (tag, color) ->
                        addView(ThtUi.text(context, tag, 10f, Color.WHITE, bold = true, maxLines = 1, gravity = Gravity.CENTER).apply {
                            setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
                            background = ThtUi.panel(context, color, Color.WHITE, 8f)
                        }, android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END
                        ))
                    }
                }, LinearLayout.LayoutParams(dp(context, 56), dp(context, 56)))
                val t = ThtUi.vertical(context)
                t.addView(ThtUi.text(context, app.label, 16f, Color.WHITE, bold = true, maxLines = 1))
                t.addView(ThtUi.text(context, "Đã cài", 12.5f, ThtUi.OK, maxLines = 1).apply {
                    setCompoundDrawablesRelativeWithIntrinsicBounds(ThtUi.dot(context, ThtUi.OK), null, null, null)
                    compoundDrawablePadding = dp(context, 6)
                }, ThtUi.matchWrap(context, 4))
                addView(t, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
                addView(ThtUi.icon(context, R.drawable.ic_ui_star, if (favorite) ThtUi.WARN else ThtUi.TEXT_3, 24).apply {
                    isClickable = true
                    contentDescription = if (favorite) "Bỏ yêu thích" else "Yêu thích"
                    setOnClickListener { favoritesStore.toggle(app.packageName); renderAll() }
                })
            })
            card.addView(ThtUi.smallButton(context, R.drawable.ic_ui_play, "Mở", ThtUi.SmallStyle.PRIMARY) {
                if (!repository.open(app.packageName)) Toast.makeText(context, "Không mở được ${app.label}", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)).apply { topMargin = dp(context, 12) })
            if (app.defaultMedia != null || app.defaultNavigation != null) {
                val current = isCurrentDefault(app)
                card.addView(ThtUi.smallButton(
                    context,
                    if (current) R.drawable.ic_ui_check_circle else R.drawable.ic_ui_star,
                    if (current) "Mặc định" else "Đặt mặc định",
                    if (current) ThtUi.SmallStyle.HIGHLIGHT else ThtUi.SmallStyle.OUTLINE
                ) {
                    app.defaultMedia?.let { mediaSettings.defaultMediaApp = it }
                    app.defaultNavigation?.let { voicePreferences.defaultNavigationApp = it }
                    onSaved()
                    Toast.makeText(context, "Đã đặt ${app.label} làm mặc định", Toast.LENGTH_SHORT).show()
                    renderAll()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)).apply { topMargin = dp(context, 8) })
            }
            return card
        }

        fun grid(list: List<LauncherAppEntry>, favorites: Set<String>): View {
            val wrap = ThtUi.vertical(context)
            list.chunked(columns).forEachIndexed { rowIndex, chunk ->
                val row = ThtUi.horizontal(context, Gravity.TOP)
                chunk.forEachIndexed { index, app ->
                    row.addView(appCard(app, app.packageName in favorites), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) marginStart = dp(context, 12)
                    })
                }
                repeat(columns - chunk.size) { row.addView(View(context), ThtUi.weight(1f, 1).apply { marginStart = dp(context, 12) }) }
                wrap.addView(row, ThtUi.matchWrap(context, if (rowIndex == 0) 0 else 12))
            }
            return wrap
        }

        fun section(iconRes: Int, title: String, list: List<LauncherAppEntry>, favorites: Set<String>) {
            if (list.isEmpty()) return
            sections.addView(ThtUi.sectionTitle(context, iconRes, title, 19f,
                trailing = ThtUi.text(context, "${list.size} ứng dụng", 13f, ThtUi.ACCENT, maxLines = 1)), ThtUi.matchWrap(context, 16))
            sections.addView(grid(list, favorites), ThtUi.matchWrap(context, 10))
        }

        fun renderDefaults() {
            defaultsCard.removeAllViews()
            defaultsCard.addView(ThtUi.sectionTitle(context, R.drawable.ic_ui_gear, "Mặc định hiện tại", 19f))
            fun row(iconRes: Int, tone: ThtUi.Tone, label: String, value: String, onChange: () -> Unit) {
                defaultsCard.addView(ThtUi.horizontal(context).apply {
                    setPadding(0, dp(context, 12), 0, dp(context, 4))
                    addView(ThtUi.badge(context, iconRes, tone, 52, 14f))
                    val t = ThtUi.vertical(context)
                    t.addView(ThtUi.text(context, label, 13f, ThtUi.TEXT_2, maxLines = 1))
                    t.addView(ThtUi.text(context, value, 18f, Color.WHITE, bold = true, maxLines = 1), ThtUi.matchWrap(context, 3))
                    addView(t, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 12) })
                    addView(ThtUi.smallButton(context, null, "Thay đổi", ThtUi.SmallStyle.PRIMARY, onChange),
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 42)))
                })
            }
            val mediaValues = DefaultMediaApp.values().toList()
            val mediaLabels = listOf("Tự động", "YouTube", "Vanced", "ReVanced", "Morphe", "SmartTube", "YouTube Music", "Zing MP3", "Spotify")
            row(R.drawable.ic_ui_music, ThtUi.Tone.PINK, "Ứng dụng media mặc định", mediaLabels[mediaValues.indexOf(mediaSettings.defaultMediaApp)]) {
                ThtUi.showChoice(context, R.drawable.ic_ui_music, "Ứng dụng media mặc định", mediaLabels, mediaValues.indexOf(mediaSettings.defaultMediaApp)) {
                    mediaSettings.defaultMediaApp = mediaValues[it]
                    onSaved()
                    renderAll()
                }
            }
            val navValues = DefaultNavigationApp.values().toList()
            val navLabels = listOf("Tự động", "Google Maps", "Vietmap Live", "Waze")
            row(R.drawable.ic_ui_place, ThtUi.Tone.TEAL, "Ứng dụng bản đồ mặc định", navLabels[navValues.indexOf(voicePreferences.defaultNavigationApp)]) {
                ThtUi.showChoice(context, R.drawable.ic_ui_place, "Ứng dụng bản đồ mặc định", navLabels, navValues.indexOf(voicePreferences.defaultNavigationApp)) {
                    voicePreferences.defaultNavigationApp = navValues[it]
                    onSaved()
                    renderAll()
                }
            }
        }

        fun renderApps() {
            val favorites = favoritesStore.all()
            sections.removeAllViews()
            if (selectedCategory == AppUiCategory.ALL && query.isBlank()) {
                section(R.drawable.ic_ui_heart, "Yêu thích", AppLauncherFilter.filter(apps, AppUiCategory.FAVORITES, "", favorites), favorites)
                section(R.drawable.ic_ui_music, "Media", AppLauncherFilter.filter(apps, AppUiCategory.MEDIA, "", favorites), favorites)
                section(R.drawable.ic_ui_place, "Bản đồ", AppLauncherFilter.filter(apps, AppUiCategory.MAP, "", favorites), favorites)
                section(R.drawable.ic_ui_apps, "Ứng dụng khác", AppLauncherFilter.filter(apps, AppUiCategory.OTHER, "", favorites), favorites)
            } else {
                val filtered = AppLauncherFilter.filter(apps, selectedCategory, query, favorites)
                if (filtered.isEmpty()) {
                    sections.addView(ThtUi.text(context,
                        if (query.isBlank()) "Không có ứng dụng trong nhóm này." else "Không tìm thấy ứng dụng phù hợp.",
                        16f, ThtUi.TEXT_2, gravity = Gravity.CENTER), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 160)))
                } else {
                    section(R.drawable.ic_ui_apps, "Kết quả", filtered, favorites)
                }
            }
        }

        fun paintCategories() {
            categoryButtons.forEach { (category, pill) ->
                pill.background = if (category == selectedCategory) ThtUi.selectedPill(context, 25f) else ThtUi.idlePill(context, 25f)
            }
        }

        renderAll = {
            renderDefaults()
            renderApps()
        }

        categoryButtons.forEach { (category, pill) ->
            pill.setOnClickListener {
                selectedCategory = category
                paintCategories()
                renderApps()
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                renderApps()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        bottom.addView(ThtUi.actionButton(context, R.drawable.ic_ui_refresh, "Làm mới danh sách ứng dụng", "Quét lại các ứng dụng đã cài đặt trên xe", primary = false) {
            apps = repository.load()
            renderAll()
            Toast.makeText(context, "Đã làm mới danh sách ứng dụng", Toast.LENGTH_SHORT).show()
        }, ThtUi.weight(0.4f, dp(context, 70)))
        bottom.addView(View(context), ThtUi.weight(0.3f, 1))
        bottom.addView(ThtUi.actionButton(context, R.drawable.ic_ui_save, "Lưu thiết lập", "Áp dụng ứng dụng mặc định", primary = true) {
            onSaved()
            Toast.makeText(context, "Đã lưu thiết lập ứng dụng", Toast.LENGTH_SHORT).show()
        }, ThtUi.weight(0.3f, dp(context, 70)))

        paintCategories()
        renderAll()
        return root
    }

    private fun variantTag(app: LauncherAppEntry): Pair<String, Int>? = when (app.defaultMedia) {
        DefaultMediaApp.VANCED -> "Vanced" to Color.rgb(40, 110, 220)
        DefaultMediaApp.REVANCED -> "ReVanced" to Color.rgb(200, 60, 120)
        DefaultMediaApp.MORPHE -> "Morphe" to Color.rgb(120, 70, 220)
        DefaultMediaApp.SMARTTUBE -> "SmartTube" to Color.rgb(210, 50, 50)
        else -> null
    }
}
