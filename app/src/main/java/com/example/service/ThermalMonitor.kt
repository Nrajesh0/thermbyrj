package com.example.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import androidx.core.content.ContextCompat
import com.example.queryDeviceStorageTotals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class LiveThermalData(
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = -1f, // -1f implies restricted/unreadable
    val batteryLevel: Int = 0,
    val batteryVoltage: Int = 0,
    val batteryCurrentMa: Int = 0,
    val batteryHealth: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val batteryTech: String = "",
    val isCharging: Boolean = false,
    val chargingSource: String = "Battery",
    val chargeTimeRemainingMs: Long = -1L,
    val cycleCount: Int = -1,

    val totalRam: Long = 0,
    val availRam: Long = 0,
    val totalStorage: Long = 0,
    val availStorage: Long = 0,

    val deviceModel: String = Build.MODEL,
    val deviceManufacturer: String = Build.MANUFACTURER,
    val osVersion: String = Build.VERSION.RELEASE,
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val board: String = Build.BOARD,
    val hardware: String = Build.HARDWARE,
    val bootloader: String = Build.BOOTLOADER,

    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val cpuArch: String = System.getProperty("os.arch") ?: "Unknown",

    val uptime: Long = 0L,
    val refreshRate: Float = 60f,
    val thermalStatusCode: Int = 0,
    val thermalStatusLabel: String = "Nominal"
)

class ThermalMonitor private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ThermalMonitor? = null

        fun getInstance(context: Context): ThermalMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThermalMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + safeCoroutineExceptionHandler())

    private val _liveData = MutableStateFlow(
        LiveThermalData(
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            bootloader = Build.BOOTLOADER,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuArch = System.getProperty("os.arch") ?: "Unknown"
        )
    )
    val liveData: StateFlow<LiveThermalData> = _liveData.asStateFlow()

    // Cache the discovered CPU thermal zone file so we never scan /sys/class/thermal/ repeatedly
    @Volatile
    private var cpuSensorDiscoveryDone = false
    @Volatile
    private var cachedCpuTempFile: File? = null

    fun updateDataFromIntent(intent: Intent?) {
        if (intent == null) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100f / scale.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            _liveData.value.batteryLevel
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val batteryTempC = if (temp > 0) temp / 10f else _liveData.value.batteryTemp

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            .let { if (it > 0) it else _liveData.value.batteryVoltage }
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            .let { if (it != -1) it else _liveData.value.batteryHealth }
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: _liveData.value.batteryTech

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
            status == BatteryManager.BATTERY_STATUS_CHARGING

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (status == BatteryManager.BATTERY_STATUS_CHARGING) "Charging" else "Battery"
        }

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        } else {
            -1
        }

        val batteryExtras = readBatteryManagerExtras(isCharging)

        _liveData.value = _liveData.value.copy(
            batteryTemp = batteryTempC,
            batteryLevel = batteryPct,
            batteryVoltage = voltage,
            batteryCurrentMa = batteryExtras.currentMa,
            batteryHealth = health,
            batteryTech = tech,
            isCharging = isCharging,
            chargingSource = source,
            chargeTimeRemainingMs = batteryExtras.chargeTimeRemainingMs,
            cycleCount = cycleCount
        )
    }

    private data class BatteryManagerExtras(
        val currentMa: Int,
        val chargeTimeRemainingMs: Long
    )

    private fun readBatteryManagerExtras(isCharging: Boolean): BatteryManagerExtras {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val rawCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            val currentMa = if (rawCurrent == Int.MIN_VALUE || rawCurrent == 0) {
                0
            } else if (abs(rawCurrent) >= 10_000) {
                rawCurrent / 1000 // Standard microamperes -> milliamperes
            } else {
                rawCurrent // Some kernels report mA directly
            }

            val chargeTimeMs = if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    bm?.computeChargeTimeRemaining() ?: -1L
                } catch (_: Exception) {
                    -1L
                }
            } else {
                -1L
            }

            BatteryManagerExtras(currentMa, chargeTimeMs)
        } catch (_: Exception) {
            BatteryManagerExtras(0, -1L)
        }
    }

    @Volatile
    private var lastStorageQueryMs: Long = 0L
    @Volatile
    private var cachedTotalStorage: Long = 0L
    @Volatile
    private var cachedAvailStorage: Long = 0L

    /**
     * Refreshes system specs (RAM, Storage, CPU thermal sensor, display refresh rate).
     * Offline-only: zero network or socket calls.
     */
    fun refreshSystemSpecs(forceStorageRefresh: Boolean = false) {
        val cpuTempC = getCpuTemperature()
        val (totRam, avRam) = getMemoryInfo()
        val now = System.currentTimeMillis()
        val (totStore, avStore) = if (forceStorageRefresh || cachedTotalStorage <= 0L || (now - lastStorageQueryMs) >= 60_000L) {
            getStorageInfo().also { (tot, av) ->
                cachedTotalStorage = tot
                cachedAvailStorage = av
                lastStorageQueryMs = now
            }
        } else {
            Pair(cachedTotalStorage, cachedAvailStorage)
        }
        val currentState = _liveData.value
        val batteryExtras = readBatteryManagerExtras(currentState.isCharging)
        val (thermalCode, thermalLabel) = readOsThermalStatus()

        _liveData.value = _liveData.value.copy(
            cpuTemp = cpuTempC,
            batteryCurrentMa = batteryExtras.currentMa,
            chargeTimeRemainingMs = batteryExtras.chargeTimeRemainingMs,
            totalRam = totRam,
            availRam = avRam,
            totalStorage = totStore,
            availStorage = avStore,
            uptime = SystemClock.elapsedRealtime(),
            refreshRate = getRefreshRate(),
            thermalStatusCode = thermalCode,
            thermalStatusLabel = thermalLabel
        )
    }

    fun forceUpdate() {
        val stickyIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        updateDataFromIntent(stickyIntent)
        refreshSystemSpecs(forceStorageRefresh = true)
    }

    private var monitoringJob: Job? = null
    private var tickerJob: Job? = null

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            if (com.example.MainActivity.isUiForeground) {
                monitorScope.launch(Dispatchers.IO) {
                    try {
                        refreshSystemSpecs()
                    } catch (_: Throwable) {
                    }
                }
            }
            return
        }

        // Pull initial state off the main thread so thermal/storage Binder I/O never stalls UI startup
        monitorScope.launch(Dispatchers.IO) {
            try {
                forceUpdate()
            } catch (_: Throwable) {
            }
        }

        // Event-driven battery monitoring via OS broadcasts (zero polling overhead)
        monitoringJob = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    try {
                        trySend(intent)
                    } catch (_: Throwable) {
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            var registered = false
            try {
                val initialSticky = ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                registered = true
                if (initialSticky != null) {
                    trySend(initialSticky)
                }
            } catch (_: Throwable) {
            }
            awaitClose {
                if (registered) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Throwable) {
                    }
                }
            }
        }.onEach { intent ->
            try {
                updateDataFromIntent(intent)
            } catch (_: Throwable) {
            }
        }.launchIn(monitorScope)

        // Low-frequency 15s refresh for RAM & current mA only while UI is visible in foreground
        tickerJob?.cancel()
        tickerJob = monitorScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (com.example.MainActivity.isUiForeground) {
                    delay(15_000L)
                    if (com.example.MainActivity.isUiForeground) {
                        try {
                            refreshSystemSpecs()
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    delay(30_000L)
                }
            }
        }
    }

    fun refreshCpuTempOnly(): Float {
        val cpu = getCpuTemperature()
        if (cpu > 0f) {
            _liveData.value = _liveData.value.copy(cpuTemp = cpu)
        }
        return cpu
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun getMemoryInfo(): Pair<Long, Long> {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return Pair(0L, 0L)
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            Pair(memInfo.totalMem, memInfo.availMem)
        } catch (_: Throwable) {
            Pair(0L, 0L)
        }
    }

    private fun getStorageInfo(): Pair<Long, Long> {
        return queryDeviceStorageTotals(context)
    }

    private fun getCpuTemperature(): Float {
        try {
            if (!cpuSensorDiscoveryDone) {
                cpuSensorDiscoveryDone = true
                val dir = File("/sys/class/thermal/")
                if (dir.exists() && dir.canRead()) {
                    val zones = dir.listFiles() ?: emptyArray()
                    for (zone in zones) {
                        if (!zone.name.startsWith("thermal_zone")) continue
                        val typeFile = File(zone, "type")
                        val tempFile = File(zone, "temp")
                        if (typeFile.canRead() && tempFile.canRead()) {
                            val type = typeFile.readText().trim().lowercase()
                            if (type.contains("cpu") || type.contains("soc") || type.contains("tsens") ||
                                type.contains("mtktscpu") || type.contains("ap_ntc") || type.contains("cluster")
                            ) {
                                val raw = tempFile.readText().trim().toFloatOrNull()
                                if (raw != null) {
                                    val tempC = if (raw > 1000f) raw / 1000f else raw
                                    if (tempC in 1f..150f) {
                                        cachedCpuTempFile = tempFile
                                        return tempC
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val tempFile = cachedCpuTempFile ?: return -1f
                val raw = tempFile.readText().trim().toFloatOrNull() ?: return -1f
                val tempC = if (raw > 1000f) raw / 1000f else raw
                if (tempC in 1f..150f) return tempC
            }
        } catch (_: Exception) {
        }
        return -1f
    }

    private fun readOsThermalStatus(): Pair<Int, String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val code = pm?.currentThermalStatus ?: android.os.PowerManager.THERMAL_STATUS_NONE
                val label = when (code) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttle"
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                    else -> "Nominal"
                }
                code to label
            } else {
                0 to "Nominal"
            }
        } catch (_: Throwable) {
            0 to "Nominal"
        }
    }

    private fun getRefreshRate(): Float {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
        } catch (_: Throwable) {
            60f
        }
    }
}
