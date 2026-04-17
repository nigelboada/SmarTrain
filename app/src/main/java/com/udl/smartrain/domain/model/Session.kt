package com.udl.smartrain.domain.model

import java.util.Date

data class Session(
    val id: String = "",
    val userId: String = "",
    val startTime: Date = Date(),
    val durationSeconds: Long = 0,
    val distanceMetres: Double = 0.0,
    val avgBpm: Int? = null,
    val intensityScore: Double = 0.0
)