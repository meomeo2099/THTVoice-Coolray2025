package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties

/**
 * Full-utterance aliases for natural phrasing found in the reference voice dataset
 * (docs/reference/voice/VOICE_PHRASES.csv) that the family parsers miss.
 * Every rule is a full match and maps to an existing typed command, so no new
 * actuator path is introduced here.
 */
object NaturalPhraseAliasParser {
    private const val DRIVE_PREFIX =
        "(?:(?:bật|chuyển|đổi|thay đổi|chuyển sang|đổi sang|thay đổi sang|chuyển xe sang|đổi xe sang|" +
            "chuyển chế độ|đổi chế độ|thay đổi chế độ|chuyển sang chế độ|đổi sang chế độ|thay đổi sang chế độ|bật chế độ|" +
            "thay đổi xe sang|chọn|chọn chế độ|chọn giúp tôi|chọn giúp tôi chế độ|chuyển giúp tôi sang|" +
            "chuyển giúp tôi sang chế độ|cho xe chạy|chạy chế độ|lái xe|chạy kiểu) )?"
    private val driveMode = Regex(
        "^$DRIVE_PREFIX(?:(chế độ(?: lái)?|lái|chạy|xe|sang) )?(thể thao|sport|bốc|thoải mái|êm ái|êm|comfort|tiết kiệm|eco)(?: nhé)?$"
    )
    private val regen = Regex(
        "^(?:(?:bật|chuyển|đổi|chỉnh)(?: sang)? )?(?:chế độ )?(?:hồi năng(?: lượng)?|tái tạo năng lượng|phanh tái sinh)(?: mức)? " +
            "(thấp|yếu|nhẹ|vừa|trung bình|cao|mạnh)$"
    )
    private val acOnUp = Regex("^(?:bật|mở) điều hòa lên$")
    private val tempDown = Regex("^(?:giảm|hạ) (?:nhiệt|bớt độ)(?: xuống)?$")
    private val tempUp = Regex("^tăng (?:nhiệt|thêm độ)(?: lên)?$")
    private val coldest = Regex("^(?:điều hòa )?(?:lạnh|mát) (?:nhất|hết cỡ)$")
    private val hottest = Regex("^(?:điều hòa )?(?:nóng|ấm) (?:nhất|hết cỡ)$")
    private val fanMax = Regex("^(?:max gió|(?:quạt|gió|quạt gió) (?:to|cao|mạnh) nhất|(?:quạt|gió) hết cỡ)$")
    private val fanUp = Regex("^(?:cho )?(?:gió|quạt gió) (?:to|mạnh) lên$|^thổi(?: gió)? mạnh lên$")
    private val fanDown = Regex("^(?:cho )?(?:gió|quạt gió) (?:nhỏ|nhẹ) lại$|^thổi(?: gió)? nhẹ lại$")
    private val volumeUp = Regex("^(?:tăng|to) (?:âm thanh|tiếng loa|tiếng)(?: lên)?$")
    private val volumeDown = Regex("^(?:giảm|nhỏ) (?:âm thanh|tiếng loa|tiếng)(?: xuống)?$")
    private val pauseMusic = Regex("^(?:dừng|ngừng|ngưng) (?:nhạc|bài hát|nhạc xe)$|^tắt nhạc xe$")
    private val battery = Regex(
        "^(?:kiểm tra (?:giúp tôi )?)?(?:mức )?pin(?: xe)?(?: còn)?(?: bao nhiêu)?$|^xe còn bao nhiêu điện$"
    )

    /** Additional full-match phrasing from the dataset (pass 2), each mapped to an existing typed command. */
    private val rules: List<Pair<Regex, VoiceCommand>> = listOf(
        "(?:bật|mở) (?:lạnh|mát|lốc lạnh)|làm (?:mát|lạnh) xe(?: giúp tôi)?|kích hoạt (?:điều hòa|lốc lạnh)" to VoiceCommand.AcPower(true),
        "tắt (?:lạnh|mát|lốc lạnh)|ngắt lốc lạnh|(?:ngừng|ngưng) điều hòa(?: xe)?" to VoiceCommand.AcPower(false),
        "(?:giảm|hạ)(?: thêm)? 1 độ" to VoiceCommand.AdjustTemperature(-1f),
        "tăng(?: thêm)? 1 độ" to VoiceCommand.AdjustTemperature(1f),
        "hạ giúp tôi nhiệt độ(?: nhé)?|chỉnh nhiệt độ thấp hơn|chỉnh nhiệt xuống|tăng độ mát" to VoiceCommand.AdjustTemperature(-CoolrayProperties.HVAC_TEMP_STEP),
        "chỉnh nhiệt độ cao hơn|chỉnh nhiệt lên|nâng độ lên|(?:cho )?ấm thêm(?: chút)?|tăng độ ấm" to VoiceCommand.AdjustTemperature(CoolrayProperties.HVAC_TEMP_STEP),
        "nhiệt độ (?:nhỏ nhất|mát nhất|thấp nhất)|bật max lạnh|(?:bật )?(?:chế độ )?làm (?:mát|lạnh) nhanh" to VoiceCommand.TemperatureLow,
        "nhiệt độ (?:lớn nhất|ấm nhất|cao nhất)|(?:sưởi ấm|làm ấm|làm nóng) nhanh" to VoiceCommand.TemperatureHigh,
        "(?:(?:chỉnh|đặt|để|set) )?(?:tốc độ gió|quạt(?: số| mức)?|gió(?: số)?|mức quạt) (?:to nhất|lớn nhất|cực đại)" to VoiceCommand.SetFanSpeed(8),
        "(?:(?:chỉnh|đặt|cho) )?(?:quạt gió|quạt|gió|mức gió|tốc độ gió)(?: mức)? (?:vừa vừa|trung bình)" to VoiceCommand.SetFanSpeed(4),
        "tắt gió|(?:ngừng|ngưng) quạt" to VoiceCommand.SetFanSpeed(0),
        "(?:cho )?quạt nhỏ lại|hạ tốc độ gió|(?:hạ|giảm) giúp tôi quạt(?: gió)?(?: nhé)?|(?:cho )?quạt(?: thổi)? nhẹ thôi|cho tốc độ gió thấp hơn" to VoiceCommand.AdjustFanSpeed(-1),
        "(?:cho )?quạt mạnh thêm|cho tốc độ gió cao hơn" to VoiceCommand.AdjustFanSpeed(1),
        "(?:(?:bật|vặn|cho) to (?:loa|âm lượng|âm thanh|tiếng)(?: lên)?|vặn to lên|cho to lên|tăng mức âm lượng|cho âm lượng to lên|vặn to loa lên|bật to loa lên|cho to tiếng lên)" to VoiceCommand.MediaControl(MediaAction.VOLUME_UP),
        "(?:(?:bật|vặn|cho) nhỏ (?:loa|âm lượng|âm thanh|tiếng)(?: lại)?|vặn nhỏ lại|cho nhỏ lại|giảm mức âm lượng|cho âm lượng nhỏ lại|vặn nhỏ loa lại|bật nhỏ loa lại|cho nhỏ tiếng lại)" to VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN),
        "tắt nhạc|dừng video|(?:ngừng|ngưng) phát(?: nhạc)?|(?:tắt|dừng) radio" to VoiceCommand.MediaControl(MediaAction.PAUSE),
        "tiếp tục (?:nhạc|video)|chơi nhạc tiếp|play tiếp" to VoiceCommand.MediaControl(MediaAction.PLAY),
        "bài kế tiếp|qua bài khác|đổi bài khác|chuyển sang bài khác|play bài(?: nhạc)? (?:tiếp theo|kế tiếp|tiếp)" to VoiceCommand.MediaControl(MediaAction.NEXT),
        "lùi lại bài" to VoiceCommand.MediaControl(MediaAction.PREVIOUS),
        "tắt (?:âm thanh|âm lượng|tiếng|tiếng loa|loa xe|tiếng xe|hết tiếng)" to VoiceCommand.MediaControl(MediaAction.MUTE),
        "(?:bật|mở)(?: lại)? (?:âm thanh|âm lượng|tiếng|tiếng loa)" to VoiceCommand.MediaControl(MediaAction.UNMUTE),
        "(?:báo cáo|báo|cho biết)(?: mức)? pin|kiểm tra dung lượng pin|tình trạng pin(?: hiện tại)?(?: của xe)?" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.BATTERY_SOC),
        "nhiệt độ ngoài trời(?: bao nhiêu| thế nào)?|(?:bên ngoài|ngoài trời|trời)(?: đang)? (?:mấy|bao nhiêu) độ|thời tiết (?:bên ngoài|ngoài xe)" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.OUTSIDE_TEMPERATURE),
        "chế độ lái(?: hiện tại)?(?: đang)?(?: là)?(?: gì| nào| thế nào)?|xe đang ở chế độ nào|(?:hiện|đang chạy) chế độ lái(?: là)? gì" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.DRIVE_MODE),
        "(?:hồi năng lượng|phanh tái sinh)(?: đang)?(?: mức)? (?:nào|là gì)|chế độ hồi năng lượng hiện tại|(?:hiện )?(?:mức )?phanh tái sinh là gì" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.REGEN_LEVEL),
        "(?:tốc độ|vận tốc)(?: hiện tại)?(?: bao nhiêu)?|xe(?: đang)? chạy tốc độ bao nhiêu" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.SPEED),
        "tổng(?: số)? (?:km|kilomet|ki lô mét)(?: đã đi)?|số km đã đi|xe chạy được bao nhiêu km rồi" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.ODOMETER),
        "đi tiếp được bao nhiêu|quãng đường ước tính|còn đi được bao xa|xe đi được bao xa nữa" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.RANGE),
        "số đang là gì|hộp số đang ở đâu|đang vào số mấy|xe đang cài số mấy" to VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.GEAR),
        "(?:mạng không dây|mạng|wifi)(?: có)? (?:đang )?bật (?:không|0)|mạng thế nào" to VoiceCommand.QueryWifiState,
        "(?:thổi|chỉnh|hướng)(?: gió)? vào người|chỉnh hướng gió vào người" to VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE)),
        "chỉnh gió kính và chân" to VoiceCommand.FanDirection(setOf(FanDirectionTarget.WINDSHIELD, FanDirectionTarget.FEET)),
        "ngoài đường bụi quá|khói bụi quá|bên ngoài hôi quá" to VoiceCommand.Recirculation(true),
        "lấy gió từ bên ngoài|thông gió cho xe|cho thoáng(?: xe| khí)" to VoiceCommand.Recirculation(false),
        "sấy giúp tôi kính lái|kính(?: chắn gió)? bị mờ|kính đọng (?:nước|sương)|sương mù bám kính" to VoiceCommand.Defrost(DefrostZone.FRONT, true),
        "(?:tìm )?(?:cây xăng|trạm xăng|chỗ đổ xăng)(?: gần nhất| gần đây)?" to VoiceCommand.Navigate(NavigationProvider.AUTO, "cây xăng gần đây"),
        "(?:tìm )?(?:trạm sạc(?: xe điện)?|chỗ sạc(?: điện)?|trụ sạc)(?: gần nhất| gần đây)" to VoiceCommand.Navigate(NavigationProvider.AUTO, "trạm sạc xe điện gần đây"),
        "thoát ra (?:màn hình chính|trang chủ)|về màn hình xe|màn hình chính" to VoiceCommand.CloseAllApps,
        "bật nhạc (?:lên|xe)|nhạc xe|bật bài hát lên|(?:mở|bật|phát|chơi) bài nhạc|play nhạc(?: lên)?" to VoiceCommand.OpenMedia(MediaContentType.MUSIC)
    ).map { (pattern, command) -> Regex("^(?:$pattern)$") to command }

    private val brightnessPercent = Regex(
        "^(?:(?:đặt|chỉnh|cho|tăng|giảm) )?độ sáng(?: màn hình)?(?: lên| xuống)? (\\d{1,3}) phần trăm$|" +
            "^(?:tăng|giảm) (\\d{1,3}) phần trăm độ sáng$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val command = SharedCommandLanguage.resolve(normalized) ?: match(normalized) ?: return null
        return ParsedVoiceCommand(text, normalized, command)
    }

    private fun match(t: String): VoiceCommand? {
        driveMode.matchEntire(t)?.let { m ->
            // A bare mode word ("êm", "tiết kiệm") without any drive context is too ambiguous.
            val hasContext = m.groupValues[1].isNotEmpty() || t != m.groupValues[2]
            if (!hasContext) return null
            return VoiceCommand.SetDriveMode(
                when (m.groupValues[2]) {
                    "thể thao", "sport", "bốc" -> DriveMode.SPORT
                    "tiết kiệm", "eco" -> DriveMode.ECO
                    else -> DriveMode.COMFORT
                }
            )
        }
        regen.matchEntire(t)?.let { m ->
            return VoiceCommand.SetRegenLevel(
                when (m.groupValues[1]) {
                    "thấp", "yếu", "nhẹ" -> RegenLevel.LOW
                    "vừa", "trung bình" -> RegenLevel.MEDIUM
                    else -> RegenLevel.HIGH
                }
            )
        }
        brightnessPercent.matchEntire(t)?.let { m ->
            val percent = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
            if (percent != null && percent in 1..100) return VoiceCommand.SetManualDisplayBrightness(percent)
        }
        rules.firstOrNull { (regex, _) -> regex.matches(t) }?.let { return it.second }
        return when {
            acOnUp.matches(t) -> VoiceCommand.AcPower(true)
            tempDown.matches(t) -> VoiceCommand.AdjustTemperature(-CoolrayProperties.HVAC_TEMP_STEP)
            tempUp.matches(t) -> VoiceCommand.AdjustTemperature(CoolrayProperties.HVAC_TEMP_STEP)
            coldest.matches(t) -> VoiceCommand.TemperatureLow
            hottest.matches(t) -> VoiceCommand.TemperatureHigh
            fanMax.matches(t) -> VoiceCommand.SetFanSpeed(8)
            fanUp.matches(t) -> VoiceCommand.AdjustFanSpeed(1)
            fanDown.matches(t) -> VoiceCommand.AdjustFanSpeed(-1)
            volumeUp.matches(t) -> VoiceCommand.MediaControl(MediaAction.VOLUME_UP)
            volumeDown.matches(t) -> VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN)
            pauseMusic.matches(t) -> VoiceCommand.MediaControl(MediaAction.PAUSE)
            battery.matches(t) -> VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.BATTERY_SOC)
            else -> null
        }
    }
}
