package com.voicegeely.app.voice

/**
 * [REF-06 / S07] Several commands in one sentence: "bật điều hòa 22 độ rồi mở nhạc".
 *
 * - The whole sentence is split and **every** part must parse before anything runs; otherwise the
 *   sentence is not a multi-command (so "chỉ đường đến bến thành và chợ lớn" or "tìm bài anh và em"
 *   stay one command for the normal parser).
 * - A part without its own verb borrows the verb of the part before it
 *   ("mở kính lái và kính phụ" → "mở kính lái", "mở kính phụ").
 * - At most [MAX_STEPS] steps; more is refused as a whole, nothing runs.
 * - Steps run one after another (see [MultiStepRunner]); each is a normal typed command.
 */
object MultiCommandPlan {
    const val MAX_STEPS = 4

    sealed class Result {
        data class Steps(val steps: List<ParsedVoiceCommand>) : Result()
        object TooMany : Result()
    }

    private val CONNECTOR = Regex(
        "\\s+(?:,\\s*)?(?:rồi thì|rồi|dồi|rùi|zồi|giồi|xong rồi thì|xong rồi|xong dồi|xong thì|xong|song rồi|sau đó thì|sau đó|và|dà|với lại|dới lại|dí lại|đồng thời|tiện thể|luôn tiện|thêm nữa là|kèm theo|kèm)\\s+"
    )
    private val COMMA = Regex("\\s*[,;]\\s*")

    private val VERBS = listOf(
        "mở", "đóng", "bật", "tắt", "tăng", "giảm", "hạ", "nâng", "kéo", "chỉnh", "đặt", "cho", "để", "phát", "dừng", "khóa", "khoá"
    )

    /** How many pieces the joining words split [text] into (1 = no joining word between words). */
    fun partCount(text: String): Int {
        val lowered = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).lowercase()
        return lowered.split(COMMA).flatMap { it.split(CONNECTOR) }.count { FollowUpContext.normalize(it).isNotEmpty() }
    }

    fun plan(text: String, parse: (String) -> ParsedVoiceCommand?): Result? {
        val lowered = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).lowercase()
        val parts = lowered.split(COMMA).flatMap { it.split(CONNECTOR) }
            .map { FollowUpContext.normalize(it) }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val steps = ArrayList<ParsedVoiceCommand>()
        var lastVerb: String? = null
        for (part in parts) {
            val parsed = parse(part)
                ?: lastVerb?.takeIf { verbOf(part) == null }?.let { parse("$it $part") }?.takeUnless { isFreeText(it.command) }
                ?: return null
            if (steps.lastOrNull()?.command != parsed.command) steps += parsed
            lastVerb = verbOf(part) ?: lastVerb
        }
        if (steps.size < 2) return null
        if (steps.size > MAX_STEPS) return Result.TooMany
        return Result.Steps(steps.map { ParsedVoiceCommand(text, it.normalizedText, it.command) })
    }

    /** A borrowed verb must not turn leftover words into an app name, a search or a destination. */
    private fun isFreeText(command: VoiceCommand): Boolean =
        command is VoiceCommand.OpenInstalledApp || command is VoiceCommand.SearchMedia || command is VoiceCommand.Navigate

    private fun verbOf(part: String): String? = part.substringBefore(' ').takeIf { it in VERBS }
}

/** Runs a [MultiCommandPlan] one step at a time and builds one short spoken summary. */
object MultiStepRunner {
    sealed class Gate {
        object Allow : Gate()
        object NeedsConfirmation : Gate()
        data class Blocked(val reason: String) : Gate()
    }

    data class Outcome(val summary: String, val results: List<CommandExecutionResult>, val stoppedAt: Int?)

    /**
     * [gate] is the safety gate per step; [run] executes one step (bounded by the caller) and returns
     * its result. Stops at the first step that fails, is rejected, needs confirmation or is blocked;
     * AcceptedUnverified continues but is reported as unverified.
     */
    fun run(
        steps: List<ParsedVoiceCommand>,
        gate: (VoiceCommand) -> Gate,
        run: (ParsedVoiceCommand) -> CommandExecutionResult
    ): Outcome {
        val sentences = ArrayList<String>()
        val results = ArrayList<CommandExecutionResult>()
        steps.forEachIndexed { index, step ->
            val stop: String? = when (val g = gate(step.command)) {
                Gate.NeedsConfirmation -> "Lệnh thứ ${index + 1} cần xác nhận, hãy nói riêng lệnh đó."
                is Gate.Blocked -> g.reason
                Gate.Allow -> {
                    val result = try { run(step) } catch (t: Throwable) {
                        CommandExecutionResult.Failed(t.message ?: "Lỗi không xác định.", t::class.java.name)
                    }
                    results += result
                    sentences += VoiceResponseFormatter.result(step.command, result)
                    if (result is CommandExecutionResult.Failed || result is CommandExecutionResult.Rejected) "" else null
                }
            }
            if (stop != null) {
                if (stop.isNotEmpty()) sentences += stop
                val left = steps.size - index - 1
                if (left > 0) sentences += "Chưa làm $left lệnh còn lại."
                return Outcome(join(sentences), results, index)
            }
        }
        return Outcome(join(sentences), results, null)
    }

    private fun join(sentences: List<String>): String =
        sentences.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.last() in ".!?") it else "$it." }
}
