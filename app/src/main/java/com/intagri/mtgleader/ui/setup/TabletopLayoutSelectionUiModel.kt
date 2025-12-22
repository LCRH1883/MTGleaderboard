package com.intagri.mtgleader.ui.setup

import com.intagri.mtgleader.model.TabletopType

data class TabletopLayoutSelectionUiModel(
    val tabletopType: TabletopType,
    val selected: Boolean,
)