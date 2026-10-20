package com.voicegeely.app.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.voicegeely.app.voice.safety.SafetyDecisionType
import com.voicegeely.app.voice.safety.VehicleSafetyGate

/** Production Android backend. It never falls back to an arbitrary browser. */
class AndroidSystemActionBackend(
    context: Context,
    private val mediaSessionSnapshots: MediaSessionSnapshotProvider =
        AndroidMediaSessionSnapshotProvider(context),
    private val parkedGate: VehicleSafetyGate = VehicleSafetyGate(null)
) : AndroidActionBackend {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun setManualDisplayBrightness(percent: Int): AndroidBackendResult {
        val level = DisplayBrightnessReading.levelForPercent(percent)
            ?: return AndroidBackendResult(false, false, detail = "Mức sáng phải từ 1 đến 100 phần trăm.")
        val decision = parkedGate.evaluateParkedOnly("Đổi độ sáng hệ thống")
        if (decision.type != SafetyDecisionType.ALLOW) {
            return AndroidBackendResult(false, false, detail = decision.reason ?: "Chỉ đổi độ sáng khi xe đang đỗ ở số P.")
        }
        return try {
            if (!Settings.System.canWrite(appContext)) {
                return AndroidBackendResult(false, false, detail = "Ứng dụng chưa có quyền sửa thiết lập hệ thống.")
            }
            val resolver = appContext.contentResolver
            val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            if (mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                return AndroidBackendResult(false, false, detail = "Hãy chuyển màn hình sang độ sáng thủ công trước.")
            }
            // Recheck vehicle state just before the system write.
            val recheck = parkedGate.evaluateParkedOnly("Đổi độ sáng hệ thống")
            if (recheck.type != SafetyDecisionType.ALLOW) {
                return AndroidBackendResult(false, false, detail = recheck.reason ?: "Xe không còn ở trạng thái đỗ.")
            }
            if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)) {
                return AndroidBackendResult(false, false, detail = "Android từ chối ghi mức sáng.")
            }
            val actualMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            val actualLevel = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            val confirmed = actualMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL && actualLevel == level
            Log.i(TAG, "op=DISPLAY_BRIGHTNESS_WRITE percent=$percent result=${if (confirmed) "CONFIRMED" else "UNVERIFIED"}")
            AndroidBackendResult(
                accepted = true,
                confirmed = confirmed,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = if (confirmed) "$percent phần trăm" else null,
                detail = if (confirmed) null else "Thiết lập độ sáng đọc lại chưa khớp."
            )
        } catch (_: Settings.SettingNotFoundException) {
            AndroidBackendResult(false, false, detail = "Không đọc được thiết lập độ sáng hệ thống.")
        } catch (t: SecurityException) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS_WRITE result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép sửa độ sáng hệ thống.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS_WRITE result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không ghi được mức sáng hệ thống.")
        }
    }

    override fun queryDisplayBrightness(): AndroidBackendResult {
        return try {
            val resolver = appContext.contentResolver
            val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            val manualLevel = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) else null
            val description = DisplayBrightnessReading.describe(mode, manualLevel)
                ?: return AndroidBackendResult(false, false, detail = "Giá trị độ sáng hệ thống không hợp lệ.")
            Log.i(TAG, "op=DISPLAY_BRIGHTNESS result=READ mode=$mode")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = description
            )
        } catch (_: Settings.SettingNotFoundException) {
            AndroidBackendResult(false, false, detail = "Không tìm thấy thiết lập độ sáng hệ thống.")
        } catch (t: SecurityException) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép đọc độ sáng hệ thống.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đọc được thiết lập độ sáng hệ thống.")
        }
    }

    override fun queryWifiSsid(): AndroidBackendResult {
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            return AndroidBackendResult(false, false, detail = "Cần cấp quyền vị trí chính xác để đọc tên mạng Wi-Fi.")
        }
        val location = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return AndroidBackendResult(false, false, detail = "Không kiểm tra được dịch vụ vị trí.")
        return try {
            if (!location.isLocationEnabled) {
                return AndroidBackendResult(false, false, detail = "Dịch vụ vị trí đang tắt; Android có thể che tên mạng Wi-Fi.")
            }
            val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return AndroidBackendResult(false, false, detail = "Không có dịch vụ mạng.")
            val network = connectivity.activeNetwork
                ?: return AndroidBackendResult(false, false, detail = "Không có mạng mặc định.")
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                return AndroidBackendResult(false, false, detail = "Wi-Fi không phải mạng mặc định hiện tại.")
            }
            val info = capabilities.transportInfo as? WifiInfo
            val name = WifiSsidReading.visibleName(info?.ssid)
                ?: return AndroidBackendResult(false, false, detail = "Android không cung cấp tên mạng Wi-Fi.")
            Log.i(TAG, "op=WIFI_SSID result=READ nameAvailable=true")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = "Tên mạng Wi-Fi mặc định là $name"
            )
        } catch (t: SecurityException) {
            Log.w(TAG, "op=WIFI_SSID result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép đọc tên mạng Wi-Fi.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=WIFI_SSID result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đọc được tên mạng Wi-Fi.")
        }
    }

    override fun queryWifiIpAddress(): AndroidBackendResult {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return AndroidBackendResult(false, false, detail = "Thiết bị không có dịch vụ mạng.")
        return try {
            val network = connectivity.activeNetwork
                ?: return AndroidBackendResult(false, false, detail = "Không có mạng mặc định để đọc IP Wi-Fi.")
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                return AndroidBackendResult(false, false, detail = "Wi-Fi không phải mạng mặc định hiện tại; chưa xác định được IP Wi-Fi.")
            }
            val addresses = connectivity.getLinkProperties(network)?.linkAddresses?.map { it.address }.orEmpty()
            val ip = WifiLinkAddressSelector.firstIpv4(addresses)
                ?: return AndroidBackendResult(false, false, detail = "Không đọc được địa chỉ IPv4 của mạng Wi-Fi mặc định.")
            Log.i(TAG, "op=WIFI_IP result=READ ipv4Available=true")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = "IP nội bộ của mạng Wi-Fi mặc định là $ip"
            )
        } catch (t: SecurityException) {
            Log.w(TAG, "op=WIFI_IP result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép đọc IP Wi-Fi.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=WIFI_IP result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đọc được IP Wi-Fi.")
        }
    }

    override fun queryWifiState(): AndroidBackendResult {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return AndroidBackendResult(false, false, detail = "Thiết bị không có dịch vụ Wi-Fi.")
        return try {
            val enabled = wifi.isWifiEnabled
            Log.i(TAG, "op=WIFI_STATE result=READ enabled=$enabled")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = if (enabled) "Wi-Fi đang bật" else "Wi-Fi đang tắt"
            )
        } catch (t: SecurityException) {
            Log.w(TAG, "op=WIFI_STATE result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép đọc trạng thái Wi-Fi.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=WIFI_STATE result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đọc được trạng thái Wi-Fi.")
        }
    }

    override fun queryWindowingCapability(mode: WindowingMode): AndroidBackendResult {
        if (mode == WindowingMode.SPLIT_SCREEN) {
            return AndroidBackendResult(false, false,
                detail = "Chưa có API hoặc hợp đồng OEM để xác nhận quyền chia đôi ứng dụng trên màn hình này.")
        }
        return try {
            val feature = when (mode) {
                WindowingMode.PIP -> PackageManager.FEATURE_PICTURE_IN_PICTURE
                WindowingMode.FREEFORM -> PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
                WindowingMode.SPLIT_SCREEN -> error("Handled above")
            }
            val label = if (mode == WindowingMode.PIP) "PiP" else "cửa sổ tự do"
            val declared = packageManager.hasSystemFeature(feature)
            Log.i(TAG, "op=WINDOWING_PROBE mode=$mode featureDeclared=$declared")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = if (declared)
                    "Android khai báo hỗ trợ $label; chưa xác minh ứng dụng mục tiêu hoặc quyền mở cửa sổ"
                else "Android không khai báo hỗ trợ $label trên thiết bị này"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "op=WINDOWING_PROBE mode=$mode result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không kiểm tra được tính năng cửa sổ Android.")
        }
    }

    override fun querySceneCapability(scene: SceneType): AndroidBackendResult {
        val packageName = "com.flyme.auto.scenedirector"
        val activityName = when (scene) {
            SceneType.REST -> "com.flyme.auto.scenedirector.activities.nap.RestActivity"
            SceneType.CAMPING -> "com.flyme.auto.scenedirector.activities.camping.CampingActivity"
        }
        val label = if (scene == SceneType.REST) "nghỉ" else "cắm trại"
        return try {
            val activity = packageManager.getActivityInfo(ComponentName(packageName, activityName), 0)
            val permissionGranted = activity.permission.isNullOrBlank() ||
                appContext.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED
            val available = activity.exported && permissionGranted && activity.enabled
            // The provider is an archive lead only. We do not query its undocumented schema.
            val providerVisible = packageManager.resolveContentProvider(packageName, 0) != null
            Log.i(TAG, "op=SCENE_PROBE scene=$scene activityVisible=true exported=${activity.exported} " +
                "permissionGranted=$permissionGranted providerVisible=$providerVisible available=$available")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                actualValue = if (available)
                    "Có lối mở Android cho chế độ $label; trạng thái scene và khả năng điều khiển chưa được xác minh"
                else "Chưa có quyền mở chế độ $label từ ứng dụng này"
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Log.i(TAG, "op=SCENE_PROBE scene=$scene result=ACTIVITY_NOT_VISIBLE")
            AndroidBackendResult(true, true, actualValue = "Không tìm thấy lối mở Android cho chế độ $label")
        } catch (t: SecurityException) {
            Log.w(TAG, "op=SCENE_PROBE scene=$scene result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép kiểm tra chế độ $label.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=SCENE_PROBE scene=$scene result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không kiểm tra được chế độ $label.")
        }
    }

    /**
     * Always try the launch (the Xiaomi app-op check can be wrong); when Xiaomi reports its
     * background-start permission as off, do not claim "Đã mở" but say it may have been blocked.
     */
    private fun mayBeBlockedByPhone(op: String, started: AndroidBackendResult, blocked: Boolean): AndroidBackendResult {
        if (!blocked || !started.accepted) return started
        Log.w(TAG, "op=$op result=STARTED_BUT_MIUI_BACKGROUND_START_OFF")
        return started.copy(confirmed = false, detail = VoiceResponseFormatter.BACKGROUND_LAUNCH_BLOCKED)
    }

    override fun openApp(packageCandidates: List<String>): AndroidBackendResult {
        val blocked = BackgroundLaunchPolicy.launchBlocked(appContext)
        return mayBeBlockedByPhone("OPEN_APP", openAppNow(packageCandidates), blocked)
    }

    private fun openAppNow(packageCandidates: List<String>): AndroidBackendResult {
        for (packageName in packageCandidates.distinct()) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.d(TAG, "op=OPEN_APP package=$packageName result=NO_LAUNCH_INTENT")
                continue
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                appContext.startActivity(launchIntent)
                Log.i(TAG, "op=OPEN_APP package=$packageName result=STARTED")
                return AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = packageName,
                    detail = "Explicit launch intent started for $packageName"
                )
            } catch (t: Throwable) {
                Log.w(TAG, "op=OPEN_APP package=$packageName result=FAILED type=${t.javaClass.simpleName}")
                // Try the next explicit candidate instead of opening a browser.
            }
        }
        Log.i(TAG, "op=OPEN_APP result=NO_TARGET")
        return AndroidBackendResult(false, false, detail = "Không tìm thấy ứng dụng phù hợp đã cài đặt.")
    }

    override fun openUri(uri: String, packageCandidates: List<String>): AndroidBackendResult {
        val blocked = BackgroundLaunchPolicy.launchBlocked(appContext)
        return mayBeBlockedByPhone("OPEN_URI", openUriNow(uri, packageCandidates), blocked)
    }

    private fun openUriNow(uri: String, packageCandidates: List<String>): AndroidBackendResult {
        val parsed = try { Uri.parse(uri) } catch (t: Throwable) {
            Log.w(TAG, "op=OPEN_URI result=INVALID_URI type=${t.javaClass.simpleName}")
            return AndroidBackendResult(false, false, detail = "URI không hợp lệ.")
        }
        for (packageName in packageCandidates.distinct()) {
            val intent = Intent(Intent.ACTION_VIEW, parsed)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) == null) {
                Log.d(TAG, "op=OPEN_URI package=$packageName result=NOT_RESOLVED")
                continue
            }
            try {
                appContext.startActivity(intent)
                Log.i(TAG, "op=OPEN_URI package=$packageName result=STARTED")
                return AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = packageName,
                    detail = "Explicit VIEW intent started for $packageName"
                )
            } catch (t: Throwable) {
                Log.w(TAG, "op=OPEN_URI package=$packageName result=FAILED type=${t.javaClass.simpleName}")
                // Try next candidate. No generic browser fallback here.
            }
        }
        Log.i(TAG, "op=OPEN_URI result=NO_TARGET")
        return AndroidBackendResult(false, false, detail = "Không có ứng dụng mục tiêu nào nhận được lệnh này.")
    }

    override fun media(action: MediaAction): AndroidBackendResult = when (action) {
        MediaAction.VOLUME_UP -> adjustVolume(action, AudioManager.ADJUST_RAISE)
        MediaAction.VOLUME_DOWN -> adjustVolume(action, AudioManager.ADJUST_LOWER)
        MediaAction.PLAY -> dispatchMediaKey(action, KeyEvent.KEYCODE_MEDIA_PLAY)
        MediaAction.PAUSE -> dispatchMediaKey(action, KeyEvent.KEYCODE_MEDIA_PAUSE)
        MediaAction.TOGGLE -> dispatchMediaKey(action, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        MediaAction.NEXT -> dispatchMediaKey(action, KeyEvent.KEYCODE_MEDIA_NEXT)
        MediaAction.PREVIOUS -> dispatchMediaKey(action, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        MediaAction.MUTE -> setMusicMuted(action, true)
        MediaAction.UNMUTE -> setMusicMuted(action, false)
    }

    /**
     * Mutes / unmutes the media stream and reads the mute state back. Assistant speech stays
     * audible while muted (owner decision 29/09/2026): see `tts/MutedSpeechGuard`.
     */
    private fun setMusicMuted(action: MediaAction, muted: Boolean): AndroidBackendResult {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
            val actual = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            Log.i(TAG, "op=MEDIA action=$action verify=MUTE expected=$muted actual=$actual")
            if (actual == muted) {
                AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = if (muted) "muted" else "unmuted"
                )
            } else {
                AndroidBackendResult(true, false, detail = "Đã gửi lệnh nhưng trạng thái tắt tiếng chưa đổi.")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "op=MEDIA action=$action verify=MUTE result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đổi được trạng thái tắt tiếng.")
        }
    }

    override fun setMediaVolume(value: Int, unit: VolumeUnit): AndroidBackendResult =
        writeMediaVolume("SET value=$value unit=$unit") { _, min, max ->
            MediaVolumeMath.target(value, unit, min, max)
        }

    override fun adjustMediaVolume(steps: Int): AndroidBackendResult =
        writeMediaVolume("ADJUST steps=$steps") { current, min, max -> MediaVolumeMath.adjust(current, steps, min, max) }

    /** Sets STREAM_MUSIC to the computed level and reads it back (actualValue = level read). */
    private fun writeMediaVolume(op: String, compute: (current: Int, min: Int, max: Int) -> Int?): AndroidBackendResult {
        return try {
            val stream = AudioManager.STREAM_MUSIC
            val max = audioManager.getStreamMaxVolume(stream)
            val min = runCatching { audioManager.getStreamMinVolume(stream) }.getOrDefault(0)
            val before = audioManager.getStreamVolume(stream)
            val target = compute(before, min, max)
                ?: return AndroidBackendResult(false, false, detail = "Âm lượng chỉ từ $min đến $max.").also {
                    Log.i(TAG, "op=MEDIA_VOLUME $op min=$min max=$max result=OUT_OF_RANGE")
                }
            if (target > 0 && runCatching { audioManager.isStreamMute(stream) }.getOrDefault(false)) {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            }
            audioManager.setStreamVolume(stream, target, 0)
            val after = audioManager.getStreamVolume(stream)
            Log.i(TAG, "op=MEDIA_VOLUME $op before=$before target=$target after=$after max=$max")
            if (after == target) {
                AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, actualValue = after.toString(),
                    detail = "Music stream volume read-back=$after/$max")
            } else {
                AndroidBackendResult(true, false, VerificationSource.ANDROID_STATE, actualValue = after.toString(),
                    detail = "Âm lượng đang ở mức $after.")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "op=MEDIA_VOLUME $op result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không chỉnh được âm lượng.")
        }
    }

    private fun adjustVolume(action: MediaAction, direction: Int): AndroidBackendResult {
        return try {
            val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Log.i(TAG, "op=MEDIA action=$action verify=VOLUME before=$before after=$current result=CONFIRMED")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = current.toString(),
                detail = "Music stream volume read-back=$current"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "op=MEDIA action=$action verify=VOLUME result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = t.message ?: "Không điều chỉnh được âm lượng.")
        }
    }

    private fun dispatchMediaKey(action: MediaAction, keyCode: Int): AndroidBackendResult {
        return try {
            val beforeActive = safeIsMusicActive()
            val trackChangeAction = action == MediaAction.NEXT || action == MediaAction.PREVIOUS
            val beforeTrack = if (trackChangeAction) safeMediaSessionSnapshot() else null
            Log.i(
                TAG,
                "op=MEDIA action=$action phase=DISPATCH beforeActive=$beforeActive " +
                    "sessionAvailable=${beforeTrack != null} package=${beforeTrack?.packageName ?: "none"}"
            )

            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))

            val expectedActive = MediaPlaybackStateVerifier.expectedActive(action, beforeActive)
            if (expectedActive != null && waitForMusicState(expectedActive)) {
                Log.i(TAG, "op=MEDIA action=$action verify=PLAYBACK_STATE expected=$expectedActive result=CONFIRMED")
                return AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = if (expectedActive) "playing" else "paused",
                    detail = "AudioManager.isMusicActive read-back=$expectedActive"
                )
            }

            if (trackChangeAction) {
                val afterTrack = waitForTrackChange(beforeTrack)
                if (afterTrack != null) {
                    Log.i(
                        TAG,
                        "op=MEDIA action=$action verify=TRACK_CHANGE package=${afterTrack.packageName} result=CONFIRMED"
                    )
                    return AndroidBackendResult(
                        accepted = true,
                        confirmed = true,
                        verificationSource = VerificationSource.MEDIA_SESSION,
                        actualValue = afterTrack.displayLabel(),
                        detail = "MediaSession metadata read-back confirmed track change in ${afterTrack.packageName}"
                    )
                }
            }

            val verification = when {
                expectedActive != null -> "PLAYBACK_STATE"
                beforeTrack != null -> "TRACK_CHANGE"
                else -> "NONE"
            }
            Log.i(TAG, "op=MEDIA action=$action verify=$verification result=ACCEPTED_UNVERIFIED")
            AndroidBackendResult(
                accepted = true,
                confirmed = false,
                verificationSource = when {
                    expectedActive != null -> VerificationSource.ANDROID_STATE
                    beforeTrack != null -> VerificationSource.MEDIA_SESSION
                    else -> VerificationSource.NONE
                },
                detail = when {
                    expectedActive != null ->
                        "Media key đã được gửi nhưng trạng thái âm thanh chưa xác nhận được."
                    beforeTrack == null ->
                        "Media key đã được gửi nhưng chưa đọc được MediaSession metadata để xác nhận bài đã đổi."
                    else ->
                        "Media key đã được gửi nhưng MediaSession chưa xác nhận bài đã đổi."
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "op=MEDIA action=$action result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = t.message ?: "Không gửi được media key.")
        }
    }

    private fun safeIsMusicActive(): Boolean? = try {
        audioManager.isMusicActive
    } catch (_: Throwable) {
        null
    }

    private fun safeMediaSessionSnapshot(): MediaSessionSnapshot? = try {
        mediaSessionSnapshots.current()
    } catch (_: Throwable) {
        null
    }

    private fun waitForMusicState(expected: Boolean): Boolean {
        repeat(MEDIA_VERIFY_ATTEMPTS) { attempt ->
            if (safeIsMusicActive() == expected) return true
            if (attempt < MEDIA_VERIFY_ATTEMPTS - 1) SystemClock.sleep(MEDIA_VERIFY_DELAY_MS)
        }
        return false
    }

    private fun waitForTrackChange(before: MediaSessionSnapshot?): MediaSessionSnapshot? {
        before ?: return null
        repeat(TRACK_VERIFY_ATTEMPTS) { attempt ->
            val after = safeMediaSessionSnapshot()
            if (MediaTrackChangeVerifier.changed(before, after)) return after
            if (attempt < TRACK_VERIFY_ATTEMPTS - 1) SystemClock.sleep(TRACK_VERIFY_DELAY_MS)
        }
        return null
    }

    private companion object {
        private const val TAG = "THTVoiceAndroid"
        const val MEDIA_VERIFY_ATTEMPTS = 7
        const val MEDIA_VERIFY_DELAY_MS = 120L
        const val TRACK_VERIFY_ATTEMPTS = 9
        const val TRACK_VERIFY_DELAY_MS = 150L
    }
}
