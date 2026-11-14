package com.ggmausepro.mapper.activity

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ggmausepro.mapper.R
import com.ggmausepro.mapper.adapter.KeyMappingAdapter
import com.ggmausepro.mapper.databinding.ActivityKeyMappingBinding
import com.ggmausepro.mapper.manager.ActionType
import com.ggmausepro.mapper.manager.KeyMapping
import com.ggmausepro.mapper.manager.KeyMappingManager
import com.ggmausepro.mapper.service.OverlayService

class KeyMappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyMappingBinding
    private lateinit var adapter: KeyMappingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        val mappingsList = KeyMappingManager.getCurrentMappings().toList()
        adapter = KeyMappingAdapter(
            mappings = mappingsList,
            onItemClick = { mapping ->
                showEditMappingDialog(mapping)
            },
            onToggleChanged = { mapping, isEnabled ->
                KeyMappingManager.setMapping(mapping)
            }
        )

        binding.recyclerMappings.layoutManager = LinearLayoutManager(this)
        binding.recyclerMappings.adapter = adapter
        updateEmptyState()
    }

    private fun setupListeners() {
        binding.fabAddMapping.setOnClickListener {
            showAddMappingDialog()
        }

        binding.btnVisualEditor.setOnClickListener {
            // Overlay Düzenleme Modunu tetikle
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_TOGGLE_EDIT_MODE
            }
            startService(intent)
            Toast.makeText(this, "Ekranda tuşları sürükleyip PUBG butonlarının üzerine yerleştirin!", Toast.LENGTH_LONG).show()
        }

        binding.btnTestMapping.setOnClickListener {
            Toast.makeText(this, "Klavyenizden tuşlara basarak ekrandaki tepkiyi test edebilirsiniz.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshList() {
        val list = KeyMappingManager.getCurrentMappings().toList()
        adapter.updateList(list)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.tvEmptyMappings.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerMappings.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddMappingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_mapping)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val editLabel = dialog.findViewById<EditText>(R.id.editKeyLabel)
        val btnDetectKey = dialog.findViewById<Button>(R.id.btnDetectKey)
        val spinnerAction = dialog.findViewById<Spinner>(R.id.spinnerActionType)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialog.findViewById<Button>(R.id.btnSave)

        val actions = ActionType.values().map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAction.adapter = spinnerAdapter

        var detectedKeyCode = KeyEvent.KEYCODE_SPACE
        var detectedKeyLabel = "Space"

        btnDetectKey.setOnClickListener {
            btnDetectKey.text = "Klavyeden bir tuşa basın..."
            btnDetectKey.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    detectedKeyCode = keyCode
                    detectedKeyLabel = KeyMappingManager.getKeyLabel(keyCode)
                    btnDetectKey.text = "Seçilen: $detectedKeyLabel"
                    true
                } else {
                    false
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val label = editLabel.text.toString().trim()
            val action = ActionType.valueOf(actions[spinnerAction.selectedItemPosition])

            // Ekran ortasına yerleştir
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2f
            val centerY = displayMetrics.heightPixels / 2f

            val newMapping = KeyMapping(
                keyCode = detectedKeyCode,
                action = action,
                screenX = centerX,
                screenY = centerY,
                label = label.ifEmpty { detectedKeyLabel }
            )

            KeyMappingManager.setMapping(newMapping)
            refreshList()
            dialog.dismiss()
            Toast.makeText(this, "Tuş eklendi! 'Görsel Düzenleyici' ile ekrandaki yerini ayarlayabilirsiniz.", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    private fun showEditMappingDialog(mapping: KeyMapping) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_mapping)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val editLabel = dialog.findViewById<EditText>(R.id.editKeyLabel)
        val btnDetectKey = dialog.findViewById<Button>(R.id.btnDetectKey)
        val spinnerAction = dialog.findViewById<Spinner>(R.id.spinnerActionType)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialog.findViewById<Button>(R.id.btnSave)

        editLabel.setText(mapping.label)
        btnDetectKey.text = "Tuş: ${KeyMappingManager.getKeyLabel(mapping.keyCode)}"

        val actions = ActionType.values().map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAction.adapter = spinnerAdapter
        spinnerAction.setSelection(mapping.action.ordinal)

        var selectedKeyCode = mapping.keyCode

        btnDetectKey.setOnClickListener {
            btnDetectKey.text = "Klavyeden yeni bir tuşa basın..."
            btnDetectKey.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    selectedKeyCode = keyCode
                    btnDetectKey.text = "Seçilen: ${KeyMappingManager.getKeyLabel(keyCode)}"
                    true
                } else {
                    false
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            mapping.label = editLabel.text.toString().trim()
            mapping.action = ActionType.valueOf(actions[spinnerAction.selectedItemPosition])
            if (selectedKeyCode != mapping.keyCode) {
                KeyMappingManager.removeMapping(mapping.keyCode)
                val updated = mapping.copy(keyCode = selectedKeyCode)
                KeyMappingManager.setMapping(updated)
            } else {
                KeyMappingManager.setMapping(mapping)
            }
            refreshList()
            dialog.dismiss()
        }

        dialog.show()
    }
}
