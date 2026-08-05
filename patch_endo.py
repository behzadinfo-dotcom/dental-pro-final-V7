import re

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'r') as f:
    content = f.read()

pattern = r'fun parseEndoCanalData\(notes: String, selectedTeeth: String = ""\): List<EndoCanalData> \{.*?catch \(e: Exception\) \{\s*getDefaultCanalsForTeeth\(selectedTeeth\)\s*\}\s*\}'

def replacer(match):
    return """fun parseEndoCanalData(notes: String, selectedTeeth: String = ""): List<EndoCanalData> {
    val defaults = getDefaultCanalsForTeeth(selectedTeeth).toMutableList()
    if (!notes.contains("[ENDO_WORKING_LENGTH:")) {
        return defaults
    }
    return try {
        val regex = Regex(\"\"\"\\[ENDO_WORKING_LENGTH:\\s*(\\[[\\s\\S]*?\\])\\s*\\]\"\"\")
        val match = regex.find(notes)
        if (match != null) {
            val jsonStr = match.groupValues[1]
            val array = org.json.JSONArray(jsonStr)
            
            val savedData = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                savedData[obj.optString("name", "MB")] = obj
            }
            
            val finalList = mutableListOf<EndoCanalData>()
            defaults.forEach { defaultCanal ->
                val savedObj = savedData[defaultCanal.name]
                if (savedObj != null) {
                    finalList.add(defaultCanal.copy(
                        lengthMm = savedObj.optString("lengthMm", defaultCanal.lengthMm),
                        refPoint = savedObj.optString("refPoint", defaultCanal.refPoint),
                        mafFile = savedObj.optString("mafFile", defaultCanal.mafFile)
                    ))
                    savedData.remove(defaultCanal.name)
                } else {
                    finalList.add(defaultCanal)
                }
            }
            
            savedData.values.forEach { obj ->
                val canalName = obj.optString("name", "Extra")
                val fallback = defaults.firstOrNull()
                finalList.add(
                    EndoCanalData(
                        name = canalName,
                        lengthMm = obj.optString("lengthMm", "21.0"),
                        refPoint = obj.optString("refPoint", "Cusp Tip"),
                        mafFile = obj.optString("mafFile", "#30"),
                        minLengthMm = obj.optDouble("minLengthMm", fallback?.minLengthMm ?: 0.5),
                        maxLengthMm = obj.optDouble("maxLengthMm", fallback?.maxLengthMm ?: 30.0)
                    )
                )
            }
            finalList
        } else {
            defaults
        }
    } catch (e: Exception) {
        defaults
    }
}"""

content = re.sub(pattern, replacer, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'w') as f:
    f.write(content)
