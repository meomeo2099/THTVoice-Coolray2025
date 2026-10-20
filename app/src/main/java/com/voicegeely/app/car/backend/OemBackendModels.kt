package com.voicegeely.app.car.backend

data class OemWriteAttempt(
    val accepted: Boolean,
    val confirmed: Boolean,
    val backend: String,
    val message: String,
    val actualValue: Int? = null
)
