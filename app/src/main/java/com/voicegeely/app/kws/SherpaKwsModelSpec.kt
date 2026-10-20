package com.voicegeely.app.kws

object SherpaKwsModelSpec {
    const val SAMPLE_RATE = 16_000
    const val FEATURE_DIM = 80
    const val PROVIDER = "cpu"

    const val MODEL_DIR = "models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
    const val ENCODER = "$MODEL_DIR/encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx"
    const val DECODER = "$MODEL_DIR/decoder-epoch-13-avg-2-chunk-8-left-64.onnx"
    const val JOINER = "$MODEL_DIR/joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx"
    const val TOKENS = "$MODEL_DIR/tokens.txt"
    const val KEYWORDS = "$MODEL_DIR/keywords.txt"
    const val SOURCE = "$MODEL_DIR/model-source.txt"

    /** Legacy/default wake label retained for compatibility with existing checks. */
    const val WAKE_LABEL = "HEY_THT"

    val BASE_REQUIRED_ASSETS = listOf(
        ENCODER,
        DECODER,
        JOINER,
        TOKENS
    )

    /** Default profile assets retained for compatibility with existing tests. */
    val REQUIRED_ASSETS = requiredAssets(WakePhrase.HEY_THT)

    fun keywords(phrase: WakePhrase): String = "$MODEL_DIR/${phrase.keywordFileName}"

    fun requiredAssets(phrase: WakePhrase): List<String> = BASE_REQUIRED_ASSETS + keywords(phrase)
}
