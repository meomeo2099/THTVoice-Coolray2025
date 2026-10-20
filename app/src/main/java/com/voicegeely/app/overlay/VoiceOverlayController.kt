package com.voicegeely.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.voicegeely.app.settings.OverlayPosition
import com.voicegeely.app.settings.UserVoicePreferences
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/** Three-zone Voice popup (mic + status · live text + recognised sentence · action + result), per the owner mockup 29/09/2026. */
class VoiceOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val stateMachine = VoiceOverlayStateMachine()
    private val preferences = UserVoicePreferences(appContext)
    private val generation = AtomicLong(0L)

    private var rootView: LinearLayout? = null
    private var statusView: TextView? = null
    private var transcriptHeadlineView: TextView? = null
    private var transcriptView: TextView? = null
    private var resultTitleView: TextView? = null
    private var resultView: TextView? = null
    private var resultSpinner: View? = null
    private var resultIcon: ImageView? = null
    private var waveIcon: ImageView? = null
    private var waveAnimator: ObjectAnimator? = null
    private var transcriptCluster: View? = null
    private var resultCluster: View? = null
    private var minimizeView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    @Volatile private var minimized = false
    @Volatile private var overlayPermissionWarningLogged = false
    @Volatile private var lastLoggedPhase: VoiceOverlayPhase? = null

    fun showListening() {
        minimized = false
        render(stateMachine.listening())
    }

    fun updateRecognition(text: String, isFinal: Boolean) {
        if (text.isBlank()) return
        render(if (isFinal) stateMachine.processing(text) else stateMachine.partial(text))
    }

    fun showProcessing(text: String = "") {
        render(stateMachine.processing(text))
    }

    fun showResult(message: String, autoHideMs: Long? = null) {
        val token = render(stateMachine.result(message))
        scheduleHide(token, autoHideMs ?: preferences.overlayAutoHideMs)
    }

    fun showError(message: String, autoHideMs: Long? = null) {
        val token = render(stateMachine.error(com.voicegeely.app.ui.PublicText.scrub(message)))
        scheduleHide(token, autoHideMs ?: preferences.overlayAutoHideMs)
    }

    fun hide() {
        generation.incrementAndGet()
        stateMachine.hide()
        logPhase(VoiceOverlayPhase.HIDDEN)
        mainHandler.post { removeView("explicit_hide") }
    }

    fun close() {
        Log.i(TAG, "close_requested")
        hide()
    }

    private fun render(state: VoiceOverlayState): Long {
        val token = generation.incrementAndGet()
        logPhase(state.phase)
        mainHandler.post {
            if (generation.get() != token) return@post
            if (state.phase == VoiceOverlayPhase.HIDDEN || !preferences.overlayEnabled) {
                if (!preferences.overlayEnabled) Log.d(TAG, "render_skipped reason=DISABLED phase=${state.phase}")
                removeView("hidden_or_disabled")
                return@post
            }
            if (!canDrawOverlay()) return@post
            ensureView()
            if (rootView == null) return@post
            val phaseColor = phaseColor(state.phase)
            statusView?.apply {
                text = state.title
                setTextColor(phaseColor)
                background = pill(Color.argb(55, Color.red(phaseColor), Color.green(phaseColor), Color.blue(phaseColor)),
                    Color.argb(140, Color.red(phaseColor), Color.green(phaseColor), Color.blue(phaseColor)), 13)
                setCompoundDrawablesRelativeWithIntrinsicBounds(dot(phaseColor), null, null, null)
            }
            transcriptHeadlineView?.text = when (state.phase) {
                VoiceOverlayPhase.LISTENING -> if (state.transcript.isBlank()) "Hãy nói lệnh..." else "${state.transcript}..."
                else -> state.transcript.ifBlank { "Đã nhận lệnh" }
            }
            transcriptView?.apply {
                val finalText = if (state.phase == VoiceOverlayPhase.LISTENING) "" else state.transcript
                text = if (finalText.isBlank()) "" else "“${finalText.replaceFirstChar { it.uppercase() }}”"
                visibility = if (finalText.isBlank()) View.GONE else View.VISIBLE
            }
            val (title, subtitle) = when (state.phase) {
                VoiceOverlayPhase.LISTENING -> "Đang chờ câu lệnh" to "Hãy nói lệnh của bạn"
                VoiceOverlayPhase.PROCESSING -> state.result.ifBlank { "Đang xử lý" } to "Vui lòng chờ trong giây lát..."
                VoiceOverlayPhase.RESULT -> state.result.ifBlank { "Hoàn tất" } to "THT Voice"
                VoiceOverlayPhase.ERROR -> "Không thực hiện được" to state.result.ifBlank { "Vui lòng thử lại" }
                VoiceOverlayPhase.HIDDEN -> "" to ""
            }
            resultTitleView?.text = title
            resultView?.apply {
                text = subtitle
                setTextColor(if (state.phase == VoiceOverlayPhase.ERROR) COLOR_ERROR else COLOR_TEXT_SECONDARY)
            }
            resultSpinner?.visibility = if (state.phase == VoiceOverlayPhase.PROCESSING) View.VISIBLE else View.GONE
            resultIcon?.apply {
                visibility = if (state.phase == VoiceOverlayPhase.PROCESSING) View.GONE else View.VISIBLE
                setImageResource(when (state.phase) {
                    VoiceOverlayPhase.RESULT -> com.tht.voice.R.drawable.ic_ui_check_circle
                    VoiceOverlayPhase.ERROR -> com.tht.voice.R.drawable.ic_ui_info
                    else -> com.tht.voice.R.drawable.ic_ui_wave
                })
                imageTintList = ColorStateList.valueOf(phaseColor)
            }
            if (state.phase == VoiceOverlayPhase.LISTENING) startWave() else stopWave()
            applyMinimizedState()
        }
        return token
    }

    private fun logPhase(phase: VoiceOverlayPhase) {
        if (lastLoggedPhase == phase) return
        lastLoggedPhase = phase
        Log.i(TAG, "phase=$phase attached=${rootView != null} minimized=$minimized")
    }

    private fun scheduleHide(token: Long, delayMs: Long) {
        if (delayMs <= 0L) {
            Log.d(TAG, "auto_hide_disabled delayMs=$delayMs")
            return
        }
        Log.d(TAG, "auto_hide_scheduled delayMs=$delayMs")
        mainHandler.postDelayed({
            if (generation.compareAndSet(token, token + 1L)) {
                stateMachine.hide()
                logPhase(VoiceOverlayPhase.HIDDEN)
                removeView("auto_hide")
            }
        }, delayMs)
    }

    private fun canDrawOverlay(): Boolean {
        val allowed = runCatching { Settings.canDrawOverlays(appContext) }
            .onFailure { Log.w(TAG, "permission_check_failed type=${it.javaClass.simpleName}") }
            .getOrDefault(false)
        if (!allowed) {
            if (!overlayPermissionWarningLogged) {
                overlayPermissionWarningLogged = true
                Log.w(TAG, "overlay_blocked reason=PERMISSION_DENIED")
            }
        } else if (overlayPermissionWarningLogged) {
            overlayPermissionWarningLogged = false
            Log.i(TAG, "overlay_permission_restored")
        }
        return allowed
    }

    private fun ensureView() {
        if (rootView != null) return

        // Left: glowing mic + name + status chip.
        val mic = FrameLayout(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(Color.rgb(20, 90, 180), Color.rgb(4, 24, 56))
                gradientRadius = dp(32).toFloat()
                setStroke(dp(3), COLOR_ACCENT)
            }
            addView(ImageView(appContext).apply {
                setImageResource(com.tht.voice.R.drawable.ic_ui_mic)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            elevation = dp(6).toFloat()
        }
        val status = textView("Đang nghe", 13f, COLOR_OK, bold = false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(12), dp(4))
            compoundDrawablePadding = dp(7)
        }
        val brandColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textView("THT Voice", 18f, Color.WHITE, bold = true))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        val left = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(18), 0)
            addView(mic, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(16) })
            addView(brandColumn)
        }

        // Middle: live waveform + partial text, then the recognised sentence in a quote box.
        val wave = ImageView(appContext).apply {
            setImageResource(com.tht.voice.R.drawable.ic_ui_wave)
            imageTintList = ColorStateList.valueOf(COLOR_ACCENT)
        }
        val transcriptHeadline = textView("Hãy nói lệnh...", 15f, Color.WHITE, bold = false).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val transcript = textView("", 15f, Color.WHITE, bold = false).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = pill(Color.rgb(10, 30, 56), Color.rgb(40, 80, 130), 10)
        }
        val middle = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(wave, LinearLayout.LayoutParams(dp(40), dp(26)).apply { marginEnd = dp(12) })
                addView(transcriptHeadline, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(transcript, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        // Right: spinner / result icon + action title + subtitle.
        val spinner = ProgressBar(appContext).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(COLOR_ACCENT)
            visibility = View.GONE
        }
        val icon = ImageView(appContext)
        val iconBox = FrameLayout(appContext).apply {
            addView(spinner, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            addView(icon, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        }
        val resultTitle = textView("Đang chờ câu lệnh", 15f, Color.WHITE, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val result = textView("Hãy nói lệnh của bạn", 13f, COLOR_TEXT_SECONDARY, bold = false).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }
        val resultColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(12), 0)
            addView(iconBox, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(14) })
            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                addView(resultTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(result, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val minimizeIcon = com.tht.voice.R.drawable.ic_ui_minus
        val minimize = controlButton(minimizeIcon) {
            minimized = !minimized
            Log.i(TAG, "minimized=$minimized")
            applyMinimizedState()
        }
        val close = controlButton(com.tht.voice.R.drawable.ic_ui_close) { close() }
        val controls = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(minimize, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(10) })
            addView(close, LinearLayout.LayoutParams(dp(46), dp(46)))
        }

        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = panel()
            elevation = dp(12).toFloat()
            addView(left, LinearLayout.LayoutParams(dp(250), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(divider(), LinearLayout.LayoutParams(dp(1), dp(64)))
            addView(middle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
            addView(divider(), LinearLayout.LayoutParams(dp(1), dp(64)))
            addView(resultColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(controls, LinearLayout.LayoutParams(dp(108), LinearLayout.LayoutParams.MATCH_PARENT))
        }

        val params = createLayoutParams()
        attachDragHandler(root, params)
        try {
            windowManager.addView(root, params)
            rootView = root
            statusView = status
            transcriptHeadlineView = transcriptHeadline
            transcriptView = transcript
            resultTitleView = resultTitle
            resultView = result
            resultSpinner = spinner
            resultIcon = icon
            waveIcon = wave
            transcriptCluster = middle
            resultCluster = resultColumn
            minimizeView = minimize
            layoutParams = params
            Log.i(TAG, "attached width=${params.width} height=${params.height} x=${params.x} y=${params.y}")
        } catch (t: Throwable) {
            Log.e(TAG, "attach_failed type=${t.javaClass.simpleName} message=${t.message.orEmpty()}")
            clearViewReferences()
        }
    }

    private fun startWave() {
        val view = waveIcon ?: return
        if (waveAnimator?.isRunning == true) return
        waveAnimator = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.55f, 1.15f).apply {
            duration = 420
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopWave() {
        waveAnimator?.cancel()
        waveAnimator = null
        waveIcon?.scaleY = 1f
    }

    private fun dot(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(dp(9), dp(9))
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = appContext.resources.displayMetrics
        val width = min(dp(1000), metrics.widthPixels - dp(36)).coerceAtLeast(dp(320))
        val height = dp(104)
        val preferred = preferences.overlayCustomPosition
        val x: Int
        val y: Int
        if (preferred != null) {
            x = preferred.first.coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
            y = preferred.second.coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        } else {
            x = ((metrics.widthPixels - width) / 2).coerceAtLeast(0)
            y = when (preferences.overlayPosition) {
                OverlayPosition.TOP -> dp(64)
                OverlayPosition.CENTER -> ((metrics.heightPixels - height) / 2).coerceAtLeast(0)
                OverlayPosition.BOTTOM -> (metrics.heightPixels - height - dp(54)).coerceAtLeast(0)
            }
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun attachDragHandler(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            if (!preferences.overlayDragEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (kotlin.math.abs(dx) < dp(4) && kotlin.math.abs(dy) < dp(4)) return@setOnTouchListener true
                    moved = true
                    val metrics = appContext.resources.displayMetrics
                    params.x = (startX + dx).coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(view, params) }
                        .onFailure { Log.w(TAG, "drag_update_failed type=${it.javaClass.simpleName}") }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        preferences.saveOverlayCustomPosition(params.x, params.y)
                        Log.i(TAG, "drag_saved x=${params.x} y=${params.y}")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyMinimizedState() {
        val params = layoutParams ?: return
        val view = rootView ?: return
        transcriptCluster?.visibility = if (minimized) View.GONE else View.VISIBLE
        resultCluster?.visibility = if (minimized) View.GONE else View.VISIBLE
        minimizeView?.setImageResource(if (minimized) com.tht.voice.R.drawable.ic_ui_arrow else com.tht.voice.R.drawable.ic_ui_minus)
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val targetWidth = if (minimized) dp(400) else min(dp(1000), screenWidth - dp(36)).coerceAtLeast(dp(320))
        if (params.width != targetWidth) {
            params.width = targetWidth
            params.x = params.x.coerceIn(0, (screenWidth - targetWidth).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "resize_update_failed type=${it.javaClass.simpleName}") }
        }
    }

    private fun controlButton(iconRes: Int, action: () -> Unit): ImageView = ImageView(appContext).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setPadding(dp(11), dp(11), dp(11), dp(11))
        isClickable = true
        isFocusable = false
        background = circle(Color.rgb(16, 44, 80), Color.rgb(40, 90, 150))
        setOnClickListener { action() }
    }

    private fun textView(textValue: String, size: Float, color: Int, bold: Boolean): TextView = TextView(appContext).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun divider(): View = View(appContext).apply { setBackgroundColor(Color.rgb(32, 70, 91)) }

    private fun panel(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.argb(250, 4, 16, 34), Color.argb(250, 8, 28, 58), Color.argb(250, 4, 16, 34))
    ).apply {
        cornerRadius = dp(52).toFloat()
        setStroke(dp(2), COLOR_ACCENT)
    }

    private fun pill(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun circle(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    private fun removeView(reason: String) {
        val view = rootView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
            .onSuccess { Log.i(TAG, "detached reason=$reason") }
            .onFailure { Log.w(TAG, "detach_failed reason=$reason type=${it.javaClass.simpleName}") }
        clearViewReferences()
    }

    private fun clearViewReferences() {
        rootView = null
        statusView = null
        transcriptHeadlineView = null
        transcriptView = null
        resultTitleView = null
        resultView = null
        stopWave()
        resultSpinner = null
        resultIcon = null
        waveIcon = null
        transcriptCluster = null
        resultCluster = null
        minimizeView = null
        layoutParams = null
        minimized = false
    }

    private fun phaseColor(phase: VoiceOverlayPhase): Int = when (phase) {
        VoiceOverlayPhase.LISTENING -> COLOR_OK
        VoiceOverlayPhase.PROCESSING -> Color.rgb(255, 205, 110)
        VoiceOverlayPhase.RESULT -> Color.rgb(130, 225, 165)
        VoiceOverlayPhase.ERROR -> COLOR_ERROR
        VoiceOverlayPhase.HIDDEN -> Color.WHITE
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "THTVoiceOverlay"
        private val COLOR_ACCENT = Color.rgb(0, 226, 255)
        private val COLOR_ACCENT_DARK = Color.rgb(0, 116, 145)
        private val COLOR_TEXT_SECONDARY = Color.rgb(190, 207, 219)
        private val COLOR_ERROR = Color.rgb(255, 125, 125)
        private val COLOR_OK = Color.rgb(46, 214, 128)
    }
}
