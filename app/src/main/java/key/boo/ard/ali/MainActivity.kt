package key.boo.ard.ali

import android.app.ActivityManager
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.Keyboard
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var pageContainer: LinearLayout
    private lateinit var previewView: GKeyboardView

    private val layoutIds = listOf("CUSTOM", "SAMSUNG", "DOLINE", "PCBOARD")
    private val layoutNames = mapOf(
        "CUSTOM" to "اختصاصی", "SAMSUNG" to "سبک سامسونگ",
        "DOLINE" to "طرح دو لاین", "PCBOARD" to "پی‌سی‌بورد"
    )
    private val layoutResIds = mapOf(
        "CUSTOM" to R.xml.keyboard_persian_letters_medium,
        "SAMSUNG" to R.xml.keyboard_samsung_medium,
        "DOLINE" to R.xml.keyboard_doline,
        "PCBOARD" to R.xml.keyboard_pcboard
    )
    private val keyboardCache = HashMap<String, Keyboard>()
    private fun cachedKeyboard(layoutId: String): Keyboard =
        keyboardCache.getOrPut(layoutId) { Keyboard(this, layoutResIds[layoutId]!!) }

    private var pendingImageTarget: Pair<String, Int>? = null
    private var imageResultCounter = 5000
    private var selectedLabelsLayout = "CUSTOM"

    private val presetThemes = listOf(
        Triple("پیش‌فرض", "#E6E8EB", "#FFFFFF") to "#1F1F1F",
        Triple("تیره", "#1C1C1E", "#3A3A3C") to "#F2F2F2",
        Triple("آبی شب", "#0D1B2A", "#1B263B") to "#E0E1DD",
        Triple("صورتی", "#2B1B2E", "#FF2D78") to "#FFFFFF",
        Triple("سبز", "#1B2E1F", "#2E7D32") to "#E8F5E9",
        Triple("نارنجی", "#2E1B0F", "#FF7A00") to "#FFF3E0"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#F0F1F5"))

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(Color.parseColor("#1E88E5"))
        topBar.setPadding(24, 36, 24, 24)
        topBar.gravity = Gravity.CENTER_VERTICAL
        val menuIcon = TextView(this); menuIcon.text = "☰"; menuIcon.textSize = 20f; menuIcon.setTextColor(Color.WHITE)
        val topTitle = TextView(this); topTitle.text = "کیبورد فارسی — Salar @Ditayl"; topTitle.textSize = 15f
        topTitle.setTextColor(Color.WHITE); topTitle.setTypeface(null, Typeface.BOLD)
        topTitle.setPadding(20, 0, 0, 0)
        topBar.addView(menuIcon); topBar.addView(topTitle)
        root.addView(topBar)

        val scroll = ScrollView(this)
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding(20, 20, 20, 100)
        scroll.addView(outer)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val previewCard = LinearLayout(this)
        previewCard.orientation = LinearLayout.VERTICAL
        previewCard.setPadding(4, 4, 4, 4)
        previewCard.background = GradientDrawable().apply { setColor(Color.parseColor("#111111")); cornerRadius = 20f }
        previewView = GKeyboardView(this, null)
        refreshPreview()
        previewCard.addView(previewView)
        outer.addView(previewCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) })

        val tileDefs = listOf(
            Triple("📱", "دستگاه", "#42A5F5"),
            Triple("⌨", "چیدمان", "#AB47BC"),
            Triple("🎨", "تم", "#EF5350"),
            Triple("⚡", "اتو", "#FBC02D"),
            Triple("✏", "متن‌ها", "#8D6E63"),
            Triple("🚀", "بهینه‌سازی", "#26A69A")
        )
        tileDefs.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 12) }
            rowItems.forEachIndexed { idx, (icon, label, colorHex) ->
                val tile = LinearLayout(this)
                tile.orientation = LinearLayout.VERTICAL
                tile.gravity = Gravity.CENTER
                tile.setPadding(12, 32, 12, 32)
                tile.background = GradientDrawable().apply { setColor(Color.parseColor(colorHex)); cornerRadius = 18f }
                val iconView = TextView(this); iconView.text = icon; iconView.textSize = 26f; iconView.gravity = Gravity.CENTER
                val labelView = TextView(this); labelView.text = label; labelView.textSize = 12f
                labelView.setTextColor(Color.WHITE); labelView.gravity = Gravity.CENTER; labelView.setPadding(0, 8, 0, 0)
                tile.addView(iconView); tile.addView(labelView)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(if (idx == 0) 0 else 8, 0, 8, 0)
                tile.layoutParams = lp
                tile.setOnClickListener { showPage(label) }
                row.addView(tile)
            }
            outer.addView(row)
        }

        pageContainer = LinearLayout(this)
        pageContainer.orientation = LinearLayout.VERTICAL
        pageContainer.setPadding(0, 20, 0, 0)
        outer.addView(pageContainer)

        setContentView(root)
        showPage("چیدمان")
    }

    private fun showPage(name: String) {
        pageContainer.removeAllViews()
        when (name) {
            "دستگاه" -> buildDeviceInfoPage()
            "چیدمان" -> buildLayoutPage()
            "تم" -> buildThemePage()
            "اتو" -> buildAutoPage()
            "متن‌ها" -> buildLabelsPage()
            "بهینه‌سازی" -> buildOptimizePage()
        }
    }

    private fun currentLayout() = prefs.getString("layout_mode", "CUSTOM") ?: "CUSTOM"

    private fun refreshPreview() {
        val layout = currentLayout()
        val prefix = "theme_${layout}_"
        previewView.keyboard = cachedKeyboard(layout)
        previewView.userScale = prefs.getInt("keyboard_scale", 100) / 100f
        val bgColor = prefs.getInt(prefix + "bg_color", Color.parseColor("#E6E8EB"))
        val keyColor = prefs.getInt(prefix + "key_color", Color.WHITE)
        val pressColor = prefs.getInt(prefix + "press_color", Color.parseColor("#D0D0D0"))
        val textColor = prefs.getInt(prefix + "text_color", Color.parseColor("#1F1F1F"))
        previewView.setStyle(null, bgColor, null, keyColor, null, pressColor, textColor, 20f * resources.displayMetrics.scaledDensity, emptyMap())
        previewView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun buildDeviceInfoPage() {
        pageContainer.addView(card("📱 اطلاعات دستگاه", "#42A5F5") { card ->
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            val cores = Runtime.getRuntime().availableProcessors()
            var tempText = "در دسترس نیست"
            try {
                val intent = registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"))
                val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                if (temp >= 0) tempText = "${temp / 10.0}°C"
            } catch (_: Exception) {}
            card.addView(infoRow("رم کل", "%.1f گیگابایت".format(totalRamGb)))
            card.addView(infoRow("رم آزاد", "%.1f گیگابایت".format(availRamGb)))
            card.addView(infoRow("هسته‌های پردازنده", "$cores"))
            card.addView(infoRow("دما", tempText))
            card.addView(infoRow("اندروید", "API ${android.os.Build.VERSION.SDK_INT}"))
            card.addView(infoRow("مدل", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
        })
    }

    private fun buildLayoutPage() {
        pageContainer.addView(card("⌨ انتخاب چیدمان", "#AB47BC") { card ->
            val group = RadioGroup(this)
            layoutIds.forEachIndexed { i, id -> group.addView(RadioButton(this).apply { this.id = 9000 + i; text = layoutNames[id] }) }
            val currentIndex = layoutIds.indexOf(currentLayout()).coerceAtLeast(0)
            group.check(9000 + currentIndex)
            group.setOnCheckedChangeListener { _, checkedId ->
                prefs.edit().putString("layout_mode", layoutIds[checkedId - 9000]).apply()
                refreshPreview()
            }
            card.addView(group)
        })
        pageContainer.addView(card("📏 اندازه کیبورد", "#34A853") { card ->
            val scaleLabel = TextView(this)
            val scaleSeekBar = SeekBar(this)
            scaleSeekBar.max = 60
            val current = prefs.getInt("keyboard_scale", 100)
            scaleSeekBar.progress = current - 70
            scaleLabel.text = "اندازه فعلی: $current٪"
            scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 70
                    scaleLabel.text = "اندازه فعلی: $value٪"
                    prefs.edit().putInt("keyboard_scale", value).apply()
                    refreshPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(scaleLabel); card.addView(scaleSeekBar)
        })
    }

    private fun buildThemePage() {
        val layout = currentLayout()
        val prefix = "theme_${layout}_"

        pageContainer.addView(card("🖼 تم‌های آماده — ${layoutNames[layout]}", "#FF7043") { card ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            presetThemes.forEach { (info, textHex) ->
                val (name, bgHex, keyHex) = info
                val swatch = Button(this)
                swatch.text = name
                swatch.setBackgroundColor(Color.parseColor(bgHex))
                swatch.setTextColor(Color.parseColor(textHex))
                swatch.setOnClickListener {
                    prefs.edit()
                        .putString(prefix + "bg_color_hex", bgHex).putString(prefix + "key_color_hex", keyHex)
                        .putString(prefix + "press_color_hex", keyHex).putString(prefix + "text_color_hex", textHex)
                        .putInt(prefix + "bg_color", Color.parseColor(bgHex)).putInt(prefix + "key_color", Color.parseColor(keyHex))
                        .putInt(prefix + "press_color", Color.parseColor(keyHex)).putInt(prefix + "text_color", Color.parseColor(textHex))
                        .apply()
                    refreshPreview()
                    showPage("تم")
                }
                row.addView(swatch)
            }
            card.addView(HorizontalScrollView(this).apply { addView(row) })
        })

        pageContainer.addView(card("🎨 رنگ‌های دستی (بکش یا تایپ کن)", "#EF5350") { card ->
            colorInputWithPicker(card, "پس‌زمینه کیبورد", prefix + "bg_color_hex", "#E6E8EB")
            colorInputWithPicker(card, "دکمه‌ها", prefix + "key_color_hex", "#FFFFFF")
            colorInputWithPicker(card, "حالت فشرده", prefix + "press_color_hex", "#D0D0D0")
            colorInputWithPicker(card, "نوشته‌ها", prefix + "text_color_hex", "#1F1F1F")

            card.addView(subHint("تصاویر (اختیاری)"))
            card.addView(imageBtn("تصویر پس‌زمینه") { pickImageFor(prefix + "bg_texture") })
            card.addView(imageBtn("تصویر دکمه‌ها") { pickImageFor(prefix + "key_idle_texture") })
            card.addView(imageBtn("تصویر حالت کلیک") { pickImageFor(prefix + "key_click_texture") })
        })
    }

    /** ردیف رنگ + مربع طیف کشیدنی + کد Hex قابل تایپ — همه با هم زنده هماهنگ و بلافاصله ذخیره/اعمال میشن. */
    private fun colorInputWithPicker(parent: LinearLayout, label: String, hexKey: String, default: String) {
        val intKey = hexKey.removeSuffix("_hex")

        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.setPadding(0, 18, 0, 6)
        val l = TextView(this); l.text = label; l.setTypeface(null, Typeface.BOLD); l.textSize = 13f
        l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val swatch = View(this)
        swatch.layoutParams = LinearLayout.LayoutParams(60, 60)
        val currentHex = prefs.getString(hexKey, default) ?: default
        val currentColor = try { Color.parseColor(currentHex) } catch (_: Exception) { Color.parseColor(default) }
        swatch.background = GradientDrawable().apply { setColor(currentColor); cornerRadius = 10f }

        val hexInput = EditText(this)
        hexInput.setText(currentHex)
        hexInput.layoutParams = LinearLayout.LayoutParams(180, LinearLayout.LayoutParams.WRAP_CONTENT)

        titleRow.addView(l); titleRow.addView(swatch); titleRow.addView(hexInput)
        parent.addView(titleRow)

        val picker = ColorPickerView(this, null)
        picker.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140)
        parent.addView(picker)
        picker.post { picker.setColorExternally(currentColor) }

        fun applyColor(color: Int, updateHexField: Boolean) {
            val hex = String.format("#%06X", 0xFFFFFF and color)
            if (updateHexField) hexInput.setText(hex)
            prefs.edit().putString(hexKey, hex).putInt(intKey, color).apply()
            swatch.background = GradientDrawable().apply { setColor(color); cornerRadius = 10f }
            refreshPreview()
        }

        picker.onColorPicked = { color -> applyColor(color, updateHexField = true) }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                val color = try { Color.parseColor(text) } catch (_: Exception) { null }
                if (color != null) {
                    picker.setColorExternally(color)
                    applyColor(color, updateHexField = false)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun buildAutoPage() {
        pageContainer.addView(card("⚡ روشن/خاموش تایپ خودکار", "#FBC02D") { card ->
            val masterSwitch = Switch(this)
            masterSwitch.text = "تایپ خودکار فعال"
            masterSwitch.isChecked = prefs.getBoolean("auto_master_enabled", true)
            masterSwitch.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_master_enabled", checked).apply() }
            card.addView(masterSwitch)
        })

        pageContainer.addView(card("📝 متن‌های آماده", "#34A853") { card ->
            val editText = EditText(this)
            editText.setText(prefs.getString("macro_items", "1\n2\n3\n4\n5"))
            editText.setLines(6)
            editText.gravity = Gravity.TOP
            card.addView(editText)
            val saveBtn = Button(this)
            saveBtn.text = "ذخیره"
            saveBtn.setOnClickListener {
                prefs.edit().putString("macro_items", editText.text.toString()).putInt("macro_line_index", 0).apply()
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            card.addView(saveBtn)
        })

        pageContainer.addView(card("⚙ نحوه‌ی اجرا و سرعت", "#26C6DA") { card ->
            val modeGroup = RadioGroup(this)
            modeGroup.orientation = RadioGroup.HORIZONTAL
            modeGroup.addView(RadioButton(this).apply { id = 1001; text = "کامل (پشت‌سرهم)" })
            modeGroup.addView(RadioButton(this).apply { id = 1002; text = "حرف‌به‌حرف (تک آیتم)" })
            modeGroup.check(if (prefs.getString("auto_mode", "FULL") == "CHAR") 1002 else 1001)
            modeGroup.setOnCheckedChangeListener { _, id -> prefs.edit().putString("auto_mode", if (id == 1002) "CHAR" else "FULL").apply() }
            card.addView(modeGroup)

            card.addView(subHint("سرعت تایپ هر حرف (زیر ۱۵ = حالت فوق‌سریع، بدون لگ)"))
            val speedLabel = TextView(this)
            val speedBar = SeekBar(this); speedBar.max = 300
            val speed = prefs.getInt("auto_speed_ms", 45); speedBar.progress = speed
            speedLabel.text = if (speed <= 15) "$speed میلی‌ثانیه (فوق‌سریع)" else "$speed میلی‌ثانیه"
            speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speedLabel.text = if (progress <= 15) "$progress میلی‌ثانیه (فوق‌سریع)" else "$progress میلی‌ثانیه"
                    prefs.edit().putInt("auto_speed_ms", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(speedLabel); card.addView(speedBar)

            card.addView(subHint("مکث بین آیتم‌ها"))
            val delayLabel = TextView(this)
            val delayBar = SeekBar(this); delayBar.max = 3000
            val delay = prefs.getInt("enter_delay_ms", 350); delayBar.progress = delay
            delayLabel.text = "$delay میلی‌ثانیه"
            delayBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    delayLabel.text = "$progress میلی‌ثانیه"
                    prefs.edit().putInt("enter_delay_ms", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(delayLabel); card.addView(delayBar)

            card.addView(subHint("تعداد تکرار کل لیست (فقط حالت کامل)"))
            val repeatLabel = TextView(this)
            val repeatBar = SeekBar(this); repeatBar.max = 49
            val repeatVal = prefs.getInt("auto_repeat_count", 1); repeatBar.progress = repeatVal - 1
            repeatLabel.text = "$repeatVal بار"
            repeatBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + 1
                    repeatLabel.text = "$v بار"
                    prefs.edit().putInt("auto_repeat_count", v).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(repeatLabel); card.addView(repeatBar)
        })
    }

    private fun buildLabelsPage() {
        pageContainer.addView(card("✏ متن‌ها — انتخاب چیدمان", "#8D6E63") { card ->
            val group = RadioGroup(this)
            group.orientation = RadioGroup.HORIZONTAL
            layoutIds.forEachIndexed { i, id -> group.addView(RadioButton(this).apply { this.id = 8000 + i; text = layoutNames[id] }) }
            group.check(8000 + layoutIds.indexOf(selectedLabelsLayout).coerceAtLeast(0))
            group.setOnCheckedChangeListener { _, checkedId ->
                selectedLabelsLayout = layoutIds[checkedId - 8000]
                showPage("متن‌ها")
            }
            card.addView(group)
        })

        pageContainer.addView(card("✏ متن‌های ${layoutNames[selectedLabelsLayout]}", "#8D6E63") { card ->
            val kb = cachedKeyboard(selectedLabelsLayout)
            val seenCodes = mutableSetOf<Int>()

            kb.keys.forEach { key ->
                val code = key.codes.getOrNull(0) ?: return@forEach
                if (!seenCodes.add(code)) return@forEach

                val originalLabel = key.label?.toString() ?: "(بدون متن)"
                val enableKey = "label_enabled_${selectedLabelsLayout}_$code"
                val textKey = "label_${selectedLabelsLayout}_$code"

                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, 5, 0, 5)

                val toggle = Switch(this)
                toggle.isChecked = prefs.getBoolean(enableKey, false)

                val nameLabel = TextView(this)
                nameLabel.text = "«$originalLabel»"
                nameLabel.textSize = 12f
                nameLabel.layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)

                val input = EditText(this)
                input.setText(prefs.getString(textKey, originalLabel))
                input.isEnabled = toggle.isChecked
                input.textSize = 12f
                input.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                input.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        prefs.edit().putString(textKey, s.toString()).apply()
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
                toggle.setOnCheckedChangeListener { _, checked ->
                    input.isEnabled = checked
                    prefs.edit().putBoolean(enableKey, checked).apply()
                }

                row.addView(toggle); row.addView(nameLabel); row.addView(input)
                card.addView(row)
            }
        })
    }

    private fun buildOptimizePage() {
        pageContainer.addView(card("🚀 روان‌سازی گوشی", "#26A69A") { card ->
            fun stepRow(title: String, desc: String, intentAction: String) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                row.setPadding(0, 14, 0, 14)
                val t = TextView(this); t.text = title; t.setTypeface(null, Typeface.BOLD)
                val d = TextView(this); d.text = desc; d.textSize = 12f; d.setTextColor(Color.GRAY)
                val btn = Button(this)
                btn.text = "برو به تنظیمات"
                btn.setOnClickListener {
                    try { startActivity(Intent(intentAction)) }
                    catch (_: Exception) {
                        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        catch (_: Exception) { Toast.makeText(this, "این گزینه در دسترس نیست", Toast.LENGTH_SHORT).show() }
                    }
                }
                row.addView(t); row.addView(d); row.addView(btn)
                card.addView(row)
            }
            stepRow("۱. حالت توسعه‌دهنده", "درباره گوشی → شماره ساخت را ۷ بار بزن", Settings.ACTION_DEVICE_INFO_SETTINGS)
            stepRow("۲. خاموش‌کردن انیمیشن‌ها", "مقیاس انیمیشن‌ها را صفر کن", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            stepRow("۳. بستن اپ‌های پرمصرف", "لیست اپ‌ها را باز کن", Settings.ACTION_APPLICATION_SETTINGS)
        })
        val openSettingsBtn = Button(this)
        openSettingsBtn.text = "فعال‌سازی کیبورد در تنظیمات گوشی"
        openSettingsBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        pageContainer.addView(openSettingsBtn)
    }

    private fun card(title: String, colorHex: String, build: (LinearLayout) -> Unit): LinearLayout {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,0,20) }
        val header = TextView(this)
        header.text = title; header.textSize = 14f; header.setTextColor(Color.WHITE)
        header.setTypeface(null, Typeface.BOLD); header.setPadding(20, 16, 20, 16)
        header.background = GradientDrawable().apply { setColor(Color.parseColor(colorHex)); cornerRadii = floatArrayOf(16f,16f,16f,16f,0f,0f,0f,0f) }
        outer.addView(header)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL; body.setPadding(20, 18, 20, 20)
        body.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadii = floatArrayOf(0f,0f,0f,0f,16f,16f,16f,16f) }
        build(body)
        outer.addView(body)
        return outer
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.setPadding(0, 5, 0, 5)
        val l = TextView(this); l.text = label; l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val v = TextView(this); v.text = value; v.setTypeface(null, Typeface.BOLD)
        row.addView(l); row.addView(v); return row
    }

    private fun subHint(text: String): TextView {
        val t = TextView(this); t.text = text; t.textSize = 12f; t.setTypeface(null, Typeface.BOLD); t.setPadding(0, 14, 0, 4); return t
    }

    private fun imageBtn(label: String, onClick: () -> Unit): Button {
        val b = Button(this); b.text = label; b.setOnClickListener { onClick() }; return b
    }

    private fun pickImageFor(prefKey: String) {
        val rc = imageResultCounter++
        pendingImageTarget = prefKey to rc
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.type = "image/*"
        startActivityForResult(intent, rc)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        val target = pendingImageTarget ?: return
        if (target.second != requestCode) return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        prefs.edit().putString(target.first, uri.toString()).apply()
        refreshPreview()
        Toast.makeText(this, "تصویر ذخیره شد", Toast.LENGTH_SHORT).show()
        pendingImageTarget = null
    }
}
