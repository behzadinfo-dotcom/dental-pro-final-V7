import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

# Modify size to 22.dp and font size to 9.sp inside ToothButton
content = content.replace("modifier = Modifier\n            .size(34.dp)", "modifier = Modifier\n            .size(20.dp)")
# Also need to find where text is rendered inside ToothButton
content = re.sub(r'fontSize = 12\.sp,\s*fontWeight = FontWeight\.Bold,\s*color = textColor', 'fontSize = 9.sp,\n                    fontWeight = FontWeight.Bold,\n                    color = textColor', content)

# I should make sure it has horizontalScroll just in case
content = content.replace("horizontalArrangement = Arrangement.spacedBy(2.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {", "modifier = Modifier.horizontalScroll(rememberScrollState()),\n                    horizontalArrangement = Arrangement.spacedBy(1.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {")

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
