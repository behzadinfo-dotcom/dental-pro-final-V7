package com.example.data

import kotlinx.coroutines.flow.Flow

class DentalRepository(private val database: AppDatabase) {

    // Patients
    val allPatients: Flow<List<Patient>> = database.patientDao().getAllPatients()
    suspend fun insertPatient(patient: Patient): Long = database.patientDao().insertPatient(patient)
    suspend fun deletePatient(patient: Patient) = database.patientDao().deletePatient(patient)
    suspend fun deletePatientCascading(patient: Patient) {
        database.treatmentDao().deleteTreatmentsForPatient(patient.id)
        database.paymentDao().deletePaymentsForPatient(patient.id)
        database.appointmentDao().deleteAppointmentsForPatient(patient.id, patient.name)
        database.patientDao().deletePatient(patient)
    }

    // Treatments
    val allTreatments: Flow<List<Treatment>> = database.treatmentDao().getAllTreatments()
    fun getTreatmentsForPatient(patientId: Long): Flow<List<Treatment>> = 
        database.treatmentDao().getTreatmentsForPatient(patientId)
    suspend fun insertTreatment(treatment: Treatment): Long = database.treatmentDao().insertTreatment(treatment)
    suspend fun deleteTreatment(treatment: Treatment) = database.treatmentDao().deleteTreatment(treatment)

    // Payments
    val allPayments: Flow<List<Payment>> = database.paymentDao().getAllPayments()
    fun getPaymentsForPatient(patientId: Long): Flow<List<Payment>> = 
        database.paymentDao().getPaymentsForPatient(patientId)
    suspend fun insertPayment(payment: Payment): Long = database.paymentDao().insertPayment(payment)
    suspend fun updatePayment(payment: Payment) = database.paymentDao().updatePayment(payment)
    suspend fun deletePayment(payment: Payment) = database.paymentDao().deletePayment(payment)

    // Appointments
    val allAppointments: Flow<List<Appointment>> = database.appointmentDao().getAllAppointments()
    suspend fun insertAppointment(appointment: Appointment): Long = database.appointmentDao().insertAppointment(appointment)
    suspend fun updateAppointment(appointment: Appointment) = database.appointmentDao().updateAppointment(appointment)
    suspend fun deleteAppointment(appointment: Appointment) = database.appointmentDao().deleteAppointment(appointment)

    // Vendors
    val allVendors: Flow<List<Vendor>> = database.vendorDao().getAllVendors()
    suspend fun insertVendor(vendor: Vendor): Long = database.vendorDao().insertVendor(vendor)
    suspend fun deleteVendor(vendor: Vendor) = database.vendorDao().deleteVendor(vendor)

    // Lab Orders
    val allLabOrders: Flow<List<LabOrder>> = database.labOrderDao().getAllLabOrders()
    suspend fun insertLabOrder(order: LabOrder): Long = database.labOrderDao().insertLabOrder(order)
    suspend fun deleteLabOrder(order: LabOrder) = database.labOrderDao().deleteLabOrder(order)

    // Lab Services (Catalog & Prices)
    val allLabServices: Flow<List<LabService>> = database.labServiceDao().getAllLabServices()
    suspend fun insertLabService(service: LabService): Long = database.labServiceDao().insertLabService(service)
    suspend fun deleteLabService(service: LabService) = database.labServiceDao().deleteLabService(service)

    // Equipment Maintenance
    val allMaintenance: Flow<List<EquipmentMaintenance>> = database.equipmentMaintenanceDao().getAllMaintenance()
    suspend fun insertMaintenance(maintenance: EquipmentMaintenance): Long = 
        database.equipmentMaintenanceDao().insertMaintenance(maintenance)
    suspend fun deleteMaintenance(maintenance: EquipmentMaintenance) = 
        database.equipmentMaintenanceDao().deleteMaintenance(maintenance)

    // Clinic Expenses
    val allExpenses: Flow<List<ClinicExpense>> = database.clinicExpenseDao().getAllExpenses()
    suspend fun insertExpense(expense: ClinicExpense): Long = database.clinicExpenseDao().insertExpense(expense)
    suspend fun deleteExpense(expense: ClinicExpense) = database.clinicExpenseDao().deleteExpense(expense)

    // Personal Transactions
    val allTransactions: Flow<List<PersonalTransaction>> = database.personalTransactionDao().getAllTransactions()
    suspend fun insertTransaction(tx: PersonalTransaction): Long = database.personalTransactionDao().insertTransaction(tx)
    suspend fun deleteTransaction(tx: PersonalTransaction) = database.personalTransactionDao().deleteTransaction(tx)

    // --- NEW REPO MODULES ---

    // Treatment Templates
    val allTemplates: Flow<List<TreatmentTemplate>> = database.treatmentTemplateDao().getAllTemplates()
    suspend fun insertTemplate(template: TreatmentTemplate): Long = database.treatmentTemplateDao().insertTemplate(template)
    suspend fun deleteTemplate(template: TreatmentTemplate) = database.treatmentTemplateDao().deleteTemplate(template)

    // Bank Accounts
    val allBankAccounts: Flow<List<BankAccount>> = database.bankAccountDao().getAllBankAccounts()
    suspend fun insertBankAccount(account: BankAccount): Long = database.bankAccountDao().insertBankAccount(account)
    suspend fun deleteBankAccount(account: BankAccount) = database.bankAccountDao().deleteBankAccount(account)

    // Personal Sub Accounts
    val allSubAccounts: Flow<List<PersonalSubAccount>> = database.personalSubAccountDao().getAllSubAccounts()
    suspend fun insertSubAccount(sub: PersonalSubAccount): Long = database.personalSubAccountDao().insertSubAccount(sub)
    suspend fun deleteSubAccount(sub: PersonalSubAccount) = database.personalSubAccountDao().deleteSubAccount(sub)

    // Children Expenses
    val allChildrenExpenses: Flow<List<ChildrenExpense>> = database.childrenExpenseDao().getAllChildrenExpenses()
    suspend fun insertChildrenExpense(expense: ChildrenExpense): Long = database.childrenExpenseDao().insertChildrenExpense(expense)
    suspend fun deleteChildrenExpense(expense: ChildrenExpense) = database.childrenExpenseDao().deleteChildrenExpense(expense)

    // Fixed Incomes
    val allFixedIncomes: Flow<List<FixedIncome>> = database.fixedIncomeDao().getAllFixedIncomes()
    suspend fun insertFixedIncome(income: FixedIncome): Long = database.fixedIncomeDao().insertFixedIncome(income)
    suspend fun deleteFixedIncome(income: FixedIncome) = database.fixedIncomeDao().deleteFixedIncome(income)

    // Settings
    val clinicSettings: Flow<ClinicSettings?> = database.clinicSettingsDao().getSettings()
    suspend fun insertSettings(settings: ClinicSettings): Long = database.clinicSettingsDao().insertSettings(settings)

    suspend fun updateGlobalFontScale(index: Int) = database.clinicSettingsDao().updateGlobalFontScale(index)
    suspend fun updateClinicalFontSize(sizePt: Int) = database.clinicSettingsDao().updateClinicalFontSize(sizePt)
    suspend fun updateGeneralSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) = 
        database.clinicSettingsDao().updateGeneralSettings(clinicName, doctorName, staffNames, if(showSummary) 1 else 0, if(showQuick) 1 else 0, if(showCharts) 1 else 0)


    // Installments
    val allInstallments: Flow<List<Installment>> = database.installmentDao().getAllInstallments()
    fun getInstallmentsForPatient(patientId: Long): Flow<List<Installment>> = database.installmentDao().getInstallmentsForPatient(patientId)
    suspend fun insertInstallment(installment: Installment): Long = database.installmentDao().insertInstallment(installment)
    suspend fun updateInstallment(installment: Installment) = database.installmentDao().updateInstallment(installment)
    suspend fun deleteInstallment(installment: Installment) = database.installmentDao().deleteInstallment(installment)
}
