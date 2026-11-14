package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties

/** Whole requests from the owner's shared corpus; existing typed handlers own all execution. */
internal object SharedCommandLanguage {
    private val aliases: Map<String, VoiceCommand> by lazy {
        val result = linkedMapOf<String, VoiceCommand>()
        fun add(command: VoiceCommand, vararg phrases: String) {
            for (phrase in phrases) {
                val key = VoiceTextNormalizer.normalize(phrase)
                val previous = result.put(key, command)
                require(previous == null || previous == command) { "Conflicting shared command alias" }
            }
        }
        add(VoiceCommand.AcPower(true),
            "cho mát xe đi", "làm mát xe ngay", "bật lạnh sâu", "nóng chết đi được", "nóng thế", "sao nóng thế", "nóng quá bạn ơi")
        add(VoiceCommand.AcPower(false),
            "lạnh cóng", "lạnh buốt", "lạnh run", "cho ấm xe lên", "lạnh thế", "sao lạnh thế", "lạnh teo rồi")
        add(VoiceCommand.AdjustTemperature(CoolrayProperties.HVAC_TEMP_STEP),
            "lạnh quá tăng độ lên", "nâng nhiệt lên tí")
        add(VoiceCommand.AdjustTemperature(-CoolrayProperties.HVAC_TEMP_STEP),
            "nóng quá giảm độ đi", "nóng quá giảm độ", "cho mát thêm", "cho mát thêm tí nữa", "cho mát thêm chút", "hạ độ xuống", "mát thêm")
        add(VoiceCommand.Recirculation(false),
            "trong xe có mùi", "xe bí quá", "ngột ngạt quá", "hút mùi ra", "bí bách quá")
        add(VoiceCommand.SetFanSpeed(CoolrayProperties.HVAC_MAX_FAN),
            "gió số cực đại", "mức gió to nhất", "mức gió cực đại")
        add(VoiceCommand.SetFanSpeed(CoolrayProperties.HVAC_MIN_FAN),
            "gió nhỏ nhất", "gió nhẹ nhất", "gió thấp nhất", "điều hòa mức nhỏ nhất")
        add(VoiceCommand.SetFanSpeed(3),
            "bậc giáo ba", "bậc gió ba", "bật giáo ba")
        add(VoiceCommand.MaxAc(true),
            "điều hòa làm mát nhanh", "máy lạnh làm mát nhanh")
        add(VoiceCommand.Defrost(DefrostZone.FRONT, true),
            "sấy giúp tôi kính lái")
        add(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.ODOMETER),
            "tổng số km đã đi", "số km đã đi", "tổng km đã đi")
        add(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FAN_SPEED),
            "quạt đang cấp mấy")
        add(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CLIMATE_TEMPERATURE),
            "điều hòa đang thế nào", "trạng thái điều hòa")
        add(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.SET_PERCENT, 10),
            "hé cửa kính một chút", "hạ hé cửa sổ", "hé cửa kiếng một chút")
        add(VoiceCommand.OpenMedia(MediaContentType.MUSIC),
            "mở cho tôi một bài nhạc", "mở cho tôi bài nhạc", "mở cho tôi một bài nhạc hay", "mở cho tôi bài nhạc hay")
        add(VoiceCommand.OpenMedia(MediaContentType.VIDEO),
            "xem video", "mở trình phát video")
        add(VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.YOUTUBE),
            "xem tu be", "chạy youty")
        add(VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.SMARTTUBE),
            "mở xì mát túp")
        add(VoiceCommand.OpenMedia(MediaContentType.MUSIC, MediaProvider.YOUTUBE_MUSIC),
            "youtube music")
        add(VoiceCommand.OpenApp(AppTarget.WAZE),
            "bản đồ waze", "bản đồ quây", "bản đồ quê dơ", "bản đồ vê dơ", "waze")
        add(VoiceCommand.OpenApp(AppTarget.MAPS),
            "bản đồ google")
        add(VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS),
            "dẫn đường")
        add(VoiceCommand.OpenInstalledApp("vtv go"),
            "xem vtv", "tv go", "tvgo", "vtv go", "vtvgo")
        add(VoiceCommand.OpenInstalledApp("fpt play"),
            "xem fpt play")
        add(VoiceCommand.OpenInstalledApp("vieon"),
            "xem vi on")
        add(VoiceCommand.OpenInstalledApp("tv360"),
            "xem tv360")
        add(VoiceCommand.OpenInstalledApp("gofa"),
            "chạy gofa")
        add(VoiceCommand.OpenInstalledApp("file manager"),
            "quản lý file", "duyệt file")
        add(VoiceCommand.OpenApp(AppTarget.KIKI),
            "trợ lý kiki")
        add(VoiceCommand.OpenApp(AppTarget.CHROME),
            "lướt web")
        add(VoiceCommand.OpenApp(AppTarget.VIETMAP),
            "mở viên map li", "mở việc map lie", "mở vietmap lie", "mở việt map lai", "mở viên map", "mở viện map", "mở viết mép li", "mở vietmap live", "mở vietmap", "mở map live", "mở map lie")
        result
    }

    fun resolve(normalized: String): VoiceCommand? = aliases[normalized]
}
