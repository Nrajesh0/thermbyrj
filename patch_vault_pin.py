import sys

with open('app/src/main/java/com/example/ui/screens/VaultActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('Create a 4-digit PIN', 'Create a 6-digit PIN')
content = content.replace('for (i in 0 until 4)', 'for (i in 0 until 6)')
content = content.replace('if (pin.length < 4)', 'if (pin.length < 6)')
content = content.replace('if (pin.length == 4)', 'if (pin.length == 6)')

with open('app/src/main/java/com/example/ui/screens/VaultActivity.kt', 'w') as f:
    f.write(content)

print("PIN length updated to 6")
