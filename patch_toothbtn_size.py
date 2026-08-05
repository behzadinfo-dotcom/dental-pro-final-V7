import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

content = content.replace("modifier = Modifier\n            .size(20.dp)", "modifier = Modifier\n            .size(33.dp)")
content = content.replace("fontSize = 9.sp,\n                    fontWeight = FontWeight.Bold,\n                    color = textColor", "fontSize = 11.sp,\n                    fontWeight = FontWeight.Bold,\n                    color = textColor")

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
