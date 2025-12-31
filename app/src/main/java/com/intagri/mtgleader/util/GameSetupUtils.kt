package com.intagri.mtgleader.util

import com.intagri.mtgleader.model.counter.CounterTemplateModel
import com.intagri.mtgleader.model.player.PlayerColor
import com.intagri.mtgleader.model.player.PlayerSetupModel

object GameSetupUtils {
    fun applyColorCounters(players: List<PlayerSetupModel>): List<PlayerSetupModel> {
        if (players.isEmpty()) {
            return emptyList()
        }
        val allGameColors = players.map { it.color }.filter { it != PlayerColor.NONE }.toSet()
        val allGameColorCounters = allGameColors.map { color ->
            CounterTemplateModel(
                id = stableColorCounterId(color),
                color = color,
                startingValue = 0,
            )
        }
        return players.map { player ->
            player.profile?.let { profile ->
                player.copy(
                    profile = profile.copy(
                        counters = profile.counters.plus(
                            allGameColorCounters.filter { it.color != player.color }
                        )
                    )
                )
            } ?: player
        }
    }

    private fun stableColorCounterId(color: PlayerColor): Int {
        return -color.ordinal
    }
}
