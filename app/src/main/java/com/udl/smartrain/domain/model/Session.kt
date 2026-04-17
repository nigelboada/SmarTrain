package com.udl.smartrain.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.Date

@Entity(tableName = "sessions") // Això diu a Room que crei una taula
data class Session(
    @PrimaryKey val id: String = "", // Firebase sol donar IDs de text
    val userId: String = "",
    val startTime: Date = Date(),
    val durationSeconds: Long = 0,
    val distanceMetres: Double = 0.0,
    val avgBpm: Int? = null,
    val intensityScore: Double = 0.0,
    val isSynced: Boolean = false // Camp clau per saber si ja s'ha pujat al núvol
) {
    // Constructor buit necessari per a Firebase Firestore
    constructor() : this("", "", Date(), 0, 0.0, null, 0.0, false)
}