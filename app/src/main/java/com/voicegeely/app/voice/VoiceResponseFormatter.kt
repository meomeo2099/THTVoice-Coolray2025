package com.voicegeely.app.voice

/** Centralizes spoken command feedback so handlers never invent success TTS. */
object VoiceResponseFormatter {
    /** Added when a Xiaomi phone may block opening apps while THT Voice runs in the background. */
    const val BACKGROUND_LAUNCH_BLOCKED =
        "Nếu ứng dụng chưa mở, hãy bật quyền hiển thị cửa sổ khi chạy nền cho trợ lý trong phần cài đặt."

    /** Spoken reasons kept in this file so every word reaches the Kokoro lexicon. */
    const val APP_NOT_FOUND = "Không tìm thấy ứng dụng này trên xe."
    const val APP_LIST_UNAVAILABLE = "Chưa đọc được danh sách ứng dụng trên xe."

    fun acknowledgement(command: VoiceCommand): String = when (command) {
        is VoiceCommand.SetTemperature -> "Đang chỉnh nhiệt độ về ${formatNumber(command.celsius)} độ."
        is VoiceCommand.AdjustTemperature -> if (command.deltaCelsius >= 0f) "Đang tăng nhiệt độ ${formatNumber(command.deltaCelsius)} độ." else "Đang giảm nhiệt độ ${formatNumber(-command.deltaCelsius)} độ."
        VoiceCommand.TemperatureHigh -> "Đang chỉnh nhiệt độ lên mức cao nhất."
        VoiceCommand.TemperatureLow -> "Đang chỉnh nhiệt độ xuống mức thấp nhất."
        is VoiceCommand.AcPower -> if (command.on) "Đang bật điều hòa." else "Đang tắt điều hòa."
        is VoiceCommand.SetFanSpeed -> "Đang chỉnh quạt về mức ${command.level}."
        is VoiceCommand.AdjustFanSpeed -> if (command.delta >= 0) "Đang tăng quạt." else "Đang giảm quạt."
        is VoiceCommand.Recirculation -> if (command.enabled) "Đang chuyển sang lấy gió trong." else "Đang chuyển sang lấy gió ngoài."
        is VoiceCommand.Defrost -> defrostAck(command)
        is VoiceCommand.FanDirection -> "Đang chỉnh hướng gió ${fanDirectionText(command.directions)}."
        is VoiceCommand.HvacEco -> if (command.on) "Đang bật điều hòa tiết kiệm." else "Đang tắt điều hòa tiết kiệm."
        is VoiceCommand.HvacAuto -> "Đang ${if (command.on) "bật" else "tắt"} AUTO điều hòa."
        is VoiceCommand.SetAutoFanProfile -> "Đang chỉnh chế độ quạt điều hòa tự động."
        is VoiceCommand.OemSwitch -> "Đang ${if (command.on) "bật" else "tắt"} ${command.feature.label}."
        is VoiceCommand.QueryOemSwitch -> "Đang đọc trạng thái ${command.feature.label}."
        is VoiceCommand.SetSunroofTilt -> "Đang ${if (command.on) "mở" else "đóng"} nghiêng cửa sổ trời."
        is VoiceCommand.HvacAirflowAutoCycle -> when {
            command.periodicMinutes != null && command.on -> "Đang bật đảo hướng gió định kỳ."
            command.periodicMinutes != null -> "Đang tắt đảo hướng gió."
            command.on -> "Đang bật tự động đảo gió."
            else -> "Đang tắt tự động đảo gió."
        }
        is VoiceCommand.SetDriveMode -> "Đang chuyển sang chế độ ${driveModeText(command.mode)}."
        is VoiceCommand.SetRegenLevel -> "Đang chỉnh hồi năng lượng về ${regenText(command.level)}."
        is VoiceCommand.SetBrakePedalFeel -> "Đang chỉnh cảm giác phanh sang ${brakeFeelText(command.mode)}."
        is VoiceCommand.ComfortableStop -> if (command.on) "Đang bật chế độ dừng êm." else "Đang tắt chế độ dừng êm."
        is VoiceCommand.SetChargeCurrentLimit -> command.control?.let { chargeControlAck(it) }
            ?: "Đang đặt giới hạn dòng sạc ${command.amps} ampe."
        is VoiceCommand.AmbientLightPower -> if (command.on) "Đang bật đèn viền." else "Đang tắt đèn viền."
        is VoiceCommand.SetAmbientBrightness -> "Đang chỉnh độ sáng đèn viền về mức ${command.level}."
        is VoiceCommand.SetAmbientColor -> "Đang đổi đèn viền sang ${ambientColorText(command.color)}."
        is VoiceCommand.InteriorLightOnDoorOpen -> if (command.on) "Đang bật đèn nội thất khi mở cửa." else "Đang tắt đèn nội thất khi mở cửa."
        is VoiceCommand.SetFollowMeHome -> "Đang chỉnh Follow-Me-Home ${followMeHomeText(command.duration)}."
        is VoiceCommand.WirelessChargerPower -> if (command.on) "Đang bật sạc không dây." else "Đang tắt sạc không dây."
        is VoiceCommand.SetDisplayTheme -> "Đang chuyển ${displayThemeText(command.mode)}."
        is VoiceCommand.ParkingWiperAutoReduction -> if (command.enabled) "Đang bật tự giảm gạt mưa khi đỗ." else "Đang tắt tự giảm gạt mưa khi đỗ."
        is VoiceCommand.SetParkingModeDuration -> "Đang chỉnh chế độ đỗ xe ${parkingDurationText(command.duration)}."
        is VoiceCommand.QueryVehicleStatus -> vehicleStatusAck(command.query)
        is VoiceCommand.QuerySceneCapability -> "Đang kiểm tra khả năng mở chế độ ${sceneText(command.scene)}."
        is VoiceCommand.OpenSceneScreen -> "Đang mở màn hình chế độ ${sceneText(command.scene)}."
        VoiceCommand.CloseAllApps -> "Đang về màn hình chính."
        is VoiceCommand.ScreenBlackout -> if (command.on) "Đang tắt màn hình." else "Đang bật lại màn hình."
        is VoiceCommand.QueryLocalInfo -> when (command.kind) {
            LocalInfoKind.TIME -> "Để tôi xem giờ."
            LocalInfoKind.DATE -> "Để tôi xem ngày."
            LocalInfoKind.ASSISTANT_STATUS -> "Tôi đây."
        }
        is VoiceCommand.QueryWindowingCapability -> "Đang kiểm tra hỗ trợ ${windowingText(command.mode)}."
        is VoiceCommand.SplitScreen -> "Đang ${splitActionText(command)}."
        VoiceCommand.QueryWifiState -> "Đang kiểm tra trạng thái Wi-Fi."
        VoiceCommand.QueryWifiIpAddress -> "Đang kiểm tra IP nội bộ của Wi-Fi."
        VoiceCommand.QueryWifiSsid -> "Đang kiểm tra tên mạng Wi-Fi."
        VoiceCommand.OpenWifiSettings -> "Đang mở cài đặt Wi-Fi."
        VoiceCommand.QueryDisplayBrightness -> "Đang kiểm tra thiết lập độ sáng màn hình."
        is VoiceCommand.SetManualDisplayBrightness -> command.mode?.let {
            "Đang chuyển độ sáng hệ thống sang ${displayBrightnessModeText(it)}."
        } ?: "Đang đặt mức sáng hệ thống ${command.percent} phần trăm."
        is VoiceCommand.Window -> windowAck(command)
        is VoiceCommand.Sunroof -> sunroofAck(command)
        is VoiceCommand.CabinLight -> if (command.on) "Đang bật đèn trong xe." else "Đang tắt đèn trong xe."
        is VoiceCommand.SeatClimate -> "Đang ${seatClimateAction(command, null)}."
        is VoiceCommand.SteeringWheelHeat -> if (command.on) "Đang bật sưởi vô lăng." else "Đang tắt sưởi vô lăng."
        is VoiceCommand.MaxAc -> if (command.on) "Đang bật làm lạnh nhanh." else "Đang tắt làm lạnh nhanh."
        is VoiceCommand.ExteriorLight -> "Đang ${exteriorLightText(command.mode)}."
        is VoiceCommand.FogLight -> if (command.on) "Đang bật đèn sương mù." else "Đang tắt đèn sương mù."
        is VoiceCommand.SetDoorLock -> if (command.locked) "Đang khóa cửa xe." else "Đang mở khóa cửa xe."
        VoiceCommand.OpenFrontTrunk -> "Đang gửi lệnh mở cốp trước."
        VoiceCommand.UnlockRearTrunk -> "Đang mở khóa cửa xe để dùng cốp sau."
        is VoiceCommand.SetHeadlightHeight -> "Đang chỉnh độ cao đèn trước về mức ${command.level}."
        is VoiceCommand.SetIntelligentHighBeam -> if (command.enabled) "Đang bật đèn pha thông minh." else "Đang tắt đèn pha thông minh."
        is VoiceCommand.OpenApp -> "Đang mở ${appText(command.target)}."
        is VoiceCommand.OpenInstalledApp -> "Đang mở ứng dụng."
        is VoiceCommand.OpenMedia -> "Đang mở ${mediaKindText(command.kind)}${mediaProviderSuffix(command.provider)}."
        is VoiceCommand.SearchMedia -> "Đang tìm ${shortText(command.query)}${mediaProviderSuffix(command.provider)}."
        is VoiceCommand.MediaControl -> mediaAck(command.action)
        is VoiceCommand.SetMediaVolume -> "Đang chỉnh âm lượng."
        is VoiceCommand.AdjustMediaVolume -> if (command.steps > 0) "Đang tăng âm lượng." else "Đang giảm âm lượng."
        is VoiceCommand.Navigate -> "Đang mở dẫn đường đến ${shortText(command.destination)}${navigationSuffix(command.provider)}."
    }

    fun result(command: VoiceCommand, execution: CommandExecutionResult): String = when (execution) {
        is CommandExecutionResult.Confirmed -> confirmed(command, execution)
        is CommandExecutionResult.AcceptedUnverified -> unverified(command, execution)
        is CommandExecutionResult.Rejected -> "Không thể thực hiện lệnh. ${execution.reason}".trim()
        is CommandExecutionResult.Failed -> "Thực hiện lệnh không thành công. ${execution.reason}".trim()
    }

    private fun confirmed(command: VoiceCommand, execution: CommandExecutionResult.Confirmed): String = when (command) {
        is VoiceCommand.SetTemperature -> "Đã chỉnh nhiệt độ về ${execution.actualValue ?: "${formatNumber(command.celsius)} độ"}."
        is VoiceCommand.AdjustTemperature -> execution.actualValue?.let { "Đã chỉnh nhiệt độ. Nhiệt độ hiện tại là $it." } ?: "Đã chỉnh nhiệt độ."
        VoiceCommand.TemperatureHigh -> "Đã chỉnh nhiệt độ lên mức cao nhất."
        VoiceCommand.TemperatureLow -> "Đã chỉnh nhiệt độ xuống mức thấp nhất."
        is VoiceCommand.AcPower -> if (command.on) "Đã bật điều hòa." else "Đã tắt điều hòa."
        is VoiceCommand.SetFanSpeed -> "Đã chỉnh quạt về mức ${command.level}."
        is VoiceCommand.AdjustFanSpeed -> execution.actualValue?.let { "Đã chỉnh quạt về $it." } ?: "Đã chỉnh quạt."
        is VoiceCommand.Recirculation -> if (command.enabled) "Đã chuyển sang lấy gió trong." else "Đã chuyển sang lấy gió ngoài."
        is VoiceCommand.Defrost -> defrostConfirmed(command)
        is VoiceCommand.FanDirection -> "Đã chỉnh hướng gió ${fanDirectionText(command.directions)}."
        is VoiceCommand.HvacEco -> if (command.on) "Đã bật điều hòa tiết kiệm." else "Đã tắt điều hòa tiết kiệm."
        is VoiceCommand.HvacAuto -> "Đã ${if (command.on) "bật" else "tắt"} AUTO điều hòa."
        is VoiceCommand.SetAutoFanProfile -> "Đã chỉnh quạt tự động sang ${when (command.profile) { AutoFanProfile.QUIET -> "êm"; AutoFanProfile.NORMAL -> "bình thường"; AutoFanProfile.STRONG -> "mạnh" }}."
        is VoiceCommand.OemSwitch -> "Đã ${if (command.on) "bật" else "tắt"} ${command.feature.label}."
        is VoiceCommand.QueryOemSwitch -> execution.actualValue?.let { "$it." } ?: "Không đọc được trạng thái ${command.feature.label}."
        is VoiceCommand.SetSunroofTilt -> "Đã ${if (command.on) "mở" else "đóng"} nghiêng cửa sổ trời."
        is VoiceCommand.HvacAirflowAutoCycle -> when {
            command.periodicMinutes != null && command.on ->
                "Đã bật đảo hướng gió ${execution.actualValue ?: "định kỳ"}, luân phiên vào mặt và xuống chân."
            command.periodicMinutes != null -> "Đã tắt đảo hướng gió."
            command.on -> "Đã bật tự động đảo gió."
            else -> "Đã tắt tự động đảo gió."
        }
        is VoiceCommand.SetDriveMode -> "Đã chuyển sang chế độ ${driveModeText(command.mode)}."
        is VoiceCommand.SetRegenLevel -> "Đã chỉnh hồi năng lượng về ${regenText(command.level)}."
        is VoiceCommand.SetBrakePedalFeel -> "Đã chỉnh cảm giác phanh sang ${brakeFeelText(command.mode)}."
        is VoiceCommand.ComfortableStop -> if (command.on) "Đã bật chế độ dừng êm." else "Đã tắt chế độ dừng êm."
        is VoiceCommand.SetChargeCurrentLimit -> command.control?.let { chargeControlConfirmed(it) }
            ?: "Đã đặt giới hạn dòng sạc ${execution.actualValue ?: "${command.amps} ampe"}."
        is VoiceCommand.AmbientLightPower -> if (command.on) "Đã bật đèn viền." else "Đã tắt đèn viền."
        is VoiceCommand.SetAmbientBrightness -> "Đã chỉnh độ sáng đèn viền về ${execution.actualValue ?: "mức ${command.level}"}."
        is VoiceCommand.SetAmbientColor -> "Đã đổi đèn viền sang ${execution.actualValue ?: ambientColorText(command.color)}."
        is VoiceCommand.InteriorLightOnDoorOpen -> if (command.on) "Đã bật đèn nội thất khi mở cửa." else "Đã tắt đèn nội thất khi mở cửa."
        is VoiceCommand.SetFollowMeHome -> "Đã chỉnh Follow-Me-Home ${execution.actualValue ?: followMeHomeText(command.duration)}."
        is VoiceCommand.WirelessChargerPower -> if (command.on) "Đã bật sạc không dây." else "Đã tắt sạc không dây."
        is VoiceCommand.SetDisplayTheme -> "Đã chuyển ${execution.actualValue ?: displayThemeText(command.mode)}."
        is VoiceCommand.ParkingWiperAutoReduction -> if (command.enabled) "Đã bật tự giảm gạt mưa khi đỗ." else "Đã tắt tự giảm gạt mưa khi đỗ."
        is VoiceCommand.SetParkingModeDuration -> "Đã chỉnh chế độ đỗ xe ${parkingDurationText(command.duration)}."
        is VoiceCommand.QueryVehicleStatus -> execution.actualValue?.let { "$it." } ?: "Đã đọc trạng thái xe."
        is VoiceCommand.QuerySceneCapability -> execution.actualValue?.let { "$it." } ?: "Không đọc được khả năng scene."
        is VoiceCommand.OpenSceneScreen -> "Đã mở màn hình chế độ ${sceneText(command.scene)}."
        VoiceCommand.CloseAllApps -> "Đã về màn hình chính. Ứng dụng khác vẫn có thể chạy nền."
        is VoiceCommand.ScreenBlackout -> if (command.on) "Đã tắt màn hình. Chạm vào màn hình hoặc nói bật màn hình để mở lại." else "Đã bật lại màn hình."
        is VoiceCommand.QueryLocalInfo -> localInfoText(command.kind, execution.actualValue)
        is VoiceCommand.QueryWindowingCapability -> execution.actualValue?.let { "$it." } ?: "Không đọc được khả năng cửa sổ."
        is VoiceCommand.SplitScreen -> "Đã ${splitActionText(command)}."
        VoiceCommand.QueryWifiState -> execution.actualValue?.let { "$it." } ?: "Không đọc được trạng thái Wi-Fi."
        VoiceCommand.QueryWifiIpAddress -> execution.actualValue?.let { "$it." } ?: "Không đọc được IP Wi-Fi."
        VoiceCommand.QueryWifiSsid -> execution.actualValue?.let { "$it." } ?: "Không đọc được tên mạng Wi-Fi."
        VoiceCommand.OpenWifiSettings -> execution.actualValue?.let { "$it." } ?: "Đã mở cài đặt Wi-Fi."
        VoiceCommand.QueryDisplayBrightness -> execution.actualValue?.let { "$it." } ?: "Không đọc được thiết lập độ sáng màn hình."
        is VoiceCommand.SetManualDisplayBrightness -> command.mode?.let {
            val actual = execution.actualValue ?: displayBrightnessModeText(it)
            "Đã chuyển độ sáng hệ thống sang $actual."
        } ?: "Đã đặt mức sáng hệ thống ${command.percent} phần trăm."
        is VoiceCommand.Window -> windowConfirmed(command, execution.actualValue)
        is VoiceCommand.Sunroof -> sunroofConfirmed(command, execution.actualValue)
        is VoiceCommand.CabinLight -> if (command.on) "Đã bật đèn trong xe." else "Đã tắt đèn trong xe."
        is VoiceCommand.SeatClimate -> "Đã ${seatClimateAction(command, execution.actualValue)}."
        is VoiceCommand.SteeringWheelHeat -> if (command.on) "Đã bật sưởi vô lăng." else "Đã tắt sưởi vô lăng."
        is VoiceCommand.MaxAc -> if (command.on) "Đã bật làm lạnh nhanh." else "Đã tắt làm lạnh nhanh."
        is VoiceCommand.ExteriorLight -> "Đã ${exteriorLightText(command.mode)}."
        is VoiceCommand.FogLight -> if (command.on) "Đã bật đèn sương mù." else "Đã tắt đèn sương mù."
        is VoiceCommand.SetDoorLock -> if (command.locked) "Đã khóa cửa xe." else "Đã mở khóa cửa xe."
        VoiceCommand.OpenFrontTrunk -> "Chưa có read-back độc lập cho cốp trước; không công bố thành công."
        VoiceCommand.UnlockRearTrunk -> "Đã mở khóa cửa xe để dùng cốp sau."
        is VoiceCommand.SetHeadlightHeight -> "Đã chỉnh độ cao đèn trước về ${execution.actualValue ?: "mức ${command.level}"}."
        is VoiceCommand.SetIntelligentHighBeam -> if (command.enabled) "Đã bật đèn pha thông minh." else "Đã tắt đèn pha thông minh."
        is VoiceCommand.OpenApp -> "Đã mở ${appText(command.target)}."
        is VoiceCommand.OpenInstalledApp -> "Đã mở ứng dụng."
        is VoiceCommand.OpenMedia -> "Đã mở ${mediaKindText(command.kind)}${mediaProviderSuffix(command.provider)}."
        is VoiceCommand.SearchMedia -> "Đã mở kết quả tìm kiếm ${shortText(command.query)}${mediaProviderSuffix(command.provider)}."
        is VoiceCommand.MediaControl -> mediaConfirmed(command.action)
        is VoiceCommand.SetMediaVolume -> execution.actualValue?.let { "Đã chỉnh âm lượng mức $it." } ?: "Đã chỉnh âm lượng."
        is VoiceCommand.AdjustMediaVolume ->
            (if (command.steps > 0) "Đã tăng âm lượng" else "Đã giảm âm lượng") +
                (execution.actualValue?.let { ", mức $it." } ?: ".")
        is VoiceCommand.Navigate -> "Đã mở dẫn đường đến ${shortText(command.destination)}${navigationSuffix(command.provider)}."
    }

    private fun unverified(command: VoiceCommand, execution: CommandExecutionResult.AcceptedUnverified): String {
        val action = when (command) {
            is VoiceCommand.SetTemperature, is VoiceCommand.AdjustTemperature, VoiceCommand.TemperatureHigh, VoiceCommand.TemperatureLow -> "điều chỉnh nhiệt độ"
            is VoiceCommand.AcPower -> "điều khiển điều hòa"
            is VoiceCommand.SetFanSpeed, is VoiceCommand.AdjustFanSpeed -> "điều chỉnh quạt"
            is VoiceCommand.Recirculation -> "chuyển chế độ lấy gió"
            is VoiceCommand.Defrost -> "điều khiển sấy kính"
            is VoiceCommand.FanDirection -> "điều chỉnh hướng gió"
            is VoiceCommand.HvacEco -> if (command.on) "bật điều hòa tiết kiệm" else "tắt điều hòa tiết kiệm"
            is VoiceCommand.HvacAuto -> "đổi AUTO điều hòa"
            is VoiceCommand.SetAutoFanProfile -> "chỉnh quạt điều hòa tự động"
            is VoiceCommand.OemSwitch -> "đổi trạng thái ${command.feature.label}"
            is VoiceCommand.QueryOemSwitch -> "đọc trạng thái ${command.feature.label}"
            is VoiceCommand.SetSunroofTilt -> "điều khiển độ nghiêng cửa sổ trời"
            is VoiceCommand.HvacAirflowAutoCycle -> when {
                command.periodicMinutes != null -> if (command.on) "bật đảo hướng gió định kỳ" else "tắt đảo hướng gió"
                command.on -> "bật tự động đảo gió"
                else -> "tắt tự động đảo gió"
            }
            is VoiceCommand.SetDriveMode -> "chuyển chế độ lái"
            is VoiceCommand.SetRegenLevel -> "điều chỉnh hồi năng lượng"
            is VoiceCommand.SetBrakePedalFeel -> "điều chỉnh cảm giác phanh"
            is VoiceCommand.ComfortableStop -> "điều chỉnh chế độ dừng êm"
            is VoiceCommand.SetChargeCurrentLimit -> if (command.control != null) "điều khiển sạc" else "đặt giới hạn dòng sạc"
            is VoiceCommand.AmbientLightPower -> "bật/tắt đèn viền"
            is VoiceCommand.SetAmbientBrightness -> "điều chỉnh độ sáng đèn viền"
            is VoiceCommand.SetAmbientColor -> "đổi màu đèn viền"
            is VoiceCommand.InteriorLightOnDoorOpen -> "điều chỉnh đèn nội thất khi mở cửa"
            is VoiceCommand.SetFollowMeHome -> "điều chỉnh Follow-Me-Home"
            is VoiceCommand.WirelessChargerPower -> "điều khiển sạc không dây"
            is VoiceCommand.SetDisplayTheme -> "đổi giao diện màn hình"
            is VoiceCommand.ParkingWiperAutoReduction -> "điều chỉnh gạt mưa khi đỗ"
            is VoiceCommand.SetParkingModeDuration -> "điều chỉnh chế độ đỗ xe"
            is VoiceCommand.QueryVehicleStatus -> "đọc trạng thái xe"
            is VoiceCommand.QuerySceneCapability -> "kiểm tra khả năng scene"
            is VoiceCommand.OpenSceneScreen -> "mở màn hình chế độ ${sceneText(command.scene)}"
            VoiceCommand.CloseAllApps -> "về màn hình chính"
            is VoiceCommand.ScreenBlackout -> if (command.on) "tắt màn hình" else "bật lại màn hình"
            is VoiceCommand.QueryLocalInfo -> "xem thông tin"
            is VoiceCommand.QueryWindowingCapability -> "kiểm tra khả năng cửa sổ"
            is VoiceCommand.SplitScreen -> splitActionText(command)
            VoiceCommand.QueryWifiState -> "đọc trạng thái Wi-Fi"
            VoiceCommand.QueryWifiIpAddress -> "đọc IP Wi-Fi"
            VoiceCommand.QueryWifiSsid -> "đọc tên mạng Wi-Fi"
            VoiceCommand.OpenWifiSettings -> "mở cài đặt Wi-Fi"
            VoiceCommand.QueryDisplayBrightness -> "đọc thiết lập độ sáng màn hình"
            is VoiceCommand.SetManualDisplayBrightness -> if (command.mode != null) "đổi chế độ độ sáng hệ thống" else "đặt mức sáng hệ thống"
            is VoiceCommand.Window -> "điều khiển kính"
            is VoiceCommand.Sunroof -> "điều khiển ${sunroofPartText(command.part)}"
            is VoiceCommand.CabinLight -> if (command.on) "bật đèn trong xe" else "tắt đèn trong xe"
            is VoiceCommand.SeatClimate -> seatClimateAction(command, null)
            is VoiceCommand.SteeringWheelHeat -> if (command.on) "bật sưởi vô lăng" else "tắt sưởi vô lăng"
            is VoiceCommand.MaxAc -> if (command.on) "bật làm lạnh nhanh" else "tắt làm lạnh nhanh"
            is VoiceCommand.ExteriorLight -> exteriorLightText(command.mode)
            is VoiceCommand.FogLight -> if (command.on) "bật đèn sương mù" else "tắt đèn sương mù"
            is VoiceCommand.SetDoorLock -> if (command.locked) "khóa cửa" else "mở khóa cửa"
            VoiceCommand.OpenFrontTrunk -> "mở cốp trước"
            VoiceCommand.UnlockRearTrunk -> "mở khóa cửa xe để dùng cốp sau"
            is VoiceCommand.SetHeadlightHeight -> "điều chỉnh độ cao đèn trước"
            is VoiceCommand.SetIntelligentHighBeam -> if (command.enabled) "bật đèn pha thông minh" else "tắt đèn pha thông minh"
            is VoiceCommand.OpenApp -> "mở ${appText(command.target)}"
            is VoiceCommand.OpenInstalledApp -> "mở ứng dụng"
            is VoiceCommand.OpenMedia -> "mở ${mediaKindText(command.kind)}"
            is VoiceCommand.SearchMedia -> "mở tìm kiếm media"
            is VoiceCommand.MediaControl -> "điều khiển media"
            is VoiceCommand.SetMediaVolume, is VoiceCommand.AdjustMediaVolume -> "chỉnh âm lượng"
            is VoiceCommand.Navigate -> "mở dẫn đường"
        }
        val suffix = execution.detail?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        return "Đã gửi lệnh $action, nhưng chưa xác nhận được trạng thái.$suffix".trim()
    }

    private fun splitActionText(command: VoiceCommand.SplitScreen): String = when (command.action) {
        SplitAction.START -> {
            val left = command.left
            val right = command.right
            if (left != null && right != null) "chia màn hình ${appText(left)} với ${appText(right)}"
            else "chia đôi màn hình"
        }
        SplitAction.SWAP -> "đổi bên chia màn hình"
        SplitAction.RATIO -> "chỉnh bên trái ${command.ratio ?: 50} phần trăm"
        SplitAction.REPLACE -> "đổi ${splitSideText(command.side)} sang ${command.app?.let(::appText) ?: "ứng dụng khác"}"
        SplitAction.FULLSCREEN -> "mở ${command.app?.let(::appText) ?: splitSideText(command.side)} toàn màn hình"
        SplitAction.RESTORE -> "khôi phục chia màn hình"
        SplitAction.HIDE -> "ẩn thanh chia màn hình"
        SplitAction.SHOW -> "hiện thanh chia màn hình"
        SplitAction.STOP -> "tắt chia màn hình"
        SplitAction.RECOVER -> "gỡ kẹt màn hình"
    }

    private fun splitSideText(side: SplitSide?): String = when (side) {
        SplitSide.LEFT -> "bên trái"
        SplitSide.RIGHT -> "bên phải"
        null -> "một bên"
    }

    private fun windowingText(mode: WindowingMode): String = when (mode) {
        WindowingMode.PIP -> "PiP"
        WindowingMode.FREEFORM -> "cửa sổ tự do"
        WindowingMode.SPLIT_SCREEN -> "chia đôi màn hình"
    }

    /** Spoken time/date/status; kept in this file so every word reaches the TTS lexicon. */
    internal fun localInfoText(kind: LocalInfoKind, raw: String?): String = when (kind) {
        LocalInfoKind.TIME -> {
            val parts = raw?.split(":")?.mapNotNull { it.toIntOrNull() }
            if (parts == null || parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) {
                "Không đọc được giờ hệ thống."
            } else if (parts[1] == 0) {
                "Bây giờ là ${parts[0]} giờ."
            } else {
                "Bây giờ là ${parts[0]} giờ ${parts[1]} phút."
            }
        }
        LocalInfoKind.DATE -> {
            val parts = raw?.split("|")?.mapNotNull { it.toIntOrNull() }
            if (parts == null || parts.size != 3 || parts[0] !in 1..7 || parts[1] !in 1..31 || parts[2] !in 1..12) {
                "Không đọc được ngày hệ thống."
            } else {
                "Hôm nay là ${weekdayText(parts[0])}, ngày ${parts[1]} tháng ${parts[2]}."
            }
        }
        LocalInfoKind.ASSISTANT_STATUS -> "Trợ lý đang sẵn sàng, bạn cứ nói lệnh."
    }

    private fun weekdayText(isoDay: Int): String = when (isoDay) {
        1 -> "thứ hai"
        2 -> "thứ ba"
        3 -> "thứ tư"
        4 -> "thứ năm"
        5 -> "thứ sáu"
        6 -> "thứ bảy"
        else -> "chủ nhật"
    }

    private fun sceneText(scene: SceneType): String = when (scene) {
        SceneType.REST -> "nghỉ"
        SceneType.CAMPING -> "cắm trại"
    }

    private fun vehicleStatusAck(query: VehicleStatusQuery): String = when (query) {
        VehicleStatusQuery.CAR_SUMMARY -> "Đang kiểm tra tình trạng xe."
        VehicleStatusQuery.SPEED -> "Đang kiểm tra tốc độ xe."
        VehicleStatusQuery.GEAR -> "Đang kiểm tra vị trí số."
        VehicleStatusQuery.BATTERY_SOC -> "Đang kiểm tra phần trăm pin."
        VehicleStatusQuery.RANGE -> "Đang kiểm tra quãng đường còn lại."
        VehicleStatusQuery.ODOMETER -> "Đang kiểm tra ODO."
        VehicleStatusQuery.TRIP_DISTANCE -> "Đang kiểm tra quãng đường chuyến đi."
        VehicleStatusQuery.OUTSIDE_TEMPERATURE -> "Đang kiểm tra nhiệt độ ngoài xe."
        VehicleStatusQuery.DOOR_STATUS -> "Đang kiểm tra trạng thái cửa."
        VehicleStatusQuery.DOOR_LOCK_STATUS -> "Đang kiểm tra trạng thái khóa cửa."
        VehicleStatusQuery.CHARGE_STATUS -> "Đang kiểm tra trạng thái sạc."
        VehicleStatusQuery.CHARGE_CURRENT -> "Đang kiểm tra dòng sạc."
        VehicleStatusQuery.CHARGE_VOLTAGE -> "Đang kiểm tra điện áp sạc."
        VehicleStatusQuery.CHARGE_CURRENT_LIMIT -> "Đang kiểm tra giới hạn dòng sạc."
        VehicleStatusQuery.PARKING_WIPER_AUTO_REDUCTION -> "Đang kiểm tra cài đặt gạt mưa khi đỗ."
        VehicleStatusQuery.PARKING_MODE_DURATION -> "Đang kiểm tra thời lượng chế độ đỗ."
        VehicleStatusQuery.AMBIENT_LIGHT_POWER -> "Đang kiểm tra trạng thái đèn viền."
        VehicleStatusQuery.AMBIENT_BRIGHTNESS -> "Đang kiểm tra độ sáng đèn viền."
        VehicleStatusQuery.AMBIENT_COLOR -> "Đang kiểm tra màu đèn viền."
        VehicleStatusQuery.INTERIOR_LIGHT_ON_DOOR_OPEN -> "Đang kiểm tra đèn nội thất khi mở cửa."
        VehicleStatusQuery.FOLLOW_ME_HOME -> "Đang kiểm tra Follow-Me-Home."
        VehicleStatusQuery.WIRELESS_CHARGER -> "Đang kiểm tra trạng thái sạc không dây."
        VehicleStatusQuery.CABIN_TEMPERATURE -> "Đang kiểm tra nhiệt độ trong xe."
        VehicleStatusQuery.AUTO_CLOSE_WINDOWS_ON_LOCK -> "Đang kiểm tra cài đặt tự đóng kính khi khóa xe."
        VehicleStatusQuery.DISPLAY_THEME -> "Đang kiểm tra chế độ giao diện màn hình."
        VehicleStatusQuery.PASSENGER_OCCUPANCY -> "Đang kiểm tra ghế phụ phía trước."
        VehicleStatusQuery.DRIVER_SEATBELT -> "Đang kiểm tra dây an toàn tài xế."
        VehicleStatusQuery.PASSENGER_SEATBELT -> "Đang kiểm tra dây an toàn ghế phụ."
        VehicleStatusQuery.CLIMATE_TEMPERATURE -> "Đang kiểm tra nhiệt độ điều hòa."
        VehicleStatusQuery.FAN_SPEED -> "Đang kiểm tra mức quạt."
        VehicleStatusQuery.DRIVE_MODE -> "Đang kiểm tra chế độ lái."
        VehicleStatusQuery.REGEN_LEVEL -> "Đang kiểm tra mức hồi năng lượng."
        VehicleStatusQuery.CRUISE_STATUS -> "Đang kiểm tra trạng thái ga tự động."
        VehicleStatusQuery.BRAKE_PEDAL_FEEL -> "Đang kiểm tra cảm giác bàn đạp phanh."
        VehicleStatusQuery.COMFORTABLE_STOP_STATUS -> "Đang kiểm tra trạng thái dừng êm."
        VehicleStatusQuery.FUEL_LEVEL -> "Đang kiểm tra mức xăng."
        VehicleStatusQuery.AVERAGE_FUEL_CONSUMPTION -> "Đang kiểm tra mức tiêu hao nhiên liệu."
        VehicleStatusQuery.COOLANT_TEMPERATURE -> "Đang kiểm tra nhiệt độ nước làm mát."
        VehicleStatusQuery.BATTERY_VOLTAGE -> "Đang kiểm tra điện áp ắc quy."
        VehicleStatusQuery.TIRE_PRESSURE -> "Đang kiểm tra áp suất lốp."
        VehicleStatusQuery.SERVICE_DUE -> "Đang kiểm tra lịch bảo dưỡng."
    }

    private fun appText(target: AppTarget): String = when (target) {
        AppTarget.YOUTUBE -> "YouTube"
        AppTarget.VANCED -> "YouTube Vanced"
        AppTarget.REVANCED -> "YouTube ReVanced"
        AppTarget.MORPHE -> "Morphe"
        AppTarget.YOUTUBE_MUSIC -> "YouTube Music"
        AppTarget.ZING_MP3 -> "Zing MP3"
        AppTarget.SPOTIFY -> "Spotify"
        AppTarget.SMARTTUBE -> "SmartTube"
        AppTarget.MAPS -> "Google Maps"
        AppTarget.DEFAULT_MAPS -> "bản đồ"
        AppTarget.WAZE -> "Waze"
        AppTarget.VIETMAP -> "Vietmap Live"
        AppTarget.CHROME -> "Chrome"
        AppTarget.NETFLIX -> "Netflix"
        AppTarget.TIKTOK -> "TikTok"
        AppTarget.VLC -> "VLC"
        AppTarget.KIKI -> "Kiki"
    }

    private fun mediaProviderText(provider: MediaProvider): String = when (provider) {
        MediaProvider.DEFAULT -> "ứng dụng mặc định"
        MediaProvider.YOUTUBE -> "YouTube"
        MediaProvider.VANCED -> "YouTube Vanced"
        MediaProvider.REVANCED -> "YouTube ReVanced"
        MediaProvider.MORPHE -> "Morphe"
        MediaProvider.YOUTUBE_MUSIC -> "YouTube Music"
        MediaProvider.ZING_MP3 -> "Zing MP3"
        MediaProvider.SPOTIFY -> "Spotify"
        MediaProvider.SMARTTUBE -> "SmartTube"
    }

    private fun mediaProviderSuffix(provider: MediaProvider): String =
        if (provider == MediaProvider.DEFAULT) "" else " trên ${mediaProviderText(provider)}"

    private fun mediaKindText(kind: MediaContentType): String = when (kind) {
        MediaContentType.MUSIC -> "nhạc"
        MediaContentType.VIDEO -> "video"
    }

    private fun navigationSuffix(provider: NavigationProvider): String = when (provider) {
        NavigationProvider.AUTO -> ""
        NavigationProvider.GOOGLE_MAPS -> " bằng Google Maps"
        NavigationProvider.WAZE -> " bằng Waze"
        NavigationProvider.VIETMAP -> " bằng Vietmap Live"
    }

    private fun mediaAck(action: MediaAction): String = when (action) {
        MediaAction.PLAY -> "Đang tiếp tục phát."
        MediaAction.PAUSE -> "Đang tạm dừng."
        MediaAction.TOGGLE -> "Đang chuyển trạng thái phát."
        MediaAction.NEXT -> "Đang chuyển sang bài tiếp theo."
        MediaAction.PREVIOUS -> "Đang quay lại bài trước."
        MediaAction.VOLUME_UP -> "Đang tăng âm lượng."
        MediaAction.VOLUME_DOWN -> "Đang giảm âm lượng."
        MediaAction.MUTE -> "Đang tắt tiếng."
        MediaAction.UNMUTE -> "Đang bật lại tiếng."
    }

    private fun mediaConfirmed(action: MediaAction): String = when (action) {
        MediaAction.PLAY -> "Đã tiếp tục phát."
        MediaAction.PAUSE -> "Đã tạm dừng."
        MediaAction.TOGGLE -> "Đã chuyển trạng thái phát."
        MediaAction.NEXT -> "Đã chuyển sang bài tiếp theo."
        MediaAction.PREVIOUS -> "Đã quay lại bài trước."
        MediaAction.VOLUME_UP -> "Đã tăng âm lượng."
        MediaAction.VOLUME_DOWN -> "Đã giảm âm lượng."
        // Assistant speech shares the media stream, so this result is silent until unmuted.
        MediaAction.MUTE -> "Đã tắt tiếng."
        MediaAction.UNMUTE -> "Đã bật lại tiếng."
    }

    private fun driveModeText(mode: DriveMode): String = when (mode) {
        DriveMode.ECO -> "Eco"
        DriveMode.COMFORT -> "Comfort"
        DriveMode.SPORT -> "Sport"
    }

    private fun regenText(level: RegenLevel): String = when (level) {
        RegenLevel.LOW -> "mức thấp"
        RegenLevel.MEDIUM -> "mức trung bình"
        RegenLevel.HIGH -> "mức cao"
    }

    private fun brakeFeelText(mode: BrakePedalFeel): String = when (mode) {
        BrakePedalFeel.COMFORT -> "Comfort"
        BrakePedalFeel.SPORT -> "Sport"
    }

    private fun chargeControlAck(action: ChargeControlAction): String = when (action) {
        ChargeControlAction.STOP -> "Đang dừng sạc."
        ChargeControlAction.RESTART -> "Đang tiếp tục sạc."
        ChargeControlAction.START_NOW -> "Đang bắt đầu sạc ngay."
    }

    private fun chargeControlConfirmed(action: ChargeControlAction): String = when (action) {
        ChargeControlAction.STOP -> "Đã dừng sạc."
        ChargeControlAction.RESTART -> "Đã tiếp tục sạc."
        ChargeControlAction.START_NOW -> "Đã bắt đầu sạc."
    }

    private fun parkingDurationText(duration: ParkingModeDuration): String = when (duration) {
        ParkingModeDuration.OFF -> "sang tắt"
        ParkingModeDuration.MINUTES_30 -> "30 phút"
        ParkingModeDuration.HOURS_1 -> "1 giờ"
        ParkingModeDuration.HOURS_2 -> "2 giờ"
        ParkingModeDuration.HOURS_3 -> "3 giờ"
        ParkingModeDuration.HOURS_4 -> "4 giờ"
        ParkingModeDuration.HOURS_5 -> "5 giờ"
        ParkingModeDuration.CONTINUOUS -> "liên tục"
    }

    private fun ambientColorText(color: AmbientColor): String = when (color) {
        AmbientColor.RED -> "màu đỏ"
        AmbientColor.GREEN -> "màu xanh lá"
        AmbientColor.BLUE -> "màu xanh dương"
        AmbientColor.WHITE -> "màu trắng"
        AmbientColor.YELLOW -> "màu vàng"
        AmbientColor.PURPLE -> "màu tím"
        AmbientColor.PINK -> "màu hồng"
        AmbientColor.ORANGE -> "màu cam"
        AmbientColor.CYAN -> "màu xanh ngọc"
    }

    private fun followMeHomeText(duration: FollowMeHomeDuration): String = when (duration) {
        FollowMeHomeDuration.OFF -> "sang tắt"
        FollowMeHomeDuration.SECONDS_30 -> "30 giây"
        FollowMeHomeDuration.SECONDS_60 -> "60 giây"
        FollowMeHomeDuration.SECONDS_90 -> "90 giây"
    }

    private fun displayThemeText(mode: DisplayThemeMode): String = when (mode) {
        DisplayThemeMode.LIGHT -> "sang giao diện sáng"
        DisplayThemeMode.DARK -> "sang giao diện tối"
        DisplayThemeMode.AUTO -> "sang giao diện tự động"
    }

    private fun displayBrightnessModeText(mode: DisplayBrightnessMode): String = when (mode) {
        DisplayBrightnessMode.MANUAL -> "chế độ thủ công"
        DisplayBrightnessMode.AUTOMATIC -> "chế độ tự động"
    }

    private fun defrostAck(command: VoiceCommand.Defrost): String {
        val zone = if (command.zone == DefrostZone.FRONT) "kính trước" else "kính sau và gương"
        return if (command.on) "Đang bật sấy $zone." else "Đang tắt sấy $zone."
    }

    private fun defrostConfirmed(command: VoiceCommand.Defrost): String {
        val zone = if (command.zone == DefrostZone.FRONT) "kính trước" else "kính sau và gương"
        return if (command.on) "Đã bật sấy $zone." else "Đã tắt sấy $zone."
    }

    private fun fanDirectionText(directions: Set<FanDirectionTarget>): String = directions.joinToString(" và ") { when (it) {
        FanDirectionTarget.FACE -> "vào mặt"
        FanDirectionTarget.FEET -> "xuống chân"
        FanDirectionTarget.WINDSHIELD -> "lên kính"
    } }

    private fun windowAck(command: VoiceCommand.Window): String {
        val target = formatWindowTargets(command.targets)
        return when (command.operation) {
            WindowOperation.OPEN -> "Đang mở $target."
            WindowOperation.CLOSE -> "Đang đóng $target."
            WindowOperation.SET_PERCENT -> "Đang chỉnh $target xuống ${command.percent ?: 0} phần trăm."
            WindowOperation.STOP -> "Đang dừng $target."
        }
    }

    private fun windowConfirmed(command: VoiceCommand.Window, actualValue: String?): String {
        val target = formatWindowTargets(command.targets)
        return when (command.operation) {
            WindowOperation.OPEN -> "Đã mở $target."
            WindowOperation.CLOSE -> "Đã đóng $target."
            WindowOperation.SET_PERCENT -> "Đã chỉnh $target xuống ${actualValue ?: "${command.percent ?: 0} phần trăm"}."
            WindowOperation.STOP -> "Đã dừng $target."
        }
    }

    private fun seatClimateAction(command: VoiceCommand.SeatClimate, actualLevel: String?): String {
        val what = when (command.kind) {
            SeatClimateKind.HEAT -> "sưởi"
            SeatClimateKind.VENT -> "thông gió"
        }
        val seats = when {
            command.seats.size >= 2 -> "hai ghế trước"
            SeatTarget.PASSENGER in command.seats -> "ghế phụ"
            else -> "ghế lái"
        }
        val level = actualLevel ?: command.level?.toString()
        return when {
            command.level == 0 -> "tắt $what $seats"
            command.delta != null && actualLevel == null -> if (command.delta > 0) "tăng $what $seats" else "giảm $what $seats"
            else -> "đặt $what $seats mức ${level ?: ""}".trim()
        }
    }

    private fun exteriorLightText(mode: ExteriorLightMode): String = when (mode) {
        ExteriorLightMode.OFF -> "tắt đèn ngoài xe"
        ExteriorLightMode.POSITION -> "bật đèn định vị"
        ExteriorLightMode.LOW_BEAM -> "bật đèn chiếu gần"
    }

    private fun sunroofPartText(part: SunroofPart): String = when (part) {
        SunroofPart.GLASS -> "cửa sổ trời"
        SunroofPart.SHADE -> "rèm che nắng"
    }

    private fun sunroofAck(command: VoiceCommand.Sunroof): String {
        val target = sunroofPartText(command.part)
        return when (command.operation) {
            WindowOperation.OPEN -> "Đang mở $target."
            WindowOperation.CLOSE -> "Đang đóng $target."
            WindowOperation.SET_PERCENT -> "Đang mở $target ${command.percent ?: 0} phần trăm."
            WindowOperation.STOP -> "Đang dừng $target."
        }
    }

    private fun sunroofConfirmed(command: VoiceCommand.Sunroof, actualValue: String?): String {
        val target = sunroofPartText(command.part)
        return when (command.operation) {
            WindowOperation.OPEN -> "Đã mở $target."
            WindowOperation.CLOSE -> "Đã đóng $target."
            WindowOperation.SET_PERCENT -> "Đã mở $target ${actualValue ?: "${command.percent ?: 0} phần trăm"}."
            WindowOperation.STOP -> "Đã dừng $target."
        }
    }

    private fun formatWindowTargets(targets: Set<WindowTarget>): String {
        if (WindowTarget.ALL in targets || targets.size >= 4) return "tất cả kính"
        return targets.joinToString(" và ") { when (it) {
            WindowTarget.FRONT_LEFT -> "kính bên lái"
            WindowTarget.FRONT_RIGHT -> "kính trước bên phụ"
            WindowTarget.REAR_LEFT -> "kính sau bên lái"
            WindowTarget.REAR_RIGHT -> "kính sau bên phụ"
            WindowTarget.ALL -> "tất cả kính"
        } }
    }

    private fun shortText(value: String): String = value.trim().let { if (it.length <= 80) it else it.take(77) + "..." }
    private fun formatNumber(value: Float): String = when {
        value % 1f == 0f -> value.toInt().toString()
        kotlin.math.abs(value % 1f) == 0.5f -> if (value.toInt() == 0) "nửa" else "${value.toInt()} rưỡi"
        else -> value.toString()
    }
}
