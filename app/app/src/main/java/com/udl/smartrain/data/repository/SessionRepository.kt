package com.udl.smartrain.data.repository

import com.udl.smartrain.domain.model.Session

interface SessionRepository {
    suspend fun saveSession(session: Session): Result<Unit>
    suspend fun getSessionHistory(userId: String): List<Session>
}

class SessionRepositoryImpl : SessionRepository {
    override suspend fun saveSession(session: Session): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getSessionHistory(userId: String): List<Session> {
        return emptyList()
    }
}