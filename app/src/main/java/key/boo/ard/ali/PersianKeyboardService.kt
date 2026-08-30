package key.boo.ard.ali

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class PersianKeyboardService : InputMethodService(), OnKeyboardActionListener {

    private lateinit var keyboardView: GKeyboardView
    private lateinit var activeKeyboard: Keyboard

    private val handler = Handler(Looper.getMainLooper())
    private var autoTypingRunnable: Runnable? = null
    private var lastTriggerTime = 0L

    companion object {
        const val KEYCODE_MACRO_NEXT = -10
        const val KEYCODE_MACRO_RESET = -11
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null) as GKeyboardView
        keyboardView = view
        keyboardView.isPreviewEnabled = false
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.clearHighlight()

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
        val keyboardBgColor = p.getInt("keyboard_bg_color", Color.parseColor("#E6E8EB"))
        val keyBgColor = p.getInt("key_bg_color", Color.WHITE)
        val keyPressColor = p.getInt("key_press_color", Color.parseColor("#D0D0D0"))
        val textColor = p.getInt("key_text_color", Color.parseColor("#1F1F1F"))
        val clickStyle = p.getString("click_style", "CLICK1")

        val keyboardTextureUri = p.getString("keyboard_texture_uri", null)
        val clickTextureUri = p.getString("click_texture_uri", null)

        val keyboardBgDrawable: Drawable = loadBitmapDrawable(keyboardTextureUri) ?: ColorDrawable(keyboardBgColor)
        keyboardView.background = keyboardBgDrawable

        val normalDrawable = roundRect(keyBgColor)
        val pressedDrawable: Drawable = if (clickStyle == "CLICK2") {
            loadBitmapDrawable(clickTextureUri) ?: roundRect(keyPressColor)
        } else {
            roundRect(keyPressColor)
        }

        keyboardView.applyDynamicStyle(normalDrawable, pressedDrawable, textColor)
        keyboardView.setHighlightColor(Color.argb(90, Color.red(keyPressColor), Color.green(keyPressColor), Color.blue(keyPressColor)))
    }

    private fun roundRect(color: Int): Drawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = 14f
        d.setColor(color)
        return d
    }

    private var cachedBitmapUri: String? = null
    private var cachedBitmap: Bitmap? = null

    private fun loadBitmapDrawable(uriString: String?): Drawable? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val bmp = if (cachedBitmapUri == uriString && cachedBitmap != null) {
                cachedBitmap
            } else {
                val uri = Uri.parse(uriString)
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }.also {
                    cachedBitmap = it
                    cachedBitmapUri = uriString
                }
            }
            bmp?.let { BitmapDrawable(resources, it) }
        } catch (_: Exception) {
            null
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic: InputConnection = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> sendEnter(ic)
            KEYCODE_MACRO_NEXT -> {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime < 150) return
                lastTriggerTime = now
                startAutoTyping(ic)
            }
            KEYCODE_MACRO_RESET -> resetMacro()
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
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
        if (autoTypingRunnable != null) return
        val items = getItems()
        if (items.isEmpty()) return

        val p = prefs()
        val startIndex = p.getInt("macro_line_index", 0) % items.size
        val fullMode = isFullAutoMode()

        typeItemSequence(ic, items, startIndex, fullMode)
    }

    private fun typeItemSequence(ic: InputConnection, items: List<String>, lineIndex: Int, continueAll: Boolean) {
        val currentItem = items[lineIndex]
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (charIndex < currentItem.length) {
                    val ch = currentItem[charIndex]
                    ic.commitText(ch.toString(), 1)
                    highlightKeyFor(ch)
                    charIndex++
                    handler.postDelayed(this, typingSpeedMs())
                } else {
                    keyboardView.clearHighlight()
                    highlightEnterKey()
                    sendEnter(ic)
                    val nextIndex = (lineIndex + 1) % items.size
                    prefs().edit().putInt("macro_line_index", nextIndex).apply()

                    if (continueAll) {
                        handler.postDelayed({
                            keyboardView.clearHighlight()
                            typeItemSequence(ic, items, nextIndex, true)
                        }, enterDelayMs())
                    } else {
                        handler.postDelayed({ keyboardView.clearHighlight() }, 150)
                        autoTypingRunnable = null
                    }
                }
            }
        }
        autoTypingRunnable = runnable
        handler.post(runnable)
    }

    private fun highlightKeyFor(ch: Char) {
        val code = ch.code
        val key = activeKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == code }
        if (key != null) keyboardView.setHighlight(key.x, key.y, key.width, key.height)
    }

    private fun highlightEnterKey() {
        val key = activeKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == Keyboard.KEYCODE_DONE }
        if (key != null) keyboardView.setHighlight(key.x, key.y, key.width, key.height)
    }

    private fun stopAutoTyping() {
        autoTypingRunnable?.let { handler.removeCallbacksAndMessages(null) }
        autoTypingRunnable = null
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

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
