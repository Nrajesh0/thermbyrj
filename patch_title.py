import sys

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

old_text = """        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = "SENSORS & POWER",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Power Matrix"""

new_text = """        Spacer(modifier = Modifier.height(64.dp))

        // Power Matrix"""

if old_text in content:
    content = content.replace(old_text, new_text)
    with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
        f.write(content)
    print("Title removed.")
else:
    print("Target text not found.")
