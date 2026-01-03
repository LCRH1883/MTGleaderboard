package com.intagri.mtgleader.persistence.gamesession

object PlacementUtils {

    data class PlacementKey(
        val eliminatedTurnNumber: Int,
        val eliminatedDuringSeatIndex: Int,
    )

    fun computePlaces(
        participants: List<GameParticipantEntity>,
    ): Map<Int, Int> {
        val eliminated = participants.mapNotNull { participant ->
            val turnNumber = participant.eliminatedTurnNumber ?: return@mapNotNull null
            val duringSeatIndex = participant.eliminatedDuringSeatIndex ?: -1
            participant.seatIndex to PlacementKey(turnNumber, duringSeatIndex)
        }
        if (eliminated.isEmpty()) {
            return participants.associate { it.seatIndex to 1 }
        }
        val grouped = eliminated.groupBy { it.second }
        val sortedGroups = grouped.entries.sortedWith(
            compareBy<Map.Entry<PlacementKey, List<Pair<Int, PlacementKey>>>> {
                it.key.eliminatedTurnNumber
            }.thenBy { it.key.eliminatedDuringSeatIndex }
        )
        val totalPlayers = participants.size
        val placeBySeat = mutableMapOf<Int, Int>()
        var alive = totalPlayers
        for (group in sortedGroups) {
            val eliminatedSeats = group.value.map { it.first }
            val eliminatedCount = eliminatedSeats.size
            val aliveAfter = (alive - eliminatedCount).coerceAtLeast(0)
            val placeForGroup = aliveAfter + 1
            eliminatedSeats.forEach { seatIndex ->
                placeBySeat[seatIndex] = placeForGroup
            }
            alive = aliveAfter
        }
        participants.forEach { participant ->
            if (!placeBySeat.containsKey(participant.seatIndex)) {
                placeBySeat[participant.seatIndex] = 1
            }
        }
        return placeBySeat
    }
}
