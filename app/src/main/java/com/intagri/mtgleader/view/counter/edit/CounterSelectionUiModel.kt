package com.intagri.mtgleader.view.counter.edit

import com.intagri.mtgleader.model.counter.CounterTemplateModel

data class CounterSelectionUiModel(
    val template: CounterTemplateModel,
    val selected: Boolean = false,
)