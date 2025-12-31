package com.intagri.mtgleader.persistence.gamesession

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey
    val localMatchId: String,
    val clientMatchId: String,
    val createdAtEpoch: Long,
    val startedAtEpoch: Long? = null,
    val endedAtEpoch: Long? = null,
    val tabletopType: String,
    val status: String,
    val startingSeatIndex: Int? = null,
    val currentTurnNumber: Int,
    val currentActiveSeatIndex: Int? = null,
    val turnOwnerSeatIndex: Int? = null,
    val turnRotationClockwise: Boolean,
    val turnTimerEnabled: Boolean,
    val turnTimerDurationSeconds: Int,
    val turnTimerSeconds: Int,
    val turnTimerOvertime: Boolean,
    val gamePaused: Boolean,
    val gameElapsedSeconds: Long,
    val pendingSync: Boolean,
    val backendMatchId: String? = null,
    val updatedAtEpoch: Long,
)

@Entity(
    tableName = "game_participants",
    primaryKeys = ["localMatchId", "seatIndex"]
)
data class GameParticipantEntity(
    val localMatchId: String,
    val seatIndex: Int,
    val participantType: String,
    val profileName: String? = null,
    val userId: String? = null,
    val guestName: String? = null,
    val displayName: String,
    val colorName: String,
    val startingLife: Int,
    val currentLife: Int,
    val countersJson: String? = null,
    val eliminatedTurnNumber: Int? = null,
    val eliminatedDuringSeatIndex: Int? = null,
    val place: Int? = null,
    val totalTurnTimeMs: Long? = null,
    val turnsTaken: Int? = null,
)

data class GameSessionWithParticipants(
    @Embedded val session: GameSessionEntity,
    @Relation(
        parentColumn = "localMatchId",
        entityColumn = "localMatchId"
    )
    val participants: List<GameParticipantEntity>
)

object GameSessionStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
}

object GameParticipantType {
    const val ACCOUNT = "ACCOUNT"
    const val LOCAL_PROFILE = "LOCAL_PROFILE"
    const val GUEST = "GUEST"
}
