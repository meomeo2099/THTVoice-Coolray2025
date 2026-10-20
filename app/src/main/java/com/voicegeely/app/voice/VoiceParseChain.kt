package com.voicegeely.app.voice

/** Runtime parse order shared by the voice service and the phrase-coverage test. */
object VoiceParseChain {
    fun parse(text: String): ParsedVoiceCommand? =
        VehicleStatusQueryParser.parse(text)
            ?: SplitScreenCommandParser.parse(text)
            ?: MediaVoiceParser.parse(text)
            ?: VoiceCommandParser.parse(text)
}
