package com.example.hyundaipleos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ValueBoundsInt(val min: Int, val max: Int)
internal data class ValueBoundsFloat(val min: Float, val max: Float)

internal data class ModeSettings(
    val driverSeatHeat: Int? = null,
    val passengerSeatHeat: Int? = null,
    val driverSeatVentilation: Int? = null,
    val passengerSeatVentilation: Int? = null,
    val steeringWheelHeat: Int? = null,
    val targetTemperatureC: Float? = null,
    val passengerTargetTemperatureC: Float? = null,
    val fanSpeed: Int? = null,
    val passengerFanSpeed: Int? = null,
    val fanDirection: String? = null,
    val passengerFanDirection: String? = null,
    val hvacPower: Boolean? = null,
    val hvacAuto: Boolean? = null,
    val airConditioning: Boolean? = null,
    val airRecirculation: Boolean? = null,
    val autoRecirculation: Boolean? = null,
    val dualZone: Boolean? = null,
    val maxAc: Boolean? = null,
    val maxDefrost: Boolean? = null,
    val airCleaner: Boolean? = null,
    val frontDefroster: Boolean? = null,
    val rearDefroster: Boolean? = null
)

internal data class UserMode(
    val id: String,
    val name: String,
    val settings: ModeSettings = ModeSettings(),
    val accentColor: Int = 0xFF7298C8.toInt(),
    val profileImageUri: String? = null
)

internal data class ModeCapabilities(
    val loading: Boolean = false,
    val driverSeatHeat: ValueBoundsInt? = null,
    val passengerSeatHeat: ValueBoundsInt? = null,
    val driverSeatVentilation: ValueBoundsInt? = null,
    val passengerSeatVentilation: ValueBoundsInt? = null,
    val steeringWheelHeat: ValueBoundsInt? = null,
    val targetTemperatureC: ValueBoundsFloat? = null,
    val passengerTargetTemperatureC: ValueBoundsFloat? = null,
    val fanSpeed: ValueBoundsInt? = null,
    val passengerFanSpeed: ValueBoundsInt? = null,
    val fanDirections: List<String>? = null,
    val passengerFanDirections: List<String>? = null,
    val hvacPower: Boolean? = null,
    val hvacAuto: Boolean? = null,
    val airConditioning: Boolean? = null,
    val airRecirculation: Boolean? = null,
    val autoRecirculation: Boolean? = null,
    val dualZone: Boolean? = null,
    val maxAc: Boolean? = null,
    val maxDefrost: Boolean? = null,
    val airCleaner: Boolean? = null,
    val frontDefroster: Boolean? = null,
    val rearDefroster: Boolean? = null
)

private const val MODE_PREFS = "user_modes"
private const val MODES_KEY = "modes"
private const val SELECTED_MODE_KEY = "selected_mode"

internal fun loadUserModes(context: Context): List<UserMode> {
    val prefs = context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE)
    val modes = runCatching {
        val array = JSONArray(prefs.getString(MODES_KEY, "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).toUserMode() }
    }.getOrDefault(emptyList())
    return if (modes.any { it.id == "basic" }) modes else listOf(UserMode("basic", "Basic")) + modes
}

internal fun persistUserModes(context: Context, modes: List<UserMode>) {
    val array = JSONArray()
    modes.forEach { mode -> array.put(mode.toJson()) }
    context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE).edit().putString(MODES_KEY, array.toString()).apply()
}

internal fun loadSelectedModeId(context: Context): String =
    context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE).getString(SELECTED_MODE_KEY, "basic") ?: "basic"

internal fun persistSelectedModeId(context: Context, id: String) {
    context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED_MODE_KEY, id).apply()
}

private fun UserMode.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("accentColor", accentColor)
    putNullable("profileImageUri", profileImageUri)
    put("settings", settings.toJson())
}

private fun JSONObject.toUserMode(): UserMode {
    val value = optJSONObject("settings") ?: JSONObject()
    return UserMode(
        id = optString("id"),
        name = optString("name", "Mode"),
        accentColor = softenModeColor(optInt("accentColor", 0xFF7298C8.toInt())),
        profileImageUri = optStringOrNull("profileImageUri"),
        settings = ModeSettings(
            driverSeatHeat = value.optIntOrNull("driverSeatHeat"),
            passengerSeatHeat = value.optIntOrNull("passengerSeatHeat"),
            driverSeatVentilation = value.optIntOrNull("driverSeatVentilation"),
            passengerSeatVentilation = value.optIntOrNull("passengerSeatVentilation"),
            steeringWheelHeat = value.optIntOrNull("steeringWheelHeat"),
            targetTemperatureC = value.optFloatOrNull("targetTemperatureC"),
            passengerTargetTemperatureC = value.optFloatOrNull("passengerTargetTemperatureC"),
            fanSpeed = value.optIntOrNull("fanSpeed"),
            passengerFanSpeed = value.optIntOrNull("passengerFanSpeed"),
            fanDirection = value.optStringOrNull("fanDirection"),
            passengerFanDirection = value.optStringOrNull("passengerFanDirection"),
            hvacPower = value.optBooleanOrNull("hvacPower"),
            hvacAuto = value.optBooleanOrNull("hvacAuto"),
            airConditioning = value.optBooleanOrNull("airConditioning"),
            airRecirculation = value.optBooleanOrNull("airRecirculation"),
            autoRecirculation = value.optBooleanOrNull("autoRecirculation"),
            dualZone = value.optBooleanOrNull("dualZone"),
            maxAc = value.optBooleanOrNull("maxAc"),
            maxDefrost = value.optBooleanOrNull("maxDefrost"),
            airCleaner = value.optBooleanOrNull("airCleaner"),
            frontDefroster = value.optBooleanOrNull("frontDefroster"),
            rearDefroster = value.optBooleanOrNull("rearDefroster")
        )
    )
}

private fun ModeSettings.toJson(): JSONObject = JSONObject().apply {
    putNullable("driverSeatHeat", driverSeatHeat)
    putNullable("passengerSeatHeat", passengerSeatHeat)
    putNullable("driverSeatVentilation", driverSeatVentilation)
    putNullable("passengerSeatVentilation", passengerSeatVentilation)
    putNullable("steeringWheelHeat", steeringWheelHeat)
    putNullable("targetTemperatureC", targetTemperatureC)
    putNullable("passengerTargetTemperatureC", passengerTargetTemperatureC)
    putNullable("fanSpeed", fanSpeed)
    putNullable("passengerFanSpeed", passengerFanSpeed)
    putNullable("fanDirection", fanDirection)
    putNullable("passengerFanDirection", passengerFanDirection)
    putNullable("hvacPower", hvacPower)
    putNullable("hvacAuto", hvacAuto)
    putNullable("airConditioning", airConditioning)
    putNullable("airRecirculation", airRecirculation)
    putNullable("autoRecirculation", autoRecirculation)
    putNullable("dualZone", dualZone)
    putNullable("maxAc", maxAc)
    putNullable("maxDefrost", maxDefrost)
    putNullable("airCleaner", airCleaner)
    putNullable("frontDefroster", frontDefroster)
    putNullable("rearDefroster", rearDefroster)
}

private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
private fun softenModeColor(color: Int): Int = when (color) {
    0xFF126BFF.toInt() -> 0xFF7298C8.toInt()
    0xFF00A6A6.toInt() -> 0xFF72A8A2.toInt()
    0xFF2E8B57.toInt() -> 0xFF86A68D.toInt()
    0xFFFF8A00.toInt() -> 0xFFD3A66E.toInt()
    0xFFE34F4F.toInt() -> 0xFFC7837D.toInt()
    0xFF607D8B.toInt() -> 0xFF8999A8.toInt()
    else -> color
}
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
private fun JSONObject.optFloatOrNull(key: String): Float? = if (isNull(key)) null else optDouble(key).toFloat()
private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (isNull(key)) null else optBoolean(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
