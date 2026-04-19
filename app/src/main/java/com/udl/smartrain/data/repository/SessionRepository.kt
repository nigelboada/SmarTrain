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

    override fun getSessionHistory(userId: String): Flow<List<Session>> {
        return sessionDao.getAllSessions()
    }

    override fun getSessionsStream(): Flow<List<Session>> {
        return sessionDao.getAllSessions() // Llegeix de Room per a màxima velocitat
    }
}