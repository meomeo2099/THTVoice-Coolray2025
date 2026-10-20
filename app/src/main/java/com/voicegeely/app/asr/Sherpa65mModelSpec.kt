package com.voicegeely.app.asr

/** Canonical model requirements for THT Voice offline Vietnamese streaming ASR. */
object Sherpa65mModelSpec {
    const val MODEL_ID = "sherpa-onnx-streaming-zipformer-vi-65M-int8"
    const val SAMPLE_RATE = 16_000
    const val FEATURE_DIM = 80
    const val PROVIDER = "cpu"
    const val TOKENS = "tokens.txt"

    /**
     * Sherpa model releases do not always use identical filenames. Some archives
     * include epoch/chunk suffixes, so discovery is role-based instead of locking
     * THT Voice to one guessed filename.
     */
    data class Files(
        val encoder: String,
        val decoder: String,
        val joiner: String,
        val tokens: String
    )

    fun discover(existingNames: Collection<String>): Files? {
        val names = existingNames.map { it.substringAfterLast('/') }.toSet()
        val encoder = choose(names, "encoder") ?: return null
        val decoder = choose(names, "decoder") ?: return null
        val joiner = choose(names, "joiner") ?: return null
        val tokens = names.firstOrNull { it == TOKENS } ?: return null
        return Files(encoder, decoder, joiner, tokens)
    }

    private fun choose(names: Set<String>, role: String): String? = names
        .asSequence()
        .filter { it.startsWith(role) && it.endsWith(".onnx") }
        // Prefer INT8 where supplied, except decoder may intentionally be fp32.
        .sortedWith(compareByDescending<String> { ".int8.onnx" in it }.thenBy { it.length })
        .firstOrNull()
}
