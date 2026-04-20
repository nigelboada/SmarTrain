package com.udl.smartrain.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.udl.smartrain.data.local.SessionDao
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

interface SessionRepository {
    suspend fun saveSession(session: Session): Result<Unit>
    suspend fun deleteSession(session: Session): Result<Unit>
    suspend fun updateSession(session: Session): Result<Unit>
    fun getSessionHistory(userId: String): Flow<List<Session>>
    fun getSessionsStream(): Flow<List<Session>> // <-- Afegeix això
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val firestore: FirebaseFirestore
) : SessionRepository {

    override suspend fun saveSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("DEBUG_DB", "--- Iniciant guardat de sessió: ${session.id} ---")

            // 1. Guardem a Room (Base de dades local)
            sessionDao.insertSession(session)
            Log.d("DEBUG_DB", "Pas 1: Guardat a Room correctament.")

            // 2. Guardem a Firebase (Backend)
            // Fem servir .await() per esperar a que Firebase acabi
            firestore.collection("sessions")
                .document(session.id)
                .set(session)
                .await()
            Log.d("DEBUG_DB", "Pas 2: Guardat a Firebase correctament.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DEBUG_DB", "Error fatal al guardar: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Esborrem de Room
            sessionDao.deleteSession(session)
            // 2. Esborrem de Firebase
            firestore.collection("sessions").document(session.id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSession(session: Session): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Actualitzem a Room
            sessionDao.updateSession(session)
            // 2. Actualitzem a Firestore (sobrescriu el document amb el mateix ID)
            firestore.collection("sessions").document(session.id).set(session).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSessionHistory(userId: String): Flow<List<Session>> {
        return sessionDao.getAllSessions()
    }

    override fun getSessionsStream(): Flow<List<Session>> {
        return sessionDao.getAllSessions() // Llegeix de Room per a màxima velocitat
    }
}