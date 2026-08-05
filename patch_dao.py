import re

with open('app/src/main/java/com/example/data/AppDatabase.kt', 'r') as f:
    content = f.read()

dao_new = """@Dao
interface ClinicSettingsDao {
    @Query("SELECT * FROM clinic_settings WHERE id = 1")
    fun getSettings(): Flow<ClinicSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ClinicSettings): Long

    @Query("UPDATE clinic_settings SET globalFontScaleIndex = :index WHERE id = 1")
    suspend fun updateGlobalFontScale(index: Int)

    @Query("UPDATE clinic_settings SET clinicalFontSizePt = :sizePt WHERE id = 1")
    suspend fun updateClinicalFontSize(sizePt: Int)

    @Query("UPDATE clinic_settings SET clinicName = :clinicName, doctorName = :doctorName, staffNames = :staffNames, showSummaryCard = :showSummary, showQuickActions = :showQuick, showCharts = :showCharts WHERE id = 1")
    suspend fun updateGeneralSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Int, showQuick: Int, showCharts: Int)
"""

content = re.sub(r'@Dao\s*interface ClinicSettingsDao \{\s*@Query\("SELECT \* FROM clinic_settings WHERE id = 1"\)\s*fun getSettings\(\): Flow<ClinicSettings\?>\s*@Insert\(onConflict = OnConflictStrategy\.REPLACE\)\s*suspend fun insertSettings\(settings: ClinicSettings\): Long', dao_new, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/AppDatabase.kt', 'w') as f:
    f.write(content)
