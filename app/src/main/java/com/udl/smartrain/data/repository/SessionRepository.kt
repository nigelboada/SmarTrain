package com.udl.smartrain.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.udl.smartrain.data.local.SessionDao
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.tasks.await

interface SessionRepository {
    suspend fun saveSession(session: Session): Result<Unit>
    suspend fun getSessionHistory(userId: String): List<Session>
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val firestore: FirebaseFirestore
) : SessionRepository {

    override suspend fun saveSession(session: Session): Result<Unit> = try {
        // 1. Guardem primer en LOCAL (sempre funciona, encara que no hi hagi internet)
        sessionDao.insertSession(session)

        // 2. Intentem guardar en REMOT (Firebase)
        firestore.collection("sessions")
            .document(session.id)
            .set(session)
            .await()

        // 3. Si ha pujat bé, actualitzem el camp 'isSynced' en local
        sessionDao.insertSession(session.copy(isSynced = true))

        Result.success(Unit)
    } catch (e: Exception) {
        // Si falla Firebase, l'app segueix funcionant perquè ja està a Room!
        Result.failure(e)
    }

    override suspend fun getSessionHistory(userId: String): List<Session> {
        // Aquí podríem decidir si llegir de Room o de Firebase
        return emptyList() // Ho omplirem després
    }
}