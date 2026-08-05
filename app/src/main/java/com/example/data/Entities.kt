package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val medicalHistory: String = "", // e.g., "Allergies, Diabetes, High Blood Pressure"
    val plannedTreatmentIds: String = "",
    val note: String = "",
    val photoUri: String? = null, // Path to patient profile photo
    val documentUris: String = "", // Comma-separated paths of clinical files/radiographs
    val createdAt: Long = System.currentTimeMillis(),
    val gender: String = "مرد", // "مرد" or "زن"
    val medicalAlerts: String = "", // Comma-separated alerts e.g. "دیابت, فشارخون, حساسیت به پنسلین"
    val selfDeclarationJson: String = "", // Interactive Dental Self-Declaration form answers
    val age: Int = 0, // Age of patient
    val birthDate: String = "", // Birth date string e.g. "1375/04/10" or "1375"
    val preferredMessenger: String = "واتساپ" // "واتساپ", "تلگرام", "روبیکا", "بله", "پیامک"
)

@Entity(
    tableName = "treatments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["date"])
    ]
)
data class Treatment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val title: String, // e.g., "Filling", "Root Canal", "Implant"
    val cost: Long,
    val discount: Long = 0L, // Discount amount
    val selectedTeeth: String = "", // Comma-separated list of selected tooth numbers from the 32-tooth chart
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val status: String = "PLANNED",
    val stages: String = "",
    val completedStages: String = "",
    val ignoredStages: String = "" // مراحلی که به‌دلیل عدم نیاز ایگنور شده‌اند (comma-separated)
)

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["date"]),
        Index(value = ["chequeDueDate"])
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val amount: Long,
    val type: String, // CASH, CHEQUE, CARD
    val bankAccountId: Long? = null, // Linked bank card/account
    val chequeNumber: String? = null,
    val chequeDueDate: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val isCleared: Boolean = true,
    val sayadiId: String? = null,
    val chequeBank: String? = null,
    val chequeDrawer: String? = null,
    val chequePhotoBase64: String? = null,
    val chequeStatus: String = "PENDING", // PENDING, CLEARED, BOUNCED, TRANSFERRED
    val transferredTo: String? = null,
    val transferDate: Long? = null
)

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientName: String,
    val date: Long, // timestamp
    val treatmentType: String,
    val plannedTreatmentIds: String = "",
    val note: String = "",
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(tableName = "vendors")
data class Vendor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String,
    val phone: String,
    val itemType: String, // e.g., "Instruments", "Consumables"
    val totalOrders: Long = 0L,
    val totalPaid: Long = 0L,
    val invoiceNumber: String? = null,
    val invoiceDetails: String? = null,
    val invoicePhotoBase64: String? = null
)

@Entity(tableName = "lab_orders")
data class LabOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labName: String,
    val patientName: String,
    val toothNumber: String,
    val prosthesisType: String, // Crown, Bridge, Denture
    val cost: Long,
    val orderDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val status: String // ORDERED, RECEIVED, FITTED
)

@Entity(tableName = "equipment_maintenance")
data class EquipmentMaintenance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentName: String,
    val description: String,
    val cost: Long,
    val date: Long = System.currentTimeMillis(),
    val nextServiceDate: Long? = null,
    val technicianName: String? = null,
    val invoiceNumber: String? = null
)

@Entity(tableName = "clinic_expenses")
data class ClinicExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Rent, Consumables, Utilities, Salaries
    val amount: Long,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val invoicePhotoBase64: String? = null,
    val paymentMethod: String = "CARD_TRANSFER",
    val subAccountName: String = "",
    val chequeNumber: String? = null,
    val labOrderId: Long? = null // در صورت ثبت خودکار از سفارش لابراتوار، برای نمایش فاکتور رسمی همان سفارش از این بخش هزینه‌ها
)

@Entity(tableName = "personal_transactions")
data class PersonalTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME (non-clinic), DRAW (from clinic), EXPENSE (personal expense)
    val subAccountName: String = "سایر", // Sub-account name (Customizable)
    val bankAccountId: Long? = null, // Linked bank card/account
    val amount: Long,
    val description: String = "",
    val paymentMethod: String = "CARD_TRANSFER", // CARD_TRANSFER, CASH, CHEQUE_TRANSFER, POS
    val chequeNumber: String? = null,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "lab_services")
data class LabService(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labName: String,
    val serviceName: String,
    val price: Long,
    val notes: String = "",
    val frameDeliveryDays: Int = 3,
    val finalDeliveryDays: Int = 7
)

// --- NEW ENTITIES AS REQUESTED ---

@Entity(
    tableName = "installments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["dueDate"])
    ]
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val treatmentId: Long? = null,
    val title: String = "قسط درمان",
    val amount: Long,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val note: String = ""
)

@Entity(tableName = "treatment_templates")
data class TreatmentTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val defaultCost: Long,
    val defaultNotes: String = "",
    val stages: String = "",
    val isStarred: Boolean = true
)

@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val cardHolder: String = "",
    val cardNumber: String = "", // e.g. "6037-xxxx-xxxx-xxxx"
    val accountNumber: String = "",
    val balance: Long = 0L,
    val smsKeywords: String = "" // Comma-separated list like "واریز,بانک ملی"
)

@Entity(tableName = "personal_sub_accounts")
data class PersonalSubAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "EXPENSE", // INCOME, EXPENSE, DRAW
    val isQuickSelect: Boolean = false
)

@Entity(tableName = "fixed_incomes")
data class FixedIncome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int = 1, // 1 to 31
    val bankAccountId: Long? = null,
    val bankAccountName: String = "",
    val subAccountName: String = "درآمد ثابت",
    val lastProcessedMonth: String = "", // e.g. "1405/05"
    val isActive: Boolean = true
)

@Entity(tableName = "children_expenses")
data class ChildrenExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childName: String,
    val title: String,
    val amount: Long,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "clinic_settings")
data class ClinicSettings(
    @PrimaryKey val id: Long = 1,
    val clinicName: String = "مطب دندانپزشکی مدرن",
    val doctorName: String = "دکتر",
    val childrenNames: String = "",
    val staffNames: String = "منشی",
    val showSummaryCard: Boolean = true,
    val showQuickActions: Boolean = true,
    val showCharts: Boolean = true,
    val smsSampleTemplate: String = "واریز مبلغ {amount} ریال به کارت {card}",
    val themeName: String = "AMBER_CLASSIC",
    val isDarkMode: Boolean = true,
    // آخرین سایز فونت انتخاب‌شده در صفحات شرح بالینی (دستورالعمل بالینی / ممنوعیت‌های درمانی) — پایدار بین اجراها
    val clinicalFontSizePt: Int = 11,
    // سایز فونت کلی برنامه — لغزنده ۸پله‌ای در تنظیمات (۰=خیلی‌کوچک ... ۷=خیلی‌بزرگ، پیش‌فرض وسط=۳)
    val globalFontScaleIndex: Int = 9
)
