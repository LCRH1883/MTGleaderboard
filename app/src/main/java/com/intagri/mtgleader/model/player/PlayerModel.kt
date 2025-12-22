package com.intagri.mtgleader.model.player

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.intagri.mtgleader.model.counter.CounterModel
import com.intagri.mtgleader.model.counter.CounterTemplateModel
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerModel(
    val id: Int,
    val life: Int = 0,
    @ColorRes val colorResId: Int = 0,
    val lifeCounter: CounterTemplateModel?,
    val counters: List<CounterModel> = emptyList(),
) : Parcelable