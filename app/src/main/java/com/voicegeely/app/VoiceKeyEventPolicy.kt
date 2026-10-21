package com.voicegeely.app

/** Android key fallback from COOLRAY2025_VOICE_SPEC §8; hardware mapping is UNVERIFIED. */
class VoiceKeyEventPolicy {
    private val captured = mutableSetOf<Pair<Int, Int>>()

    /** Keep DOWN/repeats/UP together. Only an initial DOWN may open a listening session. */
    fun handle(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        canceled: Boolean,
        deviceId: Int,
        onPress: () -> Boolean
    ): Boolean {
        if (!isVoiceKey(keyCode)) return false
        val key = deviceId to keyCode
        return when (action) {
            ACTION_DOWN -> {
                if (repeatCount > 0) key in captured
                else {
                    captured.remove(key)
                    if (!canceled && onPress()) {
                        captured.add(key)
                        true
                    } else false
                }
            }
            ACTION_UP -> captured.remove(key)
            else -> false
        }
    }

    fun clear() = captured.clear()

    companion object {
        // Android KeyEvent constants kept numeric here so the policy can run in plain JVM tests.
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val KEYCODE_SEARCH = 84
        const val KEYCODE_ASSIST = 219
        const val KEYCODE_VOICE_ASSIST = 231

        fun isVoiceKey(keyCode: Int): Boolean = keyCode == KEYCODE_SEARCH ||
            keyCode == KEYCODE_ASSIST || keyCode == KEYCODE_VOICE_ASSIST
    }
}
