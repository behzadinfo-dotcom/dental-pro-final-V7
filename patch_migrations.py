import re

with open('app/src/main/java/com/example/data/AppDatabase.kt', 'r') as f:
    content = f.read()

m15_16 = """val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN clinicalFontSizePt INTEGER NOT NULL DEFAULT 11") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN globalFontScaleIndex INTEGER NOT NULL DEFAULT 3") } catch (e: Exception) {}
    }
}"""
content = re.sub(r'val MIGRATION_15_16 = object : Migration\(15, 16\) \{.*?\n\}', m15_16, content, flags=re.DOTALL)

m16_17 = """val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE appointments ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE appointments ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
    }
}"""
content = re.sub(r'val MIGRATION_16_17 = object : Migration\(16, 17\) \{.*?\n\}', m16_17, content, flags=re.DOTALL)

m17_18 = """val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("UPDATE clinic_settings SET globalFontScaleIndex = (globalFontScaleIndex * 2) + 2 WHERE globalFontScaleIndex <= 7") } catch (e: Exception) {}
    }
}"""
content = re.sub(r'val MIGRATION_17_18 = object : Migration\(17, 18\) \{.*?\n\}', m17_18, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/AppDatabase.kt', 'w') as f:
    f.write(content)
