package com.marketpluss.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val spinnerInterval = findViewById<Spinner>(R.id.spinner_interval)
        val spinnerFont = findViewById<Spinner>(R.id.spinner_font)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val btnRefresh = findViewById<MaterialButton>(R.id.btn_refresh_now)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        val intervalLabels = resources.getStringArray(R.array.refresh_interval_labels)
        spinnerInterval.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervalLabels)
        val curInterval = Prefs.getIntervalMin(this)
        val intervalIdx = Prefs.INTERVAL_OPTIONS.indexOf(curInterval).let { if (it >= 0) it else 1 }
        spinnerInterval.setSelection(intervalIdx)

        val fontLabels = resources.getStringArray(R.array.font_size_labels)
        spinnerFont.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontLabels)
        spinnerFont.setSelection(Prefs.fontOptionIndex(this))

        btnSave.setOnClickListener {
            val interval = Prefs.INTERVAL_OPTIONS.getOrElse(spinnerInterval.selectedItemPosition) { 30 }
            val fontSp = Prefs.FONT_OPTIONS.getOrElse(spinnerFont.selectedItemPosition) {
                Prefs.FONT_DEFAULT
            }
            Prefs.setIntervalMin(this, interval)
            Prefs.setFontSp(this, fontSp)
            UpdateScheduler.schedule(this)
            // اعمال فوری فونت جدید روی ویجت
            WidgetRenderer.applyCache(this)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            val t = if (interval > 0) "هر $interval دقیقه" else "فقط دستی"
            tvStatus.text = "✅ ذخیره شد · $t · فونت ${fontSp}sp"
        }

        btnRefresh.setOnClickListener {
            tvStatus.text = "در حال بروزرسانی…"
            btnRefresh.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                val ok = WidgetRenderer.fetchAndApply(this@ConfigActivity)
                withContext(Dispatchers.Main) {
                    btnRefresh.isEnabled = true
                    tvStatus.text = if (ok) "✅ بروزرسانی موفق" else "⚠️ خطا — کش قبلی نمایش داده شد"
                }
            }
        }
    }
}
