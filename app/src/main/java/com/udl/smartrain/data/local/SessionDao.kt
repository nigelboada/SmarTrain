package com.udl.smartrain.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getSessionsByUser(userId: String): Flow<List<Session>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Query("SELECT * FROM sessions WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedSessions(userId: String): List<Session>
}
