package com.voicegeely.app.car

/**
 * Head-unit identification for the Coolray 2025 build.
 *
 * The Coolray 2025 head unit reports `ro.product.model = SX11-LA` and `ro.product.name = IHU730P`
 * (getprop dump, docs/COOLRAY2025_PROPERTIES.md). When none of the properties match, vehicle write
 * commands are blocked so a Coolray 2025 property / function ID is never written to a different
 * head unit. Android commands (apps, media, navigation) are unaffected.
 */
object CoolrayHeadUnit {
    val SUPPORTED_IDS: List<String> = listOf("SX11-LA", "IHU730P")

    /** Same property list the reference checks, in the same order. */
    val PRODUCT_PROPERTIES: List<String> = listOf(
        "ro.product.name",
        "ro.product.model",
        "ro.product.device",
        "ro.product.system.name",
        "ro.product.system.model",
        "ro.product.system.device",
        "ro.product.vendor.name",
        "ro.product.vendor.model",
        "ro.product.vendor.device"
    )

    /** Returns the matched head-unit id, or null when no product property names a supported unit. */
    fun match(values: List<String?>): String? {
        for (value in values) {
            if (value.isNullOrBlank()) continue
            val upper = value.uppercase()
            SUPPORTED_IDS.firstOrNull { upper.contains(it) }?.let { return it }
        }
        return null
    }

    /** Reads `ro.product.*` through hidden android.os.SystemProperties; any failure means "not detected". */
    fun detect(readProperty: (String) -> String? = ::readSystemProperty): String? =
        match(PRODUCT_PROPERTIES.map { key -> runCatching { readProperty(key) }.getOrNull() })

    private fun readSystemProperty(key: String): String? {
        val clazz = Class.forName("android.os.SystemProperties")
        return clazz.getMethod("get", String::class.java, String::class.java).invoke(null, key, "") as? String
    }
}
