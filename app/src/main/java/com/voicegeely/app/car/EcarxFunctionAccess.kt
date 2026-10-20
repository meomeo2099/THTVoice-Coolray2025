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
            when (val status = method.invoke(carFunction, functionId, zone)) {
                null -> null
                is Enum<*> -> status.name.equals("active", ignoreCase = true)
                else -> status.toString().equals("active", ignoreCase = true)
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun getInt(functionId: Int, zone: Int): Int? = try {
        (getInt2?.invoke(carFunction, functionId, zone) as? Number)?.toInt()
    } catch (_: Throwable) {
        null
    }

    override fun setInt(functionId: Int, zone: Int, value: Int): Boolean =
        invokeBoolean(setInt3, functionId, zone, value)

    override fun getFloat(functionId: Int, zone: Int): Float? = try {
        (getFloat2?.invoke(carFunction, functionId, zone) as? Number)?.toFloat()?.takeIf { it.isFinite() }
    } catch (_: Throwable) {
        null
    }

    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean =
        invokeBoolean(setFloat3, functionId, zone, value)

    private fun invokeBoolean(method: Method?, vararg args: Any): Boolean {
        if (method == null) return false
        return try {
            when (val result = method.invoke(carFunction, *args)) {
                is Boolean -> result
                is Number -> result.toInt() != 0
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val CAR_CLASS = "com.ecarx.xui.adaptapi.car.Car"

        fun create(context: Context): Result<EcarxFunctionAccess> = runCatching {
            val carClass = Class.forName(CAR_CLASS)
            val create = carClass.methods.firstOrNull { method ->
                method.name == "create" &&
                    method.parameterTypes.size == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            } ?: error("$CAR_CLASS.create(Context) unavailable")
            val car = create.invoke(null, context.applicationContext) ?: error("Car.create returned null")
            val getCarFunction = car.javaClass.methods.firstOrNull {
                it.name == "getICarFunction" && it.parameterTypes.isEmpty()
            } ?: error("getICarFunction unavailable on ${car.javaClass.name}")
            val function = getCarFunction.invoke(car) ?: error("getICarFunction returned null")

            val methods = function.javaClass.methods
            EcarxFunctionAccess(
                car = car,
                carFunction = function,
                supported2 = methods.firstOrNull { it.matches("isFunctionSupported", INT, INT) },
                getInt2 = methods.firstOrNull { it.matches("getFunctionValue", INT, INT) },
                setInt3 = methods.firstOrNull { it.matches("setFunctionValue", INT, INT, INT) },
                getFloat2 = methods.firstOrNull { it.matches("getCustomizeFunctionValue", INT, INT) },
                setFloat3 = methods.firstOrNull { it.matches("setCustomizeFunctionValue", INT, INT, FLOAT) }
            )
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
