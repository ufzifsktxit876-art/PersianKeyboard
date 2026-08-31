package key.boo.ard.ali

import android.app.ActivityManager
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        "CUSTOM" to "اختصاصی",
        "SAMSUNG" to "سبک سامسونگ",
        "DOLINE" to "طرح دو لاین",
        "PCBOARD" to "پی‌سی‌بورد"
    )

    private var pendingImageTarget: Pair<String, Int>? = null
    private var imageResultCounter = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#F5F5F7"))

        val header = TextView(this)
        header.text = "⌨ Salar @Ditayl"
        header.textSize = 22f
        header.setTypeface(null, Typeface.BOLD)
        header.setTextColor(Color.parseColor("#0A84FF"))
        header.gravity = Gravity.CENTER
        header.setPadding(0, 40, 0, 20)
        root.addView(header)

        val tabsRow = HorizontalScrollView(this)
        val tabsInner = LinearLayout(this)
        tabsInner.orientation = LinearLayout.HORIZONTAL
        tabsRow.addView(tabsInner)
        root.addView(tabsRow)

        val tabTitles = listOf("دستگاه", "چیدمان", "تم", "اتو", "متن‌ها", "بهینه‌سازی")
        tabTitles.forEachIndexed { index, title ->
            val b = Button(this)
            b.text = title
            b.setOnClickListener { showTab(index) }
            tabsInner.addView(b)
            tabButtons.add(b)
        }

        val scroll = ScrollView(this)
        contentContainer = LinearLayout(this)
        contentContainer.orientation = LinearLayout.VERTICAL
        contentContainer.setPadding(28, 20, 28, 100)
        scroll.addView(contentContainer)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

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

    // ---------------- تب ۱: اطلاعات دستگاه ----------------
    private fun buildDeviceInfoTab() {
        contentContainer.addView(card("📱 اطلاعات دستگاه شما", "#0A84FF") { card ->
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
                if (temp >= 0) tempText = "${temp / 10.0}°C (دمای باتری)"
            } catch (_: Exception) {}

            card.addView(infoRow("رم کل دستگاه", "%.1f گیگابایت".format(totalRamGb)))
            card.addView(infoRow("رم آزاد فعلی", "%.1f گیگابایت".format(availRamGb)))
            card.addView(infoRow("تعداد هسته پردازنده", "$cores هسته"))
            card.addView(infoRow("دمای فعلی", tempText))
            card.addView(infoRow("اندروید", "API ${android.os.Build.VERSION.SDK_INT}"))
            card.addView(infoRow("مدل گوشی", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))

            card.addView(hint("این‌ها فقط اطلاعات لحظه‌ای دستگاهت هستن؛ برای این‌که کیبورد همیشه روون بمونه، سراغ تب «بهینه‌سازی» برو."))
        })
    }

    // ---------------- تب ۲: چیدمان + اندازه ----------------
    private lateinit var layoutRadioGroup: RadioGroup
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scaleLabel: TextView

    private fun buildLayoutTab() {
        contentContainer.addView(card("⌨ انتخاب چیدمان", "#AB47BC") { card ->
            layoutRadioGroup = RadioGroup(this)
            layoutIds.forEachIndexed { i, id ->
                val rb = RadioButton(this)
                rb.id = 9000 + i
                rb.text = layoutNames[id]
                layoutRadioGroup.addView(rb)
            }
            val current = prefs.getString("layout_mode", "CUSTOM")
            val currentIndex = layoutIds.indexOf(current).coerceAtLeast(0)
            layoutRadioGroup.check(9000 + currentIndex)
            layoutRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                val idx = checkedId - 9000
                prefs.edit().putString("layout_mode", layoutIds[idx]).apply()
            }
            card.addView(layoutRadioGroup)
        })

        contentContainer.addView(card("📏 اندازه کیبورد (کوچک ↔ بزرگ)", "#34A853") { card ->
            scaleLabel = TextView(this)
            scaleSeekBar = SeekBar(this)
            scaleSeekBar.max = 90
            val current = prefs.getInt("keyboard_scale", 100)
            scaleSeekBar.progress = current - 60
            updateScaleLabel(current)
            scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 60
                    updateScaleLabel(value)
                    prefs.edit().putInt("keyboard_scale", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(scaleLabel)
            card.addView(scaleSeekBar)
            card.addView(hint("این اسلایدر روی هر چهار چیدمان (اختصاصی، سامسونگ، دو لاین، پی‌سی‌بورد) اعمال میشه."))
        })
    }

    private fun updateScaleLabel(percent: Int) {
        scaleLabel.text = "اندازه فعلی: $percent٪"
    }

    // ---------------- تب ۳: تم (رنگ + تکسچر) بر اساس چیدمان انتخابی ----------------
    private fun buildThemeTab() {
        val currentLayout = prefs.getString("layout_mode", "CUSTOM") ?: "CUSTOM"
        val prefix = "theme_${currentLayout}_"

        contentContainer.addView(card("🎨 تم برای: ${layoutNames[currentLayout]}", "#EF5350") { card ->
            card.addView(hint("هر چیدمان تم مستقل خودش رو داره — تغییری که اینجا بدی فقط رو همین چیدمان اثر می‌ذاره."))

            val bgInput = colorInput(card, "رنگ پس‌زمینه کیبورد", prefix + "bg_color_hex", "#E6E8EB")
            val keyInput = colorInput(card, "رنگ دکمه‌ها", prefix + "key_color_hex", "#FFFFFF")
            val pressInput = colorInput(card, "رنگ حالت فشرده", prefix + "press_color_hex", "#D0D0D0")
            val textInput = colorInput(card, "رنگ نوشته‌ها", prefix + "text_color_hex", "#1F1F1F")

            card.addView(subHint("تصاویر (اختیاری) — خودکار متناسب با اندازه برش می‌خورن"))
            card.addView(imageBtn("تصویر پس‌زمینه کیبورد") { pickImageFor(prefix + "bg_texture") })
            card.addView(imageBtn("تصویر دکمه‌ها (حالت عادی)") { pickImageFor(prefix + "key_idle_texture") })
            card.addView(imageBtn("تصویر حالت کلیک") { pickImageFor(prefix + "key_click_texture") })

            val saveBtn = Button(this)
            saveBtn.text = "ذخیره تم این چیدمان"
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
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            card.addView(saveBtn)
        })
    }

    // ---------------- تب ۴: اتو ----------------
    private fun buildAutoTab() {
        contentContainer.addView(card("⚡ روشن/خاموش تایپ خودکار", "#FBBC05") { card ->
            val masterSwitch = Switch(this)
            masterSwitch.text = "تایپ خودکار"
            masterSwitch.isChecked = prefs.getBoolean("auto_master_enabled", true)
            masterSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("auto_master_enabled", checked).apply()
            }
            card.addView(masterSwitch)
            card.addView(hint("وقتی خاموشه، دکمه «متن‌ها» هیچ پردازش یا باری اضافه به کیبورد نمی‌ده."))
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

        contentContainer.addView(card("⚙ نحوه‌ی تایپ خودکار", "#26C6DA") { card ->
            card.addView(hint("کامل: یک کلیک، کل لیست رو پشت‌سرهم می‌زنه. حرف‌به‌حرف: هر کلیک فقط یک آیتم."))
            val modeGroup = RadioGroup(this)
            modeGroup.orientation = RadioGroup.HORIZONTAL
            val full = RadioButton(this).apply { id = 1001; text = "کامل" }
            val char = RadioButton(this).apply { id = 1002; text = "حرف‌به‌حرف" }
            modeGroup.addView(full)
            modeGroup.addView(char)
            modeGroup.check(if (prefs.getString("auto_mode", "FULL") == "CHAR") 1002 else 1001)
            modeGroup.setOnCheckedChangeListener { _, id ->
                prefs.edit().putString("auto_mode", if (id == 1002) "CHAR" else "FULL").apply()
            }
            card.addView(modeGroup)

            card.addView(subHint("سرعت تایپ هر حرف"))
            val speedLabel = TextView(this)
            val speedBar = SeekBar(this)
            speedBar.max = 300
            val speed = prefs.getInt("auto_speed_ms", 45)
            speedBar.progress = speed
            speedLabel.text = "سرعت: $speed میلی‌ثانیه بین هر حرف (استاندارد پیشنهادی: ۴۵)"
            speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress.coerceAtLeast(10)
                    speedLabel.text = "سرعت: $v میلی‌ثانیه بین هر حرف (استاندارد پیشنهادی: ۴۵)"
                    prefs.edit().putInt("auto_speed_ms", v).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(speedLabel)
            card.addView(speedBar)

            card.addView(subHint("مکث بین آیتم‌ها (فقط حالت کامل)"))
            val delayLabel = TextView(this)
            val delayBar = SeekBar(this)
            delayBar.max = 2000
            val delay = prefs.getInt("enter_delay_ms", 120)
            delayBar.progress = delay
            delayLabel.text = "مکث: $delay میلی‌ثانیه (استاندارد پیشنهادی: ۱۲۰)"
            delayBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    delayLabel.text = "مکث: $progress میلی‌ثانیه (استاندارد پیشنهادی: ۱۲۰)"
                    prefs.edit().putInt("enter_delay_ms", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            card.addView(delayLabel)
            card.addView(delayBar)
        })
    }

    // ---------------- تب ۵: ادیت متن دکمه‌ها (به‌ازای هر چیدمان + قفل روشن/خاموش) ----------------
    private fun buildLabelsTab() {
        layoutIds.forEach { layoutId ->
            contentContainer.addView(card("✏ متن‌های ${layoutNames[layoutId]}", "#8D6E63") { card ->
                card.addView(hint("سوییچ روشن = این متن جایگزین متن پیش‌فرض کلید میشه."))

                val resId = when (layoutId) {
                    "SAMSUNG" -> R.xml.keyboard_samsung_medium
                    "DOLINE" -> R.xml.keyboard_doline
                    "PCBOARD" -> R.xml.keyboard_pcboard
                    else -> R.xml.keyboard_persian_letters_medium
                }
                val kb = android.inputmethodservice.Keyboard(this, resId)
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
                    nameLabel.layoutParams = LinearLayout.LayoutParams(140, LinearLayout.LayoutParams.WRAP_CONTENT)

                    val input = EditText(this)
                    input.setText(prefs.getString(textKey, originalLabel))
                    input.isEnabled = toggle.isChecked
                    input.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    toggle.setOnCheckedChangeListener { _, checked ->
                        input.isEnabled = checked
                        prefs.edit().putBoolean(enableKey, checked).apply()
                    }
                    input.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) prefs.edit().putString(textKey, input.text.toString()).apply()
                    }

                    row.addView(toggle); row.addView(nameLabel); row.addView(input)
                    card.addView(row)
                }
            })
        }
    }

    // ---------------- تب ۶: بهینه‌سازی گوشی ----------------
    private fun buildOptimizeTab() {
        contentContainer.addView(card("🚀 روان‌سازی گوشی", "#0A84FF") { card ->
            card.addView(hint("هر مرحله رو باز کن، تغییر رو بده، برگرد اینجا برای مرحله بعد."))

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
                        catch (_: Exception) { Toast.makeText(this, "این گزینه روی گوشیت در دسترس نیست", Toast.LENGTH_SHORT).show() }
                    }
                }
                row.addView(t); row.addView(d); row.addView(btn)
                card.addView(row)
            }

            stepRow("۱. فعال‌سازی حالت توسعه‌دهنده", "درباره گوشی → روی «شماره ساخت» ۷ بار بزن", Settings.ACTION_DEVICE_INFO_SETTINGS)
            stepRow("۲. خاموش‌کردن انیمیشن‌ها", "مقیاس انیمیشن پنجره/انتقال/انیمیشن‌کننده رو صفر کن", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            stepRow("۳. بستن اپ‌های پرمصرف پس‌زمینه", "لیست اپ‌ها رو باز کن و غیرضروری‌ها رو ببند", Settings.ACTION_APPLICATION_SETTINGS)
        })

        val openSettingsBtn = Button(this)
        openSettingsBtn.text = "فعال‌سازی کیبورد در تنظیمات گوشی"
        openSettingsBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        contentContainer.addView(openSettingsBtn)
    }

    // ---------------- کمکی‌های UI ----------------
    private fun card(title: String, colorHex: String, build: (LinearLayout) -> Unit): LinearLayout {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, 0, 0, 24)
        outer.layoutParams = p

        val header = TextView(this)
        header.text = title
        header.textSize = 15f
        header.setTextColor(Color.WHITE)
        header.setTypeface(null, Typeface.BOLD)
        header.setPadding(24, 18, 24, 18)
        val hb = GradientDrawable(); hb.setColor(Color.parseColor(colorHex)); hb.cornerRadii = floatArrayOf(18f,18f,18f,18f,0f,0f,0f,0f)
        header.background = hb
        outer.addView(header)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(24, 20, 24, 24)
        val bb = GradientDrawable(); bb.setColor(Color.WHITE); bb.cornerRadii = floatArrayOf(0f,0f,0f,0f,18f,18f,18f,18f)
        body.background = bb
        build(body)
        outer.addView(body)
        return outer
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 6, 0, 6)
        val l = TextView(this); l.text = label; l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val v = TextView(this); v.text = value; v.setTypeface(null, Typeface.BOLD)
        row.addView(l); row.addView(v)
        return row
    }

    private fun hint(text: String): TextView {
        val t = TextView(this); t.text = text; t.textSize = 12f; t.setTextColor(Color.GRAY); t.setPadding(0, 8, 0, 8)
        return t
    }

    private fun subHint(text: String): TextView {
        val t = TextView(this); t.text = text; t.textSize = 13f; t.setTypeface(null, Typeface.BOLD); t.setPadding(0, 16, 0, 6)
        return t
    }

    private fun colorInput(parent: LinearLayout, label: String, key: String, default: String): EditText {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val l = TextView(this); l.text = label; l.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val input = EditText(this); input.setText(prefs.getString(key, default))
        input.layoutParams = LinearLayout.LayoutParams(240, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(l); row.addView(input)
        parent.addView(row)
        return input
    }

    private fun imageBtn(label: String, onClick: () -> Unit): Button {
        val b = Button(this); b.text = label; b.setOnClickListener { onClick() }
        return b
    }

    private fun pickImageFor(prefKey: String) {
        val rc = imageResultCounter++
        pendingImageTarget = prefKey to rc
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
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
