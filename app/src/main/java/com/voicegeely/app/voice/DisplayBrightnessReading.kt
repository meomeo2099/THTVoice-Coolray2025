package com.voicegeely.app.voice

/** Android Settings.System values describe a preference, not measured screen luminance. */
internal object DisplayBrightnessReading {
    const val MODE_MANUAL = 0
    const val MODE_AUTOMATIC = 1

    enum class Mode { MANUAL, AUTOMATIC }

    fun levelForPercent(percent: Int): Int? = percent.takeIf { it in 1..100 }
        ?.let { ((it * 255 + 50) / 100).coerceIn(1, 255) }

    fun settingValue(mode: Mode): Int = when (mode) {
        Mode.MANUAL -> MODE_MANUAL
        Mode.AUTOMATIC -> MODE_AUTOMATIC
    }

    fun modeFromSetting(value: Int): Mode? = when (value) {
        MODE_MANUAL -> Mode.MANUAL
        MODE_AUTOMATIC -> Mode.AUTOMATIC
        else -> null
    }

    fun modeDescription(mode: Mode): String = when (mode) {
        Mode.MANUAL -> "chế độ thủ công"
        Mode.AUTOMATIC -> "chế độ tự động"
    }

    /** Strict mode read-back used after a future Settings.System write. */
    fun modeMatches(requested: Mode, actualSetting: Int): Boolean =
        modeFromSetting(actualSetting) == requested

    fun describe(mode: Int, manualLevel: Int?): String? = when (mode) {
        MODE_AUTOMATIC -> "Thiết lập độ sáng hệ thống đang ở chế độ tự động"
        MODE_MANUAL -> manualLevel?.takeIf { it in 1..255 }?.let { level ->
            val percent = ((level * 100 + 127) / 255).coerceIn(1, 100)
            "Mức đặt độ sáng hệ thống là $percent phần trăm"
        }
        else -> null
    }
}
