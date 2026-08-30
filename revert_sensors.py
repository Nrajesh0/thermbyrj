import sys

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

bad_call = """        HardwareSensorMatrix()
        Spacer(modifier = Modifier.height(24.dp))
        
        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {"""
good_call = """        // Power Matrix
        GlassCard(modifier = Modifier.fillMaxWidth()) {"""

content = content.replace(bad_call, good_call)

# Now remove the composable definition by splitting at "@Composable\nfun HardwareSensorMatrix()"
parts = content.split("@Composable\nfun HardwareSensorMatrix()")
if len(parts) > 1:
    # We want to keep everything before it, and everything after its closing brace
    # The last thing in that function is InfoRow("MAGNETOMETER"...)\n    }\n}
    after_func = parts[1].split('InfoRow("MAGNETOMETER"', 1)[1]
    # find the second closing brace after that
    closing_braces = after_func.split('}', 2)
    rest_of_file = closing_braces[-1]
    content = parts[0] + rest_of_file

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
print("Reverted!")
