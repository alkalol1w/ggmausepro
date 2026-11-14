package com.ggmausepro.mapper.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing a single key mapping.
 */
data class KeyMapping(
    val keyCode: Int,
    val action: ActionType,
    var screenX: Float,
    var screenY: Float,
    val label: String,
    var swipeEndX: Float = 0f,
    var swipeEndY: Float = 0f,
    var enabled: Boolean = true,
    var swipeDuration: Long = 200L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("keyCode", keyCode)
            put("action", action.name)
            put("screenX", screenX.toDouble())
            put("screenY", screenY.toDouble())
            put("label", label)
            put("swipeEndX", swipeEndX.toDouble())
            put("swipeEndY", swipeEndY.toDouble())
            put("enabled", enabled)
            put("swipeDuration", swipeDuration)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): KeyMapping {
            return KeyMapping(
                keyCode = json.getInt("keyCode"),
                action = ActionType.valueOf(json.getString("action")),
                screenX = json.getDouble("screenX").toFloat(),
                screenY = json.getDouble("screenY").toFloat(),
                label = json.getString("label"),
                swipeEndX = json.optDouble("swipeEndX", 0.0).toFloat(),
                swipeEndY = json.optDouble("swipeEndY", 0.0).toFloat(),
                enabled = json.optBoolean("enabled", true),
                swipeDuration = json.optLong("swipeDuration", 200L)
            )
        }
    }
}

/**
 * Enum for the type of action a key mapping performs.
 */
enum class ActionType {
    TAP,        // Single tap at (screenX, screenY)
    HOLD,       // Touch down on key down, touch up on key up
    SWIPE,      // Swipe from (screenX, screenY) to (swipeEndX, swipeEndY)
    TOGGLE      // Toggle touch on/off at (screenX, screenY)
}

/**
 * Singleton manager for key mappings.
 * Stores mappings in SharedPreferences as JSON.
 * Supports multiple named profiles.
 */
object KeyMappingManager {

    private const val TAG = "KeyMappingManager"
    private const val PREFS_NAME = "ggmausepro_mappings"
    private const val KEY_CURRENT_PROFILE = "current_profile"
    private const val KEY_PROFILES_LIST = "profiles_list"
    private const val DEFAULT_PROFILE = "PUBG"

    // Mouse button virtual key codes (not in KeyEvent)
    const val MOUSE_LEFT_CLICK = -1
    const val MOUSE_RIGHT_CLICK = -2
    const val MOUSE_MIDDLE_CLICK = -3

    private lateinit var prefs: SharedPreferences
    private var currentProfileName: String = DEFAULT_PROFILE
    private val mappings = HashMap<Int, KeyMapping>()
    private var initialized = false

    // Screen dimensions for default layout calculation
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f

    /**
     * Initialize the manager with a context. Must be called before any other method.
     */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
        screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
        currentProfileName = prefs.getString(KEY_CURRENT_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        loadProfile(currentProfileName)
        initialized = true
    }

    /**
     * Get the current active profile name.
     */
    fun getCurrentProfile(): String = currentProfileName

    /**
     * Get all available profile names.
     */
    fun getProfileNames(): List<String> {
        val json = prefs.getString(KEY_PROFILES_LIST, null)
        if (json != null) {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { arr.getString(it) }
        }
        return listOf(DEFAULT_PROFILE)
    }

    /**
     * Switch to a different profile, saving the current one first.
     */
    fun switchProfile(name: String) {
        saveCurrentProfile()
        currentProfileName = name
        prefs.edit().putString(KEY_CURRENT_PROFILE, name).apply()
        loadProfile(name)
    }

    /**
     * Create a new empty profile.
     */
    fun createProfile(name: String) {
        val profiles = getProfileNames().toMutableList()
        if (!profiles.contains(name)) {
            profiles.add(name)
            saveProfilesList(profiles)
        }
        // Save empty mappings for the new profile
        prefs.edit().putString("profile_$name", JSONArray().toString()).apply()
    }

    /**
     * Delete a profile. Cannot delete the last remaining profile.
     */
    fun deleteProfile(name: String) {
        val profiles = getProfileNames().toMutableList()
        if (profiles.size <= 1) return
        profiles.remove(name)
        saveProfilesList(profiles)
        prefs.edit().remove("profile_$name").apply()
        if (currentProfileName == name) {
            switchProfile(profiles.first())
        }
    }

    /**
     * Get a mapping for a specific key code.
     */
    fun getMapping(keyCode: Int): KeyMapping? = mappings[keyCode]

    /**
     * Get all current mappings.
     */
    fun getAllMappings(): Map<Int, KeyMapping> = HashMap(mappings)

    /**
     * Set or update a mapping.
     */
    fun setMapping(mapping: KeyMapping) {
        mappings[mapping.keyCode] = mapping
        saveCurrentProfile()
    }

    /**
     * Remove a mapping for a key code.
     */
    fun removeMapping(keyCode: Int) {
        mappings.remove(keyCode)
        saveCurrentProfile()
    }

    /**
     * Update the screen position of a mapping.
     */
    fun updatePosition(keyCode: Int, x: Float, y: Float) {
        mappings[keyCode]?.let {
            it.screenX = x
            it.screenY = y
            saveCurrentProfile()
        }
    }

    /**
     * Export the current profile as a JSON string.
     */
    fun exportProfileAsJson(): String {
        val arr = JSONArray()
        mappings.values.forEach { arr.put(it.toJson()) }
        val wrapper = JSONObject().apply {
            put("profileName", currentProfileName)
            put("version", 1)
            put("screenWidth", screenWidth.toDouble())
            put("screenHeight", screenHeight.toDouble())
            put("mappings", arr)
        }
        return wrapper.toString(2)
    }

    /**
     * Import a profile from a JSON string.
     */
    fun importProfileFromJson(json: String): Boolean {
        return try {
            val wrapper = JSONObject(json)
            val name = wrapper.getString("profileName")
            val arr = wrapper.getJSONArray("mappings")
            val importedMappings = HashMap<Int, KeyMapping>()
            for (i in 0 until arr.length()) {
                val mapping = KeyMapping.fromJson(arr.getJSONObject(i))
                importedMappings[mapping.keyCode] = mapping
            }
            // Scale positions if screen dimensions differ
            val srcWidth = wrapper.optDouble("screenWidth", screenWidth.toDouble()).toFloat()
            val srcHeight = wrapper.optDouble("screenHeight", screenHeight.toDouble()).toFloat()
            if (srcWidth != screenWidth || srcHeight != screenHeight) {
                val scaleX = screenWidth / srcWidth
                val scaleY = screenHeight / srcHeight
                importedMappings.values.forEach { m ->
                    m.screenX *= scaleX
                    m.screenY *= scaleY
                    m.swipeEndX *= scaleX
                    m.swipeEndY *= scaleY
                }
            }
            createProfile(name)
            val arr2 = JSONArray()
            importedMappings.values.forEach { arr2.put(it.toJson()) }
            prefs.edit().putString("profile_$name", arr2.toString()).apply()
            switchProfile(name)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import profile: ${e.message}", e)
            false
        }
    }

    /**
     * Get the label for a keyCode.
     */
    fun getKeyLabel(keyCode: Int): String {
        return when (keyCode) {
            MOUSE_LEFT_CLICK -> "LMB"
            MOUSE_RIGHT_CLICK -> "RMB"
            MOUSE_MIDDLE_CLICK -> "MMB"
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ESCAPE -> "ESC"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
            else -> {
                val ch = KeyEvent.keyCodeToString(keyCode)
                    .removePrefix("KEYCODE_")
                    .replace("_", " ")
                if (ch.length == 1) ch else ch.lowercase()
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }

    // --- Private helpers ---

    private fun loadProfile(name: String) {
        mappings.clear()
        val json = prefs.getString("profile_$name", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val mapping = KeyMapping.fromJson(arr.getJSONObject(i))
                    mappings[mapping.keyCode] = mapping
                }
                Log.d(TAG, "Loaded profile '$name' with ${mappings.size} mappings")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile '$name': ${e.message}", e)
                createDefaultPubgProfile()
            }
        } else {
            if (name == DEFAULT_PROFILE) {
                createDefaultPubgProfile()
                saveCurrentProfile()
            }
            // Ensure the profile is in the list
            val profiles = getProfileNames().toMutableList()
            if (!profiles.contains(name)) {
                profiles.add(name)
                saveProfilesList(profiles)
            }
        }
    }

    fun getCurrentMappings(): Collection<KeyMapping> = mappings.values

    fun saveCurrentProfile() {
        val arr = JSONArray()
        mappings.values.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("profile_$currentProfileName", arr.toString()).apply()
    }

    private fun saveProfilesList(profiles: List<String>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it) }
        prefs.edit().putString(KEY_PROFILES_LIST, arr.toString()).apply()
    }

    /**
     * Create the default PUBG Mobile profile with pre-configured key positions.
     */
    private fun createDefaultPubgProfile() {
        val w = screenWidth
        val h = screenHeight

        // Movement controls (left side joystick area)
        val joyX = w * 0.15f
        val joyY = h * 0.70f
        val joyRadius = h * 0.12f

        mappings.clear()

        // W - Forward: swipe up from joystick center
        mappings[KeyEvent.KEYCODE_W] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_W,
            action = ActionType.SWIPE,
            screenX = joyX,
            screenY = joyY,
            label = "W",
            swipeEndX = joyX,
            swipeEndY = joyY - joyRadius,
            swipeDuration = 50L
        )

        // A - Left: swipe left from joystick center
        mappings[KeyEvent.KEYCODE_A] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_A,
            action = ActionType.SWIPE,
            screenX = joyX,
            screenY = joyY,
            label = "A",
            swipeEndX = joyX - joyRadius,
            swipeEndY = joyY,
            swipeDuration = 50L
        )

        // S - Backward: swipe down from joystick center
        mappings[KeyEvent.KEYCODE_S] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_S,
            action = ActionType.SWIPE,
            screenX = joyX,
            screenY = joyY,
            label = "S",
            swipeEndX = joyX,
            swipeEndY = joyY + joyRadius,
            swipeDuration = 50L
        )

        // D - Right: swipe right from joystick center
        mappings[KeyEvent.KEYCODE_D] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_D,
            action = ActionType.SWIPE,
            screenX = joyX,
            screenY = joyY,
            label = "D",
            swipeEndX = joyX + joyRadius,
            swipeEndY = joyY,
            swipeDuration = 50L
        )

        // Space - Jump
        mappings[KeyEvent.KEYCODE_SPACE] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_SPACE,
            action = ActionType.TAP,
            screenX = w * 0.92f,
            screenY = h * 0.55f,
            label = "Space"
        )

        // C - Crouch
        mappings[KeyEvent.KEYCODE_C] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_C,
            action = ActionType.TAP,
            screenX = w * 0.92f,
            screenY = h * 0.72f,
            label = "C"
        )

        // Z - Prone
        mappings[KeyEvent.KEYCODE_Z] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_Z,
            action = ActionType.TAP,
            screenX = w * 0.92f,
            screenY = h * 0.85f,
            label = "Z"
        )

        // R - Reload
        mappings[KeyEvent.KEYCODE_R] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_R,
            action = ActionType.TAP,
            screenX = w * 0.78f,
            screenY = h * 0.65f,
            label = "R"
        )

        // F - Interact / Pickup
        mappings[KeyEvent.KEYCODE_F] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_F,
            action = ActionType.TAP,
            screenX = w * 0.50f,
            screenY = h * 0.55f,
            label = "F"
        )

        // G - Grenade
        mappings[KeyEvent.KEYCODE_G] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_G,
            action = ActionType.TAP,
            screenX = w * 0.85f,
            screenY = h * 0.30f,
            label = "G"
        )

        // M - Map
        mappings[KeyEvent.KEYCODE_M] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_M,
            action = ActionType.TAP,
            screenX = w * 0.90f,
            screenY = h * 0.05f,
            label = "M"
        )

        // Tab - Inventory / Backpack
        mappings[KeyEvent.KEYCODE_TAB] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_TAB,
            action = ActionType.TAP,
            screenX = w * 0.80f,
            screenY = h * 0.05f,
            label = "Tab"
        )

        // Mouse Left Click - Shoot (hold for auto-fire)
        mappings[MOUSE_LEFT_CLICK] = KeyMapping(
            keyCode = MOUSE_LEFT_CLICK,
            action = ActionType.HOLD,
            screenX = w * 0.88f,
            screenY = h * 0.45f,
            label = "LMB"
        )

        // Mouse Right Click - Aim / Scope
        mappings[MOUSE_RIGHT_CLICK] = KeyMapping(
            keyCode = MOUSE_RIGHT_CLICK,
            action = ActionType.TAP,
            screenX = w * 0.73f,
            screenY = h * 0.45f,
            label = "RMB"
        )

        // Shift - Sprint toggle
        mappings[KeyEvent.KEYCODE_SHIFT_LEFT] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
            action = ActionType.TOGGLE,
            screenX = w * 0.10f,
            screenY = h * 0.50f,
            label = "Shift"
        )

        // Number keys 1-5 for weapon slots
        val weaponKeys = listOf(
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5
        )
        weaponKeys.forEachIndexed { index, keyCode ->
            mappings[keyCode] = KeyMapping(
                keyCode = keyCode,
                action = ActionType.TAP,
                screenX = w * (0.55f + index * 0.06f),
                screenY = h * 0.92f,
                label = "${index + 1}"
            )
        }

        // ESC - Back / Exit
        mappings[KeyEvent.KEYCODE_ESCAPE] = KeyMapping(
            keyCode = KeyEvent.KEYCODE_ESCAPE,
            action = ActionType.TAP,
            screenX = 0f, // Special handling: performs BACK action
            screenY = 0f,
            label = "ESC"
        )

        Log.d(TAG, "Created default PUBG profile with ${mappings.size} mappings")
    }
}
