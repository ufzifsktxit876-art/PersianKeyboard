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
        val layoutMode = p.getString("layout_mode", "CUSTOM")
        val size = p.getString("keyboard_size", "MEDIUM")

        val resId = if (layoutMode == "SAMSUNG") {
            R.xml.keyboard_samsung_medium
        } else {
            when (size) {
                "SMALL" -> R.xml.keyboard_persian_letters_small
                "LARGE" -> R.xml.keyboard_persian_letters_large
                else -> R.xml.keyboard_persian_letters_medium
            }
        }

        activeKeyboard = Keyboard(this, resId)
        keyboardView.keyboard = activeKeyboard
        applyStyle(p)
    }

    private fun applyStyle(p: android.content.SharedPreferences) {
        val bgColor = p.getInt("keyboard_bg_color", Color.parseColor("#E6E8EB"))
        val keyColor = p.getInt("key_bg_color", Color.WHITE)
        val pressColor = p.getInt("key_press_color", Color.parseColor("#D0D0D0"))
        val textColor = p.getInt("key_text_color", Color.parseColor("#1F1F1F"))
        val textSizeSp = p.getInt("key_text_size", 22).toFloat()
        val density = resources.displayMetrics.scaledDensity
        val textSizePx = textSizeSp * density

        val bgBitmap = loadBitmap(p.getString("keyboard_texture_uri", null))
        val keyIdleBitmap = loadBitmap(p.getString("key_idle_texture_uri", null))
        val keyClickBitmap = loadBitmap(p.getString("key_click_texture_uri", null))

        val overrides = mutableMapOf<Int, String>()
        p.getString("label_macro", null)?.let { if (it.isNotBlank()) overrides[KEYCODE_MACRO_NEXT] = it }
        p.getString("label_reset", null)?.let { if (it.isNotBlank()) overrides[KEYCODE_MACRO_RESET] = it }
        p.getString("label_space", null)?.let { if (it.isNotBlank()) overrides[32] = it }
        p.getString("label_period", null)?.let { if (it.isNotBlank()) overrides[46] = it }

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
            val uri = Uri.parse(uriString)
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            bitmapCache[uriString] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    override fun onKeyClicked(code: Int) {
        val ic: InputConnection = currentInputConnection ?: return
        when (code) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> sendEnter(ic)
            KEYCODE_MACRO_NEXT -> startAutoTyping(ic)
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
        val p = prefs()
        val startIndex = p.getInt("macro_line_index", 0) % items.size
        typeItemSequence(ic, items, startIndex, isFullAutoMode())
    }

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
                    sendEnter(ic)
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
        val code = ch.code
        val key = activeKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == code }
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

    private fun sendEnter(ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}
