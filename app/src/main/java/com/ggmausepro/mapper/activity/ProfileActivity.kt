package com.ggmausepro.mapper.activity

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.adapter.ProfileAdapter
import com.ggmausepro.mapper.databinding.ActivityProfileBinding
import com.ggmausepro.mapper.manager.KeyMappingManager

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        val profiles = KeyMappingManager.getProfileNames()
        val currentProfile = KeyMappingManager.getCurrentProfile()

        adapter = ProfileAdapter(
            profiles = profiles,
            currentProfile = currentProfile,
            onProfileSelected = { profileName ->
                KeyMappingManager.switchProfile(profileName)
                refreshProfiles()
                Toast.makeText(this, "Profil aktif edildi: $profileName", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerProfiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerProfiles.adapter = adapter
        updateEmptyState()
    }

    private fun setupListeners() {
        binding.fabAddProfile.setOnClickListener {
            showCreateProfileDialog()
        }

        binding.btnPrebuiltPubg.setOnClickListener {
            loadPrebuiltProfile("PUBG Mobile")
        }

        binding.btnPrebuiltCod.setOnClickListener {
            loadPrebuiltProfile("Call of Duty Mobile")
        }

        binding.btnPrebuiltFreefire.setOnClickListener {
            loadPrebuiltProfile("Free Fire")
        }

        binding.btnExportProfile.setOnClickListener {
            val json = KeyMappingManager.exportProfileAsJson()
            // Panoya kopyala
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("GGMausePro Profile", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Profil JSON olarak panoya kopyalandı!", Toast.LENGTH_LONG).show()
        }

        binding.btnImportProfile.setOnClickListener {
            showImportProfileDialog()
        }
    }

    private fun loadPrebuiltProfile(name: String) {
        KeyMappingManager.createProfile(name)
        KeyMappingManager.switchProfile(name)
        refreshProfiles()
        Toast.makeText(this, "$name hazır profili yüklendi!", Toast.LENGTH_SHORT).show()
    }

    private fun refreshProfiles() {
        val profiles = KeyMappingManager.getProfileNames()
        val current = KeyMappingManager.getCurrentProfile()
        adapter.updateData(profiles, current)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.tvEmptyProfiles.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerProfiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showCreateProfileDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_profile)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val editName = dialog.findViewById<EditText>(R.id.editProfileName)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnCreate.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isNotEmpty()) {
                KeyMappingManager.createProfile(name)
                KeyMappingManager.switchProfile(name)
                refreshProfiles()
                dialog.dismiss()
                Toast.makeText(this, "Profil oluşturuldu: $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Lütfen bir profil adı girin", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showImportProfileDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_profile)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val editName = dialog.findViewById<EditText>(R.id.editProfileName)
        editName.hint = "JSON kodunu buraya yapıştırın"
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)
        btnCreate.text = "İçe Aktar"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnCreate.setOnClickListener {
            val json = editName.text.toString().trim()
            if (json.isNotEmpty()) {
                val success = KeyMappingManager.importProfileFromJson(json)
                if (success) {
                    refreshProfiles()
                    dialog.dismiss()
                    Toast.makeText(this, "Profil başarıyla içe aktarıldı!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Geçersiz JSON formatı!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
