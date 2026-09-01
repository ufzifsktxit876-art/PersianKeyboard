package key.boo.ard.ali

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * نوار رنگی قابل کشیدن: محور افقی = طیف رنگ، محور عمودی = روشنایی (بالا روشن‌تر، پایین تیره‌تر).
 * لمس یا کشیدن روی هر نقطه، همون رنگ رو زنده انتخاب می‌کنه.
 */
class ColorPickerView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var onColorPicked: ((Int) -> Unit)? = null

    private var markerX = 0f
    private var markerY = 0f
    private var gradientBitmap: Bitmap? = null

    private val markerPaintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val markerPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        gradientBitmap = buildGradient(w, h)
        if (markerX == 0f && markerY == 0f) { markerX = w / 2f; markerY = h / 2f }
    }

    private fun buildGradient(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hueColors = IntArray(361) { Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)) }
        val huePaint = Paint()
        huePaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, hueColors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), huePaint)

        val brightnessPaint = Paint()
        brightnessPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.WHITE, Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), brightnessPaint)
        return bmp
    }

    /** برای هماهنگی وقتی رنگ از یه‌جای دیگه (مثلاً تایپ دستی Hex) عوض میشه، فقط نشونگر رو جابه‌جا می‌کنیم. */
    fun setColorExternally(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        markerX = (hsv[0] / 360f) * w
        markerY = if (hsv[2] >= 1f) h * (1f - hsv[1]) * 0.5f else h * (0.5f + (1f - hsv[2]) * 0.5f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        gradientBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawCircle(markerX, markerY, 14f, markerPaintOuter)
        canvas.drawCircle(markerX, markerY, 14f, markerPaintInner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            markerX = event.x.coerceIn(0f, width.toFloat())
            markerY = event.y.coerceIn(0f, height.toFloat())
            invalidate()
            val hue = (markerX / width) * 360f
            val yRatio = markerY / height
            val hsv = if (yRatio <= 0.5f) floatArrayOf(hue, 1f - (yRatio / 0.5f), 1f)
                      else floatArrayOf(hue, 1f, 1f - ((yRatio - 0.5f) / 0.5f))
            onColorPicked?.invoke(Color.HSVToColor(hsv))
            return true
        }
        return false
    }
}
