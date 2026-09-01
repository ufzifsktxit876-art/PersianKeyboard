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
    private var autoItems: List<String> = emptyList()
    private var autoLineIndex = 0
    private var autoCharIndex = 0
    private var autoFullMode = false
    private var autoRepeatsLeft = 1
    private var autoRunnable: Runnable? = null

    companion object {
        const val KEYCODE_MACRO_NEXT = -10
        const val KEYCODE_MACRO_RESET = -11
        const val FAST_MODE_THRESHOLD_MS = 15L
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
    private fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        if (bitmapCache.containsKey(uriString)) return bitmapCache[uriString]
        return try {
            val bmp = contentResolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it) }
            bitmapCache[uriString] = bmp; bmp
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

    private fun typingSpeedMs(): Long = prefs().getInt("auto_speed_ms", 45).toLong().coerceAtLeast(0)
    private fun enterDelayMs(): Long = prefs().getInt("enter_delay_ms", 350).toLong().coerceAtLeast(0)
    private fun repeatCount(): Int = prefs().getInt("auto_repeat_count", 1).coerceAtLeast(1)

    private fun startAuto() {
        if (autoActive) return
        val items = getItems()
        if (items.isEmpty()) return

        autoActive = true
        autoItems = items
        autoLineIndex = prefs().getInt("macro_line_index", 0) % items.size
        autoCharIndex = 0
        autoFullMode = prefs().getString("auto_mode", "FULL") == "FULL"
        autoRepeatsLeft = if (autoFullMode) repeatCount() else 1

        autoRunnable = object : Runnable {
            override fun run() {
                if (!autoActive) return
                val ic = currentInputConnection
                if (ic == null) { autoActive = false; return }

                val currentItem = autoItems[autoLineIndex]
                val speed = typingSpeedMs()

                // حالت فوق‌سریع: کل کلمه یکجا، بدون حلقه‌ی حرف‌به‌حرف — صفر لگ حتی روی سرعت ۰
                if (speed <= FAST_MODE_THRESHOLD_MS) {
                    if (currentItem.isNotEmpty()) ic.commitText(currentItem, 1)
                    proceedToEnter(ic)
                    return
                }

                if (autoCharIndex < currentItem.length) {
                    val ch = currentItem[autoCharIndex]
                    ic.commitText(ch.toString(), 1)
                    keyboardView.setAutoPressedKeyByCode(ch.code)
                    autoCharIndex++
                    handler.postDelayed(this, speed)
                    return
                }

                proceedToEnter(ic)
            }
        }
        handler.post(autoRunnable!!)
    }

    /** فیدبک بصری واقعی روی خود دکمه‌ی Enter + ارسال واقعی KeyEvent، بعد رفتن سراغ آیتم بعدی. */
    private fun proceedToEnter(ic: InputConnection) {
        keyboardView.setAutoPressedKeyByCode(Keyboard.KEYCODE_DONE)
        sendRealEnter(ic)

        val nextIndex = (autoLineIndex + 1) % autoItems.size
        prefs().edit().putInt("macro_line_index", nextIndex).apply()

        val clearDelay = minOf(enterDelayMs(), 120L).coerceAtLeast(30L)
        handler.postDelayed({ if (::keyboardView.isInitialized) keyboardView.setAutoPressedKeyByCode(null) }, clearDelay)

        if (!autoFullMode) { autoActive = false; return }

        if (nextIndex == 0) {
            autoRepeatsLeft--
            if (autoRepeatsLeft <= 0) { autoActive = false; return }
        }

        autoLineIndex = nextIndex
        autoCharIndex = 0
        autoRunnable?.let { handler.postDelayed(it, enterDelayMs()) }
    }

    private fun stopAuto() {
        autoActive = false
        autoRunnable?.let { handler.removeCallbacks(it) }
        autoRunnable = null
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
