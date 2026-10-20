package com.voicegeely.app.kws

/**
 * User-selectable offline wake phrase profiles.
 *
 * Only one profile is loaded into Sherpa KWS at a time so an unused phrase does
 * not remain active in the background.
 *
 * Owner decision 15/10/2026: the UI spells the compatibility wake phrases "Ge-ly" (no car-maker
 * name on screen). Only [displayLabel] changes; the keyword files/labels and what the driver says
 * stay the same.
 */
enum class WakePhrase(
    val displayLabel: String,
    val keywordFileName: String,
    val keywordLabel: String
) {
    HEY_THT(
        displayLabel = "Hey THT",
        keywordFileName = "keywords.txt",
        keywordLabel = "HEY_THT"
    ),
    HELLO_GEELY(
        displayLabel = "Hello Ge-ly",
        keywordFileName = "keywords-hello-geely.txt",
        keywordLabel = "HELLO_GEELY"
    ),
    HEY_GEELY(
        displayLabel = "Hey Ge-ly",
        keywordFileName = "keywords-hey-geely.txt",
        keywordLabel = "HEY_GEELY"
    )
}
