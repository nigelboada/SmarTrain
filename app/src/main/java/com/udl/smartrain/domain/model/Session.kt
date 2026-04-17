package com.udl.smartrain.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sessions") // <-- Assegura't que porta el parèntesi
data class Session(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val startTime: Date = Date(), // Room necessita el Converter per això
    val durationSeconds: Long = 0,
    val distanceMetres: Double = 0.0,
    val avgBpm: Int? = null,
    val intensityScore: Double = 0.0,
    val isSynced: Boolean = false
) {
    constructor() : this("", "", Date(), 0, 0.0, null, 0.0, false)
}