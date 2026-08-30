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
    private var autoTypingActive = false

    companion object {
        const val KEYCODE_MACRO_NEXT = -10
        const val KEYCODE_MACRO_RESET = -11
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
            stopAutoTyping()
            loadKeyboardForCurrentSettings()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopAutoTyping()
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
        applyScale(kb, p.getInt("keyboard_scale", 100))
        activeKeyboard = kb
        keyboardView.keyboard = kb
        applyStyle(p)
    }

    /** بزرگ/کوچک‌کردن کل چیدمان با یه ضریب واحد؛ روی مختصات هر کلید مستقیم اعمال می‌شه. */
    private fun applyScale(kb: Keyboard, scalePercent: Int) {
        val scale = scalePercent.coerceIn(60, 150) / 100f
        if (scale == 1f) return
        for (key in kb.keys) {
            key.x = (key.x * scale).toInt()
            key.y = (key.y * scale).toInt()
            key.width = (key.width * scale).toInt()
            key.height = (key.height * scale).toInt()
        }
    }

    private fun applyStyle(p: android.content.SharedPreferences) {
        val prefix = "theme_${currentLayoutId}_"
        val bgColor = p.getInt(prefix + "bg_color", Color.parseColor("#E6E8EB"))
        val keyColor = p.getInt(prefix + "key_color", Color.WHITE)
        val pressColor = p.getInt(prefix + "press_color", Color.parseColor("#D0D0D0"))
        val textColor = p.getInt(prefix + "text_color", Color.parseColor("#1F1F1F"))
        val textSizeSp = p.getInt(prefix + "text_size", 22).toFloat()
        val textSizePx = textSizeSp * resources.displayMetrics.scaledDensity

        val bgBitmap = loadBitmap(p.getString(prefix + "bg_texture", null))
        val keyIdleBitmap = loadBitmap(p.getString(prefix + "key_idle_texture", null))
        val keyClickBitmap = loadBitmap(p.getString(prefix + "key_click_texture", null))

        val overrides = mutableMapOf<Int, String>()
        activeKeyboard.keys.map { it.codes.getOrNull(0) }.filterNotNull().distinct().forEach { code ->
            p.getString("label_${currentLayoutId}_$code", null)?.let { if (it.isNotBlank()) overrides[code] = it }
        }

        val highlightColor = Color.argb(90, Color.red(pressColor), Color.green(pressColor), Color.blue(pressColor))

        keyboardView.setStyle(
            bgBitmap, bgColor,
            keyIdleBitmap, keyColor,
            keyClickBitmap, pressColor,
            textColor, textSizePx,
            overrides, highlightColor
        )
    }

    private val bitmapCache = HashMap<String, Bitmap?>()
    private fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        if (bitmapCache.containsKey(uriString)) return bitmapCache[uriString]
        return try {
            val bmp = contentResolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it) }
            bitmapCache[uriString] = bmp
            bmp
        } catch (_: Exception) { null }
    }

    override fun onKeyClicked(code: Int) {
        val ic: InputConnection = currentInputConnection ?: return
        when (code) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> sendRealEnter(ic)
            KEYCODE_MACRO_NEXT -> {
                if (prefs().getBoolean("auto_master_enabled", true)) startAutoTyping(ic)
            }
            KEYCODE_MACRO_RESET -> resetMacro()
            else -> ic.commitText(code.toChar().toString(), 1)
        }
    }

    private fun getItems(): List<String> {
        val raw = prefs().getString("macro_items", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun isFullAutoMode(): Boolean = prefs().getString("auto_mode", "FULL") == "FULL"
    private fun typingSpeedMs(): Long = prefs().getInt("auto_speed_ms", 45).toLong().coerceAtLeast(10)
    private fun enterDelayMs(): Long = prefs().getInt("enter_delay_ms", 120).toLong().coerceAtLeast(0)

    private fun startAutoTyping(ic: InputConnection) {
        if (autoTypingActive) return
        val items = getItems()
        if (items.isEmpty()) return
        autoTypingActive = true
        val startIndex = prefs().getInt("macro_line_index", 0) % items.size
        typeItemSequence(ic, items, startIndex, isFullAutoMode())
    }

    /** تنها جایی که شمارنده رو جلو می‌بره، همینجاست — دقیقاً یک‌بار، بعد از اتمام کامل هر آیتم. */
    private fun typeItemSequence(ic: InputConnection, items: List<String>, lineIndex: Int, continueAll: Boolean) {
        val currentItem = items[lineIndex]
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (!autoTypingActive) return
                if (charIndex < currentItem.length) {
                    val ch = currentItem[charIndex]
                    ic.commitText(ch.toString(), 1)
                    highlightKeyFor(ch)
                    charIndex++
                    handler.postDelayed(this, typingSpeedMs())
                } else {
                    keyboardView.clearHighlight()
                    sendRealEnter(ic)
                    val nextIndex = (lineIndex + 1) % items.size
                    prefs().edit().putInt("macro_line_index", nextIndex).apply()

                    if (continueAll) {
                        handler.postDelayed({
                            if (autoTypingActive) typeItemSequence(ic, items, nextIndex, true)
                        }, enterDelayMs())
                    } else {
                        autoTypingActive = false
                    }
                }
            }
        }
        handler.post(runnable)
    }

    private fun highlightKeyFor(ch: Char) {
        val key = activeKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == ch.code }
        if (key != null) keyboardView.setHighlight(key.x, key.y, key.width, key.height)
    }

    private fun stopAutoTyping() {
        autoTypingActive = false
        handler.removeCallbacksAndMessages(null)
        if (::keyboardView.isInitialized) keyboardView.clearHighlight()
    }

    private fun resetMacro() {
        stopAutoTyping()
        prefs().edit().putInt("macro_line_index", 0).apply()
    }

    /**
     * به‌جای فرستادن خام کدِ Enter، دقیقاً همون مسیری رو صدا می‌زنیم که «دکمه‌ی واقعی ارسال»
     * در اپ‌هایی مثل تلگرام استفاده می‌کنه (performEditorAction). این باعث میشه رفتار
     * دقیقاً یکسان با فشردن Enter/Send واقعی خود اپ بشه، نه یه شبیه‌سازی ناقص.
     */
    private fun sendRealEnter(ic: InputConnection) {
        val info = currentInputEditorInfo
        val isMultiline = ((info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION

        if (!isMultiline && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
