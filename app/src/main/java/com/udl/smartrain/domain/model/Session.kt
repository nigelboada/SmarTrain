package com.udl.smartrain.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val sessionName: String = "Sessió nova", // Nou camp
    val startTime: Date = Date(),
    val durationSeconds: Long = 0,
    val distanceMetres: Double = 0.0,
    val avgBpm: Int? = null,
    val intensityScore: Double = 0.0,
    val isSynced: Boolean = false
) {
    // Constructor buit necessari per a Firebase
    constructor() : this("", "", "Sessió nova", Date(), 0, 0.0, null, 0.0, false)
}