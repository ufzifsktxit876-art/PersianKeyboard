package key.boo.ard.ali

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val customTypeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    private lateinit var editText: EditText
    private lateinit var modeGroup: RadioGroup
    private lateinit var sizeGroup: RadioGroup
    private lateinit var layoutGroup: RadioGroup
    private lateinit var clickStyleGroup: RadioGroup
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedLabel: TextView
    private lateinit var enterDelaySeekBar: SeekBar
    private lateinit var enterDelayLabel: TextView

    private lateinit var keyboardBgColorInput: EditText
    private lateinit var keyBgColorInput: EditText
    private lateinit var keyPressColorInput: EditText
    private lateinit var textColorInput: EditText

    private lateinit var labelMacroInput: EditText
    private lateinit var labelResetInput: EditText
    private lateinit var labelSpaceInput: EditText
    private lateinit var labelPeriodInput: EditText

    private var keyboardTextureUri: String? = null
    private var keyIdleTextureUri: String? = null
    private var keyClickTextureUri: String? = null

    private val PICK_KEYBOARD_BG = 2001
    private val PICK_KEY_IDLE = 2002
    private val PICK_KEY_CLICK = 2003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F5F5F7"))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(28, 28, 28, 80)
        scroll.addView(root)

        val header = TextView(this)
        header.text = "⌨ Salar @Ditayl"
        header.textSize = 24f
        header.typeface = Typeface.create(customTypeface, Typeface.BOLD)
        header.setTextColor(Color.parseColor("#0A84FF"))
        header.gravity = Gravity.CENTER
        header.setPadding(0, 12, 0, 28)
        root.addView(header)

        // ================= بخش ویژه Salar =================
        root.addView(sectionCard("⭐ گزینه ویژه Salar", "#4285F4") { card ->
            card.addView(hint("این بخش میان‌بر سریع به مهم‌ترین امکانات اختصاصی کیبورده — همه‌شون پایین‌تر هم با جزئیات کامل دوباره هستن."))
            card.addView(bullet("✓ تایپ خودکار با دو حالت (کامل / حرف‌به‌حرف)"))
            card.addView(bullet("✓ سه تکسچر جدا (پس‌زمینه، دکمه‌ها، حالت کلیک)"))
            card.addView(bullet("✓ رنگ‌بندی کامل و قابل تغییر"))
            card.addView(bullet("✓ دو چیدمان (سامسونگ / اختصاصی)"))
            card.addView(bullet("✓ امکان تغییر متن دکمه‌های ویژه"))
        })

        // ================= متن‌های آماده =================
        root.addView(sectionCard("📝 متن‌های آماده", "#34A853") { card ->
            card.addView(hint("هر خط یک آیتم مستقل محسوب می‌شود."))
            editText = EditText(this)
            editText.setText(prefs.getString("macro_items", "1\n2\n3\n4\n5"))
            editText.setLines(6)
            editText.gravity = Gravity.TOP
            editText.typeface = customTypeface
            card.addView(editText)
        })

        // ================= حالت اتوماسیون =================
        root.addView(sectionCard("⚡ تایپ خودکار", "#FBBC05") { card ->
            card.addView(hint("کامل: با یک کلیک، کل لیست پشت‌سرهم تایپ می‌شود. حرف به حرف: هر کلیک، فقط یک آیتم را تایپ می‌کند."))
            modeGroup = RadioGroup(this)
            modeGroup.orientation = RadioGroup.HORIZONTAL
            val fullRadio = RadioButton(this).apply { id = 1001; text = "کامل" }
            val charRadio = RadioButton(this).apply { id = 1002; text = "حرف به حرف" }
            modeGroup.addView(fullRadio)
            modeGroup.addView(charRadio)
            modeGroup.check(if (prefs.getString("auto_mode", "FULL") == "CHAR") 1002 else 1001)
            card.addView(modeGroup)

            card.addView(subHint("سرعت تایپ هر حرف"))
            speedLabel = TextView(this)
            speedSeekBar = SeekBar(this)
            speedSeekBar.max = 300
            val currentSpeed = prefs.getInt("auto_speed_ms", 45)
            speedSeekBar.progress = currentSpeed
            updateSpeedLabel(currentSpeed)
            speedSeekBar.setOnSeekBarChangeListener(seekListener { updateSpeedLabel(it.coerceAtLeast(10)) })
            card.addView(speedLabel)
            card.addView(speedSeekBar)

            card.addView(subHint("مکث بین آیتم‌ها (فقط حالت کامل)"))
            enterDelayLabel = TextView(this)
            enterDelaySeekBar = SeekBar(this)
            enterDelaySeekBar.max = 2000
            val currentDelay = prefs.getInt("enter_delay_ms", 120)
            enterDelaySeekBar.progress = currentDelay
            updateEnterDelayLabel(currentDelay)
            enterDelaySeekBar.setOnSeekBarChangeListener(seekListener { updateEnterDelayLabel(it) })
            card.addView(enterDelayLabel)
            card.addView(enterDelaySeekBar)
        })

        // ================= چیدمان و اندازه =================
        root.addView(sectionCard("⌨ چیدمان و اندازه", "#AB47BC") { card ->
            card.addView(subHint("چیدمان"))
            layoutGroup = RadioGroup(this)
            layoutGroup.orientation = RadioGroup.HORIZONTAL
            layoutGroup.addView(RadioButton(this).apply { id = 3001; text = "سبک سامسونگ" })
            layoutGroup.addView(RadioButton(this).apply { id = 3002; text = "اختصاصی" })
            layoutGroup.check(if (prefs.getString("layout_mode", "CUSTOM") == "SAMSUNG") 3001 else 3002)
            card.addView(layoutGroup)

            card.addView(subHint("اندازه"))
            sizeGroup = RadioGroup(this)
            sizeGroup.orientation = RadioGroup.HORIZONTAL
            listOf("کوچک" to 2001, "متوسط" to 2002, "بزرگ" to 2003).forEach { (l, id) ->
                sizeGroup.addView(RadioButton(this).apply { this.id = id; text = l })
            }
            sizeGroup.check(when (prefs.getString("keyboard_size", "MEDIUM")) { "SMALL" -> 2001; "LARGE" -> 2003; else -> 2002 })
            card.addView(sizeGroup)
        })

        // ================= رنگ‌ها =================
        root.addView(sectionCard("🎨 رنگ‌ها", "#EF5350") { card ->
            card.addView(hint("کد رنگ به‌صورت #RRGGBB وارد کن"))
            keyboardBgColorInput = colorRow(card, "رنگ پس‌زمینه کل کیبورد", "#E6E8EB", "keyboard_bg_color")
            keyBgColorInput = colorRow(card, "رنگ خود دکمه‌ها (حالت عادی)", "#FFFFFF", "key_bg_color")
            keyPressColorInput = colorRow(card, "رنگ دکمه در حالت فشرده", "#D0D0D0", "key_press_color")
            textColorInput = colorRow(card, "رنگ نوشته‌های کیبورد", "#1F1F1F", "key_text_color")
        })

        // ================= تکسچرها =================
        root.addView(sectionCard("🖼 تصاویر (تکسچر)", "#26C6DA") { card ->
            card.addView(hint("هر تصویر به‌صورت خودکار و بدون کش‌آمدن، دقیقاً روی اندازه‌ی محل خودش برش می‌خورد."))

            card.addView(subHint("۱) تصویر پس‌زمینه کل کیبورد"))
            card.addView(imageButton("انتخاب تصویر پس‌زمینه") { pickImage(PICK_KEYBOARD_BG) })

            card.addView(subHint("۲) تصویر بدنه دکمه‌ها (حالت عادی)"))
            card.addView(imageButton("انتخاب تصویر دکمه‌ها") { pickImage(PICK_KEY_IDLE) })

            card.addView(subHint("۳) تصویر حالت کلیک (فشرده)"))
            card.addView(imageButton("انتخاب تصویر کلیک") { pickImage(PICK_KEY_CLICK) })

            card.addView(subHint("سبک کلیک فعال"))
            clickStyleGroup = RadioGroup(this)
            clickStyleGroup.orientation = RadioGroup.HORIZONTAL
            clickStyleGroup.addView(RadioButton(this).apply { id = 4001; text = "بدون تصویر (رنگ ساده)" })
            clickStyleGroup.addView(RadioButton(this).apply { id = 4002; text = "با تصویر انتخابی" })
            clickStyleGroup.check(if (prefs.getString("click_style", "CLICK1") == "CLICK2") 4002 else 4001)
            card.addView(clickStyleGroup)
        })

        // ================= ادیت متن دکمه‌ها =================
        root.addView(sectionCard("✏ ادیت متن دکمه‌های ویژه", "#8D6E63") { card ->
            card.addView(hint("این تغییرات فقط متن روی دکمه‌ها رو عوض می‌کنه، تکسچر/رنگشون دست‌نخورده می‌مونه."))
            labelMacroInput = labelRow(card, "دکمه تایپ خودکار", "label_macro", "متن‌ها")
            labelResetInput = labelRow(card, "دکمه ریست", "label_reset", "ریست")
            labelSpaceInput = labelRow(card, "دکمه فاصله", "label_space", "Salar @Ditayl")
            labelPeriodInput = labelRow(card, "دکمه نقطه", "label_period", ".")
        })

        // دکمه‌ها
        val saveButton = Button(this)
        saveButton.text = "💾 ذخیره همه تنظیمات"
        saveButton.setPadding(0, 30, 0, 12)
        saveButton.setOnClickListener { saveAll() }
        root.addView(saveButton)

        val openSettingsButton = Button(this)
        openSettingsButton.text = "فعال‌سازی کیبورد در تنظیمات گوشی"
        openSettingsButton.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        root.addView(openSettingsButton)

        val footer = TextView(this)
        footer.text = "— Salar @Ditayl —"
        footer.setPadding(0, 36, 0, 0)
        footer.gravity = Gravity.CENTER
        footer.setTextColor(Color.GRAY)
        footer.typeface = customTypeface
        root.addView(footer)

        keyboardTextureUri = prefs.getString("keyboard_texture_uri", null)
        keyIdleTextureUri = prefs.getString("key_idle_texture_uri", null)
        keyClickTextureUri = prefs.getString("key_click_texture_uri", null)

        setContentView(scroll)
    }

    // ---------- کمکی‌های UI ----------

    private fun sectionCard(title: String, colorHex: String, build: (LinearLayout) -> Unit): LinearLayout {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        val outerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        outerParams.setMargins(0, 0, 0, 24)
        outer.layoutParams = outerParams

        val header = TextView(this)
        header.text = title
        header.textSize = 15f
        header.setTextColor(Color.WHITE)
        header.typeface = Typeface.create(customTypeface, Typeface.BOLD)
        header.setPadding(24, 18, 24, 18)
        val headerBg = GradientDrawable()
        headerBg.setColor(Color.parseColor(colorHex))
        headerBg.cornerRadii = floatArrayOf(18f, 18f, 18f, 18f, 0f, 0f, 0f, 0f)
        header.background = headerBg
        outer.addView(header)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(24, 20, 24, 24)
        val bodyBg = GradientDrawable()
        bodyBg.setColor(Color.WHITE)
        bodyBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 18f, 18f, 18f, 18f)
        body.background = bodyBg
        build(body)
        outer.addView(body)

        return outer
    }

    private fun hint(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Color.GRAY)
        t.setPadding(0, 0, 0, 12)
        t.typeface = customTypeface
        return t
    }

    private fun subHint(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTypeface(customTypeface, Typeface.BOLD)
        t.setPadding(0, 16, 0, 6)
        return t
    }

    private fun bullet(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setPadding(0, 4, 0, 4)
        t.typeface = customTypeface
        return t
    }

    private fun imageButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun colorRow(parent: LinearLayout, label: String, default: String, key: String): EditText {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 8, 0, 8)
        val lbl = TextView(this)
        lbl.text = label
        lbl.textSize = 13f
        lbl.typeface = customTypeface
        lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val input = EditText(this)
        input.setText(prefs.getString(key + "_hex", default))
        input.layoutParams = LinearLayout.LayoutParams(260, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(lbl)
        row.addView(input)
        parent.addView(row)
        return input
    }

    private fun labelRow(parent: LinearLayout, label: String, key: String, default: String): EditText {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 8, 0, 8)
        val lbl = TextView(this)
        lbl.text = label
        lbl.textSize = 13f
        lbl.typeface = customTypeface
        lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val input = EditText(this)
        input.setText(prefs.getString(key, default))
        input.layoutParams = LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(lbl)
        row.addView(input)
        parent.addView(row)
        return input
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    // ---------- تصاویر ----------

    private fun pickImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        when (requestCode) {
            PICK_KEYBOARD_BG -> keyboardTextureUri = uri.toString()
            PICK_KEY_IDLE -> keyIdleTextureUri = uri.toString()
            PICK_KEY_CLICK -> keyClickTextureUri = uri.toString()
        }
        Toast.makeText(this, "تصویر انتخاب شد — دکمه ذخیره را بزن", Toast.LENGTH_SHORT).show()
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try { Color.parseColor(hex.trim()) } catch (_: Exception) { fallback }
    }

    private fun saveAll() {
        val mode = if (modeGroup.checkedRadioButtonId == 1002) "CHAR" else "FULL"
        val size = when (sizeGroup.checkedRadioButtonId) { 2001 -> "SMALL"; 2003 -> "LARGE"; else -> "MEDIUM" }
        val layoutMode = if (layoutGroup.checkedRadioButtonId == 3001) "SAMSUNG" else "CUSTOM"
        val clickStyle = if (clickStyleGroup.checkedRadioButtonId == 4002) "CLICK2" else "CLICK1"

        val keyboardBgHex = keyboardBgColorInput.text.toString()
        val keyBgHex = keyBgColorInput.text.toString()
        val keyPressHex = keyPressColorInput.text.toString()
        val textHex = textColorInput.text.toString()

        prefs.edit()
            .putString("macro_items", editText.text.toString())
            .putString("auto_mode", mode)
            .putInt("auto_speed_ms", speedSeekBar.progress.coerceAtLeast(10))
            .putInt("enter_delay_ms", enterDelaySeekBar.progress)
            .putString("keyboard_size", size)
            .putString("layout_mode", layoutMode)
            .putString("click_style", clickStyle)
            .putString("keyboard_bg_color_hex", keyboardBgHex)
            .putString("key_bg_color_hex", keyBgHex)
            .putString("key_press_color_hex", keyPressHex)
            .putString("key_text_color_hex", textHex)
            .putInt("keyboard_bg_color", parseColorSafe(keyboardBgHex, Color.parseColor("#E6E8EB")))
            .putInt("key_bg_color", parseColorSafe(keyBgHex, Color.WHITE))
            .putInt("key_press_color", parseColorSafe(keyPressHex, Color.parseColor("#D0D0D0")))
            .putInt("key_text_color", parseColorSafe(textHex, Color.parseColor("#1F1F1F")))
            .putString("keyboard_texture_uri", keyboardTextureUri)
            .putString("key_idle_texture_uri", if (clickStyle == "CLICK2") keyIdleTextureUri else null)
            .putString("key_click_texture_uri", if (clickStyle == "CLICK2") keyClickTextureUri else null)
            .putString("label_macro", labelMacroInput.text.toString())
            .putString("label_reset", labelResetInput.text.toString())
            .putString("label_space", labelSpaceInput.text.toString())
            .putString("label_period", labelPeriodInput.text.toString())
            .putInt("macro_line_index", 0)
            .apply()

        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun updateSpeedLabel(ms: Int) {
        speedLabel.text = "سرعت: ${ms.coerceAtLeast(10)} میلی‌ثانیه بین هر حرف"
    }

    private fun updateEnterDelayLabel(ms: Int) {
        enterDelayLabel.text = "مکث بین آیتم‌ها: $ms میلی‌ثانیه"
    }
}
