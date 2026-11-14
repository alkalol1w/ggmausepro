package com.ggmausepro.mapper.model

import org.json.JSONObject
import java.util.UUID

enum class ActionType {
    TAP, HOLD, SWIPE, TOGGLE;

    companion object {
        fun fromString(value: String): ActionType {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                TAP
            }
        }
    }
}

data class KeyMapping(
    val id: String = UUID.randomUUID().toString(),
    var keyCode: Int = 0,
    var keyName: String = "",
    var actionType: ActionType = ActionType.TAP,
    var screenX: Float = 0f,
    var screenY: Float = 0f,
    var swipeEndX: Float = 0f,
    var swipeEndY: Float = 0f,
    var swipeDuration: Long = 200L,
    var label: String = "",
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("keyCode", keyCode)
            put("keyName", keyName)
            put("actionType", actionType.name)
            put("screenX", screenX.toDouble())
            put("screenY", screenY.toDouble())
            put("swipeEndX", swipeEndX.toDouble())
            put("swipeEndY", swipeEndY.toDouble())
            put("swipeDuration", swipeDuration)
            put("label", label)
            put("enabled", enabled)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): KeyMapping {
            return KeyMapping(
                id = json.optString("id", UUID.randomUUID().toString()),
                keyCode = json.optInt("keyCode", 0),
                keyName = json.optString("keyName", ""),
                actionType = ActionType.fromString(json.optString("actionType", "TAP")),
                screenX = json.optDouble("screenX", 0.0).toFloat(),
                screenY = json.optDouble("screenY", 0.0).toFloat(),
                swipeEndX = json.optDouble("swipeEndX", 0.0).toFloat(),
                swipeEndY = json.optDouble("swipeEndY", 0.0).toFloat(),
                swipeDuration = json.optLong("swipeDuration", 200L),
                label = json.optString("label", ""),
                enabled = json.optBoolean("enabled", true)
            )
        }
    }
}
