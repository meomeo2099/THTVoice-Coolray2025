package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace

/**
 * Coolray 2025 lights through the ECARX AdaptAPI (`IBcm`), zone ALL, switch ON / OFF. REFERENCE:
 * - cabin / reading light: `BCM_FUNC_LIGHT_READING_LIGHT`;
 * - exterior: low beam = `BCM_FUNC_LIGHT_DIPPED_BEAM` ON; position = `BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS`
 *   ON with dipped beam OFF; off = both OFF;
 * - fog: `BCM_FUNC_LIGHT_FRONT_FOG_LAMPS`.
 * Each write is read back before success is reported.
 */
class CoolrayLightingVoiceCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback)

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        val traceId = VehicleDebugTrace.beginCommand("LIGHT:${command::class.java.simpleName}")
        return try {
            when (command) {
                is VoiceCommand.CabinLight -> switch(F.BCM_FUNC_LIGHT_READING_LIGHT, command.on, "đèn trong xe")
                is VoiceCommand.FogLight -> switch(F.BCM_FUNC_LIGHT_FRONT_FOG_LAMPS, command.on, "đèn sương mù")
                is VoiceCommand.ExteriorLight -> exterior(command.mode)
                else -> CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm đèn.")
            }
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển đèn.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun switch(function: Int, on: Boolean, name: String): CommandExecutionResult {
        if (!support.supported(function, F.ZONE_ALL)) return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
        return support.writeIntVerify(function, F.ZONE_ALL, if (on) F.ON else F.OFF, name)
    }

    private fun exterior(mode: ExteriorLightMode): CommandExecutionResult = when (mode) {
        ExteriorLightMode.LOW_BEAM -> switch(F.BCM_FUNC_LIGHT_DIPPED_BEAM, true, "đèn chiếu gần")
        ExteriorLightMode.POSITION -> {
            val dipped = switch(F.BCM_FUNC_LIGHT_DIPPED_BEAM, false, "đèn chiếu gần")
            if (dipped is CommandExecutionResult.Rejected || dipped is CommandExecutionResult.Failed) dipped
            else combine(dipped, switch(F.BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS, true, "đèn định vị"))
        }
        ExteriorLightMode.OFF -> combine(
            switch(F.BCM_FUNC_LIGHT_DIPPED_BEAM, false, "đèn chiếu gần"),
            switch(F.BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS, false, "đèn định vị")
        )
    }

    /** Both parts confirmed → confirmed; any refusal/failure wins; otherwise unverified. */
    private fun combine(a: CommandExecutionResult, b: CommandExecutionResult): CommandExecutionResult = when {
        a is CommandExecutionResult.Failed -> a
        b is CommandExecutionResult.Failed -> b
        a is CommandExecutionResult.Rejected -> a
        b is CommandExecutionResult.Rejected -> b
        a is CommandExecutionResult.Confirmed && b is CommandExecutionResult.Confirmed -> b
        a is CommandExecutionResult.AcceptedUnverified -> a
        else -> b
    }
}
