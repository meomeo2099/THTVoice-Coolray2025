package com.voicegeely.app.voice

/**
 * [REF-04 / S06] Short follow-up phrases that refer to the previous command:
 * "tăng nữa", "giảm nữa", "thêm nữa", "về như cũ", "làm lại".
 *
 * Rules (FINDINGS §6):
 * - Only a [CommandExecutionResult.Confirmed] fan / temperature / volume command opens a context;
 *   a failed, rejected or unverified result, or any other command, clears it.
 * - The context lives [TTL_MS] (3 minutes); older context is never used.
 * - "Về như cũ" restores the value read from the car *before* the previous command; without that
 *   reading it does nothing. Never applied to doors, windows or the trunk.
 * - The phrase must be the whole utterance, so full commands still go to the normal parser.
 * - Output is a normal typed [VoiceCommand], so safety, read-back and result wording are unchanged.
 */
class FollowUpContext(private val ttlMs: Long = TTL_MS) {

    enum class Family { FAN, TEMPERATURE, VOLUME }
    private enum class Intent { MORE, LESS, WARMER, COOLER, SAME_DIRECTION, BACK, AGAIN }

    /** Value read from the car before a command, used by "về như cũ". */
    sealed class Snapshot {
        data class Fan(val level: Int) : Snapshot()
        data class Temperature(val celsius: Float) : Snapshot()
    }

    private data class Entry(val command: VoiceCommand, val before: Snapshot?, val atMs: Long)

    private var last: Entry? = null

    @Synchronized
    fun record(command: VoiceCommand, before: Snapshot?, result: CommandExecutionResult, nowMs: Long) {
        last = if (result is CommandExecutionResult.Confirmed && familyOf(command) != null) Entry(command, before, nowMs) else null
    }

    @Synchronized
    fun clear() { last = null }

    /** Typed command for a follow-up phrase, or null (no context, expired, or not a follow-up phrase). */
    @Synchronized
    fun resolve(text: String, nowMs: Long): ParsedVoiceCommand? {
        val entry = last ?: return null
        if (nowMs < entry.atMs || nowMs - entry.atMs > ttlMs) { last = null; return null }
        val normalized = normalize(text)
        val intent = intentOf(normalized) ?: return null
        val command = commandFor(entry, intent) ?: return null
        return ParsedVoiceCommand(text, normalized, command)
    }

    private fun commandFor(entry: Entry, intent: Intent): VoiceCommand? {
        val family = familyOf(entry.command) ?: return null
        val direction = when (intent) {
            Intent.MORE -> 1
            Intent.LESS -> -1
            // "nóng/ấm/lạnh/mát nữa" only mean temperature: for the fan "mát nữa" could mean either way.
            Intent.WARMER -> if (family == Family.TEMPERATURE) 1 else return null
            Intent.COOLER -> if (family == Family.TEMPERATURE) -1 else return null
            Intent.SAME_DIRECTION -> directionOf(entry.command) ?: return null
            Intent.AGAIN -> return entry.command
            Intent.BACK -> return restore(entry.before)
        }
        return when (family) {
            Family.FAN -> VoiceCommand.AdjustFanSpeed(direction * fanStep(entry.command))
            Family.TEMPERATURE -> VoiceCommand.AdjustTemperature(direction * temperatureStep(entry.command))
            Family.VOLUME -> (entry.command as? VoiceCommand.AdjustMediaVolume)
                ?.let { VoiceCommand.AdjustMediaVolume(direction * kotlin.math.abs(it.steps).coerceIn(1, MediaVolumeParser.MAX_BARE_STEPS)) }
                ?: VoiceCommand.MediaControl(if (direction > 0) MediaAction.VOLUME_UP else MediaAction.VOLUME_DOWN)
        }
    }

    private fun restore(before: Snapshot?): VoiceCommand? = when (before) {
        is Snapshot.Fan -> VoiceCommand.SetFanSpeed(before.level)
        is Snapshot.Temperature -> VoiceCommand.SetTemperature(before.celsius)
        null -> null
    }

    companion object {
        const val TTL_MS = 180_000L

        fun familyOf(command: VoiceCommand): Family? = when (command) {
            is VoiceCommand.SetFanSpeed, is VoiceCommand.AdjustFanSpeed -> Family.FAN
            is VoiceCommand.SetTemperature, is VoiceCommand.AdjustTemperature,
            VoiceCommand.TemperatureHigh, VoiceCommand.TemperatureLow -> Family.TEMPERATURE
            is VoiceCommand.MediaControl -> when (command.action) {
                MediaAction.VOLUME_UP, MediaAction.VOLUME_DOWN -> Family.VOLUME
                else -> null
            }
            is VoiceCommand.SetMediaVolume, is VoiceCommand.AdjustMediaVolume -> Family.VOLUME
            else -> null
        }

        /** Families whose "before" value is read from the car so "về như cũ" can restore it. */
        fun restorable(command: VoiceCommand): Family? = familyOf(command)?.takeIf { it != Family.VOLUME }

        private fun directionOf(command: VoiceCommand): Int? = when (command) {
            is VoiceCommand.AdjustFanSpeed -> if (command.delta > 0) 1 else if (command.delta < 0) -1 else null
            is VoiceCommand.AdjustTemperature -> if (command.deltaCelsius > 0f) 1 else if (command.deltaCelsius < 0f) -1 else null
            VoiceCommand.TemperatureHigh -> 1
            VoiceCommand.TemperatureLow -> -1
            is VoiceCommand.MediaControl -> when (command.action) {
                MediaAction.VOLUME_UP -> 1
                MediaAction.VOLUME_DOWN -> -1
                else -> null
            }
            is VoiceCommand.AdjustMediaVolume -> if (command.steps > 0) 1 else if (command.steps < 0) -1 else null
            else -> null
        }

        private fun fanStep(command: VoiceCommand): Int =
            (command as? VoiceCommand.AdjustFanSpeed)?.delta?.let { kotlin.math.abs(it) }?.takeIf { it in 1..3 } ?: 1

        private fun temperatureStep(command: VoiceCommand): Float =
            (command as? VoiceCommand.AdjustTemperature)?.deltaCelsius?.let { kotlin.math.abs(it) }?.takeIf { it in 0.5f..3f }
                ?: com.voicegeely.app.car.CoolrayProperties.HVAC_TEMP_STEP

        private val MORE_WORDS = "tăng|to|lớn|mạnh|lên|cao"
        private val LESS_WORDS = "giảm|bớt|nhỏ|yếu|xuống|thấp|hạ"
        private val WARM_WORDS = "nóng|ấm|ấm áp"
        private val COOL_WORDS = "lạnh|mát|lạnh hơn|mát hơn"
        private val SOFT_TAIL = "(?: (?:đi|nha|nhé|nhe|nghen|ha|giùm|dùm|giúp|chút|một chút|tí|một tí|xíu|một xíu|chút nữa|tí nữa|xíu nữa|thêm|lên|xuống))*"
        private val NUA = "(?:nữa|nưa|nửa|nũa|lữa|nự|nứa|nựa|lựa|nửa|nưã)"

        private val MORE = Regex("^(?:(?:$MORE_WORDS)(?: (?:$MORE_WORDS))? (?:thêm )?$NUA|thêm (?:$MORE_WORDS) $NUA|(?:$MORE_WORDS) (?:thêm )?(?:chút|tí|xíu) $NUA)$SOFT_TAIL$")
        private val LESS = Regex("^(?:(?:$LESS_WORDS)(?: (?:$LESS_WORDS))? (?:thêm )?$NUA|(?:$LESS_WORDS) (?:thêm )?(?:chút|tí|xíu) $NUA|bớt (?:$LESS_WORDS) $NUA)$SOFT_TAIL$")
        private val WARMER = Regex("^(?:(?:cho )?(?:$WARM_WORDS)(?: (?:thêm|hơn))?(?: (?:chút|tí|xíu))? $NUA|(?:vẫn )?(?:còn )?(?:lạnh|rét) quá)$SOFT_TAIL$")
        private val COOLER = Regex("^(?:(?:cho )?(?:$COOL_WORDS)(?: (?:thêm|hơn))?(?: (?:chút|tí|xíu))? $NUA|(?:vẫn )?(?:còn )?(?:nóng|nực|bức) quá)$SOFT_TAIL$")
        private val SAME = Regex("^(?:thêm $NUA|$NUA|$NUA đi|thêm (?:chút|tí|xíu)(?: $NUA)?|chút $NUA|tí $NUA|xíu $NUA)$SOFT_TAIL$")
        private val BACK = Regex("^(?:(?:đổi |chỉnh |để |cho |trả |quay |trở )?(?:lại |về )(?:như |giống )?(?:cũ|ban đầu|lúc nãy|hồi nãy|khi nãy|như trước|lúc trước)|(?:để |cho |trả |đổi |chỉnh )?(?:như|giống) (?:cũ|lúc nãy|hồi nãy|ban đầu)|hoàn tác|bỏ lệnh vừa rồi|thôi để như cũ|thôi trả lại)$SOFT_TAIL$")
        private val AGAIN = Regex("^(?:làm lại|lặp lại|thêm lần nữa|lại lần nữa|làm lần nữa|một lần nữa|lần nữa)$SOFT_TAIL$")

        private fun intentOf(text: String): Intent? = when {
            AGAIN.matches(text) -> Intent.AGAIN
            BACK.matches(text) -> Intent.BACK
            WARMER.matches(text) -> Intent.WARMER
            COOLER.matches(text) -> Intent.COOLER
            MORE.matches(text) -> Intent.MORE
            LESS.matches(text) -> Intent.LESS
            SAME.matches(text) -> Intent.SAME_DIRECTION
            else -> null
        }

        private val POLITE = Regex("^(?:(?:ê|ơi|à|này|nè|vậy|thì|còn|giờ|bây giờ|cho tôi|cho tui|cho tao|cho mình|cho em|cho anh|cho chị|cho con|hey geely|hello geely|hey tht|hi geely) )+")
        private val TRAILING = Regex("(?: (?:ạ|à|nhé|nhá|nha|nhe|nghen|nhen|hen|hỉ|nờ|rứa|nè|đi|đi mà|coi|thử coi|giùm|dùm|giúp|với|cái|luôn|liền|lẹ|nghe|ha|hè))+$")

        /**
         * Regional speech and ASR slips, applied only to short follow-up phrases (whole utterance),
         * so they never touch other commands: Southern "dìa/dề" = về, Northern n/l ("nàm nại",
         * "nạnh", "nên nữa"), wrong tones ("tang", "dảm", "them", "bợt", "như củ", "hoàn tát").
         */
        private val SLIPS: List<Pair<Regex, String>> = listOf(
            Regex("(?<![\\p{L}])(?:dìa|dề|vìa|zề|zìa)(?![\\p{L}])") to "về",
            Regex("(?<![\\p{L}])như (?:củ|cú|cụ|cù|cu)(?![\\p{L}])") to "như cũ",
            Regex("(?<![\\p{L}])(?:nhu|nhứ|như) cũ(?![\\p{L}])") to "như cũ",
            Regex("(?<![\\p{L}])y như cũ(?![\\p{L}])") to "như cũ",
            Regex("(?<![\\p{L}])hoàn (?:tát|tắc|tạc|tác)(?![\\p{L}])") to "hoàn tác",
            Regex("(?<![\\p{L}])nàm(?![\\p{L}])") to "làm",
            Regex("(?<![\\p{L}])nại(?![\\p{L}])") to "lại",
            Regex("(?<![\\p{L}])nần(?![\\p{L}])") to "lần",
            Regex("(?<![\\p{L}])nạnh(?![\\p{L}])") to "lạnh",
            Regex("(?<![\\p{L}])lóng(?![\\p{L}])") to "nóng",
            Regex("(?<![\\p{L}])nên(?= (?:nữa|thêm|chút|tí))") to "lên",
            Regex("^(?:tang|tâng|tắng|tầng|tanh)(?= )") to "tăng",
            Regex("^(?:dảm|giám|giạm|dạm|dám)(?= )") to "giảm",
            Regex("(?<![\\p{L}])(?:them|thềm|thêm thêm)(?![\\p{L}])") to "thêm",
            Regex("^(?:bợt|bớc|bợc)(?= )") to "bớt",
            Regex("^(?:mãnh|mảnh)(?= )") to "mạnh",
            Regex("^yêu(?= (?:nữa|thêm|chút|tí|xíu|lại))") to "yếu",
            Regex("(?<![\\p{L}])bự(?= (?:nữa|thêm|chút|tí|xíu))") to "to",
            Regex("(?<![\\p{L}])(?:chút xíu|tí xíu|tẹo|tí tẹo|chút chút)(?![\\p{L}])") to "chút",
            Regex("(?<![\\p{L}])(?:như hồi nãy|như khi nãy|như ban nãy|như lúc đầu|như hồi đầu)$") to "như cũ"
        )

        fun normalize(text: String): String {
            var base = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .lowercase()
                .replace(Regex("[.,!?;:\"'()\\[\\]…]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            base = base.replace(POLITE, "").replace(TRAILING, "").trim()
            SLIPS.forEach { (r, to) -> base = r.replace(base, to) }
            return base.trim()
        }
    }
}
