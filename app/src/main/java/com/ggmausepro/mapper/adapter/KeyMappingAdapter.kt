package com.ggmausepro.mapper.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.manager.KeyMapping
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.google.android.material.switchmaterial.SwitchMaterial

class KeyMappingAdapter(
    private var mappings: List<KeyMapping>,
    private val onItemClick: (KeyMapping) -> Unit,
    private val onToggleChanged: (KeyMapping, Boolean) -> Unit
) : RecyclerView.Adapter<KeyMappingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtKeyBadge: TextView = view.findViewById(R.id.txtKeyBadge)
        val txtKeyLabel: TextView = view.findViewById(R.id.txtKeyLabel)
        val txtKeyDetails: TextView = view.findViewById(R.id.txtKeyDetails)
        val switchKeyEnabled: SwitchMaterial = view.findViewById(R.id.switchKeyEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_key_mapping, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mapping = mappings[position]
        val keyName = KeyMappingManager.getKeyLabel(mapping.keyCode)

        holder.txtKeyBadge.text = keyName
        holder.txtKeyLabel.text = mapping.label.ifEmpty { keyName }
        holder.txtKeyDetails.text = "Eylem: ${mapping.action.name} | Konum: (${mapping.screenX.toInt()}, ${mapping.screenY.toInt()})"

        holder.switchKeyEnabled.setOnCheckedChangeListener(null)
        holder.switchKeyEnabled.isChecked = mapping.enabled

        holder.switchKeyEnabled.setOnCheckedChangeListener { _, isChecked ->
            mapping.enabled = isChecked
            onToggleChanged(mapping, isChecked)
        }

        holder.itemView.setOnClickListener {
            onItemClick(mapping)
        }
    }

    override fun getItemCount(): Int = mappings.size

    fun updateList(newMappings: List<KeyMapping>) {
        mappings = newMappings
        notifyDataSetChanged()
    }
}
