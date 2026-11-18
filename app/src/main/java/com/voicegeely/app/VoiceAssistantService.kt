package com.voicegeely.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import android.media.MediaRecorder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.voicegeely.app.asr.AsrInitResult
import com.voicegeely.app.asr.Sherpa65mModelSpec
import com.voicegeely.app.asr.SherpaModelInstaller
import com.voicegeely.app.asr.SherpaStreamingAsrEngine
import com.voicegeely.app.car.AndroidCarVehiclePropertyAccess
import com.voicegeely.app.car.CoolrayHeadUnit
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.kws.*
import com.voicegeely.app.kws.VoiceMicStateMachine
import com.voicegeely.app.overlay.VoiceOverlayController
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.settings.MediaAppSettings
import com.voicegeely.app.settings.TtsVoice
import com.voicegeely.app.settings.UserVoicePreferences
import com.voicegeely.app.tts.Mp3ReplyPlayer
import com.voicegeely.app.tts.Mp3VoicePacks
import com.voicegeely.app.tts.SherpaOfflineTtsEngine
import com.voicegeely.app.voice.AndroidSystemActionBackend
import com.voicegeely.app.voice.MediaPreferenceResolver
import com.voicegeely.app.voice.NavigationPreferenceResolver
import com.voicegeely.app.voice.ParsedVoiceCommand
import com.voicegeely.app.voice.VoiceParseChain
import com.voicegeely.app.voice.VoiceCommandCoordinator
import com.voicegeely.app.voice.VoiceCommandRouter
import com.voicegeely.app.voice.VoiceFeedbackAudio
import com.voicegeely.app.voice.VoiceFeedbackCue
import com.voicegeely.app.voice.VoiceFeedbackPolicy
import com.voicegeely.app.voice.VoiceResponseFormatter
import com.voicegeely.app.voice.VoiceResponseMode
import com.voicegeely.app.voice.VoiceSpeechSink
import com.voicegeely.app.voice.safety.ConfirmationIntent
import com.voicegeely.app.voice.safety.PendingCommandStore
import com.voicegeely.app.voice.safety.SafetyDecisionType
import com.voicegeely.app.voice.safety.SafetyPromptFormatter
import com.voicegeely.app.voice.safety.VehicleSafetyGate
import com.voicegeely.app.voice.safety.VoiceConfirmationParser
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * Foreground voice service with fully local microphone -> Sherpa -> typed command flow.
 * No recognized text is sent to a cloud parser.
 *
 * Microphone ownership is explicit: idle KWS is stopped and releases AudioRecord
 * before the 65M ASR session starts. KWS resumes only after command/TTS processing.
 */
class VoiceAssistantService : Service() {
    private lateinit var licenseManager: LicenseManager
    private lateinit var mediaSettings: MediaAppSettings
    private lateinit var voicePreferences: UserVoicePreferences
    private lateinit var feedbackAudio: VoiceFeedbackAudio
    private lateinit var audioDucker: com.voicegeely.app.tts.AssistantAudioDucker
    /** Music handling for the current session (pause vs duck) and the last command it ran. */
    @Volatile private var sessionPausesMusic = false
    @Volatile private var lastCommand: com.voicegeely.app.voice.VoiceCommand? = null
    @Volatile private var lastCommandSucceeded = false
    /** Voice key pressed while a command was being handled/answered: cut the reply, listen again. */
    @Volatile private var bargeInRequested = false
    private lateinit var mp3Reply: Mp3ReplyPlayer
    private lateinit var offlineTts: SherpaOfflineTtsEngine
    private lateinit var systemTts: com.voicegeely.app.tts.SystemTtsFallback
    private val boundedReply = com.voicegeely.app.tts.BoundedVoiceReply(
        onFailure = { Log.w(TAG, "reply_worker status=$it") }
    )
    private lateinit var asrEngine: SherpaStreamingAsrEngine
    @Volatile private var destroyed = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var wakeWordEngine: SherpaWakeWordEngine
    private lateinit var voiceOverlay: VoiceOverlayController
    private var vehicleAccess: VehiclePropertyAccess? = null
    private lateinit var commandCoordinator: VoiceCommandCoordinator
    private lateinit var safetyGate: VehicleSafetyGate
    private val pendingSafetyCommands = PendingCommandStore()
    private val micState = VoiceMicStateMachine()

    private val training = WakeTrainingControl()
    @Volatile private var trainingRecorder: AudioRecord? = null
    @Volatile private var trainingThread: Thread? = null
    @Volatile private var wakeRejectUntil = 0L
    @Volatile private var activeSessionEffects: WakeSessionEffects? = null
    private val recognitionRunning = AtomicBoolean(false)
    private val voiceTestRunning = AtomicBoolean(false)
    private val wakeWordRunning = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var wakeAudioRecord: AudioRecord? = null
    @Volatile private var recognitionThread: Thread? = null
    @Volatile private var wakeWordThread: Thread? = null
    @Volatile private var reopenForSafetyConfirmation = false
    @Volatile private var wakeReady = false
    @Volatile private var reopenForWakeEcho = false
    /** Bumped for every listening session; a session abandoned by the watchdog must not touch the next one. */
    @Volatile private var sessionGeneration = 0L
    @Volatile private var sessionStartedAt = 0L
    @Volatile private var sessionPhase = "IDLE"
    private val sessionThreadGeneration = InheritableThreadLocal<Long>()
    private val sessionDeadline by lazy {
        com.voicegeely.app.voice.SessionDeadline(
            schedule = { delay, action ->
                val callback = Runnable { action() }
                mainHandler.postDelayed(callback, delay)
                val cancel: () -> Unit = { mainHandler.removeCallbacks(callback) }
                cancel
            },
            onTimeout = { generation ->
                if (!destroyed && generation == sessionGeneration && recognitionRunning.get()) {
                    recoverStuckSession()
                }
            }
        )
    }
    @Volatile private var wakeEchoReopenUsed = false
    /** Wake-word mic failures in a row (bounded retry, see KwsMicPolicy). */
    @Volatile private var kwsFailures = 0
    @Volatile private var kwsRetryScheduled = false
    private var pendingKwsRetry: Runnable? = null
    /** Last audio frame the wake-word loop read (THTVoice): a loop with no audio after the car wakes is restarted. */
    @Volatile private var lastKwsFrameAt = 0L
    private val remoteLicenseCheckRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    private var voiceKeyReceiver: VoiceKeyReceiver? = null
    private val trunkClarification = com.voicegeely.app.voice.TrunkClarification()
    private val windowClarification = com.voicegeely.app.voice.WindowClarification()
    private val commandSuggestion = com.voicegeely.app.voice.CommandSuggestion(parse = { t ->
        VoiceParseChain.parse(t) ?: voicePreferences.aliasBook().let { book -> book.resolve(t) ?: book.resolveScenario(t)?.firstOrNull() }
    })
    /** [S09] Router holder so a fresh car connection can be swapped in without a new coordinator. */
    private lateinit var commandExecutor: com.voicegeely.app.voice.SwappableCommandExecutor
    private var screenOnReceiver: android.content.BroadcastReceiver? = null
    private val vehicleReconnectRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var vehicleReconnectAttempt = 0
    @Volatile private var vehicleReconnectScheduled = false
    /** [REF-02] VHAL voice-key callback handle; values are always logged, trigger only when enabled. */
    private var vehicleVoiceKeyWatch: (() -> Unit)? = null
    @Volatile private var lastMicSource: Int? = null
    private var micFrontEndInstance: com.voicegeely.app.asr.MicFrontEnd? = null
    private var micFrontEndKey: Pair<Boolean, Boolean>? = null

    /**
     * Gain + GTCRN denoise before ASR (Coolray 2025 quiet mic). Both can be turned off in Settings to
     * compare recognition in the car; rebuilt only when that choice changes.
     */
    private fun micFrontEnd(): com.voicegeely.app.asr.MicFrontEnd {
        val key = voicePreferences.micGainEnabled to voicePreferences.micDenoiseEnabled
        micFrontEndInstance?.takeIf { micFrontEndKey == key }?.let { return it }
        micFrontEndInstance?.let { old -> runCatching { old.release() } }
        val gain = if (key.first) com.voicegeely.app.asr.MicFrontEnd.DEFAULT_GAIN else 1f
        return com.voicegeely.app.asr.MicFrontEnd.create(assets, Sherpa65mModelSpec.SAMPLE_RATE, gain = gain, denoise = key.second)
            .also { micFrontEndInstance = it; micFrontEndKey = key; Log.i(TAG, "mic_front_end gain=$gain denoise=${key.second}") }
    }

    override fun onCreate() {
        super.onCreate()
        licenseManager = LicenseManager(this)
        mediaSettings = MediaAppSettings(this)
        voicePreferences = UserVoicePreferences(this)
        feedbackAudio = VoiceFeedbackAudio(com.voicegeely.app.tts.MutedSpeechGuard(com.voicegeely.app.tts.AndroidMutedSpeechAudio(this)), this)
        audioDucker = com.voicegeely.app.tts.AssistantAudioDucker(this)
        mp3Reply = Mp3ReplyPlayer(this)
        offlineTts = SherpaOfflineTtsEngine(this)
        systemTts = com.voicegeely.app.tts.SystemTtsFallback(
            this, com.voicegeely.app.tts.MutedSpeechGuard(com.voicegeely.app.tts.AndroidMutedSpeechAudio(this))
        )
        wakeWordEngine = SherpaWakeWordEngine(this, voicePreferences.wakePhrase, voicePreferences.wakeSensitivity)
        voiceOverlay = VoiceOverlayController(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Voice core đang chạy"))
        registerVoiceKeyReceiver()

        vehicleAccess = AndroidCarVehiclePropertyAccess.create(this)
        startVehicleVoiceKeyWatch()
        com.voicegeely.app.voice.AirflowDirectionCycler.log = { Log.i("THTVoiceAirflow", it) }
        com.voicegeely.app.voice.AirflowDirectionCycler.defaultMinutes = voicePreferences.airflowCycleMinutes
        commandExecutor = com.voicegeely.app.voice.SwappableCommandExecutor(buildVehicleStack(vehicleAccess))
        commandCoordinator = VoiceCommandCoordinator(
            executor = commandExecutor,
            history = com.voicegeely.app.history.VoiceHistory,
            followUp = com.voicegeely.app.voice.FollowUpContext(),
            snapshotReader = ::readFollowUpSnapshot,
            speech = VoiceSpeechSink { text ->
                if (!canPublishSessionResult()) return@VoiceSpeechSink
                broadcastStatus(text, sherpaReady = asrReady())
                voiceOverlay.showResult(text)
                if (voicePreferences.voiceFeedbackEnabled) {
                    val spoken = speakReply(text)
                    lastReplyUnspoken = !spoken
                    if (!spoken) Log.w(TAG, "TTS unavailable: ${offlineTts.lastDetail.orEmpty()}")
                }
            }
        )
        instance = this
        registerScreenOnReceiver()
        if (vehicleAccess == null) scheduleVehicleReconnect("startup_no_backend", newChain = true)
        broadcastStatus("Trợ lý đang khởi động…", sherpaReady = false)
        loadEnginesInBackground()
    }

    /**
     * Staged startup (02/10/2026): the speech models (~400 MB) used to load on the main thread
     * in onCreate, right when the head unit was still booting. Now they load on a low-priority
     * background thread, one after the other, after a short delay when the car has just started:
     * speech recognition → wake word → (assistant ready) → voice. The assistant answers
     * "đang khởi động" until recognition is ready; the first reply loads the voice if it is not
     * loaded yet.
     */
    private fun loadEnginesInBackground() {
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val delayMs = com.voicegeely.app.voice.StartupPolicy.loadDelayMs(android.os.SystemClock.elapsedRealtime())
            if (delayMs > 0) {
                Log.i(TAG, "startup_delay ms=$delayMs (head unit just booted)")
                try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return@Thread }
            }
            if (destroyed) return@Thread
            val installed = SherpaModelInstaller.ensureInstalled(this)
            val engine = SherpaStreamingAsrEngine(installed.modelDir)
            val asrInit = if (installed.available) engine.initialize() else AsrInitResult(false, installed.detail)
            if (asrInit.ready) {
                // THTVoice: one throw-away decode so the first command after the car starts is not misheard.
                val warmStart = android.os.SystemClock.elapsedRealtime()
                runCatching { engine.warmUp() }
                Log.i(TAG, "asr_warmup ms=${android.os.SystemClock.elapsedRealtime() - warmStart}")
            }
            runCatching { commandSuggestion.warmUp() }
            if (destroyed) { runCatching { engine.close() }; return@Thread }
            val wakeInit = wakeWordEngine.initialize()
            mainHandler.post {
                if (destroyed) return@post
                asrEngine = engine
                wakeReady = wakeInit.ready
                serviceReady = true
                VoiceServiceEventBus.sendBroadcast(this, Intent(ACTION_SERVICE_READY))
                broadcastStatus(
                    if (asrInit.ready) {
                        if (installed.copied) "Đã cài xong • Nhận giọng nói offline sẵn sàng" else "Nhận giọng nói offline sẵn sàng"
                    } else {
                        asrInit.detail ?: "Bộ nhận giọng nói chưa sẵn sàng"
                    },
                    sherpaReady = asrInit.ready
                )
                if (!wakeInit.ready) Log.w(TAG, "Offline wake word not ready: ${wakeInit.detail.orEmpty()}")
                ensureWakeWordListening()
            }
            // The voice is the heaviest model and is only needed for the first reply: load it last.
            if (destroyed) return@Thread
            Mp3VoicePacks.load(this, voicePreferences.ttsVoice)
            val ttsInit = offlineTts.initialize(voicePreferences.ttsVoice)
            Log.i(
                TAG,
                "THT Voice ready; sherpaReady=${asrInit.ready}; wakeReady=${wakeInit.ready}; " +
                    "wakePhrase=${wakeWordEngine.wakePhrase.name}; ttsReady=${ttsInit.ready}; " +
                    "ttsVoice=${ttsInit.voice}; vehicleBackend=${vehicleAccess != null}; modelDir=${installed.modelDir.absolutePath}"
            )
            if (!ttsInit.ready) Log.w(TAG, "Offline TTS not ready: ${ttsInit.detail.orEmpty()}")
            if (!destroyed) mainHandler.post { if (!destroyed) broadcastStatus(if (asrInit.ready) "Nhận giọng nói offline sẵn sàng" else (asrInit.detail ?: "Bộ nhận giọng nói chưa sẵn sàng"), sherpaReady = asrReady()) }
        }.apply { name = "THTVoice-Startup"; isDaemon = true }.start()
    }

    private fun asrReady(): Boolean = ::asrEngine.isInitialized && asrEngine.isReady

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!licenseManager.isLicensed()) {
            stopWakeWordListening(waitForRelease = false)
            voiceOverlay.hide()
            broadcastStatus("THT Voice chưa được kích hoạt", sherpaReady = asrReady())
            if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Voice core đang chạy"))
        refreshRemoteLicenseIfDue()
        when (intent?.action) {
            ACTION_WAKE_TRAIN -> startWakeTraining(intent.getStringExtra(EXTRA_WAKE_TRAIN_NAME).orEmpty(), intent.getBooleanExtra(EXTRA_WAKE_TRAIN_MUSIC, false))
            ACTION_WAKE_TRAIN_CANCEL -> cancelWakeTraining()
            ACTION_TRIGGER_RECOGNITION -> handleRecognitionTrigger()
            ACTION_ECARX_KEY, VoiceKeyActions.ECARX_CASE_VARIANT ->
                VoiceKeyLauncher.handle(this, intent.action, source = "service_start")
            ACTION_TEST_VOICE -> {
                val voice = TtsVoice.values().find { it.name == intent.getStringExtra(EXTRA_TTS_TEST_VOICE) }
                runVoiceTest(voice ?: voicePreferences.ttsVoice)
            }
            ACTION_SETTINGS_CHANGED -> {
                com.voicegeely.app.voice.AirflowDirectionCycler.defaultMinutes = voicePreferences.airflowCycleMinutes
                refreshWakeWordState()
                // Loading another voice takes seconds: never on the main thread.
                Thread {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    val ttsState = if (voicePreferences.voiceFeedbackEnabled) {
                        Mp3VoicePacks.load(this, voicePreferences.ttsVoice)
                        offlineTts.initialize(voicePreferences.ttsVoice)
                    } else null
                    val detail = when {
                        !voicePreferences.voiceFeedbackEnabled -> "phản hồi âm thanh"
                        ttsState?.ready == true -> "phản hồi giọng nói offline"
                        else -> ttsState?.detail ?: "TTS offline chưa sẵn sàng"
                    }
                    mainHandler.post { if (!destroyed) broadcastStatus("Đã cập nhật cài đặt • $detail", sherpaReady = asrReady()) }
                }.apply { name = "THTVoice-Settings"; isDaemon = true }.start()
            }
            null -> recoverWakeAfterSleep("start")
            else -> {
                Log.d(TAG, "Ignoring action=${intent.action}")
                ensureWakeWordListening()
            }
        }
        return START_STICKY
    }

    private fun trainingStatus(state: String, text: String) {
        if (destroyed) return
        VoiceServiceEventBus.sendBroadcast(this, Intent(ACTION_WAKE_TRAIN_STATUS).apply {
            putExtra(EXTRA_WAKE_TRAIN_STATE, state)
            putExtra(EXTRA_WAKE_TRAIN_TEXT, text)
        })
    }

    private fun cancelWakeTraining() {
        training.cancel()
        trainingThread?.interrupt()
        // AudioRecord.stop unblocks a pending read; only the worker releases its recorder/native objects.
        runCatching { trainingRecorder?.stop() }
    }

    private fun startWakeTraining(name: String, music: Boolean) {
        if (training.busy) { trainingStatus("failed", "Đang dừng học giọng trước đó. Hãy thử lại."); return }
        if (!serviceReady || !wakeReady || !asrReady() || recognitionRunning.get() || recognitionThread?.isAlive == true || replySpeaking ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            com.voicegeely.app.kws.KwsMicPolicy.callActive(currentAudioMode())) {
            trainingStatus("failed", "Micrô chưa sẵn sàng hoặc đang bận. Hãy thử lại khi xe đỗ.")
            return
        }
        val parked = runCatching { safetyGate.evaluateParkedOnly("Dạy từ gọi").type == com.voicegeely.app.voice.safety.SafetyDecisionType.ALLOW }.getOrDefault(false)
        if (!parked) { trainingStatus("failed", "Chỉ dạy từ gọi khi xe đang đỗ và ở số P."); return }
        val profile = voicePreferences.wakePhrase
        if (profile == WakePhrase.HEY_THT) {
            trainingStatus("failed", "Chọn Hey Ge-ly hoặc Hello Ge-ly và bấm Lưu trước khi dạy.")
            return
        }
        if (WakeVoices(this).all().size >= WakeVoices.MAX) {
            trainingStatus("failed", "Đã đủ giọng. Hãy quên một giọng trước khi dạy thêm.")
            return
        }
        val token = training.begin() ?: return
        stopWakeWordListening(waitForRelease = true)
        if (wakeWordThread?.isAlive == true || !micState.beginAsr()) {
            training.finish(token)
            trainingStatus("failed", "Micrô đang được giải phóng, hãy thử lại.")
            ensureWakeWordListening()
            return
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 55_000L
        val timeout = Runnable { if (training.active(token)) cancelWakeTraining() }
        mainHandler.postDelayed(timeout, 55_000L)
        trainingStatus("started", "Chuẩn bị học giọng…")
        trainingThread = thread(name = "THTVoice-WakeTraining", start = false) {
            var decoder: KwsPhoneDecoder? = null
            var checker: SherpaWakeWordEngine? = null
            var recorder: AudioRecord? = null
            fun active(): Boolean = !destroyed && training.active(token) &&
                android.os.SystemClock.elapsedRealtime() < deadline && voicePreferences.wakePhrase == profile
            fun checkActive() { if (!active()) throw InterruptedException("cancelled") }
            try {
                checkActive()
                val phoneDecoder = KwsPhoneDecoder(this)
                decoder = phoneDecoder
                val crossChecker = SherpaWakeWordEngine(this, profile)
                checker = crossChecker
                check(crossChecker.initialize().ready) { "Không mở được bộ học giọng offline." }
                checkActive()
                recorder = openMicrophone(forWakeWord = true)
                trainingRecorder = recorder
                val mic = recorder ?: error("Không mở được micrô.")
                val takes = mutableListOf<ShortArray>()
                val pcm = ShortArray(CHUNK_SAMPLES)
                repeat(WakeEnrollment.TAKES) { take ->
                    checkActive()
                    check(safetyGate.evaluateParkedOnly("Dạy từ gọi", record = false).type == com.voicegeely.app.voice.safety.SafetyDecisionType.ALLOW) {
                        "Chỉ dạy từ gọi khi xe đang đỗ và ở số P."
                    }
                    val segmenter = WakeTakeSegmenter()
                    // Measure cabin noise before cueing the speaker, never after the first syllable.
                    val calibrationDeadline = android.os.SystemClock.elapsedRealtime() + 2_000L
                    var calibrationSamples = 0
                    while (calibrationSamples < 16_000 / 3 && android.os.SystemClock.elapsedRealtime() < calibrationDeadline) {
                        checkActive()
                        val count = mic.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
                        if (count < 0) error("Micrô bị gián đoạn.")
                        if (count > 0) { segmenter.feed(pcm, count); calibrationSamples += count } else Thread.sleep(10)
                    }
                    check(segmenter.finishCalibration()) { "Không đo được âm thanh nền." }
                    // Stop capture only while the audible cue plays; restarting discards the cue buffer,
                    // and the first captured frame goes straight to the already-calibrated segmenter.
                    mic.stop()
                    trainingStatus("recording", "Lần ${take + 1}/${WakeEnrollment.TAKES}: ${profile.displayLabel}")
                    feedbackAudio.play(VoiceFeedbackCue.LISTENING)
                    feedbackAudio.awaitIdle(2_000)
                    checkActive()
                    mic.startRecording()
                    val takeDeadline = android.os.SystemClock.elapsedRealtime() + 8_000L
                    while (!segmenter.done && android.os.SystemClock.elapsedRealtime() < takeDeadline) {
                        checkActive()
                        if (com.voicegeely.app.kws.KwsMicPolicy.callActive(currentAudioMode())) error("Micrô đang dùng cho cuộc gọi.")
                        val count = mic.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
                        if (count < 0) error("Micrô bị gián đoạn.")
                        if (count > 0) segmenter.feed(pcm, count) else Thread.sleep(20)
                    }
                    takes += segmenter.take()
                }
                closeRecorder(recorder); recorder = null; trainingRecorder = null
                checkActive()
                trainingStatus("processing", "Đang kiểm tra 5 lần gọi…")
                val phones = takes.map { checkActive(); phoneDecoder.decode(it) { !active() } }
                val result = WakeEnrollment.learn(phones, SherpaWakeWordEngine.learnedLabel(profile)) { line, take ->
                    checkActive()
                    crossChecker.hears(takes[take], listOf(line), line.label) { !active() }
                }
                checkActive()
                when (result) {
                    is WakeEnrollment.Result.Failed -> trainingStatus("failed", result.reason)
                    is WakeEnrollment.Result.Learned -> {
                        val saved = training.ifActive(token) {
                            WakeVoices(this).save(WakeVoices.Voice(java.util.UUID.randomUUID().toString(), name.trim().take(48), profile,
                                result.lines.map { it.format() }, System.currentTimeMillis(), music))
                        }
                        if (saved) trainingStatus("done", "Đã học giọng ${name.trim().take(48).ifBlank { "chính" }}")
                        else trainingStatus("failed", "Chưa lưu được giọng. Hãy thử lại.")
                    }
                }
            } catch (_: InterruptedException) {
                trainingStatus("failed", "Đã hủy học giọng.")
            } catch (t: Throwable) {
                Log.w(TAG, "wake_training_failed", t)
                trainingStatus("failed", t.message ?: "Chưa học được giọng, hãy thử lại.")
            } finally {
                closeRecorder(recorder)
                trainingRecorder = null
                runCatching { decoder?.close() }
                runCatching { checker?.close() }
                mainHandler.removeCallbacks(timeout)
                micState.endProcessing()
                training.finish(token)
                trainingThread = null
                mainHandler.post { if (!destroyed) refreshWakeWordState() }
            }
        }.also { it.start() }
    }

    private fun refreshWakeWordState() {
        if (training.busy) { cancelWakeTraining(); return }
        if (!voicePreferences.wakeWordEnabled) {
            stopWakeWordListening(waitForRelease = true)
            return
        }

        if (wakeWordEngine.wakePhrase != voicePreferences.wakePhrase ||
            wakeWordEngine.sensitivity != voicePreferences.wakeSensitivity) {
            stopWakeWordListening(waitForRelease = true)
            if (wakeWordThread?.isAlive == true) return
            try { wakeWordEngine.close() } catch (_: Throwable) {}
            wakeWordEngine = SherpaWakeWordEngine(this, voicePreferences.wakePhrase, voicePreferences.wakeSensitivity)
            wakeReady = false
            Log.i(TAG, "Wake profile changed to ${voicePreferences.wakePhrase.name}")
        }

        if (!wakeReady) {
            val init = wakeWordEngine.initialize()
            wakeReady = init.ready
            if (!init.ready) Log.w(TAG, "Wake word remains unavailable: ${init.detail.orEmpty()}")
        }
        wakeWordEngine.learnedLines = WakeVoices(this).lines(voicePreferences.wakePhrase)
        ensureWakeWordListening()
    }

    private fun handleRecognitionTrigger(wakeVerification: FloatArray? = null, mediaAtTrigger: Boolean = false) {
        // An unsolicited provisional hit must not interrupt training or publish readiness errors.
        if (wakeVerification != null && (training.busy || !licenseManager.isLicensed() ||
                !asrReady() || recognitionThread?.isAlive == true || requiredUpdateHoldsAssistant() ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) return
        if (training.busy) {
            cancelWakeTraining()
            broadcastStatus("Đang dừng học giọng, vui lòng bấm Voice lại.", sherpaReady = asrReady())
            return
        }
        // Re-check on every trigger (wake word, voice key, mic button, safety re-open): a license
        // that expired or was blocked while the Voice core runs must stop it now, not at restart.
        if (!licenseManager.isLicensed()) {
            lockVoiceCore("trigger")
            return
        }
        if (requiredUpdateHoldsAssistant()) {
            val message = "Cần cập nhật THT Voice. Mở ứng dụng và bấm Cập nhật ngay."
            voiceOverlay.showError(message)
            broadcastStatus(message, sherpaReady = asrReady())
            if (!voicePreferences.voiceFeedbackEnabled || !speakReply(message)) feedbackAudio.play(VoiceFeedbackCue.ERROR)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceOverlay.showError("Chưa có quyền micrô")
            broadcastStatus("Chưa có quyền micrô", sherpaReady = asrReady())
            if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
            postPermissionNotification()
            return
        }

        // A native decode may ignore interruption. Do not enter its synchronized engine or
        // share its front end with another session while that worker is still unwinding.
        if (!recognitionRunning.get() && recognitionThread?.isAlive == true) {
            voiceOverlay.showError("Bộ nhận giọng nói đang khôi phục, vui lòng thử lại sau.")
            return
        }

        if (!::asrEngine.isInitialized) {
            // Still loading after the car started (staged startup).
            val message = "Trợ lý đang khởi động, thử lại sau giây lát."
            voiceOverlay.showError(message)
            broadcastStatus(message, sherpaReady = false)
            if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
            return
        }
        if (!asrEngine.isReady) {
            val init = asrEngine.initialize()
            if (!init.ready) {
                val detail = init.detail ?: "Bộ nhận giọng nói chưa sẵn sàng"
                voiceOverlay.showError(detail)
                broadcastStatus(detail, sherpaReady = false)
                if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
                return
            }
        }

        if (com.voicegeely.app.voice.SessionWatchdogPolicy.sessionStuck(
                recognitionRunning.get(), sessionStartedAt, android.os.SystemClock.elapsedRealtime()
            )
        ) {
            recoverStuckSession()
            return
        }

        if (!recognitionRunning.compareAndSet(false, true)) {
            if (wakeVerification != null) return
            if (com.voicegeely.app.voice.BargeInPolicy.shouldBargeIn(micState.state)) {
                // Owner decision 01/10/2026: pressing again while the assistant answers stops the
                // reply and opens the mic for a new command (the running command still finishes).
                bargeInRequested = true
                mp3Reply.interrupt()
                offlineTts.interrupt()
                systemTts.interrupt()
                Log.i(TAG, "barge_in state=${micState.state}")
                return
            }
            voiceOverlay.showListening()
            broadcastStatus("THT Voice đang nghe", sherpaReady = true)
            return
        }

        if (wakeVerification == null) mp3Reply.interrupt()
        stopWakeWordListening(waitForRelease = true)
        if (!micState.beginAsr()) {
            recognitionRunning.set(false)
            Log.w(TAG, "ASR microphone acquire rejected; state=${micState.state}")
            if (wakeVerification == null) voiceOverlay.showError("Micrô đang bận, vui lòng thử lại")
            ensureWakeWordListening()
            return
        }

        val effects = WakeSessionEffects(mediaAtTrigger)
        activeSessionEffects = effects
        val generation = ++sessionGeneration
        sessionStartedAt = android.os.SystemClock.elapsedRealtime()
        sessionPhase = "ASR_CAPTURE"
        sessionDeadline.start(generation, com.voicegeely.app.voice.SessionWatchdogPolicy.STUCK_AFTER_MS)
        recognitionThread = thread(name = "THTVoice-Sherpa", start = true) {
            runRecognitionSession(generation, wakeVerification, effects)
        }
    }

    private fun ensureWakeWordListening() {
        if (destroyed || training.busy || !serviceReady || !wakeReady || !voicePreferences.wakeWordEnabled) return
        if (recognitionRunning.get() || recognitionThread?.isAlive == true) return
        if (wakeWordEngine.wakePhrase != voicePreferences.wakePhrase) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (com.voicegeely.app.kws.KwsMicPolicy.callActive(currentAudioMode())) {
            // Phone call (also via CarPlay / Android Auto): leave the mic to it, check again later.
            scheduleWakeWordRetry(com.voicegeely.app.kws.KwsMicPolicy.CALL_RECHECK_MS, reason = "call_active")
            return
        }
        if (!wakeWordRunning.compareAndSet(false, true)) return
        if (!micState.beginKws()) {
            wakeWordRunning.set(false)
            return
        }

        wakeWordThread = thread(name = "THTVoice-KWS", start = true) {
            runWakeWordLoop()
        }
    }

    private fun currentAudioMode(): Int = runCatching {
        (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).mode
    }.getOrDefault(com.voicegeely.app.kws.KwsMicPolicy.MODE_NORMAL)

    /** Android 10+: true when the system mutes our wake-word recording for a higher-priority app. */
    private fun wakeRecorderSilenced(recorder: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audio.activeRecordingConfigurations.firstOrNull { it.clientAudioSessionId == recorder.audioSessionId }
                ?.isClientSilenced == true
        }.getOrDefault(false)
    }

    private fun scheduleWakeWordRetry(delayMs: Long, reason: String) {
        if (kwsRetryScheduled || destroyed) return
        kwsRetryScheduled = true
        Log.i(TAG, "kws_retry in=${delayMs}ms reason=$reason failures=$kwsFailures")
        val run = Runnable {
            kwsRetryScheduled = false
            pendingKwsRetry = null
            if (!destroyed) ensureWakeWordListening()
        }
        pendingKwsRetry = run
        mainHandler.postDelayed(run, delayMs)
    }

    /**
     * THTVoice 1.0.18 (16/11/2026): after the car sleeps the wake word did not answer until THT Voice
     * was opened. While the car is off the mic fails again and again, so the retry wait grows to a
     * minute; and a loop left from before sleep can sit on a dead recorder. On wake (screen on, app
     * start): forget the failures, drop the pending wait, restart a loop that gets no audio, and
     * listen now. Main thread.
     */
    private fun recoverWakeAfterSleep(reason: String) {
        if (destroyed) return
        kwsFailures = 0
        pendingKwsRetry?.let { mainHandler.removeCallbacks(it) }
        pendingKwsRetry = null
        kwsRetryScheduled = false
        val running = wakeWordRunning.get()
        val busy = recognitionRunning.get() || training.busy
        val stale = running && !busy && com.voicegeely.app.kws.KwsMicPolicy.loopStale(
            android.os.SystemClock.elapsedRealtime(), lastKwsFrameAt)
        Log.i(TAG, "wake_after_sleep reason=$reason running=$running stale=$stale")
        if (stale) stopWakeWordListening(waitForRelease = true)
        ensureWakeWordListening()
    }

    private fun stopWakeWordListening(waitForRelease: Boolean) {
        wakeWordRunning.set(false)
        val currentRecorder = wakeAudioRecord
        closeRecorder(currentRecorder)

        val worker = wakeWordThread
        if (waitForRelease && worker != null && worker !== Thread.currentThread()) {
            try {
                worker.join(KWS_STOP_JOIN_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        // A native decode owns the engine until the worker exits. Never wait on its lock here.
        if (worker?.isAlive != true) {
            try { wakeWordEngine.reset() } catch (_: Throwable) {}
            micState.endKws()
        }
    }

    @SuppressLint("MissingPermission")
    private fun runWakeWordLoop() {
        var recorder: AudioRecord? = null
        var triggerAsr = false
        var failed = false
        var yielded = false
        var verification: FloatArray? = null
        var mediaAtTrigger = false
        val recent = WakePcmBuffer(16_000 * 3)
        val gate = WakeAudioGate()
        val secondChance = WakeSecondChance { android.os.SystemClock.elapsedRealtime() }
        val activeWakePhrase = wakeWordEngine.wakePhrase
        val listenStartedAt = android.os.SystemClock.elapsedRealtime()
        try {
            wakeWordEngine.learnedLines = WakeVoices(this).lines(activeWakePhrase)
            if (!wakeWordEngine.startStream()) {
                Log.w(TAG, "Unable to create Sherpa KWS stream for ${activeWakePhrase.name}")
                failed = true
                return
            }

            lastKwsFrameAt = android.os.SystemClock.elapsedRealtime()
            recorder = openMicrophone(forWakeWord = true)
            wakeAudioRecord = recorder
            if (recorder == null) {
                Log.w(TAG, "Unable to open microphone for KWS")
                failed = true
                return
            }
            var chunksSinceCheck = 0
            preRoll.clear()
            // THTVoice: a microphone that goes silent (all zeros / no frames) is reopened instead of staying deaf.
            val signalWatchdog = com.voicegeely.app.asr.MicSignalWatchdog()
            signalWatchdog.reset(android.os.SystemClock.elapsedRealtime())
            val recovery = com.voicegeely.app.asr.MicRecoveryPolicy()
            recovery.reopened(android.os.SystemClock.elapsedRealtime())

            broadcastStatus("${activeWakePhrase.displayLabel} đang chờ lệnh", sherpaReady = asrReady())
            val pcm = ShortArray(CHUNK_SAMPLES)
            while (wakeWordRunning.get() && serviceReady && voicePreferences.wakeWordEnabled) {
                val activeRecorder = recorder ?: break
                val count = try {
                    activeRecorder.read(pcm, 0, pcm.size)
                } catch (_: Throwable) {
                    failed = true
                    break
                }
                val nowSignal = android.os.SystemClock.elapsedRealtime()
                val checkSignal = recovery.observe(pcm, count, nowSignal)
                if (!checkSignal) signalWatchdog.reset(nowSignal)
                if (checkSignal && count >= 0 && signalWatchdog.stalled(pcm, count, nowSignal)) {
                    if (!wakeWordRunning.get() || destroyed) break
                    if (com.voicegeely.app.kws.KwsMicPolicy.shouldYield(currentAudioMode(), wakeRecorderSilenced(activeRecorder))) {
                        yielded = true
                        break
                    }
                    if (!recovery.tryRecover()) { failed = true; break }
                    closeRecorder(activeRecorder)
                    recorder = openMicrophone(forWakeWord = true)
                    wakeAudioRecord = recorder
                    preRoll.clear(); gate.reset(); wakeWordEngine.reset()
                    if (recorder == null || !wakeWordEngine.startStream()) { failed = true; break }
                    val reopenedAt = android.os.SystemClock.elapsedRealtime()
                    signalWatchdog.reset(reopenedAt); recovery.reopened(reopenedAt)
                    Log.i(TAG, "kws_mic_reopened")
                    continue
                }
                if (count < 0) {
                    // Dead/invalid recorder: retrying read() would spin forever without audio.
                    Log.w(TAG, "kws_read_error code=$count")
                    failed = true
                    break
                }
                if (count == 0) continue
                lastKwsFrameAt = nowSignal

                if (++chunksSinceCheck >= KWS_YIELD_CHECK_CHUNKS) {
                    chunksSinceCheck = 0
                    if (com.voicegeely.app.kws.KwsMicPolicy.shouldYield(currentAudioMode(), wakeRecorderSilenced(activeRecorder))) {
                        Log.i(TAG, "kws_yield_mic mode=${currentAudioMode()}")
                        yielded = true
                        break
                    }
                    if (kwsFailures > 0 &&
                        android.os.SystemClock.elapsedRealtime() - listenStartedAt > com.voicegeely.app.kws.KwsMicPolicy.HEALTHY_LISTEN_MS
                    ) kwsFailures = 0
                }

                val samples = FloatArray(count) { index -> pcm[index] / 32768.0f }
                val media = runCatching { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).isMusicActive }.getOrDefault(false)
                val now = android.os.SystemClock.elapsedRealtime()
                recent.add(samples)
                preRoll.add(samples, now)
                gate.marginDb = MediaWakeGate.gateMarginDb(voicePreferences.wakeSensitivity, media)
                val frame = gate.accept(samples, now, gateEnabled = media)
                if (media) secondChance.close()
                if (wakeWordEngine.lenientActive && !secondChance.active) wakeWordEngine.startStream(false)
                if (frame.resetDecoder) wakeWordEngine.startStream(secondChance.active && !media)
                val detection = if (now >= wakeRejectUntil) wakeWordEngine.acceptWaveform(frame.samples) else null
                if (secondChance.onChunk(gate.lastSpeech, detection != null, !media)) wakeWordEngine.startStream(true)
                if (detection != null) {
                    Log.i(
                        TAG,
                        "Wake word detected profile=${activeWakePhrase.name} keyword=${detection.keyword}"
                    )
                    mediaAtTrigger = media
                    if (media || wakeWordEngine.lenientActive) verification = recent.snapshot()
                    else broadcastStatus("Đã nghe ${activeWakePhrase.displayLabel}", sherpaReady = asrReady())
                    triggerAsr = true
                    wakeWordRunning.set(false)
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Offline wake word loop failed", t)
            failed = true
        } finally {
            wakeWordRunning.set(false)
            closeRecorder(recorder)
            try { wakeWordEngine.reset() } catch (_: Throwable) {}
            wakeWordThread = null
            micState.endKws()
            if (!triggerAsr && !failed && !yielded) mainHandler.post {
                if (!destroyed && !training.busy) refreshWakeWordState()
            }
        }

        // Mic lost or given up: come back on our own instead of staying deaf until the voice key.
        if (!triggerAsr && (failed || yielded)) {
            val delay = if (yielded) {
                com.voicegeely.app.kws.KwsMicPolicy.CALL_RECHECK_MS
            } else {
                com.voicegeely.app.kws.KwsMicPolicy.retryDelayMs(kwsFailures).also { kwsFailures++ }
            }
            mainHandler.post { scheduleWakeWordRetry(delay, if (yielded) "yield" else "mic_failed") }
        }
        if (triggerAsr) kwsFailures = 0

        if (
            triggerAsr &&
            serviceReady &&
            voicePreferences.wakeWordEnabled &&
            voicePreferences.wakePhrase == activeWakePhrase
        ) {
            handleRecognitionTrigger(verification, mediaAtTrigger)
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildAudioRecord(source: Int): AudioRecord? {
        val minBytes = AudioRecord.getMinBufferSize(
            Sherpa65mModelSpec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return null

        return try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Sherpa65mModelSpec.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes * 2, CHUNK_SAMPLES * 2))
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioRecord source=$source failed: ${t.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun openMicrophone(forWakeWord: Boolean): AudioRecord? {
        // Coolray 2025: the plain MIC source carries the usable speech level on this head unit;
        // VOICE_RECOGNITION is kept as fallback only.
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        for (source in sources) {
            val candidate = buildAudioRecord(source) ?: continue
            try {
                if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                    candidate.release()
                    continue
                }
                candidate.startRecording()
                if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.i(TAG, "Microphone active with source=$source")
                    if (!forWakeWord) lastMicSource = source
                    val musicActive = runCatching { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).isMusicActive }
                        .getOrDefault(false)
                    val aec = com.voicegeely.app.asr.EchoCancelPolicy.shouldAttach(
                        forWakeWord, voicePreferences.echoCancelWhilePlaying, musicActive, replySpeaking
                    )
                    Log.i(TAG, "mic aec=$aec wake=$forWakeWord music=$musicActive reply=$replySpeaking")
                    if (aec) com.voicegeely.app.asr.MicEchoCanceler.attach(candidate)
                    return candidate
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to start source=$source: ${t.message}")
            }
            try { candidate.release() } catch (_: Throwable) {}
        }
        return null
    }

    private fun closeRecorder(recorder: AudioRecord?) {
        if (recorder == null) return
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (_: Throwable) {}
        com.voicegeely.app.asr.MicEchoCanceler.release(recorder)
        try { recorder.release() } catch (_: Throwable) {}
        if (audioRecord === recorder) audioRecord = null
        if (wakeAudioRecord === recorder) wakeAudioRecord = null
    }

    @SuppressLint("MissingPermission")
    /** A session that never finished (car or audio call hung): drop it so the key works again. */
    private fun recoverStuckSession() {
        Log.w(TAG, "session_stuck_recovered state=${micState.state} phase=$sessionPhase ageMs=${android.os.SystemClock.elapsedRealtime() - sessionStartedAt}")
        sessionDeadline.cancel()
        sessionGeneration++
        recognitionRunning.set(false)
        recognitionThread?.interrupt()
        val effects = activeSessionEffects
        if (effects?.active == true) {
            val message = "Xử lý quá lâu, phiên nghe đã dừng. Vui lòng thử lại."
            voiceOverlay.showError(message)
            broadcastStatus(message, sherpaReady = asrReady())
            mp3Reply.interrupt()
            offlineTts.interrupt()
            systemTts.interrupt()
        }
        closeRecorder(audioRecord)
        bargeInRequested = false
        reopenForSafetyConfirmation = false
        reopenForWakeEcho = false
        micState.forceIdle()
        effects?.finish(ui = {}, media = { audioDucker.release() })
        activeSessionEffects = null
        // Keep the reference until the worker exits: reset/startStream would otherwise wait
        // forever on the same native recognizer lock, blocking the service's main thread.
    }

    private fun sessionActive(generation: Long): Boolean =
        !destroyed && generation == sessionGeneration && recognitionRunning.get()

    private fun canPublishSessionResult(): Boolean {
        val generation = sessionThreadGeneration.get() ?: return !destroyed
        return sessionActive(generation)
    }

    private fun runRecognitionSession(generation: Long, wakeVerification: FloatArray? = null, effects: WakeSessionEffects) {
        sessionThreadGeneration.set(generation)
        var recorder: AudioRecord? = null
        try {
            // Confirmation shares the normal ASR ownership and watchdog, before opening the mic.
            if (wakeVerification != null) {
                if (!asrEngine.startStream()) return
                for (offset in wakeVerification.indices step CHUNK_SAMPLES) {
                    if (!sessionActive(generation)) return
                    if (!asrEngine.acceptWaveform(wakeVerification.copyOfRange(offset, minOf(offset + CHUNK_SAMPLES, wakeVerification.size)))) return
                }
                val heard = asrEngine.finishStream().text
                asrEngine.reset()
                if (!sessionActive(generation)) return
                if (WakeVerifier.verify(voicePreferences.wakePhrase, heard, acceptBlank = false) == WakeVerifier.Verdict.REJECT) {
                    wakeRejectUntil = android.os.SystemClock.elapsedRealtime() + effects.rejectionCooldownMs
                    Log.i(TAG, "wake_confirmation_rejected")
                    return
                }
            }
            if (!sessionActive(generation)) return
            effects.activate(
                show = { mp3Reply.interrupt(); voiceOverlay.showListening() },
                acquire = {
                    sessionPausesMusic = com.voicegeely.app.tts.MusicFocusPolicy.pauseMusic(
                        voicePreferences.musicWhileListening == com.voicegeely.app.settings.MusicWhileListening.PAUSE)
                    lastCommand = null
                    lastCommandSucceeded = false
                    audioDucker.acquire(sessionPausesMusic)
                },
                cue = { feedbackAudio.play(VoiceFeedbackCue.LISTENING) }
            )
            if (!sessionActive(generation)) return
            if (!asrEngine.startStream()) {
                val detail = "Không mở được bộ nhận giọng nói"
                voiceOverlay.showError(detail)
                broadcastStatus(detail, sherpaReady = asrReady())
                if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
                return
            }
            if (!sessionActive(generation)) return

            recorder = openMicrophone(forWakeWord = false)
            audioRecord = recorder
            if (!sessionActive(generation)) return
            if (recorder == null) {
                val detail = "Không khởi tạo được micrô"
                voiceOverlay.showError(detail)
                broadcastStatus(detail, sherpaReady = true)
                if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
                return
            }

            broadcastStatus("Đang nghe...", sherpaReady = true)
            val pcm = ShortArray(CHUNK_SAMPLES)
            val micProbe = com.voicegeely.app.asr.MicCaptureProbe.start(this, Sherpa65mModelSpec.SAMPLE_RATE)
            val frontEnd = micFrontEnd()
            frontEnd.reset()
            clippingMeter.reset()
            // THTVoice: hand the audio heard right after the wake phrase to the ASR, so a command said in
            // the same breath keeps its first word. Audio older than 1 s is never used.
            if (wakeVerification == null) {
                val lead = preRoll.take(android.os.SystemClock.elapsedRealtime(), HANDOFF_LEAD_MS * Sherpa65mModelSpec.SAMPLE_RATE / 1000)
                if (lead.isNotEmpty()) {
                    val leadSamples = frontEnd.process(lead)
                    if (leadSamples.isNotEmpty()) asrEngine.acceptWaveform(leadSamples)
                }
            } else preRoll.clear()
            val signalWatchdog = com.voicegeely.app.asr.MicSignalWatchdog()
            signalWatchdog.reset(android.os.SystemClock.elapsedRealtime())
            val recovery = com.voicegeely.app.asr.MicRecoveryPolicy()
            recovery.reopened(android.os.SystemClock.elapsedRealtime())
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val maxListenMs = voicePreferences.maxListenMs
            var lastPartial = ""
            var captureError: String? = null
            var connectorGraceText: String? = null
            var connectorGraceAt = 0L

            while (sessionActive(generation) && android.os.SystemClock.elapsedRealtime() - startedAt < maxListenMs) {
                val activeRecorder = recorder ?: break
                val count = activeRecorder.read(pcm, 0, pcm.size)
                if (!sessionActive(generation)) return
                val nowSignal = android.os.SystemClock.elapsedRealtime()
                val checkSignal = recovery.observe(pcm, count, nowSignal)
                if (!checkSignal) signalWatchdog.reset(nowSignal)
                if (checkSignal && count >= 0 && signalWatchdog.stalled(pcm, count, nowSignal)) {
                    if (!recovery.tryRecover() || com.voicegeely.app.kws.KwsMicPolicy.callActive(currentAudioMode()) ||
                        wakeRecorderSilenced(activeRecorder)) {
                        captureError = "Micrô bị gián đoạn. Bạn thử nói lại nhé."
                        break
                    }
                    closeRecorder(activeRecorder)
                    recorder = openMicrophone(forWakeWord = false)
                    audioRecord = recorder
                    if (recorder == null) { captureError = "Micrô bị gián đoạn. Bạn thử nói lại nhé."; break }
                    // A broken input must not turn half a command into an actuator request.
                    asrEngine.reset()
                    frontEnd.reset()
                    if (!asrEngine.startStream()) { captureError = "Không mở được bộ nhận giọng nói"; break }
                    lastPartial = ""
                    connectorGraceText = null
                    connectorGraceAt = 0L
                    clippingMeter.reset()
                    val reopenedAt = android.os.SystemClock.elapsedRealtime()
                    signalWatchdog.reset(reopenedAt); recovery.reopened(reopenedAt)
                    Log.i(TAG, "asr_mic_reopened")
                    continue
                }
                if (count < 0) {
                    // IHU629G probe: a phone call (CarPlay too) takes the mic; this recorder then
                    // returns errors and never recovers. Stop instead of spinning until timeout.
                    Log.w(TAG, "asr_read_error code=$count")
                    captureError = "Micrô bị gián đoạn. Bạn thử nói lại nhé."
                    break
                }
                if (count == 0) continue
                micProbe.add(pcm, count)
                clippingMeter.accept(pcm, count)

                val raw = FloatArray(count) { index -> pcm[index] / 32768.0f }
                voiceOverlay.updateMicLevel(com.voicegeely.app.asr.OfflineEndpointTracker.rms(raw))
                val samples = frontEnd.process(raw)
                if (!sessionActive(generation)) return
                if (samples.isNotEmpty() && !asrEngine.acceptWaveform(samples)) {
                    Log.w(TAG, "asr_input_rejected phase=chunk")
                    captureError = "Bộ nhận giọng nói không nhận được âm thanh"
                    break
                }

                if (lastPartial.isBlank() && asrEngine.noSpeechTimedOut()) {
                    Log.i(TAG, "asr_no_speech_timeout")
                    break
                }

                val partial = asrEngine.partialText()
                if (!sessionActive(generation)) return
                if (partial.isNotBlank() && partial != lastPartial) {
                    lastPartial = partial
                    broadcastRecognition(partial, isFinal = false)
                }

                if (lastPartial.isNotBlank() && asrEngine.isEndpoint()) {
                    // "mở youtube rồi …": a pause after a joining word is not the end of the command.
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (com.voicegeely.app.voice.UtteranceContinuation.stopAtEndpoint(lastPartial, connectorGraceText, connectorGraceAt, now)) break
                    if (connectorGraceText != lastPartial) {
                        connectorGraceText = lastPartial
                        connectorGraceAt = now
                        Log.i(TAG, "endpoint_after_connector wait_ms=${com.voicegeely.app.voice.UtteranceContinuation.GRACE_MS}")
                    }
                }
            }

            if (!sessionActive(generation)) return
            if (captureError == null) {
                val tail = frontEnd.finish()
                if (!sessionActive(generation)) return
                if (tail.isNotEmpty() && !asrEngine.acceptWaveform(tail)) {
                    Log.w(TAG, "asr_input_rejected phase=tail")
                    captureError = "Bộ nhận giọng nói không nhận được âm thanh"
                }
            }
            if (!sessionActive(generation)) return
            sessionPhase = "ASR_DECODE"
            val result = if (captureError == null) asrEngine.finishStream() else null
            if (!sessionActive(generation)) return
            // A failed capture or final decode must not dispatch an incomplete command.
            val heardText = result?.takeIf { it.final && it.detail == null }?.text.orEmpty().trim()
            micProbe.finish(lastMicSource, heardText)
            // "Hey Geely" said again at the start is not part of the command (owner report 03/10/2026).
            val onlyWakePhrase = com.voicegeely.app.voice.WakePhraseEcho.isOnlyWakePhrase(heardText)
            val finalText = com.voicegeely.app.voice.WakePhraseEcho.strip(heardText)

            // Release the microphone before ACK/TTS so the car audio route does
            // not keep recording or feed the assistant's own response back in.
            closeRecorder(recorder)
            recorder = null
            micState.beginProcessing()
            sessionPhase = "COMMAND_OR_REPLY"
            // End cue only when spoken feedback is off (owner request 30/09/2026): with voice
            // feedback on, the start beep plus the spoken result is enough.
            val spokenFeedback = voicePreferences.voiceFeedbackEnabled
            // Nothing heard → stop sound now. Otherwise the result decides: success sound when the
            // command was handled, stop sound otherwise (never both).
            if (onlyWakePhrase && !wakeEchoReopenUsed) {
                // Only the wake phrase was heard: listen once more for the actual command.
                Log.i(TAG, "wake_phrase_only_reopen")
                wakeEchoReopenUsed = true
                reopenForWakeEcho = true
            } else if (!spokenFeedback && finalText.isBlank()) feedbackAudio.play(VoiceFeedbackCue.ERROR)

            if (reopenForWakeEcho) {
                // Nothing to execute; the session reopens below.
            } else if (finalText.isNotBlank()) {
                lastSessionClipped = clippingMeter.clipped()
                if (lastSessionClipped) Log.i(TAG, "asr_clipping")
                voiceOverlay.updateRecognition(finalText, isFinal = true)
                broadcastRecognition(finalText, isFinal = true)
                executeRecognizedText(finalText)
            } else {
                val message = captureError ?: result?.detail ?:
                    if (clippingMeter.clipped()) com.voicegeely.app.asr.ClippingMeter.MESSAGE else "Không nghe rõ lệnh"
                voiceOverlay.showError(message)
                broadcastStatus(message, sherpaReady = true)
                if (spokenFeedback && !speakReply(message)) feedbackAudio.play(VoiceFeedbackCue.ERROR)
            }
        } catch (t: Throwable) {
            if (!sessionActive(generation)) return
            Log.e(TAG, "Offline recognition failed", t)
            if (!effects.active) return
            val detail = "Lỗi nhận dạng offline: ${t.message.orEmpty()}"
            voiceOverlay.showError(detail)
            broadcastStatus(detail, sherpaReady = asrReady())
            if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
        } finally {
            closeRecorder(recorder)
            if (generation != sessionGeneration) {
                // Abandoned by the watchdog; a newer session owns the mic and the state now.
                Log.w(TAG, "stale_session_finished generation=$generation")
                if (recognitionThread === Thread.currentThread()) recognitionThread = null
                sessionThreadGeneration.remove()
                mainHandler.post { if (!destroyed) ensureWakeWordListening() }
                return
            }
            sessionPhase = "ASR_RESET"
            try { asrEngine.reset() } catch (_: Throwable) {}
            if (!sessionActive(generation)) {
                if (recognitionThread === Thread.currentThread()) recognitionThread = null
                sessionThreadGeneration.remove()
                mainHandler.post { if (!destroyed) ensureWakeWordListening() }
                return
            }
            val bargeIn = bargeInRequested
            bargeInRequested = false
            val wakeEcho = reopenForWakeEcho
            reopenForWakeEcho = false
            val shouldReopen = reopenForSafetyConfirmation || bargeIn || wakeEcho
            reopenForSafetyConfirmation = false
            if (!wakeEcho) wakeEchoReopenUsed = false
            effects.finish(ui = { voiceOverlay.finishProcessing() }, media = {
              if (!(shouldReopen && serviceReady)) {
                // Let the last sound finish, then bring the music back up.
                sessionPhase = "FEEDBACK_CLEANUP"
                feedbackAudio.awaitIdle()
                sessionPhase = "AUDIO_FOCUS_RELEASE"
                audioDucker.release()
                // "Tạm dừng nhạc" in pause mode: the player would resume on focus return, so pause again.
                if (com.voicegeely.app.tts.MusicFocusPolicy.repauseAfterRelease(sessionPausesMusic, lastCommand, lastCommandSucceeded)) {
                    try { Thread.sleep(REPAUSE_DELAY_MS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                    audioDucker.sendPauseKey()
                }
              }
            })
            if (activeSessionEffects === effects) activeSessionEffects = null
            if (shouldReopen && serviceReady) {
                try { Thread.sleep(if (bargeIn) BARGE_IN_REOPEN_DELAY_MS else SAFETY_REOPEN_DELAY_MS) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            if (!sessionActive(generation)) {
                if (recognitionThread === Thread.currentThread()) recognitionThread = null
                sessionThreadGeneration.remove()
                mainHandler.post { if (!destroyed) ensureWakeWordListening() }
                return
            }
            sessionDeadline.cancel()
            // A press during audio cleanup is still a barge-in and must open the next session.
            val reopenAfterCleanup = shouldReopen || bargeInRequested
            bargeInRequested = false
            micState.endProcessing()
            sessionPhase = "IDLE"
            recognitionRunning.set(false)
            recognitionThread = null
            sessionThreadGeneration.remove()
            mainHandler.post {
                if (destroyed || generation != sessionGeneration || !serviceReady) return@post
                if (reopenAfterCleanup) handleRecognitionTrigger() else ensureWakeWordListening()
            }
        }
    }

    /** Local-only command path. No Groq/LLM fallback is allowed here. */
    private fun executeRecognizedText(heard: String) {
        if (!canPublishSessionResult()) return
        val text = com.voicegeely.app.voice.UtteranceContinuation.stripDanglingConnector(heard)
        if (text != heard) Log.i(TAG, "dangling_connector_dropped")
        val confirmation = VoiceConfirmationParser.parse(text)
        val pending = pendingSafetyCommands.peek()
        if (confirmation != null) {
            if (pending == null) {
                emitSafetyMessage("Không còn lệnh nào đang chờ xác nhận.", VoiceFeedbackCue.ERROR)
                return
            }
            handleSafetyConfirmation(confirmation)
            return
        }

        if (pending != null) {
            Log.i(TAG, "Safety pending command replaced hash=${pending.commandHash}")
            pendingSafetyCommands.clear()
        }

        // "mở cốp" → ask which one; the next short answer becomes "mở cốp trước/sau".
        val now = System.currentTimeMillis()
        // "Ý bạn là … phải không?" answered: yes runs the suggested phrase through the normal path.
        when (val answer = commandSuggestion.resolve(text, now)) {
            is com.voicegeely.app.voice.CommandSuggestion.Answer.Accept -> {
                Log.i(TAG, "suggestion_accepted phrase=${answer.phrase}")
                executeRecognizedText(answer.phrase)
                return
            }
            com.voicegeely.app.voice.CommandSuggestion.Answer.Decline -> {
                emitSafetyMessage(com.voicegeely.app.voice.CommandSuggestion.DECLINED, VoiceFeedbackCue.PROCESSING)
                reopenForSafetyConfirmation = true
                return
            }
            null -> Unit
        }
        windowClarification.resolve(text, now)?.let { answered ->
            Log.i(TAG, "window_clarified -> ${answered.command}")
            val safety = safetyGate.evaluate(answered.command)
            if (safety.type == SafetyDecisionType.ALLOW) executeParsedCommand(answered)
            else emitSafetyMessage(safety.reason ?: "Lệnh bị chặn vì không đủ điều kiện an toàn.", VoiceFeedbackCue.ERROR)
            return
        }
        val trunkAnswer = trunkClarification.resolve(text, now)
        if (trunkAnswer != null) {
            Log.i(TAG, "trunk_clarified -> $trunkAnswer")
            executeRecognizedText(trunkAnswer)
            return
        }
        when (val reply = trunkClarification.check(text, now)) {
            is com.voicegeely.app.voice.TrunkClarification.Reply.Ask -> {
                emitSafetyMessage(reply.question, VoiceFeedbackCue.PROCESSING)
                reopenForSafetyConfirmation = true
                return
            }
            is com.voicegeely.app.voice.TrunkClarification.Reply.Rewrite -> {
                executeRecognizedText(reply.text)
                return
            }
            is com.voicegeely.app.voice.TrunkClarification.Reply.Tell -> {
                emitSafetyMessage(reply.message, VoiceFeedbackCue.ERROR)
                return
            }
            null -> Unit
        }

        com.voicegeely.app.voice.UserAliasCommands.parse(text)?.let { request ->
            handleAliasRequest(request)
            return
        }

        // Order: follow-up phrase → several commands → built-in parser → user alias (built-in always wins).
        val followUp = commandCoordinator.followUp?.resolve(text, System.currentTimeMillis())
            ?.also { Log.i(TAG, "follow_up resolved command=${it.command} text=${it.normalizedText}") }
        if (followUp == null) {
            val aliasBook = voicePreferences.aliasBook()
            // Taught scenario ("đi làm" = several commands); a built-in command still wins.
            if (VoiceParseChain.parse(text) == null) aliasBook.resolveScenario(text)?.let { steps ->
                Log.i(TAG, "user_scenario resolved steps=${steps.size}")
                executeMultiStep(steps)
                return
            }
            when (val plan = com.voicegeely.app.voice.MultiCommandPlan.plan(text) { part -> VoiceParseChain.parse(part) ?: aliasBook.resolve(part) }) {
                is com.voicegeely.app.voice.MultiCommandPlan.Result.Steps -> { executeMultiStep(plan.steps); return }
                com.voicegeely.app.voice.MultiCommandPlan.Result.TooMany -> {
                    emitSafetyMessage("Mỗi câu chỉ làm tối đa ${com.voicegeely.app.voice.MultiCommandPlan.MAX_STEPS} lệnh. Hãy nói ngắn hơn.", VoiceFeedbackCue.ERROR)
                    return
                }
                null -> Unit
            }
        }
        val parsed: ParsedVoiceCommand = followUp
            ?: VoiceParseChain.parse(text)
            ?: voicePreferences.aliasBook().resolve(text)
                ?.also { Log.i(TAG, "user_alias resolved command=${it.command}") }
            ?: windowClarification.check(text, now)?.let { question ->
                // "mở kính" without a window: ask, then listen again for the answer.
                emitSafetyMessage(question, VoiceFeedbackCue.PROCESSING)
                reopenForSafetyConfirmation = true
                return
            }
            ?: suggestCommand(text, now)?.let { return }
            ?: run {
                val detail = "Lệnh chưa được hỗ trợ offline: $text"
                voiceOverlay.showError(detail)
                broadcastStatus(detail, sherpaReady = true)
                if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
                return
            }

        Log.i(TAG, "Offline parsed command=${parsed.command} text=${parsed.normalizedText}")
        // "bật điêu hoa" became "open app điêu hoa": no such app → offer the close command instead.
        val appName = (parsed.command as? com.voicegeely.app.voice.VoiceCommand.OpenInstalledApp)?.spokenName
        if (appName != null && !installedAppExists(appName) && suggestCommand(text, now) != null) return
        val safety = safetyGate.evaluate(parsed.command)
        when (safety.type) {
            SafetyDecisionType.ALLOW -> executeParsedCommand(parsed)
            SafetyDecisionType.REQUIRE_CONFIRMATION -> {
                val motion = safety.motion ?: run {
                    emitSafetyMessage("Không đọc được trạng thái xe để xác nhận an toàn.", VoiceFeedbackCue.ERROR)
                    return
                }
                val prompt = SafetyPromptFormatter.confirmation(parsed.command, motion)
                emitSafetyMessage(prompt, VoiceFeedbackCue.PROCESSING)
                val stored = pendingSafetyCommands.put(parsed, motion)
                reopenForSafetyConfirmation = true
                Log.i(
                    TAG,
                    "Safety confirmation required hash=${stored.commandHash} speed=${motion.speedKph} gear=${motion.gear}"
                )
            }
            SafetyDecisionType.BLOCK -> {
                Log.w(
                    TAG,
                    "Safety blocked command=${parsed.command} speed=${safety.motion?.speedKph} gear=${safety.motion?.gear} reason=${safety.reason}"
                )
                emitSafetyMessage(
                    safety.reason ?: "Lệnh bị chặn vì không đủ điều kiện an toàn.",
                    VoiceFeedbackCue.ERROR
                )
            }
        }
    }

    private fun handleSafetyConfirmation(intent: ConfirmationIntent) {
        val pending = pendingSafetyCommands.take()
        if (pending == null) {
            emitSafetyMessage("Không còn lệnh nào đang chờ xác nhận.", VoiceFeedbackCue.ERROR)
            return
        }

        if (intent == ConfirmationIntent.CANCEL) {
            Log.i(TAG, "Safety command cancelled hash=${pending.commandHash}")
            emitSafetyMessage("Đã hủy lệnh.", VoiceFeedbackCue.PROCESSING)
            return
        }

        val recheck = safetyGate.recheckConfirmed(pending.parsed.command)
        if (recheck.type != SafetyDecisionType.ALLOW) {
            Log.w(
                TAG,
                "Safety confirmation blocked after recheck hash=${pending.commandHash} " +
                    "speed=${recheck.motion?.speedKph} gear=${recheck.motion?.gear} reason=${recheck.reason}"
            )
            emitSafetyMessage(
                recheck.reason ?: "Trạng thái xe đã thay đổi, lệnh không được thực hiện.",
                VoiceFeedbackCue.ERROR
            )
            return
        }

        Log.i(
            TAG,
            "Safety confirmation accepted hash=${pending.commandHash} " +
                "speed=${recheck.motion?.speedKph} gear=${recheck.motion?.gear}"
        )
        executeParsedCommand(pending.parsed)
    }

    private fun executeParsedCommand(parsed: ParsedVoiceCommand) {
        if (!canPublishSessionResult()) return
        try {
            lastReplyUnspoken = false
            val spokenFeedback = voicePreferences.voiceFeedbackEnabled
            // Bounded (owner report 03/10/2026): a car call or reply that never returns must not
            // leave the assistant deaf to the voice key.
            val task = java.util.concurrent.FutureTask {
                runBlocking {
                    commandCoordinator.handle(
                        parsed,
                        // Owner decision 30/09/2026: speak only the result ("Đã mở YouTube"), no "Đang …" first.
                        if (spokenFeedback) VoiceResponseMode.SHORT else VoiceResponseMode.SILENT
                    )
                }
            }
            val worker = Thread(task, "THTVoice-Command").apply { isDaemon = true; start() }
            val execution = try {
                task.get(com.voicegeely.app.voice.SessionWatchdogPolicy.COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                Log.e(TAG, "command_timeout command=${parsed.command}")
                commandCoordinator.followUp?.clear()
                task.cancel(true)
                worker.interrupt()
                if (!canPublishSessionResult()) return
                // Unblocks a reply stuck in playback; the hung thread keeps no hold on the session.
                mp3Reply.interrupt()
                offlineTts.interrupt()
                systemTts.interrupt()
                lastCommand = parsed.command
                lastCommandSucceeded = false
                val detail = "Xe chưa phản hồi lệnh, vui lòng thử lại."
                voiceOverlay.showError(detail)
                broadcastStatus(detail, sherpaReady = true)
                feedbackAudio.play(VoiceFeedbackCue.ERROR)
                return
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause ?: e
            } catch (interrupted: InterruptedException) {
                task.cancel(true)
                worker.interrupt()
                Thread.currentThread().interrupt()
                return
            }
            if (!canPublishSessionResult()) return
            Log.i(TAG, "vehicle_command_result command=${parsed.command} result=$execution")
            // Result sentence not in the voice pack (e.g. split screen) and no other voice: say a short
            // reply the pack has, as THTVoice does, instead of staying silent.
            if (spokenFeedback && lastReplyUnspoken) {
                lastReplyUnspoken = false
                com.voicegeely.app.tts.Mp3ReplyFallback.single(parsed.command, execution)?.let { fallback ->
                    Log.i(TAG, "reply_fallback command=${parsed.command}")
                    if (!speakReply(fallback)) feedbackAudio.play(VoiceFeedbackPolicy.forExecution(execution))
                }
            }
            lastCommand = parsed.command
            lastCommandSucceeded = execution is com.voicegeely.app.voice.CommandExecutionResult.Confirmed ||
                execution is com.voicegeely.app.voice.CommandExecutionResult.AcceptedUnverified
            if (!spokenFeedback) {
                val resultText = VoiceResponseFormatter.result(parsed.command, execution)
                // Silent mode has no speech sink: still publish the outcome so history shows it.
                broadcastStatus(resultText, sherpaReady = true)
                voiceOverlay.showResult(resultText)
                feedbackAudio.play(VoiceFeedbackPolicy.forExecution(execution))
            }
        } catch (t: Throwable) {
            if (!canPublishSessionResult()) return
            Log.e(TAG, "Command execution failed", t)
            val detail = "Không thực hiện được lệnh: ${t.message.orEmpty()}"
            voiceOverlay.showError(detail)
            broadcastStatus(detail, sherpaReady = true)
            if (!voicePreferences.voiceFeedbackEnabled) feedbackAudio.play(VoiceFeedbackCue.ERROR)
        }
    }

    /**
     * [S07] Steps run one after another through the safety gate and the coordinator (silent per
     * step), each bounded by the command timeout; one short summary is spoken at the end.
     */
    private fun executeMultiStep(steps: List<ParsedVoiceCommand>) {
        Log.i(TAG, "multi_step count=${steps.size} commands=${steps.map { it.command }}")
        val outcome = com.voicegeely.app.voice.MultiStepRunner.run(
            steps,
            gate = { command ->
                val decision = safetyGate.evaluate(command)
                when (decision.type) {
                    SafetyDecisionType.ALLOW -> com.voicegeely.app.voice.MultiStepRunner.Gate.Allow
                    SafetyDecisionType.REQUIRE_CONFIRMATION -> com.voicegeely.app.voice.MultiStepRunner.Gate.NeedsConfirmation
                    SafetyDecisionType.BLOCK -> com.voicegeely.app.voice.MultiStepRunner.Gate.Blocked(
                        decision.reason ?: "Lệnh bị chặn vì không đủ điều kiện an toàn."
                    )
                }
            },
            run = { step -> runStepBounded(step) }
        )
        lastCommand = steps.getOrNull((outcome.stoppedAt ?: steps.lastIndex))?.command
        lastCommandSucceeded = outcome.stoppedAt == null && outcome.results.all {
            it is com.voicegeely.app.voice.CommandExecutionResult.Confirmed || it is com.voicegeely.app.voice.CommandExecutionResult.AcceptedUnverified
        }
        Log.i(TAG, "multi_step done stoppedAt=${outcome.stoppedAt} results=${outcome.results.map { it.javaClass.simpleName }}")
        emitSafetyMessage(outcome.summary, if (outcome.stoppedAt == null) VoiceFeedbackCue.PROCESSING else VoiceFeedbackCue.ERROR)
    }

    private fun runStepBounded(step: ParsedVoiceCommand): com.voicegeely.app.voice.CommandExecutionResult {
        if (!canPublishSessionResult()) return com.voicegeely.app.voice.CommandExecutionResult.Failed("Phiên nghe đã dừng.")
        val task = java.util.concurrent.FutureTask {
            runBlocking { commandCoordinator.handle(step, VoiceResponseMode.SILENT) }
        }
        val worker = Thread(task, "THTVoice-Step").apply { isDaemon = true; start() }
        return try {
            task.get(com.voicegeely.app.voice.SessionWatchdogPolicy.COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (timeout: java.util.concurrent.TimeoutException) {
            Log.e(TAG, "command_timeout step=${step.command}")
            task.cancel(true)
            worker.interrupt()
            commandCoordinator.followUp?.clear()
            com.voicegeely.app.voice.CommandExecutionResult.Failed("Xe chưa phản hồi lệnh.")
        } catch (e: java.util.concurrent.ExecutionException) {
            com.voicegeely.app.voice.CommandExecutionResult.Failed(e.cause?.message ?: "Lỗi không xác định.", e.cause?.javaClass?.name)
        } catch (interrupted: InterruptedException) {
            task.cancel(true)
            worker.interrupt()
            Thread.currentThread().interrupt()
            com.voicegeely.app.voice.CommandExecutionResult.Failed("Phiên nghe đã dừng.")
        }
    }

    /** Asks "Ý bạn là … phải không?" and listens again; null when nothing is close enough. */
    private fun suggestCommand(text: String, now: Long): String? {
        if (!canPublishSessionResult()) return null
        val extra = runCatching { voicePreferences.aliasBook().all().map { it.phrase } }.getOrDefault(emptyList())
        val question = commandSuggestion.suggest(text, extra, now) { !canPublishSessionResult() } ?: return null
        if (!canPublishSessionResult()) { commandSuggestion.clear(); return null }
        Log.i(TAG, "suggestion_offered")
        emitSafetyMessage(question, VoiceFeedbackCue.PROCESSING)
        reopenForSafetyConfirmation = true
        return question
    }

    private fun installedAppExists(spokenName: String): Boolean = runCatching {
        com.voicegeely.app.voice.InstalledAppMatcher.match(
            spokenName, com.voicegeely.app.voice.AndroidInstalledAppSource(this).apps()
        ).isNotEmpty()
    }.getOrDefault(true)

    /** [S05] "Khi tôi nói X thì Y" / "quên tên gọi X": saved locally, answered with a short reply. */
    private fun handleAliasRequest(request: com.voicegeely.app.voice.UserAliasCommands.Request) {
        val book = voicePreferences.aliasBook()
        when (request) {
            is com.voicegeely.app.voice.UserAliasCommands.Request.Teach -> when (val r = book.teach(request.phrase, request.target)) {
                is com.voicegeely.app.voice.UserAliasBook.TeachResult.Saved -> {
                    voicePreferences.userAliases = book.encode()
                    Log.i(TAG, "user_alias saved command=${r.command} replaced=${r.replaced}")
                    emitSafetyMessage(
                        if (r.steps > 1) "Đã nhớ kịch bản \"${r.alias.phrase}\" gồm ${r.steps} lệnh."
                        else "Đã nhớ: nói \"${r.alias.phrase}\" là ${r.alias.target}.",
                        VoiceFeedbackCue.PROCESSING
                    )
                }
                is com.voicegeely.app.voice.UserAliasBook.TeachResult.Refused ->
                    emitSafetyMessage(r.reason, VoiceFeedbackCue.ERROR)
            }
            is com.voicegeely.app.voice.UserAliasCommands.Request.Forget -> if (book.forget(request.phrase)) {
                voicePreferences.userAliases = book.encode()
                emitSafetyMessage("Đã quên tên gọi \"${com.voicegeely.app.voice.UserAliasBook.normalize(request.phrase)}\".", VoiceFeedbackCue.PROCESSING)
            } else {
                emitSafetyMessage("Không có tên gọi \"${com.voicegeely.app.voice.UserAliasBook.normalize(request.phrase)}\".", VoiceFeedbackCue.ERROR)
            }
        }
    }

    private fun emitSafetyMessage(message: String, cue: VoiceFeedbackCue) {
        if (!canPublishSessionResult()) return
        broadcastStatus(message, sherpaReady = asrReady())
        if (cue == VoiceFeedbackCue.ERROR) voiceOverlay.showError(message)
        else voiceOverlay.showResult(message)
        if (voicePreferences.voiceFeedbackEnabled) {
            if (!speakReply(message)) {
                Log.w(TAG, "Safety TTS unavailable: ${offlineTts.lastDetail.orEmpty()}")
                if (cue == VoiceFeedbackCue.ERROR) feedbackAudio.play(cue)
            }
        } else {
            feedbackAudio.play(cue)
        }
    }

    private fun broadcastRecognition(text: String, isFinal: Boolean) {
        if (!canPublishSessionResult()) return
        VoiceServiceEventBus.sendBroadcast(
            this,
            Intent(ACTION_RECOGNITION_TEXT).apply {
                putExtra(EXTRA_RECOGNIZED_TEXT, text)
                putExtra(EXTRA_RECOGNITION_FINAL, isFinal)
            }
        )
    }

    private fun broadcastStatus(rawMessage: String, sherpaReady: Boolean) {
        if (!canPublishSessionResult()) return
        val message = com.voicegeely.app.ui.PublicText.scrub(rawMessage)
        val ttsDetail = ttsDetailForUi()?.let { com.voicegeely.app.ui.PublicText.scrub(it) }
        lastReadiness = com.voicegeely.app.ui.EngineReadiness(sherpaReady, wakeReady, ttsReadyForUi(), ttsDetail)
        VoiceServiceEventBus.sendBroadcast(
            this,
            Intent(ACTION_ENGINE_STATUS).apply {
                putExtra(EXTRA_STATUS_MESSAGE, message)
                putExtra(EXTRA_SHERPA_READY, sherpaReady)
                putExtra(EXTRA_TTS_READY, ttsReadyForUi())
                putExtra(EXTRA_TTS_DETAIL, ttsDetail)
                putExtra(EXTRA_WAKE_READY, wakeReady)
            }
        )
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(com.tht.voice.R.drawable.ic_stat_tht)
        .setContentTitle("THT Voice")
        .setContentText(com.voicegeely.app.ui.PublicText.scrub(content))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun postPermissionNotification() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, PERMISSION_CHANNEL_ID)
            .setSmallIcon(com.tht.voice.R.drawable.ic_stat_tht)
            .setContentTitle("THT Voice cần quyền micrô")
            .setContentText("Nhấn để mở cài đặt quyền")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(PERMISSION_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "THT Voice", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(PERMISSION_CHANNEL_ID, "Quyền THT Voice", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun lockVoiceCore(reason: String) {
        Log.i(TAG, "voice_core_locked reason=$reason gate=${licenseManager.gateState()}")
        serviceReady = false
        stopWakeWordListening(waitForRelease = false)
        voiceOverlay.hide()
        broadcastStatus("THT Voice chưa được kích hoạt", sherpaReady = asrReady())
        stopSelf()
    }

    /** Background server check (renewal / remote block), throttled by [LicenseSessionPolicy]. */
    private fun refreshRemoteLicenseIfDue() {
        if (!licenseManager.remoteRefreshDue()) return
        if (!remoteLicenseCheckRunning.compareAndSet(false, true)) return
        Thread {
            try {
                val remote = licenseManager.refreshRemoteStatus()
                Log.i(TAG, "license_remote_refresh checked=${remote.checked} active=${remote.active} blocked=${remote.blocked}")
                if (remote.checked && !licenseManager.isLicensed()) lockVoiceCore("remote_refresh")
            } catch (t: Throwable) {
                Log.w(TAG, "license_remote_refresh failed type=${t.javaClass.simpleName}")
            } finally {
                remoteLicenseCheckRunning.set(false)
            }
        }.apply { name = "THTLicenseRefresh"; isDaemon = true }.start()
    }

    /** Offline Kokoro first; the Android system voice (Vietnamese) only as a backup. */
    /** A forced update was published and this version is older: hold the assistant while parked only. */
    private fun requiredUpdateHoldsAssistant(): Boolean {
        val store = com.voicegeely.app.update.UpdatePromptStore(this)
        val required = com.voicegeely.app.update.UpdatePromptPolicy.isRequired(
            store.requiredVersionCode, com.tht.voice.BuildConfig.VERSION_CODE.toLong()
        )
        if (!required) return false
        val parked = runCatching {
            safetyGate.evaluateParkedOnly("Trợ lý giọng nói").type == com.voicegeely.app.voice.safety.SafetyDecisionType.ALLOW
        }.getOrDefault(false)
        return com.voicegeely.app.update.UpdatePromptPolicy.blockAssistant(required, parked)
    }

    private fun speakReply(text: String): Boolean {
        if (!canPublishSessionResult()) return true
        // Cut short by the voice key: say nothing more, a new listening session follows.
        if (bargeInRequested) return true
        val voice = voicePreferences.ttsVoice
        val generation = sessionThreadGeneration.get()
        return boundedReply.speak { timedOut ->
            val cancelled = { timedOut() || bargeInRequested || destroyed || (generation != null && !sessionActive(generation)) }
            if (cancelled()) return@speak true
            replySpeaking = true
            try {
                if (mp3Reply.speak(text, voice, cancelled) || cancelled()) true
                else if (offlineTts.speak(text, voice) || cancelled()) true
                else systemTts.speak(text)
            } finally {
                replySpeaking = false
            }
        }
    }

    /** Audio just before the session starts (THTVoice): the first word after the wake phrase is not cut. */
    private val preRoll = com.voicegeely.app.asr.PcmPreRoll()
    /** Speech spoken too loud / too close to the mic (THTVoice). */
    private val clippingMeter = com.voicegeely.app.asr.ClippingMeter()
    /** Last session's speech was clipped (too loud). */
    @Volatile private var lastSessionClipped = false

    /** The coordinator's last reply could not be spoken by any voice. */
    @Volatile private var lastReplyUnspoken = false

    /** True while a reply is being read; a mic opened then (barge-in) gets the echo canceller. */
    @Volatile private var replySpeaking = false

    private fun ttsReadyForUi(): Boolean =
        Mp3VoicePacks.isReady(voicePreferences.ttsVoice) || offlineTts.isReady || systemTts.isReady

    private fun ttsDetailForUi(): String? = when {
        Mp3VoicePacks.isReady(voicePreferences.ttsVoice) -> "Gói giọng MP3 đã sẵn sàng"
        offlineTts.isReady -> offlineTts.lastDetail
        systemTts.isReady -> "Đang dùng giọng hệ thống (giọng đọc THT: ${offlineTts.lastDetail ?: "chưa sẵn sàng"})"
        else -> offlineTts.lastDetail ?: systemTts.detail
    }

    /** "Thử giọng" from Settings: speak a sample and report exactly what worked. */
    private fun runVoiceTest(voice: TtsVoice) {
        if (recognitionRunning.get() || !voiceTestRunning.compareAndSet(false, true)) return
        val generation = sessionGeneration
        val cancelled = { destroyed || recognitionRunning.get() || generation != sessionGeneration }
        Thread {
            try {
                val sample = "Trợ lý đang sẵn sàng, bạn cứ nói lệnh."
                val mp3Ok = mp3Reply.speak(sample, voice, cancelled)
                if (cancelled()) return@Thread
                val kokoroOk = !mp3Ok && offlineTts.speak(sample, voice)
                if (cancelled()) return@Thread
                val systemOk = !mp3Ok && !kokoroOk && systemTts.speak(sample)
                if (cancelled()) return@Thread
                val detail = when {
                    mp3Ok -> "Đã phát từ gói giọng MP3."
                    kokoroOk -> "Đã phát bằng giọng đọc THT dự phòng."
                    systemOk -> "Đã phát bằng giọng hệ thống dự phòng."
                    else -> "Chưa phát được giọng. Kiểm tra gói đã cài và âm lượng."
                }
                val ok = mp3Ok || kokoroOk || systemOk
                VoiceServiceEventBus.sendBroadcast(this, Intent(ACTION_TTS_TEST_RESULT).apply {
                    putExtra(EXTRA_TTS_TEST_OK, ok)
                    putExtra(EXTRA_TTS_TEST_DETAIL, detail)
                })
                broadcastStatus(detail, sherpaReady = asrReady())
            } finally {
                voiceTestRunning.set(false)
            }
        }.apply { name = "THTVoice-VoiceTest"; isDaemon = true }.start()
    }

    /**
     * The steering-wheel voice key arrives as an implicit broadcast. A receiver registered at
     * runtime gets it whichever app is on screen; the manifest receiver alone may not on Android 8+.
     */
    private fun registerVoiceKeyReceiver() {
        if (voiceKeyReceiver != null) return
        val receiver = VoiceKeyReceiver()
        runCatching {
            registerReceiver(receiver, android.content.IntentFilter().apply {
                VoiceKeyActions.ALL.forEach { addAction(it) }
                priority = 999
            })
            voiceKeyReceiver = receiver
            Log.i(TAG, "voice_key_runtime_receiver=REGISTERED")
        }.onFailure { Log.w(TAG, "voice_key_runtime_receiver_failed type=${it.javaClass.simpleName}") }
    }

    /** [S06] Car value before a fan/temperature command (for "về như cũ"); null when unreadable. */
    private fun readFollowUpSnapshot(family: com.voicegeely.app.voice.FollowUpContext.Family): com.voicegeely.app.voice.FollowUpContext.Snapshot? {
        val access = vehicleAccess ?: return null
        return when (family) {
            com.voicegeely.app.voice.FollowUpContext.Family.FAN ->
                access.getIntPropertyOrNull(com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_SPEED, com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_AREA)
                    ?.takeIf { it in com.voicegeely.app.car.CoolrayProperties.HVAC_MIN_FAN..com.voicegeely.app.car.CoolrayProperties.HVAC_MAX_FAN }
                    ?.let { com.voicegeely.app.voice.FollowUpContext.Snapshot.Fan(it) }
            com.voicegeely.app.voice.FollowUpContext.Family.TEMPERATURE ->
                access.getFloatProperty(com.voicegeely.app.car.CoolrayProperties.HVAC_TEMP_SET, com.voicegeely.app.car.CoolrayProperties.HVAC_TEMP_AREA)
                    .takeIf { it.isFinite() && it in com.voicegeely.app.car.CoolrayProperties.HVAC_MIN_TEMP..com.voicegeely.app.car.CoolrayProperties.HVAC_MAX_TEMP }
                    ?.let { com.voicegeely.app.voice.FollowUpContext.Snapshot.Temperature(it) }
            com.voicegeely.app.voice.FollowUpContext.Family.VOLUME -> null
        }
    }

    private var ecarxAccess: com.voicegeely.app.car.EcarxFunctionAccess? = null

    /** ECARX AdaptAPI (body functions), created once per service; null when the head unit has none. */
    private fun ecarxPort(): com.voicegeely.app.car.EcarxFunctionPort? {
        ecarxAccess?.let { return it }
        return com.voicegeely.app.car.EcarxFunctionAccess.create(this).fold(
            onSuccess = { access ->
                Log.i(TAG, "ECARX AdaptAPI: ${access.probe()}")
                ecarxAccess = access
                access
            },
            onFailure = {
                Log.w(TAG, "ECARX AdaptAPI unavailable: ${it.javaClass.simpleName} ${it.message}")
                null
            }
        )
    }

    /** Safety gate + router for one car connection (null = no vehicle backend). */
    private fun buildVehicleStack(access: VehiclePropertyAccess?): VoiceCommandRouter {
        safetyGate = VehicleSafetyGate(access)
        val headUnit = CoolrayHeadUnit.detect()
        Log.i(TAG, "Coolray head unit: ${headUnit ?: "not detected (vehicle writes blocked)"}")
        val ecarx = if (access != null && headUnit != null) {
            com.voicegeely.app.car.DeferredEcarxFunctionPort(
                connect = ::ecarxPort,
                clock = { android.os.SystemClock.elapsedRealtime() }
            )
        } else null
        val commandAccess = if (access != null && ecarx != null) {
            com.voicegeely.app.car.EcarxHvacPropertyAccess(access, ecarx)
        } else access
        vehicleAccess = commandAccess
        return VoiceCommandRouter(
            headUnitSupported = headUnit != null,
            ecarx = ecarx,
            vehicle = commandAccess,
            androidBackend = AndroidSystemActionBackend(this, parkedGate = safetyGate),
            mediaResolver = MediaPreferenceResolver(mediaSettings),
            navigationResolver = NavigationPreferenceResolver(voicePreferences)
        )
    }

    /**
     * [REF-10 / S09] Screen on after sleep: re-check the car connection (new backoff chain), the
     * voice-key paths and the wake-word mic. Every step is idempotent, so repeated screen-on
     * events never stack connections, receivers or mic owners.
     */
    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                Log.i(TAG, "wake_recover reason=${intent?.action}")
                registerVoiceKeyReceiver()
                startVehicleVoiceKeyWatch()
                checkVehicleConnection("screen_on", newChain = true)
                recoverWakeAfterSleep(intent?.action ?: "screen_on")
            }
        }
        runCatching {
            registerReceiver(receiver, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            })
            screenOnReceiver = receiver
        }.onFailure { Log.w(TAG, "screen_on_receiver_failed type=${it.javaClass.simpleName}") }
    }

    private fun checkVehicleConnection(reason: String, newChain: Boolean) {
        val telemetry = com.voicegeely.app.car.VehicleTelemetryRegistry.snapshot()
        if (com.voicegeely.app.car.VehicleReconnectPolicy.needsReconnect(vehicleAccess != null, telemetry.state, telemetry.detail)) {
            scheduleVehicleReconnect(reason, newChain)
        }
    }

    private fun scheduleVehicleReconnect(reason: String, newChain: Boolean) {
        if (destroyed) return
        if (newChain) vehicleReconnectAttempt = 0
        if (vehicleReconnectScheduled) return
        val delay = com.voicegeely.app.car.VehicleReconnectPolicy.delayMs(vehicleReconnectAttempt) ?: run {
            Log.w(TAG, "vehicle_reconnect gave_up reason=$reason attempts=$vehicleReconnectAttempt")
            return
        }
        vehicleReconnectScheduled = true
        Log.i(TAG, "vehicle_reconnect scheduled in=${delay}ms attempt=$vehicleReconnectAttempt reason=$reason")
        mainHandler.postDelayed({
            vehicleReconnectScheduled = false
            if (!destroyed) reconnectVehicle(reason)
        }, delay)
    }

    /** One connection attempt off the main thread; swaps the stack only when the new backend is up. */
    private fun reconnectVehicle(reason: String) {
        if (!vehicleReconnectRunning.compareAndSet(false, true)) return
        vehicleReconnectAttempt += 1
        thread(name = "THTVoice-CarReconnect", start = true) {
            try {
                val fresh = AndroidCarVehiclePropertyAccess.create(this)
                mainHandler.post {
                    if (destroyed) { runCatching { fresh?.disconnect() }; return@post }
                    if (fresh == null) {
                        Log.w(TAG, "vehicle_reconnect failed attempt=$vehicleReconnectAttempt reason=$reason")
                        scheduleVehicleReconnect(reason, newChain = false)
                        return@post
                    }
                    stopVehicleVoiceKeyWatch()
                    val old = vehicleAccess
                    vehicleAccess = fresh
                    commandExecutor.delegate = buildVehicleStack(fresh)
                    runCatching { old?.disconnect() }
                    startVehicleVoiceKeyWatch()
                    vehicleReconnectAttempt = 0
                    Log.i(TAG, "vehicle_reconnect ok reason=$reason")
                }
            } finally {
                vehicleReconnectRunning.set(false)
            }
        }
    }

    /**
     * [REF-02 / S02] Read-only watch of the car's voice-key property. Idempotent. Raw values are
     * logged (tag THTVoiceECARX) so the press/hold meaning can be confirmed on the car; a decoded
     * short press goes through [VoiceKeyLauncher] like the broadcast, so the shared debounce keeps
     * one session per press when both paths fire.
     */
    /**
     * Coolray 2025 (GKUI): the steering-wheel voice key comes from ECARX's input service, not the
     * Flyme broadcast. Intercept KEYCODE_VOICE_ASSIST / KEYCODE_R_VOICE_ASSIST through AdaptAPI and
     * route each press through [VoiceKeyLauncher] exactly like the broadcast (same debounce, license
     * and projection rules). Idempotent; released in [stopVehicleVoiceKeyWatch].
     */
    private fun startVehicleVoiceKeyWatch() {
        if (vehicleVoiceKeyWatch != null) return
        com.voicegeely.app.car.EcarxKeyInterceptor.start(this) { code ->
            Log.i(TAG, "voice_key_adaptapi code=$code")
            VoiceKeyLauncher.handle(this, VoiceKeyActions.ECARX, source = "adaptapi")
        }.fold(
            onSuccess = { interceptor -> vehicleVoiceKeyWatch = { interceptor.release() } },
            onFailure = { Log.w(TAG, "voice_key_adaptapi_unavailable ${it.javaClass.simpleName} ${it.message}") }
        )
    }

    private fun stopVehicleVoiceKeyWatch() {
        vehicleVoiceKeyWatch?.invoke()
        vehicleVoiceKeyWatch = null
    }

    /**
     * Voice key while the Voice core already runs: open the session inside this process.
     *
     * Calling startForegroundService()/startService() again from the background makes Android 12+
     * re-evaluate the service's "while in use" microphone access and drop it (the mic then records
     * silence outside THT Voice), and the old wake-bridge activity brought the THT Voice window to
     * the front. The in-process path keeps the access granted when the service was started from
     * the app, shows only the popup and leaves the current app on screen.
     */
    private fun handleVoiceKeyInProcess() {
        if (!licenseManager.isLicensed()) {
            lockVoiceCore("voice_key")
            return
        }
        refreshRemoteLicenseIfDue()
        handleRecognitionTrigger()
    }

    override fun onDestroy() {
        sessionDeadline.cancel()
        sessionGeneration++
        recognitionThread?.interrupt()
        destroyed = true
        cancelWakeTraining()
        com.voicegeely.app.voice.AirflowDirectionCycler.stop()
        if (instance === this) instance = null
        stopVehicleVoiceKeyWatch()
        micFrontEndInstance?.release()
        micFrontEndInstance = null
        screenOnReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenOnReceiver = null
        voiceKeyReceiver?.let { runCatching { unregisterReceiver(it) } }
        voiceKeyReceiver = null
        serviceReady = false
        lastReadiness = null
        recognitionRunning.set(false)
        reopenForSafetyConfirmation = false
        pendingSafetyCommands.clear()
        stopWakeWordListening(waitForRelease = false)
        closeRecorder(audioRecord)
        closeRecorder(wakeAudioRecord)
        micState.close()
        voiceOverlay.close()
        val closingWake = wakeWordEngine
        if (wakeWordThread?.isAlive == true) {
            thread(name = "THTVoice-KWS-Close", isDaemon = true) { runCatching { closingWake.close() } }
        } else runCatching { closingWake.close() }
        if (::asrEngine.isInitialized) try { asrEngine.close() } catch (_: Throwable) {}
        if (::mp3Reply.isInitialized) mp3Reply.interrupt()
        boundedReply.close()
        try { offlineTts.close() } catch (_: Throwable) {}
        try { systemTts.close() } catch (_: Throwable) {}
        try { audioDucker.release() } catch (_: Throwable) {}
        try { feedbackAudio.close() } catch (_: Throwable) {}
        try { vehicleAccess?.disconnect() } catch (_: Throwable) {}
        vehicleAccess = null
        Log.i(TAG, "THT Voice service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_WAKE_TRAIN = "com.tht.voice.WAKE_TRAIN"
        const val ACTION_WAKE_TRAIN_CANCEL = "com.tht.voice.WAKE_TRAIN_CANCEL"
        const val ACTION_WAKE_TRAIN_STATUS = "com.tht.voice.WAKE_TRAIN_STATUS"
        const val EXTRA_WAKE_TRAIN_STATE = "wake_train_state"
        const val EXTRA_WAKE_TRAIN_TEXT = "wake_train_text"
        const val EXTRA_WAKE_TRAIN_NAME = "wake_train_name"
        const val EXTRA_WAKE_TRAIN_MUSIC = "wake_train_music"
        const val ACTION_SERVICE_READY = "com.tht.voice.SERVICE_READY"
        const val ACTION_ENGINE_STATUS = "com.tht.voice.ENGINE_STATUS"
        const val ACTION_RECOGNITION_TEXT = "com.tht.voice.RECOGNITION_TEXT"
        const val ACTION_TRIGGER_RECOGNITION = "com.tht.voice.TRIGGER_RECOGNITION"
        const val ACTION_SETTINGS_CHANGED = "com.tht.voice.SETTINGS_CHANGED"
        const val ACTION_ECARX_KEY = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"
        const val EXTRA_STATUS_MESSAGE = "status_message"
        const val EXTRA_SHERPA_READY = "sherpa_ready"
        const val EXTRA_TTS_READY = "tts_ready"
        const val EXTRA_TTS_DETAIL = "tts_detail"
        const val ACTION_TEST_VOICE = "com.tht.voice.action.TEST_VOICE"
        const val EXTRA_TTS_TEST_VOICE = "tts_test_voice"
        const val ACTION_TTS_TEST_RESULT = "com.tht.voice.action.TTS_TEST_RESULT"
        const val EXTRA_TTS_TEST_OK = "tts_test_ok"
        const val EXTRA_TTS_TEST_DETAIL = "tts_test_detail"
        const val EXTRA_WAKE_READY = "wake_ready"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        const val EXTRA_RECOGNITION_FINAL = "recognition_final"

        private const val TAG = "THTVoiceService"
        /** Wake-word loop checks for calls / silencing about once a second. */
        private const val KWS_YIELD_CHECK_CHUNKS = 10
        private const val CHANNEL_ID = "tht_voice_service"
        private const val PERMISSION_CHANNEL_ID = "tht_voice_permission"
        private const val NOTIFICATION_ID = 1000
        private const val PERMISSION_NOTIFICATION_ID = 1001
        private const val CHUNK_SAMPLES = 1600
        /** Audio kept from before the session (THTVoice): the first word after the wake phrase. */
        private const val HANDOFF_LEAD_MS = 400
        private const val REPAUSE_DELAY_MS = 300L
        private const val SAFETY_REOPEN_DELAY_MS = 180L
        private const val BARGE_IN_REOPEN_DELAY_MS = 120L
        private const val KWS_STOP_JOIN_MS = 700L

        @Volatile private var serviceReady = false
        fun isServiceReady(): Boolean = serviceReady

        @Volatile private var instance: VoiceAssistantService? = null
        private val mainThread = android.os.Handler(android.os.Looper.getMainLooper())

        /** True when a running Voice core took the voice key in-process (no service restart, no activity). */
        fun triggerFromVoiceKey(): Boolean {
            val service = instance?.takeIf { serviceReady } ?: return false
            mainThread.post { service.handleVoiceKeyInProcess() }
            return true
        }

        /** Broadcasts are one-shot; a screen opened later reads the last published readiness here. */
        @Volatile private var lastReadiness: com.voicegeely.app.ui.EngineReadiness? = null
        fun engineReadiness(): com.voicegeely.app.ui.EngineReadiness? = if (serviceReady) lastReadiness else null
    }
}
