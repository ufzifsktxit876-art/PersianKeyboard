package key.boo.ard.ali

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
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

    companion object {
        const val KEYCODE_MACRO_NEXT = -10
        const val KEYCODE_MACRO_RESET = -11
        const val KEYCODE_MACRO_SETTINGS = -12
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null) as GKeyboardView
        keyboardView = view

        loadKeyboardForCurrentSize()
        keyboardView.isPreviewEnabled = false
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.clearHighlight()

        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keyboardView.isInitialized) {
            stopAutoTyping()
            loadKeyboardForCurrentSize()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopAutoTyping()
    }

    private fun loadKeyboardForCurrentSize() {
        val size = prefs().getString("keyboard_size", "MEDIUM")
        val resId = when (size) {
            "SMALL" -> R.xml.keyboard_persian_letters_small
            "LARGE" -> R.xml.keyboard_persian_letters_large
            else -> R.xml.keyboard_persian_letters_medium
        }
        activeKeyboard = Keyboard(this, resId)
        keyboardView.keyboard = activeKeyboard
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic: InputConnection = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> sendEnter(ic)
            KEYCODE_MACRO_NEXT -> startAutoTyping(ic)
            KEYCODE_MACRO_RESET -> resetMacro()
            KEYCODE_MACRO_SETTINGS -> openMacroSettings()
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
        }
    }

    private fun prefs() = getSharedPreferences("macro_prefs", MODE_PRIVATE)

    private fun getItems(): List<String> {
        val raw = prefs().getString("macro_items", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun isCharMode(): Boolean = prefs().getString("auto_mode", "FULL") == "CHAR"
    private fun typingSpeedMs(): Long = prefs().getInt("auto_speed_ms", 45).toLong().coerceAtLeast(10)

    private fun startAutoTyping(ic: InputConnection) {
        if (autoTypingRunnable != null) return

        val items = getItems()
        if (items.isEmpty()) return

        val p = prefs()
        val lineIndex = p.getInt("macro_line_index", 0) % items.size
        val currentItem = items[lineIndex]

        if (!isCharMode()) {
            ic.commitText(currentItem, 1)
            sendEnter(ic)
            p.edit().putInt("macro_line_index", lineIndex + 1).apply()
            return
        }

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
                    sendEnter(ic)
                    p.edit().putInt("macro_line_index", lineIndex + 1).apply()
                    autoTypingRunnable = null
                }
            }
        }
        autoTypingRunnable = runnable
        handler.post(runnable)
    }

    private fun highlightKeyFor(ch: Char) {
        val code = ch.code
        val key = activeKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == code }
        if (key != null) {
            keyboardView.setHighlight(key.x, key.y, key.width, key.height)
        }
    }

    private fun stopAutoTyping() {
        autoTypingRunnable?.let { handler.removeCallbacks(it) }
        autoTypingRunnable = null
        if (::keyboardView.isInitialized) keyboardView.clearHighlight()
    }

    private fun resetMacro() {
        stopAutoTyping()
        prefs().edit().putInt("macro_line_index", 0).apply()
    }

    private fun openMacroSettings() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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
