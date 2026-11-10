package com.voicegeely.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tht.voice.R
import com.voicegeely.app.settings.OverlayPosition
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.ui.PublicText
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compact floating voice UI for the Coolray 2025 SX11-LA head unit. */
class VoiceOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val stateMachine = VoiceOverlayStateMachine()
    private val preferences = UserVoicePreferences(appContext)
    private val generation = AtomicLong(0L)

    private var rootView: LinearLayout? = null
    private var textView: TextView? = null
    private var waveView: WaveView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    @Volatile private var micRms = 0f
    @Volatile private var overlayPermissionWarningLogged = false
    @Volatile private var lastLoggedPhase: VoiceOverlayPhase? = null

    fun showListening() {
        micRms = 0f
        render(stateMachine.listening())
    }

    fun updateMicLevel(rms: Float) {
        micRms = rms
    }

    fun updateRecognition(text: String, isFinal: Boolean) {
        if (!isFinal || text.isBlank()) return
        render(stateMachine.processing(text))
    }

    fun showProcessing(text: String = "") {
        render(stateMachine.processing(text))
    }

    fun showResult(message: String, autoHideMs: Long? = null) {
        val token = render(stateMachine.result(message))
        scheduleHide(token, autoHideMs ?: preferences.overlayAutoHideMs)
    }

    fun showError(message: String, autoHideMs: Long? = null) {
        val token = render(stateMachine.error(PublicText.scrub(message)))
        scheduleHide(token, autoHideMs ?: preferences.overlayAutoHideMs)
    }

    fun finishProcessing() {
        val state = stateMachine.finishProcessing() ?: return
        val token = render(state)
        scheduleHide(token, preferences.overlayAutoHideMs)
    }

    fun hide() {
        val token = generation.incrementAndGet()
        stateMachine.hide()
        logPhase(VoiceOverlayPhase.HIDDEN)
        mainHandler.post {
            if (generation.get() == token) removeView("explicit_hide")
        }
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
            val root = ensureView()
            val listening = state.phase == VoiceOverlayPhase.LISTENING
            waveView?.setListening(listening)
            val message = state.popupText
            textView?.apply {
                text = message
                setTextColor(if (state.phase == VoiceOverlayPhase.ERROR) COLOR_ERROR else COLOR_TEXT)
                visibility = if (message.isBlank()) View.GONE else View.VISIBLE
            }
            placeView(root)
        }
        return token
    }

    private fun logPhase(phase: VoiceOverlayPhase) {
        if (lastLoggedPhase == phase) return
        lastLoggedPhase = phase
        Log.i(TAG, "phase=$phase attached=${rootView != null}")
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

    private fun ensureView(): LinearLayout {
        rootView?.let { return it }

        val wave = WaveView()
        val message = TextView(appContext).apply {
            textSize = 22f
            setTextColor(COLOR_TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val close = ImageView(appContext).apply {
            setImageResource(R.drawable.ic_ui_close)
            imageTintList = ColorStateList.valueOf(COLOR_TEXT)
            contentDescription = "Đóng popup giọng nói"
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            })
            isFocusable = true
            setOnClickListener { close() }
        }
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(16), dp(5), dp(5), dp(5))
            minimumHeight = dp(58)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xED253E44.toInt(), 0xF6122329.toInt())
            ).apply {
                cornerRadius = dp(32).toFloat()
                setStroke(dp(1), 0x46D4FAFF)
            }
            elevation = dp(6).toFloat()
            addView(wave, LinearLayout.LayoutParams(dp(58), dp(30)))
            addView(message, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(close, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(7) })
        }.also { root ->
            rootView = root
            textView = message
            waveView = wave
        }
    }

    @Suppress("DEPRECATION") // getRealSize is available on the Android 9 head unit.
    private fun displaySize(): Point = Point().also { windowManager.defaultDisplay.getRealSize(it) }

    private fun placeView(root: LinearLayout) {
        val screen = displaySize()
        val maxWidth = minOf(dp(520), (screen.x - dp(24)).coerceAtLeast(1))
        textView?.maxWidth = (maxWidth - dp(76)).coerceAtLeast(1)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = root.measuredWidth
        val height = root.measuredHeight
        val xLimit = (screen.x - width).coerceAtLeast(0)
        val bottom = bottomLimit(screen.y, height)
        val top = minOf(dp(62), bottom)
        val custom = preferences.overlayCustomPosition
        val x = (custom?.first ?: xLimit / 2).coerceIn(0, xLimit)
        val y = (custom?.second ?: when (preferences.overlayPosition) {
            OverlayPosition.TOP -> top
            OverlayPosition.CENTER -> (screen.y - height) / 2
            OverlayPosition.BOTTOM -> bottom
        }).coerceIn(top, bottom)

        try {
            val params = layoutParams
            if (params == null) {
                val initial = WindowManager.LayoutParams(
                    width, height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    this.x = x
                    this.y = y
                }
                windowManager.addView(root, initial)
                layoutParams = initial
                attachDragHandler(root, initial)
                Log.i(TAG, "attached width=$width height=$height x=$x y=$y")
            } else if (params.width != width || params.height != height || params.x != x || params.y != y) {
                params.width = width
                params.height = height
                params.x = x
                params.y = y
                windowManager.updateViewLayout(root, params)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "layout_failed type=${e.javaClass.simpleName}")
            if (layoutParams == null) clearViewReferences()
        }
    }

    // OEM resources give 50dp for the status bar and 80dp for car navigation at 720dp height.
    private fun bottomLimit(screenHeight: Int, height: Int): Int =
        (screenHeight - height - dp(92)).coerceAtLeast(0)

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
                    if (!moved && abs(dx) < dp(4) && abs(dy) < dp(4)) return@setOnTouchListener true
                    moved = true
                    val screen = displaySize()
                    val bottom = bottomLimit(screen.y, params.height)
                    params.x = (startX + dx).coerceIn(0, (screen.x - params.width).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(minOf(dp(62), bottom), bottom)
                    runCatching { windowManager.updateViewLayout(view, params) }
                        .onFailure { Log.w(TAG, "drag_update_failed type=${it.javaClass.simpleName}") }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        preferences.saveOverlayCustomPosition(params.x, params.y)
                        Log.i(TAG, "drag_saved x=${params.x} y=${params.y}")
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeView(reason: String) {
        val view = rootView ?: return
        if (layoutParams != null) {
            runCatching { windowManager.removeViewImmediate(view) }
                .onSuccess { Log.i(TAG, "detached reason=$reason") }
                .onFailure { Log.w(TAG, "detach_failed reason=$reason type=${it.javaClass.simpleName}") }
        }
        clearViewReferences()
    }

    private fun clearViewReferences() {
        waveView?.setListening(false)
        rootView = null
        textView = null
        waveView = null
        layoutParams = null
    }

    private inner class WaveView : View(appContext) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8DF9D5.toInt() }
        private var level = 0f

        init {
            contentDescription = "Đang nghe"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        fun setListening(listening: Boolean) {
            visibility = if (listening) VISIBLE else GONE
            level = 0f
            if (listening) invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val animated = ValueAnimator.areAnimatorsEnabled()
            val target = (micRms / 0.035f).coerceIn(0f, 1f)
            level += (target - level) * if (target > level) 0.35f else 0.18f
            val step = width / 9f
            val halfBarWidth = step * 0.26f
            val centerY = height / 2f
            for (i in 0 until 9) {
                val envelope = 1f - abs(i - 4) * 0.16f
                val amplitude = if (animated) 0.14f + level * 0.8f * envelope else 0.2f + 0.22f * envelope
                val halfHeight = centerY * amplitude
                val centerX = step * (i + 0.5f)
                canvas.drawRoundRect(
                    centerX - halfBarWidth, centerY - halfHeight,
                    centerX + halfBarWidth, centerY + halfHeight,
                    halfBarWidth, halfBarWidth, paint
                )
            }
            if (animated && isAttachedToWindow && isShown) postInvalidateDelayed(33L)
        }
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "THTVoiceOverlay"
        private val COLOR_TEXT = Color.rgb(238, 245, 245)
        private val COLOR_ERROR = Color.rgb(255, 211, 162)
    }
}
