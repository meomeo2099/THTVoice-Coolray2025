package com.voicegeely.app.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnameseNumberParserTest {
    @Test fun climateTwentyAndTwentyTwoNormalizeToNumericSetpoints() {
        assertEquals("đặt nhiệt độ 20 độ", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hai mươi độ"))
        assertEquals("đặt nhiệt độ 22 độ", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hai mươi hai độ"))
        assertEquals("đặt nhiệt độ 22 độ", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hai hai độ"))
    }

    @Test fun commonAsrHiForHaiCorruptionsNormalizeToTwentyTwo() {
        assertEquals("đặt nhiệt độ 22 độ", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hi mươi hi độ"))
        assertEquals("đặt nhiệt độ 22 độ", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hi mười hi độ"))
    }

    @Test fun explicitClimateHiAndLoRemainEndpoints() {
        assertEquals("đặt nhiệt độ hi", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ hi"))
        assertEquals("đặt nhiệt độ lo", VietnameseNumberParser.parseVietnameseNumber("đặt nhiệt độ lo"))
    }

    @Test fun lexicalKhongPhrasesAreNotConvertedToZero() {
        assertEquals("bật sạc không dây", VietnameseNumberParser.parseVietnameseNumber("bật sạc không dây"))
        assertEquals("lấy không khí ngoài", VietnameseNumberParser.parseVietnameseNumber("lấy không khí ngoài"))
    }

    @Test fun climateSetpointDetectorAcceptsOnly16Through32() {
        assertTrue(VietnameseNumberParser.hasClimateSetpoint("đặt nhiệt độ 16 độ"))
        assertTrue(VietnameseNumberParser.hasClimateSetpoint("đặt nhiệt độ 32 độ"))
        assertFalse(VietnameseNumberParser.hasClimateSetpoint("đặt nhiệt độ 15 độ"))
        assertFalse(VietnameseNumberParser.hasClimateSetpoint("đặt nhiệt độ 33 độ"))
    }

    @Test fun explicitHiIsAmbiguousOnlyBeforeNormalizerDisambiguation() {
        assertTrue(VietnameseNumberParser.isAmbiguousClimateHi("đặt nhiệt độ hi"))
        assertFalse(VietnameseNumberParser.isAmbiguousClimateHi("đặt nhiệt độ 22 độ"))
        assertFalse(VietnameseNumberParser.isAmbiguousClimateHi("đặt nhiệt độ hi mươi hi"))
    }
}
