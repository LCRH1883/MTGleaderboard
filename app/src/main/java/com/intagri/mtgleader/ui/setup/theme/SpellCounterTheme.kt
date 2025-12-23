package com.intagri.mtgleader.ui.setup.theme

import androidx.annotation.StyleRes
import com.intagri.mtgleader.R

enum class SpellCounterTheme(
    val id: Long, //Must be unique and must not change
    @StyleRes val resId: Int,
) {
    NOT_SET(
        id = 0L,
        resId = R.style.Karn
    ),
    DARK(
        id = 2L,
        resId = R.style.DarkTheme,
    ),
    KARN(
        id = 8L,
        resId = R.style.Karn,
    );

    companion object {
        fun fromId(id: Long): SpellCounterTheme {
            // Map legacy theme ids to Karn/Dimir so saved selections stay valid.
            return when (id) {
                0L -> NOT_SET
                1L, 7L, 8L -> KARN
                2L, 3L, 5L, 6L -> DARK
                else -> NOT_SET
            }
        }
    }
}
