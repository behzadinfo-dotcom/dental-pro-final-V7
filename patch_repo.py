import re

with open('app/src/main/java/com/example/data/Repository.kt', 'r') as f:
    content = f.read()

repo_new = """    suspend fun insertSettings(settings: ClinicSettings): Long = database.clinicSettingsDao().insertSettings(settings)

    suspend fun updateGlobalFontScale(index: Int) = database.clinicSettingsDao().updateGlobalFontScale(index)
    suspend fun updateClinicalFontSize(sizePt: Int) = database.clinicSettingsDao().updateClinicalFontSize(sizePt)
    suspend fun updateGeneralSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) = 
        database.clinicSettingsDao().updateGeneralSettings(clinicName, doctorName, staffNames, if(showSummary) 1 else 0, if(showQuick) 1 else 0, if(showCharts) 1 else 0)
"""

content = content.replace("    suspend fun insertSettings(settings: ClinicSettings): Long = database.clinicSettingsDao().insertSettings(settings)", repo_new)

with open('app/src/main/java/com/example/data/Repository.kt', 'w') as f:
    f.write(content)
