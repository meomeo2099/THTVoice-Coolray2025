package com.voicegeely.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandHistoryObserverTest {
    @Test fun recordsTypedOutcomeBeforeSpeechCanFail() = runBlocking {
        val events = mutableListOf<String>()
        val observer = object : CommandHistoryObserver {
            override fun begin(parsed: ParsedVoiceCommand): String { events += "begin"; return "id" }
            override fun complete(id: String, parsed: ParsedVoiceCommand, result: CommandExecutionResult) {
                events += result.javaClass.simpleName
            }
        }
        val coordinator = VoiceCommandCoordinator(
            executor = object : VoiceCommandExecutor {
                override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
                    events += "execute"
                    return CommandExecutionResult.AcceptedUnverified("API nhận lệnh")
                }
            }, speech = VoiceSpeechSink { events += "speech"; error("tts") }, history = observer
        )
        runCatching { coordinator.handle(ParsedVoiceCommand("22 độ", "22 độ", VoiceCommand.SetTemperature(22f)), VoiceResponseMode.SHORT) }
        assertEquals(listOf("begin", "execute", "AcceptedUnverified", "speech"), events)
    }
    @Test fun brokenHistoryNeverPreventsCommandExecution() = runBlocking {
        val coordinator = VoiceCommandCoordinator(
            executor = object : VoiceCommandExecutor {
                override suspend fun execute(command: VoiceCommand) = CommandExecutionResult.Rejected("API từ chối")
            }, speech = VoiceSpeechSink {}, history = object : CommandHistoryObserver {
                override fun begin(parsed: ParsedVoiceCommand): String = error("storage unavailable")
                override fun complete(id: String, parsed: ParsedVoiceCommand, result: CommandExecutionResult) = error("storage unavailable")
            }
        )
        assertTrue(coordinator.handle(ParsedVoiceCommand("22 độ", "22 độ", VoiceCommand.SetTemperature(22f)), VoiceResponseMode.SILENT) is CommandExecutionResult.Rejected)
    }
}
