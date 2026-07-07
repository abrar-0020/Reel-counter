package com.example.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun insert(session: SessionEntity) {
        sessionDao.insertSession(session)
    }

    suspend fun deleteById(id: Int) {
        sessionDao.deleteSessionById(id)
    }
    
    suspend fun deleteAll() {
        sessionDao.deleteAllSessions()
    }
}
