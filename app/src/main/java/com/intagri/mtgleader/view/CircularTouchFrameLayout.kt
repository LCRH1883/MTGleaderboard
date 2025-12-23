package com.intagri.mtgleader.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class CircularTouchFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var handlingTouch = false

    // Limit touch handling to a circular area centered in the view.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            handlingTouch = isWithinCircle(ev.x, ev.y)
        }
        if (!handlingTouch) {
            return false
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            handlingTouch = false
        }
        return handled
    }

    private fun isWithinCircle(localX: Float, localY: Float): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width.coerceAtMost(height) / 2f
        val dx = localX - centerX
        val dy = localY - centerY
        return dx * dx + dy * dy <= radius * radius
    }
}
