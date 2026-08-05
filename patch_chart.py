import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

# Upper arch
old_upper = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val upperArch = listOf("18", "17", "16", "15", "14", "13", "12", "11", "21", "22", "23", "24", "25", "26", "27", "28")
                    upperArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (d * 2.5f - 18.0f).dp"""
new_upper = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val upperArch = listOf("18", "17", "16", "15", "14", "13", "12", "11", "21", "22", "23", "24", "25", "26", "27", "28")
                    upperArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (d * 3.5f - 24.0f).dp"""
content = content.replace(old_upper, new_upper)

# Lower arch
old_lower = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lowerArch = listOf("48", "47", "46", "45", "44", "43", "42", "41", "31", "32", "33", "34", "35", "36", "37", "38")
                    lowerArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (18.0f - d * 2.5f).dp"""
new_lower = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lowerArch = listOf("48", "47", "46", "45", "44", "43", "42", "41", "31", "32", "33", "34", "35", "36", "37", "38")
                    lowerArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (24.0f - d * 3.5f).dp"""
content = content.replace(old_lower, new_lower)

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
