package com.voicegeely.app.security

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import java.util.Locale

interface VinProvider {
    fun readVin(): VinReadResult
}

/** Where a VIN was read from; shown on the activation screen so hardware tests know which path works. */
enum class VinSource(val label: String) {
    ANDROID_CAR_INFO_VIN("Android Car"),
    PLATFORM_SYSTEM_PROPERTY("thuộc tính hệ thống")
}

sealed class VinReadResult {
    data class Available(val vin: String, val source: VinSource = VinSource.ANDROID_CAR_INFO_VIN) : VinReadResult()
    data class Unavailable(val reason: VinUnavailableReason) : VinReadResult()
}

enum class VinUnavailableReason {
    CAR_SERVICE_UNAVAILABLE,
    PROPERTY_SERVICE_UNAVAILABLE,
    PERMISSION_DENIED,
    EMPTY_OR_INVALID,
    READ_FAILED
}

object VinRules {
    private val vinRegex = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun normalize(raw: String?): String = raw.orEmpty()
        .trim()
        .uppercase(Locale.US)
        .replace(Regex("\\s+"), "")

    fun isValid(vin: String): Boolean = vinRegex.matches(vin)

    fun normalizeAndValidate(raw: String?): String? {
        val normalized = normalize(raw)
        return normalized.takeIf(::isValid)
    }
}

/** VIN from standard `INFO_VIN` first, then the head-unit platform system property. */
object PlatformVinProviders {
    fun create(context: Context): VinProvider =
        FallbackVinProvider(listOf(AndroidCarVinProvider(context), SystemPropertyVinProvider()))
}

/** Returns the first valid VIN; when none is found, the primary provider's reason is kept. */
class FallbackVinProvider(private val providers: List<VinProvider>) : VinProvider {
    override fun readVin(): VinReadResult {
        var first: VinReadResult.Unavailable? = null
        for (provider in providers) {
            val result = runCatching { provider.readVin() }
                .getOrElse { VinReadResult.Unavailable(VinUnavailableReason.READ_FAILED) }
            if (result is VinReadResult.Available) return result
            if (first == null) first = result as VinReadResult.Unavailable
        }
        return first ?: VinReadResult.Unavailable(VinUnavailableReason.READ_FAILED)
    }
}

/**
 * Read-only fallback: the head-unit platform exposes the VIN as system property
 * [PROPERTY_KEY] (platform reference, `REFERENCE`/`UNVERIFIED` until read on the car).
 * No actuator, no raw VHAL/OEM function ID. Invalid values become unavailable.
 */
class SystemPropertyVinProvider(
    private val readProperty: (String) -> String? = ::readAndroidSystemProperty
) : VinProvider {
    override fun readVin(): VinReadResult {
        val raw = try {
            readProperty(PROPERTY_KEY)
        } catch (_: SecurityException) {
            return VinReadResult.Unavailable(VinUnavailableReason.PERMISSION_DENIED)
        } catch (_: Throwable) {
            return VinReadResult.Unavailable(VinUnavailableReason.READ_FAILED)
        }
        val vin = VinRules.normalizeAndValidate(raw)
            ?: return VinReadResult.Unavailable(VinUnavailableReason.EMPTY_OR_INVALID)
        return VinReadResult.Available(vin, VinSource.PLATFORM_SYSTEM_PROPERTY)
    }

    companion object {
        const val PROPERTY_KEY = "sys.ecarx.vin"
    }
}

private fun readAndroidSystemProperty(key: String): String? {
    val clazz = Class.forName("android.os.SystemProperties")
    val value = clazz.getMethod("get", String::class.java, String::class.java).invoke(null, key, "") as? String
    return value?.takeIf { it.isNotBlank() }
}

/**
 * Reads VIN through the standard Android Automotive property API.
 *
 * No OEM/raw property IDs are guessed here. Any missing service, permission,
 * unsupported property or invalid value becomes VIN unavailable.
 *
 * Logging is deliberately privacy-safe: never log the VIN value itself.
 */
class AndroidCarVinProvider(context: Context) : VinProvider {
    private val appContext = context.applicationContext

    override fun readVin(): VinReadResult {
        var car: Car? = null
        Log.i(TAG, "vin_read_start source=ANDROID_CAR_INFO_VIN")
        return try {
            car = Car.createCar(appContext)
            val propertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (propertyManager == null) {
                Log.w(TAG, "vin_read_unavailable reason=PROPERTY_SERVICE_UNAVAILABLE")
                VinReadResult.Unavailable(VinUnavailableReason.PROPERTY_SERVICE_UNAVAILABLE)
            } else {
                val raw = propertyManager
                    .getProperty(String::class.java, VehiclePropertyIds.INFO_VIN, 0)
                    ?.value
                val vin = VinRules.normalizeAndValidate(raw)
                if (vin == null) {
                    val normalizedLength = VinRules.normalize(raw).length
                    Log.w(TAG, "vin_read_unavailable reason=EMPTY_OR_INVALID normalizedLength=$normalizedLength")
                    VinReadResult.Unavailable(VinUnavailableReason.EMPTY_OR_INVALID)
                } else {
                    Log.i(TAG, "vin_read_success valid=true length=${vin.length}")
                    VinReadResult.Available(vin)
                }
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "vin_read_unavailable reason=PERMISSION_DENIED type=${security.javaClass.simpleName}")
            VinReadResult.Unavailable(VinUnavailableReason.PERMISSION_DENIED)
        } catch (t: Throwable) {
            val reason = if (car == null) {
                VinUnavailableReason.CAR_SERVICE_UNAVAILABLE
            } else {
                VinUnavailableReason.READ_FAILED
            }
            Log.w(TAG, "vin_read_unavailable reason=$reason type=${t.javaClass.simpleName}")
            VinReadResult.Unavailable(reason)
        } finally {
            try {
                car?.disconnect()
                Log.i(TAG, "vin_car_service_disconnected")
            } catch (_: Throwable) {
                Log.w(TAG, "vin_car_service_disconnect_failed")
            }
        }
    }

    companion object {
        private const val TAG = "THTVoiceVIN"
    }
}
