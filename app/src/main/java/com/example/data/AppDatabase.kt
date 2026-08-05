package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun getAllPatients(): Flow<List<Patient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient): Long

    @Delete
    suspend fun deletePatient(patient: Patient)
}

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments ORDER BY date DESC")
    fun getAllTreatments(): Flow<List<Treatment>>

    @Query("SELECT * FROM treatments WHERE patientId = :patientId ORDER BY date DESC")
    fun getTreatmentsForPatient(patientId: Long): Flow<List<Treatment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatment(treatment: Treatment): Long

    @Delete
    suspend fun deleteTreatment(treatment: Treatment)

    @Query("DELETE FROM treatments WHERE patientId = :patientId")
    suspend fun deleteTreatmentsForPatient(patientId: Long)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE patientId = :patientId ORDER BY date DESC")
    fun getPaymentsForPatient(patientId: Long): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments WHERE patientId = :patientId")
    suspend fun deletePaymentsForPatient(patientId: Long)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY date ASC")
    fun getAllAppointments(): Flow<List<Appointment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment): Long

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Query("DELETE FROM appointments WHERE patientId = :patientId OR patientName = :patientName")
    suspend fun deleteAppointmentsForPatient(patientId: Long, patientName: String)
}

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun getAllVendors(): Flow<List<Vendor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: Vendor): Long

    @Delete
    suspend fun deleteVendor(vendor: Vendor)
}

@Dao
interface LabOrderDao {
    @Query("SELECT * FROM lab_orders ORDER BY dueDate ASC")
    fun getAllLabOrders(): Flow<List<LabOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabOrder(order: LabOrder): Long

    @Delete
    suspend fun deleteLabOrder(order: LabOrder)
}

@Dao
interface EquipmentMaintenanceDao {
    @Query("SELECT * FROM equipment_maintenance ORDER BY date DESC")
    fun getAllMaintenance(): Flow<List<EquipmentMaintenance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenance(maintenance: EquipmentMaintenance): Long

    @Delete
    suspend fun deleteMaintenance(maintenance: EquipmentMaintenance)
}

@Dao
interface ClinicExpenseDao {
    @Query("SELECT * FROM clinic_expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ClinicExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ClinicExpense): Long

    @Delete
    suspend fun deleteExpense(expense: ClinicExpense)
}

@Dao
interface PersonalTransactionDao {
    @Query("SELECT * FROM personal_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<PersonalTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: PersonalTransaction): Long

    @Delete
    suspend fun deleteTransaction(tx: PersonalTransaction)
}

@Dao
interface TreatmentTemplateDao {
    @Query("SELECT * FROM treatment_templates ORDER BY title ASC")
    fun getAllTemplates(): Flow<List<TreatmentTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TreatmentTemplate): Long

    @Delete
    suspend fun deleteTemplate(template: TreatmentTemplate)
}

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY bankName ASC")
    fun getAllBankAccounts(): Flow<List<BankAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(account: BankAccount): Long

    @Delete
    suspend fun deleteBankAccount(account: BankAccount)
}

@Dao
interface PersonalSubAccountDao {
    @Query("SELECT * FROM personal_sub_accounts ORDER BY name ASC")
    fun getAllSubAccounts(): Flow<List<PersonalSubAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubAccount(sub: PersonalSubAccount): Long

    @Delete
    suspend fun deleteSubAccount(sub: PersonalSubAccount)
}

@Dao
interface ChildrenExpenseDao {
    @Query("SELECT * FROM children_expenses ORDER BY date DESC")
    fun getAllChildrenExpenses(): Flow<List<ChildrenExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChildrenExpense(expense: ChildrenExpense): Long

    @Delete
    suspend fun deleteChildrenExpense(expense: ChildrenExpense)
}

@Dao
interface LabServiceDao {
    @Query("SELECT * FROM lab_services ORDER BY serviceName ASC")
    fun getAllLabServices(): Flow<List<LabService>>

    @Query("SELECT * FROM lab_services WHERE labName = :labName ORDER BY serviceName ASC")
    fun getServicesForLab(labName: String): Flow<List<LabService>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabService(service: LabService): Long

    @Delete
    suspend fun deleteLabService(service: LabService)
}

@Dao
interface FixedIncomeDao {
    @Query("SELECT * FROM fixed_incomes ORDER BY id DESC")
    fun getAllFixedIncomes(): Flow<List<FixedIncome>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixedIncome(income: FixedIncome): Long

    @Delete
    suspend fun deleteFixedIncome(income: FixedIncome)
}

@Dao
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

}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    fun getAllInstallments(): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE patientId = :patientId ORDER BY dueDate ASC")
    fun getInstallmentsForPatient(patientId: Long): Flow<List<Installment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long

    @Update
    suspend fun updateInstallment(installment: Installment)

    @Delete
    suspend fun deleteInstallment(installment: Installment)

    @Query("DELETE FROM installments WHERE patientId = :patientId")
    suspend fun deleteInstallmentsForPatient(patientId: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safe empty migration if migrating from 1 to 2
    }
}


val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `treatment_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `defaultCost` INTEGER NOT NULL, `defaultNotes` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `bank_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankName` TEXT NOT NULL, `cardHolder` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, `balance` INTEGER NOT NULL, `smsKeywords` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_sub_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `children_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childName` TEXT NOT NULL, `title` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `clinic_settings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clinicName` TEXT NOT NULL, `doctorName` TEXT NOT NULL, `childrenNames` TEXT NOT NULL, `staffNames` TEXT NOT NULL, `showFinancialSummary` INTEGER NOT NULL, `showQuickActions` INTEGER NOT NULL, `showCharts` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_years` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `yearName` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `isClosed` INTEGER NOT NULL)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate tables with INTEGER columns instead of REAL, and add indices
        db.execSQL("CREATE TABLE IF NOT EXISTS `treatments_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` INTEGER NOT NULL, `title` TEXT NOT NULL, `cost` INTEGER NOT NULL, `discount` INTEGER NOT NULL, `selectedTeeth` TEXT NOT NULL, `date` INTEGER NOT NULL, `notes` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `treatments_new` SELECT id, patientId, title, CAST(cost AS INTEGER), CAST(discount AS INTEGER), selectedTeeth, date, notes FROM treatments")
        db.execSQL("DROP TABLE treatments")
        db.execSQL("ALTER TABLE treatments_new RENAME TO treatments")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatments_patientId` ON `treatments` (`patientId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatments_date` ON `treatments` (`date`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `payments_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `bankAccountId` INTEGER, `chequeNumber` TEXT, `chequeDueDate` INTEGER, `date` INTEGER NOT NULL, `isCleared` INTEGER NOT NULL, `sayadiId` TEXT, `chequeBank` TEXT, `chequeDrawer` TEXT, `chequePhotoBase64` TEXT)")
        db.execSQL("INSERT INTO `payments_new` SELECT id, patientId, CAST(amount AS INTEGER), type, bankAccountId, chequeNumber, chequeDueDate, date, isCleared, sayadiId, chequeBank, chequeDrawer, chequePhotoBase64 FROM payments")
        db.execSQL("DROP TABLE payments")
        db.execSQL("ALTER TABLE payments_new RENAME TO payments")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_patientId` ON `payments` (`patientId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_chequeDueDate` ON `payments` (`chequeDueDate`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `vendors_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `company` TEXT NOT NULL, `phone` TEXT NOT NULL, `itemType` TEXT NOT NULL, `totalOrders` INTEGER NOT NULL, `totalPaid` INTEGER NOT NULL, `invoiceNumber` TEXT, `invoiceDetails` TEXT, `invoicePhotoBase64` TEXT)")
        db.execSQL("INSERT INTO `vendors_new` SELECT id, name, company, phone, itemType, CAST(totalOrders AS INTEGER), CAST(totalPaid AS INTEGER), invoiceNumber, invoiceDetails, invoicePhotoBase64 FROM vendors")
        db.execSQL("DROP TABLE vendors")
        db.execSQL("ALTER TABLE vendors_new RENAME TO vendors")

        db.execSQL("CREATE TABLE IF NOT EXISTS `lab_orders_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `labName` TEXT NOT NULL, `patientName` TEXT NOT NULL, `toothNumber` TEXT NOT NULL, `prosthesisType` TEXT NOT NULL, `cost` INTEGER NOT NULL, `orderDate` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `status` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `lab_orders_new` SELECT id, labName, patientName, toothNumber, prosthesisType, CAST(cost AS INTEGER), orderDate, dueDate, status FROM lab_orders")
        db.execSQL("DROP TABLE lab_orders")
        db.execSQL("ALTER TABLE lab_orders_new RENAME TO lab_orders")

        db.execSQL("CREATE TABLE IF NOT EXISTS `equipment_maintenance_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `equipmentName` TEXT NOT NULL, `description` TEXT NOT NULL, `cost` INTEGER NOT NULL, `date` INTEGER NOT NULL, `nextServiceDate` INTEGER, `technicianName` TEXT, `invoiceNumber` TEXT)")
        db.execSQL("INSERT INTO `equipment_maintenance_new` SELECT id, equipmentName, description, CAST(cost AS INTEGER), date, nextServiceDate, technicianName, invoiceNumber FROM equipment_maintenance")
        db.execSQL("DROP TABLE equipment_maintenance")
        db.execSQL("ALTER TABLE equipment_maintenance_new RENAME TO equipment_maintenance")

        db.execSQL("CREATE TABLE IF NOT EXISTS `clinic_expenses_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `amount` INTEGER NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, `invoicePhotoBase64` TEXT)")
        db.execSQL("INSERT INTO `clinic_expenses_new` SELECT id, category, CAST(amount AS INTEGER), description, date, invoicePhotoBase64 FROM clinic_expenses")
        db.execSQL("DROP TABLE clinic_expenses")
        db.execSQL("ALTER TABLE clinic_expenses_new RENAME TO clinic_expenses")

        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_transactions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `subAccountName` TEXT NOT NULL, `bankAccountId` INTEGER, `amount` INTEGER NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `personal_transactions_new` SELECT id, type, subAccountName, bankAccountId, CAST(amount AS INTEGER), description, date FROM personal_transactions")
        db.execSQL("DROP TABLE personal_transactions")
        db.execSQL("ALTER TABLE personal_transactions_new RENAME TO personal_transactions")

        db.execSQL("CREATE TABLE IF NOT EXISTS `treatment_templates_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `defaultCost` INTEGER NOT NULL, `defaultNotes` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `treatment_templates_new` SELECT id, title, CAST(defaultCost AS INTEGER), defaultNotes FROM treatment_templates")
        db.execSQL("DROP TABLE treatment_templates")
        db.execSQL("ALTER TABLE treatment_templates_new RENAME TO treatment_templates")

        db.execSQL("CREATE TABLE IF NOT EXISTS `bank_accounts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankName` TEXT NOT NULL, `cardHolder` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, `balance` INTEGER NOT NULL, `smsKeywords` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `bank_accounts_new` SELECT id, bankName, cardHolder, cardNumber, accountNumber, CAST(balance AS INTEGER), smsKeywords FROM bank_accounts")
        db.execSQL("DROP TABLE bank_accounts")
        db.execSQL("ALTER TABLE bank_accounts_new RENAME TO bank_accounts")

        db.execSQL("CREATE TABLE IF NOT EXISTS `children_expenses_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childName` TEXT NOT NULL, `title` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `children_expenses_new` SELECT id, childName, title, CAST(amount AS INTEGER), date FROM children_expenses")
        db.execSQL("DROP TABLE children_expenses")
        db.execSQL("ALTER TABLE children_expenses_new RENAME TO children_expenses")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clinic_settings ADD COLUMN themeName TEXT NOT NULL DEFAULT 'AMBER_CLASSIC'")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clinic_settings ADD COLUMN isDarkMode INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN clinicalFontSizePt INTEGER NOT NULL DEFAULT 11") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN globalFontScaleIndex INTEGER NOT NULL DEFAULT 3") } catch (e: Exception) {}
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE appointments ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE appointments ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("UPDATE clinic_settings SET globalFontScaleIndex = (globalFontScaleIndex * 2) + 2 WHERE globalFontScaleIndex <= 7") } catch (e: Exception) {}
    }
}

@Database(
    entities = [
        Patient::class,
        Treatment::class,
        Payment::class,
        Appointment::class,
        Vendor::class,
        LabOrder::class,
        EquipmentMaintenance::class,
        ClinicExpense::class,
        PersonalTransaction::class,
        TreatmentTemplate::class,
        BankAccount::class,
        PersonalSubAccount::class,
        ChildrenExpense::class,
        ClinicSettings::class,
        LabService::class,
        FixedIncome::class,
        Installment::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun paymentDao(): PaymentDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun vendorDao(): VendorDao
    abstract fun labOrderDao(): LabOrderDao
    abstract fun labServiceDao(): LabServiceDao
    abstract fun equipmentMaintenanceDao(): EquipmentMaintenanceDao
    abstract fun clinicExpenseDao(): ClinicExpenseDao
    abstract fun personalTransactionDao(): PersonalTransactionDao
    abstract fun treatmentTemplateDao(): TreatmentTemplateDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun personalSubAccountDao(): PersonalSubAccountDao
    abstract fun childrenExpenseDao(): ChildrenExpenseDao
    abstract fun clinicSettingsDao(): ClinicSettingsDao
    abstract fun fixedIncomeDao(): FixedIncomeDao
    abstract fun installmentDao(): InstallmentDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dental_pro_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
