package com.example.ui
import com.example.ui.screens.toEnglishLong

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import android.content.Context

fun parseStagesList(stagesStr: String): List<String> {
    if (stagesStr.isBlank()) return emptyList()
    return stagesStr.split(Regex("""(?:\s*-\s*|\s*,\s*|\s*\n\s*|\s*؛\s*)"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

class DentalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DentalRepository
    private val prefs = application.getSharedPreferences("dental_pro_prefs", Context.MODE_PRIVATE)
    
    
    fun scanRecentSmsForBankTransactions(context: Context) {
        viewModelScope.launch {
            try {
                val cursor = context.contentResolver.query(
                    android.net.Uri.parse("content://sms/inbox"),
                    arrayOf("body", "date", "address"),
                    null,
                    null,
                    "date DESC LIMIT 20"
                )
                cursor?.use {
                    val bodyIndex = it.getColumnIndex("body")
                    while (it.moveToNext()) {
                        if (bodyIndex != -1) {
                            val body = it.getString(bodyIndex)
                            if (body.contains("واریز") || body.contains("انتقال") || body.contains("مبلغ")) {
                                incomingSmsFlow.emit(body)
                                break // Emit only the latest matching one for now
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        val incomingSmsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    }
    
    // UI Language State: "fa" (Persian) or "en" (English)
    val isPersian = MutableStateFlow(true)

    // Currency Unit State: "تومان", "ریال", "دلار ($)"
    val currencyUnit = MutableStateFlow(prefs.getString("currency_unit", "تومان") ?: "تومان")

    fun setCurrencyUnit(unit: String) {
        currencyUnit.value = unit
        prefs.edit().putString("currency_unit", unit).apply()
    }

    fun clearAllClinicalData() {
        viewModelScope.launch {
            patients.value.forEach { repository.deletePatient(it) }
            appointments.value.forEach { repository.deleteAppointment(it) }
            allTreatments.value.forEach { repository.deleteTreatment(it) }
            allPayments.value.forEach { repository.deletePayment(it) }
            vendors.value.forEach { repository.deleteVendor(it) }
            labOrders.value.forEach { repository.deleteLabOrder(it) }
            maintenanceList.value.forEach { repository.deleteMaintenance(it) }
            clinicExpenses.value.forEach { repository.deleteExpense(it) }
            personalTransactions.value.forEach { repository.deleteTransaction(it) }
            selectedPatientId.value = null
        }
    }

    // Security & PIN State
    val isPinSecurityEnabled = MutableStateFlow(prefs.getBoolean("pin_security_enabled", false))
    val securityPinCode = MutableStateFlow(prefs.getString("security_pin", "1234") ?: "1234")
    val isAppUnlocked = MutableStateFlow(!prefs.getBoolean("pin_security_enabled", false))

    fun setPinSecurityEnabled(enabled: Boolean, pin: String) {
        isPinSecurityEnabled.value = enabled
        securityPinCode.value = pin
        prefs.edit().putBoolean("pin_security_enabled", enabled).putString("security_pin", pin).apply()
        if (!enabled) {
            isAppUnlocked.value = true
        }
    }

    fun unlockAppWithPin(enteredPin: String): Boolean {
        if (enteredPin == securityPinCode.value) {
            isAppUnlocked.value = true
            return true
        }
        return false
    }

    fun lockApp() {
        if (isPinSecurityEnabled.value) {
            isAppUnlocked.value = false
        }
    }

    // Auto Backup Settings
    val autoBackupInterval = MutableStateFlow(prefs.getString("auto_backup_interval", "DAILY") ?: "DAILY")
    val lastBackupTimestamp = MutableStateFlow(prefs.getLong("last_backup_time", System.currentTimeMillis()))

    fun setAutoBackupInterval(interval: String) {
        autoBackupInterval.value = interval
        prefs.edit().putString("auto_backup_interval", interval).apply()
    }

    fun recordBackupPerformed() {
        val now = System.currentTimeMillis()
        lastBackupTimestamp.value = now
        prefs.edit().putLong("last_backup_time", now).apply()
    }

    suspend fun generateBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("app", "DentalPro ERP")
        
        // Export Patients
        val patientArray = org.json.JSONArray()
        patients.value.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("phone", p.phone)
            obj.put("medicalHistory", p.medicalHistory)
            obj.put("note", p.note)
            patientArray.put(obj)
        }
        root.put("patients", patientArray)

        // Export Treatments
        val treatmentArray = org.json.JSONArray()
        allTreatments.value.forEach { t ->
            val obj = org.json.JSONObject()
            obj.put("id", t.id)
            obj.put("patientId", t.patientId)
            obj.put("title", t.title)
            obj.put("cost", t.cost)
            obj.put("discount", t.discount)
            obj.put("selectedTeeth", t.selectedTeeth)
            obj.put("notes", t.notes)
            obj.put("status", t.status)
            obj.put("stages", t.stages)
            obj.put("completedStages", t.completedStages)
            treatmentArray.put(obj)
        }
        root.put("treatments", treatmentArray)

        // Export Payments
        val paymentArray = org.json.JSONArray()
        allPayments.value.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("id", p.id)
            obj.put("patientId", p.patientId)
            obj.put("amount", p.amount)
            obj.put("type", p.type)
            obj.put("chequeNumber", p.chequeNumber)
            obj.put("chequeDueDate", p.chequeDueDate)
            obj.put("isCleared", p.isCleared)
            obj.put("bankAccountId", p.bankAccountId)
            paymentArray.put(obj)
        }
        root.put("payments", paymentArray)

        // Export Appointments
        val apptArray = org.json.JSONArray()
        appointments.value.forEach { a ->
            val obj = org.json.JSONObject()
            obj.put("id", a.id)
            obj.put("patientId", a.patientId)
            obj.put("patientName", a.patientName)
            obj.put("date", a.date)
            obj.put("treatmentType", a.treatmentType)
            obj.put("note", a.note)
            apptArray.put(obj)
        }
        root.put("appointments", apptArray)

        // Export Templates
        val templateArray = org.json.JSONArray()
        templates.value.forEach { temp ->
            val obj = org.json.JSONObject()
            obj.put("id", temp.id)
            obj.put("title", temp.title)
            obj.put("defaultCost", temp.defaultCost)
            obj.put("defaultNotes", temp.defaultNotes)
            obj.put("stages", temp.stages)
            templateArray.put(obj)
        }
        root.put("templates", templateArray)

        recordBackupPerformed()
        return root.toString(2)
    }

    suspend fun restoreFromBackupJson(jsonStr: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonStr)
            
            if (root.has("patients")) {
                val array = root.getJSONArray("patients")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertPatient(Patient(
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        medicalHistory = obj.optString("medicalHistory", ""),
                        note = obj.optString("note", "")
                    ))
                }
            }

            if (root.has("templates")) {
                val array = root.getJSONArray("templates")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertTemplate(TreatmentTemplate(
                        title = obj.optString("title", ""),
                        defaultCost = obj.optLong("defaultCost", 0L),
                        defaultNotes = obj.optString("defaultNotes", ""),
                        stages = obj.optString("stages", "")
                    ))
                }
            }

            if (root.has("appointments")) {
                val array = root.getJSONArray("appointments")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertAppointment(Appointment(
                        patientId = obj.optLong("patientId", 0L),
                        patientName = obj.optString("patientName", ""),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        treatmentType = obj.optString("treatmentType", ""),
                        note = obj.optString("note", "")
                    ))
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    // Current Active Tab
    val currentTab = MutableStateFlow("dashboard")
    // آیا بخش «تنظیمات اختصاصی» صفحه تنظیمات همین حالا نمایان است؟ فقط با لمس ۲ثانیه‌ای چرخ‌دنده true می‌شود
    val advancedSettingsRevealed = MutableStateFlow(false)
    val currentViewingImage = MutableStateFlow<String?>(null) // dashboard, patients, appointments, vendors, labs, maintenance, expenses, personal, more

    // Selected Patient for Record Details
    val selectedPatientId = MutableStateFlow<Long?>(null)

    init {
        isPersian.value = prefs.getBoolean("is_persian", true)
        val database = AppDatabase.getDatabase(application)
        repository = DentalRepository(database)
        
        // Seed sample data if the patient list is empty to provide a rich visual start
        viewModelScope.launch {
            repository.allPatients.first().let { list ->
                if (list.isEmpty()) {
                    seedSampleData()
                }
            }
            repository.allTemplates.first().let { list ->
                if (list.isEmpty()) {
                    seedDefaultTemplatesAndSettings()
                }
            }
        }
    }

    // Streams from Repository
    val patients = repository.allPatients.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val appointments = repository.allAppointments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val vendors = repository.allVendors.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val labOrders = repository.allLabOrders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val labServices = repository.allLabServices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val maintenanceList = repository.allMaintenance.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val clinicExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val personalTransactions = repository.allTransactions.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTreatments = repository.allTreatments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPayments = repository.allPayments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // NEW STREAMS FOR COMPREHENSIVE ERP
    val templates = repository.allTemplates.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val bankAccounts = repository.allBankAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val personalSubAccounts = repository.allSubAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val childrenExpenses = repository.allChildrenExpenses.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val fixedIncomes = repository.allFixedIncomes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val settings = repository.clinicSettings.map { it ?: ClinicSettings() }.stateIn(viewModelScope, SharingStarted.Lazily, ClinicSettings())
    val allInstallments = repository.allInstallments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addInstallment(patientId: Long, treatmentId: Long?, title: String, amount: Long, dueDate: Long, note: String) {
        viewModelScope.launch {
            repository.insertInstallment(
                Installment(
                    patientId = patientId,
                    treatmentId = treatmentId,
                    title = title,
                    amount = amount,
                    dueDate = dueDate,
                    note = note
                )
            )
        }
    }

    fun markInstallmentPaid(installment: Installment) {
        viewModelScope.launch {
            repository.updateInstallment(
                installment.copy(isPaid = true, paidDate = System.currentTimeMillis())
            )
            repository.insertPayment(
                Payment(
                    patientId = installment.patientId,
                    amount = installment.amount,
                    type = "CARD",
                    date = System.currentTimeMillis(),
                    isCleared = true,
                    chequeDrawer = "پرداخت قسط: ${installment.title}"
                )
            )
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.deleteInstallment(installment)
        }
    }

    fun processClinicalAiPrompt(
        promptText: String,
        onResult: (Boolean, ParsedClinicalResponse?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = GeminiClient.parseClinicalPrompt(promptText)
                if (response != null && response.proposals.isNotEmpty()) {
                    onResult(true, response)
                } else {
                    onResult(false, response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, null)
            }
        }
    }

    fun analyzeRadiographyScan(
        imagePathOrBase64: String,
        onResult: (Boolean, RadiographyScanResult?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = GeminiClient.analyzeRadiographyScan(imagePathOrBase64)
                if (result != null) {
                    onResult(true, result)
                } else {
                    onResult(false, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, null)
            }
        }
    }

    fun generatePostTreatmentCare(
        patientName: String,
        treatmentTitle: String,
        teeth: String,
        notes: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val careText = GeminiClient.generatePostTreatmentCareInstructions(patientName, treatmentTitle, teeth, notes)
                if (!careText.isNullOrBlank()) {
                    onResult(true, careText)
                } else {
                    onResult(false, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, null)
            }
        }
    }

    init {
        // Runs the automatic monthly fixed-income deposit check once fixedIncomes/bankAccounts streams are ready
        viewModelScope.launch {
            fixedIncomes.first()
            checkAndProcessFixedIncomes()
        }
    }

    // Selected Patient's Specific Treatments & Payments
    val selectedPatient = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else patients.map { list -> list.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val selectedPatientTreatments = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getTreatmentsForPatient(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedPatientPayments = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getPaymentsForPatient(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Toggle Language
    fun toggleLanguage() {
        isPersian.value = !isPersian.value
        prefs.edit().putBoolean("is_persian", isPersian.value).apply()
    }

    // Add Patient
    fun addPatient(name: String, phone: String, note: String, photoUri: String? = null) {
        viewModelScope.launch {
            val id = repository.insertPatient(Patient(name = name, phone = phone, note = note, photoUri = photoUri))
            selectedPatientId.value = id
        }
    }

    // Add Patient Full Detail (with Gender, Medical Alerts, Self-Declaration Form, Age & BirthDate)
    fun addPatientFull(
        name: String,
        phone: String = "",
        email: String = "",
        address: String = "",
        medicalHistory: String = "",
        note: String = "",
        photoUri: String? = null,
        documentUris: String = "",
        gender: String = "مرد",
        medicalAlerts: String = "",
        selfDeclarationJson: String = "",
        age: Int = 0,
        birthDate: String = "",
        preferredMessenger: String = "واتساپ"
    ) {
        viewModelScope.launch {
            val id = repository.insertPatient(
                Patient(
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    medicalHistory = medicalHistory,
                    note = note,
                    photoUri = photoUri,
                    documentUris = documentUris,
                    gender = gender,
                    medicalAlerts = medicalAlerts,
                    selfDeclarationJson = selfDeclarationJson,
                    age = age,
                    birthDate = birthDate,
                    preferredMessenger = preferredMessenger
                )
            )
            selectedPatientId.value = id
        }
    }

    // Update Patient Details
    fun updatePatient(patient: Patient) {
        viewModelScope.launch {
            repository.insertPatient(patient)
        }
    }

    // Delete Patient Cascading (completely clean database records)
    fun deletePatient(patient: Patient) {
        viewModelScope.launch {
            if (selectedPatientId.value == patient.id) {
                selectedPatientId.value = null
            }
            repository.deletePatientCascading(patient)
        }
    }

    // Add Treatment with Tooth selection & discount
    fun addTreatment(patientId: Long, title: String, cost: Long, notes: String = "") {
        viewModelScope.launch {
            repository.insertTreatment(Treatment(patientId = patientId, title = title, cost = cost, notes = notes))
        }
    }

    fun addTreatmentWithDetails(patientId: Long, title: String, cost: Long, discount: Long, selectedTeeth: String, notes: String = "", stages: String = "") {
        viewModelScope.launch {
            var finalStages = stages
            if (finalStages.isBlank()) {
                val matchedTemp = templates.value.find { temp ->
                    title.contains(temp.title) || temp.title.contains(title)
                }
                if (matchedTemp != null && matchedTemp.stages.isNotBlank()) {
                    finalStages = matchedTemp.stages
                }
            }
            val teethList = selectedTeeth.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (teethList.isNotEmpty()) {
                val unitDiscount = discount / teethList.size
                teethList.forEach { tooth ->
                    repository.insertTreatment(
                        Treatment(
                            patientId = patientId,
                            title = "$title (دندان $tooth)",
                            cost = cost,
                            discount = unitDiscount,
                            selectedTeeth = tooth,
                            notes = notes,
                            status = "PLANNED",
                            stages = finalStages
                        )
                    )
                }
            } else {
                repository.insertTreatment(
                    Treatment(
                        patientId = patientId,
                        title = title,
                        cost = cost,
                        discount = discount,
                        selectedTeeth = selectedTeeth,
                        notes = notes,
                        status = "PLANNED",
                        stages = finalStages
                    )
                )
            }
        }
    }

    fun updateTreatmentStatus(treatment: Treatment, newStatus: String) {
        viewModelScope.launch {
            repository.insertTreatment(treatment.copy(status = newStatus))
        }
    }

    // Delete Treatment
    fun deleteTreatment(treatment: Treatment) {
        viewModelScope.launch {
            repository.deleteTreatment(treatment)
        }
    }

    /** Restores a treatment that was just deleted (used by the Undo snackbar action). */
    fun undoDeleteTreatment(treatment: Treatment) {
        viewModelScope.launch {
            repository.insertTreatment(treatment)
        }
    }

    // Add Payment with Bank Account mapping
    fun addPayment(
        patientId: Long,
        amount: Long,
        type: String,
        chequeNum: String? = null,
        chequeDue: Long? = null,
        sayadiId: String? = null,
        chequeBank: String? = null,
        chequeDrawer: String? = null,
        chequePhotoBase64: String? = null
    ) {
        viewModelScope.launch {
            repository.insertPayment(
                Payment(
                    patientId = patientId,
                    amount = amount,
                    type = type,
                    chequeNumber = chequeNum,
                    chequeDueDate = chequeDue,
                    isCleared = type != "CHEQUE",
                    sayadiId = sayadiId,
                    chequeBank = chequeBank,
                    chequeDrawer = chequeDrawer,
                    chequePhotoBase64 = chequePhotoBase64
                )
            )
        }
    }

    fun addPaymentWithAccount(
        patientId: Long,
        amount: Long,
        type: String,
        bankAccountId: Long?,
        chequeNum: String? = null,
        chequeDue: Long? = null,
        sayadiId: String? = null,
        chequeBank: String? = null,
        chequeDrawer: String? = null,
        chequePhotoBase64: String? = null
    ) {
        viewModelScope.launch {
            repository.insertPayment(
                Payment(
                    patientId = patientId,
                    amount = amount,
                    type = type,
                    bankAccountId = bankAccountId,
                    chequeNumber = chequeNum,
                    chequeDueDate = chequeDue,
                    isCleared = type != "CHEQUE",
                    sayadiId = sayadiId,
                    chequeBank = chequeBank,
                    chequeDrawer = chequeDrawer,
                    chequePhotoBase64 = chequePhotoBase64
                )
            )
            // Add to bank account balance if specified and cleared
            if (bankAccountId != null && type != "CHEQUE") {
                adjustBankAccountBalance(bankAccountId, amount)
            }
        }
    }

    // Update Payment
    fun updatePayment(payment: Payment) {
        viewModelScope.launch {
            repository.updatePayment(payment)
        }
    }

    // Delete Payment
    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            if (payment.bankAccountId != null && payment.type != "CHEQUE") {
                adjustBankAccountBalance(payment.bankAccountId, -payment.amount)
            }
        }
    }

    /** Restores a payment that was just deleted (used by the Undo snackbar action). */
    fun undoDeletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.insertPayment(payment)
            if (payment.bankAccountId != null && payment.type != "CHEQUE") {
                adjustBankAccountBalance(payment.bankAccountId, payment.amount)
            }
        }
    }

    // Add Appointment
    fun addAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.insertAppointment(appointment)
        }
    }

    
    fun updateAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.updateAppointment(appointment)
        }
    }

    // Delete Appointment & Sync linked therapy status
    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.deleteAppointment(appointment)
            val txIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            txIds.forEach { id ->
                val tx = allTreatments.value.find { it.id == id }
                if (tx != null && tx.status != "COMPLETED") {
                    repository.insertTreatment(tx.copy(status = "PLANNED"))
                }
            }
            refreshSelectedPatient()
        }
    }

    /** Restores an appointment that was just deleted (used by the Undo snackbar action). */
    fun undoDeleteAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.insertAppointment(appointment)
        }
    }

    // Add Vendor (with invoice number/details/photo support)
    fun addVendor(name: String, company: String, phone: String, itemType: String, totalOrders: Long, totalPaid: Long, invoicePhotoBase64: String? = null) {
        viewModelScope.launch {
            repository.insertVendor(
                Vendor(
                    name = name,
                    company = company,
                    phone = phone,
                    itemType = itemType,
                    totalOrders = totalOrders,
                    totalPaid = totalPaid,
                    invoicePhotoBase64 = invoicePhotoBase64
                )
            )
        }
    }

    fun addVendorWithInvoice(name: String, company: String, phone: String, itemType: String, totalOrders: Long, totalPaid: Long, invoiceNum: String?, invoiceDetails: String?) {
        viewModelScope.launch {
            repository.insertVendor(
                Vendor(
                    name = name,
                    company = company,
                    phone = phone,
                    itemType = itemType,
                    totalOrders = totalOrders,
                    totalPaid = totalPaid,
                    invoiceNumber = invoiceNum,
                    invoiceDetails = invoiceDetails
                )
            )
        }
    }

    // Delete Vendor
    fun deleteVendor(vendor: Vendor) {
        viewModelScope.launch {
            repository.deleteVendor(vendor)
        }
    }

    // Add Lab Order
    fun addLabOrder(labName: String, patientName: String, toothNum: String, prosthesis: String, cost: Long, due: Long) {
        viewModelScope.launch {
            repository.insertLabOrder(
                LabOrder(
                    labName = labName,
                    patientName = patientName,
                    toothNumber = toothNum,
                    prosthesisType = prosthesis,
                    cost = cost,
                    dueDate = due,
                    status = "ORDERED"
                )
            )
        }
    }

    // Update Lab Order Status
    fun updateLabOrderStatus(order: LabOrder, newStatus: String) {
        viewModelScope.launch {
            repository.insertLabOrder(order.copy(status = newStatus))
        }
    }

    // Delete Lab Order
    fun deleteLabOrder(order: LabOrder) {
        viewModelScope.launch {
            repository.deleteLabOrder(order)
        }
    }

    // Lab Services Catalog Management
    fun addLabService(
        labName: String,
        serviceName: String,
        price: Long,
        notes: String = "",
        frameDeliveryDays: Int = 3,
        finalDeliveryDays: Int = 7
    ) {
        viewModelScope.launch {
            repository.insertLabService(
                LabService(
                    labName = labName,
                    serviceName = serviceName,
                    price = price,
                    notes = notes,
                    frameDeliveryDays = frameDeliveryDays,
                    finalDeliveryDays = finalDeliveryDays
                )
            )
        }
    }

    fun updateLabService(service: LabService) {
        viewModelScope.launch {
            repository.insertLabService(service)
        }
    }

    fun deleteLabService(service: LabService) {
        viewModelScope.launch {
            repository.deleteLabService(service)
        }
    }

    // Integrated Prosthesis Order & Automatic Expense Posting
    fun addProsthesisOrderAndExpense(
        labName: String,
        patientName: String,
        toothNum: String,
        prosthesis: String,
        cost: Long,
        dueDate: Long
    ) {
        viewModelScope.launch {
            val newOrderId = repository.insertLabOrder(
                LabOrder(
                    labName = labName,
                    patientName = patientName,
                    toothNumber = toothNum,
                    prosthesisType = prosthesis,
                    cost = cost,
                    orderDate = System.currentTimeMillis(),
                    dueDate = dueDate,
                    status = "ORDERED"
                )
            )
            // Immediately post as Clinic Expense debt under category "لابراتوار"
            // با ثبت labOrderId، این هزینه در صفحه هزینه‌ها هم قابلیت نمایش بعنوان فاکتور رسمی همین سفارش را دارد
            val expDesc = "سفارش پروتز ($prosthesis) - بیمار: $patientName - دندان: $toothNum - لابراتوار: $labName"
            repository.insertExpense(
                ClinicExpense(
                    category = "لابراتوار",
                    amount = cost,
                    description = expDesc,
                    date = System.currentTimeMillis(),
                    subAccountName = labName,
                    labOrderId = newOrderId
                )
            )
        }
    }

    // Cheque Transfer Helpers
    fun transferChequeToExpense(payment: Payment, recipientName: String, category: String = "سایر") {
        viewModelScope.launch {
            repository.updatePayment(
                payment.copy(
                    chequeStatus = "TRANSFERRED",
                    transferredTo = recipientName,
                    transferDate = System.currentTimeMillis()
                )
            )
            val desc = "انتقال چک شماره ${payment.chequeNumber ?: ""} (بانک ${payment.chequeBank ?: ""}) به $recipientName"
            repository.insertExpense(
                ClinicExpense(
                    category = category,
                    amount = payment.amount,
                    description = desc,
                    paymentMethod = "CHEQUE_TRANSFER",
                    chequeNumber = payment.chequeNumber
                )
            )
        }
    }

    fun transferChequeToPersonal(payment: Payment, recipientName: String, subAccountName: String = "") {
        viewModelScope.launch {
            repository.updatePayment(
                payment.copy(
                    chequeStatus = "TRANSFERRED",
                    transferredTo = recipientName,
                    transferDate = System.currentTimeMillis()
                )
            )
            val desc = "انتقال چک شماره ${payment.chequeNumber ?: ""} (بانک ${payment.chequeBank ?: ""}) بابت $recipientName"
            repository.insertTransaction(
                PersonalTransaction(
                    type = "EXPENSE",
                    subAccountName = subAccountName.ifBlank { "انتقال چک" },
                    amount = payment.amount,
                    description = desc,
                    paymentMethod = "CHEQUE_TRANSFER",
                    chequeNumber = payment.chequeNumber
                )
            )
        }
    }

    // Fixed Incomes Management & Automatic Processing
    fun addFixedIncome(title: String, amount: Long, dayOfMonth: Int, bankAccountId: Long?, bankAccountName: String, subAccountName: String) {
        viewModelScope.launch {
            repository.insertFixedIncome(
                FixedIncome(
                    title = title,
                    amount = amount,
                    dayOfMonth = dayOfMonth,
                    bankAccountId = bankAccountId,
                    bankAccountName = bankAccountName,
                    subAccountName = subAccountName
                )
            )
        }
    }

    fun deleteFixedIncome(income: FixedIncome) {
        viewModelScope.launch {
            repository.deleteFixedIncome(income)
        }
    }

    fun checkAndProcessFixedIncomes() {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val shamsiDate = toShamsiDate(System.currentTimeMillis())
            val currentYearMonth = if (shamsiDate.length >= 7) shamsiDate.substring(0, 7) else "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}"

            fixedIncomes.value.filter { it.isActive }.forEach { income ->
                if (income.lastProcessedMonth != currentYearMonth && currentDay >= income.dayOfMonth) {
                    repository.insertTransaction(
                        PersonalTransaction(
                            type = "INCOME",
                            subAccountName = income.subAccountName.ifBlank { "درآمد ثابت" },
                            bankAccountId = income.bankAccountId,
                            amount = income.amount,
                            description = "واریز خودکار درآمد ثابت ماهانه: ${income.title}",
                            paymentMethod = "CARD_TRANSFER"
                        )
                    )
                    income.bankAccountId?.let { bId ->
                        val bankAcc = bankAccounts.value.find { it.id == bId }
                        if (bankAcc != null) {
                            repository.insertBankAccount(bankAcc.copy(balance = bankAcc.balance + income.amount))
                        }
                    }
                    repository.insertFixedIncome(income.copy(lastProcessedMonth = currentYearMonth))
                }
            }
        }
    }

    // Appointment Archive Helper
    fun archiveAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.updateAppointment(appointment.copy(isCompleted = true, isArchived = true))
            val txIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            txIds.forEach { id ->
                val tx = allTreatments.value.find { it.id == id }
                if (tx != null) {
                    repository.insertTreatment(tx.copy(status = "COMPLETED"))
                }
            }
        }
    }

    // Add Maintenance Record (with invoice and tech support)
    fun addMaintenance(equipment: String, desc: String, cost: Long, date: Long, nextCheck: Long?) {
        viewModelScope.launch {
            repository.insertMaintenance(
                EquipmentMaintenance(
                    equipmentName = equipment,
                    description = desc,
                    cost = cost,
                    date = date,
                    nextServiceDate = nextCheck
                )
            )
        }
    }

    fun addMaintenanceWithInvoice(equipment: String, desc: String, cost: Long, date: Long, nextCheck: Long?, technician: String?, invoiceNum: String?) {
        viewModelScope.launch {
            repository.insertMaintenance(
                EquipmentMaintenance(
                    equipmentName = equipment,
                    description = desc,
                    cost = cost,
                    date = date,
                    nextServiceDate = nextCheck,
                    technicianName = technician,
                    invoiceNumber = invoiceNum
                )
            )
        }
    }

    // Delete Maintenance Record
    fun deleteMaintenance(record: EquipmentMaintenance) {
        viewModelScope.launch {
            repository.deleteMaintenance(record)
        }
    }

    // Update Vendor
    fun updateVendor(vendor: Vendor) {
        viewModelScope.launch {
            repository.insertVendor(vendor)
        }
    }

    // Add Clinic Expense
    fun addExpense(
        category: String,
        amount: Long,
        desc: String,
        paymentMethod: String = "CARD_TRANSFER",
        subAccountName: String = "",
        chequeNumber: String? = null
    ) {
        viewModelScope.launch {
            repository.insertExpense(
                ClinicExpense(
                    category = category,
                    amount = amount,
                    description = desc,
                    paymentMethod = paymentMethod,
                    subAccountName = subAccountName,
                    chequeNumber = chequeNumber
                )
            )
        }
    }

    // Delete Clinic Expense
    fun deleteExpense(expense: ClinicExpense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // Add Personal Transaction with Sub-Account & Bank Card mappings
    fun addPersonalTx(
        type: String,
        amount: Long,
        desc: String,
        paymentMethod: String = "CARD_TRANSFER",
        subAccountName: String = "",
        chequeNumber: String? = null
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                PersonalTransaction(
                    type = type,
                    amount = amount,
                    description = desc,
                    paymentMethod = paymentMethod,
                    subAccountName = subAccountName,
                    chequeNumber = chequeNumber
                )
            )
        }
    }

    fun addPersonalTxDetailed(type: String, subAccountName: String, bankAccountId: Long?, amount: Long, desc: String) {
        viewModelScope.launch {
            repository.insertTransaction(
                PersonalTransaction(
                    type = type,
                    subAccountName = subAccountName,
                    bankAccountId = bankAccountId,
                    amount = amount,
                    description = desc
                )
            )
            // Adjust Bank Balance (Income increases, Expense reduces, Draw increases/decreases based on business perspective)
            if (bankAccountId != null) {
                val adjustment = if (type == "INCOME" || type == "DRAW") amount else -amount
                adjustBankAccountBalance(bankAccountId, adjustment)
            }
        }
    }

    // Delete Personal Transaction
    fun deletePersonalTx(tx: PersonalTransaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            if (tx.bankAccountId != null) {
                val adjustment = if (tx.type == "INCOME" || tx.type == "DRAW") -tx.amount else tx.amount
                adjustBankAccountBalance(tx.bankAccountId, adjustment)
            }
        }
    }

    // --- OPERATIONS ON THE NEW ERPS ---

    // Treatment Templates
    fun addTemplate(title: String, defaultCost: Long, defaultNotes: String, stages: String = "", isStarred: Boolean = true) {
        viewModelScope.launch {
            repository.insertTemplate(TreatmentTemplate(title = title, defaultCost = defaultCost, defaultNotes = defaultNotes, stages = stages, isStarred = isStarred))
        }
    }

    fun updateTemplate(template: TreatmentTemplate) {
        viewModelScope.launch {
            repository.insertTemplate(template)
        }
    }

    fun toggleTemplateStar(template: TreatmentTemplate) {
        viewModelScope.launch {
            repository.insertTemplate(template.copy(isStarred = !template.isStarred))
        }
    }

    fun deleteTemplate(template: TreatmentTemplate) {
        viewModelScope.launch {
            repository.deleteTemplate(template)
        }
    }

    // Bank Accounts
    fun addBankAccount(bankName: String, cardHolder: String, cardNumber: String, accountNumber: String, balance: Long, smsKeywords: String) {
        viewModelScope.launch {
            repository.insertBankAccount(
                BankAccount(
                    bankName = bankName,
                    cardHolder = cardHolder,
                    cardNumber = cardNumber,
                    accountNumber = accountNumber,
                    balance = balance,
                    smsKeywords = smsKeywords
                )
            )
        }
    }

    fun updateBankAccount(account: BankAccount) {
        viewModelScope.launch {
            repository.insertBankAccount(account)
        }
    }

    fun deleteBankAccount(account: BankAccount) {
        viewModelScope.launch {
            repository.deleteBankAccount(account)
        }
    }

    private suspend fun adjustBankAccountBalance(id: Long, delta: Long) {
        val currentAccounts = bankAccounts.value
        val match = currentAccounts.find { it.id == id }
        if (match != null) {
            repository.insertBankAccount(match.copy(balance = match.balance + delta))
        }
    }

    // Personal Sub Accounts
    fun addPersonalSubAccount(name: String, category: String) {
        viewModelScope.launch {
            repository.insertSubAccount(PersonalSubAccount(name = name, category = category))
        }
    }

    fun updatePersonalSubAccount(sub: PersonalSubAccount) {
        viewModelScope.launch {
            repository.insertSubAccount(sub)
        }
    }

    fun deletePersonalSubAccount(sub: PersonalSubAccount) {
        viewModelScope.launch {
            repository.deleteSubAccount(sub)
        }
    }

    // Children Expenses
    fun addChildrenExpense(childName: String, title: String, amount: Long) {
        viewModelScope.launch {
            repository.insertChildrenExpense(ChildrenExpense(childName = childName, title = title, amount = amount))
        }
    }

    fun deleteChildrenExpense(expense: ChildrenExpense) {
        viewModelScope.launch {
            repository.deleteChildrenExpense(expense)
        }
    }

    // Settings
        fun updateSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) {
        viewModelScope.launch {
            repository.updateGeneralSettings(clinicName, doctorName, staffNames, showSummary, showQuick, showCharts)
        }
    }

    // ذخیره پایدار آخرین سایز فونت صفحات شرح بالینی (دستورالعمل بالینی/ممنوعیت‌های درمانی)
    fun updateClinicalFontSize(sizePt: Int) {
        viewModelScope.launch {
            repository.updateClinicalFontSize(sizePt)
        }
    }

    // لغزنده ۸پله‌ای سایز فونت کلی برنامه (۰..۷) — تنظیمات اختصاصی
    fun updateGlobalFontScale(index: Int) {
        viewModelScope.launch {
            repository.updateGlobalFontScale(index.coerceIn(0, 19))
        }
    }

    // دکمه «به‌روزرسانی» در برگ بیمار — فلوهای Room خودکار به‌روز می‌شوند؛
    // این تابع صرفاً یک re-subscribe اجباری برای اطمینان کاربر انجام می‌دهد
    fun refreshSelectedPatient() {
        viewModelScope.launch {
            val id = selectedPatientId.value
            if (id != null) {
                selectedPatientId.value = null
                selectedPatientId.value = id
            }
        }
    }

    // Instantly switch the app's neon theme without touching any other setting
    fun updateTheme(themeId: String) {
        viewModelScope.launch {
            repository.insertSettings(settings.value.copy(id = 1, themeName = themeId))
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.insertSettings(settings.value.copy(id = 1, isDarkMode = enabled))
        }
    }

    // SMS Parsing Engine
    // Automatically analyzes incoming mock/actual SMS texts to detect card transfers
    fun parseBankSms(smsText: String): SmsParseResult? {
        if (smsText.isBlank()) return null
        
        // Find amount (extract numbers from sms)
        val cleanText = smsText.replace(",", "").replace("،", "")
        val regexNumbers = "\\b\\d{4,10}\\b".toRegex()
        val matchResults = regexNumbers.findAll(cleanText).map { it.value.toEnglishLong() }.toList()
        
        val detectedAmount = matchResults.firstOrNull { it > 1000L } ?: 0L
        
        // Try to match a bank account by keywords or credit/debit numbers
        var matchedAccount: BankAccount? = null
        for (account in bankAccounts.value) {
            val keywords = account.smsKeywords.split(",")
            val hasKeyword = keywords.any { kw -> kw.trim().isNotEmpty() && smsText.contains(kw.trim()) }
            val hasCardNo = account.cardNumber.takeLast(4).let { suffix -> suffix.isNotEmpty() && smsText.contains(suffix) }
            
            if (hasKeyword || hasCardNo) {
                matchedAccount = account
                break
            }
        }
        
        // Return structured result to offer semi-automatic registration
        return SmsParseResult(
            amount = detectedAmount,
            detectedAccount = matchedAccount,
            originalText = smsText,
            isDeposit = smsText.contains("واریز") || smsText.contains("آمد به") || smsText.contains("افزایش") || smsText.contains("deposit")
        )
    }

    // Dental Chart System Standard (FDI, Universal, Palmer)
    val dentalChartSystem = MutableStateFlow(prefs.getString("dental_chart_system", "FDI") ?: "FDI")

    fun setDentalChartSystem(system: String) {
        dentalChartSystem.value = system
        prefs.edit().putString("dental_chart_system", system).apply()
    }

    // Calendar Type: "SHAMSI", "QAMARI", "MILADI"
    val calendarType = MutableStateFlow(prefs.getString("calendar_type", "SHAMSI") ?: "SHAMSI")

    fun setCalendarType(type: String) {
        calendarType.value = type
        prefs.edit().putString("calendar_type", type).apply()
    }

    // Date Formatter Helpers for Shamsi, Qamari, and Miladi
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return when (calendarType.value) {
            "QAMARI" -> toQamariDate(timestamp)
            "MILADI" -> toMiladiDate(timestamp)
            else -> toShamsiDate(timestamp)
        }
    }

    fun toShamsiDate(timestamp: Long): String {
        try {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val gYear = cal.get(java.util.Calendar.YEAR)
            val gMonth = cal.get(java.util.Calendar.MONTH) + 1
            val gDay = cal.get(java.util.Calendar.DAY_OF_MONTH)

            val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

            var gy = gYear - 1600
            var gm = gMonth - 1
            var gd = gDay - 1

            var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
            for (i in 0 until gm) {
                gDayNo += gDaysInMonth[i]
            }
            if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            while (jm < 11 && jDayNo >= jDaysInMonth[jm]) {
                jDayNo -= jDaysInMonth[jm]
                jm++
            }
            val jd = jDayNo + 1

            val shamsiMonths = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
            val monthName = if (isPersian.value) shamsiMonths.getOrElse(jm) { "" } else "${jm + 1}"
            return if (isPersian.value) "$jd $monthName $jy" else "$jy/${jm + 1}/$jd"
        } catch (e: Exception) {
            return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun toQamariDate(timestamp: Long): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val ld = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val hijriDate = java.time.chrono.Chronology.of("Hijrah-umalqura").date(ld)
                val hYear = hijriDate.get(java.time.temporal.ChronoField.YEAR)
                val hMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                val hDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                val qamariMonths = listOf("محرم", "صفر", "ربيع‌الأول", "ربيع‌الثاني", "جمادى‌الأولى", "جمادى‌الثانية", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
                val monthName = qamariMonths.getOrElse(hMonth - 1) { "" }
                if (isPersian.value) "$hDay $monthName $hYear (قمری)" else "$hYear/${hMonth}/$hDay (Hijri)"
            } else {
                SimpleDateFormat("yyyy/MM/dd (قمری)", Locale.getDefault()).format(Date(timestamp))
            }
        } catch (e: Exception) {
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun toMiladiDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = if (isPersian.value) "yyyy/MM/dd (میلادی)" else "MMM dd, yyyy"
        return SimpleDateFormat(format, Locale.getDefault()).format(date)
    }

    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    fun formatDateFullVerbal(timestamp: Long): String {
        return try {
            val calType = calendarType.value
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val zdt = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                val ld = zdt.toLocalDate()
                val dayOfWeekNumber = ld.dayOfWeek.value // 1=Mon, 7=Sun
                val dayOfWeekNamesPersian = mapOf(
                    1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنج‌شنبه", 5 to "جمعه", 6 to "شنبه", 7 to "یکشنبه"
                )
                val weekdayStr = dayOfWeekNamesPersian[dayOfWeekNumber] ?: ""

                when (calType) {
                    "SHAMSI" -> {
                        val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
                        var year = ld.year
                        var dayOfYear = ld.dayOfYear
                        val isLeap = ld.isLeapYear
                        var sYear = year - 621
                        var sMonth = 1
                        var sDay = 1
                        val daysBefore = if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 79 else 78
                        if (dayOfYear > daysBefore) {
                            dayOfYear -= daysBefore
                            if (dayOfYear <= 186) {
                                sMonth = 1 + (dayOfYear - 1) / 31
                                sDay = 1 + (dayOfYear - 1) % 31
                            } else {
                                dayOfYear -= 186
                                sMonth = 7 + (dayOfYear - 1) / 30
                                sDay = 1 + (dayOfYear - 1) % 30
                            }
                        } else {
                            sYear -= 1
                            dayOfYear += if (isLeap) 287 else 286
                            if (dayOfYear <= 186) {
                                sMonth = 1 + (dayOfYear - 1) / 31
                                sDay = 1 + (dayOfYear - 1) % 31
                            } else {
                                dayOfYear -= 186
                                sMonth = 7 + (dayOfYear - 1) / 30
                                sDay = 1 + (dayOfYear - 1) % 30
                            }
                        }
                        val monthName = months.getOrNull(sMonth - 1) ?: ""
                        "$weekdayStr $sDay $monthName $sYear"
                    }
                    "QAMARI" -> {
                        val qMonths = listOf("محرم", "صفر", "ربیع‌الأول", "ربیع‌الثانی", "جمادی‌الأول", "جمادی‌الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه")
                        val hDate = java.time.chrono.Chronology.of("Hijrah-umalqura").date(ld)
                        val hYear = hDate.get(java.time.temporal.ChronoField.YEAR)
                        val hMonth = hDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                        val hDay = hDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                        val qMonthName = qMonths.getOrNull(hMonth - 1) ?: ""
                        "$weekdayStr $hDay $qMonthName $hYear"
                    }
                    else -> {
                        val enMonths = listOf("ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
                        val mMonthName = enMonths.getOrNull(ld.monthValue - 1) ?: ""
                        "$weekdayStr ${ld.dayOfMonth} $mMonthName ${ld.year}"
                    }
                }
            } else {
                val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa"))
                sdf.format(Date(timestamp))
            }
        } catch (e: Exception) {
            formatDate(timestamp)
        }
    }

    // Single & Batch Therapy Stage Toggles
    fun toggleTreatmentStage(treatment: Treatment, stageName: String) {
        viewModelScope.launch {
            val completedList = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (completedList.contains(stageName)) {
                completedList.remove(stageName)
            } else {
                completedList.add(stageName)
            }
            val newCompletedStr = completedList.joinToString(",")
            val allStagesList = parseStagesList(treatment.stages)
            val newStatus = when {
                allStagesList.isNotEmpty() && completedList.size >= allStagesList.size -> "COMPLETED"
                completedList.isNotEmpty() -> "IN_PROGRESS"
                else -> treatment.status
            }
            repository.insertTreatment(treatment.copy(completedStages = newCompletedStr, status = newStatus))
        }
    }

    // ایگنور کردن یک مرحله/جلسه به دلیل عدم نیاز، بدون علامت‌گذاری آن به‌عنوان انجام‌شده
    fun ignoreTreatmentStage(treatment: Treatment, stageName: String) {
        viewModelScope.launch {
            val ignoredList = treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (!ignoredList.contains(stageName)) {
                ignoredList.add(stageName)
            }
            repository.insertTreatment(treatment.copy(ignoredStages = ignoredList.joinToString(",")))
        }
    }

    // بازگرداندن یک مرحله ایگنورشده به لیست مراحل باقی‌مانده
    fun unignoreTreatmentStage(treatment: Treatment, stageName: String) {
        viewModelScope.launch {
            val ignoredList = treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            ignoredList.remove(stageName)
            repository.insertTreatment(treatment.copy(ignoredStages = ignoredList.joinToString(",")))
        }
    }

    // تکرار آخرین مرحلهٔ انجام‌شده (جلسه قبلی) به‌عنوان مرحله جاری، برای اجرای دوباره آن در این جلسه
    fun repeatLastCompletedStage(treatment: Treatment) {
        viewModelScope.launch {
            val completedList = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (completedList.isEmpty()) return@launch
            val lastCompleted = completedList.removeAt(completedList.lastIndex)
            val newCompletedStr = completedList.joinToString(",")
            val allStagesList = parseStagesList(treatment.stages)
            val newStatus = when {
                allStagesList.isNotEmpty() && completedList.size >= allStagesList.size -> "COMPLETED"
                completedList.isNotEmpty() -> "IN_PROGRESS"
                else -> "IN_PROGRESS"
            }
            repository.insertTreatment(treatment.copy(completedStages = newCompletedStr, status = newStatus))
        }
    }

    fun toggleAllTreatmentStages(treatment: Treatment, checkAll: Boolean) {
        viewModelScope.launch {
            val allStagesList = parseStagesList(treatment.stages)
            val newCompletedStr = if (checkAll) allStagesList.joinToString(",") else ""
            val newStatus = if (checkAll && allStagesList.isNotEmpty()) "COMPLETED" else if (checkAll) "COMPLETED" else "PLANNED"
            repository.insertTreatment(treatment.copy(completedStages = newCompletedStr, status = newStatus))
        }
    }

    fun updateTreatment(treatment: Treatment) {
        viewModelScope.launch {
            repository.insertTreatment(treatment)
        }
    }

    // Bilingual Translation Dictionary
    fun label(key: String): String {
        if (key == "currency") {
            return currencyUnit.value
        }

        val faMap = mapOf(

            "app_title" to "دنتال‌پرو | ERP جامع مدیریت مطب",
            "dashboard" to "داشبورد مدیریت",
            "patients" to "پرونده بیماران",
            "appointments" to "نوبت‌دهی و تقویم",
            "vendors" to "فروشندگان و فاکتورها",
            "labs" to "لابراتوار دندان",
            "maintenance" to "تعمیر و نگهداری",
            "expenses" to "هزینه‌های جاری",
            "personal" to "حسابداری شخصی",
            "more" to "امکانات ERP",
            
            // Dashboard
            "summary" to "خلاصه وضعیت مالی و مطب",
            "total_patients" to "کل پرونده‌ها",
            "today_appointments" to "نوبت‌های امروز",
            "clinic_income" to "کل دریافتی مطب",
            "personal_balance" to "موجودی کل بانک‌ها",
            "uncleared_cheques" to "چک‌های وصول‌نشده",
            "quick_add" to "ثبت سریع اطلاعات",
            "quick_patient" to "بیمار جدید",
            "quick_appointment" to "نوبت جدید",
            "quick_expense" to "ثبت هزینه",
            "currency" to "تومان",
            
            // Patients Page
            "search_patient" to "جستجوی پرونده بیمار...",
            "add_new_patient" to "افزودن بیمار جدید",
            "name" to "نام و نام خانوادگی",
            "phone" to "شماره تماس",
            "notes" to "توضیحات و یادداشت",
            "no_patient_selected" to "لطفاً برای مشاهده جزئیات پرونده، یک بیمار را انتخاب کنید",
            "patient_file" to "پرونده تخصصی و درمان‌های بیمار",
            "financial_balance" to "وضعیت مالی بیمار",
            "balance_creditor" to "بستانکار",
            "balance_debtor" to "بدهکار",
            "balance_settled" to "تسویه کامل",
            "total_treatments" to "کل خدمات ارائه شده",
            "total_payments" to "کل مبالغ پرداختی",
            "treatments_tab" to "طرح درمان و خدمات (تراپی)",
            "payments_tab" to "پرداختی‌ها و چک‌ها",
            "add_treatment" to "ثبت طرح درمان جدید",
            "add_payment" to "ثبت دریافتی مالی جدید",
            "treatment_title" to "نوع خدمت (مثلا ایمپلنت، پر کردن)",
            "cost" to "هزینه کل خدمت",
            "amount" to "مبلغ تراکنش",
            "payment_type" to "روش پرداخت",
            "cash" to "نقدی",
            "card" to "پوز / کارتخوان",
            "cheque" to "چک صیادی",
            "cheque_number" to "شماره چک",
            "cheque_due_date" to "تاریخ سررسید چک",
            "save" to "ذخیره اطلاعات",
            "cancel" to "انصراف",
            "delete" to "حذف",
            
            // Appointments Page
            "patient_name" to "نام بیمار",
            "treatment_type" to "نوع خدمت درخواستی",
            "date_time" to "تاریخ و ساعت نوبت",
            "no_appointments" to "نوبت فعالی در تقویم یافت نشد",
            
            // Vendors Page
            "vendor_name" to "نماینده فروشگاه",
            "company_name" to "نام شرکت / تجهیزات",
            "product_type" to "دسته اقلام خریداری شده",
            "total_purchases" to "کل مبلغ خرید",
            "paid_amount" to "مبلغ پرداخت شده",
            "vendor_debt" to "مانده بدهی ما",
            "add_vendor" to "ثبت فاکتور خرید جدید",
            
            // Labs Page
            "lab_name" to "لابراتوار",
            "tooth_num" to "شماره دندان",
            "prosthesis_type" to "پروتز درخواستی (روکش/لمینت)",
            "due_date" to "زمان تحویل کار",
            "status" to "وضعیت کار آزمایشگاهی",
            "status_ordered" to "سفارش داده شده",
            "status_received" to "تحویل گرفته شده",
            "status_fitted" to "نصب نهایی بیمار",
            "add_lab_order" to "ثبت سفارش پروتز",
            
            // Maintenance Page
            "equipment_name" to "نام دستگاه / ابزار",
            "maintenance_desc" to "گزارش تعمیر و کالیبراسیون",
            "next_service" to "سررسید سرویس بعدی",
            "add_maintenance" to "ثبت فاکتور تعمیر و نگهداری",
            
            // Expenses Page
            "expense_category" to "دسته هزینه جاری",
            "rent" to "اجاره مطب",
            "utilities" to "قبوض خدماتی",
            "salaries" to "حقوق پرسنل مطب",
            "materials" to "مواد و ملزومات دندانپزشکی",
            "other" to "سایر هزینه‌های مطب",
            "add_expense" to "ثبت هزینه جاری مطب",
            
            // Personal Finance
            "personal_desc" to "شرح تراکنش شخصی",
            "transaction_type" to "نوع تراکنش شخصی",
            "personal_income" to "درآمد متفرقه (خارج مطب)",
            "dentist_draw" to "برداشت شخصی پزشک از صندوق مطب",
            "personal_expense" to "هزینه‌های شخصی دکتر",
            "add_personal_tx" to "ثبت تراکنش حساب شخصی"
        )

        val enMap = mapOf(
            "app_title" to "DentalPro | Comprehensive Clinic ERP",
            "dashboard" to "Dashboard",
            "patients" to "Patient Files",
            "appointments" to "Appointments & Calendar",
            "vendors" to "Vendors & Bills",
            "labs" to "Dental Labs",
            "maintenance" to "Maintenance & Repair",
            "expenses" to "Clinic Expenses",
            "personal" to "Personal Accounting",
            "more" to "ERP Features",
            
            // Dashboard
            "summary" to "Clinic Financial Summary",
            "total_patients" to "Total Files",
            "today_appointments" to "Today's Appointments",
            "clinic_income" to "Total Clinic Revenue",
            "personal_balance" to "Total Bank Balance",
            "uncleared_cheques" to "Uncleared Cheques",
            "quick_add" to "Quick Entry Panel",
            "quick_patient" to "New Patient",
            "quick_appointment" to "New Appointment",
            "quick_expense" to "Record Expense",
            "currency" to "Toman",
            
            // Patients Page
            "search_patient" to "Search patient files...",
            "add_new_patient" to "Add New Patient",
            "name" to "Full Name",
            "phone" to "Phone Number",
            "notes" to "Medical History / Note",
            "no_patient_selected" to "Please select a patient to view files",
            "patient_file" to "Clinical File & Account History",
            "financial_balance" to "Patient Financial Status",
            "balance_creditor" to "Creditor",
            "balance_debtor" to "Debtor",
            "balance_settled" to "Settled",
            "total_treatments" to "Total Procedures",
            "total_payments" to "Total Paid",
            "treatments_tab" to "Treatment Plan & Dental Chart",
            "payments_tab" to "Payments & Cheques",
            "add_treatment" to "Record Treatment Service",
            "add_payment" to "Record Patient Payment",
            "treatment_title" to "Procedure Type (e.g. Implant, Veneers)",
            "cost" to "Procedure Cost",
            "amount" to "Amount",
            "payment_type" to "Payment Method",
            "cash" to "Cash",
            "card" to "POS Card Reader",
            "cheque" to "Bank Cheque",
            "cheque_number" to "Cheque Number",
            "cheque_due_date" to "Cheque Due Date",
            "save" to "Save Details",
            "cancel" to "Cancel",
            "delete" to "Delete",
            
            // Appointments Page
            "patient_name" to "Patient Name",
            "treatment_type" to "Service Requested",
            "date_time" to "Scheduled Date & Time",
            "no_appointments" to "No appointments found",
            
            // Vendors Page
            "vendor_name" to "Rep Name",
            "company_name" to "Company Name",
            "product_type" to "Product Category",
            "total_purchases" to "Total Order Value",
            "paid_amount" to "Paid Amount",
            "vendor_debt" to "Our Outstanding Balance",
            "add_vendor" to "Add New Invoice",
            
            // Labs Page
            "lab_name" to "Dental Laboratory",
            "tooth_num" to "Tooth #",
            "prosthesis_type" to "Prosthesis Crown / Veneers",
            "due_date" to "Delivery Date",
            "status" to "Work Status",
            "status_ordered" to "Ordered",
            "status_received" to "Received",
            "status_fitted" to "Fitted to Patient",
            "add_lab_order" to "Add Lab Order",
            
            // Maintenance Page
            "equipment_name" to "Equipment Name",
            "maintenance_desc" to "Service & Calibration Report",
            "next_service" to "Next Service Date",
            "add_maintenance" to "Add Maintenance Bill",
            
            // Expenses Page
            "expense_category" to "Category",
            "rent" to "Rent Expense",
            "utilities" to "Utility Bills",
            "salaries" to "Staff Salaries",
            "materials" to "Dental Materials",
            "other" to "Other Clinic Expense",
            "add_expense" to "Record Clinic Expense",
            
            // Personal Finance
            "personal_desc" to "Transaction Description",
            "transaction_type" to "Transaction Type",
            "personal_income" to "Private Income",
            "dentist_draw" to "Doctor Drawing",
            "personal_expense" to "Doctor Personal Expense",
            "add_personal_tx" to "Add Personal Transaction"
        )

        return if (isPersian.value) {
            faMap[key] ?: key
        } else {
            enMap[key] ?: key
        }
    }

    fun addSampleHypotheticalPatient() {
        viewModelScope.launch {
            seedSampleData()
        }
    }

    // Seed realistic sample data with a rich hypothetical patient
    private suspend fun seedSampleData() {
        val patientId = repository.insertPatient(
            Patient(
                name = "علی رضایی (پرونده نمونه جامع)",
                phone = "09123456789",
                medicalHistory = "بدون سابقه بیماری حساسیت‌زا - پرونده تست طرح درمان دندانپزشکی",
                note = "شامل درمان‌های عصب‌کشی تک کانال، دوکانال (دندان ۱۴)، سه کانال فک پایین (دندان ۴۶) و ترمیم‌های ۳‌بعدی قدامی و خلفی"
            )
        )

        if (patientId > 0) {
            // 1. Single Canal Endo (Tooth 11 - Upper Incisor)
            repository.insertTreatment(
                Treatment(
                    patientId = patientId,
                    title = "عصب‌کشی (درمان ریشه) - تک کانال دندان ۱۱",
                    cost = 3500000L,
                    discount = 0L,
                    selectedTeeth = "11",
                    notes = "تعداد کانال: 1\nطول کارکرد: 21mm\nسایز MAF: 35 (استاندارد)\nروش پرکردن: Lateral Condensation\nکانال C1: 21mm [MAF: 35]",
                    status = "در حال درمان",
                    stages = "جلسه ۱: اکسس و تخلیه عصب|جلسه ۲: شستشو و پرکردن ریشه",
                    completedStages = "جلسه ۱: اکسس و تخلیه عصب"
                )
            )

            // 2. Double Canal Endo (Tooth 14 - Upper Premolar)
            repository.insertTreatment(
                Treatment(
                    patientId = patientId,
                    title = "عصب‌کشی (درمان ریشه) - ۲ کانال دندان ۱۴",
                    cost = 4500000L,
                    discount = 0L,
                    selectedTeeth = "14",
                    notes = "تعداد کانال: 2\nطول کارکرد: 20.5mm\nسایز MAF: 30 (استاندارد)\nروش پرکردن: Single Cone\nکانال Buccal: 20.5mm [MAF: 30]\nکانال Palatal: 20mm [MAF: 30]",
                    status = "در حال درمان",
                    stages = "جلسه ۱: آماده‌سازی کانال بوکال و پالاتال|جلسه ۲: پرکردن گوتا",
                    completedStages = "جلسه ۱: آماده‌سازی کانال بوکال و پالاتال"
                )
            )

            // 3. Triple Canal Endo (Tooth 46 - Lower Molar)
            repository.insertTreatment(
                Treatment(
                    patientId = patientId,
                    title = "عصب‌کشی (درمان ریشه) - ۳ کانال دندان ۴۶ فک پایین",
                    cost = 5800000L,
                    discount = 300000L,
                    selectedTeeth = "46",
                    notes = "تعداد کانال: 3\nطول کارکرد: 21.5mm\nسایز MAF: F2 (روتاری)\nروش پرکردن: Warm Vertical\nکانال MB: 21.5mm [MAF: F2]\nکانال ML: 21mm [MAF: F2]\nکانال Distal: 22mm [MAF: F3]",
                    status = "تکمیل شده",
                    stages = "جلسه ۱: آماده‌سازی کانال‌ها|جلسه ۲: شکل‌دهی روتاری|جلسه ۳: انسداد گوتا",
                    completedStages = "جلسه ۱: آماده‌سازی کانال‌ها|جلسه ۲: شکل‌دهی روتاری|جلسه ۳: انسداد گوتا"
                )
            )

            // 4. Anterior Restoration (Tooth 21 - Incisal, Mesial, Cervical/Gingival)
            repository.insertTreatment(
                Treatment(
                    patientId = patientId,
                    title = "ترمیم کامپوزیت قدامی زیبایی - دندان ۲۱",
                    cost = 2800000L,
                    discount = 0L,
                    selectedTeeth = "21",
                    notes = "سطوح ترمیم: I-M-V\nکلاس پوسیدگی: Class IV (Black's)\nنوع ماده: کامپوزیت اولترادنت A2",
                    status = "در حال درمان",
                    stages = "جلسه ۱: تراش پوسیدگی، باندینگ و فرم‌دهی اینسیزال مزیال و طوق جینجیوال",
                    completedStages = ""
                )
            )

            // 5. Posterior Restoration (Tooth 36 - Occlusal, Mesial, Distal, Buccal, Gingival)
            repository.insertTreatment(
                Treatment(
                    patientId = patientId,
                    title = "ترمیم ۳‌بعدی خلفی - دندان ۳۶ (MODBV)",
                    cost = 3200000L,
                    discount = 200000L,
                    selectedTeeth = "36",
                    notes = "سطوح ترمیم: O-M-D-B-V\nکلاس پوسیدگی: Class II (Black's)\nنوع ماده: آمالگام کپسولی / کامپوزیت پکریبل",
                    status = "تکمیل شده",
                    stages = "جلسه ۱: تراش و بازسازی کامل تاج دندان",
                    completedStages = "جلسه ۱: تراش و بازسازی کامل تاج دندان"
                )
            )
        }
    }

    private suspend fun seedDefaultTemplatesAndSettings() {
        // Seeding standard Treatment templates with full stage details
        repository.insertTemplate(TreatmentTemplate(title = "ترمیم کامپوزیت (Composite Filling)", defaultCost = 2000000L, defaultNotes = "بازسازی زیبایی و عملکرد تاج دندان", stages = "جلسه ۱: بی‌حسی، تراش پوسیدگی، اچ و باندینگ، لایه‌گذاری انکریمنتال و پالیش", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "ترمیم آمالگام (Amalgam Filling)", defaultCost = 1800000L, defaultNotes = "ترمیم مقاوم خلفی با آلیاژ فلزی", stages = "جلسه ۱: بی‌حسی، برداشت پوسیدگی، آماده‌سازی حفره، کندانس و کاروینگ آمالگام و تنظیم بایت", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "ونیر کامپوزیت (Composite Veneer)", defaultCost = 3000000L, defaultNotes = "اصلاح فرم، رنگ و فواصل دندان‌های جلو", stages = "جلسه ۱: آنالیز و انتخاب رنگ، آماده‌سازی مینا، لایه‌گذاری آناتومیک و پالیش - جلسه ۲: بررسی بایت و پالیش نهایی", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "لمینت سرامیکی (Porcelain Laminate Veneer)", defaultCost = 6000000L, defaultNotes = "پوسته سرامیکی ماندگار جهت اصلاح طرح لبخند", stages = "جلسه ۱: آنالیز، تراش مینا و قالب‌گیری - جلسه ۲: تست فریم سرامیکی - جلسه ۳: ایزولاسیون با رابردام و سمنتاسیون رزینی", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "اینله / آنله (Inlay / Onlay)", defaultCost = 4500000L, defaultNotes = "ترمیم غیرمستقیم برای دندان‌های خلفی با تخریب وسیع", stages = "جلسه ۱: پاک‌سازی پوسیدگی، دیواره‌سازی، تراش و قالب‌گیری - جلسه ۲: تست انطباق و سمنتاسیون رزینی", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "بلیچینگ مطبی (In-Office Bleaching)", defaultCost = 4000000L, defaultNotes = "سفید کردن تخصصی دندان‌ها", stages = "جلسه ۱: جرم‌گیری سبک، ایزولاسیون لثه، بکارگیری ژل پراکسید طی ۳-۴ پارت و تحویل ژل ضدحساسیت", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "عصب‌کشی و درمان ریشه (Root Canal Treatment - RCT)", defaultCost = 4500000L, defaultNotes = "تخليه، شکل‌دهی و پرکردن کانال‌های ریشه", stages = "جلسه ۱: بی‌حسی، رابردم، حفره دسترسی، تعیین طول و شکل‌دهی روتاری - جلسه ۲: شست‌وشوی نهایی و انسداد کانال با گوتاپرکا و سیلر", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "درمان مجدد ریشه (Re-RCT)", defaultCost = 6000000L, defaultNotes = "تخليه مواد پرکننده قدیمی و پاک‌سازی مجدد کانال‌ها", stages = "جلسه ۱: خارج کردن گوتاپرکا، پاک‌سازی و شست‌وشو و پانسمان کلسیم هیدروکساید - جلسه ۲: شست‌وشو و پرکردن مجدد کانال‌ها", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "کاشت ایمپلنت تخصصی (Dental Implant)", defaultCost = 12000000L, defaultNotes = "جایگزینی ریشه دندان با پایه تیتانیومی", stages = "جلسه ۱: جراحی کاشت فیکسچر و بخیه - جلسه ۲: کشیدن بخیه - جلسه ۳: نصب هیلینگ کپ - جلسه ۴: قالب‌گیری - جلسه ۵: تست اباتمنت - جلسه ۶: تحویل روکش ایمپلنت", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "پیوند استخوان و سینوس لیفت (Bone Graft & Sinus Lift)", defaultCost = 7000000L, defaultNotes = "بازسازی استخوان فک یا بالا بردن غشای سینوس", stages = "جلسه ۱: جراحی فلپ، بکارگیری پودر استخوان و ممبران و بخیه - جلسه ۲: کشیدن بخیه و کنترل التیام", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "جراحی افزایش طول تاج (Crown Lengthening)", defaultCost = 3000000L, defaultNotes = "اصلاح عرض بیولوژیک و بافت استخوان دور دندان", stages = "جلسه ۱: جراحی فلپ، اصلاح استخوان (استئوتومی) و بخیه - جلسه ۲: کشیدن بخیه و ارزیابی لثه", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "کشیدن دندان / جراحی دندان عقل (Tooth Extraction / Wisdom Surgery)", defaultCost = 1200000L, defaultNotes = "خارج کردن دندان غیرقابل نگهداری یا نهفته", stages = "جلسه ۱: بی‌حسی، جراحی/لوکسیشن، خروج دندان و بخیه - جلسه ۲: کشیدن بخیه و کنترل ترمیم", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "روکش تک واحدی (PFM / Zirconia Crown)", defaultCost = 4000000L, defaultNotes = "پوشش کامل تاج دندان جهت بازگرداندن استحکام", stages = "جلسه ۱: تراش دندان، نخ پس‌زنی، قالب‌گیری و روکش موقت - جلسه ۲: تست فریم - جلسه ۳: تست بیسکویتی - جلسه ۴: چسباندن نهایی و تنظیم بایت", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "بریج دندانی (Dental Bridge)", defaultCost = 10000000L, defaultNotes = "جایگزینی دندان خالی با تکیه بر دندان‌های مجاور", stages = "جلسه ۱: تراش دندان‌های پایه و قالب‌گیری - جلسه ۲: تست فریم متصل - جلسه ۳: تست بیسکویتی - جلسه ۴: چسباندن دائم", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "دندان مصنوعی و پروتز متحرک (Denture)", defaultCost = 8000000L, defaultNotes = "پروتز کامل یا پارسیال برای بی‌دندانی", stages = "جلسه ۱: قالب‌گیری اولیه - جلسه ۲: قالب‌گیری نهایی - جلسه ۳: ثبت بایت - جلسه ۴: امتحان واکس - جلسه ۵: تحویل پروتز - جلسه ۶: تنظیم زخم‌ها", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "ارتودنسی ثابت (Fixed Orthodontics)", defaultCost = 25000000L, defaultNotes = "منظم کردن دندان‌ها با براکت‌های فلزی یا سرامیکی", stages = "جلسه ۱: ثبت مدارک تشخیصی - جلسه ۲: نصب براکت‌ها و سیم اولیه - جلسات ماهانه: تنظیمات سیم و کش - جلسه پایانی: ده‌باندینگ و تحویل ریتینر", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "ارتودنسی شفاف / الاینر (Clear Aligners)", defaultCost = 30000000L, defaultNotes = "ردیف کردن دندان‌ها با پلاک‌های شفاف نامرئی", stages = "جلسه ۱: اسکن ۳ بعدی و طراحی دیجیتال - جلسه ۲: نصب اتچمنت‌ها و تحویل سری اول - مراجعات دوره‌ای: تحویل پلاک‌های بعدی", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "جرم‌گیری و بروساژ (Scaling & Polishing)", defaultCost = 1200000L, defaultNotes = "پاک‌سازی جرم‌های فوق و زیر لثه‌ای", stages = "جلسه ۱: جرم‌گیری اولتراسونیک، کورتاژ دستی، بروساژ و فلورایدتراپی", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "جرم‌گیری عمیق و تسطیح سطح ریشه (Scaling & Root Planing - SRP)", defaultCost = 2500000L, defaultNotes = "درمان بیماری‌های لثه و تسطیح سطح ریشه", stages = "جلسه ۱: بی‌حسی و SRP دو کوادرانت اول - جلسه ۲: بی‌حسی و SRP دو کوادرانت مقابل - جلسه ۳: ارزیابی مجدد عمق پاکت‌ها", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "عصب‌کشی دندان شیری / پالپوتومی (Pediatric Endodontics)", defaultCost = 2500000L, defaultNotes = "حفظ دندان شیری عفونی‌شده تا زمان رویش دندان دائمی", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، پالپوتومی/پالپکتومی، قرار دادن ماده دارویی و ترمیم تاج", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "روکش استیل دندان شیری (SS Crown)", defaultCost = 2000000L, defaultNotes = "بازسازی دندان شیری خلفی دارای تخریب وسیع", stages = "جلسه ۱: بی‌حسی، آماده‌سازی دندان، انتخاب سایز روکش، تطابق حاشیه و سمنتاسیون با گلاس‌آینومر", isStarred = true))
        repository.insertTemplate(TreatmentTemplate(title = "فیشور سیلانت (Fissure Sealant)", defaultCost = 1000000L, defaultNotes = "پوشش شیارهای عمیق دندان‌های آسیاب جهت پیشگیری", stages = "جلسه ۱: تمیز کردن شیار، اچ اسیدی، شست‌وشو، اعمال رزین سیلانت و کیورینگ نوری", isStarred = true))

        // Seeding Standard Personal Sub-Accounts
        listOf(
            "اجاره مسکن و شارژ",
            "خریدهای روزانه و سوپرمارکت",
            "هزینه‌های درمانی و دارو",
            "قبوض آب، برق، گاز و تلفن",
            "تفریح، سفر و رستوران",
            "پس‌انداز و سرمایه‌گذاری",
            "خودرو و حمل و نقل",
            "آموزش و شهریه فرزندان",
            "اقساط وام و تسهیلات"
        ).forEach { subName ->
            repository.insertSubAccount(PersonalSubAccount(name = subName, category = "EXPENSE"))
        }

        // Seeding Ready Standard Personal Transactions
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        repository.insertTransaction(
            PersonalTransaction(
                type = "EXPENSE",
                amount = 8500000L,
                description = "پرداخت شارژ و هزینه‌های مشاعات مسکن",
                paymentMethod = "CARD_TRANSFER",
                subAccountName = "اجاره مسکن و شارژ",
                date = now - 2 * oneDayMs
            )
        )
        repository.insertTransaction(
            PersonalTransaction(
                type = "EXPENSE",
                amount = 3200000L,
                description = "خرید اقلام سوپرمارکت و مواد غذایی خانواده",
                paymentMethod = "POS",
                subAccountName = "خریدهای روزانه و سوپرمارکت",
                date = now - oneDayMs
            )
        )
        repository.insertTransaction(
            PersonalTransaction(
                type = "DRAW",
                amount = 25000000L,
                description = "برداشت ماهانه از حساب مطب جهت هزینه‌های شخصی",
                paymentMethod = "CARD_TRANSFER",
                subAccountName = "برداشت شخصی",
                date = now - 5 * oneDayMs
            )
        )

        // Seeding Default Lab Services Catalog
        val sampleLab = "لابراتوار تخصصی دنتال آرت"
        listOf(
            LabService(labName = sampleLab, serviceName = "روکش زيركونيا (تمام سرامیک)", price = 2500000L),
            LabService(labName = sampleLab, serviceName = "روکش PFM (فلز سرامیک)", price = 1800000L),
            LabService(labName = sampleLab, serviceName = "دست دندان فلكسي (پارسیل)", price = 6500000L),
            LabService(labName = sampleLab, serviceName = "اینله / آنله سرامیکی", price = 2200000L),
            LabService(labName = sampleLab, serviceName = "پست و کور ریختگی", price = 1200000L)
        ).forEach { repository.insertLabService(it) }

        // Seeding Default Settings
        repository.insertSettings(
            ClinicSettings(
                id = 1,
                clinicName = "مطب دندانپزشکی",
                doctorName = "دکتر",
                childrenNames = "",
                staffNames = "منشی مطب"
            )
        )
    }
}

// Data class to communicate SMS parsed details to Compose UI dialogs
data class SmsParseResult(
    val amount: Long,
    val detectedAccount: BankAccount?,
    val originalText: String,
    val isDeposit: Boolean
)
