package com.voicegeely.app.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Steering-wheel voice key on the Coolray 2025 head unit (GKUI, AdaptAPI 1.1.18.9).
 *
 * This head unit does not send the Flyme `ECARX_KEY_RVOICEASSIST_EVENT` broadcast; keys are handed
 * out by ECARX's input service. AdaptAPI exposes it as
 * `com.ecarx.xui.adaptapi.input.Input.create(context).requestKeysInterception(int[], IKeyCallback)`
 * (`IKeyCallback.onKeyPressed(int)` / `onKeyReleased(int)` return true = consumed).
 * Keys requested: ECARX vendor voice key 298 (the steering-wheel voice key on SX11 Coolray units,
 * delivered by CarService InputHalService as vendor code 298), plus `KeyCode.KEYCODE_VOICE_ASSIST`
 * (231) and `KEYCODE_R_VOICE_ASSIST` (200231) from AdaptAPI. Only voice codes are requested so
 * volume / power / media keys are never taken from the system.
 *
 * Reflection only (no vendor JAR at compile time). The trigger fires on key release, on the main
 * thread. REFERENCE: from the head unit's AdapterAPI.jar, not yet confirmed on the car.
 */
class EcarxKeyInterceptor private constructor(
    private val input: Any,
    private val callback: Any,
    private val granted: IntArray
) {
    /** Keys the input service actually granted (empty = nothing intercepted). */
    fun grantedKeys(): IntArray = granted

    fun release() {
        runCatching {
            input.javaClass.methods.firstOrNull { it.name == "abandonKeysInterception" && it.parameterTypes.size == 1 }
                ?.invoke(input, callback)
        }.onFailure { Log.w(TAG, "ecarx_key_abandon_failed ${it.message}") }
    }

    companion object {
        private const val TAG = "EcarxKeyInterceptor"
        const val KEYCODE_VOICE_ASSIST = 231
        const val KEYCODE_R_VOICE_ASSIST = 200231
        /** ECARX vendor code of the steering-wheel voice key on SX11 (Coolray) head units. */
        const val KEYCODE_ECARX_WHEEL_VOICE = 298
        val VOICE_KEYS = intArrayOf(KEYCODE_ECARX_WHEEL_VOICE, KEYCODE_VOICE_ASSIST, KEYCODE_R_VOICE_ASSIST)

        /** Long presses on some wheels repeat the press; ignore triggers closer than this. */
        private const val DEBOUNCE_MS = 700L

        fun start(context: Context, onVoiceKey: (Int) -> Unit): Result<EcarxKeyInterceptor> = runCatching {
            val inputClass = Class.forName("com.ecarx.xui.adaptapi.input.Input")
            val callbackClass = Class.forName("com.ecarx.xui.adaptapi.input.IKeyCallback")
            val input = inputClass.getMethod("create", Context::class.java).invoke(null, context.applicationContext)
                ?: error("Input.create returned null")
            val main = Handler(Looper.getMainLooper())
            var lastFire = 0L
            val handler = InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "onKeyPressed" -> {
                        val code = (args?.getOrNull(0) as? Int) ?: -1
                        Log.i(TAG, "ecarx_key_pressed code=$code")
                        code in VOICE_KEYS
                    }
                    "onKeyReleased" -> {
                        val code = (args?.getOrNull(0) as? Int) ?: -1
                        Log.i(TAG, "ecarx_key_released code=$code")
                        if (code in VOICE_KEYS) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastFire >= DEBOUNCE_MS) {
                                lastFire = now
                                main.post { runCatching { onVoiceKey(code) } }
                            }
                            true
                        } else false
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "THTVoiceKeyCallback"
                    else -> null
                }
            }
            val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass), handler)
            val request = input.javaClass.methods.firstOrNull {
                it.name == "requestKeysInterception" && it.parameterTypes.size == 2
            } ?: error("requestKeysInterception unavailable on ${input.javaClass.name}")
            val granted = (request.invoke(input, VOICE_KEYS, callback) as? IntArray) ?: IntArray(0)
            Log.i(TAG, "ecarx_key_interception requested=${VOICE_KEYS.joinToString()} granted=${granted.joinToString()}")
            EcarxKeyInterceptor(input, callback, granted)
        }
    }
}
