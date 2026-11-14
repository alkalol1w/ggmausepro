package com.ggmausepro.mapper.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var mappings: MutableList<KeyMapping> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("isActive", isActive)
            val mappingsArray = JSONArray()
            mappings.forEach { mappingsArray.put(it.toJson()) }
            put("mappings", mappingsArray)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Profile {
            val mappingsArray = json.optJSONArray("mappings") ?: JSONArray()
            val mappingsList = mutableListOf<KeyMapping>()
            for (i in 0 until mappingsArray.length()) {
                mappingsList.add(KeyMapping.fromJson(mappingsArray.getJSONObject(i)))
            }
            return Profile(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", ""),
                mappings = mappingsList,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                isActive = json.optBoolean("isActive", false)
            )
        }
    }
}
