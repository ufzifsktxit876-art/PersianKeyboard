package key.boo.ard.ali

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GKeyboardView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    interface OnKeyClickListener {
        fun onKeyClicked(code: Int)
    }

    var noPressVisualCodes: Set<Int> = setOf(-10, -11)
    var userScale: Float = 1f
        set(value) { field = value.coerceIn(0.7f, 1.3f); requestLayout(); invalidate() }

    var keyboard: Keyboard? = null
        set(value) { field = value; requestLayout(); invalidate() }

    var listener: OnKeyClickListener? = null
    private val handler = Handler(Looper.getMainLooper())

    // عرض «طراحی‌شده»ی کیبورد — همون عرضی که کلاس Keyboard موقع محاسبه‌ی %p استفاده کرده (عرض کامل صفحه)
    private val designWidthPx: Int = context.resources.displayMetrics.widthPixels

    private var bgBitmap: Bitmap? = null
    private var bgColor: Int = Color.parseColor("#E6E8EB")
    private var keyIdleBitmap: Bitmap? = null
    private var keyIdleColor: Int = Color.WHITE
    private var keyClickBitmap: Bitmap? = null
    private var keyClickColor: Int = Color.parseColor("#D0D0D0")
    private var textColor: Int = Color.parseColor("#1F1F1F")
    private var textSizePx: Float = 40f
    private var labelOverrides: Map<Int, String> = emptyMap()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pressedKey: Keyboard.Key? = null
    private var repeatRunnable: Runnable? = null

    // ضریب فعلی رندر: عرض واقعی View تقسیم بر عرض طراحی، ضربدر ترجیح دستی کاربر
    private var renderScale: Float = 1f

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun setStyle(
        bgBitmap: Bitmap?, bgColor: Int,
        keyIdleBitmap: Bitmap?, keyIdleColor: Int,
        keyClickBitmap: Bitmap?, keyClickColor: Int,
        textColor: Int, textSizePx: Float,
        labelOverrides: Map<Int, String>
    ) {
        this.bgBitmap = bgBitmap; this.bgColor = bgColor
        this.keyIdleBitmap = keyIdleBitmap; this.keyIdleColor = keyIdleColor
        this.keyClickBitmap = keyClickBitmap; this.keyClickColor = keyClickColor
        this.textColor = textColor; this.textSizePx = textSizePx
        this.labelOverrides = labelOverrides
        invalidate()
    }

    fun setAutoPressedKeyByCode(code: Int?) {
        pressedKey = if (code == null) null else keyboard?.keys?.firstOrNull { it.codes.getOrNull(0) == code }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val fitScale = if (designWidthPx > 0) viewWidth.toFloat() / designWidthPx.toFloat() else 1f
        renderScale = fitScale * userScale

        val designContentHeight = keyboard?.keys?.maxOfOrNull { it.y + it.height } ?: 200
        val height = (designContentHeight * renderScale).toInt()
        setMeasuredDimension(viewWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kb = keyboard ?: return

        if (bgBitmap != null) drawBitmapCover(canvas, bgBitmap!!, RectF(0f, 0f, width.toFloat(), height.toFloat()), 0f)
        else canvas.drawColor(bgColor)

        canvas.save()
        canvas.scale(renderScale, renderScale)

        for (key in kb.keys) {
            val code = key.codes.getOrNull(0) ?: 0
            val canShowPress = code !in noPressVisualCodes
            val isPressed = canShowPress && key === pressedKey

            val rect = RectF((key.x + 2).toFloat(), (key.y + 3).toFloat(), (key.x + key.width - 2).toFloat(), (key.y + key.height - 3).toFloat())
            val bmp = if (isPressed) keyClickBitmap else keyIdleBitmap
            val color = if (isPressed) keyClickColor else keyIdleColor

            if (bmp != null) drawBitmapCover(canvas, bmp, rect, 14f)
            else { keyPaint.color = color; canvas.drawRoundRect(rect, 14f, 14f, keyPaint) }

            if (key.icon != null) {
                val icon = key.icon
                val iw = icon.intrinsicWidth; val ih = icon.intrinsicHeight
                val left = key.x + (key.width - iw) / 2; val top = key.y + (key.height - ih) / 2
                icon.setBounds(left, top, left + iw, top + ih)
                icon.setTint(textColor)
                icon.draw(canvas)
            } else {
                val label = labelOverrides[code] ?: key.label?.toString() ?: ""
                if (label.isNotEmpty()) {
                    val maxTextSize = key.height * 0.38f
                    textPaint.textSize = minOf(textSizePx, maxTextSize)
                    textPaint.color = textColor
                    val cx = key.x + key.width / 2f
                    val cy = key.y + key.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(label, cx, cy, textPaint)
                }
            }
        }
        canvas.restore()
    }

    private fun drawBitmapCover(canvas: Canvas, bmp: Bitmap, rect: RectF, radius: Float) {
        canvas.save()
        val path = Path(); path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        val scale = maxOf(rect.width() / bmp.width, rect.height() / bmp.height)
        val scaledW = bmp.width * scale; val scaledH = bmp.height * scale
        val left = rect.left + (rect.width() - scaledW) / 2f
        val top = rect.top + (rect.height() - scaledH) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + scaledW, top + scaledH), null)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val kb = keyboard ?: return false
        // مختصات لمس رو به فضای "طراحی" کیبورد برمی‌گردونیم (معکوس renderScale)
        val x = (event.x / renderScale).toInt()
        val y = (event.y / renderScale).toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKeyAt(kb, x, y)
                pressedKey = key
                invalidate()
                if (key != null && key.repeatable) startRepeat(key)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val key = findKeyAt(kb, x, y)
                if (key !== pressedKey) { stopRepeat(); pressedKey = null; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                stopRepeat()
                val key = pressedKey
                pressedKey = null
                invalidate()
                if (key != null && !key.repeatable) {
                    key.codes.getOrNull(0)?.let { listener?.onKeyClicked(it) }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat(); pressedKey = null; invalidate()
                return true
            }
        }
        return false
    }

    private fun startRepeat(key: Keyboard.Key) {
        val code = key.codes.getOrNull(0) ?: return
        listener?.onKeyClicked(code)
        val r = object : Runnable {
            override fun run() {
                if (pressedKey === key) { listener?.onKeyClicked(code); handler.postDelayed(this, 60) }
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, 400)
    }

    private fun stopRepeat() { repeatRunnable?.let { handler.removeCallbacks(it) }; repeatRunnable = null }

    private fun findKeyAt(kb: Keyboard, x: Int, y: Int): Keyboard.Key? {
        for (key in kb.keys) {
            if (x >= key.x && x <= key.x + key.width && y >= key.y && y <= key.y + key.height) return key
        }
        return null
    }
}
