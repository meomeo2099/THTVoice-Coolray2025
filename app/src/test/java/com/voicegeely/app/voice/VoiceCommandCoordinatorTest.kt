package com.voicegeely.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandCoordinatorTest {

    @Test
    fun fullModeAcknowledgesThenConfirms() = runBlocking {
        val spoken = mutableListOf<String>()
        val coordinator = VoiceCommandCoordinator(
            executor = object : VoiceCommandExecutor {
                override suspend fun execute(command: VoiceCommand): CommandExecutionResult =
                    CommandExecutionResult.Confirmed(
                        verificationSource = VerificationSource.VEHICLE_READ_BACK,
                        actualValue = "22 độ"
                    )
            },
            speech = VoiceSpeechSink { spoken += it }
        )

        coordinator.handle(
            ParsedVoiceCommand(
                originalText = "chỉnh nhiệt độ 22 độ",
                normalizedText = "chỉnh nhiệt độ 22 độ",
                command = VoiceCommand.SetTemperature(22f)
            ),
            VoiceResponseMode.FULL
        )

        assertEquals(2, spoken.size)
        assertEquals("Đang chỉnh nhiệt độ về 22 độ.", spoken[0])
        assertEquals("Đã chỉnh nhiệt độ về 22 độ.", spoken[1])
    }

    @Test
    fun unverifiedNeverClaimsSuccess() = runBlocking {
        val spoken = mutableListOf<String>()
        val coordinator = VoiceCommandCoordinator(
            executor = object : VoiceCommandExecutor {
                override suspend fun execute(command: VoiceCommand): CommandExecutionResult =
                    CommandExecutionResult.AcceptedUnverified()
            },
            speech = VoiceSpeechSink { spoken += it }
        )

        coordinator.handle(
            ParsedVoiceCommand(
                originalText = "hạ kính bên lái 50 phần trăm",
                normalizedText = "hạ kính bên lái 50 phần trăm",
                command = VoiceCommand.Window(
                    targets = setOf(WindowTarget.FRONT_LEFT),
                    operation = WindowOperation.SET_PERCENT,
                    percent = 50
                )
            ),
            VoiceResponseMode.SHORT
        )

        assertEquals(1, spoken.size)
        assertTrue(spoken.single().contains("chưa xác nhận được trạng thái"))
    }

    @Test
    fun silentModeSpeaksNothing() = runBlocking {
        val spoken = mutableListOf<String>()
        val coordinator = VoiceCommandCoordinator(
            executor = object : VoiceCommandExecutor {
                override suspend fun execute(command: VoiceCommand): CommandExecutionResult =
                    CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_CALLBACK)
            },
            speech = VoiceSpeechSink { spoken += it }
        )

        coordinator.handle(
            ParsedVoiceCommand(
                originalText = "đóng kính bên lái",
                normalizedText = "đóng kính bên lái",
                command = VoiceCommand.Window(
                    targets = setOf(WindowTarget.FRONT_LEFT),
                    operation = WindowOperation.CLOSE
                )
            ),
            VoiceResponseMode.SILENT
        )

        assertTrue(spoken.isEmpty())
    }
}
