import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

pattern = r'val completedSet = treatment\.completedStages\.split\("\,"\)\[.*?\]\.toSet\(\)\n\n\s*Column\('
# Wait, let's just use replace
old_str = """                                    val completedSet = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

                                    Column("""
new_str = """                                    val completedSet = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                    val initialCompletedSet = remember(treatment.id) { completedSet }
                                    val remainingStages = allStages.filter { !initialCompletedSet.contains(it) }

                                    Column("""
content = content.replace(old_str, new_str)

content = content.replace("allStages.forEach { stage ->", "remainingStages.forEach { stage ->")

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
