package key.boo.ard.ali

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class PersianKeyboardService : InputMethodService(), GKeyboardView.OnKeyClickListener {

    private lateinit var keyboardView: GKeyboardView
    private lateinit var activeKeyboard: Keyboard
    private var currentLayoutId: String = "CUSTOM"

    private val handler = Handler(Looper.getMainLooper())

    private var autoActive = false
    // شناسه‌ی هر اجرا؛ هر بار شروع/توقف عوض می‌شه تا callback های قدیمیِ جامونده (که می‌تونستن
    // باعث پریدن/جابه‌جایی آیتم‌ها بشن) خودشون رو تشخیص بدن و کاری نکنن.
    private var autoSession = 0
    private var autoItems: List<String> = emptyList()
    private var autoLineIndex = 0
    private var autoCharIndex = 0
    private var autoFullMode = false
    private var autoRepeatsLeft = 1
    private var autoRunnable: Runnable? = null
    private var batchOpen = false

    companion object {
        const val KEYCODE_MACRO_NEXT = -10
        const val KEYCODE_MACRO_RESET = -11
        const val MIN_SAFE_TICK_MS = 4L
        const val MAX_TEXTURE_DIMENSION = 512
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null) as GKeyboardView
        keyboardView = view
        keyboardView.listener = this
        loadKeyboardForCurrentSettings()
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keyboardView.isInitialized) {
            if (!restarting) stopAuto()
            loadKeyboardForCurrentSettings()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopAuto()
    }

    private fun prefs() = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)

    private fun loadKeyboardForCurrentSettings() {
        val p = prefs()
        currentLayoutId = p.getString("layout_mode", "CUSTOM") ?: "CUSTOM"
        val resId = when (currentLayoutId) {
            "SAMSUNG" -> R.xml.keyboard_samsung_medium
            "DOLINE" -> R.xml.keyboard_doline
            "PCBOARD" -> R.xml.keyboard_pcboard
            else -> R.xml.keyboard_persian_letters_medium
        }
        val kb = Keyboard(this, resId)
        activeKeyboard = kb
        keyboardView.keyboard = kb
        keyboardView.userScale = p.getInt("keyboard_scale", 100) / 100f
        applyStyle(p)
    }

    private fun applyStyle(p: android.content.SharedPreferences) {
        val prefix = "theme_${currentLayoutId}_"
        val bgColor = p.getInt(prefix + "bg_color", Color.parseColor("#E6E8EB"))
        val keyColor = p.getInt(prefix + "key_color", Color.WHITE)
        val pressColor = p.getInt(prefix + "press_color", Color.parseColor("#D0D0D0"))
        val textColor = p.getInt(prefix + "text_color", Color.parseColor("#1F1F1F"))
        val textSizeSp = p.getInt(prefix + "text_size", 20).toFloat()
        val textSizePx = textSizeSp * resources.displayMetrics.scaledDensity

        val bgBitmap = loadBitmap(p.getString(prefix + "bg_texture", null))
        val keyIdleBitmap = loadBitmap(p.getString(prefix + "key_idle_texture", null))
        val keyClickBitmap = loadBitmap(p.getString(prefix + "key_click_texture", null))

        val overrides = mutableMapOf<Int, String>()
        activeKeyboard.keys.mapNotNull { it.codes.getOrNull(0) }.distinct().forEach { code ->
            if (p.getBoolean("label_enabled_${currentLayoutId}_$code", false)) {
                val custom = p.getString("label_${currentLayoutId}_$code", null)
                if (!custom.isNullOrBlank()) overrides[code] = custom
            }
        }

        keyboardView.setStyle(bgBitmap, bgColor, keyIdleBitmap, keyColor, keyClickBitmap, pressColor, textColor, textSizePx, overrides)
    }

    private val bitmapCache = HashMap<String, Bitmap?>()

    /** عکس رو فقط یک‌بار، با اندازه‌ی نمونه‌گیری‌شده (downsampled) کش می‌کنیم — رسم بعدی‌ها بسیار سبک‌ترن. */
    private fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        if (bitmapCache.containsKey(uriString)) return bitmapCache[uriString]
        return try {
            val uri = Uri.parse(uriString)

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

            var sampleSize = 1
            val longestSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (longestSide / sampleSize > MAX_TEXTURE_DIMENSION) sampleSize *= 2

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }

            bitmapCache[uriString] = bmp
            bmp
        } catch (_: Exception) { null }
    }

    override fun onKeyClicked(code: Int) {
        val ic: InputConnection = currentInputConnection ?: return
        when (code) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> sendRealEnter(currentInputConnection)
            KEYCODE_MACRO_NEXT -> tryStartAuto()
            KEYCODE_MACRO_RESET -> resetMacro()
            else -> ic.commitText(code.toChar().toString(), 1)
        }
    }

    private fun tryStartAuto() {
        if (!prefs().getBoolean("auto_master_enabled", true)) return
        startAuto()
    }

    private fun getItems(): List<String> {
        val raw = prefs().getString("macro_items", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun typingSpeedMs(): Long = prefs().getInt("auto_speed_ms", 15).toLong().coerceAtLeast(0L)
    private fun enterDelayMs(): Long = prefs().getInt("enter_delay_ms", 350).toLong().coerceAtLeast(0L)
    private fun repeatCount(): Int = prefs().getInt("auto_repeat_count", 1).coerceAtLeast(1)
    private fun highlightDuringAuto(): Boolean = prefs().getBoolean("auto_show_highlight", false)

    private fun startAuto() {
        if (autoActive) return
        val items = getItems()
        if (items.isEmpty()) return

        autoActive = true
        autoSession++
        val mySession = autoSession
        autoItems = items
        autoLineIndex = prefs().getInt("macro_line_index", 0) % items.size
        autoCharIndex = 0
        autoFullMode = prefs().getString("auto_mode", "FULL") == "FULL"
        autoRepeatsLeft = if (autoFullMode) repeatCount() else 1

        openBatchIfNeeded()
        val showHighlight = highlightDuringAuto()

        // بعد از Enter، چند بار (نه فقط یکی) با فاصله‌ی کوتاه چک می‌کنیم کادر خالی شده یا نه.
        // این تعادل بین سرعت و مطمئن‌بودنه: برای گوشی/شبکه‌ی معمولی همون چک اول کافیه (سریع)،
        // ولی اگه گوشی داغ/کند بود یا شبکه لحظه‌ای کند شد، تا ۴ بار دیگه (هر بار ۲۰ میلی‌ثانیه)
        // صبر می‌کنه قبل از شروع کلمه‌ی بعدی — دقیقاً همینجا بود که «حرف اول کلمه‌ی بعدی» گم می‌شد.
        fun waitForFieldClearedThenAdvance(nextIndex: Int) {
            fun advance() {
                if (autoSession != mySession) return
                autoLineIndex = nextIndex
                autoCharIndex = 0
                openBatchIfNeeded()
                autoRunnable?.let { handler.post(it) }
            }
            fun checkAttempt(attemptsLeft: Int) {
                if (autoSession != mySession || !autoActive) return
                val ic2 = currentInputConnection
                if (ic2 == null) { autoActive = false; return }
                val stillHasText = !ic2.getTextBeforeCursor(1, 0).isNullOrEmpty()
                if (!stillHasText || attemptsLeft <= 0) {
                    advance()
                } else {
                    handler.postDelayed({ checkAttempt(attemptsLeft - 1) }, 20L)
                }
            }
            handler.postDelayed({ checkAttempt(4) }, enterDelayMs())
        }

        fun finishItemAndAdvance(ic: InputConnection) {
            if (autoSession != mySession) return
            // هایلایت بصری روی Enter — دقیقاً همون چیزی که می‌خواستی: کلید Enter هم واقعاً «کلیک» نشون داده بشه
            if (showHighlight) keyboardView.setAutoPressedKeyByCode(Keyboard.KEYCODE_DONE)
            closeBatchIfNeeded(ic)
            sendRealEnter(ic)

            val nextIndex = (autoLineIndex + 1) % autoItems.size
            prefs().edit().putInt("macro_line_index", nextIndex).apply()

            if (showHighlight) {
                val clearDelay = minOf(enterDelayMs(), 120L)
                handler.postDelayed({
                    if (autoSession == mySession && ::keyboardView.isInitialized) keyboardView.setAutoPressedKeyByCode(null)
                }, clearDelay)
            }

            if (!autoFullMode) { autoActive = false; return }

            if (nextIndex == 0) {
                autoRepeatsLeft--
                if (autoRepeatsLeft <= 0) { autoActive = false; return }
            }

            waitForFieldClearedThenAdvance(nextIndex)
        }

        autoRunnable = object : Runnable {
            override fun run() {
                if (autoSession != mySession || !autoActive) return
                val ic = currentInputConnection
                if (ic == null) { autoActive = false; return }

                val currentItem = autoItems[autoLineIndex]

                // تایپ واقعی و حرف‌به‌حرف، هم‌زمان با هایلایت روی همون کلید — دقیقاً چیزی که می‌خواستی:
                // ببینی کلمات واقعاً روی کلیدها کلیک می‌شن، نه یه‌جا کپی‌پیست مصنوعی.
                if (autoCharIndex < currentItem.length) {
                    val ch = currentItem[autoCharIndex]
                    ic.commitText(ch.toString(), 1)
                    if (showHighlight) keyboardView.setAutoPressedKeyByCode(ch.code)
                    autoCharIndex++
                    handler.postDelayed(this, typingSpeedMs())
                    return
                }

                finishItemAndAdvance(ic)
            }
        }
        handler.post(autoRunnable!!)
    }

    private fun openBatchIfNeeded() {
        if (!batchOpen) {
            currentInputConnection?.beginBatchEdit()
            batchOpen = true
        }
    }

    private fun closeBatchIfNeeded(ic: InputConnection) {
        if (batchOpen) {
            ic.endBatchEdit()
            batchOpen = false
        }
    }

    private fun stopAuto() {
        autoActive = false
        autoRunnable?.let { handler.removeCallbacks(it) }
        autoRunnable = null
        if (batchOpen) { currentInputConnection?.endBatchEdit(); batchOpen = false }
        if (::keyboardView.isInitialized) keyboardView.setAutoPressedKeyByCode(null)
    }

    private fun resetMacro() {
        stopAuto()
        prefs().edit().putInt("macro_line_index", 0).apply()
    }

    private fun sendRealEnter(ic: InputConnection?) {
        if (ic == null) return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}

