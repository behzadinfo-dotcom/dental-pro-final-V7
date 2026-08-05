import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

# Replace upper jaw rows
def upper_repl(m):
    return """Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                                                listOf""" + m.group(1) + """.forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (d * 1.2f - 9.6f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.primaryContainer,"""

# Replace lower jaw rows
def lower_repl(m):
    return """Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                                                listOf""" + m.group(1) + """.forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (9.6f - d * 1.2f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,"""

# Patterns
content = re.sub(r'Row\(horizontalArrangement = Arrangement\.spacedBy\(4\.dp\)\) \{\s*listOf(\(.*?\))\.forEach \{ num ->\s*Surface\(\s*color = if \(num == "\|"\) Color\.Transparent else MaterialTheme\.colorScheme\.primaryContainer,', upper_repl, content)

content = re.sub(r'Row\(horizontalArrangement = Arrangement\.spacedBy\(4\.dp\)\) \{\s*listOf(\(.*?\))\.forEach \{ num ->\s*Surface\(\s*color = if \(num == "\|"\) Color\.Transparent else MaterialTheme\.colorScheme\.secondaryContainer,', lower_repl, content)

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
