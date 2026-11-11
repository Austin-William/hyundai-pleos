package com.example.hyundaipleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

internal data class VehicleScan(
    val timestamp: Long,
    val odometerKm: Float?,
    val rangeKm: Float?,
    val batteryPercent: Int?,
    val tireThresholdsKpa: Map<String, Float>,
    val activeWarnings: List<String>?
)

internal data class GeoPoint(val latitude: Double, val longitude: Double, val timestamp: Long)

internal data class VehicleIncident(
    val timestamp: Long,
    val warningType: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKm: Float?,
    val priority: Int
)

internal data class VehicleDashboardState(
    val odometerKm: Float? = null,
    val rangeKm: Float? = null,
    val batteryPercent: Int? = null,
    val tireThresholdsKpa: Map<String, Float> = emptyMap(),
    val activeWarnings: List<String>? = null,
    val refreshing: Boolean = false,
    val updatedAt: Long? = null,
    val scans: List<VehicleScan> = emptyList(),
    val location: GeoPoint? = null,
    val locationPermissionGranted: Boolean = false,
    val incidents: List<VehicleIncident> = emptyList()
)

internal class VehicleDataController(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val preferences: SharedPreferences = context.getSharedPreferences("vehicle_scans", Context.MODE_PRIVATE)
    private val incidentPreferences: SharedPreferences = context.getSharedPreferences("vehicle_incidents", Context.MODE_PRIVATE)
    var state by mutableStateOf(VehicleDashboardState(scans = readHistory(), incidents = readIncidents()))
        private set
    var modeCapabilities by mutableStateOf(ModeCapabilities())
        private set
    private var started = false
    private var knownActiveWarnings: Set<String>? = incidentPreferences.getStringSet("active_warnings", null)?.toSet()
    private var locationListener: LocationListener? = null

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            value?.let { state = state.copy(odometerKm = it) }
        }

        override fun onFailed(e: Exception) = Unit
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            telltales?.let(::handleTelltales)
        }

        override fun onFailed(e: Exception) = Unit
    }

    fun start() {
        if (started) return
        started = true
        vehicle.initialize()
        runCatching { vehicle.getSafety().registerTelltales(telltaleListener) }
        runCatching { vehicle.getOdometer().registerDistanceDriven(distanceListener) }
        val fineGranted = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        setGpsPermission(fineGranted || coarseGranted)
        refresh()
    }

    fun release() {
        if (!started) return
        started = false
        runCatching { vehicle.getSafety().unregisterTelltales(telltaleListener) }
        runCatching { vehicle.getOdometer().unregisterDistanceDriven(distanceListener) }
        stopLocationUpdates()
        vehicle.release()
    }

    fun setGpsPermission(granted: Boolean) {
        state = state.copy(locationPermissionGranted = granted)
        if (granted && started) startLocationUpdates() else stopLocationUpdates()
    }

    fun loadModeCapabilities() {
        if (!started) return
        modeCapabilities = ModeCapabilities(loading = true)
        val remaining = AtomicInteger(16)
        fun finished() {
            if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false)
        }
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val passenger = SeatArea(SeatValue.ROW_1_RIGHT)

        runCatching {
            vehicle.getSeat().checkSeatTemperatureCapabilities(
                onComplete = { config, areas ->
                    val canSet = canWrite(config)
                    modeCapabilities = modeCapabilities.copy(
                        driverSeatHeat = areas[driver].takeIf { canSet }?.let { ValueBoundsInt(it.minValue, it.maxValue) },
                        passengerSeatHeat = areas[passenger].takeIf { canSet }?.let { ValueBoundsInt(it.minValue, it.maxValue) }
                    ); finished()
                }, onFailed = { finished() }
            )
        }.onFailure { finished() }
        runCatching {
            vehicle.getSeat().checkSeatVentilationCapabilities(
                onComplete = { config, areas ->
                    val canSet = canWrite(config)
                    modeCapabilities = modeCapabilities.copy(
                        driverSeatVentilation = areas[driver].takeIf { canSet }?.let { ValueBoundsInt(it.minValue, it.maxValue) },
                        passengerSeatVentilation = areas[passenger].takeIf { canSet }?.let { ValueBoundsInt(it.minValue, it.maxValue) }
                    ); finished()
                }, onFailed = { finished() }
            )
        }.onFailure { finished() }
        runCatching {
            vehicle.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities(
                onComplete = { config, bounds ->
                    modeCapabilities = modeCapabilities.copy(steeringWheelHeat = bounds.takeIf { canWrite(config) }?.let { ValueBoundsInt(it.minValue, it.maxValue) }); finished()
                }, onFailed = { finished() }
            )
        }.onFailure { finished() }
        runCatching {
            vehicle.getHVAC().checkHvacTemperatureCapabilities(
                onComplete = { config, areas, _ ->
                    modeCapabilities = modeCapabilities.copy(
                        targetTemperatureC = areas[driver].takeIf { canWrite(config) }?.let { ValueBoundsFloat(it.minValue, it.maxValue) },
                        passengerTargetTemperatureC = areas[passenger].takeIf { canWrite(config) }?.let { ValueBoundsFloat(it.minValue, it.maxValue) }
                    ); finished()
                }, onFailed = { finished() }
            )
        }.onFailure { finished() }
        runCatching {
            vehicle.getHVAC().checkHvacFanSpeedCapabilities(
                onComplete = { config, areas ->
                    modeCapabilities = modeCapabilities.copy(
                        fanSpeed = areas[driver].takeIf { canWrite(config) }?.let { ValueBoundsInt(it.minValue, it.maxValue) },
                        passengerFanSpeed = areas[passenger].takeIf { canWrite(config) }?.let { ValueBoundsInt(it.minValue, it.maxValue) }
                    ); finished()
                }, onFailed = { finished() }
            )
        }.onFailure { finished() }
        runCatching {
            vehicle.getHVAC().checkHvacFanDirectionCapabilities(
                onComplete = { config, areas ->
                    val canSet = canWrite(config)
                    modeCapabilities = modeCapabilities.copy(
                        fanDirections = areas[driver].takeIf { canSet }?.map { it.name },
                        passengerFanDirections = areas[passenger].takeIf { canSet }?.map { it.name }
                    )
                    finished()
                },
                onFailed = { finished() }
            )
        }.onFailure { finished() }

        checkHvacSwitchCapability(remaining, driver, "power") { modeCapabilities = modeCapabilities.copy(hvacPower = it) }
        checkHvacSwitchCapability(remaining, driver, "auto") { modeCapabilities = modeCapabilities.copy(hvacAuto = it) }
        checkHvacSwitchCapability(remaining, driver, "ac") { modeCapabilities = modeCapabilities.copy(airConditioning = it) }
        checkHvacSwitchCapability(remaining, driver, "recirculation") { modeCapabilities = modeCapabilities.copy(airRecirculation = it) }
        checkHvacSwitchCapability(remaining, driver, "auto_recirculation") { modeCapabilities = modeCapabilities.copy(autoRecirculation = it) }
        checkHvacSwitchCapability(remaining, driver, "dual") { modeCapabilities = modeCapabilities.copy(dualZone = it) }
        checkHvacSwitchCapability(remaining, driver, "max_ac") { modeCapabilities = modeCapabilities.copy(maxAc = it) }
        checkHvacSwitchCapability(remaining, driver, "defrost") { modeCapabilities = modeCapabilities.copy(maxDefrost = it) }
        checkHvacSwitchCapability(remaining, driver, "air_cleaner") { modeCapabilities = modeCapabilities.copy(airCleaner = it) }
        checkWindowDefrosterCapabilities(remaining)
    }

    private fun checkHvacSwitchCapability(remaining: AtomicInteger, area: SeatArea, key: String, update: (Boolean?) -> Unit) {
        val complete: (CapabilityConfig, List<SeatArea>) -> Unit = { config, areas ->
            update(canWrite(config) && area in areas)
            if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false)
        }
        val failed: (Exception) -> Unit = {
            update(false)
            if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false)
        }
        runCatching {
            when (key) {
                "power" -> vehicle.getHVAC().checkHvacPowerCapabilities(complete, failed)
                "auto" -> vehicle.getHVAC().checkHvacAutoCapabilities(complete, failed)
                "ac" -> vehicle.getHVAC().checkHvacAcCapabilities(complete, failed)
                "recirculation" -> vehicle.getHVAC().checkHvacRecirculationCapabilities(complete, failed)
                "auto_recirculation" -> vehicle.getHVAC().checkHvacAutoRecirculationCapabilities(complete, failed)
                "dual" -> vehicle.getHVAC().checkHvacDualCapabilities(complete, failed)
                "max_ac" -> vehicle.getHVAC().checkHvacMaxAcCapabilities(complete, failed)
                "defrost" -> vehicle.getHVAC().checkHvacMaxDefrostCapabilities(complete, failed)
                else -> vehicle.getHVAC().checkHvacAirCleanerCapabilities(complete, failed)
            }
        }.onFailure { failed(it as? Exception ?: Exception(it)) }
    }

    private fun checkWindowDefrosterCapabilities(remaining: AtomicInteger) {
        runCatching {
            vehicle.getHVAC().checkHvacDefrosterCapabilities(
                onComplete = { config, areas ->
                    val canSet = canWrite(config)
                    modeCapabilities = modeCapabilities.copy(
                        frontDefroster = canSet && WindowArea(WindowValue.FRONT_WINDSHIELD) in areas,
                        rearDefroster = canSet && WindowArea(WindowValue.REAR_WINDSHIELD) in areas
                    )
                    if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false)
                },
                onFailed = { if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false) }
            )
        }.onFailure { if (remaining.decrementAndGet() == 0) modeCapabilities = modeCapabilities.copy(loading = false) }
    }

    fun applyMode(mode: UserMode) {
        if (!started) return
        val settings = if (mode.id == "basic") mode.settings.copy(
            driverSeatHeat = 0,
            passengerSeatHeat = 0,
            driverSeatVentilation = 0,
            passengerSeatVentilation = 0,
            steeringWheelHeat = 0
        ) else mode.settings
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val passenger = SeatArea(SeatValue.ROW_1_RIGHT)
        applySeatLevel(settings.driverSeatHeat, driver, ventilation = false)
        applySeatLevel(settings.passengerSeatHeat, passenger, ventilation = false)
        applySeatLevel(settings.driverSeatVentilation, driver, ventilation = true)
        applySeatLevel(settings.passengerSeatVentilation, passenger, ventilation = true)
        settings.steeringWheelHeat?.let { value ->
            runCatching {
                vehicle.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities(
                    onComplete = { config, bounds -> if (canWrite(config) && value in bounds.minValue..bounds.maxValue) vehicle.getSteeringWheel().setHvacSteeringWheelHeat(value) },
                    onFailed = {}
                )
            }
        }
        settings.targetTemperatureC?.let { value ->
            applyTemperature(value, driver)
        }
        settings.passengerTargetTemperatureC?.let { applyTemperature(it, passenger) }
        settings.fanSpeed?.let { value ->
            applyFanSpeed(value, driver)
        }
        settings.passengerFanSpeed?.let { applyFanSpeed(it, passenger) }
        settings.fanDirection?.let { applyFanDirection(it, driver) }
        settings.passengerFanDirection?.let { applyFanDirection(it, passenger) }
        applyHvacSwitch(settings.hvacPower, "power", driver)
        applyHvacSwitch(settings.hvacAuto, "auto", driver)
        applyHvacSwitch(settings.airConditioning, "ac", driver)
        applyHvacSwitch(settings.airRecirculation, "recirculation", driver)
        applyHvacSwitch(settings.autoRecirculation, "auto_recirculation", driver)
        applyHvacSwitch(settings.dualZone, "dual", driver)
        applyHvacSwitch(settings.maxAc, "max_ac", driver)
        applyHvacSwitch(settings.maxDefrost, "defrost", driver)
        applyHvacSwitch(settings.airCleaner, "air_cleaner", driver)
        applyWindowDefroster(settings.frontDefroster, WindowArea(WindowValue.FRONT_WINDSHIELD))
        applyWindowDefroster(settings.rearDefroster, WindowArea(WindowValue.REAR_WINDSHIELD))
    }

    private fun applyTemperature(value: Float, area: SeatArea) {
        runCatching {
            vehicle.getHVAC().checkHvacTemperatureCapabilities(
                onComplete = { config, areas, _ ->
                    val bounds = areas[area]
                    if (canWrite(config) && bounds != null && value in bounds.minValue..bounds.maxValue) vehicle.getHVAC().setHvacTemperature(area, value, TemperatureUnit.CELSIUS)
                }, onFailed = {}
            )
        }
    }

    private fun applyFanDirection(value: String, area: SeatArea) {
        val direction = runCatching { FanDirectionType.valueOf(value) }.getOrNull() ?: return
        runCatching {
            vehicle.getHVAC().checkHvacFanDirectionCapabilities(
                onComplete = { config, areas -> if (canWrite(config) && direction in areas[area].orEmpty()) vehicle.getHVAC().setHvacFanDirection(area, direction) },
                onFailed = {}
            )
        }
    }

    private fun applyFanSpeed(value: Int, area: SeatArea) {
        runCatching {
            vehicle.getHVAC().checkHvacFanSpeedCapabilities(
                onComplete = { config, areas ->
                    val bounds = areas[area]
                    if (canWrite(config) && bounds != null && value in bounds.minValue..bounds.maxValue) vehicle.getHVAC().setHvacFanSpeed(area, value)
                }, onFailed = {}
            )
        }
    }

    private fun applyWindowDefroster(value: Boolean?, area: WindowArea) {
        if (value == null) return
        runCatching {
            vehicle.getHVAC().checkHvacDefrosterCapabilities(
                onComplete = { config, areas -> if (canWrite(config) && area in areas) vehicle.getHVAC().setHvacDefroster(area, value) },
                onFailed = {}
            )
        }
    }

    private fun applySeatLevel(value: Int?, area: SeatArea, ventilation: Boolean) {
        if (value == null) return
        runCatching {
            val check: ((CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit) = { config, areas ->
                val bounds = areas[area]
                if (canWrite(config) && bounds != null && value in bounds.minValue..bounds.maxValue) {
                    if (ventilation) vehicle.getSeat().setHvacSeatVentilation(area, value)
                    else vehicle.getSeat().setHvacSeatTemperature(area, value)
                }
            }
            val failed: (Exception) -> Unit = {}
            if (ventilation) vehicle.getSeat().checkSeatVentilationCapabilities(check, failed)
            else vehicle.getSeat().checkSeatTemperatureCapabilities(check, failed)
        }
    }

    private fun applyHvacSwitch(value: Boolean?, key: String, area: SeatArea) {
        if (value == null) return
        val complete: (CapabilityConfig, List<SeatArea>) -> Unit = { config, supported ->
            if (canWrite(config) && area in supported) {
                when (key) {
                    "power" -> vehicle.getHVAC().setHvacPower(area, value)
                    "auto" -> vehicle.getHVAC().setHvacAuto(area, value)
                    "ac" -> vehicle.getHVAC().setHvacAc(area, value)
                    "recirculation" -> vehicle.getHVAC().setHvacRecirculation(area, value)
                    "auto_recirculation" -> vehicle.getHVAC().setHvacAutoRecirculation(area, value)
                    "dual" -> vehicle.getHVAC().setHvacDual(area, value)
                    "max_ac" -> vehicle.getHVAC().setHvacMaxAc(area, value)
                    "defrost" -> vehicle.getHVAC().setHvacMaxDefrost(area, value)
                    else -> vehicle.getHVAC().setHvacAirCleaner(area, value)
                }
            }
        }
        val failed: (Exception) -> Unit = {}
        runCatching {
            when (key) {
                "power" -> vehicle.getHVAC().checkHvacPowerCapabilities(complete, failed)
                "auto" -> vehicle.getHVAC().checkHvacAutoCapabilities(complete, failed)
                "ac" -> vehicle.getHVAC().checkHvacAcCapabilities(complete, failed)
                "recirculation" -> vehicle.getHVAC().checkHvacRecirculationCapabilities(complete, failed)
                "auto_recirculation" -> vehicle.getHVAC().checkHvacAutoRecirculationCapabilities(complete, failed)
                "dual" -> vehicle.getHVAC().checkHvacDualCapabilities(complete, failed)
                "max_ac" -> vehicle.getHVAC().checkHvacMaxAcCapabilities(complete, failed)
                "defrost" -> vehicle.getHVAC().checkHvacMaxDefrostCapabilities(complete, failed)
                else -> vehicle.getHVAC().checkHvacAirCleanerCapabilities(complete, failed)
            }
        }
    }

    private fun canWrite(config: CapabilityConfig): Boolean =
        config.accessType == AccessType.WRITE || config.accessType == AccessType.READ_WRITE

    fun refresh() {
        if (!started) return
        state = state.copy(refreshing = true)
        val accumulator = ScanAccumulator()
        val remaining = AtomicInteger(5)

        fun finishRead() {
            if (remaining.decrementAndGet() != 0) return
            val scan = accumulator.toScan(System.currentTimeMillis())
            val history = if (accumulator.hasAnySuccessfulRead()) {
                (listOf(scan) + state.scans).take(100).also(::saveHistory)
            } else state.scans
            state = state.copy(
                odometerKm = scan.odometerKm ?: state.odometerKm,
                rangeKm = scan.rangeKm ?: state.rangeKm,
                batteryPercent = scan.batteryPercent ?: state.batteryPercent,
                tireThresholdsKpa = if (scan.tireThresholdsKpa.isNotEmpty()) scan.tireThresholdsKpa else state.tireThresholdsKpa,
                activeWarnings = scan.activeWarnings ?: state.activeWarnings,
                refreshing = false,
                updatedAt = scan.timestamp,
                scans = history
            )
        }

        runCatching {
            vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(
                onComplete = { config ->
                    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                        runCatching {
                            vehicle.getOdometer().getCurrentDistanceDriven(
                                onComplete = { value -> accumulator.odometer(value); finishRead() },
                                onFailed = { finishRead() }
                            )
                        }.onFailure { finishRead() }
                    } else finishRead()
                },
                onFailed = { finishRead() }
            )
        }.onFailure { finishRead() }

        @Suppress("DEPRECATION")
        runCatching {
            vehicle.getOdometer().getRangeRemainingDistance(
                onComplete = { meters -> accumulator.range(meters?.div(1000f)); finishRead() },
                onFailed = { finishRead() }
            )
        }.onFailure { finishRead() }

        runCatching {
            vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
                onComplete = { config ->
                    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                        runCatching {
                            vehicle.getEvBattery().getEvBatteryStateOfCharge(
                                onComplete = { percent -> accumulator.battery(percent); finishRead() },
                                onFailed = { finishRead() }
                            )
                        }.onFailure { finishRead() }
                    } else finishRead()
                },
                onFailed = { finishRead() }
            )
        }.onFailure { finishRead() }

        runCatching {
            vehicle.getSafety().getTelltalesInfo(
                onComplete = { telltales ->
                    handleTelltales(telltales)
                    accumulator.warnings(telltales.telltales.filter { it.state != TelltaleState.OFF }.map { it.type.name })
                    finishRead()
                },
                onFailed = { finishRead() }
            )
        }.onFailure { finishRead() }

        runCatching {
            vehicle.getTire().checkLowTirePressureCapabilities(
                onComplete = { config, supportedAreas ->
                    val supportedWheels = supportedAreas.flatMap { it.values.toList() }.distinct()
                    if ((config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) || supportedWheels.isEmpty()) {
                        accumulator.tireThresholds(emptyMap())
                        finishRead()
                    } else {
                        val tireReadsRemaining = AtomicInteger(supportedWheels.size)
                        val thresholds = mutableMapOf<String, Float>()
                        supportedWheels.forEach { wheel ->
                            runCatching {
                                vehicle.getTire().getLowTirePressure(
                                    area = WheelArea(wheel),
                                    onComplete = { threshold ->
                                        if (threshold != null) synchronized(thresholds) { thresholds[wheel.name] = threshold }
                                        if (tireReadsRemaining.decrementAndGet() == 0) {
                                            accumulator.tireThresholds(synchronized(thresholds) { thresholds.toMap() })
                                            finishRead()
                                        }
                                    },
                                    onFailed = {
                                        if (tireReadsRemaining.decrementAndGet() == 0) {
                                            accumulator.tireThresholds(synchronized(thresholds) { thresholds.toMap() })
                                            finishRead()
                                        }
                                    }
                                )
                            }.onFailure {
                                if (tireReadsRemaining.decrementAndGet() == 0) {
                                    accumulator.tireThresholds(synchronized(thresholds) { thresholds.toMap() })
                                    finishRead()
                                }
                            }
                        }
                    }
                },
                onFailed = { finishRead() }
            )
        }.onFailure { finishRead() }
    }

    @Synchronized
    private fun handleTelltales(telltales: Telltales) {
        val active = telltales.telltales.filter { it.state != TelltaleState.OFF }
        val activeTypes = active.map { it.type.name }.toSet()
        val previous = knownActiveWarnings
        val newlyActive = if (previous == null) active else active.filter { it.type.name !in previous }
        knownActiveWarnings = activeTypes
        incidentPreferences.edit().putStringSet("active_warnings", activeTypes).apply()
        if (newlyActive.isEmpty()) {
            state = state.copy(activeWarnings = activeTypes.toList())
            return
        }

        val now = System.currentTimeMillis()
        val location = state.location?.takeIf { now - it.timestamp <= 60_000L }
        val newIncidents = newlyActive.map { item ->
            VehicleIncident(
                timestamp = now,
                warningType = item.type.name,
                latitude = location?.latitude,
                longitude = location?.longitude,
                distanceKm = state.odometerKm,
                priority = item.priority
            )
        }
        val incidents = (newIncidents + state.incidents).take(500)
        saveIncidents(incidents)
        state = state.copy(activeWarnings = activeTypes.toList(), incidents = incidents)
    }

    private fun startLocationUpdates() {
        if (locationListener != null) return
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        val listener = LocationListener { location ->
            state = state.copy(location = GeoPoint(location.latitude, location.longitude, location.time))
        }
        locationListener = listener
        runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
                state = state.copy(location = GeoPoint(location.latitude, location.longitude, location.time))
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 5f, listener, Looper.getMainLooper())
        }.onFailure { locationListener = null }
    }

    private fun stopLocationUpdates() {
        locationListener?.let { listener -> runCatching { locationManager.removeUpdates(listener) } }
        locationListener = null
    }

    private fun readHistory(): List<VehicleScan> = runCatching {
        val array = JSONArray(preferences.getString("scans", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val thresholdsJson = item.optJSONObject("tires") ?: JSONObject()
            val thresholds = thresholdsJson.keys().asSequence().associateWith { thresholdsJson.getDouble(it).toFloat() }
            val warningsJson = item.optJSONArray("warnings")
            val warnings = warningsJson?.let { json -> (0 until json.length()).map { json.getString(it) } }
            VehicleScan(
                timestamp = item.getLong("timestamp"),
                odometerKm = item.nullableFloat("odometer"),
                rangeKm = item.nullableFloat("range"),
                batteryPercent = item.nullableInt("battery"),
                tireThresholdsKpa = thresholds,
                activeWarnings = warnings
            )
        }
    }.getOrDefault(emptyList())

    private fun saveHistory(scans: List<VehicleScan>) {
        val array = JSONArray()
        scans.forEach { scan ->
            val tires = JSONObject().apply { scan.tireThresholdsKpa.forEach { (key, value) -> put(key, value.toDouble()) } }
            val item = JSONObject()
                .put("timestamp", scan.timestamp)
                .put("tires", tires)
            item.putNullable("odometer", scan.odometerKm?.toDouble())
            item.putNullable("range", scan.rangeKm?.toDouble())
            item.putNullable("battery", scan.batteryPercent)
            scan.activeWarnings?.let { item.put("warnings", JSONArray(it)) }
            array.put(item)
        }
        preferences.edit().putString("scans", array.toString()).apply()
    }

    private fun readIncidents(): List<VehicleIncident> = runCatching {
        val array = JSONArray(incidentPreferences.getString("incidents", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            VehicleIncident(
                timestamp = item.getLong("timestamp"),
                warningType = item.getString("type"),
                latitude = item.nullableDouble("latitude"),
                longitude = item.nullableDouble("longitude"),
                distanceKm = item.nullableFloat("distance"),
                priority = item.optInt("priority")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveIncidents(incidents: List<VehicleIncident>) {
        val array = JSONArray()
        incidents.forEach { incident ->
            val item = JSONObject()
                .put("timestamp", incident.timestamp)
                .put("type", incident.warningType)
            item.putNullable("latitude", incident.latitude)
            item.putNullable("longitude", incident.longitude)
            item.putNullable("distance", incident.distanceKm?.toDouble())
            item.put("priority", incident.priority)
            array.put(item)
        }
        incidentPreferences.edit().putString("incidents", array.toString()).apply()
    }
}

private class ScanAccumulator {
    private var odometerKm: Float? = null
    private var rangeKm: Float? = null
    private var batteryPercent: Int? = null
    private var tireThresholdsKpa: Map<String, Float> = emptyMap()
    private var activeWarnings: List<String>? = null
    private var successfulReads = 0

    @Synchronized fun odometer(value: Float?) { odometerKm = value; successfulReads++ }
    @Synchronized fun range(value: Float?) { rangeKm = value; successfulReads++ }
    @Synchronized fun battery(value: Int?) { batteryPercent = value; successfulReads++ }
    @Synchronized fun tireThresholds(value: Map<String, Float>) { tireThresholdsKpa = value; successfulReads++ }
    @Synchronized fun warnings(value: List<String>) { activeWarnings = value; successfulReads++ }
    @Synchronized fun hasAnySuccessfulRead() = successfulReads > 0
    @Synchronized fun toScan(timestamp: Long) = VehicleScan(timestamp, odometerKm, rangeKm, batteryPercent, tireThresholdsKpa, activeWarnings)
}

private fun JSONObject.nullableFloat(key: String): Float? = if (isNull(key)) null else optDouble(key).toFloat()
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else optDouble(key)
private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
