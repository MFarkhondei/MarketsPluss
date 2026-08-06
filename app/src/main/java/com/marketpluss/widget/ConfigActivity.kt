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

        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val btnRefresh = findViewById<MaterialButton>(R.id.btn_refresh_now)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        val labels = resources.getStringArray(R.array.refresh_interval_labels)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val cur = Prefs.getIntervalMin(this)
        val idx = Prefs.INTERVAL_OPTIONS.indexOf(cur).let { if (it >= 0) it else 1 }
        spinner.setSelection(idx)

        btnSave.setOnClickListener {
            val interval = Prefs.INTERVAL_OPTIONS.getOrElse(spinner.selectedItemPosition) { 30 }
            Prefs.setIntervalMin(this, interval)
            UpdateScheduler.schedule(this)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            val t = if (interval > 0) "هر $interval دقیقه" else "فقط دستی"
            tvStatus.text = "✅ ذخیره شد · $t"
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
