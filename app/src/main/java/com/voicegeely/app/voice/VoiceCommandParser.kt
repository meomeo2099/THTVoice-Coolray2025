package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties

/** Deterministic local parser for high-confidence vehicle and Android commands. */
object VoiceCommandParser {
    private const val CRACK_PERCENT = 10
    private val halfSetpoint = Regex("(?<!\\d)(1[5-9]|2\\d|3[0-2])\\s*(?:rưỡi|(?:phẩy|chấm)\\s*5)(?!\\d)")
    private val halfSetpointDigits = Regex("(?<!\\d)(1[5-9]|2\\d|3[0-2])[.,]5(?!\\d)")
    private val halfDegree = Regex("(?:^| )nửa độ(?: |$)")
    private val amountHalf = Regex("(?<!\\d)[1-5] độ rưỡi")
    private val temperatureContext = Regex("nhiệt độ|điều hòa|độ c|nóng hơn|lạnh hơn|mát hơn|ấm hơn")
    private val highTemp = Regex("(?:^| )(?:hi|cao nhất|tối đa|nóng nhất|hết cỡ)(?: |$)")
    private val lowTemp = Regex("(?:^| )(?:lo|thấp nhất|tối thiểu|lạnh nhất)(?: |$)")
    private val increase = Regex("tăng|nóng hơn|ấm hơn|lên")
    private val decrease = Regex("giảm|lạnh hơn|mát hơn|xuống")

    private val hvacContext = Regex("điều hòa|a c|ac|máy lạnh")
    private val explicitAcContext = Regex("(?:^| )(?:a c|ac)(?: |$)")
    private val explicitHvacPowerContext = Regex("nguồn điều hòa|hệ thống điều hòa|hvac")
    private val onVerb = Regex("bật|mở|khởi động|on")
    private val offVerb = Regex("tắt|đóng|dừng|ngắt|off")
    private val fanContext = Regex("quạt|tốc độ gió|mức gió")
    private val fanHigh = Regex("mạnh nhất|cao nhất|tối đa|hết cỡ")
    // Owner decision 29/09/2026: "nhỏ nhất" means the lowest running level (1), not off.
    private val fanLow = Regex("yếu nhất|thấp nhất|tối thiểu|nhỏ nhất|nhẹ nhất|êm nhất")
    private val recirculationInside = Regex("gió trong|lấy gió trong|tuần hoàn(?: gió)?|recirculation")
    private val recirculationOutside = Regex("gió ngoài|lấy gió ngoài|khí ngoài|fresh air")
    private val defrostContext = Regex(
        "sấy kính|sưởi kính|phá sương|defrost|(?:sấy|sưởi) gương|gương(?: chiếu hậu| xe)? bị mờ"
    )
    // Mirror heating shares the rear-defrost actuator; "kính hậu" / "chiếu hậu" are rear, not windshield.
    private val rear = Regex("kính sau|phía sau|đằng sau|kính hậu|chiếu hậu|gương")
    private val fanDirectionContext = Regex("hướng gió|thổi|gió vào mặt|gió xuống chân|gió lên kính")

    private val windowContext = Regex("kính|cửa sổ|cửa kính|kiếng")
    private val openWindow = Regex("mở|hạ|hé|kéo xuống")
    private val crackWindow = Regex("(?<![\\p{L}])hé(?![\\p{L}])")
    private val closeWindow = Regex("đóng|nâng|kéo lên")
    private val stopWindow = Regex("dừng|ngừng|đứng lại|stop")

    private val ambientContext = Regex("đèn viền|đèn ambient|ambient light|ambient")
    private val interiorDoorLightContext = Regex("đèn nội thất khi mở cửa|đèn trong xe khi mở cửa|đèn nội thất theo cửa")
    private val followMeHomeContext = Regex("follow me home|đèn chờ về nhà|đèn dẫn đường về nhà|đèn về nhà")
    private val wirelessChargerContext = Regex("sạc không dây|sạc wireless|wireless charger|sạc qi")
    private val displayThemeContext = Regex("giao diện|chủ đề|theme")
    private val parkingWiperContext = Regex("tự giảm gạt mưa khi đỗ|gạt mưa tự giảm khi đỗ|parking wiper reduction")
    private val parkingModeContext = Regex("chế độ đỗ xe|parking mode")
    private val brakeFeelContext = Regex("cảm giác phanh|chế độ bàn đạp phanh|brake pedal feel")
    private val comfortableStopContext = Regex("chế độ dừng êm|comfortable stop")
    private val chargeLimitContext = Regex("giới hạn dòng sạc|dòng sạc tối đa|charging current limit")

    private val openAppVerb = Regex("^(?:mở|bật|chạy|khởi động)\\s+")
    private val navigationContext = Regex("dẫn đường|chỉ đường|đi đến|đi tới|navigation")
    private val searchMediaVerb = Regex("^(?:tìm kiếm|tìm|phát|nghe|mở)\\s+")
    private val mediaProviderNames = "youtube music|yt music|youtube vanced|vanced|revanced|re vanced|morphe|youtube|you tube|smart ?tube|spotify|zing mp3|zing"

    fun parse(text: String): ParsedVoiceCommand? {
        CoolrayOemCommandParser.parse(text)?.let { return it }
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        MaxAcCommandParser.parse(text)?.let { return it }
        SunroofCommandParser.parse(text)?.let { return it }
        CoolrayLightCommandParser.parse(text)?.let { return it }
        SeatClimateCommandParser.parse(text)?.let { return it }
        SteeringWheelHeatCommandParser.parse(text)?.let { return it }
        DoorTrunkCommandParser.parse(text)?.let { return it }
        HeadlightHeightCommandParser.parse(text)?.let { return it }
        IntelligentHighBeamCommandParser.parse(text)?.let { return it }
        HvacEcoCommandParser.parse(text)?.let { return it }
        HvacAirflowAutoCycleCommandParser.parse(text)?.let { return it }
        SceneScreenCommandParser.parse(text)?.let { return it }
        AndroidLocalCommandParser.parse(text)?.let { return it }
        MediaVolumeParser.parse(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        if (MediaVolumeParser.outOfRange(normalized)) return null
        NaturalPhraseAliasParser.parse(text)?.let { return it }
        parseTemperature(normalized, text)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseHvacControl(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseAmbientLighting(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseConvenienceSettings(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseParkingMode(normalized, text)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseBrakeSettings(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseChargeLimit(normalized, text)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseDriving(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseWindow(normalized, text)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseManualBrightness(normalized, text)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseDisplayBrightness(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseWifiSsid(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseWifiIp(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseWifiState(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseWindowingCapability(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        if (unsupportedWindowingMutation(normalized)) return null
        parseSceneCapability(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseNavigation(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseMediaSearch(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseMediaControl(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        parseOpenApp(normalized)?.let { return ParsedVoiceCommand(text, normalized, it) }
        return null
    }

    private fun parseTemperature(text: String, originalText: String = text): VoiceCommand? {
        if (!temperatureContext.containsMatchIn(text)) return null
        if (highTemp.containsMatchIn(text)) return VoiceCommand.TemperatureHigh
        if (lowTemp.containsMatchIn(text)) return VoiceCommand.TemperatureLow
        // Coolray half-degree setpoints: "22 rưỡi", "22 phẩy 5", "22,5" / "22.5" (15.5..32.5 parsed;
        // the executor enforces the Coolray 15.5..28.5 range).
        (halfSetpoint.find(text) ?: halfSetpointDigits.find(originalText))?.let { match ->
            return VoiceCommand.SetTemperature(match.groupValues[1].toInt() + 0.5f)
        }
        val values = numbers(text)
        values.firstOrNull { it in 16..32 }?.let { return VoiceCommand.SetTemperature(it.toFloat()) }
        if (halfDegree.containsMatchIn(text) && values.isEmpty()) {
            return when {
                increase.containsMatchIn(text) -> VoiceCommand.AdjustTemperature(0.5f)
                decrease.containsMatchIn(text) -> VoiceCommand.AdjustTemperature(-0.5f)
                else -> null
            }
        }
        val amount = values.firstOrNull { it in 1..5 }?.toFloat()?.let { if (amountHalf.containsMatchIn(text)) it + 0.5f else it }
        return when {
            increase.containsMatchIn(text) -> VoiceCommand.AdjustTemperature(amount ?: CoolrayProperties.HVAC_TEMP_STEP)
            decrease.containsMatchIn(text) -> VoiceCommand.AdjustTemperature(-(amount ?: CoolrayProperties.HVAC_TEMP_STEP))
            else -> null
        }
    }

    private fun parseHvacControl(text: String): VoiceCommand? {
        if (defrostContext.containsMatchIn(text)) {
            val zone = if (rear.containsMatchIn(text)) DefrostZone.REAR else DefrostZone.FRONT
            return VoiceCommand.Defrost(zone, !offVerb.containsMatchIn(text))
        }
        if (recirculationInside.containsMatchIn(text)) return VoiceCommand.Recirculation(true)
        if (recirculationOutside.containsMatchIn(text)) return VoiceCommand.Recirculation(false)
        parseFanDirection(text)?.let { return it }
        parseFanSpeed(text)?.let { return it }
        if (explicitAcContext.containsMatchIn(text)) {
            return when {
                offVerb.containsMatchIn(text) -> VoiceCommand.AcPower(false, HvacPowerTarget.COMPRESSOR)
                onVerb.containsMatchIn(text) -> VoiceCommand.AcPower(true, HvacPowerTarget.COMPRESSOR)
                else -> null
            }
        }
        if (explicitHvacPowerContext.containsMatchIn(text)) {
            return when {
                offVerb.containsMatchIn(text) -> VoiceCommand.AcPower(false, HvacPowerTarget.SYSTEM)
                onVerb.containsMatchIn(text) -> VoiceCommand.AcPower(true, HvacPowerTarget.SYSTEM)
                else -> null
            }
        }
        if (hvacContext.containsMatchIn(text)) {
            return when {
                offVerb.containsMatchIn(text) -> VoiceCommand.AcPower(false)
                onVerb.containsMatchIn(text) -> VoiceCommand.AcPower(true)
                else -> null
            }
        }
        return null
    }

    private fun parseFanSpeed(text: String): VoiceCommand? {
        if (!fanContext.containsMatchIn(text)) return null
        if (Regex("tắt quạt|quạt 0|quạt mức 0").containsMatchIn(text)) return VoiceCommand.SetFanSpeed(0)
        if (fanHigh.containsMatchIn(text)) return VoiceCommand.SetFanSpeed(8)
        if (fanLow.containsMatchIn(text)) return VoiceCommand.SetFanSpeed(1)
        val value = numbers(text).firstOrNull { it in 0..8 }
        val changing = increase.containsMatchIn(text) || decrease.containsMatchIn(text)
        if (!changing && value != null) return VoiceCommand.SetFanSpeed(value)
        return when {
            increase.containsMatchIn(text) -> VoiceCommand.AdjustFanSpeed(value?.coerceAtLeast(1) ?: 1)
            decrease.containsMatchIn(text) -> VoiceCommand.AdjustFanSpeed(-(value?.coerceAtLeast(1) ?: 1))
            Regex("bật quạt|mở quạt").containsMatchIn(text) -> VoiceCommand.SetFanSpeed(1)
            else -> null
        }
    }

    private fun parseFanDirection(text: String): VoiceCommand.FanDirection? {
        if (!fanDirectionContext.containsMatchIn(text)) return null
        val directions = linkedSetOf<FanDirectionTarget>()
        if (Regex("mặt|ngang mặt|khoang xe|trong xe").containsMatchIn(text)) directions += FanDirectionTarget.FACE
        if (Regex("chân|xuống dưới|thổi xuống").containsMatchIn(text)) directions += FanDirectionTarget.FEET
        if (Regex("kính|lên trên|kính lái|thổi lên").containsMatchIn(text)) directions += FanDirectionTarget.WINDSHIELD
        if (Regex("mọi hướng|tất cả hướng|toàn bộ hướng").containsMatchIn(text)) {
            directions.clear()
            directions += listOf(FanDirectionTarget.FACE, FanDirectionTarget.FEET, FanDirectionTarget.WINDSHIELD)
        }
        return directions.takeIf { it.isNotEmpty() }?.let { VoiceCommand.FanDirection(it) }
    }

    private fun parseAmbientLighting(text: String): VoiceCommand? {
        if (!ambientContext.containsMatchIn(text)) return null

        parseAmbientColor(text)?.let { return VoiceCommand.SetAmbientColor(it) }

        Regex("(?<!\\d)(100|[1-9]?\\d)(?!\\d)\\s*(?:%|phần trăm)")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { percent ->
                val level = if (percent <= 0) 0 else ((percent * 20 + 99) / 100).coerceIn(1, 20)
                return VoiceCommand.SetAmbientBrightness(level)
            }

        Regex("(?:độ sáng|mức)\\s*(20|1\\d|[0-9])(?!\\d)")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { level ->
                return VoiceCommand.SetAmbientBrightness(level.coerceIn(0, 20))
            }

        return when {
            offVerb.containsMatchIn(text) -> VoiceCommand.AmbientLightPower(false)
            onVerb.containsMatchIn(text) -> VoiceCommand.AmbientLightPower(true)
            else -> null
        }
    }

    private fun parseAmbientColor(text: String): AmbientColor? = when {
        Regex("màu đỏ|(?:^| )đỏ(?: |$)").containsMatchIn(text) -> AmbientColor.RED
        Regex("xanh lá|màu lá|màu xanh lá").containsMatchIn(text) -> AmbientColor.GREEN
        Regex("xanh dương|màu xanh dương|màu xanh biển").containsMatchIn(text) -> AmbientColor.BLUE
        Regex("màu trắng|(?:^| )trắng(?: |$)").containsMatchIn(text) -> AmbientColor.WHITE
        Regex("màu vàng|(?:^| )vàng(?: |$)").containsMatchIn(text) -> AmbientColor.YELLOW
        Regex("màu tím|(?:^| )tím(?: |$)").containsMatchIn(text) -> AmbientColor.PURPLE
        Regex("màu hồng|(?:^| )hồng(?: |$)").containsMatchIn(text) -> AmbientColor.PINK
        Regex("màu cam|(?:^| )cam(?: |$)").containsMatchIn(text) -> AmbientColor.ORANGE
        Regex("xanh ngọc|màu cyan|cyan").containsMatchIn(text) -> AmbientColor.CYAN
        else -> null
    }

    private fun parseConvenienceSettings(text: String): VoiceCommand? {
        if (parkingWiperContext.containsMatchIn(text)) {
            val on = Regex("(?:^| )(?:bật|on)(?: |$)").containsMatchIn(text)
            val off = Regex("(?:^| )(?:tắt|off)(?: |$)").containsMatchIn(text)
            return when {
                on == off -> null
                on -> VoiceCommand.ParkingWiperAutoReduction(true)
                else -> VoiceCommand.ParkingWiperAutoReduction(false)
            }
        }
        if (interiorDoorLightContext.containsMatchIn(text)) {
            return when {
                offVerb.containsMatchIn(text) -> VoiceCommand.InteriorLightOnDoorOpen(false)
                onVerb.containsMatchIn(text) -> VoiceCommand.InteriorLightOnDoorOpen(true)
                else -> null
            }
        }

        if (followMeHomeContext.containsMatchIn(text)) {
            if (offVerb.containsMatchIn(text)) return VoiceCommand.SetFollowMeHome(FollowMeHomeDuration.OFF)
            val seconds = Regex("(?<!\\d)(30|60|90)(?!\\d)\\s*(?:giây|s)?")
                .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when (seconds) {
                30 -> VoiceCommand.SetFollowMeHome(FollowMeHomeDuration.SECONDS_30)
                60 -> VoiceCommand.SetFollowMeHome(FollowMeHomeDuration.SECONDS_60)
                90 -> VoiceCommand.SetFollowMeHome(FollowMeHomeDuration.SECONDS_90)
                else -> null
            }
        }

        if (wirelessChargerContext.containsMatchIn(text)) {
            return when {
                offVerb.containsMatchIn(text) -> VoiceCommand.WirelessChargerPower(false)
                onVerb.containsMatchIn(text) -> VoiceCommand.WirelessChargerPower(true)
                else -> null
            }
        }

        if (displayThemeContext.containsMatchIn(text)) {
            return when {
                Regex("tự động|auto").containsMatchIn(text) -> VoiceCommand.SetDisplayTheme(DisplayThemeMode.AUTO)
                Regex("tối|dark").containsMatchIn(text) -> VoiceCommand.SetDisplayTheme(DisplayThemeMode.DARK)
                Regex("sáng|light").containsMatchIn(text) -> VoiceCommand.SetDisplayTheme(DisplayThemeMode.LIGHT)
                else -> null
            }
        }

        return null
    }

    private fun parseParkingMode(text: String, originalText: String): VoiceCommand? {
        if (!parkingModeContext.containsMatchIn(text)) return null
        if (Regex("liên tục|không giới hạn|vô hạn|continuous").containsMatchIn(text)) return null
        if (Regex("(?<!\\d)[-−]\\s*\\d+|\\d+[.,]\\d+").containsMatchIn(originalText)) return null
        val turnOn = Regex("(?:^| )(?:bật|on)(?: |$)").containsMatchIn(text)
        val turnOff = Regex("(?:^| )(?:tắt|off)(?: |$)").containsMatchIn(text)
        if (turnOn && turnOff) return null
        val quantities = numbers(text)
        if (turnOff) return if (quantities.isEmpty()) VoiceCommand.SetParkingModeDuration(ParkingModeDuration.OFF) else null
        val value = quantities.singleOrNull() ?: return null
        val duration = when {
            value == 30 && Regex("(?<!\\d)30(?!\\d)\\s*phút").containsMatchIn(text) ->
                ParkingModeDuration.MINUTES_30
            value in 1..5 && Regex("(?<!\\d)[1-5](?!\\d)\\s*giờ").containsMatchIn(text) ->
                when (value) {
                    1 -> ParkingModeDuration.HOURS_1
                    2 -> ParkingModeDuration.HOURS_2
                    3 -> ParkingModeDuration.HOURS_3
                    4 -> ParkingModeDuration.HOURS_4
                    else -> ParkingModeDuration.HOURS_5
                }
            else -> return null
        }
        return VoiceCommand.SetParkingModeDuration(duration)
    }

    private fun parseBrakeSettings(text: String): VoiceCommand? {
        if (comfortableStopContext.containsMatchIn(text)) {
            val turnOn = Regex("(?:^| )(?:bật|on)(?: |$)").containsMatchIn(text)
            val turnOff = Regex("(?:^| )(?:tắt|off)(?: |$)").containsMatchIn(text)
            return when {
                turnOn == turnOff -> null
                turnOn -> VoiceCommand.ComfortableStop(true)
                else -> VoiceCommand.ComfortableStop(false)
            }
        }
        if (!brakeFeelContext.containsMatchIn(text)) return null
        val comfort = Regex("(?:^| )(?:comfort|êm ái|thoải mái)(?: |$)").containsMatchIn(text)
        val sport = Regex("(?:^| )(?:sport|thể thao)(?: |$)").containsMatchIn(text)
        return when {
            comfort == sport -> null
            comfort -> VoiceCommand.SetBrakePedalFeel(BrakePedalFeel.COMFORT)
            else -> VoiceCommand.SetBrakePedalFeel(BrakePedalFeel.SPORT)
        }
    }

    private fun parseChargeLimit(text: String, originalText: String): VoiceCommand? {
        if (!chargeLimitContext.containsMatchIn(text)) return null
        // Normalization removes punctuation, so reject signed/decimal quantities first.
        if (Regex("(?<!\\d)[-−]\\s*\\d+|\\d+[.,]\\d+").containsMatchIn(originalText)) return null
        val amps = numbers(text).singleOrNull() ?: return null
        return amps.takeIf { it in 5..32 }?.let { VoiceCommand.SetChargeCurrentLimit(it) }
    }

    private val driveModeRequest = Regex(
        "^(?:hãy |vui lòng )?(?:(?:chuyển|đổi|bật|mở|đặt)(?: sang| về)? )?" +
            "(?:chế độ(?: lái)?|drive mode) (?:eco|tiết kiệm|comfort|thoải mái|tiêu chuẩn|standard|sport|thể thao|dynamic)" +
            "(?: giúp tôi)?(?: nhé| đi)?$|^(?:eco|comfort|sport) mode$"
    )

    private fun parseDriving(text: String): VoiceCommand? {
        if (Regex("chế độ lái|drive mode|chế độ eco|chế độ comfort|chế độ sport|eco mode|sport mode").containsMatchIn(text)) {
            if (!driveModeRequest.matches(text)) return null
            return when {
                Regex("eco|tiết kiệm").containsMatchIn(text) -> VoiceCommand.SetDriveMode(DriveMode.ECO)
                Regex("comfort|thoải mái|tiêu chuẩn|standard").containsMatchIn(text) -> VoiceCommand.SetDriveMode(DriveMode.COMFORT)
                Regex("sport|thể thao|dynamic").containsMatchIn(text) -> VoiceCommand.SetDriveMode(DriveMode.SPORT)
                else -> null
            }
        }
        if (Regex("hồi năng lượng|phanh tái sinh|tái sinh|regen|regeneration").containsMatchIn(text)) {
            return when {
                Regex("thấp|nhẹ|mức 1|low").containsMatchIn(text) -> VoiceCommand.SetRegenLevel(RegenLevel.LOW)
                Regex("trung bình|vừa|mức 2|mid|medium").containsMatchIn(text) -> VoiceCommand.SetRegenLevel(RegenLevel.MEDIUM)
                Regex("cao|mạnh|mức 3|high").containsMatchIn(text) -> VoiceCommand.SetRegenLevel(RegenLevel.HIGH)
                else -> null
            }
        }
        return null
    }

    private fun parseWindow(text: String, originalText: String): VoiceCommand? {
        if (!windowContext.containsMatchIn(text)) return null
        if (defrostContext.containsMatchIn(text) || fanDirectionContext.containsMatchIn(text)) return null
        if (hasInvalidWindowQuantity(text, originalText)) return null
        val targets = parseWindowTargets(text) ?: return null
        // "hé" means a crack, never a full open: default to 10 % unless a percent is spoken.
        val percent = parseWindowPercent(text) ?: if (crackWindow.containsMatchIn(text)) CRACK_PERCENT else null
        val operation = when {
            stopWindow.containsMatchIn(text) -> WindowOperation.STOP
            closeWindow.containsMatchIn(text) -> if (percent == null) WindowOperation.CLOSE else WindowOperation.SET_PERCENT
            openWindow.containsMatchIn(text) -> if (percent == null) WindowOperation.OPEN else WindowOperation.SET_PERCENT
            percent != null -> WindowOperation.SET_PERCENT
            else -> return null
        }
        val canonicalPercent = when (operation) {
            WindowOperation.OPEN -> 100
            WindowOperation.CLOSE -> 0
            WindowOperation.SET_PERCENT -> percent
            WindowOperation.STOP -> null
        }
        return VoiceCommand.Window(targets, operation, canonicalPercent)
    }

    /** Reject unsupported numeric targets before OPEN/CLOSE can become a fallback. */
    private fun hasInvalidWindowQuantity(text: String, originalText: String): Boolean {
        if (Regex("(?<!\\d)[-−]\\s*\\d+|\\d+[.,]\\d+").containsMatchIn(originalText)) return true
        return Regex("(?<!\\d)\\d+(?!\\d)").findAll(text).any { match ->
            val value = match.value.toIntOrNull()
            value == null || value > 100
        }
    }

    /**
     * Resolve only explicit, deterministic window targets. Ambiguous side-only
     * phrases such as "kính bên trái/phải" and untargeted "mở kính" fail
     * closed instead of silently defaulting to the driver window.
     */
    private fun parseWindowTargets(text: String): Set<WindowTarget>? {
        if (Regex("tất cả|toàn bộ|bốn kính|4 kính").containsMatchIn(text)) return setOf(WindowTarget.ALL)

        val back = Regex("sau|phía sau|đằng sau").containsMatchIn(text)
        val front = Regex("trước|phía trước|đằng trước").containsMatchIn(text)
        val driver = Regex("bên lái|ghế lái|tài xế|(?:^| )(?:lái|tài)(?: |$)").containsMatchIn(text)
        val passenger = Regex("bên phụ|ghế phụ|(?:^| )phụ(?: |$)").containsMatchIn(text)
        val leftSide = Regex("bên trái|(?:^| )trái(?: |$)").containsMatchIn(text)
        val rightSide = Regex("bên phải|(?:^| )phải(?: |$)").containsMatchIn(text)
        val left = driver || leftSide
        val right = passenger || rightSide

        // A spoken side that ASR has damaged must never broaden into a whole-row write.
        if (!left && !right && Regex("(?:^| )(?:bên|ghế)(?: |$)").containsMatchIn(text)) return null

        return when {
            back && left && right -> setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT)
            front && left && right -> setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT)
            back && right -> setOf(WindowTarget.REAR_RIGHT)
            back && left -> setOf(WindowTarget.REAR_LEFT)
            front && right -> setOf(WindowTarget.FRONT_RIGHT)
            front && left -> setOf(WindowTarget.FRONT_LEFT)
            back -> setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT)
            front -> setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT)
            passenger -> setOf(WindowTarget.FRONT_RIGHT)
            driver -> setOf(WindowTarget.FRONT_LEFT)
            // Owner decision 29/09/2026: a side without front/rear means the front window.
            leftSide && rightSide -> setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT)
            leftSide -> setOf(WindowTarget.FRONT_LEFT)
            rightSide -> setOf(WindowTarget.FRONT_RIGHT)
            else -> null
        }
    }

    /**
     * Window counts and window positions are different slots. After Vietnamese
     * number normalization, "hai kính trước" becomes "2 kính trước"; the 2 is
     * a count and must never become a 2% actuator target.
     */
    private fun parseWindowPercent(text: String): Int? {
        Regex("(?<!\\d)(100|[1-9]?\\d)(?!\\d)\\s*(?:%|phần trăm)")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it.takeIf { it in 0..100 } }

        Regex("(?:^| )(?:về|đến|ở|mức)\\s+(100|[1-9]?\\d)(?!\\d)")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it.takeIf { it in 0..100 } }

        return Regex("(?<!\\d)(100|[1-9]?\\d)(?!\\d)").findAll(text)
            .firstOrNull { match ->
                val value = match.value.toIntOrNull() ?: return@firstOrNull false
                val suffix = text.substring(match.range.last + 1).trimStart()
                val isWindowCount = value in 2..4 && Regex("^(?:kính|cửa sổ|cửa kính)(?: |$)").containsMatchIn(suffix)
                !isWindowCount
            }
            ?.value?.toIntOrNull()?.takeIf { it in 0..100 }
    }

    private fun parseManualBrightness(text: String, original: String): VoiceCommand? {
        if (!Regex("^(?:đặt|chỉnh)\\s+độ sáng màn hình\\s+").containsMatchIn(text)) return null
        if (Regex("(?<!\\d)[-−]\\s*\\d+|\\d+[.,]\\d+").containsMatchIn(original)) return null
        val match = Regex("^(?:đặt|chỉnh)\\s+độ sáng màn hình\\s+(\\d{1,3})\\s*(?:%|phần trăm)$").matchEntire(text)
            ?: return null
        val percent = match.groupValues[1].toIntOrNull() ?: return null
        return percent.takeIf { it in 1..100 }?.let { VoiceCommand.SetManualDisplayBrightness(it) }
    }

    private fun parseDisplayBrightness(text: String): VoiceCommand? =
        if (Regex("^(?:(?:kiểm tra|xem|cho biết)\\s+)?độ sáng màn hình$|^màn hình\\s+(?:đang\\s+)?sáng bao nhiêu$").matches(text))
            VoiceCommand.QueryDisplayBrightness else null

    private fun parseWifiSsid(text: String): VoiceCommand? =
        if (Regex("^(?:(?:kiểm tra|xem|cho biết)\\s+)?(?:tên mạng|ssid)\\s+wi ?fi$|^wi ?fi\\s+(?:đang\\s+)?kết nối mạng nào$").matches(text))
            VoiceCommand.QueryWifiSsid else null

    private fun parseWifiIp(text: String): VoiceCommand? =
        if (Regex("^(?:(?:kiểm tra|xem|cho biết)\\s+)?(?:địa chỉ\\s+)?ip\\s+(?:của\\s+)?wi ?fi$").matches(text))
            VoiceCommand.QueryWifiIpAddress else null

    private fun parseWifiState(text: String): VoiceCommand? =
        if (Regex("^(?:kiểm tra|xem|cho biết)\\s+(?:trạng thái\\s+)?wi ?fi$|^wi ?fi\\s+(?:đang\\s+)?(?:bật|tắt)\\s+(?:hay|hoặc)\\s+(?:bật|tắt)$").matches(text))
            VoiceCommand.QueryWifiState else null

    private fun unsupportedWindowingMutation(text: String): Boolean =
        Regex("^(?:mở|bật|chạy|khởi động|chia)\\s+.*(?:pip|picture in picture|freeform|cửa sổ tự do|chia đôi màn hình|chia màn hình)")
            .containsMatchIn(text)

    private fun parseWindowingCapability(text: String): VoiceCommand.QueryWindowingCapability? {
        val target = Regex("^(?:kiểm tra|xem|cho biết)\\s+(?:khả năng\\s+)?(?:pip|picture in picture|freeform|cửa sổ tự do|chia đôi màn hình|chia màn hình)$")
            .matchEntire(text) ?: return null
        val mode = when (target.value.substringAfterLast(' ')) {
            "pip" -> WindowingMode.PIP
            "freeform" -> WindowingMode.FREEFORM
            else -> when {
                text.endsWith("picture in picture") -> WindowingMode.PIP
                text.endsWith("cửa sổ tự do") -> WindowingMode.FREEFORM
                else -> WindowingMode.SPLIT_SCREEN
            }
        }
        return VoiceCommand.QueryWindowingCapability(mode)
    }

    private fun parseSceneCapability(text: String): VoiceCommand.QuerySceneCapability? {
        val scene = when {
            Regex("chế độ nghỉ|rest mode").containsMatchIn(text) -> SceneType.REST
            Regex("chế độ cắm trại|camping mode").containsMatchIn(text) -> SceneType.CAMPING
            else -> return null
        }
        val query = Regex("^(?:kiểm tra|xem|cho biết)\\s+(?:khả năng\\s+)?(?:chế độ nghỉ|chế độ cắm trại|rest mode|camping mode)$|^(?:xe có|có)\\s+(?:chế độ nghỉ|chế độ cắm trại|rest mode|camping mode)(?:\\s+(?:không|0))?$")
        return if (query.matches(text)) VoiceCommand.QuerySceneCapability(scene) else null
    }

    private fun parseNavigation(text: String): VoiceCommand.Navigate? {
        if (!navigationContext.containsMatchIn(text)) return null
        val provider = detectNavigationProvider(text) ?: NavigationProvider.AUTO
        val destination = sequenceOf(
            Regex("(?:dẫn đường|chỉ đường)(?:\\s+(?:đến|tới))?\\s+(.+)").find(text)?.groupValues?.getOrNull(1),
            Regex("(?:đi đến|đi tới)\\s+(.+)").find(text)?.groupValues?.getOrNull(1)
        ).filterNotNull().firstOrNull()?.let { stripNavigationProvider(it) }?.trim().orEmpty()
        if (destination.isBlank()) return null
        return VoiceCommand.Navigate(provider, destination)
    }

    private fun parseMediaSearch(text: String): VoiceCommand.SearchMedia? {
        val provider = detectMediaProvider(text) ?: return null
        if (!searchMediaVerb.containsMatchIn(text)) return null
        var query = text.replaceFirst(searchMediaVerb, "").trim()
        query = query.replace(Regex("\\s+(?:trên|qua|bằng)\\s+(?:$mediaProviderNames)\\s*$"), "").trim()
        if (isOnlyMediaProvider(query, provider)) return null
        query = query.replace(Regex("^(?:$mediaProviderNames)\\s+"), "").trim()
        if (query.isBlank()) return null
        return VoiceCommand.SearchMedia(provider, query, defaultMediaKind(provider))
    }

    private fun parseMediaControl(text: String): VoiceCommand.MediaControl? = when {
        Regex("bài tiếp theo|chuyển bài|next(?: song| track)?").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.NEXT)
        Regex("bài trước|bài vừa rồi|previous(?: song| track)?|quay lại bài").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.PREVIOUS)
        Regex("tạm dừng|pause|dừng phát|dừng nhạc").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.PAUSE)
        Regex("tiếp tục phát|phát tiếp|resume").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.PLAY)
        Regex("phát nhạc|play music").matches(text) -> VoiceCommand.MediaControl(MediaAction.PLAY)
        Regex("tăng âm lượng|tăng volume|volume up").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.VOLUME_UP)
        Regex("giảm âm lượng|giảm volume|volume down").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN)
        Regex("play pause|phát tạm dừng").containsMatchIn(text) -> VoiceCommand.MediaControl(MediaAction.TOGGLE)
        else -> null
    }

    private val browseAppVerb = Regex("^(?:vào|xem|lướt)\\s+(?:app\\s+|ứng dụng\\s+)?")
    // Leftover vehicle/system nouns: these are unsupported commands, not app names.
    private val notAnAppName = Regex(
        "(?:^| )(?:kính|cửa|đèn|điều hòa|quạt|gió|cốp|sạc|nhiệt|màn hình|chế độ|pin|ghế|còi|nhạc|bài|video|âm|loa|" +
            "wifi|wi fi|mạng|bluetooth|số|phanh|xe|trợ lý|lạnh|mát|ấm|nóng|led|tiết kiệm|êm|thể thao|sport|eco|bốc|" +
            "nhẹ nhàng|kiểu|phai|fai|oai|ngoài file|ngoại file|avas|a vát|a va|cảnh báo|tiếng|chuông|chốt|xi nhan|nắp|" +
            "capo|capô|ca pô|gạt mưa|gạt nước|kênh|giáo|" +
            // 13/10/2026: car features that are not apps ("bật cruise" opened an app search).
            "cruise|ga tự động|kiểm soát hành trình|hành trình|lọc không khí|lọc khí|lọc bụi|ion|" +
            "camera lùi|camera sau|camera trước|camera hành trình|cửa sổ trời|sưởi|làm mát|gương|lốp|áp suất|" +
            "giữ làn|lệch làn|hỗ trợ lái|phanh tự động|khẩn cấp)(?: |$)"
    )

    private fun parseOpenApp(text: String): VoiceCommand? {
        val verb = openAppVerb.find(text)
        if (verb == null) {
            // "vào waze", "xem tiktok": only for apps the catalog already knows.
            val browse = browseAppVerb.find(text) ?: return null
            val target = detectAppTarget(InstalledAppMatcher.canonicalName(text.substring(browse.range.last + 1)))
            return target?.let { VoiceCommand.OpenApp(it) }
        }
        detectAppTarget(text)?.let { return VoiceCommand.OpenApp(it) }
        val verbWord = verb.value.trim()
        val name = text.substring(verb.range.last + 1)
            .removePrefix("ứng dụng ").removePrefix("app ").trim()
        if (name.isBlank()) return null
        val canonical = InstalledAppMatcher.canonicalName(name)
        detectAppTarget(canonical)?.let { return VoiceCommand.OpenApp(it) }
        // "chạy …" is mostly driving talk ("chạy êm", "chạy tiết kiệm"); only mở/bật/khởi động open apps by name.
        if (verbWord == "chạy") return null
        if (notAnAppName.containsMatchIn(canonical)) return null
        return VoiceCommand.OpenInstalledApp(name)
    }

    private fun isOnlyMediaProvider(text: String, provider: MediaProvider): Boolean = when (provider) {
        MediaProvider.DEFAULT -> false
        MediaProvider.YOUTUBE -> Regex("youtube|you tube").matches(text)
        MediaProvider.VANCED -> Regex("youtube vanced|vanced").matches(text)
        MediaProvider.REVANCED -> Regex("revanced|re vanced").matches(text)
        MediaProvider.MORPHE -> Regex("morphe").matches(text)
        MediaProvider.YOUTUBE_MUSIC -> Regex("youtube music|yt music").matches(text)
        MediaProvider.ZING_MP3 -> Regex("zing mp3|zing").matches(text)
        MediaProvider.SPOTIFY -> Regex("spotify").matches(text)
        MediaProvider.SMARTTUBE -> Regex("smart ?tube").matches(text)
    }

    internal fun detectAppTarget(text: String): AppTarget? = when {
        Regex("youtube music|yt music").containsMatchIn(text) -> AppTarget.YOUTUBE_MUSIC
        Regex("youtube vanced|(?:^| )vanced(?: |$)").containsMatchIn(text) -> AppTarget.VANCED
        Regex("revanced|re vanced").containsMatchIn(text) -> AppTarget.REVANCED
        Regex("morphe").containsMatchIn(text) -> AppTarget.MORPHE
        Regex("smart ?tube").containsMatchIn(text) -> AppTarget.SMARTTUBE
        Regex("youtube|you tube").containsMatchIn(text) -> AppTarget.YOUTUBE
        Regex("zing mp3|zing").containsMatchIn(text) -> AppTarget.ZING_MP3
        Regex("spotify").containsMatchIn(text) -> AppTarget.SPOTIFY
        Regex("google maps?|gu gồ mát|gu gờ mát").containsMatchIn(text) -> AppTarget.MAPS
        Regex("(?<![\\p{L}\\p{N}])(?:maps?|bản đồ)(?![\\p{L}\\p{N}])").containsMatchIn(text) -> AppTarget.DEFAULT_MAPS
        Regex("waze").containsMatchIn(text) -> AppTarget.WAZE
        Regex("vietmap(?: live)?").containsMatchIn(text) -> AppTarget.VIETMAP
        Regex("chrome|trình duyệt").containsMatchIn(text) -> AppTarget.CHROME
        Regex("netflix").containsMatchIn(text) -> AppTarget.NETFLIX
        Regex("tiktok|tik tok").containsMatchIn(text) -> AppTarget.TIKTOK
        Regex("vlc").containsMatchIn(text) -> AppTarget.VLC
        Regex("kiki").containsMatchIn(text) -> AppTarget.KIKI
        else -> null
    }

    private fun detectMediaProvider(text: String): MediaProvider? = when {
        Regex("youtube music|yt music").containsMatchIn(text) -> MediaProvider.YOUTUBE_MUSIC
        Regex("youtube vanced|(?:^| )vanced(?: |$)").containsMatchIn(text) -> MediaProvider.VANCED
        Regex("revanced|re vanced").containsMatchIn(text) -> MediaProvider.REVANCED
        Regex("morphe").containsMatchIn(text) -> MediaProvider.MORPHE
        Regex("smart ?tube").containsMatchIn(text) -> MediaProvider.SMARTTUBE
        Regex("youtube|you tube").containsMatchIn(text) -> MediaProvider.YOUTUBE
        Regex("spotify").containsMatchIn(text) -> MediaProvider.SPOTIFY
        Regex("zing mp3|zing").containsMatchIn(text) -> MediaProvider.ZING_MP3
        else -> null
    }

    private fun defaultMediaKind(provider: MediaProvider): MediaContentType = when (provider) {
        MediaProvider.YOUTUBE_MUSIC, MediaProvider.ZING_MP3, MediaProvider.SPOTIFY -> MediaContentType.MUSIC
        else -> MediaContentType.VIDEO
    }

    private fun detectNavigationProvider(text: String): NavigationProvider? = when {
        Regex("waze").containsMatchIn(text) -> NavigationProvider.WAZE
        Regex("vietmap(?: live)?").containsMatchIn(text) -> NavigationProvider.VIETMAP
        Regex("google maps|maps|bản đồ").containsMatchIn(text) -> NavigationProvider.GOOGLE_MAPS
        else -> null
    }

    private fun stripNavigationProvider(value: String): String = value
        .replace(Regex("\\s+(?:bằng|trên|qua)\\s+(?:google maps|maps|waze|vietmap(?: live)?)\\s*$"), "")
        .trim()

    private fun numbers(text: String): List<Int> = Regex("(?<!\\d)\\d{1,3}(?!\\d)")
        .findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
}
