package com.intagri.mtgleader.persistence.gamesession

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementUtilsTest {
    @Test
    fun computePlaces_handlesTiesByTurnAndActiveSeat() {
        val participants = listOf(
            participant(seatIndex = 0, eliminatedTurn = 2, eliminatedDuringSeat = 1),
            participant(seatIndex = 1, eliminatedTurn = 3, eliminatedDuringSeat = 2),
            participant(seatIndex = 2, eliminatedTurn = 3, eliminatedDuringSeat = 2),
            participant(seatIndex = 3, eliminatedTurn = 4, eliminatedDuringSeat = 3),
            participant(seatIndex = 4, eliminatedTurn = null, eliminatedDuringSeat = null),
        )

        val places = PlacementUtils.computePlaces(participants)

        assertEquals(5, places[0])
        assertEquals(3, places[1])
        assertEquals(3, places[2])
        assertEquals(2, places[3])
        assertEquals(1, places[4])
    }

    private fun participant(
        seatIndex: Int,
        eliminatedTurn: Int?,
        eliminatedDuringSeat: Int?,
    ): GameParticipantEntity {
        return GameParticipantEntity(
            localMatchId = "match-1",
            seatIndex = seatIndex,
            participantType = GameParticipantType.GUEST,
            profileName = null,
            userId = null,
            guestName = "Guest $seatIndex",
            displayName = "Guest $seatIndex",
            colorName = "RED",
            startingLife = 40,
            currentLife = 0,
            countersJson = null,
            eliminatedTurnNumber = eliminatedTurn,
            eliminatedDuringSeatIndex = eliminatedDuringSeat,
            place = null,
            totalTurnTimeMs = 0L,
            turnsTaken = 0,
        )
    }
}
