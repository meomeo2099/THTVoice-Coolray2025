package com.voicegeely.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voicegeely.app.car.AndroidCarVehiclePropertyAccess
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.settings.MediaAppSettings
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.AboutScreenView
import com.voicegeely.app.ui.AssistantRuntimeStore
import com.voicegeely.app.ui.AssistantScreenBinding
import com.voicegeely.app.ui.AssistantScreenView
import com.voicegeely.app.ui.HomeScreenBinding
import com.voicegeely.app.ui.HomeScreenView
import com.voicegeely.app.ui.MainDestination
import com.voicegeely.app.ui.SettingsScreenView
import com.voicegeely.app.ui.SupportScreenView
import com.voicegeely.app.ui.apps.AppsScreenView
import com.voicegeely.app.update.AppUpdateFlowStatus
import com.voicegeely.app.update.SafeAppUpdateManager
import com.voicegeely.app.voice.safety.VehicleSafetyGate

class MainActivity : AppCompatActivity() {
    private val voiceKeyHandler = VoiceKeyEventHandler()

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        voiceKeyHandler.handle(this, event, "activity_key") || super.dispatchKeyEvent(event)

    /** Scale the approved 1920×1080 design to this screen before any view is built. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.voicegeely.app.ui.ResponsiveContext.wrap(newBase))
    }

    private lateinit var statusText: TextView
    private lateinit var recognizedText: TextView
    private lateinit var licenseText: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var licenseManager: LicenseManager
    private lateinit var mediaSettings: MediaAppSettings
    private lateinit var voicePreferences: UserVoicePreferences

    private val navButtons = linkedMapOf<MainDestination, View>()
    private val assistantRuntime = AssistantRuntimeStore()
    private var assistantBinding: AssistantScreenBinding? = null
    private var homeBinding: HomeScreenBinding? = null
    private var currentDestination = MainDestination.HOME
    private var waitingForOverlayPermission = false
    private var serviceStartRequested = false
    @Volatile private var updateRunning = false
    private var autoUpdateCheckRunning = false

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoiceAssistantService.ACTION_TTS_TEST_RESULT -> {
                    val ok = intent.getBooleanExtra(VoiceAssistantService.EXTRA_TTS_TEST_OK, false)
                    com.voicegeely.app.ui.ThtUi.showDialog(
                        this@MainActivity,
                        if (ok) com.tht.voice.R.drawable.ic_ui_check_circle else com.tht.voice.R.drawable.ic_ui_info,
                        if (ok) "Giọng trợ lý hoạt động" else "Giọng trợ lý chưa đọc được",
                        intent.getStringExtra(VoiceAssistantService.EXTRA_TTS_TEST_DETAIL).orEmpty() +
                            if (ok) "" else "\n\nNếu chưa có gói giọng đọc: Cài đặt → Gói giọng đọc → Tải về hoặc Cài từ tệp. Có thể cài giọng tiếng Việt cho trình đọc của Android để dùng dự phòng."
                    )
                }
                VoiceAssistantService.ACTION_SERVICE_READY -> {
                    assistantRuntime.onServiceReady()
                    updateStatus("Voice core đã sẵn sàng")
                    renderRuntimeScreens()
                }
                VoiceAssistantService.ACTION_ENGINE_STATUS -> {
                    val message = intent.getStringExtra(VoiceAssistantService.EXTRA_STATUS_MESSAGE).orEmpty()
                    assistantRuntime.onEngineStatus(
                        message = message,
                        asrReady = intent.getBooleanExtra(VoiceAssistantService.EXTRA_SHERPA_READY, false),
                        kwsReady = intent.getBooleanExtra(VoiceAssistantService.EXTRA_WAKE_READY, false),
                        ttsReady = intent.getBooleanExtra(VoiceAssistantService.EXTRA_TTS_READY, false),
                        ttsDetail = intent.getStringExtra(VoiceAssistantService.EXTRA_TTS_DETAIL)
                    )
                    updateStatus(message)
                    renderRuntimeScreens()
                }
                VoiceAssistantService.ACTION_RECOGNITION_TEXT -> {
                    val text = intent.getStringExtra(VoiceAssistantService.EXTRA_RECOGNIZED_TEXT).orEmpty().trim()
                    if (text.isNotBlank()) {
                        val isFinal = intent.getBooleanExtra(VoiceAssistantService.EXTRA_RECOGNITION_FINAL, false)
                        assistantRuntime.onRecognition(text, isFinal)
                        recognizedText.text = if (isFinal) "Đã nghe: $text" else "Đang nghe: $text"
                        renderRuntimeScreens()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        licenseManager = LicenseManager(this)
        mediaSettings = MediaAppSettings(this)
        voicePreferences = UserVoicePreferences(this)
        assistantRuntime.initialize(VoiceAssistantService.isServiceReady(), VoiceAssistantService.engineReadiness())
        setContentView(buildNavigationShell())

        VoiceServiceEventBus.registerReceiver(
            serviceReceiver,
            IntentFilter().apply {
                addAction(VoiceAssistantService.ACTION_SERVICE_READY)
                addAction(VoiceAssistantService.ACTION_ENGINE_STATUS)
                addAction(VoiceAssistantService.ACTION_RECOGNITION_TEXT)
                addAction(VoiceAssistantService.ACTION_TTS_TEST_RESULT)
            }
        )
        refreshLicenseStatus()
        if (licenseManager.isLicensed()) ensurePermissionsAndStart() else lockToActivation("create")
    }

    private var layoutSpec: com.voicegeely.app.ui.ResponsiveLayout.LayoutSpec? = null

    private fun buildNavigationShell(): View {
        val layout = com.voicegeely.app.ui.ResponsiveContext.spec(this)
        layoutSpec = layout
        val ui = com.voicegeely.app.ui.ThtUi
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.BG)
        }

        // Top bar: logo, tagline, live status, license, time.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (layout.railIconsOnly) 16 else 44), 0, dp(28), 0)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 18, 32), Color.rgb(5, 12, 22))
            )
        }
        outer.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        outer.addView(View(this).apply { setBackgroundColor(Color.rgb(18, 36, 58)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        topBar.addView(ui.icon(this, com.tht.voice.R.drawable.ic_ui_wave, ui.ACCENT, 34))
        topBar.addView(ui.text(this, "THT Voice", 28f, Color.WHITE, bold = true, maxLines = 1), ui.wrap().apply { marginStart = dp(14) })
        if (!layout.railIconsOnly) {
            topBar.addView(View(this).apply { setBackgroundColor(Color.rgb(40, 64, 92)) }, LinearLayout.LayoutParams(dp(1), dp(30)).apply {
                marginStart = dp(22); marginEnd = dp(22)
            })
            topBar.addView(ui.text(this, "Trợ lý giọng nói tiếng Việt cho xe của bạn", 17f, Color.rgb(200, 214, 230), maxLines = 1))
        }
        statusText = ui.text(this, "Đang khởi tạo...", 14f, ui.TEXT_2, maxLines = 1, gravity = Gravity.END)
        topBar.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(20); marginEnd = dp(20) })
        recognizedText = TextView(this)
        licenseText = ui.text(this, "", 14f, ui.OK, maxLines = 1).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = ui.panel(this@MainActivity, Color.argb(40, 46, 214, 128), Color.argb(110, 46, 214, 128), 16f)
            isClickable = true
            setOnClickListener { showLicenseDialog() }
        }
        topBar.addView(licenseText)
        topBar.addView(View(this).apply { setBackgroundColor(Color.rgb(40, 64, 92)) }, LinearLayout.LayoutParams(dp(1), dp(30)).apply {
            marginStart = dp(20); marginEnd = dp(20)
        })
        topBar.addView(android.widget.TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 20f
            setTextColor(Color.WHITE)
        })

        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        outer.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Navigation rail with the night-road artwork at the bottom.
        val railFrame = FrameLayout(this).apply { setBackgroundColor(ui.RAIL) }
        root.addView(railFrame, LinearLayout.LayoutParams(dp(layout.railWidthDp), ViewGroup.LayoutParams.MATCH_PARENT))
        railFrame.addView(android.widget.ImageView(this).apply {
            setImageResource(com.tht.voice.R.drawable.tht_rail_road)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            alpha = 0.85f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380), Gravity.BOTTOM))
        railFrame.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ui.RAIL, Color.argb(0, 6, 13, 24))
            )
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140), Gravity.BOTTOM).apply { bottomMargin = dp(240) })
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = if (layout.railIconsOnly) 8 else 14
            setPadding(dp(pad), dp(18), dp(pad), dp(18))
        }
        railFrame.addView(rail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        MainDestination.ordered.forEach { destination ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (layout.railIconsOnly) Gravity.CENTER else Gravity.CENTER_VERTICAL
                setPadding(dp(if (layout.railIconsOnly) 0 else 22), 0, dp(10), 0)
                contentDescription = destination.title
                isClickable = true
                setOnClickListener { showDestination(destination) }
                addView(ui.icon(this@MainActivity, railIcon(destination), ui.TEXT_2, 28))
                if (!layout.railIconsOnly) {
                    addView(ui.text(this@MainActivity, destination.title, 18f, ui.TEXT_2, maxLines = 1), ui.wrap().apply { marginStart = dp(16) })
                }
            }
            navButtons[destination] = item
            rail.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(layout.railButtonHeightDp)).apply { topMargin = dp(8) })
        }

        contentHost = FrameLayout(this).apply { setPadding(dp(20), dp(16), dp(20), dp(12)) }
        root.addView(contentHost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        showDestination(MainDestination.HOME)
        return outer
    }

    private fun railIcon(destination: MainDestination): Int = when (destination) {
        MainDestination.HOME -> com.tht.voice.R.drawable.ic_ui_home
        MainDestination.ASSISTANT -> com.tht.voice.R.drawable.ic_ui_mic
        MainDestination.APPS -> com.tht.voice.R.drawable.ic_ui_apps
        MainDestination.SETTINGS -> com.tht.voice.R.drawable.ic_ui_gear
        MainDestination.SUPPORT -> com.tht.voice.R.drawable.ic_ui_heart
        MainDestination.ABOUT -> com.tht.voice.R.drawable.ic_ui_info
    }

    private fun showDestination(destination: MainDestination) {
        val ui = com.voicegeely.app.ui.ThtUi
        currentDestination = destination
        if (destination != MainDestination.ASSISTANT) assistantBinding = null
        if (destination != MainDestination.HOME) homeBinding = null
        navButtons.forEach { (item, view) ->
            val selected = item == destination
            val row = view as LinearLayout
            row.background = if (selected) ui.gradient(this, intArrayOf(Color.rgb(10, 60, 130), Color.rgb(6, 30, 70)), 14f, ui.ACCENT_2) else null
            val tint = if (selected) ui.ACCENT else ui.TEXT_2
            (row.getChildAt(0) as? android.widget.ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(tint)
            (row.getChildAt(1) as? TextView)?.apply {
                setTextColor(if (selected) Color.WHITE else ui.TEXT_2)
                typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            }
        }

        contentHost.removeAllViews()
        val content = when (destination) {
            MainDestination.HOME -> buildHomeScreen()
            MainDestination.ASSISTANT -> buildAssistantScreen()
            MainDestination.APPS -> buildAppsScreen()
            MainDestination.SETTINGS -> buildSettingsScreen()
            MainDestination.SUPPORT -> buildSupportScreen()
            MainDestination.ABOUT -> buildAboutScreen()
        }
        // Every screen scrolls vertically when it is taller than the display (small head units, phones).
        val hosted = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentHost.addView(hosted, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildHomeScreen(): View {
        val binding = HomeScreenView.build(
            context = this,
            runtime = assistantRuntime.state,
            voicePreferences = voicePreferences,
            mediaSettings = mediaSettings,
            onAssistant = { showDestination(MainDestination.ASSISTANT) },
            onApps = { showDestination(MainDestination.APPS) },
            onSettings = { showDestination(MainDestination.SETTINGS) },
            onSupport = { showDestination(MainDestination.SUPPORT) }
        )
        homeBinding = binding
        return binding.root
    }

    private fun buildAssistantScreen(): View {
        val binding = AssistantScreenView.build(
            context = this,
            initial = assistantRuntime.state,
            voicePreferences = voicePreferences,
            onListen = { triggerRecognition() },
            onSettingsChanged = { sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED) }
        )
        assistantBinding = binding
        return binding.root
    }

    private fun renderRuntimeScreens() {
        assistantBinding?.render(assistantRuntime.state)
        homeBinding?.render(assistantRuntime.state, voicePreferences, mediaSettings)
    }

    private fun buildAppsScreen(): View = AppsScreenView.build(
        context = this,
        mediaSettings = mediaSettings,
        voicePreferences = voicePreferences,
        onVoiceTest = { voiceHint ->
            Toast.makeText(this, "Hãy nói: “$voiceHint”", Toast.LENGTH_LONG).show()
            triggerRecognition()
        },
        onSaved = { sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED) }
    )

    private fun buildSettingsScreen(): View = SettingsScreenView.build(
        context = this,
        voicePreferences = voicePreferences,
        mediaSettings = mediaSettings,
        onSaved = {
            sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED)
            Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show()
            showDestination(MainDestination.SETTINGS)
        },
        onRestored = {
            sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED)
            Toast.makeText(this, "Đã khôi phục mặc định", Toast.LENGTH_SHORT).show()
            showDestination(MainDestination.SETTINGS)
        },
        onPreviewVoice = { sendServiceAction(VoiceAssistantService.ACTION_TEST_VOICE, it.name) },
        onVoiceLicenseRequired = {
            startActivity(Intent(this, ActivationActivity::class.java)
                .putExtra(ActivationActivity.EXTRA_DOWNLOAD_LICENSE, true))
        },
        extraVoiceRows = settingsExtraRows()
    )

    /** Extra rows in "Thiết lập giọng nói": the downloadable voice pack (when not bundled). */
    private fun settingsExtraRows(): List<View> = listOfNotNull(
        if (!com.voicegeely.app.voice.BackgroundLaunchPolicy.isXiaomi()) null else com.voicegeely.app.ui.ThtUi.settingRow(
            this,
            com.tht.voice.R.drawable.ic_ui_apps,
            "Mở ứng dụng khi đang dùng app khác (Xiaomi)",
            when (com.voicegeely.app.voice.BackgroundLaunchPolicy.miuiBackgroundStartAllowed(this)) {
                true -> "Đã cho phép. Trợ lý mở được ứng dụng khi đang ở app khác."
                false -> "Đang bị chặn: bật \"Hiển thị cửa sổ bật lên khi chạy trong nền\" cho THT Voice."
                null -> "Nếu lệnh mở app không chạy khi đang ở app khác, bật \"Hiển thị cửa sổ bật lên khi chạy trong nền\"."
            },
            com.voicegeely.app.ui.ThtUi.smallButton(this, com.tht.voice.R.drawable.ic_ui_gear, "Mở quyền",
                com.voicegeely.app.ui.ThtUi.SmallStyle.OUTLINE) {
                if (!com.voicegeely.app.voice.BackgroundLaunchPolicy.openPermissionSettings(this)) {
                    Toast.makeText(this, "Không mở được trang quyền của Xiaomi", Toast.LENGTH_SHORT).show()
                }
            }.apply { minHeight = dp(44); setPadding(dp(16), dp(10), dp(16), dp(10)) }
        ),
        com.voicegeely.app.ui.VoicePackRow.build(
            context = this,
            onPickFile = { runCatching { voicePackPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } },
            onInstalled = { sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED) }
        )
    )

    private val voicePackPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            com.voicegeely.app.tts.TtsPackManager.installFromUri(this, uri) {
                sendServiceAction(VoiceAssistantService.ACTION_SETTINGS_CHANGED)
            }
        }
    }

    private fun buildSupportScreen(): View = SupportScreenView.build(
        context = this,
        onCopy = { label, value ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("THT Voice $label", value))
            Toast.makeText(this, "Đã sao chép ${label.lowercase()}", Toast.LENGTH_SHORT).show()
        }
    )

    private fun buildAboutScreen(): View = AboutScreenView.build(
        context = this,
        actions = AboutScreenView.Actions(
            onCheckUpdate = { checkForUpdates() },
            onChangelog = {
                com.voicegeely.app.ui.ThtUi.showDialog(this, com.tht.voice.R.drawable.ic_ui_list, "Nhật ký phiên bản", MainUiTexts.CHANGELOG_TEXT)
            },
            onManual = {
                com.voicegeely.app.ui.ThtUi.showDialog(this, com.tht.voice.R.drawable.ic_ui_book, "Hướng dẫn sử dụng", MainUiTexts.MANUAL_TEXT)
            },
            onProject = {
                com.voicegeely.app.ui.ThtUi.showSupportContact(this) {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_FACEBOOK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        Toast.makeText(this, "Không mở được Facebook", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onAssistant = { showDestination(MainDestination.ASSISTANT) },
            onApps = { showDestination(MainDestination.APPS) },
            onSettings = { showDestination(MainDestination.SETTINGS) },
            onFeature = { title, body ->
                com.voicegeely.app.ui.ThtUi.showDialog(this, com.tht.voice.R.drawable.ic_ui_info, title, body)
            }
        )
    )

    private fun triggerRecognition() {
        if (!licenseManager.isLicensed()) {
            lockToActivation("action")
            return
        }
        assistantRuntime.markManualListening()
        renderRuntimeScreens()
        recognizedText.text = "Đang nghe: …"
        sendServiceAction(VoiceAssistantService.ACTION_TRIGGER_RECOGNITION)
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = roundedPanel(COLOR_ACTION, COLOR_ACCENT_DARK, 14f)
        setOnClickListener { onClick() }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }

    private fun roundedPanel(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radiusDp * resources.displayMetrics.density
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun checkForUpdates() {
        if (!licenseManager.isLicensed()) {
            lockToActivation("action")
            return
        }
        if (updateRunning) {
            Toast.makeText(this, "Đang xử lý cập nhật", Toast.LENGTH_SHORT).show()
            return
        }
        updateRunning = true
        updateStatus("Đang kiểm tra cập nhật...")
        Thread {
            val vehicle = AndroidCarVehiclePropertyAccess.create(this)
            val result = try {
                SafeAppUpdateManager(
                    context = this,
                    licenseManager = licenseManager,
                    safetyGate = VehicleSafetyGate(vehicle)
                ).checkDownloadAndInstall()
            } finally {
                runCatching { vehicle?.disconnect() }
            }
            runOnUiThread {
                updateRunning = false
                updateStatus(result.detail)
                when (result.status) {
                    AppUpdateFlowStatus.WAITING_FOR_PARK -> com.voicegeely.app.ui.ThtUi.showDialog(
                        this, com.tht.voice.R.drawable.ic_ui_update, "Cập nhật", result.detail
                    )
                    AppUpdateFlowStatus.INSTALL_PERMISSION_REQUIRED -> showUnknownSourcesDialog()
                    AppUpdateFlowStatus.VERIFICATION_FAILED,
                    AppUpdateFlowStatus.DOWNLOAD_FAILED,
                    AppUpdateFlowStatus.CHECK_FAILED,
                    AppUpdateFlowStatus.INSTALL_LAUNCH_FAILED -> com.voicegeely.app.ui.ThtUi.showDialog(
                        this, com.tht.voice.R.drawable.ic_ui_update, "Cập nhật", result.detail
                    )
                    else -> com.voicegeely.app.ui.ThtUi.showDialog(this, com.tht.voice.R.drawable.ic_ui_update, "Cập nhật", result.detail)
                }
            }
        }.start()
    }

    private fun showUnknownSourcesDialog() {
        com.voicegeely.app.ui.ThtUi.showDialog(
            this,
            com.tht.voice.R.drawable.ic_ui_update,
            "Quyền cài đặt cập nhật",
            "APK đã được xác minh nhưng Android chưa cho phép THT Voice mở trình cài đặt. Hãy bật quyền cài ứng dụng không rõ nguồn gốc cho THT Voice.",
            primaryLabel = "Mở cài đặt",
            onPrimary = {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }.onFailure {
                    Toast.makeText(this, "Không mở được trang cấp quyền", Toast.LENGTH_LONG).show()
                }
            },
            secondaryLabel = "Để sau"
        )
    }

    private fun ensurePermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        requestOverlayIfNeededThenStart()
    }

    private fun requestOverlayIfNeededThenStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false
            startVoiceService()
            return
        }
        if (waitingForOverlayPermission) return
        waitingForOverlayPermission = true
        Toast.makeText(this, "Hãy bật quyền Hiển thị trên ứng dụng khác cho THT Voice", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            waitingForOverlayPermission = false
            startVoiceService()
        }
    }

    private fun startVoiceService() {
        if (serviceStartRequested && !VoiceAssistantService.isServiceReady()) return
        serviceStartRequested = true
        val intent = Intent(this, VoiceAssistantService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            updateStatus(if (VoiceAssistantService.isServiceReady()) "Voice core đã sẵn sàng" else "Đang khởi động Voice core...")
        } catch (t: Throwable) {
            serviceStartRequested = false
            updateStatus("Không khởi động được Voice core: ${t.message.orEmpty()}")
        }
    }

    private fun sendServiceAction(action: String, previewVoice: String? = null) {
        val intent = Intent(this, VoiceAssistantService::class.java).apply {
            this.action = action
            if (previewVoice != null) putExtra(VoiceAssistantService.EXTRA_TTS_TEST_VOICE, previewVoice)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (t: Throwable) {
            updateStatus("Không gửi được lệnh tới Voice core: ${t.message.orEmpty()}")
        }
    }

    /** License info for an activated device: status, device code and a server re-check. */
    private fun showLicenseDialog() {
        if (!licenseManager.isLicensed()) {
            lockToActivation("license_dialog")
            return
        }
        com.voicegeely.app.ui.ThtUi.showDialog(
            this,
            com.tht.voice.R.drawable.ic_ui_shield,
            "Bản quyền THT Voice",
            "${licenseManager.statusText()}\n\nMã thiết bị\n${licenseManager.formattedRequestCode()}",
            primaryLabel = "Đóng",
            secondaryLabel = "Kiểm tra lại",
            onSecondary = {
                Toast.makeText(this, "Đang kiểm tra với máy chủ...", Toast.LENGTH_SHORT).show()
                Thread {
                    val remote = licenseManager.refreshRemoteStatus()
                    runOnUiThread {
                        Toast.makeText(this, remote.message, Toast.LENGTH_LONG).show()
                        refreshLicenseStatus()
                        if (!licenseManager.isLicensed()) lockToActivation("remote_check")
                    }
                }.start()
            }
        )
    }

    private fun refreshLicenseStatus() {
        licenseText.text = licenseManager.statusText()
    }

    private fun updateStatus(message: String) {
        statusText.text = message.ifBlank { "THT Voice" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestOverlayIfNeededThenStart()
            else updateStatus("Cần quyền micrô để nhận lệnh giọng nói")
        }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) startVoiceService()
        }
        if (!licenseManager.isLicensed()) {
            lockToActivation("resume")
            return
        }
        assistantRuntime.initialize(VoiceAssistantService.isServiceReady(), VoiceAssistantService.engineReadiness())
        renderRuntimeScreens()
        refreshLicenseStatus()
        maybeAutoCheckUpdate()
        maybeShowCustomerNotice()
    }

    private var noticeCheckRunning = false

    /** "Thông báo cho khách": once, inside this screen only, never while the car is moving. */
    private fun maybeShowCustomerNotice() {
        if (noticeCheckRunning) return
        val store = com.voicegeely.app.security.CustomerNoticeStore(this)
        val notice = store.load() ?: return
        if (notice.id == store.seenId) return
        noticeCheckRunning = true
        Thread {
            val vehicle = runCatching { AndroidCarVehiclePropertyAccess.create(this) }.getOrNull()
            val motion = try {
                VehicleSafetyGate(vehicle).readMotionState()
            } catch (_: Throwable) {
                com.voicegeely.app.voice.safety.VehicleMotionState(null, null)
            } finally {
                runCatching { vehicle?.disconnect() }
            }
            runOnUiThread {
                noticeCheckRunning = false
                if (isFinishing || autoUpdateCheckRunning) return@runOnUiThread
                if (!com.voicegeely.app.security.CustomerNoticePolicy.shouldShow(notice, store.seenId, System.currentTimeMillis(), motion)) return@runOnUiThread
                com.voicegeely.app.ui.ThtUi.showDialog(
                    this, com.tht.voice.R.drawable.ic_ui_info, "Thông báo", notice.text,
                    primaryLabel = "Đã xem",
                    onPrimary = { store.markSeen(notice.id) }
                ).setOnCancelListener { store.markSeen(notice.id) }
            }
        }.apply { name = "THTVoice-Notice"; isDaemon = true }.start()
    }

    /**
     * Automatic update check (owner request 01/10/2026): on open, at most every 6 hours, only
     * while parked. A required (forced) update shows a dialog that cannot be dismissed.
     */
    private fun maybeAutoCheckUpdate() {
        if (updateRunning || autoUpdateCheckRunning) return
        val store = com.voicegeely.app.update.UpdatePromptStore(this)
        val current = com.tht.voice.BuildConfig.VERSION_CODE.toLong()
        if (com.voicegeely.app.update.UpdatePromptPolicy.isRequired(store.requiredVersionCode, current)) {
            showUpdatePrompt(store.requiredVersionName, "", force = true)
            return
        }
        if (!com.voicegeely.app.update.UpdatePromptPolicy.shouldAutoCheck(System.currentTimeMillis(), store.lastAutoCheckAt)) return
        autoUpdateCheckRunning = true
        Thread {
            val vehicle = AndroidCarVehiclePropertyAccess.create(this)
            val check = try {
                SafeAppUpdateManager(this, licenseManager, VehicleSafetyGate(vehicle)).checkOnly()
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { vehicle?.disconnect() }
            }
            runOnUiThread {
                autoUpdateCheckRunning = false
                if (check == null || !check.ok) return@runOnUiThread // not parked or offline: try next time
                store.lastAutoCheckAt = System.currentTimeMillis()
                val meta = check.metadata
                if (!check.updateAvailable || meta == null) return@runOnUiThread
                val name = meta.versionName.orEmpty()
                if (check.forceUpdate) {
                    store.requiredVersionCode = meta.versionCode
                    store.requiredVersionName = name
                }
                if (!isFinishing) showUpdatePrompt(name, check.changelog, force = check.forceUpdate)
            }
        }.start()
    }

    private fun showUpdatePrompt(versionName: String, changelog: String, force: Boolean) {
        val title = if (force) "Cần cập nhật THT Voice" else "Có bản THT Voice mới"
        val body = buildString {
            append(if (versionName.isNotBlank()) "Bản $versionName đã sẵn sàng." else "Đã có bản mới.")
            if (changelog.isNotBlank()) append("\n\n").append(changelog.trim())
            if (force) append("\n\nĐây là bản bắt buộc. Khi xe đỗ, trợ lý giọng nói tạm dừng cho tới khi cập nhật xong.")
        }
        val dialog = com.voicegeely.app.ui.ThtUi.showDialog(
            this, com.tht.voice.R.drawable.ic_ui_update, title, body,
            primaryLabel = "Cập nhật ngay",
            onPrimary = { checkForUpdates() },
            secondaryLabel = if (force) null else "Để sau"
        )
        if (force) {
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    /**
     * The license is no longer valid (expired, blocked, wrong device, clock rollback): stop the
     * Voice core and return to the full-screen activation gate. There is no in-app dialog with
     * a "Đóng" button, so the main UI can never be used unlicensed.
     */
    private fun lockToActivation(reason: String) {
        android.util.Log.i("THTVoiceSecurity", "main_locked_to_activation reason=$reason gate=${licenseManager.gateState()}")
        runCatching { stopService(Intent(this, VoiceAssistantService::class.java)) }
        startActivity(Intent(this, ActivationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onDestroy() {
        voiceKeyHandler.clear()
        runCatching { VoiceServiceEventBus.unregisterReceiver(serviceReceiver) }
        assistantBinding = null
        homeBinding = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_MIC = 1001
        private const val SUPPORT_FACEBOOK_URL = "https://www.facebook.com/100004637940665"

        private val COLOR_BACKGROUND = Color.rgb(6, 13, 22)
        private val COLOR_RAIL = Color.rgb(5, 16, 27)
        private val COLOR_PANEL = Color.rgb(10, 24, 38)
        private val COLOR_BORDER = Color.rgb(25, 61, 82)
        private val COLOR_ACTION = Color.rgb(10, 55, 77)
        private val COLOR_NAV_SELECTED = Color.rgb(5, 42, 60)
        private val COLOR_ACCENT = Color.rgb(0, 226, 255)
        private val COLOR_ACCENT_DARK = Color.rgb(0, 116, 145)
        private val COLOR_ACCENT_SOFT = Color.rgb(141, 221, 238)
        private val COLOR_TEXT_PRIMARY = Color.rgb(224, 235, 244)
        private val COLOR_TEXT_SECONDARY = Color.rgb(145, 165, 183)
        private val COLOR_SUCCESS = Color.rgb(102, 230, 170)
    }
}

internal object MainUiTexts {
    const val CHANGELOG_TEXT = """Bản cập nhật mới

• Nhiều lệnh trong một câu: "tắt điều hòa rồi mở nhạc"; kịch bản: một câu bạn đặt chạy nhiều lệnh
• Nói tiếp: "tăng nữa", "giảm nữa", "về như cũ" cho quạt, nhiệt độ, âm lượng
• Âm lượng theo số: "âm lượng 15", "âm lượng 50 phần trăm", "tăng âm lượng 3 nấc"
• Tên gọi riêng: dạy trợ lý câu nói của bạn; sao chép / gửi danh sách trong Cài đặt
• "Mở kính" không nói kính nào → trợ lý hỏi lại; chưa hiểu câu → gợi ý lệnh gần nhất
• "Tình trạng xe": xăng, quãng đường còn lại và kính trong một câu
• Hiểu thêm cách nói Bắc - Trung - Nam và các từ hay bị nghe nhầm
• Giọng đọc: hết tiếng dư cuối câu, đọc từ kỹ thuật bằng tiếng Việt, phản hồi nhanh hơn
• Nghe tốt hơn: ngừng sau "rồi" vẫn nghe tiếp; không nói gì thì dừng sau 4 giây
• Tự kết nối lại xe sau khi xe ngủ dậy; Kiểm tra nút voice trong Cài đặt

THT Voice v1.0

• Nhận dạng giọng nói offline (ASR 65M) — không cần mạng
• Từ đánh thức: Hey THT, Hey Ge-ly, Hello Ge-ly (chọn trong Cài đặt); hoặc nút Voice trên xe
• Giọng đọc offline Ngọc Huyền / Diễm Trinh
• Điều hòa: nhiệt độ, quạt, hướng gió, lấy gió, sấy kính trước/sau và gương, điều hòa tiết kiệm, tự động đảo gió
• Xe: kính, cửa sổ trời, đèn, sưởi ghế và vô lăng, cốp sau
• Hỏi trạng thái: xăng, quãng đường, tốc độ, số, nhiệt độ nước làm mát, ắc quy, áp suất lốp, bảo dưỡng
• Mở mọi ứng dụng đã cài theo tên; nhạc, video, dẫn đường mặc định
• Màn hình đen, về màn hình chính, hỏi giờ / ngày
• Chỉ báo "Đã…" khi xe xác nhận trạng thái; lệnh nhạy cảm chỉ chạy khi xe đỗ
• Giao diện tự co giãn theo màn hình"""

    const val MANUAL_TEXT = """1. Kích hoạt ứng dụng hợp lệ, cấp quyền micro và hiển thị trên ứng dụng khác.
2. Gọi trợ lý: nói từ đánh thức đã chọn trong Cài đặt (mặc định Hey Ge-ly), bấm nút Voice trên xe, hoặc bấm mic ở màn Trợ lý.
3. Nói lệnh tự nhiên, ví dụ:
   • "Đặt 22 độ", "22 rưỡi", "Gió to lên", "Sấy kính sau"
   • "Hạ kính bên lái", "Mở tất cả kính", "Mở cửa sổ trời", "Bật sưởi ghế lái"
   • "Xăng còn bao nhiêu", "Tình trạng xe", "Mấy giờ rồi"
   • "Mở Zalo", "Bật nhạc", "Âm lượng 15", "Chỉ đường tới sân bay"
   • Nhiều lệnh một lần: "Tắt điều hòa rồi mở nhạc"
4. Nói tiếp ngay sau lệnh: "tăng nữa", "giảm nữa", "về như cũ".
5. Nói "mở kính" mà không nói kính nào: trợ lý hỏi lại, bạn chỉ cần trả lời "lái", "sau", "tất cả"…
6. Tên gọi riêng (Cài đặt): dạy trợ lý câu nói của bạn, ví dụ "Khi tôi nói đi làm thì bật điều hòa 24 độ rồi mở nhạc".
7. Một số lệnh (cốp, đèn, màn hình đen…) chỉ chạy khi xe ở số P và đứng yên.
8. Trợ lý chỉ nói "Đã…" khi đọc lại được trạng thái từ xe; nếu chưa xác nhận sẽ báo rõ."""
}
