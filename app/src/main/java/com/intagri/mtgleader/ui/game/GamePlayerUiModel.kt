package com.intagri.mtgleader.ui.game

import com.intagri.mtgleader.model.player.PlayerModel
import com.intagri.mtgleader.view.counter.edit.CounterSelectionUiModel
import com.intagri.mtgleader.view.counter.edit.RearrangeCounterUiModel

data class GamePlayerUiModel(
    var model: PlayerModel,
    var counterSelections: List<CounterSelectionUiModel> = emptyList(),
    var rearrangeCounters: List<RearrangeCounterUiModel> = emptyList(),
    var newCounterAdded: Boolean = false,
    var pullToReveal: Boolean = false,
    var currentMenu: Menu = Menu.MAIN,
    var rearrangeButtonEnabled: Boolean = false,
    var isStartingPlayer: Boolean = false,
    var isStartingPlayerSelectable: Boolean = false,
    var isCurrentTurnPlayer: Boolean = false,
) {
    enum class Menu {
        MAIN,
        EDIT_COUNTERS,
        REARRANGE_COUNTERS,
    }
}
