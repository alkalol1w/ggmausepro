package com.ggmausepro.mapper.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.manager.KeyMappingManager

class ProfileAdapter(
    private var profiles: List<String>,
    private var currentProfile: String,
    private val onProfileSelected: (String) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgActiveIndicator: ImageView = view.findViewById(R.id.imgActiveIndicator)
        val txtProfileName: TextView = view.findViewById(R.id.txtProfileName)
        val txtMappingCount: TextView = view.findViewById(R.id.txtMappingCount)
        val btnSelectProfile: Button = view.findViewById(R.id.btnSelectProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profileName = profiles[position]
        val isActive = profileName == currentProfile

        holder.txtProfileName.text = profileName
        holder.txtMappingCount.text = if (isActive) "Aktif Profil" else "Kullanılabilir"
        holder.imgActiveIndicator.setImageResource(
            if (isActive) R.drawable.indicator_active else R.drawable.indicator_inactive
        )

        holder.btnSelectProfile.text = if (isActive) "Seçili" else "Kullan"
        holder.btnSelectProfile.isEnabled = !isActive

        holder.btnSelectProfile.setOnClickListener {
            onProfileSelected(profileName)
        }
    }

    override fun getItemCount(): Int = profiles.size

    fun updateData(newProfiles: List<String>, newActiveProfile: String) {
        profiles = newProfiles
        currentProfile = newActiveProfile
        notifyDataSetChanged()
    }
}
