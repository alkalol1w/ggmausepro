package com.ggmausepro.mapper.activity

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.manager.MouseCaptureManager
import com.ggmausepro.mapper.shizuku.ShizukuManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val txtShizukuStatus = findViewById<TextView>(R.id.txtShizukuStatus)
        val btnRequestShizuku = findViewById<Button>(R.id.btnRequestShizuku)
        val txtSensitivityValue = findViewById<TextView>(R.id.txtSensitivityValue)
        val seekSensitivity = findViewById<SeekBar>(R.id.seekSensitivity)
        val switchInvertY = findViewById<SwitchMaterial>(R.id.switchInvertY)
        val switchAntiBan = findViewById<SwitchMaterial>(R.id.switchAntiBanRandomization)

        // Shizuku durumunu takip et
        lifecycleScope.launch {
            ShizukuManager.hasPermission.collectLatest { hasPerm ->
                if (hasPerm) {
                    txtShizukuStatus.text = "Shizuku: AKTİF (Sıfır Gecikme Modu devrede)"
                    txtShizukuStatus.setTextColor(getColor(R.color.neon_green))
                    btnRequestShizuku.isEnabled = false
                    btnRequestShizuku.text = "Yetkilendirildi"
                } else if (ShizukuManager.isShizukuAvailable.value) {
                    txtShizukuStatus.text = "Shizuku: Servis çalışıyor ama yetki bekleniyor"
                    txtShizukuStatus.setTextColor(getColor(R.color.neon_yellow))
                    btnRequestShizuku.isEnabled = true
                    btnRequestShizuku.text = "Yetki İste"
                } else {
                    txtShizukuStatus.text = "Shizuku: Algılanamadı (Normal mod aktif)"
                    txtShizukuStatus.setTextColor(getColor(R.color.status_inactive))
                    btnRequestShizuku.isEnabled = true
                    btnRequestShizuku.text = "Yeniden Kontrol Et"
                }
            }
        }

        btnRequestShizuku.setOnClickListener {
            if (ShizukuManager.isShizukuAvailable.value) {
                ShizukuManager.requestPermission()
            } else {
                ShizukuManager.checkStatus()
                Toast.makeText(this, "Shizuku uygulamasının açık ve çalışıyor olduğundan emin olun.", Toast.LENGTH_LONG).show()
            }
        }

        // Fare Hassasiyeti
        val currentSensitivity = MouseCaptureManager.getSensitivity()
        seekSensitivity.progress = (currentSensitivity * 10).toInt()
        txtSensitivityValue.text = String.format("Hassasiyet: %.1fx", currentSensitivity)

        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 10f
                txtSensitivityValue.text = String.format("Hassasiyet: %.1fx", value)
                if (fromUser) {
                    MouseCaptureManager.setSensitivity(value)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Y Eksenini Ters Çevir
        switchInvertY.isChecked = MouseCaptureManager.isInvertY()
        switchInvertY.setOnCheckedChangeListener { _, isChecked ->
            MouseCaptureManager.setInvertY(isChecked)
        }

        // Anti-ban rastgeleliği
        switchAntiBan.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if (isChecked) "Dokunma rastgeleliği devrede" else "Dokunma rastgeleliği kapalı", Toast.LENGTH_SHORT).show()
        }
    }
}
