package com.udl.smartrain.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val sessionName: String = "Sessio nova",
    val startTime: Date = Date(),
    val durationSeconds: Long = 0,
    val distanceMetres: Double = 0.0,
    val avgBpm: Int? = null,
    val intensityScore: Double = 0.0,
    val dominantActivity: String = "",
    val avgMlConfidence: Double = 0.0,
    val mlPredictionCount: Int = 0,
    val highIntensityCount: Int = 0,
    val activityTimeline: String = "",
    val ragTitle: String = "",
    val ragAnswer: String = "",
    val ragSourceTitles: String = "",
    val ragProvider: String = "rules",
    val ragModel: String = "rule_based",
    val ragLatencyMillis: Long = 0,
    val ragUsedFallback: Boolean = false,
    val ragFallbackReason: String = "",
    val isSynced: Boolean = false
) {
    constructor() : this(
        "",
        "",
        "Sessio nova",
        Date(),
        0,
        0.0,
        null,
        0.0,
        "",
        0.0,
        0,
        0,
        "",
        "",
        "",
        "",
        "rules",
        "rule_based",
        0,
        false,
        "",
        false
    )
}
