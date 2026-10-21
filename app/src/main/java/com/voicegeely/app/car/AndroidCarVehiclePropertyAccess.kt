package com.voicegeely.app.car

import android.car.Car
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

class AndroidCarVehiclePropertyAccess private constructor(
    private val car: Car,
    private val propertyManager: CarPropertyManager
) : VehiclePropertyAccess {
    override fun getIntProperty(propertyId: Int, areaId: Int): Int = try {
        propertyManager.getIntProperty(propertyId, areaId).also { VehicleTelemetryRegistry.markIoSuccess(); trace("GET_INT", propertyId, areaId, it, "OK") }
    } catch (t: Throwable) { traceFailure("GET_INT", propertyId, areaId, t); -1 }

    override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean = try {
        propertyManager.getBooleanProperty(propertyId, areaId).also { VehicleTelemetryRegistry.markIoSuccess(); trace("GET_BOOL", propertyId, areaId, it, "OK") }
    } catch (t: Throwable) { traceFailure("GET_BOOL", propertyId, areaId, t); false }

    override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? = try {
        propertyManager.getBooleanProperty(propertyId, areaId).also { VehicleTelemetryRegistry.markIoSuccess(); trace("GET_BOOL", propertyId, areaId, it, "OK") }
    } catch (t: Throwable) { traceFailure("GET_BOOL", propertyId, areaId, t); null }

    override fun getFloatProperty(propertyId: Int, areaId: Int): Float = try {
        propertyManager.getFloatProperty(propertyId, areaId).also { VehicleTelemetryRegistry.markIoSuccess(); trace("GET_FLOAT", propertyId, areaId, it, "OK") }
    } catch (t: Throwable) { traceFailure("GET_FLOAT", propertyId, areaId, t); Float.NaN }

    override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = try {
        val config = propertyManager.getCarPropertyConfig(propertyId)
        if (config == null) {
            traceRange(propertyId, areaId, null, null, "NO_CONFIG")
            null
        } else {
            val min = (config.getMinValue(areaId) as? Number)?.toInt()
            val max = (config.getMaxValue(areaId) as? Number)?.toInt()
            if (min == null || max == null || max <= min) {
                traceRange(propertyId, areaId, min, max, "INVALID_RANGE")
                null
            } else {
                VehicleTelemetryRegistry.markIoSuccess()
                traceRange(propertyId, areaId, min, max, "OK")
                VehicleIntRange(min, max, "VHAL_CONFIG")
            }
        }
    } catch (t: Throwable) { traceRangeFailure(propertyId, areaId, t); null }

    override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean = write("SET_INT", propertyId, areaId, value) { propertyManager.setIntProperty(propertyId, areaId, value) }
    override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean = write("SET_BOOL", propertyId, areaId, value) { propertyManager.setBooleanProperty(propertyId, areaId, value) }
    override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean = write("SET_FLOAT", propertyId, areaId, value) { propertyManager.setFloatProperty(propertyId, areaId, value) }

    private inline fun write(op: String, propertyId: Int, areaId: Int, value: Any, action: () -> Unit): Boolean {
        val writeSeq = VehicleDebugTrace.markWrite()
        return try { action(); VehicleTelemetryRegistry.markIoSuccess(); trace(op, propertyId, areaId, value, "ACCEPTED", writeSeq); true }
        catch (t: Throwable) { traceFailure(op, propertyId, areaId, t, value, writeSeq); false }
    }

    /**
     * Read-only change callback for one Int property (REF-02 voice key). Returns a handle that
     * unregisters, or null when the property/permission is missing. Never writes.
     */
    fun watchInt(propertyId: Int, onValue: (Int) -> Unit): (() -> Unit)? {
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: android.car.hardware.CarPropertyValue<*>?) {
                val raw = (value?.value as? Number)?.toInt() ?: return
                if (value.propertyId == propertyId) onValue(raw)
            }
            override fun onErrorEvent(propId: Int, zone: Int) {
                Log.w(TAG, "op=WATCH result=ERROR_EVENT prop=$propId area=$zone")
            }
        }
        return try {
            val ok = propertyManager.registerCallback(callback, propertyId, 0f)
            Log.i(TAG, "op=WATCH prop=$propertyId result=${if (ok) "REGISTERED" else "REJECTED"}")
            if (!ok) null else ({ runCatching { propertyManager.unregisterCallback(callback, propertyId) } ; Unit })
        } catch (t: Throwable) {
            Log.w(TAG, "op=WATCH prop=$propertyId result=FAILED status=${failureStatus(t)} type=${t.javaClass.simpleName}")
            null
        }
    }

    override fun disconnect() { try { car.disconnect() } catch (_: Throwable) {}; VehicleTelemetryRegistry.markDisconnected(); Log.i(TAG, "trace=0 scope=NONE writeSeq=${VehicleDebugTrace.writeSequence()} op=DISCONNECT result=OK") }

    private fun trace(op: String, propertyId: Int, areaId: Int, value: Any, result: String, writeSeq: Long = VehicleDebugTrace.writeSequence()) {
        Log.v(TAG, "trace=${VehicleDebugTrace.currentTraceId() ?: 0L} scope=${VehicleDebugTrace.currentScope() ?: "NONE"} writeSeq=$writeSeq op=$op property=$propertyId area=$areaId value=$value result=$result")
    }
    private fun traceFailure(op: String, propertyId: Int, areaId: Int, error: Throwable, requestedValue: Any? = null, writeSeq: Long = VehicleDebugTrace.writeSequence()) {
        VehicleTelemetryRegistry.markIoFailure(error.javaClass.simpleName)
        val valuePart = requestedValue?.let { " value=$it" }.orEmpty()
        Log.w(TAG, "trace=${VehicleDebugTrace.currentTraceId() ?: 0L} scope=${VehicleDebugTrace.currentScope() ?: "NONE"} writeSeq=$writeSeq op=$op property=$propertyId area=$areaId$valuePart result=FAILED status=${failureStatus(error)} type=${error.javaClass.simpleName}")
    }
    private fun traceRange(propertyId: Int, areaId: Int, min: Int?, max: Int?, result: String) {
        Log.v(TAG, "trace=${VehicleDebugTrace.currentTraceId() ?: 0L} scope=${VehicleDebugTrace.currentScope() ?: "NONE"} writeSeq=${VehicleDebugTrace.writeSequence()} op=GET_RANGE property=$propertyId area=$areaId min=${min ?: "none"} max=${max ?: "none"} result=$result source=${if (result == "OK") "VHAL_CONFIG" else "NONE"}")
    }
    private fun traceRangeFailure(propertyId: Int, areaId: Int, error: Throwable) {
        VehicleTelemetryRegistry.markIoFailure(error.javaClass.simpleName)
        Log.w(TAG, "trace=${VehicleDebugTrace.currentTraceId() ?: 0L} scope=${VehicleDebugTrace.currentScope() ?: "NONE"} writeSeq=${VehicleDebugTrace.writeSequence()} op=GET_RANGE property=$propertyId area=$areaId result=FAILED status=${failureStatus(error)} type=${error.javaClass.simpleName}")
    }
    private fun failureStatus(error: Throwable) = if (error is SecurityException) "SECURITY_DENIED" else "BACKEND_ERROR"

    companion object {
        private const val TAG = "THTVoiceVHAL"
        /** Max wait for the Android 9 car service binder (connect() is asynchronous there). */
        private const val P_CONNECT_TIMEOUT_MS = 5_000L
        private val connectThread by lazy { android.os.HandlerThread("THTVoiceCarConnect").apply { start() } }

        /**
         * Coolray 2025 runs Android 9 (API 28): `Car.createCar(Context)` only exists from Android 10
         * and throws NoSuchMethodError there, so the car was never connected. API 28 uses
         * `createCar(context, ServiceConnection, Handler)` + `connect()` and the managers are only
         * usable after `onServiceConnected`. Callbacks run on a private HandlerThread, so waiting here
         * never deadlocks the main thread.
         */
        @Suppress("DEPRECATION")
        private fun connectApi28(context: Context): Car {
            val latch = java.util.concurrent.CountDownLatch(1)
            val connection = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) { latch.countDown() }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {
                    VehicleTelemetryRegistry.markDisconnected()
                    Log.w(TAG, "trace=0 scope=NONE op=CONNECT result=SERVICE_DISCONNECTED")
                }
            }
            val car = Car.createCar(context, connection, android.os.Handler(connectThread.looper))
            car.connect()
            if (!latch.await(P_CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                try { car.disconnect() } catch (_: Throwable) {}
                error("car service not connected after ${P_CONNECT_TIMEOUT_MS} ms")
            }
            Log.i(TAG, "trace=0 scope=NONE op=CONNECT mode=API28_SERVICE_CONNECTION result=BOUND")
            return car
        }

        fun create(context: Context): AndroidCarVehiclePropertyAccess? {
            var car: Car? = null
            return try {
                val connected: Car = if (android.os.Build.VERSION.SDK_INT >= 29) {
                    Car.createCar(context.applicationContext) ?: error("Car.createCar returned null")
                } else {
                    connectApi28(context.applicationContext)
                }
                car = connected
                val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: run {
                    VehicleTelemetryRegistry.markUnavailable("NO_PROPERTY_MANAGER"); Log.w(TAG, "trace=0 scope=NONE writeSeq=${VehicleDebugTrace.writeSequence()} op=CONNECT result=NO_PROPERTY_MANAGER"); try { connected.disconnect() } catch (_: Throwable) {}; return null
                }
                VehicleTelemetryRegistry.markConnected(); Log.i(TAG, "trace=0 scope=NONE writeSeq=${VehicleDebugTrace.writeSequence()} op=CONNECT result=OK"); AndroidCarVehiclePropertyAccess(connected, manager)
            } catch (t: Throwable) {
                VehicleTelemetryRegistry.markUnavailable(t.javaClass.simpleName)
                Log.w(TAG, "trace=0 scope=NONE writeSeq=${VehicleDebugTrace.writeSequence()} op=CONNECT result=FAILED status=${if (t is SecurityException) "SECURITY_DENIED" else "BACKEND_ERROR"} type=${t.javaClass.simpleName}")
                try { car?.disconnect() } catch (_: Throwable) {}; null
            }
        }
    }
}
