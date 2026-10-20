package com.voicegeely.app.settings

import com.voicegeely.app.security.LicenseConfig

/**
 * Transitional cloud config while THT Voice migrates from Groq STT/LLM
 * to fully offline Sherpa-ONNX streaming ASR.
 * No general Q&A or weather endpoint belongs in the vehicle voice core.
 */
object GroqConfig {
    const val WHISPER_MODEL = "whisper-large-v3"
    const val LLM_MODEL = "openai/gpt-oss-20b"
    const val LLM_ENABLED = true

    val WHISPER_ENDPOINT: String
        get() = LicenseConfig.endpoint("api/stt")

    val LLM_ENDPOINT: String
        get() = LicenseConfig.endpoint("api/llm")

    val HEALTH_ENDPOINT: String
        get() = LicenseConfig.endpoint("api/health")
}
