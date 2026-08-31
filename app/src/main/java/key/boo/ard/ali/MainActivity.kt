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
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private val tabButtons = mutableListOf<Button>()

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
    private var previewView: GKeyboardView? = null

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
        root.orientation = LinearLayout.HORIZONTAL
        root.setBackgroundColor(Color.parseColor("#F0F1F5"))

        val sideMenu = ScrollView(this)
        sideMenu.layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.MATCH_PARENT)
        sideMenu.setBackgroundColor(Color.parseColor("#1C1C1E"))
        val sideMenuInner = LinearLayout(this)
        sideMenuInner.orientation = LinearLayout.VERTICAL
        sideMenuInner.setPadding(0, 60, 0, 20)
        sideMenu.addView(sideMenuInner)

        val brandLabel = TextView(this)
        brandLabel.text = "⌨\nSalar\n@Ditayl"
        brandLabel.setTextColor(Color.parseColor("#0A84FF"))
        brandLabel.setTypeface(null, Typeface.BOLD)
        brandLabel.textSize = 14f
        brandLabel.gravity = Gravity.CENTER
        brandLabel.setPadding(8, 0, 8, 40)
        sideMenuInner.addView(brandLabel)

        val tabTitles = listOf("📱 دستگاه", "⌨ چیدمان", "🎨 تم", "⚡ اتو", "✏ متن‌ها", "🚀 بهینه‌سازی")
        tabTitles.forEachIndexed { index, title ->
            val b = Button(this)
            b.text = title
            b.textSize = 12f
            b.setTextColor(Color.WHITE)
            b.background = null
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setPadding(24, 28, 8, 28)
            b.setOnClickListener { showTab(index) }
            sideMenuInner.addView(b)
            tabButtons.add(b)
        }
        root.addView(sideMenu)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        contentContainer = LinearLayout(this)
        contentContainer.orientation = LinearLayout.VERTICAL
        contentContainer.setPadding(24, 24, 24, 100)
        scroll.addView(contentContainer)
        mainCol.addView(scroll)
        root.addView(mainCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        setContentView(root)
        showTab(0)
    }

    private fun showTab(index: Int) {
        contentContainer.removeAllViews()
        tabButtons.forEachIndexed { i, b -> b.alpha = if (i == index) 1f else 0.5f }
        when (index) {
            0 -> buildDeviceInfoTab()
            1 -> buildLayoutTab()
            2 -> buildThemeTab()
            3 -> buildAutoTab()
            4 -> buildLabelsTab()
            5 -> buildOptimizeTab()
        }
    }

    private fun buildDeviceInfoTab() {
        contentContainer.addView(card("📱 اطلاعات دستگاه", "#0A84FF") { card ->
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

    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scaleLabel: TextView

    private fun buildLayoutTab() {
        contentContainer.addView(card("⌨ انتخاب چیدمان", "#AB47BC") { card ->
            val group = RadioGroup(this)
            layoutIds.forEachIndexed { i, id -> group.addView(RadioButton(this).apply { this.id = 9000 + i; text = layoutNames[id] }) }
            val currentIndex = layoutIds.indexOf(prefs.getString("layout_mode", "CUSTOM")).coerceAtLeast(0)
            group.check(9000 + currentIndex)
            group.setOnCheckedChangeListener { _, checkedId -> prefs.edit().putString("layout_mode", layoutIds[checkedId - 9000]).apply() }
            card.addView(group)
        })
        contentContainer.addView(card("📏 اندازه کیبورد", "#34A853") { card ->
            scaleLabel = TextView(this)
            scaleSeekBar = SeekBar(this)
            scaleSeekBar.max = 60
            val current = prefs.getInt("keyboard_scale", 100)
            scaleSeekBar.progress = current - 70
            updateScaleLabel(current)
            scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 70
                    updateScaleLabel(value)
                    prefs.edit().putInt("keyboard_scale", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(scaleLabel); card.addView(scaleSeekBar)
            card.addView(hint("این فقط ارتفاع کلیدها رو کم/زیاد می‌کنه (کوچیک‌تر/بزرگ‌تر)؛ عرض کیبورد همیشه دقیقاً هم‌عرض صفحه می‌مونه."))
        })
    }

    private fun updateScaleLabel(percent: Int) { scaleLabel.text = "اندازه فعلی: $percent٪" }

    private fun buildThemeTab() {
        val currentLayout = prefs.getString("layout_mode", "CUSTOM") ?: "CUSTOM"
        val prefix = "theme_${currentLayout}_"

        contentContainer.addView(card("👁 پیش‌نمایش زنده — ${layoutNames[currentLayout]}", "#0A84FF") { card ->
            val preview = GKeyboardView(this, null)
            preview.keyboard = cachedKeyboard(currentLayout)
            preview.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            previewView = preview
            card.addView(preview)
            refreshPreview(prefix)
        })

        contentContainer.addView(card("🖼 تم‌های آماده", "#FF7043") { card ->
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
                    showTab(2)
                }
                row.addView(swatch)
            }
            card.addView(HorizontalScrollView(this).apply { addView(row) })
        })

        contentContainer.addView(card("🎨 رنگ‌های دستی", "#EF5350") { card ->
            val bgInput = colorInput(card, "پس‌زمینه کیبورد", prefix + "bg_color_hex", "#E6E8EB")
            val keyInput = colorInput(card, "دکمه‌ها", prefix + "key_color_hex", "#FFFFFF")
            val pressInput = colorInput(card, "حالت فشرده", prefix + "press_color_hex", "#D0D0D0")
            val textInput = colorInput(card, "نوشته‌ها", prefix + "text_color_hex", "#1F1F1F")
            card.addView(subHint("تصاویر (اختیاری)"))
            card.addView(imageBtn("تصویر پس‌زمینه") { pickImageFor(prefix + "bg_texture") })
            card.addView(imageBtn("تصویر دکمه‌ها") { pickImageFor(prefix + "key_idle_texture") })
            card.addView(imageBtn("تصویر حالت کلیک") { pickImageFor(prefix + "key_click_texture") })
            val saveBtn = Button(this)
            saveBtn.text = "ذخیره و پیش‌نمایش"
            saveBtn.setOnClickListener {
                fun parse(hex: String, fallback: Int) = try { Color.parseColor(hex.trim()) } catch (_: Exception) { fallback }
                prefs.edit()
                    .putString(prefix + "bg_color_hex", bgInput.text.toString())
                    .putString(prefix + "key_color_hex", keyInput.text.toString())
                    .putString(prefix + "press_color_hex", pressInput.text.toString())
                    .putString(prefix + "text_color_hex", textInput.text.toString())
                    .putInt(prefix + "bg_color", parse(bgInput.text.toString(), Color.parseColor("#E6E8EB")))
                    .putInt(prefix + "key_color", parse(keyInput.text.toString(), Color.WHITE))
                    .putInt(prefix + "press_color", parse(pressInput.text.toString(), Color.parseColor("#D0D0D0")))
                    .putInt(prefix + "text_color", parse(textInput.text.toString(), Color.parseColor("#1F1F1F")))
                    .apply()
                refreshPreview(prefix)
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            card.addView(saveBtn)
        })
    }

    private fun refreshPreview(prefix: String) {
        val preview = previewView ?: return
        val bgColor = prefs.getInt(prefix + "bg_color", Color.parseColor("#E6E8EB"))
        val keyColor = prefs.getInt(prefix + "key_color", Color.WHITE)
        val pressColor = prefs.getInt(prefix + "press_color", Color.parseColor("#D0D0D0"))
        val textColor = prefs.getInt(prefix + "text_color", Color.parseColor("#1F1F1F"))
        preview.setStyle(null, bgColor, null, keyColor, null, pressColor, textColor, 20f * resources.displayMetrics.scaledDensity, emptyMap())
    }

    private fun buildAutoTab() {
        contentContainer.addView(card("⚡ روشن/خاموش تایپ خودکار", "#FBBC05") { card ->
            val masterSwitch = Switch(this)
            masterSwitch.text = "تایپ خودکار فعال"
            masterSwitch.isChecked = prefs.getBoolean("auto_master_enabled", true)
            masterSwitch.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_master_enabled", checked).apply() }
            card.addView(masterSwitch)
        })

        contentContainer.addView(card("📝 متن‌های آماده", "#34A853") { card ->
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

        contentContainer.addView(card("⚙ نحوه‌ی اجرا", "#26C6DA") { card ->
            val modeGroup = RadioGroup(this)
            modeGroup.orientation = RadioGroup.HORIZONTAL
            modeGroup.addView(RadioButton(this).apply { id = 1001; text = "کامل (پشت‌سرهم)" })
            modeGroup.addView(RadioButton(this).apply { id = 1002; text = "حرف‌به‌حرف (تک آیتم)" })
            modeGroup.check(if (prefs.getString("auto_mode", "FULL") == "CHAR") 1002 else 1001)
            modeGroup.setOnCheckedChangeListener { _, id -> prefs.edit().putString("auto_mode", if (id == 1002) "CHAR" else "FULL").apply() }
            card.addView(modeGroup)

            card.addView(subHint("سرعت تایپ هر حرف"))
            val speedLabel = TextView(this)
            val speedBar = SeekBar(this); speedBar.max = 300
            val speed = prefs.getInt("auto_speed_ms", 45); speedBar.progress = speed
            speedLabel.text = "$speed میلی‌ثانیه"
            speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress.coerceAtLeast(10)
                    speedLabel.text = "$v میلی‌ثانیه"
                    prefs.edit().putInt("auto_speed_ms", v).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(speedLabel); card.addView(speedBar)

            card.addView(subHint("مکث بین آیتم‌ها (پیشنهاد: حداقل ۳۰۰ برای جلوگیری از خطای ارسال تلگرام)"))
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

    private fun buildLabelsTab() {
        layoutIds.forEach { layoutId ->
            contentContainer.addView(card("✏ متن‌های ${layoutNames[layoutId]}", "#8D6E63") { card ->
                val kb = cachedKeyboard(layoutId)
                val seenCodes = mutableSetOf<Int>()

                kb.keys.forEach { key ->
                    val code = key.codes.getOrNull(0) ?: return@forEach
                    if (!seenCodes.add(code)) return@forEach

                    val originalLabel = key.label?.toString() ?: "(بدون متن)"
                    val enableKey = "label_enabled_${layoutId}_$code"
                    val textKey = "label_${layoutId}_$code"

                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, 6, 0, 6)

                    val toggle = Switch(this)
                    toggle.isChecked = prefs.getBoolean(enableKey, false)

                    val nameLabel = TextView(this)
                    nameLabel.text = "«$originalLabel»"
                    nameLabel.layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)

                    val input = EditText(this)
                    input.setText(prefs.getString(textKey, originalLabel))
                    input.isEnabled = toggle.isChecked
                    input.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val saveBtn = Button(this)
                    saveBtn.text = "ذخیره"
                    saveBtn.textSize = 10f
                    saveBtn.setOnClickListener {
                        prefs.edit().putString(textKey, input.text.toString()).putBoolean(enableKey, toggle.isChecked).apply()
                        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                    }

                    toggle.setOnCheckedChangeListener { _, checked ->
                        input.isEnabled = checked
                        prefs.edit().putBoolean(enableKey, checked).apply()
                    }

                    row.addView(toggle); row.addView(nameLabel); row.addView(input); row.addView(saveBtn)
                    card.addView(row)
                }
            })
        }
    }

    private fun buildOptimizeTab() {
        contentContainer.addView(card("🚀 روان‌سازی گوشی", "#0A84FF") { card ->
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
        contentContainer.addView(openSettingsBtn)
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

    private fun hint(text: String): TextView {
        val t = TextView(this); t.text = text; t.textSize = 11f; t.setTextColor(Color.GRAY); t.setPadding(0, 6, 0, 6); return t
    }

    private fun subHint(text: String): TextView {
        val t = TextView(this); t.text = text; t.textSize = 12f; t.setTypeface(null, Typeface.BOLD); t.setPadding(0, 14, 0, 4); return t
    }

    private fun colorInput(parent: LinearLayout, label: String, key: String, default: String): EditText {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val l = TextView(this); l.text = label; l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val input = EditText(this); input.setText(prefs.getString(key, default))
        input.layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(l); row.addView(input); parent.addView(row); return input
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
        Toast.makeText(this, "تصویر ذخیره شد", Toast.LENGTH_SHORT).show()
        pendingImageTarget = null
    }
}
