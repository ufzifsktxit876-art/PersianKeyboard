package key.boo.ard.ali

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
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

    private var keyboardTextureUri: String? = null
    private var clickTextureUri: String? = null

    private val PICK_KEYBOARD_IMAGE = 1001
    private val PICK_CLICK_IMAGE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 40, 40, 60)
        scroll.addView(root)

        fun sectionTitle(text: String): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = 16f
            t.setPadding(0, 32, 0, 8)
            t.setTypeface(null, android.graphics.Typeface.BOLD)
            return t
        }

        val title = TextView(this)
        title.text = "کیبورد فارسی — Salar @Ditayl"
        title.textSize = 19f
        title.setPadding(0, 0, 0, 16)
        root.addView(title)

        // متن‌های آماده
        root.addView(sectionTitle("متن‌های آماده (هر خط یک آیتم)"))
        editText = EditText(this)
        editText.setText(prefs.getString("macro_items", "1\n2\n3\n4\n5"))
        editText.setLines(6)
        editText.gravity = Gravity.TOP
        root.addView(editText)

        // حالت اتو
        root.addView(sectionTitle("حالت تایپ خودکار"))
        val autoExplain = TextView(this)
        autoExplain.text = "کامل: با یک بار زدن، کل لیست پشت‌سرهم و خودکار تایپ می‌شود. حرف به حرف: هر بار زدن، فقط یک آیتم را تایپ می‌کند."
        autoExplain.textSize = 12f
        autoExplain.setTextColor(Color.GRAY)
        root.addView(autoExplain)
        modeGroup = RadioGroup(this)
        modeGroup.orientation = RadioGroup.HORIZONTAL
        val fullRadio = RadioButton(this).apply { id = 1001; text = "کامل" }
        val charRadio = RadioButton(this).apply { id = 1002; text = "حرف به حرف" }
        modeGroup.addView(fullRadio)
        modeGroup.addView(charRadio)
        modeGroup.check(if (prefs.getString("auto_mode", "FULL") == "CHAR") 1002 else 1001)
        root.addView(modeGroup)

        // سرعت تایپ
        root.addView(sectionTitle("سرعت تایپ هر حرف"))
        speedLabel = TextView(this)
        speedSeekBar = SeekBar(this)
        speedSeekBar.max = 300
        val currentSpeed = prefs.getInt("auto_speed_ms", 45)
        speedSeekBar.progress = currentSpeed
        updateSpeedLabel(currentSpeed)
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateSpeedLabel(progress.coerceAtLeast(10))
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        root.addView(speedLabel)
        root.addView(speedSeekBar)

        // تاخیر بین پیام‌ها (فقط حالت کامل)
        root.addView(sectionTitle("مکث بین هر آیتم و آیتم بعدی (فقط حالت کامل)"))
        enterDelayLabel = TextView(this)
        enterDelaySeekBar = SeekBar(this)
        enterDelaySeekBar.max = 2000
        val currentEnterDelay = prefs.getInt("enter_delay_ms", 120)
        enterDelaySeekBar.progress = currentEnterDelay
        updateEnterDelayLabel(currentEnterDelay)
        enterDelaySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateEnterDelayLabel(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        root.addView(enterDelayLabel)
        root.addView(enterDelaySeekBar)

        // اندازه کیبورد
        root.addView(sectionTitle("اندازه کیبورد"))
        sizeGroup = RadioGroup(this)
        sizeGroup.orientation = RadioGroup.HORIZONTAL
        listOf("کوچک" to 2001, "متوسط" to 2002, "بزرگ" to 2003).forEach { (label, rid) ->
            sizeGroup.addView(RadioButton(this).apply { id = rid; text = label })
        }
        sizeGroup.check(when (prefs.getString("keyboard_size", "MEDIUM")) { "SMALL" -> 2001; "LARGE" -> 2003; else -> 2002 })
        root.addView(sizeGroup)

        // چیدمان
        root.addView(sectionTitle("چیدمان کیبورد"))
        layoutGroup = RadioGroup(this)
        layoutGroup.orientation = RadioGroup.HORIZONTAL
        val samsungRadio = RadioButton(this).apply { id = 3001; text = "سبک سامسونگ" }
        val customRadio = RadioButton(this).apply { id = 3002; text = "اختصاصی (قابل ادیت)" }
        layoutGroup.addView(samsungRadio)
        layoutGroup.addView(customRadio)
        layoutGroup.check(if (prefs.getString("layout_mode", "CUSTOM") == "SAMSUNG") 3001 else 3002)
        root.addView(layoutGroup)

        // سبک کلیک
        root.addView(sectionTitle("سبک کلیک"))
        val clickExplain = TextView(this)
        clickExplain.text = "کلیک یک: ساده، بدون تکسچر اضافه. کلیک دو: از رنگ/تصویری که پایین انتخاب می‌کنی استفاده می‌کند."
        clickExplain.textSize = 12f
        clickExplain.setTextColor(Color.GRAY)
        root.addView(clickExplain)
        clickStyleGroup = RadioGroup(this)
        clickStyleGroup.orientation = RadioGroup.HORIZONTAL
        val click1 = RadioButton(this).apply { id = 4001; text = "کلیک یک" }
        val click2 = RadioButton(this).apply { id = 4002; text = "کلیک دو" }
        clickStyleGroup.addView(click1)
        clickStyleGroup.addView(click2)
        clickStyleGroup.check(if (prefs.getString("click_style", "CLICK1") == "CLICK2") 4002 else 4001)
        root.addView(clickStyleGroup)

        // رنگ‌ها
        root.addView(sectionTitle("رنگ‌ها (کد رنگ به‌صورت #RRGGBB)"))

        fun colorRow(label: String, defaultHex: String, key: String): EditText {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val lbl = TextView(this)
            lbl.text = label
            lbl.textSize = 13f
            lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val input = EditText(this)
            input.setText(prefs.getString(key + "_hex", defaultHex))
            input.layoutParams = LinearLayout.LayoutParams(280, LinearLayout.LayoutParams.WRAP_CONTENT)
            row.addView(lbl)
            row.addView(input)
            root.addView(row)
            return input
        }

        keyboardBgColorInput = colorRow("رنگ پس‌زمینه کل کیبورد", "#E6E8EB", "keyboard_bg_color")
        keyBgColorInput = colorRow("رنگ خود دکمه‌ها", "#FFFFFF", "key_bg_color")
        keyPressColorInput = colorRow("رنگ حالت فشرده (کلیک)", "#D0D0D0", "key_press_color")
        textColorInput = colorRow("رنگ نوشته‌های کیبورد", "#1F1F1F", "key_text_color")

        // تصاویر
        root.addView(sectionTitle("تصویر پس‌زمینه (اختیاری)"))
        keyboardTextureUri = prefs.getString("keyboard_texture_uri", null)
        clickTextureUri = prefs.getString("click_texture_uri", null)

        val pickKeyboardImgBtn = Button(this)
        pickKeyboardImgBtn.text = "انتخاب تصویر پس‌زمینه کیبورد"
        pickKeyboardImgBtn.setOnClickListener { pickImage(PICK_KEYBOARD_IMAGE) }
        root.addView(pickKeyboardImgBtn)

        val pickClickImgBtn = Button(this)
        pickClickImgBtn.text = "انتخاب تصویر برای حالت کلیک (کلیک دو)"
        pickClickImgBtn.setOnClickListener { pickImage(PICK_CLICK_IMAGE) }
        root.addView(pickClickImgBtn)

        val imgNote = TextView(this)
        imgNote.text = "تصویر انتخابی به‌صورت خودکار متناسب با اندازه کیبورد کراپ/کشیده می‌شود."
        imgNote.textSize = 11f
        imgNote.setTextColor(Color.GRAY)
        root.addView(imgNote)

        // ذخیره
        val saveButton = Button(this)
        saveButton.text = "ذخیره همه تنظیمات"
        saveButton.setPadding(0, 40, 0, 0)
        saveButton.setOnClickListener { saveAll() }
        root.addView(saveButton)

        val openSettingsButton = Button(this)
        openSettingsButton.text = "فعال‌سازی کیبورد در تنظیمات گوشی"
        openSettingsButton.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        root.addView(openSettingsButton)

        val footer = TextView(this)
        footer.text = "Salar @Ditayl"
        footer.setPadding(0, 40, 0, 0)
        footer.gravity = Gravity.CENTER
        footer.setTextColor(Color.GRAY)
        root.addView(footer)

        setContentView(scroll)
    }

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
            PICK_KEYBOARD_IMAGE -> keyboardTextureUri = uri.toString()
            PICK_CLICK_IMAGE -> clickTextureUri = uri.toString()
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
            .putString("click_texture_uri", clickTextureUri)
            .putInt("macro_line_index", 0)
            .apply()

        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun updateSpeedLabel(ms: Int) {
        speedLabel.text = "سرعت تایپ: ${ms.coerceAtLeast(10)} میلی‌ثانیه بین هر حرف"
    }

    private fun updateEnterDelayLabel(ms: Int) {
        enterDelayLabel.text = "مکث بین آیتم‌ها: $ms میلی‌ثانیه"
    }
}
