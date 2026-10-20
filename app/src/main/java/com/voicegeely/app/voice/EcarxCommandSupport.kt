package com.voicegeely.app.voice

import com.voicegeely.app.car.EcarxFunctionPort

/**
 * Shared write + read-back steps for Coolray 2025 executors that go through the ECARX AdaptAPI.
 * Results follow the THT Voice lifecycle: only a matching read-back is [CommandExecutionResult.Confirmed].
 */
internal class EcarxCommandSupport(
    private val port: EcarxFunctionPort,
    private val waitForReadback: (Long) -> Unit,
    private val verifyDelays: LongArray = DEFAULT_DELAYS
) {
    /** false only when the car says the function is not active for this zone; unknown counts as allowed. */
    fun supported(functionId: Int, zone: Int): Boolean =
        try { port.isSupported(functionId, zone) != false } catch (_: Throwable) { true }

    fun readInt(functionId: Int, zone: Int): Int? = try { port.getInt(functionId, zone) } catch (_: Throwable) { null }

    fun readFloat(functionId: Int, zone: Int): Float? =
        try { port.getFloat(functionId, zone)?.takeIf { it.isFinite() } } catch (_: Throwable) { null }

    /**
     * Writes [value] (skipped when already there) and polls [state] until it equals [expected].
     * [state] defaults to reading the same function back.
     */
    fun writeIntVerify(
        functionId: Int,
        zone: Int,
        value: Int,
        name: String,
        expected: Int = value,
        state: () -> Int? = { readInt(functionId, zone) },
        actualText: String? = null
    ): CommandExecutionResult {
        if (!port.isMock && state() == expected) return confirmed(actualText)
        val accepted = try { port.setInt(functionId, zone, value) } catch (t: Throwable) {
            return CommandExecutionResult.Failed(t.message ?: "Lỗi khi gửi lệnh $name.", t::class.java.name)
        }
        if (!accepted) return CommandExecutionResult.Rejected("Xe không nhận lệnh $name.")
        if (port.isMock) return confirmed(actualText)
        var readable = false
        for (delay in verifyDelays) {
            if (!sleep(delay)) return interrupted()
            val actual = state() ?: continue
            readable = true
            if (actual == expected) return confirmed(actualText)
        }
        return CommandExecutionResult.AcceptedUnverified(
            if (readable) "Trạng thái $name đọc lại chưa khớp lệnh." else "Không đọc lại được trạng thái $name."
        )
    }

    fun sleep(ms: Long): Boolean = try { waitForReadback(ms); true } catch (_: InterruptedException) {
        Thread.currentThread().interrupt(); false
    }

    fun confirmed(actual: String? = null) = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, actual)
    fun interrupted() = CommandExecutionResult.AcceptedUnverified("Quá trình xác nhận bị gián đoạn.")

    companion object {
        val DEFAULT_DELAYS = longArrayOf(150L, 300L, 500L, 800L)
        const val NOT_ACTIVE = "Chức năng này không có hoặc chưa bật trên xe."
        const val NO_BODY_API = "Chưa kết nối được bộ điều khiển thân xe."
    }
}
