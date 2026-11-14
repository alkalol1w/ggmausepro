package com.ggmausepro.mapper.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.databinding.ActivityMainBinding
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.ggmausepro.mapper.manager.MouseCaptureManager
import com.ggmausepro.mapper.service.InputMapperService
import com.ggmausepro.mapper.service.OverlayService
import com.ggmausepro.mapper.shizuku.ShizukuManager
import com.ggmausepro.mapper.util.EventBus

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        KeyMappingManager.init(this)
        MouseCaptureManager.init(this)

        setupListeners()
        setupProfileSpinner()
        setupSensitivitySlider()

        EventBus.subscribe<EventBus.MappingStateChanged>(this) { event ->
            runOnUiThread {
                updateMappingUI(event.isActive)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        ShizukuManager.checkStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.unregister(this)
    }

    private fun setupListeners() {
        binding.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnGrantOverlay.setOnClickListener {
            openOverlaySettings()
        }

        binding.btnToggleMapping.setOnClickListener {
            toggleMappingService()
        }

        binding.btnEditKeyLayout.setOnClickListener {
            startActivity(Intent(this, KeyMappingActivity::class.java))
        }

        binding.btnProfiles.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupProfileSpinner() {
        val profiles = KeyMappingManager.getProfileNames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profiles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProfiles.adapter = adapter

        val currentProfile = KeyMappingManager.getCurrentProfile()
        val selectedIndex = profiles.indexOf(currentProfile)
        if (selectedIndex >= 0) {
            binding.spinnerProfiles.setSelection(selectedIndex)
        }

        binding.spinnerProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = profiles[position]
                if (selected != KeyMappingManager.getCurrentProfile()) {
                    KeyMappingManager.switchProfile(selected)
                    binding.tvActiveProfile.text = selected
                    Toast.makeText(this@MainActivity, "Profil değiştirildi: $selected", Toast.LENGTH_SHORT).show()
                }
            }

            override fun無視(parent: AdapterView<*>?) {}
            private fun AdapterView<*>?.無視(parent: AdapterView<*>?) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSensitivitySlider() {
        val currentSensitivity = MouseCaptureManager.getSensitivity()
        binding.sliderSensitivity.value = currentSensitivity
        binding.tvSensitivityValue.text = String.format("%.1fx", currentSensitivity)

        binding.sliderSensitivity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                MouseCaptureManager.setSensitivity(value)
                binding.tvSensitivityValue.text = String.format("%.1fx", value)
            }
        }
    }

    private fun toggleMappingService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Lütfen önce Ekran Üstünde Gösterme İzni verin!", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }

        if (InputMapperService.isMappingActive) {
            InputMapperService.instance?.stopMapping()
            stopService(Intent(this, OverlayService::class.java))
            updateMappingUI(false)
        } else {
            // Shizuku kontrolü
            if (ShizukuManager.isShizukuAvailable.value && !ShizukuManager.hasPermission.value) {
                ShizukuManager.requestPermission()
            }

            // Overlay servisini başlat
            val overlayIntent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }

            // Mapper servisini başlat
            InputMapperService.instance?.startMapping()
            updateMappingUI(true)
            Toast.makeText(this, "Eşleme Başlatıldı! PUBG'yi açıp oynamaya başlayabilirsiniz.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatus() {
        // Servis durumu
        val isServiceRunning = InputMapperService.instance != null
        binding.tvServiceStatus.text = if (isServiceRunning) getString(R.string.service_enabled) else getString(R.string.service_disabled)
        binding.tvServiceStatus.setTextColor(
            ContextCompat.getColor(this, if (isServiceRunning) R.color.status_active else R.color.status_inactive)
        )
        binding.indicatorService.setBackgroundResource(
            if (isServiceRunning) R.drawable.indicator_active else R.drawable.indicator_inactive
        )

        // Overlay izni
        val hasOverlay = hasOverlayPermission()
        binding.tvOverlayStatus.text = if (hasOverlay) getString(R.string.overlay_granted) else getString(R.string.overlay_not_granted)
        binding.tvOverlayStatus.setTextColor(
            ContextCompat.getColor(this, if (hasOverlay) R.color.status_active else R.color.status_inactive)
        )
        binding.indicatorOverlay.setBackgroundResource(
            if (hasOverlay) R.drawable.indicator_active else R.drawable.indicator_inactive
        )

        // Profil
        val profileName = KeyMappingManager.getCurrentProfile()
        binding.tvActiveProfile.text = profileName
        binding.indicatorProfile.setBackgroundResource(R.drawable.indicator_active)

        updateMappingUI(InputMapperService.isMappingActive)
    }

    private fun updateMappingUI(isActive: Boolean) {
        if (isActive) {
            binding.btnToggleMapping.text = getString(R.string.stop_mapping)
            binding.btnToggleMapping.setBackgroundColor(ContextCompat.getColor(this, R.color.status_error))
            binding.tvMappingStatus.text = "Mapping AKTİF - Tuşlar ekrana gönderiliyor"
            binding.tvMappingStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            binding.btnToggleMapping.text = getString(R.string.start_mapping)
            binding.btnToggleMapping.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_green))
            binding.tvMappingStatus.text = getString(R.string.mapping_inactive)
            binding.tvMappingStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}
