package com.udl.smartrain.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.udl.smartrain.data.local.SessionDao
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface SessionRepository {
    suspend fun saveSession(session: Session): Result<Unit>
    suspend fun deleteSession(session: Session): Result<Unit>
    suspend fun updateSession(session: Session): Result<Unit>
    suspend fun syncRemoteSessions(userId: String): Result<Unit>
    suspend fun syncPendingSessions(userId: String): Result<Unit>
    fun getSessionHistory(userId: String): Flow<List<Session>>
    fun getSessionsStream(): Flow<List<Session>>
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val firestore: FirebaseFirestore
) : SessionRepository {

    override suspend fun saveSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localSession = session.copy(isSynced = false)
            sessionDao.insertSession(localSession)

            val syncedSession = session.copy(isSynced = true)
            firestore.collection(SESSIONS_COLLECTION)
                .document(session.id)
                .set(syncedSession)
                .await()

            sessionDao.insertSession(syncedSession)
            Log.d("DEBUG_DB", "Sessio guardada a Room i Firestore: ${session.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DEBUG_DB", "Error guardant sessio: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sessionDao.deleteSession(session)
            firestore.collection(SESSIONS_COLLECTION).document(session.id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localSession = session.copy(isSynced = false)
            sessionDao.updateSession(localSession)

            val syncedSession = session.copy(isSynced = true)
            firestore.collection(SESSIONS_COLLECTION).document(session.id).set(syncedSession).await()
            sessionDao.updateSession(syncedSession)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncRemoteSessions(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(SESSIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { document -> document.toObject(Session::class.java) }
                .forEach { remoteSession ->
                    sessionDao.insertSession(remoteSession.copy(isSynced = true))
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DEBUG_DB", "Error sincronitzant Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun syncPendingSessions(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sessionDao.getUnsyncedSessions(userId).forEach { pendingSession ->
                val syncedSession = pendingSession.copy(isSynced = true)
                firestore.collection(SESSIONS_COLLECTION)
                    .document(pendingSession.id)
                    .set(syncedSession)
                    .await()
                sessionDao.insertSession(syncedSession)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DEBUG_DB", "Error sincronitzant pendents: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun getSessionHistory(userId: String): Flow<List<Session>> {
        return sessionDao.getSessionsByUser(userId)
    }

    override fun getSessionsStream(): Flow<List<Session>> {
        return sessionDao.getAllSessions()
    }

    private companion object {
        const val SESSIONS_COLLECTION = "sessions"
    }
}
