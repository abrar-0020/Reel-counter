package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val reelsViewed: Int,
    val retries: Int,
    val totalParseTime: Long,
    val totalSignatureTime: Long,
    val totalConfirmationTime: Long,
    val falsePositives: Int = 0,
    val missedConfirmations: Int = 0
) {
    val averageSecondsPerReel: Float
        get() = if (reelsViewed > 0) durationSeconds.toFloat() / reelsViewed else 0f
        
    val averageLatencyMs: Float
        get() = if (reelsViewed > 0) totalConfirmationTime.toFloat() / reelsViewed else 0f
}
