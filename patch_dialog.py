import re

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'r') as f:
    content = f.read()

# Pattern to replace the Row containing A-, fontSize, A+, and Edit
pattern = r'Row\(\s*verticalAlignment = Alignment\.CenterVertically,\s*horizontalArrangement = Arrangement\.spacedBy\(2\.dp\)\s*\) \{\s*// Font Size Zoom Controls.*?// Edit Toggle Button\s*IconButton\(\s*onClick = \{ isEditMode = !isEditMode \},\s*modifier = Modifier\.size\(32\.dp\)\s*\) \{\s*Icon\(\s*if \(isEditMode\) Icons\.Default\.Check else Icons\.Default\.Edit,\s*contentDescription = "Edit Protocol",\s*tint = MaterialTheme\.colorScheme\.primary,\s*modifier = Modifier\.size\(18\.dp\)\s*\)\s*\}\s*\}'

new_row = """Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edit Protocol",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }"""

content = re.sub(pattern, new_row, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'w') as f:
    f.write(content)
