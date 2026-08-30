import sys

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# 1. Add imports
imports = """import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.DisposableEffect
"""
# insert imports at the top
content = imports + content

# 2. Add the Environmental/Hardware Matrix Composable at the end
new_composable = """
@Composable
fun HardwareSensorMatrix() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    var accelData by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    var gyroData by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    var lightData by remember { mutableStateOf(0f) }
    var magData by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }

    DisposableEffect(sensorManager) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelData = event.values.clone()
                    Sensor.TYPE_GYROSCOPE -> gyroData = event.values.clone()
                    Sensor.TYPE_LIGHT -> lightData = event.values[0]
                    Sensor.TYPE_MAGNETIC_FIELD -> magData = event.values.clone()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ENVIRONMENTAL SENSORS", 
            color = TextTertiary, 
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        InfoRow("LIGHT (LUX)", String.format("%.1f lx", lightData))
        Spacer(modifier = Modifier.height(16.dp))
        InfoRow("ACCELEROMETER", String.format("X:%.1f Y:%.1f Z:%.1f", accelData[0], accelData[1], accelData[2]))
        Spacer(modifier = Modifier.height(16.dp))
        InfoRow("GYROSCOPE", String.format("X:%.1f Y:%.1f Z:%.1f", gyroData[0], gyroData[1], gyroData[2]))
        Spacer(modifier = Modifier.height(16.dp))
        InfoRow("MAGNETOMETER", String.format("X:%.1f Y:%.1f Z:%.1f", magData[0], magData[1], magData[2]))
    }
}
"""

content = content + new_composable

# 3. Add call to HardwareSensorMatrix in SensorsScreen
target_call = """        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {"""
replacement_call = """        HardwareSensorMatrix()
        Spacer(modifier = Modifier.height(24.dp))
        
        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {"""

if target_call in content:
    content = content.replace(target_call, replacement_call)
    with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
        f.write(content)
    print("Injected Sensor Matrix!")
else:
    print("Could not find the insertion point.")
