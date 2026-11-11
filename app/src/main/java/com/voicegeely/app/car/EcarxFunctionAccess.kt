package com.voicegeely.app.car

import android.content.Context
import java.lang.reflect.Method

/**
 * Function-level access to the ECARX AdaptAPI (`ICarFunction`) used by Coolray 2025 executors.
 * Implemented by [EcarxFunctionAccess] on the car and by fakes in unit tests.
 *
 * A `true` from a write only means AdaptAPI accepted the request; executors still read back before
 * reporting success.
 */
interface EcarxFunctionPort {
    val isMock: Boolean get() = false

    /** true = active, false = not available / not active on this car, null = could not ask. */
    fun isSupported(functionId: Int, zone: Int): Boolean?

    fun getInt(functionId: Int, zone: Int): Int?
    fun setInt(functionId: Int, zone: Int, value: Int): Boolean

    fun getFloat(functionId: Int, zone: Int): Float?
    fun setFloat(functionId: Int, zone: Int, value: Float): Boolean

    // --- MCU property path of the AdaptAPI CarImpl (vendor INFO_ID_* properties, 0x287xxxxx) ---
    /** `CarImpl.sendBytesToMcuByPropID(prop, bytes)`; false when unavailable. */
    fun sendBytesToMcu(propertyId: Int, bytes: ByteArray): Boolean = false
    /** `CarImpl.getIntByPropID(prop, area)` (falls back to the 1-arg overload); null when unavailable. */
    fun getIntByProp(propertyId: Int, area: Int): Int? = null
    /** `CarImpl.getBytesByPropID(prop)`; null when unavailable. */
    fun getBytesByProp(propertyId: Int): ByteArray? = null
}

/** In-memory port used when the car connection is a mock (development without a head unit). */
class MockEcarxFunctionPort : EcarxFunctionPort {
    private val ints = HashMap<Pair<Int, Int>, Int>()
    private val floats = HashMap<Pair<Int, Int>, Float>()
    override val isMock: Boolean get() = true
    override fun isSupported(functionId: Int, zone: Int): Boolean = true
    override fun getInt(functionId: Int, zone: Int): Int? = ints[functionId to zone]
    override fun setInt(functionId: Int, zone: Int, value: Int): Boolean { ints[functionId to zone] = value; return true }
    override fun getFloat(functionId: Int, zone: Int): Float? = floats[functionId to zone]
    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean { floats[functionId to zone] = value; return true }
    private val props = HashMap<Int, Int>()
    override fun sendBytesToMcu(propertyId: Int, bytes: ByteArray): Boolean {
        if (propertyId == Coolray2025Functions.INFO_ID_WINDOW_GROUP) {
            bytes.forEachIndexed { i, b -> if (b.toInt() != -1) Coolray2025Functions.windowStateProp(i)?.let { props[it] = b.toInt() } }
        }
        return true
    }
    override fun getIntByProp(propertyId: Int, area: Int): Int? = props[propertyId]
}

/**
 * Reflection-only bridge to the ECARX AdaptAPI bundled on the head unit (boot classpath
 * `AdapterAPI.jar` + `AdapterAPIImpl.jar`). Resolves
 * `com.ecarx.xui.adaptapi.car.Car.create(context).getICarFunction()` and the documented overloads:
 * `isFunctionSupported(int,int)`, `get/setFunctionValue(int,int[,int])`,
 * `get/setCustomizeFunctionValue(int,int[,float])`.
 *
 * The app does not compile against the vendor JAR. Every call is guarded; a failure reads as
 * "unknown" (null / false), never as success.
 */
class EcarxFunctionAccess private constructor(
    private val car: Any,
    private val carFunction: Any,
    private val supported2: Method?,
    private val getInt2: Method?,
    private val setInt3: Method?,
    private val getFloat2: Method?,
    private val setFloat3: Method?
) : EcarxFunctionPort {

    data class Probe(
        val available: Boolean,
        val carClassName: String? = null,
        val carFunctionClassName: String? = null,
        val isSupported: Boolean = false,
        val intAccess: Boolean = false,
        val floatAccess: Boolean = false,
        val error: String? = null
    )

    fun probe(): Probe = Probe(
        available = true,
        carClassName = car.javaClass.name,
        carFunctionClassName = carFunction.javaClass.name,
        isSupported = supported2 != null,
        intAccess = getInt2 != null && setInt3 != null,
        floatAccess = getFloat2 != null && setFloat3 != null
    )

    override fun isSupported(functionId: Int, zone: Int): Boolean? {
        val method = supported2 ?: return null
        return try {
            val supported = when (val status = method.invoke(carFunction, functionId, zone)) {
                null -> null
                is Enum<*> -> status.name.equals("active", ignoreCase = true)
                else -> status.toString().equals("active", ignoreCase = true)
            }
            if (supported != true) VehicleTelemetryRegistry.recordResult("ECARX", "IS_SUPPORTED", functionId, zone,
                if (supported == false) "INACTIVE" else "UNKNOWN")
            supported
        } catch (error: Throwable) {
            VehicleTelemetryRegistry.recordFailure("ECARX", "IS_SUPPORTED", functionId, zone, error)
            null
        }
    }

    override fun getInt(functionId: Int, zone: Int): Int? = try {
        (getInt2?.invoke(carFunction, functionId, zone) as? Number)?.toInt()
    } catch (error: Throwable) {
        VehicleTelemetryRegistry.recordFailure("ECARX", "GET_INT", functionId, zone, error)
        null
    }

    override fun setInt(functionId: Int, zone: Int, value: Int): Boolean =
        invokeBoolean("setFunctionValue", setInt3, functionId, zone, value)

    override fun getFloat(functionId: Int, zone: Int): Float? = try {
        (getFloat2?.invoke(carFunction, functionId, zone) as? Number)?.toFloat()?.takeIf { it.isFinite() }
    } catch (error: Throwable) {
        VehicleTelemetryRegistry.recordFailure("ECARX", "GET_FLOAT", functionId, zone, error)
        null
    }

    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean =
        invokeBoolean("setCustomizeFunctionValue", setFloat3, functionId, zone, value)

    private val carMethods by lazy { car.javaClass.methods }
    private fun carMethod(name: String, vararg types: Class<*>): Method? =
        carMethods.firstOrNull { it.name == name && it.parameterTypes.contentEquals(arrayOf(*types)) }

    override fun sendBytesToMcu(propertyId: Int, bytes: ByteArray): Boolean = try {
        val method = carMethod("sendBytesToMcuByPropID", INT, ByteArray::class.java)
        val accepted = when (val r = method?.invoke(car, propertyId, bytes)) {
            is Boolean -> r
            is Number -> r.toInt() != 0
            else -> false
        }
        VehicleTelemetryRegistry.recordResult("ECARX", "SEND_MCU", propertyId, null,
            if (method == null) "METHOD_UNAVAILABLE" else if (accepted) "ACCEPTED_UNVERIFIED" else "REJECTED")
        accepted
    } catch (error: Throwable) {
        VehicleTelemetryRegistry.recordFailure("ECARX", "SEND_MCU", propertyId, null, error)
        false
    }

    override fun getIntByProp(propertyId: Int, area: Int): Int? = try {
        val two = carMethod("getIntByPropID", INT, INT)
        val one = carMethod("getIntByPropID", INT)
        ((two?.invoke(car, propertyId, area) ?: one?.invoke(car, propertyId)) as? Number)?.toInt()
    } catch (error: Throwable) {
        VehicleTelemetryRegistry.recordFailure("ECARX", "GET_MCU_INT", propertyId, area, error)
        null
    }

    override fun getBytesByProp(propertyId: Int): ByteArray? = try {
        carMethod("getBytesByPropID", INT)?.invoke(car, propertyId) as? ByteArray
    } catch (error: Throwable) {
        VehicleTelemetryRegistry.recordFailure("ECARX", "GET_MCU_BYTES", propertyId, null, error)
        null
    }

    private fun invokeBoolean(operation: String, method: Method?, vararg args: Any): Boolean {
        if (method == null) {
            VehicleTelemetryRegistry.recordResult("ECARX", operation, args[0] as Int, args[1] as Int, "METHOD_UNAVAILABLE")
            return false
        }
        return try {
            val accepted = when (val result = method.invoke(carFunction, *args)) {
                is Boolean -> result
                is Number -> result.toInt() != 0
                else -> false
            }
            VehicleTelemetryRegistry.recordResult("ECARX", "$operation value=${args[2]}", args[0] as Int, args[1] as Int,
                if (accepted) "ACCEPTED_UNVERIFIED" else "REJECTED")
            accepted
        } catch (error: Throwable) {
            VehicleTelemetryRegistry.recordFailure("ECARX", method.name,
                args.getOrNull(0) as? Int, args.getOrNull(1) as? Int, error)
            false
        }
    }

    companion object {
        private const val CAR_CLASS = "com.ecarx.xui.adaptapi.car.Car"
        // CarFunctionImpl.getInstance stores the first VehicleSignalManager for the whole process.
        // Reuse that same CarImpl when the voice service restarts; retain application context only.
        private var processAccess: EcarxFunctionAccess? = null

        @Synchronized
        fun create(context: Context): Result<EcarxFunctionAccess> = runCatching {
            processAccess?.let { existing ->
                EcarxCarConnection.awaitConnected(existing.car)
                return@runCatching existing
            }
            val carClass = Class.forName(CAR_CLASS)
            val create = carClass.methods.firstOrNull { method ->
                method.name == "create" &&
                    method.parameterTypes.size == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            } ?: error("$CAR_CLASS.create(Context) unavailable")
            val car = create.invoke(null, context.applicationContext) ?: error("Car.create returned null")
            fromCar(car).also { processAccess = it }
        }.onFailure { VehicleTelemetryRegistry.recordFailure("ECARX", "CONNECT", null, null, it) }

        /** Shared construction path, also exercised with lifecycle-accurate JVM test doubles. */
        internal fun fromCar(car: Any): EcarxFunctionAccess {
            EcarxCarConnection.awaitConnected(car)
            val getCarFunction = car.javaClass.methods.firstOrNull {
                it.name == "getICarFunction" && it.parameterTypes.isEmpty()
            } ?: error("getICarFunction unavailable on ${car.javaClass.name}")
            val function = getCarFunction.invoke(car) ?: error("getICarFunction returned null")

            val methods = function.javaClass.methods
            return EcarxFunctionAccess(
                car = car,
                carFunction = function,
                supported2 = methods.firstOrNull { it.matches("isFunctionSupported", INT, INT) },
                getInt2 = methods.firstOrNull { it.matches("getFunctionValue", INT, INT) },
                setInt3 = methods.firstOrNull { it.matches("setFunctionValue", INT, INT, INT) },
                getFloat2 = methods.firstOrNull { it.matches("getCustomizeFunctionValue", INT, INT) },
                setFloat3 = methods.firstOrNull { it.matches("setCustomizeFunctionValue", INT, INT, FLOAT) }
            ).also { VehicleTelemetryRegistry.recordConnection("ECARX", "READY") }
        }

        fun probe(context: Context): Probe = create(context).fold(
            onSuccess = { it.probe() },
            onFailure = { Probe(available = false, error = it.message ?: it::class.java.name) }
        )

        private val INT: Class<*> = Int::class.javaPrimitiveType!!
        private val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

        private fun Method.matches(name: String, vararg types: Class<*>): Boolean =
            this.name == name && parameterTypes.contentEquals(arrayOf(*types))
    }
}
