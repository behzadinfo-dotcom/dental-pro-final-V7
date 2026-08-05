package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.reports.*
import com.example.ui.DentalViewModel
import com.example.ui.theme.LocalNeonPalette
import java.io.File

private data class ReportEntry(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val generate: (Context2) -> File
)

/** Bundles everything a report generator might need, gathered once per screen composition. */
private class Context2(
    val androidContext: android.content.Context,
    val clinicName: String,
    val doctorName: String,
    val accent: Int,
    val currency: String,
    val dateFmt: (Long) -> String,
    val amountFmt: (Long) -> String,
    val vm: DentalViewModel
)

@Composable
fun ReportsCenterScreen(viewModel: DentalViewModel, isPersian: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val neonPalette = LocalNeonPalette.current
    val accent = neonPalette.primary.toArgb()
    val currency = viewModel.label("currency")

    val patients by viewModel.patients.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val labOrders by viewModel.labOrders.collectAsState()
    val maintenanceList by viewModel.maintenanceList.collectAsState()
    val clinicExpenses by viewModel.clinicExpenses.collectAsState()
    val personalTransactions by viewModel.personalTransactions.collectAsState()
    val childrenExpenses by viewModel.childrenExpenses.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val appointments by viewModel.appointments.collectAsState()

    fun patientNameOf(id: Long) = patients.firstOrNull { it.id == id }?.name ?: "—"
    fun amountFmt(v: Long) = formatAmount(v)
    fun dateFmt(v: Long) = viewModel.formatDate(v)

    var generatedFile by remember { mutableStateOf<File?>(null) }
    var generatedTitle by remember { mutableStateOf("") }

    val ctx2 = Context2(context, settings.clinicName, settings.doctorName, accent, currency, ::dateFmt, ::amountFmt, viewModel)

    val reports = remember(patients, allTreatments, allPayments, vendors, labOrders, maintenanceList, clinicExpenses, personalTransactions, childrenExpenses, bankAccounts, appointments) {
        listOf(
            ReportEntry("income", if (isPersian) "گزارش درآمد دوره‌ای" else "Period Income Report", if (isPersian) "همه پرداخت‌های ثبت‌شده" else "All recorded payments", Icons.Default.TrendingUp) { c ->
                ReportGenerators.periodIncomeReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", allPayments, c.dateFmt, c.amountFmt, ::patientNameOf)
            },
            ReportEntry("debtors", if (isPersian) "گزارش مطالبات معوق" else "Outstanding Debtors", if (isPersian) "بیماران بدهکار به ترتیب مبلغ" else "Debtor patients by amount", Icons.Default.MoneyOff) { c ->
                val debtors = patients.map { p ->
                    val cost = allTreatments.filter { it.patientId == p.id }.sumOf { it.cost - it.discount }
                    val paid = allPayments.filter { it.patientId == p.id }.sumOf { it.amount }
                    Triple(p.name, p.phone, cost - paid)
                }.filter { it.third > 0 }
                ReportGenerators.outstandingDebtorsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, debtors, c.amountFmt)
            },
            ReportEntry("cheques", if (isPersian) "گزارش چک‌ها" else "Cheques Report", if (isPersian) "در جریان، پاس‌شده و برگشتی" else "Pending, cleared & bounced", Icons.Default.ConfirmationNumber) { c ->
                ReportGenerators.chequesReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, allPayments.filter { it.type == "CHEQUE" }, c.dateFmt, c.amountFmt, ::patientNameOf)
            },
            ReportEntry("profit_loss", if (isPersian) "گزارش سود و زیان" else "Profit & Loss", if (isPersian) "درآمد در برابر هزینه‌های مطب" else "Clinic income vs expenses", Icons.Default.Assessment) { c ->
                val income = allPayments.sumOf { it.amount }
                ReportGenerators.profitLossReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, clinicExpenses, maintenanceList.sumOf { it.cost }, c.amountFmt)
            },
            ReportEntry("tax", if (isPersian) "خلاصه مالیاتی سالانه" else "Annual Tax Summary", if (isPersian) "جهت ارائه به حسابدار" else "For your accountant", Icons.Default.Gavel) { c ->
                val byMonth = allPayments.groupBy { c.dateFmt(it.date).take(7) }.mapValues { it.value.sumOf { p -> p.amount } }
                val totalExpense = clinicExpenses.sumOf { it.amount } + maintenanceList.sumOf { it.cost }
                ReportGenerators.taxSummaryReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "جاری" else "Current", byMonth.toList(), totalExpense, c.amountFmt)
            },
            ReportEntry("bank", if (isPersian) "گزارش حساب‌های بانکی" else "Bank Accounts", if (isPersian) "موجودی هر حساب" else "Balance per account", Icons.Default.AccountBalance) { c ->
                ReportGenerators.bankAccountsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, bankAccounts, c.amountFmt)
            },
            ReportEntry("draw", if (isPersian) "گزارش برداشت شخصی" else "Personal Draw Report", if (isPersian) "برداشت دکتر از درآمد مطب" else "Owner draws vs clinic income", Icons.Default.Savings) { c ->
                val income = allPayments.sumOf { it.amount }
                ReportGenerators.personalDrawReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, personalTransactions.filter { it.type == "DRAW" }, c.dateFmt, c.amountFmt)
            },
            ReportEntry("children", if (isPersian) "هزینه‌های فرزندان" else "Children Expenses", if (isPersian) "به تفکیک هر فرزند" else "Broken down per child", Icons.Default.ChildCare) { c ->
                ReportGenerators.childrenExpenseReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", childrenExpenses, c.dateFmt, c.amountFmt)
            },
            ReportEntry("performance", if (isPersian) "عملکرد درمانی" else "Treatment Performance", if (isPersian) "پرتکرارترین خدمات" else "Most frequent services", Icons.Default.BarChart) { c ->
                ReportGenerators.treatmentPerformanceReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", allTreatments, c.amountFmt)
            },
            ReportEntry("lab", if (isPersian) "سفارش‌های آزمایشگاهی" else "Lab Orders Report", if (isPersian) "وضعیت و سررسید سفارش‌ها" else "Status & due dates", Icons.Default.Science) { c ->
                ReportGenerators.labOrdersReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, labOrders, c.dateFmt, c.amountFmt)
            },
            ReportEntry("maintenance", if (isPersian) "تعمیر و نگهداری تجهیزات" else "Equipment Maintenance", if (isPersian) "تاریخچه هزینه هر دستگاه" else "Cost history per device", Icons.Default.Handyman) { c ->
                ReportGenerators.maintenanceReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, maintenanceList, c.dateFmt, c.amountFmt)
            },
            ReportEntry("vendors", if (isPersian) "گزارش تامین‌کنندگان" else "Vendors Report", if (isPersian) "بدهی به هر فروشنده" else "Outstanding balance per vendor", Icons.Default.LocalShipping) { c ->
                ReportGenerators.vendorsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, vendors, c.amountFmt)
            },
            ReportEntry("dashboard", if (isPersian) "گزارش جامع ماهانه مطب" else "Monthly Dashboard Report", if (isPersian) "خلاصه یک‌صفحه‌ای کل مطب" else "One-page clinic summary", Icons.Default.Dashboard) { c ->
                val income = allPayments.sumOf { it.amount }
                val expense = clinicExpenses.sumOf { it.amount } + maintenanceList.sumOf { it.cost }
                val debt = patients.sumOf { p ->
                    val cost = allTreatments.filter { it.patientId == p.id }.sumOf { it.cost - it.discount }
                    val paid = allPayments.filter { it.patientId == p.id }.sumOf { it.amount }
                    maxOf(0L, cost - paid)
                }
                ReportGenerators.monthlyDashboardReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, expense, debt, appointments.size, patients.size, c.amountFmt)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = if (isPersian) "گزارش‌ها و اسناد" else "Reports & Documents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPersian) "تولید، چاپ و اشتراک‌گذاری اسناد PDF" else "Generate, print & share PDF documents",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(reports) { report ->
                ReportRow(
                    report = report,
                    isPersian = isPersian,
                    onGenerate = {
                        val file = report.generate(ctx2)
                        generatedFile = file
                        generatedTitle = report.title
                    }
                )
            }
        }
    }

    val fileToActOn = generatedFile
    if (fileToActOn != null) {
        AlertDialog(
            onDismissRequest = { generatedFile = null },
            title = { Text(if (isPersian) "سند آماده شد" else "Document Ready") },
            text = {
                Text(
                    if (isPersian) "«$generatedTitle» ساخته شد. می‌خواهید چاپ کنید یا اشتراک بگذارید؟"
                    else "\"$generatedTitle\" is ready. Print it or share it?"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        printPdfFile(context, fileToActOn, generatedTitle)
                        generatedFile = null
                    }) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "چاپ" else "Print")
                    }
                    TextButton(onClick = {
                        sharePdfToWhatsApp(context, fileToActOn)
                        generatedFile = null
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "واتساپ" else "WhatsApp")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { generatedFile = null }) {
                    Text(if (isPersian) "بستن" else "Close")
                }
            }
        )
    }
}

@Composable
fun PatientSendActionsRow(
    patient: Patient,
    treatments: List<Treatment>,
    payments: List<Payment>,
    viewModel: DentalViewModel,
    isPersian: Boolean
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val neonPalette = LocalNeonPalette.current
    val accent = neonPalette.primary.toArgb()
    val bg = neonPalette.background.toArgb()
    val currency = viewModel.label("currency")

    var pendingAction by remember { mutableStateOf<String?>(null) } // "statement" or "plan"

    fun hasUsablePhone() = patient.phone.isNotBlank() && patient.phone != "ثبت نشده"

    fun sendStatementViaMessenger(messenger: String) {
        val totalCost = treatments.sumOf { it.cost - it.discount }
        val totalPaid = payments.sumOf { it.amount }
        val balance = totalCost - totalPaid
        
        if (messenger == "واتساپ") {
            val items = treatments.sortedByDescending { it.date }.take(12).map {
                SummaryLineItem(it.title, viewModel.formatDate(it.date), "${formatAmount(it.cost - it.discount)} $currency")
            }
            val footer = listOf(
                (if (isPersian) "جمع هزینه درمان‌ها" else "Total treatment cost") to "${formatAmount(totalCost)} $currency",
                (if (isPersian) "جمع پرداختی‌ها" else "Total paid") to "${formatAmount(totalPaid)} $currency",
                (if (balance > 0) (if (isPersian) "مانده بدهی" else "Balance due") else (if (isPersian) "مانده بستانکاری" else "Credit balance")) to "${formatAmount(kotlin.math.abs(balance))} $currency"
            )
            val file = buildSummaryImage(
                context, if (isPersian) "صورت‌حساب بیمار" else "Patient Statement",
                settings.clinicName, settings.doctorName, patient.name, items, footer, accent, bg, "statement"
            )
            shareImageToWhatsApp(context, file)
        } else {
            val text = buildStatementSmsText(
                settings.clinicName, patient.name, currency,
                formatAmount(totalCost), formatAmount(totalPaid), formatAmount(kotlin.math.abs(balance)), balance > 0
            )
            com.example.ui.screens.openMessengerChat(context, patient.phone, text, messenger)
        }
    }

    fun sendTreatmentPlanViaMessenger(messenger: String) {
        val planned = treatments.filter { it.status == "PLANNED" }
        val totalPrice = planned.sumOf { it.cost - it.discount }
        if (messenger == "واتساپ") {
            val items = planned.map {
                SummaryLineItem(it.title, if (it.selectedTeeth.isNotBlank()) (if (isPersian) "دندان: ${it.selectedTeeth}" else "Tooth: ${it.selectedTeeth}") else "", "${formatAmount(it.cost - it.discount)} $currency")
            }
            val footer = listOf((if (isPersian) "هزینه کل تخمینی" else "Total estimated cost") to "${formatAmount(totalPrice)} $currency")
            val file = buildSummaryImage(
                context, if (isPersian) "طرح درمان پیشنهادی" else "Proposed Treatment Plan",
                settings.clinicName, settings.doctorName, patient.name, items, footer, accent, bg, "treatment_plan"
            )
            shareImageToWhatsApp(context, file)
        } else {
            val text = buildTreatmentPlanSmsText(settings.clinicName, patient.name, currency, planned.map { it.title }, formatAmount(totalPrice))
            com.example.ui.screens.openMessengerChat(context, patient.phone, text, messenger)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { pendingAction = "statement" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isPersian) "ارسال صورت‌حساب" else "Send Statement", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = { pendingAction = "plan" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isPersian) "ارسال طرح درمان" else "Send Treatment Plan", fontSize = 12.sp)
        }
    }

    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (isPersian) "انتخاب کانال ارسال" else "Choose messenger channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isPersian) "پیام‌رسان پیش‌فرض بیمار: ${patient.preferredMessenger}"
                        else "Patient's preferred messenger: ${patient.preferredMessenger}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        if (isPersian) "کانال مورد نظر خود را جهت ارسال متنی یا تصویری انتخاب کنید:"
                        else "Select channel to send:",
                        fontSize = 11.sp
                    )

                    val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                    messengers.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { m ->
                                val isPref = m == patient.preferredMessenger
                                Button(
                                    onClick = {
                                        if (action == "statement") sendStatementViaMessenger(m) else sendTreatmentPlanViaMessenger(m)
                                        pendingAction = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (isPref) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPref) "⭐ $m (پیش‌فرض)" else m,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReportRow(report: ReportEntry, isPersian: Boolean, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onGenerate() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(report.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(report.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(report.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}
