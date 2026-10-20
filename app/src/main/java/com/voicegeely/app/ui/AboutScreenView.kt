package com.voicegeely.app.ui

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.tht.voice.R
import com.voicegeely.app.ui.ThtUi.dp

/** "Giới thiệu": hero, 8 feature cards, version + system info, 4 action buttons. */
object AboutScreenView {
    data class Actions(
        val onCheckUpdate: () -> Unit,
        val onChangelog: () -> Unit,
        val onManual: () -> Unit,
        val onProject: () -> Unit,
        val onAssistant: () -> Unit = {},
        val onApps: () -> Unit = {},
        val onSettings: () -> Unit = {},
        val onFeature: (title: String, body: String) -> Unit = { _, _ -> }
    )

    fun build(context: Context, actions: Actions): View {
        val spec = ResponsiveContext.spec(context)
        val narrow = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
        val root = ThtUi.vertical(context)

        val logoTitle = ThtUi.horizontal(context).apply {
            addView(ThtUi.icon(context, R.drawable.ic_ui_wave, ThtUi.ACCENT, 64))
            addView(ThtUi.text(context, "THT Voice", 52f, ThtUi.ACCENT, bold = true, maxLines = 1), ThtUi.wrap().apply { marginStart = dp(context, 18) })
        }
        root.addView(ThtUi.hero(
            context,
            title = "",
            subtitle = "Trợ lý giọng nói tiếng Việt cho xe của bạn",
            body = "THT Voice giúp bạn điều khiển xe, tra cứu thông tin, giải trí và nhiều tác vụ khác bằng giọng nói tiếng Việt, mang lại trải nghiệm lái xe thông minh, an toàn và thuận tiện hơn mỗi ngày.",
            heightDp = spec.fitDp(300, 1f, 230, heightFraction = 0.28f),
            titleGradient = false,
            extra = null
        ).also { hero ->
            // Put the logo row above the subtitle inside the hero's text column.
            ((hero.getChildAt(2) as? LinearLayout))?.let { column ->
                column.removeViewAt(0)
                column.addView(logoTitle, 0)
            }
        })

        root.addView(ThtUi.sectionTitle(context, null, "Tính năng chính", 21f), ThtUi.matchWrap(context, 18))
        val features = listOf(
            Feature(R.drawable.ic_ui_mic, ThtUi.Tone.BLUE, "Trợ lý giọng nói", "Hiểu và thực hiện lệnh tiếng Việt tự nhiên", actions.onAssistant, null),
            Feature(R.drawable.ic_ui_car, ThtUi.Tone.PURPLE, "Điều khiển xe", "Điều khiển các tính năng của xe bằng giọng nói", null,
                "Điều hòa (nhiệt độ, quạt, hướng gió, lấy gió, sấy kính/gương, Eco), kính cửa sổ, chế độ lái, hồi năng lượng, đèn viền, khóa cửa, cốp.\n\nTrợ lý chỉ báo \"Đã…\" khi đọc lại được trạng thái từ xe. Kính, chế độ lái và hồi năng lượng cần xác nhận khi xe đang chạy; khóa cửa, cốp, đèn pha… chỉ chạy khi xe ở số P."),
            Feature(R.drawable.ic_ui_music, ThtUi.Tone.PINK, "Media giải trí", "Nghe nhạc, xem video, podcast dễ dàng hơn", actions.onApps, null),
            Feature(R.drawable.ic_ui_place, ThtUi.Tone.TEAL, "Bản đồ dẫn đường", "Tìm kiếm địa điểm, dẫn đường bằng ứng dụng mặc định", actions.onApps, null),
            Feature(R.drawable.ic_ui_apps, ThtUi.Tone.PURPLE, "Quản lý ứng dụng", "Mở và quản lý các ứng dụng giải trí, bản đồ, ...", actions.onApps, null),
            Feature(R.drawable.ic_ui_wave, ThtUi.Tone.TEAL, "Nhận dạng mạnh mẽ", "Chạy offline, hiểu nhiều cách diễn đạt", null,
                "Nhận dạng giọng nói tiếng Việt chạy hoàn toàn trên thiết bị, không cần Internet.\n\nTrợ lý chuẩn hóa lỗi nghe nhầm thường gặp (\"bạc điều hòa\" → bật điều hòa, \"mở cánh lái\" → mở kính lái…) và hiểu nhiều cách nói cho cùng một lệnh. Câu mơ hồ sẽ không được đoán để thao tác xe."),
            Feature(R.drawable.ic_ui_headphones, ThtUi.Tone.ORANGE, "Giọng nói tự nhiên", "Phản hồi mượt mà, gần gũi và thân thiện", actions.onSettings, null),
            Feature(R.drawable.ic_ui_shield, ThtUi.Tone.RED, "An toàn & riêng tư", "Bảo vệ dữ liệu, xử lý an toàn và minh bạch", null,
                "Giọng nói được xử lý ngay trên xe, không gửi lên máy chủ.\n\nLệnh nhạy cảm kiểm tra tốc độ và cần số trước khi chạy; thiếu dữ liệu thì chặn. Không cập nhật OTA khi xe đang chạy. License lưu mã hóa bằng Android Keystore; VIN chỉ lưu dưới dạng mã băm.")
        )
        val perRow = if (narrow) 2 else 4
        features.chunked(perRow).forEachIndexed { rowIndex, chunk ->
            val row = ThtUi.horizontal(context)
            chunk.forEachIndexed { index, f ->
                val click: () -> Unit = f.action ?: { actions.onFeature(f.title, f.detail.orEmpty()) }
                row.addView(ThtUi.navCard(context, f.icon, f.tone, f.title, f.subtitle, badgeDp = 58, onClick = click),
                    ThtUi.weight(1f, dp(context, 100)).apply { if (index > 0) marginStart = dp(context, 14) })
            }
            root.addView(row, ThtUi.matchWrap(context, if (rowIndex == 0) 12 else 12))
        }

        val info = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val version = infoPanel(context, R.drawable.ic_ui_info, "Thông tin phiên bản")
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = packageInfo?.versionName ?: "—"
        val versionCode = packageInfo?.longVersionCode?.toString() ?: "—"
        listOf(
            Triple(R.drawable.ic_ui_wave, "Phiên bản ứng dụng", "v$versionName (Build $versionCode)"),
            Triple(R.drawable.ic_ui_person, "Tác giả", "THT Voice Team"),
            Triple(R.drawable.ic_ui_code, "Nền tảng hỗ trợ", "Android Automotive, Android 9.0+")
        ).forEach { (iconRes, label, value) -> version.addView(infoRow(context, iconRes, label, value, ThtUi.ACCENT)) }

        val system = infoPanel(context, R.drawable.ic_ui_monitor, "Thông tin hệ thống")
        val rows = runtimeRows(context)
        val grid = ThtUi.horizontal(context, Gravity.TOP)
        val colA = ThtUi.vertical(context)
        val colB = ThtUi.vertical(context)
        rows.forEachIndexed { index, (iconRes, label, value) ->
            (if (index % 2 == 0) colA else colB).addView(infoRow(context, iconRes, label, value, if (value.startsWith("Đã kết nối")) ThtUi.ACCENT else ThtUi.TEXT))
        }
        grid.addView(colA, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context, 18) })
        grid.addView(colB, ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
        system.addView(grid, ThtUi.matchWrap(context, 2))

        if (narrow) {
            info.addView(version, ThtUi.matchWrap(context))
            info.addView(system, ThtUi.matchWrap(context, 12))
        } else {
            info.addView(version, ThtUi.weight(0.47f, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = dp(context, 14) })
            info.addView(system, ThtUi.weight(0.53f, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(info, ThtUi.matchWrap(context, 16))

        val buttons = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        listOf(
            Triple(Triple(R.drawable.ic_ui_refresh, "Kiểm tra cập nhật", ThtUi.Tone.BLUE), actions.onCheckUpdate, 0),
            Triple(Triple(R.drawable.ic_ui_list, "Xem nhật ký phiên bản", ThtUi.Tone.PURPLE), actions.onChangelog, 1),
            Triple(Triple(R.drawable.ic_ui_book, "Hướng dẫn sử dụng", ThtUi.Tone.TEAL), actions.onManual, 2),
            Triple(Triple(R.drawable.ic_ui_chat, "Hỗ trợ", ThtUi.Tone.BLUE), actions.onProject, 3)
        ).forEach { (meta, action, index) ->
            val (iconRes, label, tone) = meta
            val button = ThtUi.horizontal(context).apply {
                setPadding(dp(context, 22), 0, dp(context, 16), 0)
                background = ThtUi.gradient(context, intArrayOf(tone.card, ThtUi.PANEL), 16f, tone.border)
                addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 28))
                addView(ThtUi.text(context, label, 16f, Color.WHITE, bold = true, maxLines = 1, gravity = Gravity.CENTER),
                    ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(ThtUi.icon(context, R.drawable.ic_ui_chevron, ThtUi.TEXT_2, 24))
                isClickable = true
                foreground = ThtUi.rippleForeground(context)
                setOnClickListener { action() }
            }
            if (narrow) buttons.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 60)).apply { if (index > 0) topMargin = dp(context, 10) })
            else buttons.addView(button, ThtUi.weight(1f, dp(context, 60)).apply { if (index > 0) marginStart = dp(context, 14) })
        }
        root.addView(buttons, ThtUi.matchWrap(context, 16))
        return root
    }

    private data class Feature(
        val icon: Int,
        val tone: ThtUi.Tone,
        val title: String,
        val subtitle: String,
        val action: (() -> Unit)?,
        val detail: String?
    )

    private fun infoPanel(context: Context, iconRes: Int, title: String): LinearLayout = ThtUi.vertical(context).apply {
        setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 12))
        background = ThtUi.panel(context, ThtUi.PANEL, ThtUi.BORDER, 18f)
        addView(ThtUi.sectionTitle(context, iconRes, title, 19f))
    }

    private fun infoRow(context: Context, iconRes: Int, label: String, value: String, valueColor: Int): View = ThtUi.horizontal(context).apply {
        setPadding(0, dp(context, 9), 0, dp(context, 9))
        addView(ThtUi.icon(context, iconRes, ThtUi.ACCENT, 24))
        addView(ThtUi.text(context, label, 15f, ThtUi.TEXT_2, maxLines = 1), ThtUi.weight(1f, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(context, 14) })
        addView(ThtUi.text(context, value, 15f, valueColor, maxLines = 1, gravity = Gravity.END))
    }

    /** Real device values only; unknown ones show "—". */
    internal fun runtimeRows(context: Context): List<Triple<Int, String, String>> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        val totalRam = runCatching { activityManager?.getMemoryInfo(memoryInfo); memoryInfo.totalMem }
            .getOrNull()?.takeIf { it > 0L }?.let(::formatBytes) ?: "—"
        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            "${formatBytes(stat.totalBytes)} (còn trống ${formatBytes(stat.availableBytes)})"
        }.getOrDefault("—")
        val metrics = context.resources.displayMetrics
        val wifi = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) "Đã kết nối" else "Chưa kết nối"
        }.getOrDefault("—")
        @Suppress("DEPRECATION")
        val bluetooth = runCatching { if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) "Đã bật" else "Đã tắt" }.getOrDefault("—")
        return listOf(
            Triple(R.drawable.ic_ui_code, "Phiên bản Android", "Android ${Build.VERSION.RELEASE.orEmpty()} (API ${Build.VERSION.SDK_INT})"),
            Triple(R.drawable.ic_ui_chip, "Bộ xử lý (CPU)", "${Runtime.getRuntime().availableProcessors()} nhân • ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}"),
            Triple(R.drawable.ic_ui_chip, "Bộ nhớ RAM", totalRam),
            Triple(R.drawable.ic_ui_storage, "Bộ nhớ trong", storage),
            Triple(R.drawable.ic_ui_monitor, "Độ phân giải màn hình", "${metrics.widthPixels} × ${metrics.heightPixels}"),
            Triple(R.drawable.ic_ui_wifi, "Wi-Fi", wifi),
            Triple(R.drawable.ic_ui_bluetooth, "Bluetooth", bluetooth)
        )
    }

    internal fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f GB", gib)
    }
}
