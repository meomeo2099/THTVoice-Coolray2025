package com.voicegeely.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoolrayHeadUnitTest {
    @Test fun matchesCoolray2025HeadUnitIds() {
        assertEquals("SX11-LA", CoolrayHeadUnit.match(listOf("", null, "SX11-LA")))
        assertEquals("IHU730P", CoolrayHeadUnit.match(listOf("ihu730p")))
    }

    @Test fun otherHeadUnitsAreNotCoolray2025() {
        assertNull(CoolrayHeadUnit.match(listOf("IHU629G", "IHU624G", "IHU689G", "E245", "EX5")))
        assertNull(CoolrayHeadUnit.match(emptyList()))
    }

    @Test fun detectChecksEveryProductPropertyAndSurvivesErrors() {
        val read = { key: String ->
            when (key) {
                "ro.product.name" -> throw IllegalStateException("hidden API")
                "ro.product.model" -> "SX11-LA"
                else -> " "
            }
        }
        assertEquals("SX11-LA", CoolrayHeadUnit.detect(read))
        assertNull(CoolrayHeadUnit.detect { null })
    }

    @Test fun jvmWithoutSystemPropertiesIsNotDetected() {
        assertNull(CoolrayHeadUnit.detect())
    }
}
