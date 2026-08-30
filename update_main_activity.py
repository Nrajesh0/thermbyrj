import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('import androidx.compose.material.icons.filled.Sensors', 'import androidx.compose.material.icons.filled.BatteryFull')
content = content.replace('import androidx.compose.material.icons.outlined.Sensors', 'import androidx.compose.material.icons.outlined.BatteryFull')

content = content.replace('TabItem("Sensors", Icons.Filled.Sensors, Icons.Outlined.Sensors)', 'TabItem("Power", Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)')

content = content.replace('2 -> SensorsScreen(viewModel)', '2 -> PowerScreen(viewModel)')

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
print("Updated MainActivity")
