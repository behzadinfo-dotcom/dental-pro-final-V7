import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

new_lower = """            // Lower Arch (Mandibular)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "فک پایین (Mandibular)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lowerArch = listOf("48", "47", "46", "45", "44", "43", "42", "41", "31", "32", "33", "34", "35", "36", "37", "38")
                    lowerArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (18.0f - d * 2.5f).dp
                        Box(modifier = Modifier.offset(y = yOff)) {
                            ToothButton(
                                number = toothNum,
                                displayLabel = formatToothLabel(toothNum, system),
                                isSelected = selectedList.contains(toothNum),
                                statusType = toothStatusMap[toothNum],
                                onClick = { toggleTooth(toothNum) }
                            )
                        }
                    }
                }
            }"""

pattern = r'// Lower Arch \(Mandibular\).*?HorizontalDivider\(modifier = Modifier.padding\(vertical = 8.dp\), color = MaterialTheme.colorScheme.outlineVariant.copy\(alpha = 0.5f\)\)'
# Actually let's just find the Surface containing the formatted selected list and replace up to there
pattern = r'// Lower Arch \(Mandibular\).*?(?=Surface\(\s*color = MaterialTheme\.colorScheme\.primaryContainer,\s*shape = RoundedCornerShape\(8\.dp\),\s*modifier = Modifier\.padding\(top = 4\.dp\)\s*\))'

content = re.sub(pattern, new_lower + '\n\n            ', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
