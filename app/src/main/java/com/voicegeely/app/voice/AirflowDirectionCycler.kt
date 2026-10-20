package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Periodic fan-direction swing (owner request 08/10/2026: "đảo gió mỗi phút", interval adjustable).
 * Alternates vào mặt ↔ xuống chân every [intervalMinutes]. Damper moves are silent and cost no
 * power (drive_assist CLIMATE-FACTS). Stops by itself when the HVAC is off or the car stops
 * answering, when the driver sets a direction by hand, or on "tắt đảo gió".
 * Coolray: HVAC_FAN_DIRECTION 0x15400501 area 1 (1 face, 2 feet) and HVAC_POWER area 5 from the
 * Galaxy path. REFERENCE/UNVERIFIED on the car.
 */
object AirflowDirectionCycler {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 10
    /** Set by the voice service to android.util.Log (kept out of this class for JVM tests). */
    @Volatile var log: (String) -> Unit = {}
    private const val MAX_READ_FAILURES = 3

    /** Interval used when the voice command names none (Settings → "Đảo hướng gió định kỳ"). */
    @Volatile var defaultMinutes: Int = 1
        set(value) { field = value.coerceIn(MIN_MINUTES, MAX_MINUTES) }

    @Volatile var intervalMinutes: Int = 0
        private set
    val isRunning: Boolean get() = task != null

    private var executor: ScheduledExecutorService? = null
    private var task: ScheduledFuture<*>? = null
    private var readFailures = 0

    /** Next direction after [current]: face → feet, anything else → face. */
    fun nextDirection(current: Int?): Int = if (current == FACE) FEET else FACE

    @Synchronized
    fun start(vehicle: VehiclePropertyAccess, minutes: Int) {
        stop()
        val interval = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val runner = executor ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "THTVoice-Airflow").apply { isDaemon = true }
        }.also { executor = it }
        intervalMinutes = interval
        readFailures = 0
        task = runner.scheduleWithFixedDelay({ tick(vehicle) }, interval.toLong(), interval.toLong(), TimeUnit.MINUTES)
        log("airflow_cycle_start minutes=$interval")
    }

    @Synchronized
    fun stop() {
        task?.cancel(false)
        if (task != null) log("airflow_cycle_stop")
        task = null
        intervalMinutes = 0
    }

    private fun tick(vehicle: VehiclePropertyAccess) {
        try {
            if (!hvacOn(vehicle)) {
                log("airflow_cycle_stop reason=hvac_off_or_unreadable")
                stop()
                return
            }
            val current = runCatching {
                vehicle.getIntPropertyOrNull(CoolrayProperties.HVAC_FAN_DIRECTION, CoolrayProperties.HVAC_FAN_DIRECTION_AREA)
            }.getOrNull()
            val next = nextDirection(current)
            val ok = runCatching {
                vehicle.setIntProperty(CoolrayProperties.HVAC_FAN_DIRECTION, CoolrayProperties.HVAC_FAN_DIRECTION_AREA, next)
            }.getOrDefault(false)
            log("airflow_cycle_tick from=$current to=$next accepted=$ok")
        } catch (t: Throwable) {
            log("airflow_cycle_tick_failed type=${t.javaClass.simpleName}")
        }
    }

    private fun hvacOn(vehicle: VehiclePropertyAccess): Boolean {
        val on = runCatching { vehicle.getBoolPropertyOrNull(CoolrayProperties.HVAC_POWER, CoolrayProperties.HVAC_POWER_AREA) }.getOrNull()
        if (on != null) {
            readFailures = 0
            return on
        }
        return ++readFailures < MAX_READ_FAILURES
    }

    private const val FACE = 1
    private const val FEET = 2
}
