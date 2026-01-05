package com.intagri.mtgleader.ui.setup.theme

import androidx.annotation.StyleRes
import com.intagri.mtgleader.R

enum class SpellCounterTheme(
    val id: Long, //Must be unique and must not change
    @StyleRes val resId: Int,
) {
    NOT_SET(
        id = 0L,
        resId = R.style.DarkTheme
    ),
    DARK(
        id = 2L,
        resId = R.style.DarkTheme,
    );

    companion object {
        fun fromId(id: Long): SpellCounterTheme {
            return when (id) {
                0L -> NOT_SET
                else -> DARK
            }
        }
    }
}
