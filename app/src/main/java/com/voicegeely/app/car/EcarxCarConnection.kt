package com.voicegeely.app.car

/**
 * Car.create only constructs CarImpl. Its inherited connect() binds a separate Android Car
 * client. Wait for that client's property manager before creating the OEM function singleton.
 * Shipped VehicleSignalManager: connect 0x1191a, isConnected 0x11202, manager 0x11182.
 * Call on the command worker so the main-thread service callback can run.
 */
internal object EcarxCarConnection {
    fun awaitConnected(
        car: Any,
        timeoutMs: Long = 5_000L,
        nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
        pause: (Long) -> Unit = { Thread.sleep(it) }
    ) {
        val methods = car.javaClass.methods
        fun method(name: String) = methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?: error("ECARX $name() unavailable on ${car.javaClass.name}")
        val connect = method("connect")
        val connected = method("isConnected")
        val manager = method("getCarPropertyManager")
        fun ready() = connected.invoke(car) == true && manager.invoke(car) != null
        try {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("ECARX connection cancelled")
            if (ready()) return
            connect.invoke(car)
            val started = nowMs()
            while (!ready()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("ECARX connection cancelled")
                val remaining = timeoutMs - (nowMs() - started)
                check(remaining > 0) { "ECARX service/property manager not ready after $timeoutMs ms" }
                pause(minOf(50L, remaining))
            }
        } catch (error: Throwable) {
            runCatching { methods.firstOrNull { it.name == "disconnect" && it.parameterTypes.isEmpty() }?.invoke(car) }
            if (error is InterruptedException) Thread.currentThread().interrupt()
            throw error
        }
    }
}
