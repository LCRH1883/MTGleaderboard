package com.intagri.mtgleader.ui.setup.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.intagri.mtgleader.R

object ScThemeUtils {
    fun resolveThemedTitle(context: Context, theme: SpellCounterTheme): CharSequence {
        return context.getString(R.string.app_name)
    }

    fun resolveTheme(context: Context, theme: SpellCounterTheme): SpellCounterTheme {
        return if (theme != SpellCounterTheme.NOT_SET) {
            theme
        } else {
            when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    SpellCounterTheme.DARK
                }
                Configuration.UI_MODE_NIGHT_NO -> {
                    SpellCounterTheme.KARN
                }
                else -> {
                    SpellCounterTheme.KARN
                }
            }
        }
    }

    fun resolveThemeColor(context: Context, @AttrRes themeColorResId: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(themeColorResId, tv, true)
        return tv.data
    }

    fun resolveThemeDrawable(context: Context, @AttrRes drawableAttrResId: Int): Drawable? {
        val tv = TypedValue()
        context.theme.resolveAttribute(drawableAttrResId, tv, true)
        return ContextCompat.getDrawable(context, tv.resourceId)
    }

    fun isLightTheme(context: Context): Boolean {
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.scIsLightTheme, tv, true)
        return tv.data != 0
    }
}

val Context.previewBackgroundColor: Int
    @ColorInt
    get() = ColorUtils.setAlphaComponent(
        ContextCompat.getColor(
            this,
            R.color.accent_blue
        ), this.resources.getInteger(R.integer.player_color_alpha)
    )
