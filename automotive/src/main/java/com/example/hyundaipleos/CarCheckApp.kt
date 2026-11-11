package com.example.hyundaipleos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap

private val Ink = Color(0xFF111317)
private val Muted = Color(0xFF626A73)
private val Accent = Color(0xFF126BFF)
private val Line = Color(0xFFD9DDE2)
private val Tile = Color.White
private val Background = Color(0xFFF1F3F5)

@Composable
fun CarCheckApp() {
    val context = LocalContext.current
    val controller = remember(context) { VehicleDataController(context) }
    val data = controller.state
    val capabilities = controller.modeCapabilities
    val language = LocalConfiguration.current.locales[0].language
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    // Keep one of the four home tiles available for the add-mode action.
    var modes by remember(context) { mutableStateOf(loadUserModes(context).take(3)) }
    var selectedModeId by remember(context) { mutableStateOf(loadSelectedModeId(context)) }
    var page by remember { mutableStateOf("home") }
    var editingModeId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingSettings by remember { mutableStateOf(ModeSettings()) }
    var editingAccentColor by remember { mutableStateOf(0xFF7298C8.toInt()) }
    var editingProfileImageUri by remember { mutableStateOf<String?>(null) }
    var pickingModeToEdit by remember { mutableStateOf(false) }
    var showNameError by remember { mutableStateOf(false) }
    val profileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            editingProfileImageUri = uri.toString()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        controller.setGpsPermission(permissions.values.any { it })
    }

    fun selectMode(mode: UserMode) {
        selectedModeId = mode.id
        persistSelectedModeId(context, mode.id)
        controller.applyMode(mode)
    }

    fun openModeEditor(mode: UserMode?) {
        editingModeId = mode?.id
        editingName = mode?.name ?: ""
        editingSettings = mode?.settings ?: ModeSettings()
        editingAccentColor = mode?.accentColor ?: 0xFF7298C8.toInt()
        editingProfileImageUri = mode?.profileImageUri
        showNameError = false
        page = "mode_editor"
        controller.loadModeCapabilities()
    }

    fun saveMode() {
        val name = editingName.trim()
        if (name.isEmpty()) {
            showNameError = true
            return
        }
        val isNew = editingModeId == null
        if (isNew && modes.size >= 3) return
        val id = editingModeId ?: java.util.UUID.randomUUID().toString()
        val saved = UserMode(id, name, editingSettings, editingAccentColor, editingProfileImageUri)
        val updated = if (isNew) modes + saved else modes.map { if (it.id == id) saved else it }
        modes = updated.take(3)
        persistUserModes(context, modes)
        selectMode(saved)
        page = "home"
    }

    fun deleteMode() {
        val id = editingModeId ?: return
        modes = modes.filterNot { it.id == id }
        persistUserModes(context, modes)
        if (selectedModeId == id) {
            val basic = modes.firstOrNull { it.id == "basic" } ?: UserMode("basic", "Basic")
            selectedModeId = basic.id
            persistSelectedModeId(context, basic.id)
            controller.applyMode(basic)
        }
        page = "home"
    }

    DisposableEffect(controller) {
        controller.start()
        modes.firstOrNull { it.id == selectedModeId }?.let(controller::applyMode)
        onDispose { controller.release() }
    }
    LaunchedEffect(controller) {
        val hasLocationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else controller.setGpsPermission(true)
    }

    if (page == "home") {
        HomePage(
            modes = modes,
            selectedModeId = selectedModeId,
            pickingModeToEdit = pickingModeToEdit,
            healthData = data,
            language = language,
            onSelectMode = ::selectMode,
            onAddMode = { openModeEditor(null) },
            onStartEditMode = { pickingModeToEdit = true },
            onCancelEditMode = { pickingModeToEdit = false },
            onEditMode = { mode -> pickingModeToEdit = false; openModeEditor(mode) },
            onOpenCarHealth = { page = "health" }
        )
    } else if (page == "mode_editor") {
        Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 32.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t(if (editingModeId == null) "CREATE A MODE" else "EDIT MODE", if (editingModeId == null) "CRÉER UN MODE" else "MODIFIER LE MODE"), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(t("Configure available vehicle comfort controls", "Configurez les commandes de confort disponibles"), color = Muted, fontSize = 18.sp)
                }
                IconActionButton(ActionGlyph.BACK, t("BACK", "RETOUR"), Ink) { page = "home" }
            }
            Spacer(Modifier.height(14.dp))
            ModeEditorPage(
                name = editingName,
                settings = editingSettings,
                capabilities = capabilities,
                language = language,
                showNameError = showNameError,
                isBasic = editingModeId == "basic",
                accentColor = editingAccentColor,
                profileImageUri = editingProfileImageUri,
                onPickProfileImage = { profileImagePicker.launch(arrayOf("image/*")) },
                onClearProfileImage = { editingProfileImageUri = null },
                onColorChange = { editingAccentColor = it },
                onNameChange = { editingName = it; showNameError = false },
                onSettingsChange = { editingSettings = it },
                onSave = ::saveMode,
                onCancel = { page = "home" },
                onDelete = ::deleteMode
            )
        }
    } else {
        Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 32.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (page) {
                            "incidents" -> t("INCIDENTS", "INCIDENTS")
                            "scans" -> t("SCAN HISTORY", "HISTORIQUE DES SCANS")
                            else -> t("VEHICLE HEALTH", "ÉTAT DU VÉHICULE")
                        }, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp
                    )
                    Text(
                        when (page) {
                            "incidents" -> t("GPS-tagged vehicle alerts", "Alertes du véhicule avec position GPS")
                            "scans" -> t("Saved vehicle checks", "Contrôles enregistrés")
                            else -> t("Live vehicle status", "État du véhicule en direct")
                        }, color = Muted, fontSize = 20.sp
                    )
                }
                if (page == "health") {
                    IconActionButton(ActionGlyph.BACK, t("HOME", "ACCUEIL"), Ink) { page = "home" }
                    Spacer(Modifier.width(8.dp))
                    IconActionButton(ActionGlyph.REFRESH, if (data.refreshing) t("READING…", "LECTURE…") else t("REFRESH", "ACTUALISER"), Ink, enabled = !data.refreshing) { controller.refresh() }
                    Spacer(Modifier.width(8.dp))
                    IconActionButton(ActionGlyph.SCANS, t("SCANS", "SCANS"), Ink) { page = "scans" }
                    Spacer(Modifier.width(8.dp))
                    IconActionButton(ActionGlyph.INCIDENTS, t("INCIDENTS", "INCIDENTS"), Ink) { page = "incidents" }
                } else {
                    IconActionButton(ActionGlyph.BACK, t("BACK", "RETOUR"), Ink) { page = "health" }
                }
            }
            when (page) {
                "incidents" -> IncidentPage(data.incidents, language) { incident -> openIncidentMap(context, incident) }
                "scans" -> HistoryPage(data.scans, language)
                else -> DashboardPage(data, language)
            }
        }
    }
}

@Composable
private fun HomePage(
    modes: List<UserMode>,
    selectedModeId: String,
    pickingModeToEdit: Boolean,
    healthData: VehicleDashboardState,
    language: String,
    onSelectMode: (UserMode) -> Unit,
    onAddMode: () -> Unit,
    onStartEditMode: () -> Unit,
    onCancelEditMode: () -> Unit,
    onEditMode: (UserMode) -> Unit,
    onOpenCarHealth: () -> Unit
) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    Row(Modifier.fillMaxSize().background(Background).padding(28.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(Modifier.weight(1.15f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("MODES", "MODES"), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    if (pickingModeToEdit) Text(t("Choose a mode to edit", "Choisissez un mode à modifier"), color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = if (pickingModeToEdit) onCancelEditMode else onStartEditMode) {
                    Text(if (pickingModeToEdit) t("CANCEL", "ANNULER") else t("EDIT MODES", "MODIFIER LES MODES"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (col in 0..1) {
                            val index = row * 2 + col
                            val mode = modes.getOrNull(index)
                            if (mode == null) {
                                AddModeTile(Modifier.weight(1f).fillMaxHeight(), t("ADD MODE", "AJOUTER UN MODE"), onAddMode)
                            } else {
                                UserModeTile(
                                    mode = mode,
                                    selected = mode.id == selectedModeId,
                                    choosingToEdit = pickingModeToEdit,
                                    language = language,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { if (pickingModeToEdit) onEditMode(mode) else onSelectMode(mode) }
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
        Column(Modifier.weight(.85f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("HEALTH CHECK", "BILAN DE SANTÉ"), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            HealthSummaryCard(healthData, language, Modifier.fillMaxWidth().weight(1f).padding(bottom = 2.dp))
            Row(Modifier.fillMaxWidth().height(162.dp).background(Tile).clickable(onClick = onOpenCarHealth).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⌁", color = Accent, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Text(t("Details", "Détails"), color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("›", color = Accent, fontSize = 46.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HealthSummaryCard(data: VehicleDashboardState, language: String, modifier: Modifier = Modifier) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    val warnings = data.activeWarnings
    val warningSummary = when {
        warnings == null -> t("Waiting for vehicle data", "En attente des données du véhicule")
        warnings.isEmpty() -> t("No active warnings", "Aucune alerte active")
        language == "fr" -> if (warnings.size == 1) "1 alerte active" else "${warnings.size} alertes actives"
        language == "ko" -> "${warnings.size}개 활성 경고"
        else -> if (warnings.size == 1) "1 active warning" else "${warnings.size} active warnings"
    }
    Column(modifier.background(Tile)) {
        HealthSummaryRow(
            Modifier.weight(1f),
            icon = if (warnings == null) "…" else if (warnings.isEmpty()) "✓" else "!",
            title = t("Vehicle warnings", "Alertes du véhicule"),
            value = warningSummary,
            selected = warnings?.isNotEmpty() == true,
            valueColor = when {
                warnings == null -> Muted
                warnings.isEmpty() -> Color(0xFF2E7D32)
                else -> Color(0xFFB3261E)
            }
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        HealthSummaryRow(Modifier.weight(1f), "↗", t("Distance", "Distance"), data.odometerKm?.let { "${formatNumber(it)} km" } ?: "—")
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        HealthSummaryRow(Modifier.weight(1f), "⌁", t("Range", "Autonomie"), data.rangeKm?.let { "${formatNumber(it)} km" } ?: "—")
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        HealthSummaryRow(Modifier.weight(1f), "▣", t("Battery", "Batterie"), data.batteryPercent?.let { "$it%" } ?: "—")
    }
}

@Composable
private fun HealthSummaryRow(
    modifier: Modifier,
    icon: String,
    title: String,
    value: String,
    selected: Boolean = false,
    valueColor: Color = Muted
) {
    Column(
        modifier.fillMaxWidth().background(if (selected) Color(0xFFECEDEF) else Tile).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(icon, color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text(value, color = if (selected) Accent else valueColor, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UserModeTile(mode: UserMode, selected: Boolean, choosingToEdit: Boolean, language: String, modifier: Modifier, onClick: () -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    val accent = Color(mode.accentColor)
    Column(
        modifier.background(if (selected) accent.copy(alpha = .12f) else Tile)
            .border(if (selected || choosingToEdit) 3.dp else 1.dp, accent.copy(alpha = if (selected || choosingToEdit) 1f else .28f))
            .clickable(onClick = onClick).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModeProfilePhoto(mode.profileImageUri, Modifier.size(76.dp), accent)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (mode.id == "basic") t("BASIC", "BASIQUE") else mode.name, color = Ink, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(if (choosingToEdit) t("TAP TO EDIT THIS MODE", "APPUYER POUR MODIFIER") else if (selected) t("SELECTED", "SÉLECTIONNÉ") else t("TAP TO APPLY", "APPUYER POUR ACTIVER"), color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(accent))
    }
}

@Composable
private fun AddModeTile(modifier: Modifier, label: String, onClick: () -> Unit) {
    Column(modifier.background(Tile).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("+", color = Accent, fontSize = 68.sp, fontWeight = FontWeight.Light, lineHeight = 74.sp)
        Text(label, color = Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeProfilePhoto(uri: String?, modifier: Modifier, accent: Color) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(CircleShape).background(accent.copy(alpha = .14f)).border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image!!, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.size(30.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arm = size.minDimension * .48f
                val notch = size.minDimension * .12f
                val star = Path().apply {
                    moveTo(cx, cy - arm)
                    lineTo(cx + notch, cy - notch)
                    lineTo(cx + arm, cy)
                    lineTo(cx + notch, cy + notch)
                    lineTo(cx, cy + arm)
                    lineTo(cx - notch, cy + notch)
                    lineTo(cx - arm, cy)
                    lineTo(cx - notch, cy - notch)
                    close()
                }
                drawPath(star, accent)
            }
        }
    }
}

@Composable
private fun ModeEditorPage(
    name: String,
    settings: ModeSettings,
    capabilities: ModeCapabilities,
    language: String,
    showNameError: Boolean,
    isBasic: Boolean,
    accentColor: Int,
    profileImageUri: String?,
    onPickProfileImage: () -> Unit,
    onClearProfileImage: () -> Unit,
    onColorChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onSettingsChange: (ModeSettings) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    val accent = Color(accentColor)
    val palette = listOf(0xFF7298C8.toInt(), 0xFF72A8A2.toInt(), 0xFF86A68D.toInt(), 0xFFD3A66E.toInt(), 0xFFC7837D.toInt(), 0xFF8999A8.toInt())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Tile).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeProfilePhoto(profileImageUri, Modifier.size(66.dp).clickable(onClick = onPickProfileImage).semantics { contentDescription = t("Change profile photo", "Changer la photo du mode") }, accent)
            OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(t("Mode name", "Nom du mode"), fontSize = 19.sp) }, singleLine = true, isError = showNameError, supportingText = if (showNameError) ({ Text(t("Enter a name", "Saisissez un nom")) }) else null, modifier = Modifier.weight(1.1f))
            if (profileImageUri != null) TextButton(onClick = onClearProfileImage) { Text(t("REMOVE PHOTO", "RETIRER LA PHOTO"), fontSize = 16.sp) }
            Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                palette.forEach { color ->
                    val isSelected = color == accentColor
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(color)).then(if (isSelected) Modifier.border(3.dp, Ink, CircleShape) else Modifier).clickable { onColorChange(color) }.semantics { contentDescription = "Mode color" })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFB8BEC5)))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeCategoryPanel("♨", t("SEATS & STEERING", "SIÈGES ET VOLANT")) {
                ModeItemPair(
                    left = { IntModeSetting(t("Driver seat heat", "Chauffage siège conducteur"), settings.driverSeatHeat, capabilities.driverSeatHeat, capabilities.loading, language) { onSettingsChange(settings.copy(driverSeatHeat = it)) } },
                    right = { IntModeSetting(t("Passenger seat heat", "Chauffage siège passager"), settings.passengerSeatHeat, capabilities.passengerSeatHeat, capabilities.loading, language) { onSettingsChange(settings.copy(passengerSeatHeat = it)) } }
                )
                ModeItemPair(
                    left = { IntModeSetting(t("Driver seat ventilation", "Ventilation siège conducteur"), settings.driverSeatVentilation, capabilities.driverSeatVentilation, capabilities.loading, language) { onSettingsChange(settings.copy(driverSeatVentilation = it)) } },
                    right = { IntModeSetting(t("Passenger seat ventilation", "Ventilation siège passager"), settings.passengerSeatVentilation, capabilities.passengerSeatVentilation, capabilities.loading, language) { onSettingsChange(settings.copy(passengerSeatVentilation = it)) } }
                )
                ModeItemPair(
                    left = { IntModeSetting(t("Steering wheel heat", "Chauffage du volant"), settings.steeringWheelHeat, capabilities.steeringWheelHeat, capabilities.loading, language) { onSettingsChange(settings.copy(steeringWheelHeat = it)) } },
                    right = {},
                    showBottomDivider = false
                )
            }
            ModeCategoryPanel("❄", t("CLIMATE & AIRFLOW", "CLIMATISATION ET AIR")) {
                ModeItemPair(
                    left = { FloatModeSetting(t("Driver temperature (°C)", "Température conducteur (°C)"), settings.targetTemperatureC, capabilities.targetTemperatureC, capabilities.loading, language) { onSettingsChange(settings.copy(targetTemperatureC = it)) } },
                    right = { FloatModeSetting(t("Passenger temperature (°C)", "Température passager (°C)"), settings.passengerTargetTemperatureC, capabilities.passengerTargetTemperatureC, capabilities.loading, language) { onSettingsChange(settings.copy(passengerTargetTemperatureC = it)) } }
                )
                ModeItemPair(
                    left = { IntModeSetting(t("Fan speed", "Vitesse de ventilation"), settings.fanSpeed, capabilities.fanSpeed, capabilities.loading, language) { onSettingsChange(settings.copy(fanSpeed = it)) } },
                    right = { IntModeSetting(t("Passenger fan", "Ventilation passager"), settings.passengerFanSpeed, capabilities.passengerFanSpeed, capabilities.loading, language) { onSettingsChange(settings.copy(passengerFanSpeed = it)) } }
                )
                ModeItemPair(
                    left = { FanDirectionModeSetting(t("Air direction", "Direction de l’air"), settings.fanDirection, capabilities.fanDirections, capabilities.loading, language) { onSettingsChange(settings.copy(fanDirection = it)) } },
                    right = { FanDirectionModeSetting(t("Passenger air direction", "Direction d’air passager"), settings.passengerFanDirection, capabilities.passengerFanDirections, capabilities.loading, language) { onSettingsChange(settings.copy(passengerFanDirection = it)) } },
                    showBottomDivider = false
                )
            }
            ModeCategoryPanel("◉", t("CLIMATE CONTROL", "COMMANDES CLIMATISATION")) {
                ModeItemPair(
                    left = { BooleanModeSetting(t("Climate power", "Climatisation"), settings.hvacPower, capabilities.hvacPower, capabilities.loading, language) { onSettingsChange(settings.copy(hvacPower = it)) } },
                    right = { BooleanModeSetting(t("Automatic climate", "Climatisation automatique"), settings.hvacAuto, capabilities.hvacAuto, capabilities.loading, language) { onSettingsChange(settings.copy(hvacAuto = it)) } }
                )
                ModeItemPair(
                    left = { BooleanModeSetting(t("A/C", "A/C"), settings.airConditioning, capabilities.airConditioning, capabilities.loading, language) { onSettingsChange(settings.copy(airConditioning = it)) } },
                    right = { BooleanModeSetting(t("Air recirculation", "Recyclage de l’air"), settings.airRecirculation, capabilities.airRecirculation, capabilities.loading, language) { onSettingsChange(settings.copy(airRecirculation = it)) } }
                )
                ModeItemPair(
                    left = { BooleanModeSetting(t("Auto recirculation", "Recyclage automatique"), settings.autoRecirculation, capabilities.autoRecirculation, capabilities.loading, language) { onSettingsChange(settings.copy(autoRecirculation = it)) } },
                    right = {},
                    showBottomDivider = false
                )
            }
            ModeCategoryPanel("⌁", t("AIR & DEFROST", "AIR ET DÉGIVRAGE")) {
                ModeItemPair(
                    left = { BooleanModeSetting(t("Dual-zone sync", "Synchronisation des zones"), settings.dualZone, capabilities.dualZone, capabilities.loading, language) { onSettingsChange(settings.copy(dualZone = it)) } },
                    right = { BooleanModeSetting(t("Max A/C", "Climatisation maximale"), settings.maxAc, capabilities.maxAc, capabilities.loading, language) { onSettingsChange(settings.copy(maxAc = it)) } }
                )
                ModeItemPair(
                    left = { BooleanModeSetting(t("Max defrost", "Désembuage maximal"), settings.maxDefrost, capabilities.maxDefrost, capabilities.loading, language) { onSettingsChange(settings.copy(maxDefrost = it)) } },
                    right = { BooleanModeSetting(t("Air cleaner", "Purificateur d’air"), settings.airCleaner, capabilities.airCleaner, capabilities.loading, language) { onSettingsChange(settings.copy(airCleaner = it)) } }
                )
                ModeItemPair(
                    left = { BooleanModeSetting(t("Front defroster", "Désembuage pare-brise"), settings.frontDefroster, capabilities.frontDefroster, capabilities.loading, language) { onSettingsChange(settings.copy(frontDefroster = it)) } },
                    right = { BooleanModeSetting(t("Rear defroster", "Dégivrage lunette arrière"), settings.rearDefroster, capabilities.rearDefroster, capabilities.loading, language) { onSettingsChange(settings.copy(rearDefroster = it)) } },
                    showBottomDivider = false
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(72.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onSave, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                    Text(t("SAVE", "ENREGISTRER"), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color(0xFF2E7D32), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Box(Modifier.weight(1f).height(72.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                    Text(t("CANCEL", "ANNULER"), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!isBasic) Box(Modifier.weight(1f).height(72.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                    Text(t("DELETE", "SUPPRIMER"), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color(0xFFB3261E), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)) {
        Text(icon, color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ModeCategoryPanel(icon: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Tile).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CategoryHeader(icon, title)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        content()
    }
}

@Composable
private fun ModeItemPair(left: @Composable () -> Unit, right: @Composable () -> Unit, showBottomDivider: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp), contentAlignment = Alignment.CenterStart) { left() }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp), contentAlignment = Alignment.CenterStart) { right() }
        }
        if (showBottomDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun IntModeSetting(label: String, value: Int?, bounds: ValueBoundsInt?, loading: Boolean, language: String, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    Column(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (bounds != null) Switch(checked = value != null, onCheckedChange = { enabled -> onChange(if (enabled) value ?: ((bounds.min + bounds.max + 1) / 2) else null) })
        }
        when {
            value != null -> Text("${t("Level", "Niveau")} $value", color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
            bounds == null && !loading -> Text(t("Not supported by this vehicle", "Non pris en charge par ce véhicule"), color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
        }
        if (bounds != null && value != null && bounds.min < bounds.max) {
            val steps = (bounds.max - bounds.min - 1).coerceIn(0, 50)
            Slider(value = value.toFloat().coerceIn(bounds.min.toFloat(), bounds.max.toFloat()), onValueChange = { onChange(it.toInt()) }, valueRange = bounds.min.toFloat()..bounds.max.toFloat(), steps = steps)
        }
    }
}

@Composable
private fun FloatModeSetting(label: String, value: Float?, bounds: ValueBoundsFloat?, loading: Boolean, language: String, modifier: Modifier = Modifier, onChange: (Float?) -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    Column(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (bounds != null) Switch(checked = value != null, onCheckedChange = { enabled -> onChange(if (enabled) value ?: ((bounds.min + bounds.max) / 2f) else null) })
        }
        when {
            value != null -> Text("${"%.1f".format(Locale.US, value)} °C", color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
            bounds == null && !loading -> Text(t("Not supported by this vehicle", "Non pris en charge par ce véhicule"), color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
        }
        if (bounds != null && value != null && bounds.min < bounds.max) {
            Slider(value = value.coerceIn(bounds.min, bounds.max), onValueChange = onChange, valueRange = bounds.min..bounds.max)
        }
    }
}

@Composable
private fun FanDirectionModeSetting(label: String, value: String?, options: List<String>?, loading: Boolean, language: String, modifier: Modifier = Modifier, onChange: (String?) -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (options.isNullOrEmpty()) {
            Text(label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
            if (!loading) Text(t("Not supported by this vehicle", "Non pris en charge par ce véhicule"), color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(value?.let(::fanDirectionLabel) ?: "▼", fontSize = 18.sp) }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(t("Clear", "Effacer")) }, onClick = { onChange(null); expanded = false })
                options.forEach { option -> DropdownMenuItem(text = { Text(fanDirectionLabel(option)) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}

private fun fanDirectionLabel(value: String): String = when (value) {
    "FACE" -> "Face"
    "FLOOR" -> "Floor"
    "FACE_AND_FLOOR" -> "Face + floor"
    "DEFROST" -> "Defrost"
    "FACE_AND_DEFROST" -> "Face + defrost"
    "DEFROST_AND_FLOOR" -> "Defrost + floor"
    "FACE_AND_FLOOR_AND_DEFROST" -> "Face + floor + defrost"
    else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun BooleanModeSetting(label: String, value: Boolean?, supported: Boolean?, loading: Boolean, language: String, modifier: Modifier = Modifier, onChange: (Boolean?) -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    Column(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (supported == true) {
                if (value != null) TextButton(onClick = { onChange(null) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text(t("CLEAR", "EFFACER"), fontSize = 14.sp) }
                Switch(checked = value == true, onCheckedChange = { onChange(it) })
            }
        }
        when {
            supported != true && !loading -> Text(t("Not supported by this vehicle", "Non pris en charge par ce véhicule"), color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
            value != null -> Text(if (value) t("On", "Activé") else t("Off", "Désactivé"), color = Muted, fontSize = 18.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun DashboardPage(data: VehicleDashboardState, language: String) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().weight(.75f)) {
            MetricTile(Modifier.weight(1f), t("DISTANCE", "DISTANCE"), data.odometerKm?.let { formatNumber(it) } ?: "—", "km", if (data.odometerKm == null) t("Unavailable", "Indisponible") else t("Total distance", "Distance totale"))
            TileDivider()
            MetricTile(Modifier.weight(1f), t("EST. RANGE", "AUTONOMIE EST."), data.rangeKm?.let { formatNumber(it) } ?: "—", "km", if (data.rangeKm == null) t("Unavailable", "Indisponible") else t("Remaining distance", "Distance restante"))
            TileDivider()
            MetricTile(Modifier.weight(1f), t("BATTERY", "BATTERIE"), data.batteryPercent?.toString() ?: "—", if (data.batteryPercent != null) "%" else "", if (data.batteryPercent == null) t("Unavailable", "Indisponible") else t("State of charge", "Niveau de charge"))
        }
        Row(Modifier.fillMaxWidth().weight(1.4f)) {
            Column(Modifier.weight(1.25f).fillMaxHeight().background(Tile).padding(22.dp)) {
                Text(t("TIRE PRESSURE", "PRESSION DES PNEUS"), color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(t("Critical low-pressure threshold · kPa", "Seuil de pression très basse · kPa"), color = Muted, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(210.dp).fillMaxHeight()) {
                        TireTile("FL", t("FRONT LEFT", "AVANT GAUCHE"), data.tireThresholdsKpa["LEFT_FRONT"], Modifier.weight(1f), language)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        TireTile("RL", t("REAR LEFT", "ARRIÈRE GAUCHE"), data.tireThresholdsKpa["LEFT_REAR"], Modifier.weight(1f), language)
                    }
                    Spacer(Modifier.width(10.dp))
                    Image(
                        painter = painterResource(R.drawable.vehicle_top_view),
                        contentDescription = t("Vehicle top view", "Vue du dessus du véhicule"),
                        modifier = Modifier.width(64.dp).height(108.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.width(210.dp).fillMaxHeight()) {
                        TireTile("FR", t("FRONT RIGHT", "AVANT DROIT"), data.tireThresholdsKpa["RIGHT_FRONT"], Modifier.weight(1f), language)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        TireTile("RR", t("REAR RIGHT", "ARRIÈRE DROIT"), data.tireThresholdsKpa["RIGHT_REAR"], Modifier.weight(1f), language)
                    }
                }
            }
            TileDivider()
            Column(Modifier.weight(.75f).fillMaxHeight().background(Tile).padding(22.dp)) {
                Text(t("VEHICLE WARNINGS", "ALERTES DU VÉHICULE"), color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                when {
                    data.activeWarnings.isNullOrEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(Color(0xFF16865B), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(t("Nothing to report", "Rien à signaler"), color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.activeWarnings.take(5).forEach { warning ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).background(Color(0xFFD63A32), CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(localizedWarning(warning, language), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Spacer(Modifier.height(12.dp))
                Text(
                    data.updatedAt?.let {
                        if (language == "ko") "업데이트 ${formatTime(it, language)}"
                        else t("UPDATED ${formatTime(it, language)}", "MIS À JOUR ${formatTime(it, language)}")
                    } ?: t("WAITING FOR VEHICLE DATA", "EN ATTENTE DES DONNÉES DU VÉHICULE"),
                    color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp
                )
                Spacer(Modifier.height(8.dp))
                val gpsText = data.location?.let { "GPS  ${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}" }
                    ?: if (data.locationPermissionGranted) t("Waiting for GPS fix", "En attente du signal GPS") else t("GPS permission required", "Autorisation GPS requise")
                Text(gpsText, color = Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun IncidentPage(incidents: List<VehicleIncident>, language: String, onOpenMap: (VehicleIncident) -> Unit) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    if (incidents.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Tile).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⌖", color = Accent, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(t("No incidents recorded", "Aucun incident enregistré"), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t("New vehicle warnings will be saved with their time and GPS location.", "Les nouvelles alertes seront enregistrées avec leur date et leur position GPS."), color = Muted, fontSize = 20.sp)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Tile).verticalScroll(rememberScrollState())) {
            incidents.forEachIndexed { index, incident ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(incidentTitle(incident.warningType, language), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(formatTime(incident.timestamp, language), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (incident.latitude != null && incident.longitude != null) {
                            IconActionButton(ActionGlyph.MAP, t("VIEW MAP", "VOIR LA CARTE"), Ink) { onOpenMap(incident) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val locationText = if (incident.latitude != null && incident.longitude != null) {
                        "${t("GPS", "GPS")}: ${formatCoordinate(incident.latitude)}, ${formatCoordinate(incident.longitude)}"
                    } else t("GPS location unavailable", "Position GPS indisponible")
                    Text(locationText, color = Muted, fontSize = 18.sp)
                    incident.distanceKm?.let {
                        Text("${t("Distance at event", "Distance au moment de l’incident")}: ${formatNumber(it)} km", color = Muted, fontSize = 18.sp)
                    }
                }
                if (index < incidents.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

private fun incidentTitle(type: String, language: String): String = when (type) {
    "TPMS" -> localized(language, "TPMS warning — possible tire pressure loss", "Alerte TPMS — baisse de pression possible")
    else -> localizedWarning(type, language)
}

private fun openIncidentMap(context: android.content.Context, incident: VehicleIncident) {
    val latitude = incident.latitude ?: return
    val longitude = incident.longitude ?: return
    val query = Uri.encode(incident.warningType.replace('_', ' '))
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($query)")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

@Composable
private fun HistoryPage(scans: List<VehicleScan>, language: String) {
    val t: (String, String) -> String = { en, fr -> localized(language, en, fr) }
    if (scans.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Tile).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▤", color = Accent, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(t("No scans yet", "Aucun scan pour le moment"), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t("Use Refresh to save a vehicle reading.", "Utilisez Actualiser pour enregistrer un relevé du véhicule."), color = Muted, fontSize = 21.sp)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Tile).verticalScroll(rememberScrollState())) {
            scans.forEachIndexed { index, scan ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp)) {
                    Text(formatTime(scan.timestamp, language), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val odometer = scan.odometerKm?.let { "${formatNumber(it)} km" } ?: "—"
                    val range = scan.rangeKm?.let { "${formatNumber(it)} km" } ?: "—"
                    val battery = scan.batteryPercent?.let { "$it%" } ?: "—"
                    Text("${t("Distance", "Distance")}: $odometer   ·   ${t("Range", "Autonomie")}: $range   ·   ${t("Battery", "Batterie")}: $battery", color = Ink, fontSize = 19.sp)
                    val warningText = when {
                        scan.activeWarnings == null -> t("Warnings unavailable", "Alertes indisponibles")
                        scan.activeWarnings.isEmpty() -> t("No active warnings", "Aucune alerte active")
                        else -> scan.activeWarnings.joinToString { localizedWarning(it, language) }
                    }
                    Text("${t("Warnings", "Alertes")}: $warningText", color = Muted, fontSize = 17.sp)
                    val tireKeys = listOf("LEFT_FRONT", "RIGHT_FRONT", "LEFT_REAR", "RIGHT_REAR")
                    val tireNames = listOf("FL", "FR", "RL", "RR")
                    val tireSummary = tireKeys.mapIndexedNotNull { tireIndex, key ->
                        scan.tireThresholdsKpa[key]?.let { "${tireNames[tireIndex]} ${formatNumber(it)} kPa" }
                    }.joinToString(" · ").ifEmpty { "—" }
                    Text("${t("Low-pressure thresholds", "Seuils de pression basse")}: $tireSummary", color = Muted, fontSize = 17.sp)
                }
                if (index < scans.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

private enum class ActionGlyph { REFRESH, SCANS, INCIDENTS, BACK, MAP }

@Composable
private fun IconActionButton(glyph: ActionGlyph, description: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Canvas(
        Modifier.size(52.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        val tint = color.copy(alpha = if (enabled) 1f else .42f)
        val stroke = 2.2.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (glyph) {
            ActionGlyph.REFRESH -> {
                drawArc(tint, 40f, 282f, false, Offset(size.width * .22f, size.height * .22f), Size(size.width * .56f, size.height * .56f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(tint, Offset(size.width * .67f, size.height * .20f), Offset(size.width * .80f, size.height * .21f), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * .80f, size.height * .21f), Offset(size.width * .77f, size.height * .34f), stroke, cap = StrokeCap.Round)
            }
            ActionGlyph.SCANS -> {
                drawRoundRect(tint, Offset(size.width * .25f, size.height * .16f), Size(size.width * .50f, size.height * .68f), CornerRadius(size.width * .04f), style = Stroke(stroke))
                drawLine(tint, Offset(size.width * .36f, size.height * .38f), Offset(size.width * .64f, size.height * .38f), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * .36f, size.height * .51f), Offset(size.width * .64f, size.height * .51f), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * .36f, size.height * .64f), Offset(size.width * .57f, size.height * .64f), stroke, cap = StrokeCap.Round)
            }
            ActionGlyph.INCIDENTS, ActionGlyph.MAP -> {
                val pin = Path().apply {
                    moveTo(cx, size.height * .84f)
                    cubicTo(size.width * .43f, size.height * .66f, size.width * .25f, size.height * .48f, size.width * .25f, size.height * .38f)
                    cubicTo(size.width * .25f, size.height * .19f, size.width * .39f, size.height * .12f, cx, size.height * .12f)
                    cubicTo(size.width * .61f, size.height * .12f, size.width * .75f, size.height * .19f, size.width * .75f, size.height * .38f)
                    cubicTo(size.width * .75f, size.height * .48f, size.width * .57f, size.height * .66f, cx, size.height * .84f)
                    close()
                }
                drawPath(pin, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(tint, radius = stroke * 1.5f, center = Offset(cx, size.height * .38f))
            }
            ActionGlyph.BACK -> {
                drawLine(tint, Offset(size.width * .76f, cy), Offset(size.width * .27f, cy), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * .27f, cy), Offset(size.width * .46f, size.height * .30f), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * .27f, cy), Offset(size.width * .46f, size.height * .70f), stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, title: String, value: String, unit: String, caption: String) {
    Column(modifier.fillMaxHeight().background(Tile).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Ink, fontSize = 56.sp, fontWeight = FontWeight.Medium, lineHeight = 60.sp)
            if (unit.isNotEmpty()) Text(" $unit", color = Muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(caption, color = Muted, fontSize = 17.sp)
    }
}

@Composable
private fun TireTile(position: String, label: String, thresholdKpa: Float?, modifier: Modifier, language: String) {
    Row(modifier.width(210.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(position, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
        Column {
            Text(label, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
            Text(thresholdKpa?.let { "${formatNumber(it)} kPa" } ?: "—", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            if (thresholdKpa == null) Text(localized(language, "Unavailable", "Indisponible"), color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TileDivider() { Box(Modifier.width(1.dp).fillMaxHeight().background(Line)) }

private fun formatNumber(value: Float): String = String.format(Locale.US, "%,.0f", value)

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)

private fun formatTime(timestamp: Long, language: String): String {
    val locale = when (language) { "ko" -> Locale.KOREAN; "fr" -> Locale.FRANCE; else -> Locale.US }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(timestamp))
}

private val koreanTranslations = mapOf(
    "INCIDENTS" to "차량 이상 기록",
    "GPS-tagged vehicle alerts" to "GPS 위치가 기록된 차량 경고",
    "Alertes du véhicule avec position GPS" to "GPS 위치가 기록된 차량 경고",
    "SCAN HISTORY" to "스캔 기록",
    "HISTORIQUE DES SCANS" to "스캔 기록",
    "VEHICLE HEALTH" to "차량 상태",
    "ÉTAT DU VÉHICULE" to "차량 상태",
    "Saved vehicle checks" to "저장된 차량 점검 기록",
    "Contrôles enregistrés" to "저장된 차량 점검 기록",
    "Live vehicle status" to "실시간 차량 상태",
    "État du véhicule en direct" to "실시간 차량 상태",
    "BACK" to "뒤로",
    "RETOUR" to "뒤로",
    "READING…" to "조회 중…",
    "LECTURE…" to "조회 중…",
    "REFRESH" to "새로고침",
    "ACTUALISER" to "새로고침",
    "SCANS" to "스캔 기록",
    "HISTORY" to "기록",
    "HISTORIQUE" to "기록",
    "DISTANCE" to "누적 주행 거리",
    "Distance" to "주행 거리",
    "Unavailable" to "정보 없음",
    "Indisponible" to "정보 없음",
    "Total distance" to "총 주행 거리",
    "Distance totale" to "총 주행 거리",
    "EST. RANGE" to "예상 주행 가능 거리",
    "AUTONOMIE EST." to "예상 주행 가능 거리",
    "Remaining distance" to "남은 주행 거리",
    "Distance restante" to "남은 주행 거리",
    "BATTERY" to "배터리",
    "BATTERIE" to "배터리",
    "State of charge" to "충전량",
    "Niveau de charge" to "충전량",
    "TIRE PRESSURE" to "타이어 공기압",
    "PRESSION DES PNEUS" to "타이어 공기압",
    "Critical low-pressure threshold · kPa" to "타이어 저압 경고 기준 · kPa",
    "Seuil de pression très basse · kPa" to "타이어 저압 경고 기준 · kPa",
    "FRONT LEFT" to "앞 왼쪽",
    "AVANT GAUCHE" to "앞 왼쪽",
    "FRONT RIGHT" to "앞 오른쪽",
    "AVANT DROIT" to "앞 오른쪽",
    "REAR LEFT" to "뒤 왼쪽",
    "ARRIÈRE GAUCHE" to "뒤 왼쪽",
    "REAR RIGHT" to "뒤 오른쪽",
    "ARRIÈRE DROIT" to "뒤 오른쪽",
    "VEHICLE WARNINGS" to "차량 경고",
    "ALERTES DU VÉHICULE" to "차량 경고",
    "Nothing to report" to "이상 없음",
    "Rien à signaler" to "이상 없음",
    "No active warnings" to "현재 경고 없음",
    "Aucune alerte active" to "현재 경고 없음",
    "GPS" to "GPS",
    "VIEW MAP" to "지도 보기",
    "VOIR LA CARTE" to "지도 보기",
    "GPS location unavailable" to "GPS 위치를 사용할 수 없음",
    "Position GPS indisponible" to "GPS 위치를 사용할 수 없음",
    "GPS permission required" to "GPS 권한 필요",
    "Autorisation GPS requise" to "GPS 권한 필요",
    "Waiting for GPS fix" to "GPS 신호 수신 대기 중",
    "En attente du signal GPS" to "GPS 신호 수신 대기 중",
    "No incidents recorded" to "기록된 이상 항목이 없습니다",
    "Aucun incident enregistré" to "기록된 이상 항목이 없습니다",
    "New vehicle warnings will be saved with their time and GPS location." to "새 차량 경고는 발생 시간과 GPS 위치와 함께 저장됩니다.",
    "Les nouvelles alertes seront enregistrées avec leur date et leur position GPS." to "새 차량 경고는 발생 시간과 GPS 위치와 함께 저장됩니다.",
    "Distance at event" to "발생 당시 주행 거리",
    "Distance au moment de l’incident" to "발생 당시 주행 거리",
    "TPMS warning — possible tire pressure loss" to "TPMS 경고 — 타이어 공기압 저하 가능성",
    "Alerte TPMS — baisse de pression possible" to "TPMS 경고 — 타이어 공기압 저하 가능성",
    "Waiting for vehicle data" to "차량 데이터 대기 중",
    "Live vehicle check is not available yet" to "실시간 차량 점검 데이터를 아직 사용할 수 없습니다",
    "Le contrôle du véhicule est en attente" to "실시간 차량 점검 데이터를 아직 사용할 수 없습니다",
    "Vehicle systems are currently clear" to "현재 차량 시스템에 경고가 없습니다",
    "Aucun avertissement signalé pour le moment" to "현재 차량 시스템에 경고가 없습니다",
    "WAITING FOR VEHICLE DATA" to "차량 데이터 대기 중",
    "EN ATTENTE DES DONNÉES DU VÉHICULE" to "차량 데이터 대기 중",
    "No scans yet" to "저장된 스캔이 없습니다",
    "Aucun scan pour le moment" to "저장된 스캔이 없습니다",
    "Use Refresh to save a vehicle reading." to "새로고침을 눌러 차량 데이터를 기록하세요.",
    "Utilisez Actualiser pour enregistrer un relevé du véhicule." to "새로고침을 눌러 차량 데이터를 기록하세요.",
    "Kilométrage" to "주행 거리",
    "Range" to "주행 가능 거리",
    "Autonomie" to "주행 가능 거리",
    "Battery" to "배터리",
    "Batterie" to "배터리",
    "Warnings unavailable" to "경고 정보 없음",
    "Alertes indisponibles" to "경고 정보 없음",
    "Warnings" to "경고",
    "Alertes" to "경고",
    "Low-pressure thresholds" to "타이어 저압 기준",
    "Seuils de pression basse" to "타이어 저압 기준",
    "HOME" to "홈",
    "ACCUEIL" to "홈",
    "CREATE A MODE" to "모드 만들기",
    "CRÉER UN MODE" to "모드 만들기",
    "EDIT MODE" to "모드 편집",
    "MODIFIER LE MODE" to "모드 편집",
    "Configure available vehicle comfort controls" to "차량의 편의 기능을 설정하세요",
    "Configurez les commandes de confort disponibles" to "차량의 편의 기능을 설정하세요",
    "MODES" to "모드",
    "APPS" to "앱",
    "APPLICATIONS" to "앱",
    "HEALTH CHECK" to "차량 점검",
    "BILAN DE SANTÉ" to "차량 점검",
    "Details" to "상세 정보",
    "Détails" to "상세 정보",
    "ADD MODE" to "모드 추가",
    "AJOUTER UN MODE" to "모드 추가",
    "EDIT MODES" to "모드 편집",
    "MODIFIER LES MODES" to "모드 편집",
    "CANCEL" to "취소",
    "ANNULER" to "취소",
    "Choose a mode to edit" to "편집할 모드를 선택하세요",
    "Choisissez un mode à modifier" to "편집할 모드를 선택하세요",
    "TAP TO EDIT THIS MODE" to "이 모드를 편집하려면 누르세요",
    "APPUYER POUR MODIFIER" to "이 모드를 편집하려면 누르세요",
    "BASIC" to "기본",
    "BASIQUE" to "기본",
    "SELECTED" to "선택됨",
    "SÉLECTIONNÉ" to "선택됨",
    "TAP TO APPLY" to "눌러서 적용",
    "APPUYER POUR ACTIVER" to "눌러서 적용",
    "Car Health" to "차량 상태",
    "Santé du véhicule" to "차량 상태",
    "Vehicle status and incidents" to "차량 상태 및 이상 기록",
    "État et incidents du véhicule" to "차량 상태 및 이상 기록",
    "Vehicle top view" to "차량 위에서 보기",
    "Vue du dessus du véhicule" to "차량 위에서 보기",
    "Mode name" to "모드 이름",
    "Nom du mode" to "모드 이름",
    "CHOOSE PHOTO" to "사진 선택",
    "CHOISIR UNE PHOTO" to "사진 선택",
    "REMOVE PHOTO" to "사진 삭제",
    "RETIRER LA PHOTO" to "사진 삭제",
    "MODE COLOR" to "모드 색상",
    "COULEUR DU MODE" to "모드 색상",
    "SEATS & STEERING" to "시트 및 스티어링 휠",
    "CLIMATE & AIRFLOW" to "공조 및 송풍",
    "CLIMATE CONTROL" to "공조 제어",
    "COMMANDES CLIMATISATION" to "공조 제어",
    "AIR & DEFROST" to "송풍 및 성에 제거",
    "AIR ET DÉGIVRAGE" to "송풍 및 성에 제거",
    "Driver temperature (°C)" to "운전석 온도 (°C)",
    "Température conducteur (°C)" to "운전석 온도 (°C)",
    "Passenger temperature (°C)" to "동승석 온도 (°C)",
    "Température passager (°C)" to "동승석 온도 (°C)",
    "Passenger fan" to "동승석 송풍",
    "Ventilation passager" to "동승석 송풍",
    "Passenger air direction" to "동승석 송풍 방향",
    "Direction d’air passager" to "동승석 송풍 방향",
    "Air direction" to "송풍 방향",
    "Direction de l’air" to "송풍 방향",
    "Auto recirculation" to "자동 내기 순환",
    "Recyclage automatique" to "자동 내기 순환",
    "Dual-zone sync" to "듀얼 존 동기화",
    "Synchronisation des zones" to "듀얼 존 동기화",
    "Max A/C" to "최대 냉방",
    "Climatisation maximale" to "최대 냉방",
    "Front defroster" to "앞유리 성에 제거",
    "Désembuage pare-brise" to "앞유리 성에 제거",
    "Rear defroster" to "뒷유리 열선",
    "Dégivrage lunette arrière" to "뒷유리 열선",
    "Enter a name" to "이름을 입력하세요",
    "Saisissez un nom" to "이름을 입력하세요",
    "SEATS AND STEERING" to "시트 및 스티어링 휠",
    "SIÈGES ET VOLANT" to "시트 및 스티어링 휠",
    "Driver seat heat" to "운전석 열선",
    "Chauffage siège conducteur" to "운전석 열선",
    "Passenger seat heat" to "동승석 열선",
    "Chauffage siège passager" to "동승석 열선",
    "Driver seat ventilation" to "운전석 통풍",
    "Ventilation siège conducteur" to "운전석 통풍",
    "Passenger seat ventilation" to "동승석 통풍",
    "Ventilation siège passager" to "동승석 통풍",
    "Steering wheel heat" to "스티어링 휠 열선",
    "Chauffage du volant" to "스티어링 휠 열선",
    "CLIMATE" to "공조",
    "CLIMATISATION" to "공조",
    "Target temperature (°C)" to "목표 온도 (°C)",
    "Température cible (°C)" to "목표 온도 (°C)",
    "Fan speed" to "송풍 세기",
    "Vitesse de ventilation" to "송풍 세기",
    "Climate power" to "공조 전원",
    "Climatisation" to "공조 전원",
    "Automatic climate" to "자동 공조",
    "Climatisation automatique" to "자동 공조",
    "Air recirculation" to "내기 순환",
    "Recyclage de l’air" to "내기 순환",
    "Max defrost" to "최대 성에 제거",
    "Désembuage maximal" to "최대 성에 제거",
    "Air cleaner" to "공기 청정",
    "Purificateur d’air" to "공기 청정",
    "SAVE MODE" to "모드 저장",
    "ENREGISTRER LE MODE" to "모드 저장",
    "SAVE" to "저장",
    "ENREGISTRER" to "저장",
    "DELETE" to "삭제",
    "SUPPRIMER" to "삭제",
    "Checking vehicle support…" to "차량 지원 확인 중…",
    "Vérification de la compatibilité…" to "차량 지원 확인 중…",
    "Not supported by this vehicle" to "이 차량에서 지원하지 않음",
    "Non pris en charge par ce véhicule" to "이 차량에서 지원하지 않음",
    "Not set" to "설정 안 됨",
    "Non défini" to "설정 안 됨",
    "Level" to "단계",
    "Niveau" to "단계",
    "On" to "켜짐",
    "Activé" to "켜짐",
    "Off" to "꺼짐",
    "Désactivé" to "꺼짐",
    "CLEAR" to "초기화",
    "EFFACER" to "초기화"
)

private val koreanWarnings = mapOf(
    "PARKING_BRAKE" to "주차 브레이크",
    "ELECTRONIC_PARKING_BRAKE" to "전자식 주차 브레이크",
    "SEAT_BELT" to "안전벨트",
    "DOOR_OPEN" to "도어 열림",
    "ABS" to "ABS 브레이크",
    "MDPS" to "파워 스티어링",
    "TPMS" to "타이어 공기압 경고",
    "LOW_VOLT_BATTERY" to "저전압 배터리",
    "ESC" to "차체 자세 제어 장치",
    "ESC_OFF" to "차체 자세 제어 장치 꺼짐",
    "AIRBAG" to "에어백",
    "MASTER_WARNING" to "종합 경고",
    "ENGINE_OIL_PRESSURE" to "엔진 오일 압력",
    "ENGINE_TEMPERATURE" to "엔진 온도",
    "ENGINE" to "엔진 경고",
    "LOW_EV_BATTERY" to "전기차 배터리 부족",
    "LOW_FUEL" to "연료 부족",
    "LOW_FUEL_FCEV" to "연료 부족",
    "FCA" to "전방 충돌 방지 보조",
    "LKA" to "차로 유지 보조",
    "LFA" to "차로 주행 보조",
    "LCA_LEFT" to "왼쪽 차로 변경 보조",
    "LCA_RIGHT" to "오른쪽 차로 변경 보조",
    "ELK" to "긴급 차로 유지 보조",
    "ICC" to "스마트 크루즈 컨트롤",
    "TRUNK_OPEN" to "트렁크 열림",
    "LOW_WASHER_FLUID" to "워셔액 부족"
)

private fun localized(language: String, english: String, french: String): String = when (language) {
    "ko" -> koreanTranslations[english] ?: koreanTranslations[french] ?: english
    "fr" -> french
    else -> english
}

private fun localizedWarning(warning: String, language: String): String = when (language) {
    "ko" -> koreanWarnings[warning] ?: "차량 경고"
    "fr" -> warning.replace('_', ' ')
    else -> warning.replace('_', ' ')
}
