package com.voicegeely.app.voice

/**
 * [REF-05 / S05] Phrases the user teaches THT Voice ("máy lạnh" → "bật điều hòa").
 *
 * Rules:
 * - At most [MAX_ALIASES] aliases, stored locally only (no ASR/LLM training, no network).
 * - The target must parse to a typed command with the normal parser; the alias resolves to that
 *   parse, so safety gate, read-back and result wording are unchanged.
 * - A phrase that already means something (built-in command, follow-up, confirm/cancel, teach/forget
 *   words) cannot be taught: built-in commands are never overridden. At runtime the normal parser
 *   still runs first, so an alias only applies when nothing built-in understands the sentence.
 * - Whole-utterance match on the normalized phrase; no fuzzy matching of numbers or targets.
 */
class UserAliasBook(
    private val parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse,
    private val isReserved: (String) -> Boolean = ::defaultReserved,
    /** Owner 16/10/2026 "kịch bản": a target may be several commands ("… rồi …"), max 4. */
    private val planner: (String) -> MultiCommandPlan.Result? = { MultiCommandPlan.plan(it, parse) }
) {
    data class Alias(val phrase: String, val target: String)

    sealed class TeachResult {
        data class Saved(val alias: Alias, val command: VoiceCommand, val replaced: Boolean, val steps: Int = 1) : TeachResult()
        data class Refused(val reason: String) : TeachResult()
    }

    private val aliases = LinkedHashMap<String, Alias>()

    @Synchronized
    fun all(): List<Alias> = aliases.values.toList()

    @Synchronized
    fun teach(phraseText: String, targetText: String): TeachResult {
        val phrase = normalize(phraseText)
        // A comma between commands means the same as "rồi" (normalizing would drop it).
        val target = normalize(targetText.replace(Regex("\\s*[,;]\\s*"), " rồi "))
        if (phrase.length < MIN_LEN || phrase.length > MAX_LEN) return TeachResult.Refused("Tên gọi phải dài từ $MIN_LEN đến $MAX_LEN ký tự.")
        if (target.isEmpty()) return TeachResult.Refused("Chưa có lệnh để gán cho \"$phrase\".")
        if (phrase == target) return TeachResult.Refused("Tên gọi và lệnh giống nhau.")
        if (isReserved(phrase)) return TeachResult.Refused("\"$phrase\" là câu dành riêng cho trợ lý, không đặt được.")
        if (parse(phrase) != null) return TeachResult.Refused("\"$phrase\" đã là một lệnh có sẵn.")
        if (UserAliasCommands.parse(target) != null) return TeachResult.Refused("Không gán tên gọi cho lệnh dạy hoặc quên.")
        // Same order as live speech: several commands first, then one command.
        var steps = 1
        val command = when (val plan = planner(target)) {
            is MultiCommandPlan.Result.Steps -> { steps = plan.steps.size; plan.steps.first().command }
            MultiCommandPlan.Result.TooMany -> return TeachResult.Refused("Một kịch bản chỉ được tối đa ${MultiCommandPlan.MAX_STEPS} lệnh.")
            null -> {
                val single = parse(target)?.command ?: return TeachResult.Refused("Trợ lý chưa hiểu lệnh \"$target\".")
                // "chỉ đường tới bến thành và chợ lớn" is one destination; "tắt điều hòa rồi <lạ>" is not one command.
                val freeText = single is VoiceCommand.Navigate || single is VoiceCommand.SearchMedia || single is VoiceCommand.OpenInstalledApp
                if (MultiCommandPlan.partCount(target) > 1 && !freeText)
                    return TeachResult.Refused("Có lệnh trong kịch bản trợ lý chưa hiểu. Hãy nói từng lệnh có sẵn, nối bằng \"rồi\".")
                single
            }
        }
        val replaced = aliases.containsKey(phrase)
        if (!replaced && aliases.size >= MAX_ALIASES) return TeachResult.Refused("Đã đủ $MAX_ALIASES tên gọi. Xoá bớt trong Cài đặt.")
        val alias = Alias(phrase, target)
        aliases[phrase] = alias
        return TeachResult.Saved(alias, command, replaced, steps)
    }

    @Synchronized
    fun forget(phraseText: String): Boolean = aliases.remove(normalize(phraseText)) != null

    /** The typed command for a taught phrase (whole utterance), or null. */
    @Synchronized
    fun resolve(text: String): ParsedVoiceCommand? {
        val phrase = normalize(text)
        val alias = aliases[phrase] ?: return null
        if (planner(alias.target) is MultiCommandPlan.Result.Steps) return null
        val parsed = parse(alias.target) ?: return null
        return ParsedVoiceCommand(text, phrase, parsed.command)
    }

    /**
     * Owner decision 14/10/2026: customers send their taught phrases to the owner themselves
     * (copy / share from Settings); nothing is uploaded. Plain readable text, one alias per line.
     */
    @Synchronized
    fun exportText(header: String = EXPORT_HEADER): String =
        if (aliases.isEmpty()) "" else buildString {
            append(header).append(" (").append(aliases.size).append(")\n")
            aliases.values.forEach { append("\"").append(it.phrase).append("\" → ").append(it.target).append('\n') }
        }.trimEnd()

    /** The steps of a taught scenario (2..4 commands), or null for a single-command alias / no alias. */
    @Synchronized
    fun resolveScenario(text: String): List<ParsedVoiceCommand>? {
        val phrase = normalize(text)
        val alias = aliases[phrase] ?: return null
        val plan = planner(alias.target) as? MultiCommandPlan.Result.Steps ?: return null
        return plan.steps.map { ParsedVoiceCommand(text, it.normalizedText, it.command) }
    }

    @Synchronized
    fun encode(): String = aliases.values.joinToString("\n") { "${clean(it.phrase)}\t${clean(it.target)}" }

    @Synchronized
    fun load(encoded: String?) {
        aliases.clear()
        encoded.orEmpty().lineSequence()
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 } }
            .map { (p, t) -> Alias(normalize(p), normalize(t)) }
            .filter { it.phrase.length in MIN_LEN..MAX_LEN && it.target.isNotEmpty() }
            .take(MAX_ALIASES)
            .forEach { aliases[it.phrase] = it }
    }

    companion object {
        const val MAX_ALIASES = 40
        const val MIN_LEN = 2
        const val MAX_LEN = 40
        const val EXPORT_HEADER = "Tên gọi riêng THT Voice"

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        fun normalize(text: String): String = FollowUpContext.normalize(text)

        private val RESERVED = setOf(
            "dừng", "dừng lại", "hủy", "huỷ", "hủy bỏ", "thôi", "không", "có", "đồng ý", "ok", "oke", "được",
            "xác nhận", "đúng", "sai", "im", "im lặng", "tắt", "bật", "mở", "đóng"
        )

        fun defaultReserved(phrase: String): Boolean =
            phrase in RESERVED ||
                FollowUpContext().apply {
                    record(VoiceCommand.AdjustFanSpeed(1), FollowUpContext.Snapshot.Fan(1), CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK), 0L)
                }.resolve(phrase, 0L) != null ||
                UserAliasCommands.parse(phrase) != null
    }
}

/** Voice phrases that teach or forget an alias. Recognised before every other parser. */
object UserAliasCommands {
    sealed class Request {
        data class Teach(val phrase: String, val target: String) : Request()
        data class Forget(val phrase: String) : Request()
    }

    private val SAY = "(?:nói|bảo|kêu|gọi|nói là|đọc)"
    private val ME = "(?:tôi|tui|mình|em|anh|chị|tao|tớ|con|bố|mẹ|ba|má)"
    private val MEANS = "(?:thì (?:là |có nghĩa là |nghĩa là |hiểu là )?|nghĩa là |có nghĩa là |tức là |là |hiểu là )"

    private val TEACH = listOf(
        Regex("^(?:khi|lúc|hễ|mỗi khi|mỗi lần) $ME $SAY (.+?) $MEANS(.+)$"),
        Regex("^(?:dạy|ghi nhớ|nhớ|học)(?: (?:cho bạn|cho em|bạn|giúp tôi|giùm|nhé))? (?:là |rằng )?(.+?) (?:nghĩa là|có nghĩa là|tức là|là lệnh) (.+)$"),
        Regex("^(?:đặt|thêm) tên gọi (.+?) (?:cho lệnh|là lệnh|cho|là) (.+)$")
    )
    private val FORGET = listOf(
        Regex("^(?:quên|bỏ|xoá|xóa)(?: (?:đi|giúp tôi|giùm))? (?:tên gọi|câu|từ|cách gọi) (.+)$"),
        Regex("^quên (.+?) (?:đi|nhé|nha)$")
    )

    fun parse(text: String): Request? {
        val t = FollowUpContext.normalize(text)
            .replace(Regex("^(?:dậy|giạy|dại)(?= )"), "dạy")
            .replace(Regex("^(?:quen|quến|quện)(?= (?:tên gọi|câu|cách gọi|từ))"), "quên")
            .replace(Regex("^(?:hồi|lúc mà|khi mà|mỗi khi mà) "), "khi ")
        TEACH.forEach { r -> r.find(t)?.let { m ->
            val phrase = m.groupValues[1].trim().trim('"')
            val target = m.groupValues[2].trim().trim('"')
            if (phrase.isNotEmpty() && target.isNotEmpty()) return Request.Teach(phrase, target)
        } }
        FORGET.forEach { r -> r.find(t)?.let { m ->
            val phrase = m.groupValues[1].trim().trim('"')
            if (phrase.isNotEmpty()) return Request.Forget(phrase)
        } }
        return null
    }
}
