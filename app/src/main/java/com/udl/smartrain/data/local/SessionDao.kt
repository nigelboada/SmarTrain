package com.udl.smartrain.data.local

import androidx.room.*
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * FROM sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<Session>
}