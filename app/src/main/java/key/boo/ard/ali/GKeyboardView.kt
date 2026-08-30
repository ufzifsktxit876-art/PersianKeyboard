package key.boo.ard.ali

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable

class GKeyboardView(context: Context, attrs: AttributeSet?) : android.inputmethodservice.KeyboardView(context, attrs) {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var highlightRect: RectF? = null

    fun setHighlightColor(color: Int) {
        highlightPaint.color = color
    }

    fun setHighlight(x: Int, y: Int, width: Int, height: Int) {
        highlightRect = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
        invalidate()
    }

    fun clearHighlight() {
        highlightRect = null
        invalidate()
    }

    /** استایل کلیدها رو در لحظه از تنظیمات کاربر می‌گیره و اعمال می‌کنه (رفلکشن روی فیلد داخلی KeyboardView). */
    fun applyDynamicStyle(normalDrawable: Drawable, pressedDrawable: Drawable, textColor: Int) {
        val stateList = StateListDrawable()
        stateList.setExitFadeDuration(0)
        stateList.setEnterFadeDuration(0)
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
        stateList.addState(intArrayOf(), normalDrawable)

        try {
            val bgField = android.inputmethodservice.KeyboardView::class.java.getDeclaredField("mKeyBackground")
            bgField.isAccessible = true
            bgField.set(this, stateList)
        } catch (_: Exception) { }

        try {
            val colorField = android.inputmethodservice.KeyboardView::class.java.getDeclaredField("mKeyTextColor")
            colorField.isAccessible = true
            colorField.set(this, textColor)
        } catch (_: Exception) { }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        highlightRect?.let { canvas.drawRoundRect(it, 12f, 12f, highlightPaint) }
    }
}
