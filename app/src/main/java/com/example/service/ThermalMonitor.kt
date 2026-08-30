package com.example.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

data class LiveThermalData(
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = -1f, // -1f implies restricted/unreadable
    val batteryLevel: Int = 0,
    val batteryVoltage: Int = 0,
    val batteryHealth: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val batteryTech: String = "",
    val isCharging: Boolean = false,
    
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
    
    val ipAddress: String = "Air-Gapped (No Network)",
    val uptime: Long = 0L,
    val refreshRate: Float = 0f
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

    fun updateDataFromIntent(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).roundToInt()
        } else {
            _liveData.value.batteryLevel
        }

        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTempC = if (temp > 0) temp / 10f else _liveData.value.batteryTemp
        
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: _liveData.value.batteryVoltage
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: _liveData.value.batteryHealth
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: _liveData.value.batteryTech
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                         plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                         plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        _liveData.value = _liveData.value.copy(
            batteryTemp = batteryTempC,
            batteryLevel = batteryPct,
            batteryVoltage = voltage,
            batteryHealth = health,
            batteryTech = tech,
            isCharging = isCharging
        )
    }

    /**
     * Refreshes system specs (RAM, Storage, CPU thermal zones, Network IP, display refresh rate).
     * Call this when displaying the device info tab or when the app is in the foreground.
     */
    fun refreshSystemSpecs() {
        val cpuTempC = getCpuTemperature()
        val (totRam, avRam) = getMemoryInfo()
        val (totStore, avStore) = getStorageInfo()

        _liveData.value = _liveData.value.copy(
            cpuTemp = cpuTempC,
            totalRam = totRam,
            availRam = avRam,
            totalStorage = totStore,
            availStorage = avStore,
            ipAddress = "Air-Gapped (No Network)",
            uptime = SystemClock.elapsedRealtime(),
            refreshRate = getRefreshRate()
        )
    }

    fun forceUpdate() {
        refreshSystemSpecs()
    }

    private var monitoringJob: Job? = null
    fun startMonitoring() {
        if (monitoringJob != null) return
        
        monitoringJob = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    trySend(intent)
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }.onEach { intent ->
            updateDataFromIntent(intent)
        }.launchIn(CoroutineScope(Dispatchers.Default))
        
        forceUpdate()
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private fun getMemoryInfo(): Pair<Long, Long> {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            Pair(memInfo.totalMem, memInfo.availMem)
        } catch(e: Exception) {
            Pair(0L, 0L)
        }
    }
    
    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            Pair(statFs.totalBytes, statFs.availableBytes)
        } catch(e: Exception) {
            Pair(0L, 0L)
        }
    }

    private fun getCpuTemperature(): Float {
        var cpuTemp = -1f
        var foundReading = false
        try {
            val dir = File("/sys/class/thermal/")
            if (dir.exists()) {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.name.startsWith("thermal_zone")) {
                        val tempFile = File(file, "temp")
                        val typeFile = File(file, "type")
                        if (tempFile.exists() && typeFile.exists()) {
                            val type = typeFile.readText().trim()
                            if (type.contains("cpu", ignoreCase = true) || type.contains("soc", ignoreCase = true)) {
                                val temp = tempFile.readText().trim().toFloatOrNull()
                                if (temp != null) {
                                    val tempC = if (temp > 1000) temp / 1000f else temp
                                    if (tempC > 0 && tempC > cpuTemp) {
                                        cpuTemp = tempC
                                        foundReading = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (foundReading) cpuTemp else -1f
    }

    private fun getRefreshRate(): Float {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.refreshRate
        } catch (e: Exception) {
            0f
        }
    }
}
