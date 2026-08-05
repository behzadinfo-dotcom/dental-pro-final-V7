import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

content = content.replace("val yOff = (d * 1.2f - 9.6f).dp", "val yOff = (d * 2.2f - 17.6f).dp")
content = content.replace("val yOff = (9.6f - d * 1.2f).dp", "val yOff = (17.6f - d * 2.2f).dp")
content = content.replace("padding(horizontal = 4.dp, vertical = 2.dp)", "padding(horizontal = 3.dp, vertical = 2.dp)")

# Wait, spacing could also be Arrangement.spacedBy(2.dp) to 1.dp
content = content.replace("horizontalArrangement = Arrangement.spacedBy(2.dp)", "horizontalArrangement = Arrangement.spacedBy(1.dp)")

# Let's adjust vertical padding for the row so the curve has enough space
content = content.replace("Modifier.padding(vertical = 8.dp)", "Modifier.padding(vertical = 14.dp)")

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
