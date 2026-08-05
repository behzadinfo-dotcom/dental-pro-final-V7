@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import kotlin.OptIn
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.concurrent.Executors

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.*
import com.example.reports.*
import com.example.ui.DentalViewModel
import com.example.ui.GeminiClient
import com.example.ui.ParsedClinicalResponse
import com.example.ui.ParsedClinicalProposal
import kotlinx.coroutines.launch
import com.example.ui.theme.*
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import java.io.File
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard

fun getPatientFormattedName(name: String, gender: String, isPersian: Boolean = true): String {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return ""
    
    val isFemale = gender == "زن" || gender == "خانم" || gender.lowercase() == "female" || cleanName.startsWith("خانم") || cleanName.startsWith("Mrs") || cleanName.startsWith("Ms")
    
    if (cleanName.startsWith("آقای") || cleanName.startsWith("خانم") || cleanName.startsWith("Mr.") || cleanName.startsWith("Mrs.") || cleanName.startsWith("Ms.")) {
        return cleanName
    }
    
    val prefix = if (isPersian) {
        if (isFemale) "خانم " else "آقای "
    } else {
        if (isFemale) "Mrs. " else "Mr. "
    }
    return "$prefix$cleanName"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDentalScreen(viewModel: DentalViewModel) {
    val isPersian by viewModel.isPersian.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val currentViewingImage by viewModel.currentViewingImage.collectAsState()
    val topBarContext = LocalContext.current

    if (currentViewingImage != null) {
        FullScreenImageViewer(imageStr = currentViewingImage!!) {
            viewModel.currentViewingImage.value = null
        }
    }

    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr

    val mainTabs = remember { listOf("dashboard", "patients", "appointments", "more") }
    val pagerState = rememberPagerState(initialPage = 0) { mainTabs.size }
    // اسکرول/سوایپ بین ۴ زیرمنوی اصلی فقط وقتی مجاز است که واقعاً در سطح صفحات اصلی هستیم،
    // نه داخل یک نمای جزئی (مثل جزئیات بیمار / پنجره تراپی) که همچنان currentTab را تغییر نمی‌دهد.
    val selectedPatientIdForPager by viewModel.selectedPatientId.collectAsState()
    val isOnMainTopLevelPage = currentTab in mainTabs && selectedPatientIdForPager == null

    LaunchedEffect(currentTab) {
        val index = mainTabs.indexOf(currentTab)
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val targetTab = mainTabs[pagerState.currentPage]
        if (currentTab in mainTabs && currentTab != targetTab) {
            viewModel.currentTab.value = targetTab
        }
    }

    val neonPalette = LocalNeonPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .neonGlow(neonPalette.glow, RoundedCornerShape(8.dp), elevation = 10.dp)
                                    .border(1.dp, neonPalette.glow.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = viewModel.label("app_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // تک‌کلیک → صفحه تنظیمات شخصی‌سازی مطب، بدون هیچ گزینه اختصاصی/لمس‌طولانی
                        // لمس ۲ثانیه‌ای → همان صفحه، این‌بار همراه با گزینه‌های بیشتر تنظیمات
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .threeSecondLongPress(
                                    onLongClick = {
                                        viewModel.advancedSettingsRevealed.value = true
                                        android.widget.Toast.makeText(
                                            topBarContext,
                                            "گزینه‌های بیشتر تنظیمات در دسترس",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        viewModel.currentTab.value = "settings"
                                    },
                                    onClick = {
                                        viewModel.advancedSettingsRevealed.value = false
                                        viewModel.currentTab.value = "settings"
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = neonPalette.glow.copy(alpha = 0.45f),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 2.dp.toPx()
                            )
                        },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val dashboardLabel = viewModel.label("dashboard")
                    val patientsLabel = viewModel.label("patients")
                    val appointmentsLabel = viewModel.label("appointments")
                    val moreLabel = if (isPersian) "بخش‌های دیگر" else "More Modules"

                    NavigationBarItem(
                        selected = currentTab == "dashboard",
                        onClick = { viewModel.currentTab.value = "dashboard" },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = dashboardLabel) },
                        label = { Text(dashboardLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "patients",
                        onClick = { viewModel.currentTab.value = "patients" },
                        icon = { Icon(Icons.Default.ContactPage, contentDescription = patientsLabel) },
                        label = { Text(patientsLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "appointments",
                        onClick = { viewModel.currentTab.value = "appointments" },
                        icon = { Icon(Icons.Default.CalendarToday, contentDescription = appointmentsLabel) },
                        label = { Text(appointmentsLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = currentTab in listOf("vendors", "labs", "maintenance", "expenses", "personal", "more", "templates", "bank", "settings", "financial", "cheques"),
                        onClick = { viewModel.currentTab.value = "more" },
                        icon = { Icon(Icons.Default.Menu, contentDescription = moreLabel) },
                        label = { Text(moreLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (currentTab in mainTabs) {
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        when (currentTab) {
                            "dashboard" -> DashboardScreen(viewModel)
                            "patients" -> PatientsScreen(viewModel)
                            "appointments" -> AppointmentsScreen(viewModel)
                            "more" -> MoreModulesGridScreen(viewModel)
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                        },
                        label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            "vendors" -> VendorsScreen(viewModel)
                            "labs" -> LabsScreen(viewModel)
                            "maintenance" -> MaintenanceScreen(viewModel)
                            "expenses" -> ExpensesScreen(viewModel)
                            "personal" -> PersonalScreen(viewModel)
                            "templates" -> TemplatesManagerScreen(viewModel, isPersian) { viewModel.currentTab.value = "more" }
                            "bank" -> BankAccountsManagerScreen(viewModel, isPersian) { viewModel.currentTab.value = "more" }
                            "cheques" -> ChequesManagerScreen(viewModel, isPersian) { viewModel.currentTab.value = "more" }
                            "reports" -> ReportsCenterScreen(viewModel, isPersian) { viewModel.currentTab.value = "more" }
                            "settings" -> ClinicSettingsScreen(viewModel, isPersian) { viewModel.currentTab.value = "more" }
                            else -> DashboardScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
    
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: DentalViewModel) {
    val patients by viewModel.patients.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()
    val appointments by viewModel.appointments.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val personalTx by viewModel.personalTransactions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val expenses by viewModel.clinicExpenses.collectAsState()

    val isPersian by viewModel.isPersian.collectAsState()
    val currency = viewModel.label("currency")

    // Calculations
    val clinicIncome = allPayments.sumOf { it.amount }
    val personalDraw = personalTx.filter { it.type == "DRAW" }.sumOf { it.amount }
    val personalOtherIncome = personalTx.filter { it.type == "INCOME" }.sumOf { it.amount }
    val personalExpenses = personalTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    
    // Personal balance: Dentist personal income/draws minus personal expenses
    val personalBalance = personalDraw + personalOtherIncome - personalExpenses

    val unclearedCheques = allPayments.filter { it.type == "CHEQUE" && !it.isCleared }.sumOf { it.amount }

    // Advanced Calculations for Visual Gradient Charts
    val totalCost = allTreatments.sumOf { it.cost - it.discount }
    val totalPaid = allPayments.sumOf { it.amount }
    val outstandingDebt = if (totalCost > totalPaid) totalCost - totalPaid else 0L
    val totalExpenses = expenses.sumOf { it.amount }

    // Quick Patient Add Form State
    var showQuickPatientDialog by remember { mutableStateOf(false) }

    // Quick Expense Form State
    var showQuickExpenseDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Image Illustration Card (with custom clinic/doctor branding + neon glow)
        item {
            val neonPalette = LocalNeonPalette.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .neonGlow(neonPalette.glow, RoundedCornerShape(16.dp), elevation = 22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(neonPalette.heroBrush())
                    .border(1.dp, neonPalette.glow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                // Soft radial glow accent behind the logo
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(160.dp)
                        .background(neonPalette.glowRadialBrush())
                )
                Image(
                    painter = painterResource(id = R.drawable.img_app_icon),
                    contentDescription = "Dental Banner",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(100.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (settings.clinicName.isNotBlank()) settings.clinicName else (if (isPersian) "کلینیک دندانپزشکی دیجیتال" else "Digital Dental Clinic"),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (settings.doctorName.isNotBlank()) "${if (isPersian) "مدیریت درمانگر: دکتر " else "Dentist: Dr. "}${settings.doctorName}" else (if (isPersian) "سیستم یکپارچه حسابداری، نوبت‌دهی و درمان" else "Integrated billing, appointments & clinical records"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Summary Stats Grid (Toggleable via Settings)
        if (settings.showSummaryCard) {
            item {
                Text(
                    text = viewModel.label("summary"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = viewModel.label("total_patients"),
                            value = "${patients.size}",
                            icon = Icons.Default.People,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                        StatCard(
                            title = viewModel.label("today_appointments"),
                            value = "${appointments.size}",
                            icon = Icons.Default.Event,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = viewModel.label("clinic_income"),
                            value = "${formatAmount(clinicIncome)} $currency",
                            icon = Icons.Default.AttachMoney,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surface,
                            valueColor = Color(0xFF388E3C)
                        )
                        StatCard(
                            title = viewModel.label("personal_balance"),
                            value = "${formatAmount(personalBalance)} $currency",
                            icon = Icons.Default.AccountBalanceWallet,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surface,
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (unclearedCheques > 0) {
                        StatCard(
                            title = viewModel.label("uncleared_cheques"),
                            value = "${formatAmount(unclearedCheques)} $currency",
                            icon = Icons.Default.Payments,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            valueColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Premium Financial Canvas Chart (Toggleable via Settings)
        if (settings.showCharts) {
            item {
                ClinicFinancialGradientChart(
                    revenue = clinicIncome,
                    outstanding = outstandingDebt,
                    expenses = totalExpenses,
                    isPersian = isPersian
                )
            }
        }

        // Quick Actions Row (Toggleable via Settings)
        if (settings.showQuickActions) {
            item {
                Text(
                    text = viewModel.label("quick_add"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        label = viewModel.label("quick_patient"),
                        icon = Icons.Default.PersonAdd,
                        onClick = { showQuickPatientDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        label = viewModel.label("quick_appointment"),
                        icon = Icons.Default.AddAlarm,
                        onClick = { viewModel.currentTab.value = "appointments" },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        label = viewModel.label("quick_expense"),
                        icon = Icons.Default.MoneyOff,
                        onClick = { showQuickExpenseDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Interactive Weekday Calendar on Dashboard!
        item {
            InteractiveAppointmentsCalendar(viewModel = viewModel, isPersian = isPersian)
        }

        // Quick Paste banking SMS detector on Dashboard!
    }

    // Quick Add Dialogs
    if (showQuickPatientDialog) {
        AddPatientDialog(
            viewModel = viewModel,
            onDismiss = { showQuickPatientDialog = false }
        )
    }

    if (showQuickExpenseDialog) {
        AddExpenseDialog(
            viewModel = viewModel,
            onDismiss = { showQuickExpenseDialog = false }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// 2. PATIENTS FILE MANAGER (DENTAL RECORDS)
// ==========================================
@Composable
fun PatientsScreen(viewModel: DentalViewModel) {
    val patients by viewModel.patients.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()
    val selectedPatientId by viewModel.selectedPatientId.collectAsState()
    val selectedPatient by viewModel.selectedPatient.collectAsState()
    val treatments by viewModel.selectedPatientTreatments.collectAsState()
    val payments by viewModel.selectedPatientPayments.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }
    val isPersian by viewModel.isPersian.collectAsState()

    val filteredPatients = patients.filter {
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.phone.contains(searchQuery)
    }

    if (selectedPatientId != null && selectedPatient != null) {
        // Patient Detailed Medical & Billing View (Interactive split layout)
        PatientDetailsView(
            patient = selectedPatient!!,
            treatments = treatments,
            payments = payments,
            viewModel = viewModel,
            onBack = { viewModel.selectedPatientId.value = null }
        )
    } else {
        // List of Patients Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = viewModel.label("patients"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(viewModel.label("add_new_patient"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(viewModel.label("search_patient")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (filteredPatients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPersian) "هیچ پرونده‌ای یافت نشد" else "No patient records found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPatients) { patient ->
                        PatientListItem(
                            patient = patient,
                            isPersian = isPersian,
                            onClick = {
                                viewModel.selectedPatientId.value = patient.id
                            },
                            onDelete = {
                                patientToDelete = patient
                            }
                        )
                    }
                }
            }
        }
    }

    if (patientToDelete != null) {
        val pName = getPatientFormattedName(patientToDelete!!.name, patientToDelete!!.gender, isPersian)
        AlertDialog(
            onDismissRequest = { patientToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف پرونده بیمار" else "Confirm Patient Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف کامل پرونده «$pName» و تمامی سوابق درمانی، نوبت‌ها و پرداخت‌های مرتبط اطمینان دارید؟ این عملیات غیرقابل بازگشت است."
                    else "Are you sure you want to completely delete patient file '$pName' and all associated treatments, appointments, and payments? This action cannot be undone.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePatient(patientToDelete!!)
                        patientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف کامل پرونده" else "Delete Patient File", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { patientToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddPatientDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
    }
}

@Composable
fun PatientListItem(
    patient: Patient,
    isPersian: Boolean = true,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isFemale = patient.gender == "زن" || patient.gender == "خانم" || patient.gender.lowercase() == "female"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (!patient.photoUri.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer else if (isFemale) Color(0xFFFCE4EC) else Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!patient.photoUri.isNullOrBlank()) {
                        SmartImage(
                            model = patient.photoUri!!,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isFemale) Icons.Default.Woman else Icons.Default.Man,
                            contentDescription = null,
                            tint = if (isFemale) Color(0xFFC2185B) else Color(0xFF1565C0)
                        )
                    }
                }
                Column {
                    Text(text = getPatientFormattedName(patient.name, patient.gender, isPersian), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = patient.phone, fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
            }
        }
    }
}

@Composable
fun TreatmentStageChecklistCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsState()
    val allStagesList = remember(treatment.stages) {
        parseStagesList(treatment.stages)
    }
    val completedStagesList = remember(treatment.completedStages) {
        treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val allChecked = allStagesList.isNotEmpty() && completedStagesList.containsAll(allStagesList)

    if (allStagesList.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "مراحل تراپی و درمان:" else "Therapy Stages:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!readOnly) {
                        // Group/Batch Check Action
                        TextButton(
                            onClick = { viewModel.toggleAllTreatmentStages(treatment, !allChecked) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                if (allChecked) Icons.Default.SelectAll else Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (allChecked) (if (isPersian) "خارج کردن همه" else "Uncheck All") else (if (isPersian) "تیک دسته‌ای همه" else "Batch Check All"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${completedStagesList.size}/${allStagesList.size} ${if (isPersian) "مرحله انجام شده" else "done"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Stage Progress bar
                val progress = if (allStagesList.isEmpty()) 0f else completedStagesList.size.toFloat() / allStagesList.size.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )

                // Single stage items
                allStagesList.forEach { stage ->
                    val isChecked = completedStagesList.contains(stage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!readOnly) Modifier.clickable { viewModel.toggleTreatmentStage(treatment, stage) }
                                else Modifier
                            )
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!readOnly) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleTreatmentStage(treatment, stage) },
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isChecked) Color(0xFF2E7D32) else Color.Gray,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stage,
                            fontSize = 11.sp,
                            fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium,
                            color = if (isChecked) Color.Gray else MaterialTheme.colorScheme.onSurface,
                            style = if (isChecked) MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PatientDetailsView(
    patient: Patient,
    treatments: List<Treatment>,
    payments: List<Payment>,
    viewModel: DentalViewModel,
    onBack: () -> Unit
) {
    val currency = viewModel.label("currency")
    val totalCost = treatments.sumOf { it.cost - it.discount }
    val totalPaid = payments.sumOf { it.amount }
    val netBalance = totalCost - totalPaid

    var currentSubTab by remember { mutableStateOf("treatments") } // treatments, payments, installments, documents
    var showTreatmentForm by remember { mutableStateOf(false) }
    var showPaymentForm by remember { mutableStateOf(false) }
    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showEditPatientDialog by remember { mutableStateOf(false) }
    var treatmentToDelete by remember { mutableStateOf<Treatment?>(null) }
    var treatmentToEdit by remember { mutableStateOf<Treatment?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    val allInstallments by viewModel.allInstallments.collectAsState()
    val patientInstallments = remember(allInstallments, patient.id) { allInstallments.filter { it.patientId == patient.id } }

    val isPersian = viewModel.isPersian.collectAsState().value
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val b64 = saveUriToLocalFile(context, it)
            if (b64 != null) {
                viewModel.updatePatient(patient.copy(photoUri = b64))
            }
        }
    }
    val launchAvatarCamera = rememberSafeCameraLauncher(context) { path ->
        viewModel.updatePatient(patient.copy(photoUri = path))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Back Header with Interactive Avatar & Patient Edit Card
        var showAvatarPicker by remember { mutableStateOf(false) }
        val isFemale = patient.gender == "زن" || patient.gender == "خانم" || patient.gender.lowercase() == "female"
        val avatarBg = if (!patient.photoUri.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer
                       else if (isFemale) Color(0xFFFCE4EC) else Color(0xFFE3F2FD)
        val avatarBorder = if (!patient.photoUri.isNullOrBlank()) MaterialTheme.colorScheme.primary
                         else if (isFemale) Color(0xFFE91E63) else Color(0xFF1976D2)
        val avatarTint = if (isFemale) Color(0xFFC2185B) else Color(0xFF1565C0)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                // Interactive Patient Photo Avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(avatarBg)
                        .border(2.dp, avatarBorder, CircleShape)
                        .clickable { showAvatarPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (!patient.photoUri.isNullOrBlank()) {
                        SmartImage(
                            model = patient.photoUri!!,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isFemale) Icons.Default.Woman else Icons.Default.Man,
                            contentDescription = null,
                            tint = avatarTint,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Main Patient Header Column taking full weight(1f)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = getPatientFormattedName(patient.name, patient.gender, isPersian),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (patient.age > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${patient.age} ${if (isPersian) "ساله" else "yrs"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (isPersian) "تلفن: " else "Phone: "}${patient.phone.ifBlank { "ثبت نشده" }}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (patient.birthDate.isNotBlank()) {
                            Text(
                                text = "${if (isPersian) "تولد: " else "Born: "}${patient.birthDate}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // ردیف مستقل آیکون‌های عملیاتی — زیر هدر برگ بیمار (تماس/پیامک/ادیت/رفرش)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (patient.phone.isNotBlank() && patient.phone != "ثبت نشده") {
                    IconButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = if (isPersian) "تماس" else "Call",
                            tint = ClearColor
                        )
                    }
                    IconButton(onClick = {
                        sendSmsPrefilled(context, patient.phone, "سلام ${patient.name} عزیز، از مطب تماس می‌گیریم.")
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = if (isPersian) "پیامک" else "SMS",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Refresh — force-reload this patient's data from the database
                IconButton(onClick = {
                    viewModel.refreshSelectedPatient()
                    android.widget.Toast.makeText(
                        context,
                        if (isPersian) "به‌روزرسانی شد" else "Refreshed",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isPersian) "به‌روزرسانی" else "Refresh",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Edit Patient Pencil Button
                IconButton(onClick = { showEditPatientDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Patient",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }

        if (showEditPatientDialog) {
            EditPatientDialog(
                patient = patient,
                viewModel = viewModel,
                onDismiss = { showEditPatientDialog = false }
            )
        }

            if (showAvatarPicker) {
                AlertDialog(
                    onDismissRequest = { showAvatarPicker = false },
                    title = { Text(if (isPersian) "بارگذاری تصویر بیمار" else "Upload Patient Photo", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (isPersian) "منبع عکس بیمار را انتخاب کنید:" else "Select patient photo source:")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { launchAvatarCamera(); showAvatarPicker = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPersian) "دوربین" else "Camera", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { avatarGalleryLauncher.launch("image/*"); showAvatarPicker = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAvatarPicker = false }) {
                            Text(if (isPersian) "انصراف" else "Cancel")
                        }
                    }
                )
            }

        // Medical Warnings Badges, Risk Analysis & Self-Declaration Viewer Card
        var showSelfDecDetailsDialog by remember { mutableStateOf(false) }
        var showRiskGuidelinesDialog by remember { mutableStateOf(false) }
        var selectedProtocolTitleForPatientDetail by remember { mutableStateOf<String?>(null) }

        val hasMedicalAlerts = patient.medicalAlerts.isNotBlank() && patient.medicalAlerts != "بدون مورد"
        val hasRiskySelfDeclaration = remember(patient.selfDeclarationJson) {
            try {
                if (patient.selfDeclarationJson.isNotBlank()) {
                    val obj = org.json.JSONObject(patient.selfDeclarationJson)
                    var hasRisk = false
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (obj.getString(key) == "بله") hasRisk = true
                    }
                    hasRisk
                } else false
            } catch(e: Exception) { false }
        }
        val hasSelfDeclaration = patient.selfDeclarationJson.isNotBlank()
        
        val isRedRisk = hasMedicalAlerts || hasRiskySelfDeclaration

        // Only show Prominent Clinical Risks Card if the patient actually has medical alerts or a self-declaration on file
        if (hasMedicalAlerts || hasSelfDeclaration) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRedRisk) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.5.dp, if (isRedRisk) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRedRisk) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (isPersian) "🔴 موارد خطر، احتیاط‌ها و ممنوعیت‌های درمانی" else "🔴 Risk Alerts, Precautions & Contraindications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { showRiskGuidelinesDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(if (isPersian) "دستورالعمل بالینی" else "Clinical Rules", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (patient.medicalAlerts.isNotBlank() && patient.medicalAlerts != "بدون مورد") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            patient.medicalAlerts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { alert ->
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = alert,
                                        color = MaterialTheme.colorScheme.onError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (patient.selfDeclarationJson.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (isPersian) "فرم خوداظهاری سلامت بیمار تکمیل شده" else "Health Declaration On File",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = { showSelfDecDetailsDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (isPersian) "مشاهده پاسخ‌ها" else "View Answers", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Detailed Clinical Risk & Contraindications Modal Dialog
        if (showRiskGuidelinesDialog) {
            val alerts = patient.medicalAlerts
            // سایز فونت این دیالوگ به‌صورت خودکار از همان مقدار ذخیره‌شده در صفحات
            // شرح بالینی (دستورالعمل بالینی) گرفته می‌شود — نسبت‌ها حفظ می‌شوند
            val protoBase = settings.clinicalFontSizePt
            val protocolTitleFontSize = (protoBase + 4).sp
            val protocolSectionFontSize = (protoBase + 1).sp
            val protocolBodyFontSize = protoBase.sp
            val protocolSmallFontSize = (protoBase - 1).coerceAtLeast(7).sp
            // این دیالوگ هم مانند صفحه دستورالعمل بالینی از لغزنده سراسری فونت مستثنی است
            val riskDialogDensity = androidx.compose.ui.unit.Density(density = LocalDensity.current.density, fontScale = 1f)

            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides riskDialogDensity) {
            AlertDialog(
                onDismissRequest = { showRiskGuidelinesDialog = false },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = if (isPersian) "دستورالعمل بالینی و ممنوعیت‌های درمانی" else "Clinical Guidelines & Contraindications",
                                fontWeight = FontWeight.Bold,
                                fontSize = protocolTitleFontSize,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = if (isPersian) "بر اساس آخرین گایدلاین‌های معتبر دانشگاهی (ADA / AHA)" else "Based on ADA / AHA Evidence-Based Clinical Protocols",
                            fontSize = protocolSmallFontSize,
                            color = Color.Gray
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Section 1: Bleeding / Anticoagulants / Bleeding History
                        if (alerts.contains("انعقاد خون") || alerts.contains("خونریزی شدید")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🩸 اختلالات انعقادی / مصرف رقیق‌کننده خون (وارفارین/آسپرین):" else "🩸 Bleeding / Anticoagulant Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.error)
                                    Text(if (isPersian) "• احتیاط: سنجش آزمایشگاهی INR قبل جراحی یا کشیدن دندان (محدوده مجاز INR ≤ ۲.۵ تا ۳). استفاده از پانسمان بندآورنده خونریزی (ژلفوم، گاز ترانگزامیک اسید، بخیه متابولیک محکم)." else "• Precaution: Check INR (target ≤ 2.5-3). Use hemostatic agents, Tranexamic acid & tight sutures.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: قطع خودسرانه وارفارین بدون دستور پزشک معالج ممنوع است. تجویز داروهای NSAID (ایبوپروفن، نپروکسن، مفنامیک اسید) به علت افزایش خطر خونریزی معده اکیداً ممنوع بوده و فقط استامینوفن مجاز است." else "❌ Contraindication: Do NOT stop Warfarin independently. NSAIDs (Ibuprofen) are strictly contra-indicated; prescribe Acetaminophen.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 2: Penicillin / Anesthetic Allergies
                        if (alerts.contains("حساسیت دارویی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "⚠️ حساسیت دارویی / پنی‌سیلین یا بی حسی:" else "⚠️ Drug & Anesthetic Allergy Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.error)
                                    Text(if (isPersian) "• احتیاط (به‌روزرسانی‌شده طبق بیانیه علمی ۲۰۲۱ انجمن قلب آمریکا AHA): کلیندامایسین دیگر به‌دلیل خطر عفونت کلستریدیوم دیفیسیل توصیه نمی‌شود. در حساسیت به پنی‌سیلین، از سفالکسین (در صورت نبود واکنش شدید نوع آنافیلاکسی) یا آزیترومایسین/کلاریترومایسین یا داکسی‌سایکلین استفاده شود. در صورت حساسیت به بی‌حسی‌های آمیدی یا مواد نگهدارنده سولفیت، از مپیواکائین ۳٪ بدون اپی‌نفرین استفاده شود." else "• Precaution (updated per AHA 2021 Scientific Statement): Clindamycin is no longer recommended due to C. difficile risk. For penicillin allergy use Cephalexin (if no history of anaphylaxis) or Azithromycin/Clarithromycin or Doxycycline. Use Mepivacaine 3% without Epinephrine for sulfite/amide allergies.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: تجویز تمام مشتقات پنی‌سیلین (آموکسی‌سیلین، آمی‌سیلین، کوآموکسی‌کلاو) و سفالوسپورین‌ها در بیماران با آنفیلاکسی سابقه اکیداً ممنوع است." else "❌ Contraindication: All Penicillins (Amoxicillin, Augmentin) and Cephalosporins are strictly forbidden.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3: Cardiovascular / Hypertension / Diabetes / Epilepsy (تطبیق دقیق - فقط فشار خون بالا، نه پایین)
                        if (alerts.contains("بیماری قلبی") || alerts.contains("فشار خون بالا") || alerts.contains("دیابت") || alerts.contains("صرع")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫀 بیماران قلبی-عروقی، فشارخون بالا، دیابت و صرع:" else "🫀 Cardiovascular, Hypertension & Diabetes Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: کنترل فشار خون قبل کار (فشار بالای ۱۶۰/۱۰۰ نیاز به استراحت و ارجاع). محدودیت اپی‌نفرین به حداکثر ۲ کارپول ۱/۱۰۰۰۰۰. تنظیم وقت صبحگاهی برای بیماران دیابتی پس از مصرف صبحانه و انسولین (پرهیز از افت قندخون حین درمان). در دریچه مصنوعی قلب یا سابقه اندوکاردیت، پروفیلاکسی آنتی‌بیوتیکی طبق AHA 2021 فقط برای پرخطرترین گروه لازم است." else "• Precaution: Check blood pressure (refer if >160/100). Limit Epinephrine to max 2 cartridges 1:100k. Schedule diabetic patients in morning after meal & insulin (avoid hypoglycemia mid-treatment). For prosthetic valves/endocarditis history, AHA 2021 prophylaxis applies only to highest-risk group.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: استفاده از نخ پس‌کشنده لثه (Retraction cord) آغشته به اپی‌نفرین غلیظ اکیداً ممنوع است." else "❌ Contraindication: Retraction cords impregnated with concentrated Epinephrine are forbidden.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3b: Hypotension / Vasovagal Syncope during Injection (NEW)
                        if (alerts.contains("فشار خون پایین")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "💧 فشار خون پایین / سنکوپ وازوواگال حین تزریق:" else "💧 Hypotension / Vasovagal Syncope Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: بیمار را در وضعیت نیمه‌خوابیده (Semi-supine) و نه کاملاً عمودی قرار دهید؛ پیش از تزریق حتماً آسپیره کنید و بی‌حسی را آهسته تزریق نمایید؛ از ناشتا بودن طولانی بیمار پیش از نوبت پرهیز شود. علائم هشدار: رنگ‌پریدگی، تعریق سرد، سرگیجه، تهوع." else "• Precaution: Position patient semi-supine (not fully upright) before injection; always aspirate before injecting and inject slowly; ensure patient hasn't fasted for long before the appointment. Warning signs: pallor, cold sweat, dizziness, nausea.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "🚑 در صورت بروز غش/سنکوپ حین کار: فوراً درمان را متوقف کنید، بیمار را در وضعیت خوابیده با پاهای بالاتر از سطح قلب (Trendelenburg) قرار دهید، لباس تنگ را شل کنید، علائم حیاتی را پایش و در صورت نیاز اکسیژن تجویز کنید؛ اغلب موارد وازوواگال طی ۱-۲ دقیقه برطرف می‌شود. از بلندشدن ناگهانی بیمار پس از درمان خودداری شود." else "🚑 If syncope occurs: stop treatment immediately, place patient supine with legs elevated (Trendelenburg), loosen tight clothing, monitor vitals, give oxygen if needed; most vasovagal episodes resolve within 1-2 minutes. Avoid sudden postural changes after treatment.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3c: Asthma / Chronic Respiratory Disease (NEW)
                        if (alerts.contains("آسم")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫁 آسم و بیماری تنفسی مزمن:" else "🫁 Asthma / Chronic Respiratory Disease:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: از بیمار بخواهید اسپری برونکودیلاتور اورژانسی خود را همراه بیاورد؛ از مواد بودار/اسپری‌های قوی که محرک برونکواسپاسم هستند پرهیز شود؛ در آسم شدید، استفاده از رابردم با احتیاط بیشتری انجام شود." else "• Precaution: Have patient bring rescue inhaler to appointment; avoid strong-odor materials/aerosols that trigger bronchospasm; use rubber dam cautiously in severe asthma.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در بیماران با آسم حساس به آسپرین (Aspirin-Exacerbated Respiratory Disease)، تجویز آسپرین و سایر NSAIDها ممنوع است." else "❌ Contraindication: Avoid Aspirin/NSAIDs in patients with Aspirin-Exacerbated Respiratory Disease.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3d: Kidney Disease (NEW)
                        if (alerts.contains("بیماری کلیوی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫘 بیماری کلیوی:" else "🫘 Kidney Disease:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: تنظیم دوز داروهای دفع‌شونده از کلیه؛ در بیماران دیالیزی، درمان روز بعد از دیالیز انجام شود و از بازوی دارای فیستول برای فشارسنجی استفاده نشود." else "• Precaution: Adjust doses of renally-cleared drugs; for dialysis patients, treat the day after dialysis and avoid the fistula arm for BP measurement.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: NSAIDها به‌دلیل سمیت کلیوی و تتراسایکلین‌ها در نارسایی کلیوی ممنوع هستند." else "❌ Contraindication: NSAIDs (nephrotoxic) and Tetracyclines are contraindicated in renal impairment.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3e: Liver Disease / Hepatitis (NEW)
                        if (alerts.contains("بیماری کبدی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫄 بیماری کبدی / هپاتیت:" else "🫄 Liver Disease / Hepatitis:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: کبد محل تولید فاکتورهای انعقادی است؛ احتمال خونریزی بیشتر در نظر گرفته شود. رعایت کامل پروتکل کنترل عفونت به‌دلیل احتمال انتقال از راه خون (هپاتیت B/C)." else "• Precaution: Liver produces clotting factors; higher bleeding risk should be considered. Follow full infection-control protocol given bloodborne transmission risk (Hep B/C).", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در آسیب کبدی قابل‌توجه، دوز استامینوفن باید محدود شود و از داروهایی با متابولیسم عمده کبدی تا حد امکان پرهیز شود." else "❌ Contraindication: Limit Acetaminophen dose in significant liver impairment; avoid drugs with major hepatic metabolism where possible.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3f: Thyroid Disorder (NEW)
                        if (alerts.contains("اختلال تیروئید")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🦋 اختلال تیروئید:" else "🦋 Thyroid Disorder:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: پیش از درمان الکتیو، کنترل بودن وضعیت تیروئید (یوتیروئید) تایید شود." else "• Precaution: Confirm thyroid status is well-controlled (euthyroid) before elective treatment.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در پرکاری تیروئید کنترل‌نشده، مصرف اپی‌نفرین باید به حداقل برسد (خطر آریتمی/توفان تیروئیدی)." else "❌ Contraindication: Minimize Epinephrine in uncontrolled hyperthyroidism (risk of arrhythmia/thyroid storm).", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3g: Osteoporosis / Bisphosphonate (MRONJ Risk) (NEW)
                        if (alerts.contains("استئوپروز")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🦴 استئوپروز / مصرف بیس‌فسفونات (خطر MRONJ):" else "🦴 Osteoporosis / Bisphosphonate (MRONJ Risk):", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط (طبق راهنمای انجمن دندانپزشکی آمریکا ADA): طبق شواهد فعلی، توقف خودسرانه یا دوره‌ای دارو («تعطیلی دارویی») به‌طور روتین توصیه نمی‌شود و تصمیم باید با هماهنگی پزشک/تیم انکولوژی تجویزکننده گرفته شود. حتی‌الامکان از اقدامات تهاجمی استخوانی غیرضروری پرهیز و بهداشت دهان به‌شدت رعایت شود؛ خطر در بیس‌فسفونات‌های وریدی (مصارف سرطانی) بسیار بیشتر از خوراکی (مصرف استئوپروز) است." else "• Precaution (per ADA guidance): Current evidence does not routinely support a unilateral 'drug holiday'; decisions should be coordinated with the prescribing physician/oncology team. Minimize unnecessary invasive bone procedures and maintain strict oral hygiene; risk is markedly higher with IV bisphosphonates (cancer use) than oral (osteoporosis use).", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: انجام جراحی استخوانی الکتیو بدون هماهنگی پزشکی در بیماران پرخطر (بیس‌فسفونات وریدی/سرطانی) توصیه نمی‌شود." else "❌ Contraindication: Elective invasive bone surgery without medical clearance in high-risk (IV bisphosphonate/cancer) patients should be deferred.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3h: Cancer / Chemotherapy / Radiotherapy (NEW)
                        if (alerts.contains("سرطان")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🎗️ سرطان / سابقه شیمی‌درمانی یا پرتودرمانی:" else "🎗️ Cancer / Chemotherapy / Radiotherapy:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: پیش از اقدامات تهاجمی حین شیمی‌درمانی فعال، بررسی شمارش خون (خطر نوتروپنی/ترومبوسیتوپنی) و هماهنگی با آنکولوژیست ضروری است. در سابقه پرتودرمانی سر و گردن، خطر استئورادیونکروز وجود دارد؛ از کشیدن دندان غیرضروری در استخوان تحت تابش پرهیز و مشاوره تخصصی گرفته شود." else "• Precaution: Check blood counts (neutropenia/thrombocytopenia risk) before invasive procedures during active chemo, coordinate with oncologist. History of head/neck radiotherapy carries osteoradionecrosis risk; avoid unnecessary extractions in irradiated bone and seek specialist consultation.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: اقدامات تهاجمی الکتیو در دوره نوتروپنی/ترومبوسیتوپنی فعال بدون هماهنگی پزشکی ممنوع است." else "❌ Contraindication: Elective invasive procedures during active neutropenia/thrombocytopenia without medical clearance.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 4: Pregnancy / Lactation
                        if (alerts.contains("بارداری")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🤰 بارداری و شیردهی:" else "🤰 Pregnancy & Lactation Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.tertiary)
                                    Text(if (isPersian) "• احتیاط: زمان طلایی درمان‌های روتین، سه ماهه دوم بارداری است. رعایت اصل ALARA و استفاده حتمی از پیش‌بند سربی و یقه تیرویید هنگام رادیوگرافی. بی‌حسی انتخابی لیدوکائین ۲٪ با اپی‌نفرین (رده B)." else "• Precaution: Safe window is 2nd trimester. Use lead apron & thyroid collar for X-rays. Lidocaine 2% with 1:100k epi is Class B safe.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: تجویز داروهای رده C و D (تتراسایکلین، بنزودیازپین‌ها، NSAIDها در ۳ ماهه سوم) اکیداً ممنوع است." else "❌ Contraindication: Category C/D drugs (Tetracyclines, NSAIDs in 3rd trimester, Benzodiazepines) strictly prohibited.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 5: Smoking (NEW)
                        if (alerts.contains("دخانیات")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🚬 مصرف دخانیات:" else "🚬 Smoking:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: خطر بیشتر تاخیر در ترمیم زخم، خشکی سوکت پس از کشیدن دندان، پیشرفت بیماری پریودنتال و شکست ایمپلنت. توصیه به ترک یا کاهش مصرف حداقل ۲۴-۴۸ ساعت پیش و پس از جراحی." else "• Precaution: Higher risk of delayed healing, dry socket after extraction, periodontal progression, and implant failure. Recommend cessation/reduction at least 24-48h before and after surgery.", fontSize = protocolBodyFontSize)
                                }
                            }
                        }

                        // Section 6: Recent Surgery / Hospitalization (NEW)
                        if (alerts.contains("جراحی یا بستری اخیر")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🏥 سابقه جراحی یا بستری اخیر:" else "🏥 Recent Surgery / Hospitalization:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: تاییدیه پزشکی جهت انجام درمان‌های الکتیو دندانپزشکی از پزشک معالج اخذ شود؛ توجه به داروهای ضدانعقاد باقی‌مانده، ضعف ایمنی موقت یا خطر عفونت مرتبط با نوع جراحی اخیر." else "• Precaution: Obtain medical clearance from treating physician for elective dental treatment; consider residual anticoagulation, temporary immunosuppression, or infection risk related to the recent surgery.", fontSize = protocolBodyFontSize)
                                }
                            }
                        }

                        // General Clinical Protocol Notice
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "توصیه می‌شود قبل از شروع درمان‌های جراحی، پرینت فرم خوداظهاری توسط بیمار امضا گردد." else "Recommend obtaining patient signed printed declaration prior to surgical procedures.",
                                    fontSize = protocolSmallFontSize,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRiskGuidelinesDialog = false }) {
                        Text(if (isPersian) "متوجه شدم و بستن" else "Understood & Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
            }
        }

        if (showSelfDecDetailsDialog) {
            val selfDecAnswers = remember(patient.selfDeclarationJson) {
                try {
                    val jsonObj = org.json.JSONObject(patient.selfDeclarationJson)
                    val qLabels = listOf(
                        "bleeding" to "1. سابقه خونریزی شدید پس از کشیدن دندان یا جراحی؟",
                        "allergy" to "2. حساسیت به بی‌حسی دندانپزشکی یا پنی‌سیلین؟",
                        "meds" to "3. مصرف داروهای رقیق‌کننده خون (مانند وارفارین/آسپرین)؟",
                        "chronic" to "4. سابقه بیماری قلبی، فشارخون، دیابت یا صرع؟",
                        "pregnant" to "5. بارداری یا شیردهی (ویژه بانوان)؟",
                        "surgery" to "6. سابقه جراحی یا بستری اخیر در بیمارستان؟"
                    )
                    qLabels.map { (key, qText) ->
                        qText to jsonObj.optString(key, "خیر")
                    }
                } catch(e: Exception) {
                    emptyList()
                }
            }

            AlertDialog(
                onDismissRequest = { showSelfDecDetailsDialog = false },
                title = {
                    Text(
                        text = if (isPersian) "فرم خوداظهاری سلامت بیمار: ${patient.name}" else "Patient Health Declaration: ${patient.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selfDecAnswers.isNotEmpty()) {
                            selfDecAnswers.forEach { (qText, ans) ->
                                val ansColor = if (ans == "بله") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(text = qText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "پاسخ: $ans",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ansColor
                                    )
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        } else {
                            Text(text = patient.selfDeclarationJson, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelfDecDetailsDialog = false }) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            )
        }

        // Financial Health Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    netBalance > 0 -> MaterialTheme.colorScheme.errorContainer
                    netBalance < 0 -> Color(0xFFE8F5E9)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = viewModel.label("financial_balance"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatAmount(kotlin.math.abs(netBalance))} $currency",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = when {
                            netBalance > 0 -> DebtColor
                            netBalance < 0 -> ClearColor
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Text(
                        text = when {
                            netBalance > 0 -> viewModel.label("balance_debtor")
                            netBalance < 0 -> viewModel.label("balance_creditor")
                            else -> viewModel.label("balance_settled")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            netBalance > 0 -> DebtColor
                            netBalance < 0 -> ClearColor
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = viewModel.label("total_treatments"), fontSize = 11.sp, color = Color.Gray)
                        Text(text = "${formatAmount(totalCost)} $currency", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = viewModel.label("total_payments"), fontSize = 11.sp, color = Color.Gray)
                        Text(text = "${formatAmount(totalPaid)} $currency", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Send-to-Patient Actions: Statement & Treatment Plan (WhatsApp = image, SMS = short text)
        PatientSendActionsRow(
            patient = patient,
            treatments = treatments,
            payments = payments,
            viewModel = viewModel,
            isPersian = isPersian
        )

        // Segmented Tabs: Treatment / Payments / Installments / Documents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { currentSubTab = "treatments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "treatments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "treatments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(viewModel.label("treatments_tab"), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "payments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "payments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "payments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(viewModel.label("payments_tab"), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "installments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "installments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "installments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(if (isPersian) "اقساط و بدهی" else "Installments", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "documents" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "documents") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "documents") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(if (isPersian) "رادیوگرافی" else "Docs", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Subtab section title with toggle action
        val titleText = when (currentSubTab) {
            "treatments" -> viewModel.label("treatments_tab")
            "installments" -> if (isPersian) "اقساط و بدهی بیمار" else "Installments & Debts"
            "documents" -> if (isPersian) "رادیوگرافی و مدارک" else "Radiographs & Docs"
            else -> viewModel.label("payments_tab")
        }
        val isFormOpen = if (currentSubTab == "treatments") showTreatmentForm else showPaymentForm

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = titleText, fontWeight = FontWeight.Bold)
            if (currentSubTab == "treatments" || currentSubTab == "payments") {
                IconButton(onClick = {
                    if (currentSubTab == "treatments") {
                        showTreatmentForm = !showTreatmentForm
                    } else {
                        showPaymentForm = !showPaymentForm
                    }
                }) {
                    Icon(
                        imageVector = if (isFormOpen) Icons.Default.Close else Icons.Default.AddCircle,
                        contentDescription = "Toggle Form",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (currentSubTab == "installments") {
                Button(
                    onClick = { showAddInstallmentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "ثبت قسط جدید" else "Add Installment", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Render Active List or Forms
        if (currentSubTab == "treatments") {
            // AI Clinical Assistant Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = if (isPersian) "ثبت هوشمند با دستیار صوتی/متنی AI" else "AI Clinical Voice/Text Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isPersian) "استخراج خودکار طرح‌درمان و هزینه‌ها از صورت‌جلسه معاینه" else "Parse exam notes & costs into treatment plan automatically",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { showAiAssistantDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "شروع" else "Start", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showTreatmentForm) {
                AddTreatmentForm(
                    patientId = patient.id,
                    viewModel = viewModel,
                    onSaved = { showTreatmentForm = false }
                )
            } else {
                if (treatments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPersian) "درمانی ثبت نشده" else "No treatments recorded", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        treatments.forEach { treatment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = treatment.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.threeSecondLongPress(
                                                    onLongClick = {
                                                        if (TherapyKnowledgeBase.findProtocol(treatment.title) != null) {
                                                            selectedProtocolTitleForPatientDetail = treatment.title
                                                        }
                                                    }
                                                )
                                            )
                                            val statusColor = when(treatment.status) {
                                                "COMPLETED" -> Color(0xFF2E7D32)
                                                "IN_PROGRESS" -> Color(0xFFF57C00)
                                                "CANCELLED" -> Color(0xFFD32F2F)
                                                else -> Color.Gray
                                            }
                                            val statusText = when(treatment.status) {
                                                "COMPLETED" -> if (isPersian) "پایان یافته" else "Completed"
                                                "IN_PROGRESS" -> if (isPersian) "در حال انجام" else "In Progress"
                                                "CANCELLED" -> if (isPersian) "لغو شده" else "Cancelled"
                                                else -> if (isPersian) "برنامه‌ریزی شده" else "Planned"
                                            }
                                            Surface(
                                                color = statusColor.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(statusText, fontSize = 9.5.sp, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(text = viewModel.formatDate(treatment.date), fontSize = 11.sp, color = Color.Gray)

                                        if (treatment.selectedTeeth.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isPersian) "دندان‌ها: ${treatment.selectedTeeth}" else "Teeth: ${treatment.selectedTeeth}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (treatment.discount > 0.0) {
                                            Surface(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isPersian) "تخفیف: -${formatAmount(treatment.discount)} $currency" else "Discount: -${formatAmount(treatment.discount)} $currency",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val displayNotes = treatment.notes.cleanNotesForDisplay()
                                        if (displayNotes.isNotEmpty()) {
                                            Text(text = displayNotes, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                                        }

                                        // Therapy Stages Checklist (Read-Only in patient record view)
                                        TreatmentStageChecklistCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        // Root Canal Working Lengths
                                        EndoWorkingLengthCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        // Restoration Surface Model (Read-Only)
                                        ToothSurfaceRestorationCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${formatAmount(treatment.cost - treatment.discount)} $currency",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (treatment.discount > 0.0) {
                                                    Text(
                                                        text = if (isPersian) "تخفیف: -${formatAmount(treatment.discount)} $currency" else "Discount: -${formatAmount(treatment.discount)} $currency",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                OutlinedButton(
                                                    onClick = { treatmentToEdit = treatment },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isPersian) "ویرایش درمان" else "Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedIconButton(
                                                    onClick = { treatmentToDelete = treatment }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentSubTab == "installments") {
            if (patientInstallments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPersian) "هیچ قسط یا تعهد مالی برای این بیمار ثبت نشده است" else "No installments recorded", color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patientInstallments.forEach { inst ->
                        val isOverdue = !inst.isPaid && inst.dueDate < System.currentTimeMillis()
                        val statusColor = when {
                            inst.isPaid -> Color(0xFF2E7D32)
                            isOverdue -> Color(0xFFD32F2F)
                            else -> Color(0xFFE65100)
                        }
                        val statusText = when {
                            inst.isPaid -> if (isPersian) "پرداخت شده" else "Paid"
                            isOverdue -> if (isPersian) "معوقه" else "Overdue"
                            else -> if (isPersian) "در انتظار پرداخت" else "Pending"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = inst.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "سررسید: ${viewModel.formatDate(inst.dueDate)}", fontSize = 11.sp, color = Color.Gray)
                                        if (inst.note.isNotBlank()) {
                                            Text(text = inst.note, fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "${formatAmount(inst.amount)} $currency", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = statusColor)
                                        Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                            Text(statusText, fontSize = 9.5.sp, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!inst.isPaid) {
                                        Button(
                                            onClick = { viewModel.markInstallmentPaid(inst) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "ثبت پرداخت و تسویه قسط" else "Settle Installment", fontSize = 10.5.sp)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteInstallment(inst) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentSubTab == "documents") {
            ClinicalDocumentsTab(
                patient = patient,
                viewModel = viewModel,
                isPersian = isPersian
            )
        } else {
            // Payments Sub-Tab
            if (showPaymentForm) {
                AddPaymentForm(
                    patientId = patient.id,
                    viewModel = viewModel,
                    onSaved = { showPaymentForm = false }
                )
            } else {
                if (payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPersian) "دریافتی ثبت نشده" else "No payments recorded", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        payments.forEach { payment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${viewModel.label("payment_type")}: ${viewModel.label(payment.type.lowercase())}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(text = viewModel.formatDate(payment.date), fontSize = 11.sp, color = Color.Gray)
                                            if (payment.type == "CHEQUE") {
                                                Text(
                                                    text = "${viewModel.label("cheque_number")}: ${payment.chequeNumber ?: ""}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Gray
                                                )
                                                if (!payment.sayadiId.isNullOrBlank()) {
                                                    Text(
                                                        text = "شناسه صیادی: ${payment.sayadiId}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                payment.chequeDueDate?.let {
                                                    Text(
                                                        text = "${viewModel.label("cheque_due_date")}: ${viewModel.formatDate(it)}",
                                                        fontSize = 11.sp,
                                                        color = DebtColor
                                                    )
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "${formatAmount(payment.amount)} $currency", fontWeight = FontWeight.Bold, color = ClearColor)
                                            if (editMode) {
                                                IconButton(onClick = { paymentToDelete = payment }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                                }
                                            }
                                        }

                                    // Render Cheque Photo preview if exists
                                    if (payment.type == "CHEQUE" && !payment.chequePhotoBase64.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        SmartImage(
                                            model = payment.chequePhotoBase64,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (treatmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { treatmentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف درمان" else "Confirm Treatment Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف درمان «${treatmentToDelete!!.title}» برای بیمار اطمینان دارید؟"
                    else "Are you sure you want to delete treatment '${treatmentToDelete!!.title}'?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = treatmentToDelete!!
                        viewModel.deleteTreatment(deleted)
                        treatmentToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "درمان «${deleted.title}» حذف شد" else "'${deleted.title}' deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeleteTreatment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف درمان" else "Delete Treatment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { treatmentToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف تراکنش پرداخت" else "Confirm Payment Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف تراکنش پرداخت به مبلغ «${formatAmount(paymentToDelete!!.amount)} $currency» اطمینان دارید؟"
                    else "Are you sure you want to delete this payment of ${formatAmount(paymentToDelete!!.amount)} $currency?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = paymentToDelete!!
                        viewModel.deletePayment(deleted)
                        paymentToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "پرداخت «${formatAmount(deleted.amount)} $currency» حذف شد" else "Payment of ${formatAmount(deleted.amount)} $currency deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeletePayment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف پرداخت" else "Delete Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (treatmentToEdit != null) {
        EditTreatmentDialog(
            treatment = treatmentToEdit!!,
            viewModel = viewModel,
            onDelete = {
                treatmentToDelete = treatmentToEdit
                treatmentToEdit = null
            },
            onDismiss = { treatmentToEdit = null }
        )
    }

    if (selectedProtocolTitleForPatientDetail != null) {
        ClinicalProtocolDialog(
            therapyTitle = selectedProtocolTitleForPatientDetail!!,
            patientMedicalHistory = patient.medicalHistory,
            patientMedicalAlerts = patient.medicalAlerts,
            isPersian = isPersian,
            initialFontSizePt = settings.clinicalFontSizePt,
            onDismiss = { selectedProtocolTitleForPatientDetail = null }
        )
    }

    if (showAiAssistantDialog) {
        AiClinicalAssistantDialog(
            patient = patient,
            viewModel = viewModel,
            onDismiss = { showAiAssistantDialog = false }
        )
    }

    if (showAddInstallmentDialog) {
        AddInstallmentDialog(
            patientId = patient.id,
            viewModel = viewModel,
            onDismiss = { showAddInstallmentDialog = false }
        )
    }
}
}
}

// ==========================================
// AI CLINICAL ASSISTANT DIALOG
// ==========================================
@Composable
fun AiClinicalAssistantDialog(
    patient: Patient,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var parsedResponse by remember { mutableStateOf<ParsedClinicalResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isPersian = viewModel.isPersian.collectAsState().value
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                inputText = matches[0]
            }
        }
    }

    fun launchVoiceRecognition() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (isPersian) "fa-IR" else "en-US")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, if (isPersian) "صورت‌جلسه معاینه بیمار را بگویید..." else "Speak clinical exam note...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "سیستم تشخیص گفتار در این دستگاه فعال نیست", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isPersian) "دستیار صوتی و متنی AI معاینه" else "AI Clinical Voice & Text Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = if (isPersian) "نتایج معاینه، شماره دندان‌ها و هزینه‌ها را بگویید یا تایپ کنید تا طرح‌درمان خودکار استخراج شود:" else "Speak or type clinical findings, teeth numbers, and costs to extract treatments:",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                // Quick Samples
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val samples = if (isPersian) listOf(
                        "دندان ۱۴ عصب کشی ۴ میلیون و دندان ۲۱ روکش ۳.۵ میلیون",
                        "دندان ۴۶ ایمپلنت کامل به مبلغ ۱۵ میلیون",
                        "دندان ۱۱ و ۱۲ ترمیمی کامپوزیت ۲.۵ میلیون"
                    ) else listOf(
                        "Tooth 14 Root Canal 4000000, Tooth 21 Crown 3500000",
                        "Tooth 46 Implant 15000000"
                    )
                    samples.forEach { sample ->
                        FilterChip(
                            selected = false,
                            onClick = { inputText = sample },
                            label = { Text(sample, fontSize = 10.sp) }
                        )
                    }
                }

                // Text Field with Mic Icon
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    placeholder = { Text(if (isPersian) "مثال: دندان ۱۶ نیاز به عصب کشی دارد هزینه ۴.۵ میلیون و دندان ۲۴ پرکردن ۲ میلیون..." else "Type or record voice note...", fontSize = 11.sp) },
                    trailingIcon = {
                        IconButton(onClick = { launchVoiceRecognition() }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                Button(
                    onClick = {
                        if (inputText.isBlank()) return@Button
                        isProcessing = true
                        errorMessage = null
                        parsedResponse = null
                        viewModel.processClinicalAiPrompt(inputText) { success, result ->
                            isProcessing = false
                            if (success && result != null) {
                                parsedResponse = result
                            } else {
                                errorMessage = if (isPersian) "عدم امکان تحلیل هوشمند یا خطای ارتباط با API." else "Failed to parse note with AI."
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPersian) "در حال استخراج هوشمند..." else "Extracting with AI...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPersian) "استخراج و تحلیل طرح‌درمان" else "Extract Treatment Proposals", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                errorMessage?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Results View
                val currentResp = parsedResponse
                if (currentResp != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isPersian) "🎯 طرح‌درمان‌های پیشنهادی هوش مصنوعی:" else "🎯 Extracted Treatment Proposals:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                            if (currentResp.patientAlerts.isNotBlank()) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text("⚠️ ${currentResp.patientAlerts}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(6.dp))
                                }
                            }

                            currentResp.proposals.forEachIndexed { idx, prop ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${idx + 1}. ${prop.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${formatAmount(prop.cost)} تومان", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                        if (prop.teethList.isNotEmpty()) {
                                            Text("دندان‌ها: ${prop.teethList.joinToString(", ")}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        if (prop.notes.isNotBlank()) {
                                            Text(prop.notes, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        if (prop.suggestedStages.isNotEmpty()) {
                                            Text("مراحل: ${prop.suggestedStages.joinToString(" ➔ ")}", fontSize = 10.sp, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    currentResp.proposals.forEach { prop ->
                                        viewModel.addTreatmentWithDetails(
                                            patientId = patient.id,
                                            title = prop.title,
                                            cost = prop.cost,
                                            discount = 0L,
                                            selectedTeeth = prop.teethList.joinToString(","),
                                            notes = prop.notes,
                                            stages = prop.suggestedStages.joinToString(" - ")
                                        )
                                    }
                                    if (currentResp.patientAlerts.isNotBlank()) {
                                        val existing = patient.medicalAlerts
                                        val updatedAlerts = if (existing.isBlank() || existing == "بدون مورد") currentResp.patientAlerts else "$existing, ${currentResp.patientAlerts}"
                                        viewModel.updatePatient(patient.copy(medicalAlerts = updatedAlerts))
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "ثبت تمام این طرح‌درمان‌ها در پرونده" else "Save All Proposals to Record", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADD INSTALLMENT DIALOG
// ==========================================
@Composable
fun AddInstallmentDialog(
    patientId: Long,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("قسط درمان دندانپزشکی") }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var daysOffset by remember { mutableStateOf("30") }
    val isPersian = viewModel.isPersian.collectAsState().value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "➕ ثبت قسط جدید برای بیمار" else "➕ Add New Patient Installment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isPersian) "عنوان قسط" else "Installment Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(if (isPersian) "مبلغ قسط (تومان)" else "Amount") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysOffset,
                    onValueChange = { daysOffset = it },
                    label = { Text(if (isPersian) "مهلت پرداخت (چند روز آینده؟)" else "Due in (days)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (isPersian) "توضیحات یا شماره چک/ضمانت" else "Note / Cheque Info") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toEnglishLong()
                    val offset = daysOffset.toEnglishLong().coerceAtLeast(1)
                    val dueDate = System.currentTimeMillis() + (offset * 24 * 3600 * 1000L)
                    if (amount > 0) {
                        viewModel.addInstallment(
                            patientId = patientId,
                            treatmentId = null,
                            title = title,
                            amount = amount,
                            dueDate = dueDate,
                            note = note
                        )
                        onDismiss()
                    }
                },
                enabled = amountStr.toEnglishLong() > 0
            ) {
                Text(if (isPersian) "ثبت قسط" else "Save Installment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

@Composable
fun EditTreatmentDialog(
    treatment: Treatment,
    viewModel: DentalViewModel,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isPersian = viewModel.isPersian.collectAsState().value
    var title by remember { mutableStateOf(treatment.title) }
    var costStr by remember { mutableStateOf(treatment.cost.toString()) }
    var discountStr by remember { mutableStateOf(treatment.discount.toString()) }
    var selectedTeeth by remember { mutableStateOf(treatment.selectedTeeth) }
    var notes by remember { mutableStateOf(treatment.notes) }
    var status by remember { mutableStateOf(treatment.status) }
    var stages by remember { mutableStateOf(treatment.stages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "ویرایش طرح درمان" else "Edit Treatment Plan", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isPersian) "عنوان درمان" else "Treatment Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text(if (isPersian) "هزینه (تومان)" else "Cost") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text(if (isPersian) "تخفیف (تومان)" else "Discount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = selectedTeeth,
                    onValueChange = { selectedTeeth = it },
                    label = { Text(if (isPersian) "دندان‌های مرتبط (مثلاً 11, 12)" else "Teeth") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(if (isPersian) "وضعیت درمان:" else "Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "برنامه‌ریزی شده",
                        "در حال درمان",
                        "تکمیل شده",
                        "لغو شده"
                    ).forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = stages,
                    onValueChange = { stages = it },
                    label = { Text(if (isPersian) "مراحل درمان (با | جدا کنید)" else "Treatment Stages (separated by |)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isPersian) "توضیحات و ملاحظات بالینی" else "Clinical Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = costStr.toEnglishLong()
                    val discount = discountStr.toEnglishLong()
                    if (title.isNotBlank()) {
                        viewModel.updateTreatment(
                            treatment.copy(
                                title = title.trim(),
                                cost = cost,
                                discount = discount,
                                selectedTeeth = selectedTeeth.trim(),
                                notes = notes.trim(),
                                status = status,
                                stages = stages.trim()
                            )
                        )
                        onDismiss()
                    }
                }
            ) {
                Text(if (isPersian) "ذخیره تغییرات" else "Save Changes")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "حذف طرح درمان" else "Delete", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        }
    )
}

// ----------------------------------------------------
// ULTRA FAST INPUTS: PRE-FILLED TREATMENT TEMPLATES
// ----------------------------------------------------
@Composable
fun AddTreatmentForm(
    patientId: Long,
    viewModel: DentalViewModel,
    onSaved: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var discountStr by remember { mutableStateOf("") }
    var selectedTeeth by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val titleError = if (title.isBlank()) "شرح درمان الزامی است" else null
    val costError = if (!com.example.util.isValidAmount(costStr)) "هزینه باید یک عدد معتبر و غیرمنفی باشد" else null

    val isPersian = viewModel.isPersian.collectAsState().value
    val customTemplates by viewModel.templates.collectAsState()
    val chartSystem by viewModel.dentalChartSystem.collectAsState()

    // Fallback default templates if database doesn't have any
    val starredTemplates = customTemplates.filter { it.isStarred }
    val effectiveTemplates = if (starredTemplates.isNotEmpty()) starredTemplates else customTemplates
    val templates = if (effectiveTemplates.isNotEmpty()) {
        effectiveTemplates.map { TreatmentTemplate(title = it.title, defaultCost = it.defaultCost, defaultNotes = it.defaultNotes) }
    } else {
        listOf(
            TreatmentTemplate(title = "عصب‌کشی (Root Canal Treatment)", defaultCost = 4500000L, defaultNotes = "Root Canal Treatment"),
            TreatmentTemplate(title = "ترمیم کامپوزیت (Composite Filling)", defaultCost = 2000000L, defaultNotes = "Composite Filling"),
            TreatmentTemplate(title = "روکش دندان (Zirconia / PFM Crown)", defaultCost = 3500000L, defaultNotes = "Zirconia Crown"),
            TreatmentTemplate(title = "ایمپلنت تخصصی (Dental Implant)", defaultCost = 12000000L, defaultNotes = "Dental Implant"),
            TreatmentTemplate(title = "کشیدن دندان (Tooth Extraction)", defaultCost = 800000L, defaultNotes = "Tooth Extraction"),
            TreatmentTemplate(title = "جرم‌گیری (Scaling & Polishing)", defaultCost = 1200000L, defaultNotes = "Scaling & Polishing"),
            TreatmentTemplate(title = "ارتودنسی (Orthodontic Treatment)", defaultCost = 25000000L, defaultNotes = "Orthodontic Treatment"),
            TreatmentTemplate(title = "بلیچینگ (In-Office Bleaching)", defaultCost = 4000000L, defaultNotes = "Teeth Whitening"),
            TreatmentTemplate(title = "پالپوتومی و روکش اطفال (Pediatric Pulpotomy)", defaultCost = 2500000L, defaultNotes = "Pediatric Dentistry"),
            TreatmentTemplate(title = "جراحی لثه (Periodontal Surgery)", defaultCost = 3000000L, defaultNotes = "Crown Lengthening / Perio"),
            TreatmentTemplate(title = "ونییر و لمینت (Veneers & Laminate)", defaultCost = 3500000L, defaultNotes = "Cosmetic Veneers"),
            TreatmentTemplate(title = "دست دندان (Complete / Partial Denture)", defaultCost = 8000000L, defaultNotes = "Prosthodontics")
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isPersian) "الگوهای آماده برای ورود سریع:" else "Quick Entry Templates:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Horizontal Scroll for Quick Templates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                templates.forEach { temp ->
                    val dispLabel = temp.title
                    InputChip(
                        selected = title == dispLabel,
                        onClick = {
                            title = dispLabel
                            costStr = temp.defaultCost.toLong().toString()
                            note = temp.defaultNotes
                        },
                        label = { Text(dispLabel, fontSize = 11.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(viewModel.label("treatment_title")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSubmit && titleError != null,
                supportingText = {
                    if (attemptedSubmit && titleError != null) {
                        Text(titleError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text(if (viewModel.isPersian.collectAsState().value) "هزینه واحد (تومان)" else "Unit Cost (Toman)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = attemptedSubmit && costError != null,
                        supportingText = {
                            if (attemptedSubmit && costError != null) {
                                Text(costError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )
                    RealTimeAmountHelper(costStr)
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text(if (isPersian) "تخفیف (تومان)" else "Discount (Toman)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    RealTimeAmountHelper(discountStr)
                }
            }

            // Interactive 32-Tooth Chart
            InteractiveToothChart(
                selectedTeeth = selectedTeeth,
                onTeethChanged = { selectedTeeth = it },
                system = chartSystem
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(viewModel.label("notes")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    attemptedSubmit = true
                    val cost = costStr.toEnglishLong()
                    val discount = discountStr.toEnglishLong()
                    if (titleError == null && costError == null) {
                        var finalNote = note
                        val lowerTitle = title.lowercase()
                        if ((lowerTitle.contains("عصب") || lowerTitle.contains("rct") || lowerTitle.contains("endo") || lowerTitle.contains("ریشه") || lowerTitle.contains("کانال"))
                            && !finalNote.contains("[ENDO_WORKING_LENGTH:")) {
                            val defaultCanals = getDefaultCanalsForTeeth(selectedTeeth)
                            finalNote = encodeEndoCanalData(finalNote, defaultCanals)
                        }

                        viewModel.addTreatmentWithDetails(
                            patientId = patientId,
                            title = title,
                            cost = cost,
                            discount = discount,
                            selectedTeeth = selectedTeeth,
                            notes = finalNote
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun AddPaymentForm(
    patientId: Long,
    viewModel: DentalViewModel,
    onSaved: () -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val amountError = if (!com.example.util.isValidPositiveAmount(amountStr)) "مبلغ باید یک عدد معتبر و بزرگ‌تر از صفر باشد" else null
    var paymentType by remember { mutableStateOf("CASH") } // CASH, CARD, CHEQUE
    var chequeNum by remember { mutableStateOf("") }
    var chequeDueDays by remember { mutableStateOf("30") } // e.g. due in 30 days
    var sayadiId by remember { mutableStateOf("") }
    var chequeBank by remember { mutableStateOf("") }
    var chequeDrawer by remember { mutableStateOf("") }
    var chequePhotoBase64 by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    var showScannerSim by remember { mutableStateOf(false) }

    val isPersian = viewModel.isPersian.collectAsState().value
    val context = androidx.compose.ui.platform.LocalContext.current
    val currency = viewModel.label("currency")


    val contentResolver = context.contentResolver

    // Camera launcher
    val launchCamera = rememberSafeCameraLauncher(context) { path ->
        chequePhotoBase64 = path
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                chequePhotoBase64 = saveUriToLocalFile(context, uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showScannerSim) {
        RealSayadiBarcodeScannerDialog(
            onDismiss = { showScannerSim = false },
            onScanned = { scanned16DigitId ->
                sayadiId = scanned16DigitId
                showScannerSim = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Amount Input
            Column {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(viewModel.label("amount")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = attemptedSubmit && amountError != null,
                    supportingText = {
                        if (attemptedSubmit && amountError != null) {
                            Text(amountError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                )
                RealTimeAmountHelper(amountStr)
            }

            // Payment Type Selector Row
            Text(text = viewModel.label("payment_type"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CASH", "CARD_TRANSFER", "CHEQUE", "POS").forEach { type ->
                    val labelText = viewModel.label(type.lowercase())
                    ElevatedFilterChip(
                        selected = paymentType == type,
                        onClick = { paymentType = type },
                        label = { Text(labelText) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // If Cheque, show comprehensive sayadi cheque fields

            if (paymentType == "CARD_TRANSFER" || paymentType == "POS") {
                if (bankAccounts.isEmpty()) {
                    Text(text = if (isPersian) "ابتدا در بخش مدیریت مالی، حساب/کارت بانکی تعریف کنید" else "Please define a bank account in financial management first", fontSize = 11.sp, color = Color.Gray)
                } else {
                    Text(text = if (isPersian) "انتخاب حساب/کارت مقصد:" else "Select Destination Account/Card:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bankAccounts.forEach { acc ->
                            InputChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
            if (paymentType == "CHEQUE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sayadiId,
                        onValueChange = { sayadiId = it },
                        label = { Text(if (isPersian) "شناسه ۱۶ رقمی صیادی" else "16-digit Sayadi ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { showScannerSim = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = chequeNum,
                        onValueChange = { chequeNum = it },
                        label = { Text(viewModel.label("cheque_number")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    var selectedChequeDueDate by remember { mutableStateOf<Long?>(null) }
                    var formattedDueDateText by remember { mutableStateOf("") }

                    val chequeDueDatePicker = remember {
                        val cal = java.util.Calendar.getInstance()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val selCal = java.util.Calendar.getInstance()
                                selCal.set(year, month, dayOfMonth)
                                selectedChequeDueDate = selCal.timeInMillis
                                formattedDueDateText = viewModel.formatDate(selCal.timeInMillis)
                            },
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH),
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }

                    OutlinedTextField(
                        value = if (formattedDueDateText.isNotEmpty()) formattedDueDateText else (if (isPersian) "انتخاب تاریخ سررسید" else "Select Due Date"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isPersian) "تاریخ سررسید چک" else "Due Date") },
                        trailingIcon = {
                            IconButton(onClick = { chequeDueDatePicker.show() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Due Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { chequeDueDatePicker.show() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = chequeBank,
                        onValueChange = { chequeBank = it },
                        label = { Text(if (isPersian) "بانک صادرکننده" else "Issuing Bank") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chequeDrawer,
                        onValueChange = { chequeDrawer = it },
                        label = { Text(if (isPersian) "نام صادرکننده" else "Drawer Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Camera & Gallery Document Section for Cheque Photo
                Text(
                    text = if (isPersian) "تصویر چک صیادی" else "Sayadi Cheque Photo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { launchCamera() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "دوربین" else "Camera", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                    }
                }

                // Render Photo Preview inside Form
                chequePhotoBase64?.let { base64 ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        SmartImage(
                            model = base64,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { chequePhotoBase64 = null },
                            modifier = Modifier
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    attemptedSubmit = true
                    val amount = amountStr.toEnglishLong()
                    if (amountError == null) {
                        val dueTimestamp = if (paymentType == "CHEQUE") {
                            val days = chequeDueDays.toEnglishLong()
                            System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                        } else null

                        viewModel.addPaymentWithAccount(
                            bankAccountId = selectedAccountId,
                            patientId = patientId,
                            amount = amount,
                            type = paymentType,
                            chequeNum = if (paymentType == "CHEQUE") chequeNum else null,
                            chequeDue = dueTimestamp,
                            sayadiId = if (paymentType == "CHEQUE") sayadiId else null,
                            chequeBank = if (paymentType == "CHEQUE") chequeBank else null,
                            chequeDrawer = if (paymentType == "CHEQUE") chequeDrawer else null,
                            chequePhotoBase64 = if (paymentType == "CHEQUE") chequePhotoBase64 else null
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
        }
    }
    
}

// ==========================================
// 3. APPOINTMENTS SCHEDULER
// ==========================================
// متن مودبانه و نیمه‌رسمی اطلاع‌رسانی نوبت به بیمار (برای پیامک و تصویر واتساپ)
fun buildAppointmentReminderText(isPersian: Boolean, patientName: String, dateMillis: Long, viewModel: DentalViewModel): String {
    val dateStr = viewModel.formatDateFullVerbal(dateMillis)
    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateMillis))
    return if (isPersian) {
        "با سلام و احترام؛\n$patientName عزیز، نوبت شما در مطب دندان‌پزشکی برای روز $dateStr ساعت $timeStr ثبت شده است.\nخواهشمندیم چند دقیقه زودتر تشریف بیاورید.\nبا تشکر از همراهی شما."
    } else {
        "Dear $patientName,\nYour dental appointment is scheduled for $dateStr at $timeStr.\nPlease arrive a few minutes early.\nThank you."
    }
}

@Composable
fun AppointmentsScreen(viewModel: DentalViewModel) {
    val appointments by viewModel.appointments.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }
    var apptToDelete by remember { mutableStateOf<Appointment?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var archiveFilter by remember { mutableStateOf("ALL") } // ALL, TODAY, UPCOMING, PAST

    // Form States
    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var treatmentType by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueDaysStr by remember { mutableStateOf("1") }
    var selectedTreatmentIds by remember { mutableStateOf(setOf<Long>()) }

    val isPersian = viewModel.isPersian.collectAsState().value
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val filteredAppointments = appointments.filter { appt ->
        val matchesQuery = appt.patientName.contains(searchQuery, ignoreCase = true) ||
                appt.treatmentType.contains(searchQuery, ignoreCase = true) ||
                appt.note.contains(searchQuery, ignoreCase = true) ||
                viewModel.formatDate(appt.date).contains(searchQuery)

        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val matchesFilter = when (archiveFilter) {
            "TODAY" -> kotlin.math.abs(appt.date - now) < oneDayMs && !appt.isArchived
            "UPCOMING" -> appt.date >= now && !appt.isArchived
            "PAST" -> appt.isArchived || appt.date < now
            else -> !appt.isArchived
        }

        matchesQuery && matchesFilter
    }.sortedBy { it.date } // نوبت‌ها به ترتیب ساعت و روز

    // گروه‌بندی نوبت‌ها بر اساس روز — هر روز در یک کادر مجزا نمایش داده می‌شود
    val appointmentsByDay = filteredAppointments.groupBy { appt ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = appt.date }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.label("appointments"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showForm = !showForm },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showForm) viewModel.label("cancel") else viewModel.label("quick_appointment"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Search & Filter Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isPersian) "جستجوی نوبت بر اساس نام، نوع درمان یا تاریخ..." else "Search appointments...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Archive Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "ALL" to if (isPersian) "همه نوبت‌ها" else "All",
                "TODAY" to if (isPersian) "نوبت‌های امروز" else "Today",
                "UPCOMING" to if (isPersian) "نوبت‌های آینده" else "Upcoming",
                "PAST" to if (isPersian) "آرشیو نوبت‌های گذشته" else "Past Archive"
            ).forEach { (filterKey, filterLabel) ->
                ElevatedFilterChip(
                    selected = archiveFilter == filterKey,
                    onClick = { archiveFilter = filterKey },
                    label = { Text(filterLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (showForm) {
            // Interactive Fast Booking Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isPersian) "ثبت سریع نوبت پزشک" else "Fast Appointment Scheduler",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Select Patient
                    Text(text = viewModel.label("patient_name"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        patients.forEach { p ->
                            InputChip(
                                selected = selectedPatient?.id == p.id,
                                onClick = { selectedPatient = p },
                                label = { Text(p.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    val patientTreatments = allTreatments.filter { it.patientId == selectedPatient?.id && it.status != "COMPLETED" }
                    if (patientTreatments.isNotEmpty()) {
                        Text(text = if (isPersian) "انتخاب از طرح درمان:" else "Select from Treatment Plan:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            patientTreatments.forEach { tr ->
                                InputChip(
                                    selected = selectedTreatmentIds.contains(tr.id),
                                    onClick = {
                                        selectedTreatmentIds = if (selectedTreatmentIds.contains(tr.id)) {
                                            selectedTreatmentIds - tr.id
                                        } else {
                                            selectedTreatmentIds + tr.id
                                        }
                                    },
                                    label = { Text(tr.title, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = treatmentType,
                        onValueChange = { treatmentType = it },
                        label = { Text(viewModel.label("treatment_type")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    val context = LocalContext.current
                    var selectedDateMills by remember { mutableStateOf<Long?>(null) }
                    var formattedDateText by remember { mutableStateOf("") }

                    val calendar = java.util.Calendar.getInstance()
                    val timePickerDialog = remember {
                        android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val cal = java.util.Calendar.getInstance()
                                selectedDateMills?.let { cal.timeInMillis = it }
                                cal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(java.util.Calendar.MINUTE, minute)
                                selectedDateMills = cal.timeInMillis
                                
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                formattedDateText = sdf.format(cal.time)
                            },
                            calendar.get(java.util.Calendar.HOUR_OF_DAY),
                            calendar.get(java.util.Calendar.MINUTE),
                            true
                        )
                    }
                    val datePickerDialog = remember {
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = java.util.Calendar.getInstance()
                                cal.set(year, month, dayOfMonth)
                                selectedDateMills = cal.timeInMillis
                                
                                val diffMs = cal.timeInMillis - System.currentTimeMillis()
                                val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs) + 1
                                dueDaysStr = if (diffDays > 0) diffDays.toString() else "0"
                                timePickerDialog.show()
                            },
                            calendar.get(java.util.Calendar.YEAR),
                            calendar.get(java.util.Calendar.MONTH),
                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = formattedDateText,
                            onValueChange = {},
                            label = { Text(if (isPersian) "انتخاب تاریخ تقویم" else "Choose Calendar Date") },
                            modifier = Modifier.weight(1.2f),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                                }
                            }
                        )

                        OutlinedTextField(
                            value = dueDaysStr,
                            onValueChange = { dueDaysStr = it },
                            label = { Text(if (isPersian) "چند روز بعد؟" else "In how many days?") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(viewModel.label("notes")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val pat = selectedPatient
                            val days = dueDaysStr.toEnglishLong()
                            val txType = if (selectedTreatmentIds.isNotEmpty()) "Selected Treatments" else treatmentType
                            if (pat != null && txType.isNotEmpty()) {
                                viewModel.addAppointment(
                                    Appointment(
                                        patientId = pat.id,
                                        patientName = pat.name,
                                        date = selectedDateMills ?: (System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)),
                                        treatmentType = txType,
                                        note = note,
                                        plannedTreatmentIds = selectedTreatmentIds.joinToString(",")
                                    )
                                )
                                showForm = false
                                selectedPatient = null
                                treatmentType = ""
                                note = ""
                                selectedTreatmentIds = emptySet()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Appointments List
            if (filteredAppointments.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotBlank() || archiveFilter != "ALL") (if (isPersian) "هیچ نوبتی با این مشخصات یافت نشد" else "No appointments found") else viewModel.label("no_appointments"),
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    appointmentsByDay.forEach { (dayMillis, dayAppts) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // هدر روز — هر روز در کادر مجزای خودش
                                Text(
                                    text = viewModel.formatDateFullVerbal(dayMillis),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                dayAppts.forEach { appt ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(text = appt.patientName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(appt.date))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "🕒 $timeStr",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(text = appt.treatmentType, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        Text(text = viewModel.formatDateFullVerbal(appt.date), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        if (appt.note.isNotEmpty()) {
                                            Text(text = appt.note, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Action buttons in a single row under the appointment field
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { sessionAppointment = appt },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPersian) "شروع جلسه" else "Start Session", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    // اطلاع‌رسانی نوبت به بیمار از طریق پیامک (متن مودبانه و نیمه‌رسمی)
                                    OutlinedIconButton(
                                        onClick = {
                                            val msg = buildAppointmentReminderText(isPersian, appt.patientName, appt.date, viewModel)
                                            sendSmsPrefilled(context, patients.find { it.name == appt.patientName }?.phone ?: "", msg)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Message,
                                            contentDescription = if (isPersian) "اطلاع‌رسانی پیامکی" else "Notify via SMS",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    // اطلاع‌رسانی نوبت به بیمار از طریق واتساپ (تصویر حاوی متن مودبانه و نیمه‌رسمی)
                                    OutlinedIconButton(
                                        onClick = {
                                            val msg = buildAppointmentReminderText(isPersian, appt.patientName, appt.date, viewModel)
                                            val file = generateSimpleInfoCardImage(
                                                context,
                                                title = if (isPersian) "یادآوری نوبت مطب" else "Appointment Reminder",
                                                lines = msg.split("۔", "\n").filter { it.isNotBlank() }
                                            )
                                            shareImageToWhatsApp(context, file)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Chat,
                                            contentDescription = if (isPersian) "اطلاع‌رسانی از طریق واتساپ" else "Notify via WhatsApp",
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    OutlinedIconButton(
                                        onClick = { apptToComplete = appt },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = if (isPersian) "آرشیو نوبت" else "Archive", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    OutlinedIconButton(
                                        onClick = { apptToDelete = appt },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                                } // end dayAppts.forEach
                            }
                        }
                    } // end item
                    } // end appointmentsByDay.forEach
                }
            }
        }
    }

    // Deletion Confirmation Dialog
    if (apptToDelete != null) {
        AlertDialog(
            onDismissRequest = { apptToDelete = null },
            title = { Text(if (isPersian) "حذف نوبت دندانپزشکی" else "Delete Appointment", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف نوبت بیمار «${apptToDelete?.patientName}» اطمینان دارید؟" else "Are you sure you want to delete this appointment?") },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = apptToDelete!!
                        viewModel.deleteAppointment(deleted)
                        apptToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "نوبت «${deleted.patientName}» حذف شد" else "Appointment for '${deleted.patientName}' deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeleteAppointment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف نهایی" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { apptToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    if (apptToComplete != null) {
        AlertDialog(
            onDismissRequest = { apptToComplete = null },
            title = { Text(if (isPersian) "تکمیل و آرشیو نوبت دندانپزشکی" else "Complete & Archive Appointment", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا نوبت بیمار «${apptToComplete?.patientName}» انجام شده است و به آرشیو منتقل شود؟ (طرح درمان‌های مرتبط نیز به وضعیت تکمیل شده تغییر خواهند کرد)" else "Mark appointment as completed and move to archive?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archiveAppointment(apptToComplete!!)
                        apptToComplete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text(if (isPersian) "تکمیل و انتقال به آرشیو" else "Complete & Archive") }
            },
            dismissButton = {
                TextButton(onClick = { apptToComplete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    // Interactive Session Details & Stage Management Dialog
    if (sessionAppointment != null) {
        val appt = sessionAppointment!!
        val patientTreatments = allTreatments.filter { it.patientId == appt.patientId }
        val allTemplatesList by viewModel.templates.collectAsState()
        val settings by viewModel.settings.collectAsState()

        var editNotes by remember { mutableStateOf(appt.note) }
        var editTxType by remember { mutableStateOf(appt.treatmentType) }
        var selectedTemplateForTx by remember { mutableStateOf<Pair<Long, TreatmentTemplate>?>(null) }
        var sessionFinishConfirmed by remember(appt.id) { mutableStateOf(appt.isCompleted) }

        AlertDialog(
            onDismissRequest = { sessionAppointment = null },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isPersian) "مدیریت جلسات و تراپی درمان" else "Session & Therapy Stage Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${if (isPersian) "بیمار: " else "Patient: "}${appt.patientName} | ${viewModel.formatDate(appt.date)}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editTxType,
                        onValueChange = { editTxType = it },
                        label = { Text(if (isPersian) "عنوان درمان / شرح جلسه" else "Session Treatment Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text(viewModel.label("notes")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Stages fetched directly from Database for this patient
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "تراپی‌های این جلسه:" else "Session Therapies:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { showManageTherapiesDialog = true },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (isPersian) "ویرایش تراپی‌های جلسه" else "Manage Session Therapies", fontSize = 10.sp)
                        }
                    }
                    val plannedIds = remember(appt.plannedTreatmentIds) { appt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet() }
                    val sessionTreatments = patientTreatments.filter { plannedIds.contains(it.id) }

                    if (sessionTreatments.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isPersian) "هیچ طرح درمانی برای این بیمار در پرونده ثبت نشده است." else "No treatment plans recorded for this patient.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        sessionTreatments.forEach { tx ->
                            val patientForProtocol = remember(appt.patientId) { patients.find { it.id == appt.patientId } }
                            // Auto-fetch stages from ready templates if tx.stages is empty
                            var txStagesStr = tx.stages
                            if (txStagesStr.isBlank() && allTemplatesList.isNotEmpty()) {
                                val matchedTemp = allTemplatesList.find { temp ->
                                    tx.title.contains(temp.title) || temp.title.contains(tx.title)
                                }
                                if (matchedTemp != null && matchedTemp.stages.isNotBlank()) {
                                    txStagesStr = matchedTemp.stages
                                    viewModel.updateTreatment(tx.copy(stages = matchedTemp.stages))
                                }
                            }

                            val allStagesList = remember(txStagesStr) {
                                parseStagesList(txStagesStr)
                            }
                            val completedStagesList = remember(tx.completedStages) {
                                tx.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }
                            val ignoredStagesList = remember(tx.ignoredStages) {
                                tx.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }
                            val remainingStagesList = remember(allStagesList, completedStagesList, ignoredStagesList) {
                                allStagesList.filter { !completedStagesList.contains(it) && !ignoredStagesList.contains(it) }
                            }
                            // دیالوگ ادیت مرحله جاری در همان نشست (بدون خروج از روند جلسه)
                            var showEditStageDialog by remember { mutableStateOf(false) }
                            var stageEditNoteDraft by remember(tx.id) { mutableStateOf("") }
                            // دیالوگ شرح مفصل پروتکل بالینی تراپی (با لمس طولانی روی عنوان تراپی باز می‌شود)
                            var showTherapyProtocolDialog by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (remainingStagesList.isEmpty() && allStagesList.isNotEmpty()) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Treatment Title & Status Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = run {
                                                        val sessionNumber = completedStagesList.size + 1
                                                        val shortTitle = tx.title.lineSequence().firstOrNull()?.trim().orEmpty()
                                                        "$shortTitle — ${if (isPersian) "جلسه $sessionNumber" else "Session $sessionNumber"}"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.threeSecondLongPress(
                                                        onLongClick = {
                                                            if (TherapyKnowledgeBase.findProtocol(tx.title) != null) {
                                                                showTherapyProtocolDialog = true
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            if (tx.selectedTeeth.isNotBlank()) {
                                                Text(text = "${if (isPersian) "دندان:" else "Tooth:"} ${tx.selectedTeeth}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        Surface(
                                            color = when (tx.status) {
                                                "COMPLETED" -> Color(0xFFE8F5E9)
                                                "IN_PROGRESS" -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = when (tx.status) {
                                                    "COMPLETED" -> if (isPersian) "تکمیل شده" else "Completed"
                                                    "IN_PROGRESS" -> if (isPersian) "در حال انجام" else "In Progress"
                                                    else -> if (isPersian) "برنامه‌ریزی شده" else "Planned"
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (tx.status) {
                                                    "COMPLETED" -> Color(0xFF2E7D32)
                                                    "IN_PROGRESS" -> MaterialTheme.colorScheme.primary
                                                    else -> Color.Gray
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Option to import/sync stages from ready templates if empty
                                    if (allStagesList.isEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    Text(
                                                        text = if (isPersian) "فراخوانی مراحل درمان از الگوهای آماده:" else "Import stages from ready templates:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    allTemplatesList.forEach { template ->
                                                        AssistChip(
                                                            onClick = {
                                                                viewModel.updateTreatment(tx.copy(stages = template.stages))
                                                            },
                                                            label = { Text(template.title, fontSize = 10.sp) },
                                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Progress Bar
                                        val progress = completedStagesList.size.toFloat() / allStagesList.size.toFloat()
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(
                                                    text = "${if (isPersian) "پیشرفت مراحل تراپی:" else "Stage Progress:"} ${completedStagesList.size} از ${allStagesList.size}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            )
                                        }

                                        // Section 1: Completed stages in previous sessions
                                        if (completedStagesList.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = if (isPersian) "✓ مراحل انجام‌شده در جلسات قبلی:" else "✓ Completed in previous sessions:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                completedStagesList.forEach { doneStage ->
                                                    Surface(
                                                        color = Color(0xFFE8F5E9),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                                                Text(
                                                                    text = doneStage,
                                                                    fontSize = 11.sp,
                                                                    color = Color(0xFF1B5E20),
                                                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { viewModel.toggleTreatmentStage(tx, doneStage) },
                                                                modifier = Modifier.size(18.dp)
                                                            ) {
                                                                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Section 2: Active stage for THIS session & Future queued stages
                                        val activeCurrentStage = remainingStagesList.firstOrNull()
                                        val futureQueuedStages = remainingStagesList.drop(1)

                                        if (activeCurrentStage != null) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = if (isPersian) "📍 مرحله اختصاصی جهت اجرا در این جلسه:" else "📍 Active Stage for THIS Session:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                // Primary Active Stage Card for Current Session
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                Text(
                                                                    text = activeCurrentStage,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (isPersian) "جلسه جاری" else "Current Session",
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }

                                                        Button(
                                                            onClick = {
                                                                // Confirm and execute stage in this session
                                                                viewModel.toggleTreatmentStage(tx, activeCurrentStage)
                                                                val updatedType = "${tx.title} ($activeCurrentStage)"
                                                                editTxType = updatedType
                                                                viewModel.updateAppointment(appt.copy(treatmentType = updatedType, note = editNotes))
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(vertical = 6.dp)
                                                        ) {
                                                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = if (isPersian) "✓ تایید و ثبت انجام این مرحله در این جلسه" else "✓ Confirm & Complete Stage in this Session",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        // ردیف اکشن‌های تکمیلی: ایگنور کردن / ادیت همین جلسه / تکرار جلسه قبلی
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    // بی‌نیازی از این مرحله: ایگنور کردن بدون علامت‌گذاری به‌عنوان انجام‌شده
                                                                    viewModel.ignoreTreatmentStage(tx, activeCurrentStage)
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
                                                            ) {
                                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (isPersian) "عدم نیاز (ایگنور)" else "Ignore",
                                                                    fontSize = 9.sp
                                                                )
                                                            }
                                                            OutlinedButton(
                                                                onClick = {
                                                                    stageEditNoteDraft = editNotes
                                                                    showEditStageDialog = true
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (isPersian) "ادیت همین جلسه" else "Edit Session",
                                                                    fontSize = 9.sp
                                                                )
                                                            }
                                                            if (completedStagesList.isNotEmpty()) {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        // تکرار آخرین مرحلهٔ انجام‌شده از جلسه قبلی به‌عنوان مرحله جاری
                                                                        viewModel.repeatLastCompletedStage(tx)
                                                                    },
                                                                    modifier = Modifier.weight(1f),
                                                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text(
                                                                        text = if (isPersian) "تکرار جلسه قبل" else "Repeat Last",
                                                                        fontSize = 9.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (showEditStageDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showEditStageDialog = false },
                                                        title = {
                                                            Text(if (isPersian) "ادیت جلسه جاری" else "Edit Current Session")
                                                        },
                                                        text = {
                                                            OutlinedTextField(
                                                                value = stageEditNoteDraft,
                                                                onValueChange = { stageEditNoteDraft = it },
                                                                label = { Text(if (isPersian) "یادداشت این جلسه" else "Session Note") },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                minLines = 3
                                                            )
                                                        },
                                                        confirmButton = {
                                                            TextButton(onClick = {
                                                                editNotes = stageEditNoteDraft
                                                                viewModel.updateAppointment(appt.copy(note = stageEditNoteDraft))
                                                                showEditStageDialog = false
                                                            }) {
                                                                Text(if (isPersian) "ذخیره" else "Save")
                                                            }
                                                        },
                                                        dismissButton = {
                                                            TextButton(onClick = { showEditStageDialog = false }) {
                                                                Text(if (isPersian) "انصراف" else "Cancel")
                                                            }
                                                        }
                                                    )
                                                }

                                                if (showTherapyProtocolDialog) {
                                                    ClinicalProtocolDialog(
                                                        therapyTitle = tx.title,
                                                        patientMedicalHistory = patientForProtocol?.medicalHistory ?: "",
                                                        patientMedicalAlerts = patientForProtocol?.medicalAlerts ?: "",
                                                        isPersian = isPersian,
                                                        initialFontSizePt = settings.clinicalFontSizePt,
                                                        onDismiss = { showTherapyProtocolDialog = false }
                                                    )
                                                }

                                                // Section 3: Future queued stages for upcoming sessions
                                                if (futureQueuedStages.isNotEmpty()) {
                                                    Text(
                                                        text = if (isPersian) "⏳ مراحل و جلسات بعدی تراپی (در نوبت درمان):" else "⏳ Upcoming Future Therapy Sessions:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Gray,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )

                                                    futureQueuedStages.forEachIndexed { fIdx, futureStage ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                                    Text(
                                                                        text = futureStage,
                                                                        fontSize = 11.sp,
                                                                        color = Color.DarkGray
                                                                    )
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = if (isPersian) "جلسه ${completedStagesList.size + fIdx + 2}" else "Session ${completedStagesList.size + fIdx + 2}",
                                                                            fontSize = 9.sp,
                                                                            color = Color.Gray,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // All stages finished
                                            Surface(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                                    Text(
                                                        text = if (isPersian) "تمامی مراحل تراپی این درمان با موفقیت به پایان رسید." else "All therapy stages for this treatment have been completed.",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Root Canal Working Length (Endo Working Length)
                                    EndoWorkingLengthCard(treatment = tx, viewModel = viewModel)

                                    // Restoration Surface Model (Editable)
                                    ToothSurfaceRestorationCard(treatment = tx, viewModel = viewModel)
                                }
                            }
                        }
                    }

                    // پایان جلسه و آرشیو نوبت — در انتهای صفحه مدیریت جلسات و تراپی درمان
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = sessionFinishConfirmed, onCheckedChange = { sessionFinishConfirmed = it })
                        Text(
                            text = if (isPersian) "این جلسه به پایان رسید (اتمام کردن)" else "This session has finished",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAppointment(appt.copy(treatmentType = editTxType, note = editNotes, isCompleted = sessionFinishConfirmed))
                        sessionAppointment = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(viewModel.label("save"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { sessionAppointment = null }) { Text(viewModel.label("cancel")) }
            }
        )
    }

}

// ==========================================
// MORE MODULES MENU SELECTION GRID
// ==========================================
@Composable
fun MoreModulesGridScreen(viewModel: DentalViewModel) {
    val isPersian = viewModel.isPersian.collectAsState().value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isPersian) "پنل‌های تخصصی و حسابداری مطب" else "Specialized Modules & General Ledgers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1: Vendors & Labs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("vendors"),
                        desc = if (isPersian) "تدارکات و خرید اقلام مصرفی" else "Suppliers & supplies orders",
                        icon = Icons.Default.ShoppingCart,
                        onClick = { viewModel.currentTab.value = "vendors" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = viewModel.label("labs"),
                        desc = if (isPersian) "ساخت روکش و پروتزهای دندانی" else "Prosthetics, crowns & dentures",
                        icon = Icons.Default.Build,
                        onClick = { viewModel.currentTab.value = "labs" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Equipment Maintenance & Clinic Expenses
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("maintenance"),
                        desc = if (isPersian) "تعمیر اتوکلاو، توربین و یونیت" else "Repairs & dental chair maintenance",
                        icon = Icons.Default.Handyman,
                        onClick = { viewModel.currentTab.value = "maintenance" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = viewModel.label("expenses"),
                        desc = if (isPersian) "قبوض، اجاره مطب، حقوق پرسنل" else "Clinic bills, salaries & utilities",
                        icon = Icons.Default.ReceiptLong,
                        onClick = { viewModel.currentTab.value = "expenses" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Dentist Personal Accounting
                // (توجه: «الگوهای آماده درمان» به منوی تنظیمات اختصاصی — لمس ۲ثانیه‌ای چرخ‌دنده بالای صفحه — منتقل شد)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("personal"),
                        desc = if (isPersian) "حساب‌ها و برداشت‌های شخصی پزشک" else "Private bookkeeping & drawers ledger",
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = { viewModel.currentTab.value = "personal" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 4: Sayadi Cheques Management & Bank Accounts
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = if (isPersian) "مدیریت چک‌های صیادی" else "Sayad Cheques Ledger",
                        desc = if (isPersian) "اسکن صیاد، پاس، برگشتی، انتقال چک" else "Scan, clear, bounce & endorse cheques",
                        icon = Icons.Default.ConfirmationNumber,
                        onClick = { viewModel.currentTab.value = "cheques" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = if (isPersian) "حساب‌ها و کارت‌ها" else "Bank Accounts",
                        desc = if (isPersian) "مدیریت موجودی حساب‌های بانکی" else "Manage bank balances",
                        icon = Icons.Default.CreditCard,
                        onClick = { viewModel.currentTab.value = "bank" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 5: Clinic Settings & Reports Center
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = if (isPersian) "شخصی‌سازی مطب" else "Clinic Branding",
                        desc = if (isPersian) "نام کلینیک، پزشک و چیدمان داشبورد" else "Branding, staff list & dashboard layout",
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = { viewModel.currentTab.value = "settings" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = if (isPersian) "گزارش‌ها و اسناد" else "Reports & Documents",
                        desc = if (isPersian) "چاپ فاکتور، صورت‌حساب و گزارش‌های مالی PDF" else "Print invoices, statements & financial PDFs",
                        icon = Icons.Default.Description,
                        onClick = { viewModel.currentTab.value = "reports" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleMenuCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
            }
        }
    }
    
}

// ==========================================
// 4. VENDORS MANAGEMENT
// ==========================================
@Composable
fun VendorsScreen(viewModel: DentalViewModel) {
    val vendors by viewModel.vendors.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var vendorToDelete by remember { mutableStateOf<Vendor?>(null) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }

    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsState().value
    val scope = rememberCoroutineScope()

    var invoicePhotoBase64 by remember { mutableStateOf<String?>(null) }
    var isScanningInvoice by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // Camera launcher
    val launchCamera = rememberSafeCameraLauncher(context) { path ->
        invoicePhotoBase64 = path
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                invoicePhotoBase64 = saveUriToLocalFile(context, uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("vendors"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_vendor"))
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Document & Invoice Camera/Gallery picker
                    Text(
                        text = if (isPersian) "تصویر فاکتور خرید" else "Purchase Invoice Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "عکس دوربین" else "Camera Image", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                        }
                    }

                    // Photo preview and AI trigger
                    invoicePhotoBase64?.let { base64 ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                SmartImage(
                                    model = base64,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { invoicePhotoBase64 = null },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isScanningInvoice = true
                                            val result = GeminiClient.scanInvoice(base64)
                                            if (result != null) {
                                                name = result.vendorName
                                                company = result.companyName
                                                type = result.itemType.ifEmpty { "Consumables" }
                                                orders = result.amount.toLong().toString()
                                                paid = result.amount.toLong().toString()
                                            }
                                            isScanningInvoice = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isScanningInvoice
                                ) {
                                    if (isScanningInvoice) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isPersian) "در حال خواندن فاکتور..." else "Extracting Invoice...")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPersian) "استخراج خودکار مبلغ و فروشنده (هوش مصنوعی)" else "AI Auto-Extract Amount & Vendor")
                                    }
                                }
                            }
                    }

                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(viewModel.label("vendor_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text(viewModel.label("company_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(viewModel.label("phone")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(viewModel.label("product_type")) }, modifier = Modifier.fillMaxWidth())
                    
                    Column {
                        OutlinedTextField(value = orders, onValueChange = { orders = it }, label = { Text(viewModel.label("total_purchases")) }, modifier = Modifier.fillMaxWidth())
                        RealTimeAmountHelper(orders)
                    }

                    Column {
                        OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text(viewModel.label("paid_amount")) }, modifier = Modifier.fillMaxWidth())
                        RealTimeAmountHelper(paid)
                    }

                    Button(
                        onClick = {
                            val ord = orders.toEnglishLong()
                            val pd = paid.toEnglishLong()
                            if (name.isNotEmpty()) {
                                viewModel.addVendor(name, company, phone, type, ord, pd, invoicePhotoBase64)
                                showForm = false
                                // reset
                                name = ""
                                company = ""
                                phone = ""
                                type = ""
                                orders = ""
                                paid = ""
                                invoicePhotoBase64 = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vendors) { vendor ->
                    val balance = vendor.totalOrders - vendor.totalPaid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${vendor.name} (${vendor.company})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "${viewModel.label("product_type")}: ${vendor.itemType}", fontSize = 12.sp, color = Color.Gray)
                                Text(text = "${viewModel.label("phone")}: ${vendor.phone}", fontSize = 11.sp, color = Color.Gray)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text(text = "${viewModel.label("total_purchases")}: ${formatAmount(vendor.totalOrders)} $currency", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(text = "${viewModel.label("paid_amount")}: ${formatAmount(vendor.totalPaid)} $currency", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(100.dp)) {
                                Text(text = viewModel.label("vendor_debt"), fontSize = 10.sp, color = Color.Gray)
                                Text(text = "${formatAmount(balance)} $currency", fontWeight = FontWeight.Bold, color = if (balance > 0) DebtColor else ClearColor)
                                
                                // Render thumbnail if invoice photo exists
                                vendor.invoicePhotoBase64?.let { base64 ->
                                    var showZoom by remember { mutableStateOf(false) }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Card(
                                        onClick = { showZoom = true },
                                        modifier = Modifier.size(44.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color.LightGray)
                                    ) {
                                        SmartImage(
                                            model = base64,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    if (showZoom) {
                                        AlertDialog(
                                            onDismissRequest = { showZoom = false },
                                            title = { Text(if (isPersian) "تصویر فاکتور خرید" else "Invoice Photo Preview") },
                                            text = {
                                                SmartImage(
                                                    model = base64,
                                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showZoom = false }) {
                                                    Text(if (isPersian) "بستن" else "Close")
                                                }
                                            }
                                        )
                                    }
                                }

                                IconButton(onClick = { vendorToDelete = vendor }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (vendorToDelete != null) {
        AlertDialog(
            onDismissRequest = { vendorToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف تامین‌کننده/لابراتوار" else "Confirm Vendor Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف کامل اطلاعات «${vendorToDelete!!.name} (${vendorToDelete!!.company})» اطمینان دارید؟ این عمل غیرقابل بازگشت است."
                    else "Are you sure you want to delete vendor '${vendorToDelete!!.name}'?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVendor(vendorToDelete!!)
                        vendorToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف اطلاعات" else "Delete Vendor", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vendorToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

// ==========================================
// 5. DENTAL LABS
// ==========================================
@Composable
fun LabsScreen(viewModel: DentalViewModel) {
    val orders by viewModel.labOrders.collectAsState()
    val services by viewModel.labServices.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val isPersian = viewModel.isPersian.collectAsState().value
    val currency = viewModel.label("currency")

    var selectedTab by remember { mutableStateOf(0) } // 0: Orders, 1: Services Catalog
    var showForm by remember { mutableStateOf(false) }
    var labOrderToDelete by remember { mutableStateOf<LabOrder?>(null) }
    var serviceToDelete by remember { mutableStateOf<LabService?>(null) }
    var serviceToEdit by remember { mutableStateOf<LabService?>(null) }

    // Lab Order Form inputs
    var selectedLabName by remember { mutableStateOf("") }
    var selectedPatientObj by remember { mutableStateOf<Patient?>(null) }
    var toothNum by remember { mutableStateOf("") }
    var prosthesis by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }
    var formattedDueDateText by remember { mutableStateOf("") }
    var deliveryType by remember { mutableStateOf("FINAL") } // FRAME or FINAL
    var selectedTxIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var itemPriceOverrides by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // Service Form inputs
    var sLabName by remember { mutableStateOf("") }
    var sServiceName by remember { mutableStateOf("") }
    var sPriceStr by remember { mutableStateOf("") }
    var sNotes by remember { mutableStateOf("") }
    var sFrameDays by remember { mutableStateOf("3") }
    var sFinalDays by remember { mutableStateOf("7") }

    val context = LocalContext.current

    val dueDatePicker = remember {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selCal = java.util.Calendar.getInstance()
                selCal.set(year, month, dayOfMonth)
                selectedDueDate = selCal.timeInMillis
                formattedDueDateText = viewModel.formatDateFullVerbal(selCal.timeInMillis)
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    val availableLabNames = (services.map { it.labName } + vendors.map { it.name } + listOf("لابراتوار مرکزی", "لابراتوار پارس")).distinct().filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("labs"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (showForm) viewModel.label("cancel")
                    else if (selectedTab == 0) viewModel.label("add_lab_order")
                    else if (isPersian) "افزودن خدمت جدید" else "Add Lab Service"
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; showForm = false },
                text = { Text(if (isPersian) "ثبت و لیست سفارشات کاری" else "Lab Orders", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; showForm = false },
                text = { Text(if (isPersian) "تعرفه و لیست قیمت خدمات" else "Lab Prices Catalog", fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedTab == 0) {
            // Orders Tab
            if (showForm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (isPersian) "ثبت سفارش پروتز به لابراتوار" else "New Prosthesis Order", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        // 1. Lab Selector (Dropdown)
                        Text(if (isPersian) "انتخاب لابراتوار:" else "Select Lab:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var labDropdownExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = labDropdownExpanded,
                            onExpandedChange = { labDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedLabName,
                                onValueChange = { selectedLabName = it; labDropdownExpanded = true },
                                label = { Text(if (isPersian) "نام لابراتوار (انتخاب یا تایپ جدید)" else "Lab Name (select or type new)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            val filteredLabNames = availableLabNames.filter { selectedLabName.isBlank() || it.contains(selectedLabName, ignoreCase = true) }
                            if (filteredLabNames.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = labDropdownExpanded,
                                    onDismissRequest = { labDropdownExpanded = false }
                                ) {
                                    filteredLabNames.forEach { lName ->
                                        DropdownMenuItem(
                                            text = { Text(lName) },
                                            onClick = {
                                                selectedLabName = lName
                                                labDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Patient Selector (Dropdown) & Auto-Fill from Record
                        Text(if (isPersian) "انتخاب بیمار از پرونده:" else "Select Patient:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var patientDropdownExpanded by remember { mutableStateOf(false) }
                        var patientQuery by remember { mutableStateOf("") }
                        ExposedDropdownMenuBox(
                            expanded = patientDropdownExpanded,
                            onExpandedChange = { patientDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPatientObj?.name ?: patientQuery,
                                onValueChange = {
                                    patientQuery = it
                                    selectedPatientObj = null
                                    selectedTxIds = emptySet()
                                    itemPriceOverrides = emptyMap()
                                    patientDropdownExpanded = true
                                },
                                label = { Text(viewModel.label("patient_name")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            val filteredPatients = patients.filter { patientQuery.isBlank() || it.name.contains(patientQuery, ignoreCase = true) }
                            if (filteredPatients.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = patientDropdownExpanded,
                                    onDismissRequest = { patientDropdownExpanded = false }
                                ) {
                                    filteredPatients.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                selectedPatientObj = p
                                                patientQuery = ""
                                                patientDropdownExpanded = false
                                                // Auto-select all of the patient's open treatments by default (still editable below)
                                                val pTreatments = allTreatments.filter { it.patientId == p.id && it.status != "COMPLETED" && isLabRelatedTreatment(it, services) }
                                                selectedTxIds = pTreatments.map { it.id }.toSet()
                                                itemPriceOverrides = pTreatments.associate { tx ->
                                                    val matchedService = services.find { s ->
                                                        (selectedLabName.isBlank() || s.labName.equals(selectedLabName, ignoreCase = true)) &&
                                                        (s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true))
                                                    } ?: services.find { s -> s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true) }
                                                    tx.id to (matchedService?.price ?: 0L).toString()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Itemized treatment checklist — matches each treatment to the lab's price list,
                        // groups by type, and combines everything into a single invoice.
                        if (selectedPatientObj != null) {
                            val patientTreatments = allTreatments.filter { it.patientId == selectedPatientObj!!.id && isLabRelatedTreatment(it, services) }
                            if (patientTreatments.isNotEmpty()) {
                                Text(
                                    if (isPersian) "خدمات مربوط به این بیمار (تیک بزنید تا در فاکتور واحد لحاظ شود):" else "Patient's services (check to include in the combined invoice):",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    sessionTreatments.forEach { tx ->
                                        val isChecked = selectedTxIds.contains(tx.id)
                                        val priceStr = itemPriceOverrides[tx.id] ?: tx.cost.toString()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedTxIds = if (checked) selectedTxIds + tx.id else selectedTxIds - tx.id
                                                    if (checked && !itemPriceOverrides.containsKey(tx.id)) {
                                                        val matchedService = services.find { s ->
                                                            (selectedLabName.isBlank() || s.labName.equals(selectedLabName, ignoreCase = true)) &&
                                                            (s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true))
                                                        }
                                                        itemPriceOverrides = itemPriceOverrides + (tx.id to (matchedService?.price ?: tx.cost).toString())
                                                    }
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${tx.title}${if (tx.selectedTeeth.isNotBlank()) " (${if (isPersian) "دندان" else "Tooth"} ${tx.selectedTeeth})" else ""}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedTextField(
                                                value = priceStr,
                                                onValueChange = { itemPriceOverrides = itemPriceOverrides + (tx.id to it) },
                                                modifier = Modifier.width(110.dp),
                                                singleLine = true,
                                                enabled = isChecked,
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }

                                // Recompute the aggregated fields whenever the selection changes
                                LaunchedEffect(selectedTxIds, itemPriceOverrides, selectedPatientObj) {
                                    val chosen = patientTreatments.filter { selectedTxIds.contains(it.id) }
                                    if (chosen.isNotEmpty()) {
                                        toothNum = chosen.map { it.selectedTeeth }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                                        val grouped = chosen.groupBy { it.title }
                                        prosthesis = grouped.entries.joinToString(" + ") { (title, list) -> if (list.size > 1) "$title x${list.size}" else title }
                                        costStr = chosen.sumOf { (itemPriceOverrides[it.id] ?: it.cost.toString()).toEnglishLongOrNull() ?: it.cost }.toString()
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isPersian) "تعداد خدمات انتخاب‌شده: ${selectedTxIds.size}" else "Selected services: ${selectedTxIds.size}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${formatAmount(costStr.toEnglishLongOrNull() ?: 0L)} $currency",
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Quick Service selector from Catalog for selected lab
                        val availableServices = services.filter { s -> selectedLabName.isBlank() || s.labName.contains(selectedLabName, ignoreCase = true) }
                        if (availableServices.isNotEmpty()) {
                            Text(if (isPersian) "انتخاب خدمت از تعرفه و محاسبه هوشمند زمان تحویل:" else "Select Service from Price Catalog:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableServices.forEach { s ->
                                    FilterChip(
                                        selected = prosthesis == s.serviceName,
                                        onClick = {
                                            if (selectedLabName.isBlank()) selectedLabName = s.labName
                                            // این یک سفارش تک‌خدمتی سریع است؛ برای جلوگیری از ناهماهنگی قیمت،
                                            // انتخاب چک‌لیست درمان‌های تعدادی (که مبلغ را خودکار بازنویسی می‌کند) پاک می‌شود
                                            // تا قیمت دقیقاً همان تعرفه ثبت‌شده لابراتوار باقی بماند.
                                            selectedTxIds = emptySet()
                                            itemPriceOverrides = emptyMap()
                                            prosthesis = s.serviceName
                                            costStr = s.price.toString()
                                            val days = if (deliveryType == "FRAME") s.frameDeliveryDays else s.finalDeliveryDays
                                            val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                            selectedDueDate = targetTime
                                            formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                        },
                                        label = { Text("${s.serviceName} (${formatAmount(s.price)})", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(value = toothNum, onValueChange = { toothNum = it }, label = { Text(viewModel.label("tooth_num")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                        OutlinedTextField(value = prosthesis, onValueChange = { prosthesis = it }, label = { Text(viewModel.label("prosthesis_type")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                        // هشدار شفاف‌سازی: اگر قیمت/عنوان فعلی با تعرفه رسمی ثبت‌شده لابراتوار مطابقت نداشته باشد، به دندانپزشک اطلاع داده می‌شود
                        // تا از ناهماهنگی احتمالی بین فاکتور نهایی و تعرفه رسمی لابراتوار آگاه باشد.
                        run {
                            val catalogMatch = services.find { it.serviceName.equals(prosthesis, ignoreCase = true) && (selectedLabName.isBlank() || it.labName.equals(selectedLabName, ignoreCase = true)) }
                            val currentCost = costStr.toEnglishLongOrNull() ?: 0L
                            if (prosthesis.isNotBlank() && (catalogMatch == null || catalogMatch.price != currentCost)) {
                                Text(
                                    text = if (isPersian) "⚠️ این مبلغ/عنوان با تعرفه رسمی ثبت‌شده لابراتوار مطابقت ندارد (احتمال ناهماهنگی قیمت)."
                                    else "⚠️ This title/price doesn't match the lab's official catalog rate (possible pricing mismatch).",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Text(if (isPersian) "نوع تحویل سفارش:" else "Delivery Type:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = deliveryType == "FRAME",
                                onClick = {
                                    deliveryType = "FRAME"
                                    val matchedServ = services.find { it.serviceName.contains(prosthesis, ignoreCase = true) }
                                    val days = matchedServ?.frameDeliveryDays ?: 3
                                    val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                    selectedDueDate = targetTime
                                    formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                },
                                label = { Text(if (isPersian) "امتحان فریم (۳ روزه)" else "Frame Test", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = deliveryType == "FINAL",
                                onClick = {
                                    deliveryType = "FINAL"
                                    val matchedServ = services.find { it.serviceName.contains(prosthesis, ignoreCase = true) }
                                    val days = matchedServ?.finalDeliveryDays ?: 7
                                    val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                    selectedDueDate = targetTime
                                    formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                },
                                label = { Text(if (isPersian) "تحویل نهایی (۷ روزه)" else "Final Delivery", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = costStr, onValueChange = { costStr = it }, label = { Text(if (isPersian) "هزینه کل فاکتور (تومان)" else "Total Cost (Toman)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(
                                value = if (formattedDueDateText.isNotEmpty()) formattedDueDateText else (if (isPersian) "انتخاب تاریخ تحویل" else "Select Delivery Date"),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (isPersian) "تاریخ و روز تحویل سررسید" else "Due Delivery Date") },
                                trailingIcon = {
                                    IconButton(onClick = { dueDatePicker.show() }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Delivery Date", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.weight(1f).clickable { dueDatePicker.show() }
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isPersian) "ℹ️ با ثبت سفارش، مبلغ فاکتور بلافاصله به صورت بدهی به لابراتوار در هزینه های جاری ثبت می گردد."
                                else "ℹ️ Total invoice cost will be automatically posted to Clinic Expenses as lab debt.",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val cost = costStr.toEnglishLong()
                                val dueTime = selectedDueDate ?: (System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L)
                                val pName = selectedPatientObj?.name ?: ""
                                if (selectedLabName.isNotEmpty() && pName.isNotEmpty()) {
                                    viewModel.addProsthesisOrderAndExpense(
                                        labName = selectedLabName,
                                        patientName = pName,
                                        toothNum = toothNum,
                                        prosthesis = prosthesis,
                                        cost = cost,
                                        dueDate = dueTime
                                    )
                                    showForm = false
                                    selectedLabName = ""
                                    selectedPatientObj = null
                                    selectedTxIds = emptySet()
                                    itemPriceOverrides = emptyMap()
                                    patientQuery = ""
                                    toothNum = ""
                                    prosthesis = ""
                                    costStr = ""
                                    formattedDueDateText = ""
                                    selectedDueDate = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPersian) "ثبت سفارش و صدور فاکتور بدهی" else "Save Order & Issue Invoice")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "لابراتوار ${order.labName} ⬅️ بیمار: ${order.patientName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "${viewModel.label("prosthesis_type")}: ${order.prosthesisType} (${viewModel.label("tooth_num")}: ${order.toothNumber})", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "سررسید تحویل: ${viewModel.formatDateFullVerbal(order.dueDate)}", fontSize = 11.sp, color = DebtColor, fontWeight = FontWeight.Bold)
                                        Text(text = "${viewModel.label("cost")}: ${formatAmount(order.cost)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { labOrderToDelete = order }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("ORDERED", "RECEIVED", "FITTED").forEach { status ->
                                        val isSelected = order.status == status
                                        ElevatedButton(
                                            onClick = { viewModel.updateLabOrderStatus(order, status) },
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = viewModel.label("status_${status.lowercase()}"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    var showOfficialInvoiceDialog by remember { mutableStateOf(false) }
                                    OutlinedButton(
                                        onClick = { showOfficialInvoiceDialog = true },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(if (isPersian) "فاکتور رسمی" else "Invoice", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (showOfficialInvoiceDialog) {
                                        OfficialLabInvoiceDialog(
                                            order = order,
                                            viewModel = viewModel,
                                            isPersian = isPersian,
                                            onDismiss = { showOfficialInvoiceDialog = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Services Catalog Tab
            if (showForm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (isPersian) "تعریف خدمت و تعرفه قیمت جدید لابراتوار" else "Define New Lab Service", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = sLabName,
                            onValueChange = { sLabName = it },
                            label = { Text(if (isPersian) "نام لابراتوار" else "Lab Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sServiceName,
                            onValueChange = { sServiceName = it },
                            label = { Text(if (isPersian) "نام خدمت / نوع پروتز (مثلاً روکش زيركونيا)" else "Service / Prosthesis Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sPriceStr,
                            onValueChange = { sPriceStr = it },
                            label = { Text(if (isPersian) "قیمت خدمت (تومان)" else "Price (Toman)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sFrameDays,
                                onValueChange = { sFrameDays = it },
                                label = { Text(if (isPersian) "روزهای تحویل امتحان فریم" else "Frame Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sFinalDays,
                                onValueChange = { sFinalDays = it },
                                label = { Text(if (isPersian) "روزهای تحویل نهایی" else "Final Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = sNotes,
                            onValueChange = { sNotes = it },
                            label = { Text(if (isPersian) "توضیحات و مشخصات" else "Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                        Button(
                            onClick = {
                                val p = sPriceStr.toEnglishLong()
                                val fDays = sFrameDays.toEnglishLong().toInt().coerceAtLeast(1)
                                val fnDays = sFinalDays.toEnglishLong().toInt().coerceAtLeast(1)
                                if (sLabName.isNotBlank() && sServiceName.isNotBlank()) {
                                    viewModel.addLabService(sLabName, sServiceName, p, sNotes, fDays, fnDays)
                                    showForm = false
                                    sLabName = ""
                                    sServiceName = ""
                                    sPriceStr = ""
                                    sNotes = ""
                                    sFrameDays = "3"
                                    sFinalDays = "7"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPersian) "ثبت در تعرفه خدمات" else "Save Service")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(services) { serv ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = serv.labName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = serv.serviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "⏱️ تحویل امتحان فریم: ${serv.frameDeliveryDays} روز | تحویل نهایی: ${serv.finalDeliveryDays} روز",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (serv.notes.isNotBlank()) {
                                        Text(text = serv.notes, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text(text = "${formatAmount(serv.price)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { serviceToEdit = serv }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Service", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { serviceToDelete = serv }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Service", tint = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (serviceToEdit != null) {
        var eLab by remember { mutableStateOf(serviceToEdit!!.labName) }
        var eName by remember { mutableStateOf(serviceToEdit!!.serviceName) }
        var ePrice by remember { mutableStateOf(serviceToEdit!!.price.toString()) }
        var eNotes by remember { mutableStateOf(serviceToEdit!!.notes) }
        var eFrame by remember { mutableStateOf(serviceToEdit!!.frameDeliveryDays.toString()) }
        var eFinal by remember { mutableStateOf(serviceToEdit!!.finalDeliveryDays.toString()) }

        AlertDialog(
            onDismissRequest = { serviceToEdit = null },
            title = { Text(if (isPersian) "ویرایش تعرفه خدمت لابراتوار" else "Edit Lab Service", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = eLab, onValueChange = { eLab = it }, label = { Text(if (isPersian) "نام لابراتوار" else "Lab Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(if (isPersian) "نام خدمت" else "Service Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ePrice, onValueChange = { ePrice = it }, label = { Text(if (isPersian) "قیمت (تومان)" else "Price") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = eFrame, onValueChange = { eFrame = it }, label = { Text(if (isPersian) "روزهای فریم" else "Frame Days") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = eFinal, onValueChange = { eFinal = it }, label = { Text(if (isPersian) "روزهای نهایی" else "Final Days") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = eNotes, onValueChange = { eNotes = it }, label = { Text(if (isPersian) "توضیحات" else "Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = ePrice.toEnglishLong()
                        val fDays = eFrame.toEnglishLong().toInt().coerceAtLeast(1)
                        val fnDays = eFinal.toEnglishLong().toInt().coerceAtLeast(1)
                        if (eLab.isNotBlank() && eName.isNotBlank()) {
                            viewModel.updateLabService(
                                serviceToEdit!!.copy(
                                    labName = eLab,
                                    serviceName = eName,
                                    price = price,
                                    notes = eNotes,
                                    frameDeliveryDays = fDays,
                                    finalDeliveryDays = fnDays
                                )
                            )
                            serviceToEdit = null
                        }
                    }
                ) {
                    Text(if (isPersian) "ذخیره" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { serviceToEdit = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    if (serviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { serviceToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف خدمت" else "Confirm Service Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف خدمت «${serviceToDelete!!.serviceName}» متعلق به لابراتوار «${serviceToDelete!!.labName}» اطمینان دارید؟"
                    else "Are you sure you want to delete service ${serviceToDelete!!.serviceName}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLabService(serviceToDelete!!)
                        serviceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف خدمت" else "Delete Service", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { serviceToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (labOrderToDelete != null) {
        AlertDialog(
            onDismissRequest = { labOrderToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف سفارش" else "Confirm Order Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف سفارش «${labOrderToDelete!!.prosthesisType}» برای بیمار «${labOrderToDelete!!.patientName}» اطمینان دارید؟"
                    else "Are you sure you want to delete lab order for ${labOrderToDelete!!.patientName}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLabOrder(labOrderToDelete!!)
                        labOrderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف سفارش" else "Delete Order", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labOrderToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

// ==========================================
// SAYADI BARCODE SCANNER DIALOG (CAMERA & ML KIT)
// ==========================================
@OptIn(ExperimentalGetImage::class)
@Composable
fun RealSayadiBarcodeScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var manualInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "اسکنر بارکد صیادی (دوربین)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!hasCameraPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "جهت اسکن مستقیم بارکد چک صیادی، مجوز استفاده از دوربین لازم است.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            ) {
                                Text("اعطای دسترسی به دوربین")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val barcodeScanner = BarcodeScanning.getClient()
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        val raw = barcode.rawValue ?: barcode.displayValue ?: ""
                                                        val digits = raw.filter { it.isDigit() }
                                                        val match = Regex("\\d{16}").find(digits)?.value ?: (if (digits.length == 16) digits else null)
                                                        if (match != null) {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onScanned(match)
                                                            onDismiss()
                                                            break
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Green reticle overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(120.dp)
                                .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                        )

                        Text(
                            text = "بارکد مربعی یا میله‌ای چک صیادی را داخل کادر قرار دهید",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) manualInput = it },
                        label = { Text("ورود دستی شناسه ۱۶ رقمی", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            if (manualInput.length == 16) {
                                onScanned(manualInput)
                                onDismiss()
                            } else {
                                errorMessage = "شناسه صیاد باید دقیقاً ۱۶ رقم باشد"
                            }
                        },
                        enabled = manualInput.length == 16
                    ) {
                        Text("ثبت", fontSize = 11.sp)
                    }
                }

                TextButton(
                    onClick = {
                        val sample16Digit = "1009" + (1000000000L..9999999999L).random().toString()
                        onScanned(sample16Digit)
                        onDismiss()
                    }
                ) {
                    Text("💡 تولید شناسه صیادی نمونه (جهت تست)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

// ==========================================
// SAYADI CHEQUES MANAGER SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChequesManagerScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    onBack: () -> Unit
) {
    val allPayments by viewModel.allPayments.collectAsState()
    val allPatients by viewModel.patients.collectAsState()
    val currency = viewModel.label("currency")

    val cheques = remember(allPayments) {
        allPayments.filter { it.type == "CHEQUE" }
    }

    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    var chequeToChangeStatus by remember { mutableStateOf<Payment?>(null) }
    var chequeToTransfer by remember { mutableStateOf<Payment?>(null) }
    var chequeToEdit by remember { mutableStateOf<Payment?>(null) }
    var chequeToDelete by remember { mutableStateOf<Payment?>(null) }
    var showAddChequeDialog by remember { mutableStateOf(false) }
    var fullPhotoUri by remember { mutableStateOf<String?>(null) }

    val filteredCheques = remember(cheques, selectedStatusFilter, searchQuery, allPatients) {
        cheques.filter { cheque ->
            val statusMatches = when (selectedStatusFilter) {
                "PENDING" -> cheque.chequeStatus == "PENDING" || (!cheque.isCleared && cheque.chequeStatus != "BOUNCED" && cheque.chequeStatus != "TRANSFERRED")
                "CLEARED" -> cheque.chequeStatus == "CLEARED" || (cheque.isCleared && cheque.chequeStatus != "TRANSFERRED")
                "BOUNCED" -> cheque.chequeStatus == "BOUNCED"
                "TRANSFERRED" -> cheque.chequeStatus == "TRANSFERRED"
                else -> true
            }

            val patientName = allPatients.find { it.id == cheque.patientId }?.name ?: ""
            val searchMatches = searchQuery.isBlank() ||
                (cheque.sayadiId?.contains(searchQuery, ignoreCase = true) == true) ||
                (cheque.chequeBank?.contains(searchQuery, ignoreCase = true) == true) ||
                (cheque.chequeDrawer?.contains(searchQuery, ignoreCase = true) == true) ||
                (patientName.contains(searchQuery, ignoreCase = true))

            statusMatches && searchMatches
        }
    }

    // Stats calculations
    val totalCount = cheques.size
    val totalSum = cheques.sumOf { it.amount }
    val pendingSum = cheques.filter { it.chequeStatus == "PENDING" || (!it.isCleared && it.chequeStatus != "BOUNCED" && it.chequeStatus != "TRANSFERRED") }.sumOf { it.amount }
    val clearedSum = cheques.filter { it.chequeStatus == "CLEARED" || (it.isCleared && it.chequeStatus != "TRANSFERRED") }.sumOf { it.amount }
    val bouncedSum = cheques.filter { it.chequeStatus == "BOUNCED" }.sumOf { it.amount }
    val transferredSum = cheques.filter { it.chequeStatus == "TRANSFERRED" }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isPersian) "مدیریت و بایگانی چک‌های صیادی" else "Sayad Cheques Ledger",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddChequeDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "ثبت چک جدید" else "New Cheque", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // KPI Stats Horizontal Summary Cards
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📊 خلاصه وضعیت کلی چک‌های دریافتی ($totalCount فقره)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "مجموع: ${formatAmount(totalSum)} $currency",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                title = "در جریان وصول",
                                amount = pendingSum,
                                currency = currency,
                                color = Color(0xFF0288D1),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "پاس شده",
                                amount = clearedSum,
                                currency = currency,
                                color = Color(0xFF22C55E),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "برگشتی",
                                amount = bouncedSum,
                                currency = currency,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "واگذار شده",
                                amount = transferredSum,
                                currency = currency,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("جستجو براساس شناسه صیاد ۱۶ رقمی، بانک، صادرکننده یا بیمار...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "همه چک‌ها ($totalCount)",
                            "PENDING" to "در جریان وصول",
                            "CLEARED" to "پاس شده",
                            "BOUNCED" to "برگشتی",
                            "TRANSFERRED" to "واگذار شده"
                        ).forEach { (code, label) ->
                            val isSel = selectedStatusFilter == code
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusFilter = code },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // List of Cheques
            if (filteredCheques.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Text("هیچ چکی با مشخصات یا فیلتر انتخابی یافت نشد.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                items(filteredCheques) { cheque ->
                    val patient = allPatients.find { it.id == cheque.patientId }
                    ChequeCardItem(
                        cheque = cheque,
                        patientName = patient?.name ?: "بیمار نامشخص",
                        currency = currency,
                        viewModel = viewModel,
                        onChangeStatus = { chequeToChangeStatus = cheque },
                        onTransfer = { chequeToTransfer = cheque },
                        onEdit = { chequeToEdit = cheque },
                        onDelete = { chequeToDelete = cheque },
                        onViewPhoto = { photo -> fullPhotoUri = photo }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // 1. Change Status Dialog
    chequeToChangeStatus?.let { cheque ->
        ChequeStatusChangeDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToChangeStatus = null }
        )
    }

    // 2. Transfer / Endorse Dialog
    chequeToTransfer?.let { cheque ->
        ChequeTransferDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToTransfer = null }
        )
    }

    // 3. Edit Cheque Dialog
    chequeToEdit?.let { cheque ->
        ChequeEditDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToEdit = null }
        )
    }

    // 4. Delete Cheque Dialog
    chequeToDelete?.let { cheque ->
        AlertDialog(
            onDismissRequest = { chequeToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("حذف چک صیادی", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("آیا از حذف چک صیادی به مبلغ ${formatAmount(cheque.amount)} $currency با شناسه ${cheque.sayadiId ?: "نامشخص"} اطمینان دارید؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(cheque)
                        chequeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف چک")
                }
            },
            dismissButton = {
                TextButton(onClick = { chequeToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // 5. Add Cheque Dialog
    if (showAddChequeDialog) {
        if (allPatients.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showAddChequeDialog = false },
                title = { Text("ثبت دریافتی و چک جدید") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                        AddPaymentForm(
                            patientId = allPatients.first().id,
                            viewModel = viewModel,
                            onSaved = { showAddChequeDialog = false }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddChequeDialog = false }) {
                        Text("بستن")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddChequeDialog = false },
                title = { Text("بیماری یافت نشد") },
                text = { Text("لطفا ابتدا یک بیمار در سیستم ثبت کنید تا بتوانید برای وی چک صیادی جدید ثبت نمایید.") },
                confirmButton = {
                    Button(onClick = { showAddChequeDialog = false }) { Text("متوجه شدم") }
                }
            )
        }
    }

    // Photo Fullscreen Dialog
    fullPhotoUri?.let { uri ->
        Dialog(onDismissRequest = { fullPhotoUri = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تصویر کامل چک صیادی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { fullPhotoUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    SmartImage(
                        model = uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    amount: Long,
    currency: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text("${formatAmount(amount)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(currency, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ChequeCardItem(
    cheque: Payment,
    patientName: String,
    currency: String,
    viewModel: DentalViewModel,
    onChangeStatus: () -> Unit,
    onTransfer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val status = cheque.chequeStatus
    val (statusLabel, badgeBg, badgeText) = when (status) {
        "CLEARED" -> Triple("پاس شده / وصول شده", Color(0xFF22C55E), Color.White)
        "BOUNCED" -> Triple("برگشتی / نقد نشده", Color(0xFFEF4444), Color.White)
        "TRANSFERRED" -> Triple("واگذار شده به: ${cheque.transferredTo ?: "ثالث"}", Color(0xFFF59E0B), Color.Black)
        else -> Triple("در جریان وصول", Color(0xFF0288D1), Color.White)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(patientName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                color = Color(0xFF6366F1).copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                        Text("شناسه ۱۶ رقمی صیادی:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                    }
                    Text(
                        text = cheque.sayadiId ?: "ثبت نشده",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4338CA),
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("مبلغ چک:", fontSize = 10.sp, color = Color.Gray)
                    Text("${formatAmount(cheque.amount)} $currency", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("تاریخ سررسید:", fontSize = 10.sp, color = Color.Gray)
                    cheque.chequeDueDate?.let { due ->
                        Text(viewModel.formatDate(due), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (due < System.currentTimeMillis() && cheque.chequeStatus == "PENDING") Color.Red else MaterialTheme.colorScheme.onSurface)
                    } ?: Text("-", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!cheque.chequeBank.isNullOrBlank()) {
                    Text("بانک: ${cheque.chequeBank}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!cheque.chequeDrawer.isNullOrBlank()) {
                    Text("صادرکننده: ${cheque.chequeDrawer}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!cheque.chequePhotoBase64.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewPhoto(cheque.chequePhotoBase64) }
                ) {
                    SmartImage(
                        model = cheque.chequePhotoBase64,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("🔍 مشاهده تصویر چک", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeStatus,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PublishedWithChanges, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("تغییر وضعیت", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onTransfer,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("انتقال چک", fontSize = 10.sp)
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChequeStatusChangeDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(cheque.chequeStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعیین وضعیت نهایی چک صیادی", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("شناسه صیاد: ${cheque.sayadiId ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("مبلغ: ${formatAmount(cheque.amount)} ${viewModel.label("currency")}", fontSize = 12.sp)

                Text("لطفاً وضعیت نهایی چک را انتخاب کنید:", fontSize = 11.sp, color = Color.Gray)

                listOf(
                    "PENDING" to "در جریان وصول (ایام سررسید)",
                    "CLEARED" to "پاس شده / وصول و واریز شد",
                    "BOUNCED" to "برگشتی / نقد نشده"
                ).forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedStatus = code }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = selectedStatus == code, onClick = { selectedStatus = code })
                        Text(label, fontSize = 12.sp, fontWeight = if (selectedStatus == code) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = cheque.copy(
                        chequeStatus = selectedStatus,
                        isCleared = selectedStatus == "CLEARED" || selectedStatus == "TRANSFERRED"
                    )
                    viewModel.updatePayment(updated)
                    onDismiss()
                }
            ) {
                Text("ذخیره تغییر وضعیت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ChequeTransferDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val vendors by viewModel.vendors.collectAsState()
    val labOrders by viewModel.labOrders.collectAsState()
    val personalSubAccounts by viewModel.personalSubAccounts.collectAsState()
    val isPersian = viewModel.isPersian.collectAsState().value

    val distinctLabs = remember(labOrders) { labOrders.map { it.labName }.distinct().filter { it.isNotBlank() } }
    val expenseCategories = listOf("اجاره مطب", "تجهیزات و مواد دندانپزشکی", "قبوض و مراقبت", "حقوق کادر مطب", "سایر هزینه‌های جاری")

    var destType by remember { mutableStateOf("VENDOR") } // VENDOR, LAB, EXPENSE, PERSONAL, OTHER
    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var selectedLabName by remember { mutableStateOf("") }
    var selectedExpenseCat by remember { mutableStateOf(expenseCategories.first()) }
    var selectedPersonalAccount by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf(cheque.transferredTo ?: "") }
    var transferNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتقال / واگذاری چک صیادی و اعمال بدهکار-بستانکار", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("شناسه صیاد: ${cheque.sayadiId ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("مبلغ چک: ${formatAmount(cheque.amount)} ${viewModel.label("currency")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Text("نوع حساب مقصد جهت فراخوانی و اعمال حسابداری:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "VENDOR" to "فروشندگان و تامین‌کنندگان",
                        "LAB" to "حساب لابراتوار",
                        "EXPENSE" to "هزینه جاری مطب",
                        "PERSONAL" to "سرفصل حساب شخصی",
                        "OTHER" to "سایر / شخص ثالث"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = destType == code,
                            onClick = { destType = code },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                when (destType) {
                    "VENDOR" -> {
                        if (vendors.isEmpty()) {
                            Text("هیچ تامین‌کننده‌ای ثبت نشده است.", fontSize = 11.sp, color = Color.Gray)
                        } else {
                            Text("انتخاب فروشنده/تامین‌کننده مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                vendors.forEach { v ->
                                    FilterChip(
                                        selected = selectedVendor?.id == v.id,
                                        onClick = {
                                            selectedVendor = v
                                            recipientName = "${v.name} (${v.company})"
                                        },
                                        label = { Text("${v.name} - ${v.company}", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    "LAB" -> {
                        Text("انتخاب یا درج نام لابراتوار مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (distinctLabs.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                distinctLabs.forEach { lab ->
                                    FilterChip(
                                        selected = selectedLabName == lab,
                                        onClick = {
                                            selectedLabName = lab
                                            recipientName = "لابراتوار $lab"
                                        },
                                        label = { Text(lab, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            label = { Text("نام دقیق لابراتوار") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "EXPENSE" -> {
                        Text("سرفصل هزینه جاری مطب:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            expenseCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedExpenseCat == cat,
                                    onClick = {
                                        selectedExpenseCat = cat
                                        recipientName = "هزینه مطب: $cat"
                                    },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    "PERSONAL" -> {
                        Text("سرفصل هزینه شخصی مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (personalSubAccounts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                personalSubAccounts.forEach { sub ->
                                    FilterChip(
                                        selected = selectedPersonalAccount == sub.name,
                                        onClick = {
                                            selectedPersonalAccount = sub.name
                                            recipientName = "حساب شخصی: ${sub.name}"
                                        },
                                        label = { Text(sub.name, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            label = { Text("نام دریافت‌کننده چک", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                OutlinedTextField(
                    value = transferNote,
                    onValueChange = { transferNote = it },
                    label = { Text("توضیحات بابت انتقال (اختیاری)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalRecipient = recipientName.ifBlank { "واگذاری چک" }
                    val updated = cheque.copy(
                        chequeStatus = "TRANSFERRED",
                        transferredTo = finalRecipient.trim(),
                        transferDate = System.currentTimeMillis(),
                        isCleared = true
                    )
                    viewModel.updatePayment(updated)

                    // Execute accounting debit/credit for destination
                    when (destType) {
                        "VENDOR" -> {
                            selectedVendor?.let { v ->
                                viewModel.updateVendor(v.copy(totalPaid = v.totalPaid + cheque.amount))
                            }
                        }
                        "EXPENSE" -> {
                            viewModel.addExpense(
                                category = selectedExpenseCat,
                                amount = cheque.amount,
                                desc = "انتقال چک صیاد ${cheque.sayadiId ?: cheque.chequeNumber ?: ""} - $transferNote"
                            )
                        }
                        "PERSONAL" -> {
                            val targetAccount = selectedPersonalAccount.ifBlank { "هزینه‌های شخصی" }
                            viewModel.addPersonalTx(
                                type = "EXPENSE",
                                amount = cheque.amount,
                                desc = "انتقال چک صیاد ${cheque.sayadiId ?: cheque.chequeNumber ?: ""} به حساب $targetAccount - $transferNote",
                                paymentMethod = "CHEQUE_TRANSFER",
                                subAccountName = targetAccount,
                                chequeNumber = cheque.chequeNumber
                            )
                        }
                    }
                    onDismiss()
                },
                enabled = recipientName.isNotBlank() || selectedVendor != null || selectedLabName.isNotBlank() || destType == "EXPENSE" || destType == "PERSONAL"
            ) {
                Text("ثبت انتقال و اعمال بدهکار/بستانکار")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ChequeEditDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf(cheque.amount.toString()) }
    var sayadiId by remember { mutableStateOf(cheque.sayadiId ?: "") }
    var bankName by remember { mutableStateOf(cheque.chequeBank ?: "") }
    var drawerName by remember { mutableStateOf(cheque.chequeDrawer ?: "") }
    var chequeNum by remember { mutableStateOf(cheque.chequeNumber ?: "") }
    var photoUri by remember { mutableStateOf(cheque.chequePhotoBase64) }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        RealSayadiBarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { scannedId ->
                sayadiId = scannedId
                showScanner = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش مشخصات چک صیادی", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountStr = it },
                    label = { Text("مبلغ چک (${viewModel.label("currency")})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sayadiId,
                        onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) sayadiId = it },
                        label = { Text("شناسه ۱۶ رقمی صیادی", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("نام بانک صادرکننده") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = drawerName,
                    onValueChange = { drawerName = it },
                    label = { Text("نام صادرکننده چک") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = chequeNum,
                    onValueChange = { chequeNum = it },
                    label = { Text("شماره سریال چک") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAmount = amountStr.toLongOrNull() ?: cheque.amount
                    val updated = cheque.copy(
                        amount = newAmount,
                        sayadiId = sayadiId.ifBlank { null },
                        chequeBank = bankName.ifBlank { null },
                        chequeDrawer = drawerName.ifBlank { null },
                        chequeNumber = chequeNum.ifBlank { null },
                        chequePhotoBase64 = photoUri
                    )
                    viewModel.updatePayment(updated)
                    onDismiss()
                }
            ) {
                Text("ذخیره تغییرات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

// ==========================================
// 6. EQUIPMENT MAINTENANCE
// ==========================================
@Composable
fun MaintenanceScreen(viewModel: DentalViewModel) {
    val items by viewModel.maintenanceList.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var nextCheckDaysStr by remember { mutableStateOf("180") }

    val currency = viewModel.label("currency")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("maintenance"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_maintenance"))
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(viewModel.label("equipment_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("maintenance_desc")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = costStr, onValueChange = { costStr = it }, label = { Text(if (viewModel.isPersian.collectAsState().value) "هزینه واحد (تومان)" else "Unit Cost (Toman)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nextCheckDaysStr, onValueChange = { nextCheckDaysStr = it }, label = { Text(if (viewModel.isPersian.collectAsState().value) "یادآور سرویس بعدی (چند روز دیگر)؟" else "Next service due in how many days?") }, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            val cost = costStr.toEnglishLong()
                            val days = nextCheckDaysStr.toEnglishLongOrNull()
                            val nextDue = if (days != null) System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L) else null
                            if (name.isNotEmpty()) {
                                viewModel.addMaintenance(name, desc, cost, System.currentTimeMillis(), nextDue)
                                showForm = false
                                name = ""
                                desc = ""
                                costStr = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = record.equipmentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = record.description, fontSize = 12.sp, color = Color.Gray)
                                Text(text = "${viewModel.label("cost")}: ${formatAmount(record.cost)} $currency", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(text = viewModel.formatDate(record.date), fontSize = 11.sp, color = Color.Gray)
                                record.nextServiceDate?.let {
                                    Text(text = "${viewModel.label("next_service")}: ${viewModel.formatDate(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteMaintenance(record) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesScreen(viewModel: DentalViewModel) {
    val expenses by viewModel.clinicExpenses.collectAsState()
    val labOrdersForInvoice by viewModel.labOrders.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var invoiceOrderToShow by remember { mutableStateOf<com.example.data.LabOrder?>(null) }
    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsState().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("expenses"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(viewModel.label("add_expense"))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(expenses) { exp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = viewModel.label(exp.category.lowercase()), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (exp.description.isNotEmpty()) {
                                Text(text = exp.description, fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(text = viewModel.formatDate(exp.date), fontSize = 11.sp, color = Color.Gray)
                            // اگر این هزینه از یک سفارش لابراتوار ثبت شده باشد، امکان مشاهده فاکتور رسمی همان سفارش فراهم شود
                            val linkedOrder = exp.labOrderId?.let { oid -> labOrdersForInvoice.find { it.id == oid } }
                            if (linkedOrder != null) {
                                TextButton(
                                    onClick = { invoiceOrderToShow = linkedOrder },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPersian) "مشاهده فاکتور رسمی" else "View Official Invoice", fontSize = 10.sp)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${formatAmount(exp.amount)} $currency", fontWeight = FontWeight.Bold, color = DebtColor)
                            IconButton(onClick = { viewModel.deleteExpense(exp) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddExpenseDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }

    invoiceOrderToShow?.let { order ->
        OfficialLabInvoiceDialog(
            order = order,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { invoiceOrderToShow = null }
        )
    }
}

@Composable
fun PersonalScreen(viewModel: DentalViewModel) {
    val txList by viewModel.personalTransactions.collectAsState()
    val subAccounts by viewModel.personalSubAccounts.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()

    var showForm by remember { mutableStateOf(false) }

    // Form inputs
    var type by remember { mutableStateOf("EXPENSE") } // EXPENSE, DRAW, INCOME
    var selectedMethod by remember { mutableStateOf("CARD_TRANSFER") } // CARD_TRANSFER, CASH, CHEQUE_TRANSFER, POS
    var selectedSubAccount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var amountStr by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var chequeNum by remember { mutableStateOf("") }
    var selectedChequeForPersonal by remember { mutableStateOf<Payment?>(null) }

    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsState().value

    val totalExpenses = txList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalDraw = txList.filter { it.type == "DRAW" }.sumOf { it.amount }
    val totalIncome = txList.filter { it.type == "INCOME" }.sumOf { it.amount }

    // Aggregated Sub-Accounts Summary
    val aggregatedSubAccounts = remember(txList) {
        txList.filter { it.type == "EXPENSE" }
            .groupBy { it.subAccountName.ifBlank { if (isPersian) "سایر هزینه‌های شخصی" else "Other Personal" } }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("personal"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_personal_tx"))
            }
        }

        var showManageSubAccounts by remember { mutableStateOf(false) }
        // (توجه: «سرفصل‌های شخصی» و «درآمد ثابت ماهانه» به منوی تنظیمات اختصاصی —
        // لمس ۲ثانیه‌ای چرخ‌دنده بالای صفحه — منتقل شدند)
        if (showManageSubAccounts) {
            ManageSubAccountsDialog(viewModel = viewModel, isPersian = isPersian, onDismiss = { showManageSubAccounts = false })
        }

        // Summary Cards Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = if (isPersian) "مجموع هزینه‌های شخصی" else "Total Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(text = "${formatAmount(totalExpenses)} $currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = if (isPersian) "مجموع برداشت از مطب" else "Total Clinic Draw", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = "${formatAmount(totalDraw)} $currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = viewModel.label("transaction_type"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "EXPENSE" to (if (isPersian) "هزینه شخصی" else "Personal Expense"),
                            "DRAW" to (if (isPersian) "برداشت از مطب" else "Clinic Draw"),
                            "INCOME" to (if (isPersian) "درآمد متفرقه" else "Other Income")
                        ).forEach { (txType, labelText) ->
                            ElevatedFilterChip(
                                selected = type == txType,
                                onClick = { type = txType },
                                label = { Text(labelText, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (type == "EXPENSE") {
                        Text(text = if (isPersian) "سرفصل هزینه شخصی:" else "Personal Sub-Account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickSelectNames = subAccounts.filter { it.isQuickSelect }.map { it.name }
                            val otherNames = (subAccounts.map { it.name } + listOf(
                                "اجاره مسکن و شارژ", "خریدهای روزانه و سوپرمارکت", "هزینه‌های درمانی و دارو",
                                "قبوض آب، برق، گاز و تلفن", "تفریح، سفر و رستوران", "خودرو و حمل و نقل",
                                "آموزش و شهریه فرزندان", "اقساط وام و تسهیلات"
                            )).distinct().filterNot { it in quickSelectNames }
                            val allSubAccountNames = quickSelectNames + otherNames

                            allSubAccountNames.forEach { subName ->
                                val isQuick = subName in quickSelectNames
                                FilterChip(
                                    selected = selectedSubAccount == subName,
                                    onClick = { selectedSubAccount = subName },
                                    label = { Text(subName, fontSize = 11.sp) },
                                    leadingIcon = if (isQuick) {
                                        { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    Text(text = if (isPersian) "روش پرداخت:" else "Payment Method:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "CARD_TRANSFER" to (if (isPersian) "کارت به کارت" else "Card Transfer"),
                            "CASH" to (if (isPersian) "نقدی" else "Cash"),
                            "CHEQUE_TRANSFER" to (if (isPersian) "انتقال چک" else "Cheque Transfer"),
                            "POS" to (if (isPersian) "پوز" else "POS")
                        ).forEach { (mCode, mLabel) ->
                            FilterChip(
                                selected = selectedMethod == mCode,
                                onClick = { selectedMethod = mCode },
                                label = { Text(mLabel, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedMethod == "CARD_TRANSFER" || selectedMethod == "POS") {
                        if (bankAccounts.isNotEmpty()) {
                            Text(text = if (isPersian) "حساب/کارت بانکی مبدا:" else "Source Bank Card:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bankAccounts.forEach { acc ->
                                    FilterChip(
                                        selected = selectedAccountId == acc.id,
                                        onClick = { selectedAccountId = acc.id },
                                        label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    } else if (selectedMethod == "CHEQUE_TRANSFER") {
                        val untransferredChequesForPersonal = remember(allPayments) {
                            allPayments.filter { it.type == "CHEQUE" && it.chequeStatus != "TRANSFERRED" }
                        }
                        if (untransferredChequesForPersonal.isNotEmpty()) {
                            Text(if (isPersian) "انتخاب از چک‌های دریافتی ثبت‌شده:" else "Select from received cheques:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            var chequeDropdownExpandedPersonal by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = chequeDropdownExpandedPersonal,
                                onExpandedChange = { chequeDropdownExpandedPersonal = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedChequeForPersonal?.let { c -> "${c.chequeBank ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}" } ?: (if (isPersian) "— بدون انتخاب —" else "— None —"),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chequeDropdownExpandedPersonal) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = chequeDropdownExpandedPersonal,
                                    onDismissRequest = { chequeDropdownExpandedPersonal = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isPersian) "— بدون چک (وارد کردن دستی) —" else "— None (manual entry) —") },
                                        onClick = {
                                            selectedChequeForPersonal = null
                                            chequeDropdownExpandedPersonal = false
                                        }
                                    )
                                    untransferredChequesForPersonal.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text("${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}", fontSize = 12.sp) },
                                            onClick = {
                                                selectedChequeForPersonal = c
                                                chequeNum = c.chequeNumber ?: ""
                                                amountStr = c.amount.toString()
                                                desc = if (isPersian) "انتقال چک شماره ${c.chequeNumber ?: ""} (بانک ${c.chequeBank ?: ""}، صادرکننده ${c.chequeDrawer ?: ""})" else "Cheque transfer #${c.chequeNumber ?: ""} (${c.chequeBank ?: ""})"
                                                chequeDropdownExpandedPersonal = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = chequeNum,
                            onValueChange = { chequeNum = it; selectedChequeForPersonal = null },
                            label = { Text(if (isPersian) "شماره/شناسه چک صیادی" else "Cheque Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = selectedChequeForPersonal != null
                        )
                    }

                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(viewModel.label("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("personal_desc")) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)

                    Button(
                        onClick = {
                            val amt = amountStr.toEnglishLong()
                            if (amt >= 0) {
                                val subName = selectedSubAccount.ifBlank { if (isPersian) "هزینه‌های شخصی" else "General Personal" }
                                val cheque = selectedChequeForPersonal
                                if (cheque != null) {
                                    viewModel.transferChequeToPersonal(cheque, recipientName = desc.ifBlank { subName }, subAccountName = subName)
                                } else {
                                    viewModel.addPersonalTx(
                                        type = type,
                                        amount = amt,
                                        desc = desc.trim(),
                                        paymentMethod = selectedMethod,
                                        subAccountName = subName,
                                        chequeNumber = chequeNum.ifBlank { null }
                                    )
                                }
                                // Debit/credit source bank card if applicable
                                if ((selectedMethod == "CARD_TRANSFER" || selectedMethod == "POS") && selectedAccountId != null) {
                                    val acc = bankAccounts.find { it.id == selectedAccountId }
                                    if (acc != null) {
                                        viewModel.updateBankAccount(acc.copy(balance = acc.balance - amt))
                                    }
                                }
                                showForm = false
                                amountStr = ""
                                desc = ""
                                chequeNum = ""
                                selectedChequeForPersonal = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            // Display Aggregated Sub-Accounts Summary Header
            if (aggregatedSubAccounts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = if (isPersian) "📊 تجمیع سرفصل‌های هزینه شخصی:" else "📊 Aggregated Personal Expense Headings:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        aggregatedSubAccounts.forEach { (subName, totalAmt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = subName, fontSize = 12.sp)
                                Text(text = "${formatAmount(totalAmt)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(txList) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val titleStr = when (tx.type) {
                                    "DRAW" -> viewModel.label("dentist_draw")
                                    "INCOME" -> viewModel.label("personal_income")
                                    else -> tx.subAccountName.ifBlank { viewModel.label("personal_expense") }
                                }
                                Text(text = titleStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (tx.paymentMethod.isNotBlank()) {
                                    val mLabel = when (tx.paymentMethod) {
                                        "CARD_TRANSFER" -> if (isPersian) "کارت به کارت" else "Card Transfer"
                                        "CASH" -> if (isPersian) "نقدی" else "Cash"
                                        "CHEQUE_TRANSFER" -> if (isPersian) "انتقال چک" else "Cheque Transfer"
                                        "POS" -> if (isPersian) "پوز" else "POS"
                                        else -> tx.paymentMethod
                                    }
                                    Text(text = "روش: $mLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                if (tx.description.isNotEmpty()) {
                                    Text(text = tx.description, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(text = viewModel.formatDate(tx.date), fontSize = 11.sp, color = Color.Gray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val valColor = when (tx.type) {
                                    "DRAW" -> ClearColor
                                    "INCOME" -> ClearColor
                                    else -> DebtColor
                                }
                                val sign = if (tx.type == "EXPENSE") "-" else "+"
                                Text(text = "$sign${formatAmount(tx.amount)} $currency", fontWeight = FontWeight.Bold, color = valColor)
                                IconButton(onClick = { viewModel.deletePersonalTx(tx) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PERSONAL SUB-ACCOUNTS MANAGEMENT
// ==========================================
@Composable
fun ManageSubAccountsDialog(viewModel: DentalViewModel, isPersian: Boolean, onDismiss: () -> Unit) {
    val subAccounts by viewModel.personalSubAccounts.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("EXPENSE") }
    var subToDelete by remember { mutableStateOf<PersonalSubAccount?>(null) }
    var subToEdit by remember { mutableStateOf<PersonalSubAccount?>(null) }
    var editName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "مدیریت سرفصل‌های هزینه شخصی و درآمد متفرقه" else "Manage Personal Sub-Accounts", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isPersian) "افزودن سرفصل جدید:" else "Add new sub-account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "EXPENSE" to (if (isPersian) "هزینه" else "Expense"),
                        "INCOME" to (if (isPersian) "درآمد متفرقه" else "Other Income"),
                        "DRAW" to (if (isPersian) "برداشت" else "Draw")
                    ).forEach { (code, lbl) ->
                        FilterChip(
                            selected = newCategory == code,
                            onClick = { newCategory = code },
                            label = { Text(lbl, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(if (isPersian) "نام سرفصل" else "Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addPersonalSubAccount(newName.trim(), newCategory)
                            newName = ""
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = null) }
                }

                HorizontalDivider()
                Text(if (isPersian) "سرفصل‌های ثبت‌شده (تیک ستاره = نمایش در انتخاب سریع):" else "Existing sub-accounts (star = show in quick-select):", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                if (subAccounts.isEmpty()) {
                    Text(if (isPersian) "هیچ سرفصلی ثبت نشده است." else "No sub-accounts yet.", fontSize = 12.sp, color = Color.Gray)
                }
                subAccounts.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (subToEdit?.id == sub.id) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                if (editName.isNotBlank()) {
                                    viewModel.updatePersonalSubAccount(sub.copy(name = editName.trim()))
                                }
                                subToEdit = null
                            }) { Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF2E7D32)) }
                        } else {
                            Text(text = sub.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconToggleButton(
                                checked = sub.isQuickSelect,
                                onCheckedChange = { checked -> viewModel.updatePersonalSubAccount(sub.copy(isQuickSelect = checked)) }
                            ) {
                                Icon(
                                    if (sub.isQuickSelect) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Quick Select",
                                    tint = if (sub.isQuickSelect) Color(0xFFFFA000) else Color.Gray
                                )
                            }
                            IconButton(onClick = { subToEdit = sub; editName = sub.name }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { subToDelete = sub }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "بستن" else "Close") }
        }
    )

    if (subToDelete != null) {
        AlertDialog(
            onDismissRequest = { subToDelete = null },
            title = { Text(if (isPersian) "تایید حذف سرفصل" else "Confirm Delete", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف سرفصل «${subToDelete!!.name}» اطمینان دارید؟" else "Delete sub-account '${subToDelete!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePersonalSubAccount(subToDelete!!)
                        subToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { subToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }
}

// ==========================================
// FIXED MONTHLY INCOME MANAGEMENT
// ==========================================
@Composable
fun ManageFixedIncomesDialog(viewModel: DentalViewModel, isPersian: Boolean, onDismiss: () -> Unit) {
    val fixedIncomes by viewModel.fixedIncomes.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val currency = viewModel.label("currency")

    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dayStr by remember { mutableStateOf("1") }
    var selectedBankId by remember { mutableStateOf<Long?>(null) }
    var subAccountName by remember { mutableStateOf("درآمد ثابت") }
    var incomeToDelete by remember { mutableStateOf<FixedIncome?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "درآمدهای ثابت ماهانه (واریز خودکار)" else "Fixed Monthly Incomes (Auto-Deposit)", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isPersian) "ℹ️ در تاریخ سررسید هر ماه، مبلغ به‌صورت خودکار به‌عنوان درآمد به حساب بانکی انتخابی واریز و ثبت می‌شود."
                        else "ℹ️ On the due date each month, the amount is automatically deposited and recorded as income to the selected bank account.",
                        fontSize = 11.sp, modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(if (isPersian) "عنوان درآمد" else "Income Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(if (isPersian) "مبلغ (تومان)" else "Amount") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = { v -> if (v.isEmpty() || (v.toIntOrNull() != null && v.toInt() in 1..31)) dayStr = v },
                        label = { Text(if (isPersian) "روز سررسید (۱ تا ۳۱)" else "Due Day (1-31)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if (bankAccounts.isNotEmpty()) {
                    Text(if (isPersian) "حساب بانکی مقصد:" else "Destination Bank Account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        bankAccounts.forEach { acc ->
                            FilterChip(
                                selected = selectedBankId == acc.id,
                                onClick = { selectedBankId = acc.id },
                                label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = subAccountName, onValueChange = { subAccountName = it }, label = { Text(if (isPersian) "سرفصل درآمد" else "Income Sub-Account") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Button(
                    onClick = {
                        val amt = amountStr.toEnglishLong()
                        val day = dayStr.toIntOrNull() ?: 1
                        if (title.isNotBlank() && amt > 0) {
                            val bankName = bankAccounts.find { it.id == selectedBankId }?.bankName ?: ""
                            viewModel.addFixedIncome(
                                title = title.trim(),
                                amount = amt,
                                dayOfMonth = day,
                                bankAccountId = selectedBankId,
                                bankAccountName = bankName,
                                subAccountName = subAccountName.ifBlank { "درآمد ثابت" }
                            )
                            title = ""; amountStr = ""; dayStr = "1"; selectedBankId = null; subAccountName = "درآمد ثابت"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "افزودن درآمد ثابت" else "Add Fixed Income")
                }

                HorizontalDivider()
                if (fixedIncomes.isEmpty()) {
                    Text(if (isPersian) "هیچ درآمد ثابتی ثبت نشده است." else "No fixed incomes yet.", fontSize = 12.sp, color = Color.Gray)
                }
                fixedIncomes.forEach { income ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = income.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = (if (isPersian) "روز ${income.dayOfMonth} هر ماه" else "Day ${income.dayOfMonth} of each month") +
                                        (if (income.bankAccountName.isNotBlank()) " • ${income.bankAccountName}" else ""),
                                    fontSize = 10.sp, color = Color.Gray
                                )
                            }
                            Text(text = "${formatAmount(income.amount)} $currency", fontWeight = FontWeight.Bold, color = ClearColor, fontSize = 12.sp)
                            IconButton(onClick = { incomeToDelete = income }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "بستن" else "Close") }
        }
    )

    if (incomeToDelete != null) {
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            title = { Text(if (isPersian) "تایید حذف" else "Confirm Delete", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف درآمد ثابت «${incomeToDelete!!.title}» اطمینان دارید؟" else "Delete fixed income '${incomeToDelete!!.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFixedIncome(incomeToDelete!!)
                        incomeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }
}

// ==========================================
// DIALOGS & SHARED COMPONENT UTILS
// ==========================================

@Composable
fun AddPatientDialog(viewModel: DentalViewModel, onDismiss: () -> Unit) {
    val isPersian by viewModel.isPersian.collectAsState()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") } // Optional now!
    var preferredMessenger by remember { mutableStateOf("واتساپ") }
    var gender by remember { mutableStateOf(if (isPersian) "آقای" else "Mr.") }
    var ageStr by remember { mutableStateOf("") }
    var birthDateStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val nameError = if (name.isBlank()) "نام بیمار الزامی است" else null
    val phoneError = if (!com.example.util.isValidIranPhone(phone)) "شماره تلفن معتبر نیست" else null
    val validationError = nameError ?: phoneError

    // موارد هشدار پزشکی و خوداظهاری سلامت اکنون یک فهرست واحد و یکپارچه است (به‌جای دو بخش جدا و ناهماهنگ قبلی)
    var selectedAlerts by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isPersian) "پذیرش بیمار جدید و تشکیل پرونده" else "Patient Admission & Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isPersian) "ثبت مشخصات، جنسیت، پیام‌رسان پیش‌فرض و فرم خوداظهاری" else "Register info, gender, messenger & self-declaration",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Patient Name (Required)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (isPersian) "نام و نام خانوادگی بیمار *" else "Patient Full Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = attemptedSubmit && nameError != null,
                        supportingText = {
                            if (attemptedSubmit && nameError != null) {
                                Text(nameError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )

                    // Phone Number (Optional)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(if (isPersian) "شماره تماس (اختیاری)" else "Phone Number (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(if (isPersian) "اختیاری - در صورت عدم وجود خالی بگذارید" else "Optional") },
                        isError = attemptedSubmit && phoneError != null,
                        supportingText = {
                            if (attemptedSubmit && phoneError != null) {
                                Text(phoneError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )

                    // Preferred Messenger Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "پیام‌رسان پیش‌فرض بیمار جهت ارسال پیام و مدارک:" else "Preferred Messenger:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                            messengers.forEach { m ->
                                FilterChip(
                                    selected = preferredMessenger == m,
                                    onClick = { preferredMessenger = m },
                                    label = { Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Age & Birth Date with automatic bidirectional calculation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { input ->
                                ageStr = input
                                val parsedAge = input.toEnglishIntOrNull()
                                if (parsedAge != null && parsedAge > 0 && parsedAge < 120) {
                                    birthDateStr = "${1405 - parsedAge}"
                                }
                            },
                            label = { Text(if (isPersian) "سن بیمار" else "Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("مثلاً ۲۸") }
                        )

                        OutlinedTextField(
                            value = birthDateStr,
                            onValueChange = { input ->
                                birthDateStr = input
                                val yearOnly = input.takeWhile { it.isDigit() }.toEnglishIntOrNull()
                                if (yearOnly != null && yearOnly in 1300..1405) {
                                    ageStr = "${1405 - yearOnly}"
                                }
                            },
                            label = { Text(if (isPersian) "تاریخ / سال تولد" else "Birth Date / Year") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            placeholder = { Text("۱۳۷۵") }
                        )
                    }

                    // Gender Selector (جنسیت)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "جنسیت بیمار:" else "Patient Gender:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val genderOptions = if (isPersian) listOf("آقای", "خانم") else listOf("Mr.", "Ms.")
                            genderOptions.forEach { g ->
                                val isMale = g == "آقای" || g == "Mr."
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            if (isMale) Icons.Default.Man else Icons.Default.Woman,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // موارد هشدار پزشکی و خوداظهاری سلامت (ادغام‌شده - قبلاً این بخش و «فرم خوداظهاری» دو بخش جدا و ناهماهنگ بودند)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "📋 موارد هشدار، بیماری‌های خاص و خوداظهاری سلامت بیمار:" else "📋 Medical Warnings & Health Self-Declaration:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = if (isPersian) "هر مورد مرتبط با وضعیت سلامت بیمار را روشن کنید؛ این اطلاعات مستقیماً برای هشدارهای بالینی حین درمان استفاده می‌شود." else "Toggle any items relevant to the patient's health; used directly for clinical risk alerts during treatment.",
                                fontSize = 9.5.sp,
                                color = Color.Gray
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            DENTAL_MEDICAL_ALERT_ITEMS.forEach { item ->
                                val isSel = selectedAlerts.contains(item.labelFa)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAlerts = if (isSel) selectedAlerts - item.labelFa else selectedAlerts + item.labelFa }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = item.labelFa, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = isSel,
                                        onCheckedChange = { checked -> selectedAlerts = if (checked) selectedAlerts + item.labelFa else selectedAlerts - item.labelFa },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.error)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(if (isPersian) "توضیحات و یادداشت اولیه" else "Initial Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSubmit = true
                    if (validationError == null) {
                        val alertsStr = selectedAlerts.joinToString(", ")
                        val selfDecMap = DENTAL_MEDICAL_ALERT_ITEMS.associate { item ->
                            item.slug to (if (selectedAlerts.contains(item.labelFa)) "بله" else "خیر")
                        }
                        val jsonStr = org.json.JSONObject(selfDecMap as Map<*, *>).toString()

                        viewModel.addPatientFull(
                            name = name,
                            phone = phone.ifBlank { "ثبت نشده" },
                            gender = gender,
                            medicalAlerts = alertsStr,
                            selfDeclarationJson = jsonStr,
                            note = note,
                            age = ageStr.toEnglishIntOrNull() ?: 0,
                            birthDate = birthDateStr,
                            preferredMessenger = preferredMessenger
                        )
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(viewModel.label("cancel"))
            }
        }
    )
}

@Composable
fun AddExpenseDialog(viewModel: DentalViewModel, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf("Materials") }
    var amountStr by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val isPersian by viewModel.isPersian.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val untransferredCheques = remember(allPayments) {
        allPayments.filter { it.type == "CHEQUE" && it.chequeStatus != "TRANSFERRED" }
    }
    var selectedCheque by remember { mutableStateOf<Payment?>(null) }
    var chequeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.label("add_expense"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (untransferredCheques.isNotEmpty()) {
                    Text(
                        if (isPersian) "انتقال از یک چک دریافتی (اختیاری):" else "Transfer from a received cheque (optional):",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    ExposedDropdownMenuBox(
                        expanded = chequeDropdownExpanded,
                        onExpandedChange = { chequeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCheque?.let { c -> "${c.chequeBank ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}" } ?: (if (isPersian) "بدون انتخاب چک (هزینه نقدی/عادی)" else "No cheque selected"),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chequeDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = chequeDropdownExpanded,
                            onDismissRequest = { chequeDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPersian) "— بدون چک —" else "— None —") },
                                onClick = {
                                    selectedCheque = null
                                    amountStr = ""
                                    desc = ""
                                    chequeDropdownExpanded = false
                                }
                            )
                            untransferredCheques.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}", fontSize = 12.sp) },
                                    onClick = {
                                        selectedCheque = c
                                        amountStr = c.amount.toString()
                                        desc = (if (isPersian) "انتقال چک شماره ${c.chequeNumber ?: ""} (بانک ${c.chequeBank ?: ""}، صادرکننده ${c.chequeDrawer ?: ""})" else "Cheque transfer #${c.chequeNumber ?: ""} (${c.chequeBank ?: ""})")
                                        chequeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Rent", "Utilities", "Salaries", "Materials", "Other").forEach { cat ->
                        ElevatedFilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(viewModel.label(cat.lowercase())) }
                        )
                    }
                }
                OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(viewModel.label("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = selectedCheque != null)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("notes")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.toEnglishLong()
                if (amt >= 0) {
                    val cheque = selectedCheque
                    if (cheque != null) {
                        viewModel.transferChequeToExpense(cheque, recipientName = desc.ifBlank { viewModel.label(category.lowercase()) }, category = category)
                    } else {
                        viewModel.addExpense(category, amt, desc)
                    }
                    onDismiss()
                }
            }) {
                Text(viewModel.label("save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(viewModel.label("cancel"))
            }
        }
    )
}

// Format currency helper
fun formatAmount(value: Long): String {
    val absValue = kotlin.math.abs(value)
    return if (absValue >= 1_000_000_000) {
        val billions = absValue.toDouble() / 1_000_000_000
        val formatted = String.format(Locale.US, "%.1f", billions)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr میلیارد"
    } else if (absValue >= 1_000_000) {
        val millions = absValue.toDouble() / 1_000_000
        val formatted = String.format(Locale.US, "%.1f", millions)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr میلیون"
    } else if (absValue >= 1_000) {
        val thousands = absValue.toDouble() / 1_000
        val formatted = String.format(Locale.US, "%.1f", thousands)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr هزار"
    } else {
        value.toString()
    }
}

// Downscale and compress image for SQLite storage as base64

// Compress camera captures to Base64

// Convert Base64 string to Android Bitmap for drawing in Composables

// Convert any numeric Long amount to full Persian alphabetical text (e.g. 250000 -> دویست و پنجاه هزار)
fun numberToPersianWords(number: Long): String {
    if (number == 0L) return "صفر"
    
    val units = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    val teens = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    val tens = listOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    val hundreds = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    val groups = listOf("", "هزار", "میلیون", "میلیارد", "تریلیون")

    var temp = number
    val parts = mutableListOf<String>()
    var groupIndex = 0

    while (temp > 0) {
        val part = (temp % 1000).toInt()
        if (part > 0) {
            val partWords = mutableListOf<String>()
            val h = part / 100
            val t = (part % 100) / 10
            val u = part % 10

            if (h > 0) partWords.add(hundreds[h])
            
            if (t == 1) {
                partWords.add(teens[u])
            } else {
                if (t > 1) partWords.add(tens[t])
                if (u > 0) partWords.add(units[u])
            }

            val groupWord = groups[groupIndex]
            var combined = partWords.joinToString(" و ")
            if (groupWord.isNotEmpty()) {
                combined += " $groupWord"
            }
            parts.add(0, combined)
        }
        temp /= 1000
        groupIndex++
    }

    return parts.joinToString(" و ")
}

// Format any input string of numbers to Persian words with "تومان" currency suffix
fun formatToPersianWordsWithUnit(amountStr: String): String {
    val value = amountStr.toEnglishLong()
    if (value == 0L) return ""
    return "${numberToPersianWords(value)} تومان"
}

// Beautiful real-time assistant that renders below OutlinedTextField amount fields
@Composable
fun RealTimeAmountHelper(amountStr: String) {
    val words = remember(amountStr) { formatToPersianWordsWithUnit(amountStr) }
    if (words.isNotEmpty()) {
        Text(
            text = words,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}



@Composable
fun AppointmentSessionDialog(
    appointment: com.example.data.Appointment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val allTreatments by viewModel.allTreatments.collectAsState()
    val patientTreatments = allTreatments.filter { it.patientId == appointment.patientId }
    val plannedIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    
    var selectedIds by remember { mutableStateOf(plannedIds) }
    val isPersian = viewModel.isPersian.collectAsState().value
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "مدیریت جلسه درمانی" else "Manage Treatment Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isPersian) "طرح‌های درمان را برای این جلسه انتخاب کنید:" else "Select treatments for this session:",
                    fontSize = 13.sp, color = Color.Gray
                )
                
                val selectable = patientTreatments.filter { it.status != "COMPLETED" || selectedIds.contains(it.id) }
                if (selectable.isEmpty()) {
                    Text(text = if (isPersian) "طرح درمانی یافت نشد." else "No active treatments found.")
                } else {
                    selectable.forEach { tr ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (selectedIds.contains(tr.id)) {
                                    selectedIds - tr.id
                                } else {
                                    selectedIds + tr.id
                                }
                            }
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(tr.id),
                                onCheckedChange = { chk -> 
                                    selectedIds = if (chk) selectedIds + tr.id else selectedIds - tr.id 
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = tr.title, fontWeight = FontWeight.Bold)
                                Text(text = "${formatAmount(tr.cost - tr.discount)} ${viewModel.label("currency")}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateAppointment(appointment.copy(
                    plannedTreatmentIds = selectedIds.joinToString(","),
                    treatmentType = if (selectedIds.isNotEmpty()) if (isPersian) "طرح های انتخابی" else "Selected Treatments" else ""
                ))
                onDismiss()
            }) {
                Text(if (isPersian) "ذخیره جلسه" else "Save Session")
            }
        },
        dismissButton = {
            if (selectedIds.isNotEmpty()) {
                Button(
                    onClick = {
                        val toComplete = patientTreatments.filter { selectedIds.contains(it.id) }
                        toComplete.forEach { tr ->
                            viewModel.updateTreatmentStatus(tr, "COMPLETED")
                        }
                        viewModel.updateAppointment(appointment.copy(
                            plannedTreatmentIds = selectedIds.joinToString(","),
                            treatmentType = if (isPersian) "انجام شده" else "Completed"
                        ))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(if (isPersian) "پایان و تایید انجام" else "Complete & Confirm", color = Color.White)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(viewModel.label("cancel"))
                }
            }
        }
    )
}

fun String.toEnglishIntOrNull(): Int? {
    if (this.isBlank()) return null
    val englishStr = this.map { ch ->
        when (ch) {
            '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
            '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> ch
        }
    }.joinToString("")
    return englishStr.toIntOrNull()
}

fun parseStagesList(stagesStr: String): List<String> {
    if (stagesStr.isBlank()) return emptyList()
    return stagesStr.split(Regex("""(?:\s*-\s*|\s*\|\s*|\s*,\s*|\s*\n\s*|\s*؛\s*)"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPatientDialog(
    patient: Patient,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsState()

    var name by remember { mutableStateOf(patient.name) }
    var gender by remember { mutableStateOf(if (patient.gender.isNotBlank()) (if (patient.gender == "زن" || patient.gender == "خانم" || patient.gender == "Ms.") (if (isPersian) "خانم" else "Ms.") else (if (isPersian) "آقای" else "Mr.")) else (if (isPersian) "آقای" else "Mr.")) }
    var ageStr by remember { mutableStateOf(if (patient.age > 0) patient.age.toString() else "") }
    var birthDateStr by remember { mutableStateOf(patient.birthDate) }
    var phone by remember { mutableStateOf(patient.phone) }
    var preferredMessenger by remember { mutableStateOf(if (patient.preferredMessenger.isNotBlank()) patient.preferredMessenger else "واتساپ") }
    var note by remember { mutableStateOf(patient.note) }
    var medicalHistory by remember { mutableStateOf(patient.medicalHistory) }

    // موارد هشدار پزشکی و خوداظهاری سلامت اکنون یک فهرست واحد و یکپارچه است (لیست مشترک DENTAL_MEDICAL_ALERT_ITEMS)
    // برای بیمارانی که پیش از این ادغام ثبت شده‌اند، تلاش می‌شود داده‌های قبلی (چه از فهرست هشدارهای قدیمی و چه از
    // پرسشنامه جداگانه قبلی) به‌صورت best-effort به آیتم‌های جدید نگاشت شوند تا داده‌ای از دست نرود.
    val legacyAlertLabels = remember(patient.medicalAlerts) {
        patient.medicalAlerts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val legacySelfDec = remember(patient.selfDeclarationJson) {
        try {
            if (patient.selfDeclarationJson.isNotBlank()) org.json.JSONObject(patient.selfDeclarationJson) else null
        } catch (e: Exception) { null }
    }
    var selectedAlerts by remember {
        mutableStateOf(
            DENTAL_MEDICAL_ALERT_ITEMS.filter { item ->
                // ۱) تطبیق مستقیم با برچسب جدید (برای بیمارانی که قبلاً با همین فهرست یکپارچه ثبت شده‌اند)
                legacyAlertLabels.any { it == item.labelFa } ||
                // ۲) تطبیق کلیدواژه‌ای با فهرست‌های قدیمی و متفاوت دو دیالوگ قبلی
                legacyAlertLabels.any { old ->
                    when (item.slug) {
                        "diabetes" -> old.contains("دیابت")
                        "htn" -> old.contains("فشار") && !old.contains("پایین") && !old.contains("افت")
                        "cardiac" -> old.contains("قلب")
                        "asthma" -> old.contains("آسم")
                        "epilepsy" -> old.contains("صرع")
                        "kidney" -> old.contains("کلی")
                        "liver" -> old.contains("کبد") || old.contains("هپاتیت") || old.contains("ایدز") || old.contains("HIV")
                        "thyroid" -> old.contains("تیروئید")
                        "bleeding_disorder" -> old.contains("انعقاد") || old.contains("رقیق")
                        "allergy" -> old.contains("حساسیت") || old.contains("پنسلین") || old.contains("پنی")
                        "pregnancy" -> old.contains("بارداری") || old.contains("شیردهی")
                        else -> false
                    }
                } ||
                // ۳) تطبیق با پاسخ‌های پرسشنامه جداگانه قدیمی (bleeding/meds/pregnant/surgery)
                (item.slug == "bleeding_history" && legacySelfDec?.optString("bleeding") == "بله") ||
                (item.slug == "bleeding_disorder" && legacySelfDec?.optString("meds") == "بله") ||
                (item.slug == "allergy" && legacySelfDec?.optString("allergy") == "بله") ||
                (item.slug == "pregnancy" && legacySelfDec?.optString("pregnant") == "بله") ||
                (item.slug == "recent_surgery" && legacySelfDec?.optString("surgery") == "بله")
            }.map { it.labelFa }.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isPersian) "ویرایش پرونده بیمار و فرم خوداظهاری" else "Edit Patient Record & Self-Declaration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isPersian) "اصلاح مشخصات، هشدارها و پاسخ‌های فرم سلامت" else "Modify info, alerts & health declaration answers",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Patient Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (isPersian) "نام و نام خانوادگی بیمار *" else "Patient Full Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Phone Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(if (isPersian) "شماره تماس" else "Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Preferred Messenger Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "پیام‌رسان پیش‌فرض بیمار جهت ارسال پیام و مدارک:" else "Preferred Messenger:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                            messengers.forEach { m ->
                                FilterChip(
                                    selected = preferredMessenger == m,
                                    onClick = { preferredMessenger = m },
                                    label = { Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Age & Birth Date with automatic calculation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { input ->
                                ageStr = input
                                val parsedAge = input.toEnglishIntOrNull()
                                if (parsedAge != null && parsedAge > 0 && parsedAge < 120) {
                                    birthDateStr = "${1405 - parsedAge}"
                                }
                            },
                            label = { Text(if (isPersian) "سن بیمار" else "Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = birthDateStr,
                            onValueChange = { input ->
                                birthDateStr = input
                                val yearOnly = input.takeWhile { it.isDigit() }.toEnglishIntOrNull()
                                if (yearOnly != null && yearOnly in 1300..1405) {
                                    ageStr = "${1405 - yearOnly}"
                                }
                            },
                            label = { Text(if (isPersian) "تاریخ / سال تولد" else "Birth Date / Year") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }

                    // Gender Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "جنسیت بیمار:" else "Patient Gender:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val genderOptions = if (isPersian) listOf("آقای", "خانم") else listOf("Mr.", "Ms.")
                            genderOptions.forEach { g ->
                                val isMale = g == "آقای" || g == "Mr."
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            if (isMale) Icons.Default.Man else Icons.Default.Woman,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // موارد هشدار پزشکی و خوداظهاری سلامت (ادغام‌شده با فرم خوداظهاری قبلی)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "📋 موارد هشدار، بیماری‌های خاص و خوداظهاری سلامت بیمار:" else "📋 Medical Warnings & Health Self-Declaration:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            DENTAL_MEDICAL_ALERT_ITEMS.forEach { item ->
                                val isSel = selectedAlerts.contains(item.labelFa)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAlerts = if (isSel) selectedAlerts - item.labelFa else selectedAlerts + item.labelFa }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = item.labelFa, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = isSel,
                                        onCheckedChange = { checked -> selectedAlerts = if (checked) selectedAlerts + item.labelFa else selectedAlerts - item.labelFa },
                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.error)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(if (isPersian) "یادداشت اولیه" else "Initial Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = medicalHistory,
                        onValueChange = { medicalHistory = it },
                        label = { Text(if (isPersian) "سوابق پزشکی و ملاحظات" else "Medical History & Considerations") },
                        modifier = Modifier.fillMaxWidth()
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val alertsStr = selectedAlerts.joinToString(", ")
                        val selfDecMap = DENTAL_MEDICAL_ALERT_ITEMS.associate { item ->
                            item.slug to (if (selectedAlerts.contains(item.labelFa)) "بله" else "خیر")
                        }
                        val jsonStr = org.json.JSONObject(selfDecMap as Map<*, *>).toString()
                        val parsedAge = ageStr.toEnglishIntOrNull() ?: patient.age

                        val updated = patient.copy(
                            name = name,
                            phone = phone.ifBlank { "ثبت نشده" },
                            gender = gender,
                            age = parsedAge,
                            birthDate = birthDateStr,
                            medicalAlerts = alertsStr,
                            selfDeclarationJson = jsonStr,
                            note = note,
                            medicalHistory = medicalHistory,
                            preferredMessenger = preferredMessenger
                        )
                        viewModel.updatePatient(updated)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isPersian) "ذخیره تغییرات" else "Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

data class EndoCanalData(
    val name: String,
    val lengthMm: String = "21.0",
    val refPoint: String = "Cusp Tip",
    val mafFile: String = "#30",
    // بازه کنترلی طول کار (mm) مخصوص همین کانال/دندان - بر اساس میانگین‌های استاندارد اندودانتیکس (جداول Ingle / Weine)
    val minLengthMm: Double = 0.5,
    val maxLengthMm: Double = 30.0
)

/**
 * پروفایل تشریحی هر دندان: تعداد و نام علمی کانال‌ها، طول نرمال/میانگین (پیش‌فرض)
 * و بازه کنترلی مخصوص همان دندان (کمینه سراسری ۰.۵ میلی‌متر طبق الزام پروژه).
 * مقادیر میانگین بر اساس جداول مرجع آموزشی اندودانتیکس (Ingle's Endodontics / Weine's average tooth length table).
 */
private data class CanalAnatomySpec(val name: String, val avgLength: Double, val refPoint: String, val mafFile: String, val rangeHalfWidth: Double)

private fun buildCanal(spec: CanalAnatomySpec): EndoCanalData {
    val min = (spec.avgLength - spec.rangeHalfWidth).coerceAtLeast(0.5)
    val max = spec.avgLength + spec.rangeHalfWidth
    return EndoCanalData(
        name = spec.name,
        lengthMm = String.format(java.util.Locale.US, "%.1f", spec.avgLength),
        refPoint = spec.refPoint,
        mafFile = spec.mafFile,
        minLengthMm = min,
        maxLengthMm = max
    )
}

/**
 * پروفایل تشریحی کامل بر اساس شماره دقیق دندان (نماد FDI دو رقمی).
 * دندان‌های نیش (کانین) به دلیل بلندترین ریشه دندانی، هم میانگین بالاتر و هم بازه کنترلی وسیع‌تری دارند.
 */
private fun canalSpecsForTooth(toothNumber: Int): List<CanalAnatomySpec> {
    return when (toothNumber) {
        // ===== فک بالا - دائمی (Maxillary Permanent) =====
        11, 21 -> listOf(CanalAnatomySpec("C", 23.0, "Incisal Edge", "#40", 4.0)) // سانترال بالا
        12, 22 -> listOf(CanalAnatomySpec("C", 22.0, "Incisal Edge", "#35", 4.0)) // لترال بالا
        13, 23 -> listOf(CanalAnatomySpec("C", 26.5, "Cusp Tip", "#45", 5.5)) // کانین بالا - بلندترین دندان، بازه کنترلی وسیع‌تر
        14, 24 -> listOf( // پرمولار اول بالا - معمولاً ۲ کانال B/L
            CanalAnatomySpec("B", 20.5, "Cusp Tip", "#25", 4.0),
            CanalAnatomySpec("L", 21.5, "Cusp Tip", "#25", 4.0)
        )
        15, 25 -> listOf(CanalAnatomySpec("C", 21.5, "Cusp Tip", "#30", 4.0)) // پرمولار دوم بالا - معمولاً تک‌کانال
        16, 26 -> listOf( // مولار اول بالا - ۳ ریشه استاندارد
            CanalAnatomySpec("MB", 20.5, "MB Cusp", "#30", 3.5),
            CanalAnatomySpec("DB", 20.0, "DB Cusp", "#25", 3.5),
            CanalAnatomySpec("P", 21.5, "Palatal Cusp", "#40", 3.5)
        )
        17, 27 -> listOf( // مولار دوم بالا
            CanalAnatomySpec("MB", 20.0, "MB Cusp", "#30", 3.5),
            CanalAnatomySpec("DB", 19.5, "DB Cusp", "#25", 3.5),
            CanalAnatomySpec("P", 21.0, "Palatal Cusp", "#40", 3.5)
        )
        18, 28 -> listOf(CanalAnatomySpec("MB", 19.0, "MB Cusp", "#30", 4.5), CanalAnatomySpec("P", 19.5, "Palatal Cusp", "#30", 4.5)) // مولار سوم بالا - تشریح متغیر

        // ===== فک پایین - دائمی (Mandibular Permanent) =====
        31, 41 -> listOf(CanalAnatomySpec("C", 20.7, "Incisal Edge", "#35", 3.5)) // سانترال پایین
        32, 42 -> listOf(CanalAnatomySpec("C", 21.1, "Incisal Edge", "#35", 3.5)) // لترال پایین
        33, 43 -> listOf(CanalAnatomySpec("C", 25.6, "Cusp Tip", "#45", 5.5)) // کانین پایین - بلند و بازه کنترلی وسیع
        34, 44 -> listOf(CanalAnatomySpec("C", 21.6, "Cusp Tip", "#30", 4.0)) // پرمولار اول پایین
        35, 45 -> listOf(CanalAnatomySpec("C", 22.3, "Cusp Tip", "#30", 4.0)) // پرمولار دوم پایین
        36, 46 -> listOf( // مولار اول پایین - ۲ ریشه (مزیال دو کاناله، دیستال یک/دو کاناله)
            CanalAnatomySpec("MB", 21.0, "MB Cusp", "#30", 3.5),
            CanalAnatomySpec("ML", 20.5, "ML Cusp", "#25", 3.5),
            CanalAnatomySpec("D", 21.5, "Distal Cusp", "#40", 3.5)
        )
        37, 47 -> listOf(
            CanalAnatomySpec("MB", 20.5, "MB Cusp", "#30", 3.5),
            CanalAnatomySpec("ML", 20.0, "ML Cusp", "#25", 3.5),
            CanalAnatomySpec("D", 21.0, "Distal Cusp", "#40", 3.5)
        )
        38, 48 -> listOf(CanalAnatomySpec("M", 19.5, "Mesial Cusp", "#30", 4.5), CanalAnatomySpec("D", 19.5, "Distal Cusp", "#30", 4.5)) // مولار سوم پایین

        // ===== دندان‌های شیری - فک بالا (Primary Maxillary) =====
        51, 61 -> listOf(CanalAnatomySpec("C", 15.0, "Incisal Edge", "#25", 3.0))
        52, 62 -> listOf(CanalAnatomySpec("C", 15.5, "Incisal Edge", "#25", 3.0))
        53, 63 -> listOf(CanalAnatomySpec("C", 17.5, "Cusp Tip", "#30", 3.5)) // کانین شیری - نسبتاً بلندتر
        54, 64 -> listOf(CanalAnatomySpec("B", 15.0, "Cusp Tip", "#25", 3.0), CanalAnatomySpec("L", 15.0, "Cusp Tip", "#25", 3.0))
        55, 65 -> listOf(
            CanalAnatomySpec("MB", 16.5, "MB Cusp", "#25", 3.0),
            CanalAnatomySpec("DB", 16.0, "DB Cusp", "#25", 3.0),
            CanalAnatomySpec("P", 17.0, "Palatal Cusp", "#30", 3.0)
        )

        // ===== دندان‌های شیری - فک پایین (Primary Mandibular) =====
        71, 81 -> listOf(CanalAnatomySpec("C", 14.0, "Incisal Edge", "#25", 3.0))
        72, 82 -> listOf(CanalAnatomySpec("C", 14.5, "Incisal Edge", "#25", 3.0))
        73, 83 -> listOf(CanalAnatomySpec("C", 17.0, "Cusp Tip", "#30", 3.5)) // کانین شیری
        74, 84 -> listOf(CanalAnatomySpec("M", 15.0, "Mesial Cusp", "#25", 3.0), CanalAnatomySpec("D", 15.0, "Distal Cusp", "#25", 3.0))
        75, 85 -> listOf(CanalAnatomySpec("M", 16.5, "Mesial Cusp", "#25", 3.0), CanalAnatomySpec("D", 16.5, "Distal Cusp", "#25", 3.0))

        else -> listOf(CanalAnatomySpec("B", 21.0, "Cusp Tip", "#25", 4.0), CanalAnatomySpec("L", 21.0, "Cusp Tip", "#25", 4.0))
    }
}

fun getDefaultCanalsForTeeth(selectedTeethStr: String): List<EndoCanalData> {
    val toothDigits = Regex("""\d+""").findAll(selectedTeethStr).map { it.value }.toList()
    val teeth = if (toothDigits.isNotEmpty()) toothDigits else selectedTeethStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val firstToothNum = teeth.firstOrNull()?.toIntOrNull() ?: 14
    return canalSpecsForTooth(firstToothNum).map { buildCanal(it) }
}

fun parseEndoCanalData(notes: String, selectedTeeth: String = ""): List<EndoCanalData> {
    val defaults = getDefaultCanalsForTeeth(selectedTeeth).toMutableList()
    if (!notes.contains("[ENDO_WORKING_LENGTH:")) {
        return defaults
    }
    return try {
        val regex = Regex("""\[ENDO_WORKING_LENGTH:\s*(\[[\s\S]*?\])\s*\]""")
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
}

fun encodeEndoCanalData(existingNotes: String, canals: List<EndoCanalData>): String {
    val cleanNotes = existingNotes.cleanNotesForDisplay()
    val jsonArray = org.json.JSONArray()
    canals.forEach { c ->
        val obj = org.json.JSONObject()
        obj.put("name", c.name)
        obj.put("lengthMm", c.lengthMm)
        obj.put("refPoint", c.refPoint)
        obj.put("mafFile", c.mafFile)
        obj.put("minLengthMm", c.minLengthMm)
        obj.put("maxLengthMm", c.maxLengthMm)
        jsonArray.put(obj)
    }
    val tag = "[ENDO_WORKING_LENGTH: ${jsonArray.toString()}]"
    return if (cleanNotes.isBlank()) tag else "$cleanNotes\n$tag"
}

fun String.cleanNotesForDisplay(): String {
    return this.replace(Regex("""\[ENDO_WORKING_LENGTH:\s*\[[\s\S]*?\]\s*\]"""), "")
               .replace(Regex("""\[RESTORATION_SURFACES:\s*[^\]]*\]"""), "")
               .replace(Regex("""\[CAVITY_CLASS:\s*[^\]]*\]"""), "")
               .trim()
}

@Composable
fun ToothSchematicDiagram(
    selectedTeeth: String,
    canals: List<EndoCanalData>,
    isPersian: Boolean
) {
    val toothNum = selectedTeeth.trim().ifBlank { "14" }
    val isUpperArch = remember(toothNum) {
        val digits = Regex("""\d+""").find(toothNum)?.value
        val num = digits?.toIntOrNull() ?: 14
        (num in 11..28) || (num in 51..68)
    }

    // Color mapper according to prompt requirement:
    // Lingual / Tongue (زبان/زبانی): RED (قرمز)
    // Facial / Buccal (صورت/دهانی): ORANGE (نارنجی)
    // Posterior / Mesial / Distal / Central (پشت/مزیال/دیستال/مرکزی): BLUE (آبی)
    fun getCanalDirectionColor(canalName: String): Color {
        // رنگ‌بندی مستقل و متمایز برای هر کانال بر اساس نام علمی آن (نه فقط جهت کلی)،
        // تا در دندان‌های چندکاناله مثل مولارها هر کانال به‌وضوح از بقیه قابل تفکیک باشد.
        return when (canalName.uppercase()) {
            "MB" -> Color(0xFFFF9800)   // نارنجی - Mesio-Buccal
            "MB2" -> Color(0xFFFFC107)  // کهربایی - Mesio-Buccal 2
            "DB" -> Color(0xFFE64A19)   // نارنجی سوخته - Disto-Buccal
            "P", "PALATAL" -> Color(0xFF8E24AA) // بنفش - Palatal
            "ML" -> Color(0xFF26A69A)   // فیروزه‌ای - Mesio-Lingual
            "DL" -> Color(0xFF00897B)   // سبزآبی - Disto-Lingual
            "D", "DISTAL" -> Color(0xFF42A5F5) // آبی - Distal
            "M", "MESIAL" -> Color(0xFF66BB6A) // سبز - Mesial
            "B", "BUCCAL", "F", "FACIAL" -> Color(0xFFFF9800) // نارنجی - Buccal (تک‌کانال)
            "L", "LINGUAL" -> Color(0xFFEF5350) // قرمز - Lingual (تک‌کانال)
            "C" -> Color(0xFF42A5F5)    // آبی - کانال منفرد قدامی (Central/Single)
            else -> Color(0xFF9E9E9E)   // خاکستری - نام ناشناخته
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersian) "🦷 نمایه ۳‌بعدی کانال‌ها (دندان $toothNum)" else "🦷 3D Root Canal View (Tooth $toothNum)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = if (isUpperArch) Color(0xFF1E88E5).copy(alpha = 0.15f) else Color(0xFF43A047).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isUpperArch) Color(0xFF1E88E5) else Color(0xFF43A047))
                ) {
                    Text(
                        text = if (isPersian) (if (isUpperArch) "فک بالا (Maxilla) ▲" else "فک پایین (Mandible) ▼") else (if (isUpperArch) "Maxillary ▲" else "Mandibular ▼"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpperArch) Color(0xFF1565C0) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Directional Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF5350), CircleShape))
                    Text(if (isPersian) "زبان (زبانی/پالاتال)" else "Tongue (Lingual)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                    Text(if (isPersian) "صورت (دهانی/بوکال)" else "Face (Buccal)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF42A5F5), CircleShape))
                    Text(if (isPersian) "پشت (مزیال/دیستال)" else "Back (Mesial/Distal)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42A5F5))
                }
            }

            // Canvas for 3D Schematic
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B)
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw background grid lines
                val gridStep = 20.dp.toPx()
                var gx = gridStep
                while (gx < canvasWidth) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.04f),
                        start = androidx.compose.ui.geometry.Offset(gx, 0f),
                        end = androidx.compose.ui.geometry.Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridStep
                }

                val rulerX = 28.dp.toPx()
                val crownWidth = canvasWidth * 0.62f
                val crownLeft = (canvasWidth - crownWidth) / 2f
                val crownRight = crownLeft + crownWidth

                // بازه خط‌کش به‌صورت پویا بر اساس بازه کنترلی واقعی همین دندان محاسبه می‌شود
                // (مثلاً برای کانین که ریشه بلندتری دارد، خط‌کش هم بازه وسیع‌تری نشان می‌دهد)
                val canalMinBound = (canals.minOfOrNull { it.minLengthMm } ?: 10.0).toFloat()
                val canalMaxBound = (canals.maxOfOrNull { it.maxLengthMm } ?: 30.0).toFloat()
                val rulerLo = (kotlin.math.floor(canalMinBound / 5f) * 5f).coerceAtLeast(0f)
                val rulerHiRaw = kotlin.math.ceil(canalMaxBound / 5f) * 5f
                val rulerHi = if (rulerHiRaw <= rulerLo) rulerLo + 5f else rulerHiRaw
                val rulerTicks = generateSequence(rulerLo) { it + 5f }.takeWhile { it <= rulerHi + 0.01f }.toList()

                // Determine Crown & Root direction based on Upper/Lower Arch
                // Upper Arch (Maxilla): Crown at bottom, Root Apex extending UPWARDS
                // Lower Arch (Mandible): Crown at top, Root Apex extending DOWNWARDS
                val crownHeight = 36.dp.toPx()
                val crownTop: Float
                val crownBottom: Float
                val rulerStartY: Float
                val rulerEndY: Float

                if (isUpperArch) {
                    crownBottom = canvasHeight - 12.dp.toPx()
                    crownTop = crownBottom - crownHeight
                    rulerStartY = crownTop
                    rulerEndY = 16.dp.toPx()
                } else {
                    crownTop = 12.dp.toPx()
                    crownBottom = crownTop + crownHeight
                    rulerStartY = crownBottom
                    rulerEndY = canvasHeight - 16.dp.toPx()
                }

                // Ruler line
                drawLine(
                    color = Color(0xFF64748B),
                    start = androidx.compose.ui.geometry.Offset(rulerX, rulerStartY),
                    end = androidx.compose.ui.geometry.Offset(rulerX, rulerEndY),
                    strokeWidth = 2.dp.toPx()
                )

                val mmStep = (rulerEndY - rulerStartY) / (rulerTicks.size - 1).coerceAtLeast(1)
                rulerTicks.forEachIndexed { i, mmLabel ->
                    val y = rulerStartY + i * mmStep
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = androidx.compose.ui.geometry.Offset(rulerX - 4.dp.toPx(), y),
                        end = androidx.compose.ui.geometry.Offset(rulerX + 4.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        mmLabel.toInt().toString(),
                        rulerX - 20.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#94A3B8")
                            textSize = 9.dp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                    )
                }

                // Draw Tooth Crown (3D Shaded)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFE2E8F0),
                            Color(0xFFCBD5E1)
                        )
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(crownLeft, crownTop),
                    size = androidx.compose.ui.geometry.Size(crownWidth, crownHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )

                // Pulp Chamber Shading
                val pulpMarginX = crownWidth * 0.2f
                val pulpMarginY = crownHeight * 0.25f
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFF880E4F)),
                        center = androidx.compose.ui.geometry.Offset(crownLeft + crownWidth * 0.5f, crownTop + crownHeight * 0.5f),
                        radius = 22.dp.toPx()
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(crownLeft + pulpMarginX, crownTop + pulpMarginY),
                    size = androidx.compose.ui.geometry.Size(crownWidth - pulpMarginX * 2, crownHeight - pulpMarginY * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Render each Root Canal
                val numCanals = canals.size.coerceAtLeast(1)
                val canalSpacing = crownWidth / (numCanals + 1)
                val canalXPositions = (1..numCanals).map { crownLeft + it * canalSpacing }
                val rootDirSign = if (isUpperArch) -1f else 1f // ریشه‌ها از تاج به سمت اپکس (بالا برای فک بالا، پایین برای فک پایین) واگرا می‌شوند
                val rootBaseY = if (isUpperArch) crownTop else crownBottom
                // نکته مهم: طول/موقعیت شماتیک ریشه فقط به بازه استاندارد آناتومیک دندان (rulerLo/rulerHi) وابسته است،
                // نه به مقدار فعلی canal.lengthMm که کاربر در حال ویرایش آن است. به همین دلیل با تغییر عدد طول کار
                // توسط کاربر (دکمه‌های +/-)، شکل و اندازه ریشه ثابت می‌ماند و فقط خط رنگی کانال داخل آن جابه‌جا می‌شود.
                val rootExtent = kotlin.math.abs(rulerEndY - rootBaseY) * 0.58f

                // شماتیک بدنه ریشه (سایه‌روشن) متناسب با تعداد ریشه استاندارد همین دندان و با تیپرینگ واقعی‌تر
                // (باریک و نوک‌تیز به سمت اپکس، مطابق شکل واقعی ریشه دندان در تصویر مرجع)
                canalXPositions.forEachIndexed { idx, rx ->
                    val spread = if (numCanals == 1) 0f else (rx - (crownLeft + crownWidth / 2f)) * 0.95f
                    val rootTipX = rx + spread
                    val rootTipY = rootBaseY + rootDirSign * rootExtent
                    val rootWidthAtBase = (crownWidth / (numCanals + 1)) * 0.68f
                    val midY = rootBaseY + rootDirSign * rootExtent * 0.55f

                    val rootPath = Path().apply {
                        moveTo(rx - rootWidthAtBase / 2f, rootBaseY)
                        // ضلع چپ ریشه: از عریض در قاعده به‌تدریج باریک می‌شود
                        quadraticTo(rx - rootWidthAtBase * 0.42f, midY, rx - rootWidthAtBase * 0.08f, rootTipY - rootDirSign * (rootExtent * 0.08f))
                        // نوک تیز و مخروطی اپکس (مطابق ریشه واقعی دندان)
                        lineTo(rootTipX, rootTipY)
                        lineTo(rx + rootWidthAtBase * 0.08f, rootTipY - rootDirSign * (rootExtent * 0.08f))
                        // ضلع راست ریشه
                        quadraticTo(rx + rootWidthAtBase * 0.42f, midY, rx + rootWidthAtBase / 2f, rootBaseY)
                        close()
                    }
                    drawPath(
                        path = rootPath,
                        brush = Brush.verticalGradient(
                            colors = if (isUpperArch) listOf(Color(0xFFEDE4CE), Color(0xFFCBBE9C)) else listOf(Color(0xFFCBBE9C), Color(0xFFEDE4CE))
                        ),
                        alpha = 0.55f
                    )
                    drawPath(
                        path = rootPath,
                        color = Color(0xFF8D7B68).copy(alpha = 0.65f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }

                canals.forEachIndexed { idx, canal ->
                    val canalX = canalXPositions[idx]
                    val lengthVal = canal.lengthMm.toFloatOrNull() ?: 21.0f
                    val normalized = ((lengthVal - rulerLo) / (rulerHi - rulerLo)).coerceIn(0.03f, 1.0f)

                    val apexY = rulerStartY + normalized * (rulerEndY - rulerStartY)
                    val canalBaseColor = getCanalDirectionColor(canal.name)

                    val curveOffsetX = if (idx == 0) -10.dp.toPx() else if (idx == numCanals - 1) 10.dp.toPx() else 0f
                    val apexX = canalX + curveOffsetX

                    val canalStartY = if (isUpperArch) crownTop else crownBottom

                    val path = Path().apply {
                        moveTo(canalX, canalStartY)
                        cubicTo(
                            canalX, canalStartY + (apexY - canalStartY) * 0.4f,
                            apexX, canalStartY + (apexY - canalStartY) * 0.7f,
                            apexX, apexY
                        )
                    }

                    // Outer glow
                    drawPath(
                        path = path,
                        color = canalBaseColor.copy(alpha = 0.35f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 10.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Canal Core
                    drawPath(
                        path = path,
                        color = canalBaseColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Canal Inner Highlight
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.75f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Length dashed line to ruler
                    drawLine(
                        color = canalBaseColor.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(rulerX, apexY),
                        end = androidx.compose.ui.geometry.Offset(apexX, apexY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Apex Point Circles
                    drawCircle(
                        color = canalBaseColor.copy(alpha = 0.4f),
                        radius = 8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                    drawCircle(
                        color = canalBaseColor,
                        radius = 4.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                }
            }

            // Canal Badges Row below diagram
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                canals.forEach { canal ->
                    val canalColor = getCanalDirectionColor(canal.name)

                    Surface(
                        color = canalColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, canalColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "کانال ${canal.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = canalColor
                            )
                            Text(
                                text = "${canal.lengthMm} mm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = canalColor
                            )
                            Text(
                                text = "MAF ${canal.mafFile}",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun parseRestorationSurfaces(notes: String): Set<String> {
    val regex = Regex("""\[RESTORATION_SURFACES:\s*([^\]]*)\]""")
    val match = regex.find(notes)
    if (match != null) {
        val str = match.groupValues[1]
        return str.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }
    return emptySet()
}

fun parseCavityClass(notes: String): String {
    val regex = Regex("""\[CAVITY_CLASS:\s*([^\]]*)\]""")
    val match = regex.find(notes)
    return match?.groupValues?.get(1)?.trim() ?: ""
}

fun encodeRestorationData(existingNotes: String, surfaces: Set<String>, cavityClass: String): String {
    val cleanNotes = existingNotes.cleanNotesForDisplay()
    val surfStr = surfaces.joinToString(", ")
    var tag = ""
    if (surfaces.isNotEmpty()) {
        tag += "[RESTORATION_SURFACES: $surfStr]"
    }
    if (cavityClass.isNotBlank()) {
        if (tag.isNotEmpty()) tag += "\n"
        tag += "[CAVITY_CLASS: $cavityClass]"
    }
    return if (cleanNotes.isBlank()) tag else "$cleanNotes\n$tag"
}

@Composable
fun ToothSurfaceRestorationCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsState()
    val isRestorationTreatment = remember(treatment.title, treatment.notes) {
        val t = treatment.title.lowercase()
        t.contains("ترمیم") || t.contains("پر") || t.contains("کامپوزیت") || t.contains("آمالگام") ||
                t.contains("restor") || t.contains("composite") || t.contains("amalgam") || t.contains("filling") ||
                treatment.notes.contains("[RESTORATION_SURFACES:")
    }

    if (!isRestorationTreatment) return

    var expanded by remember { mutableStateOf(!readOnly) }
    var selectedSurfaces by remember(treatment.notes) {
        mutableStateOf(parseRestorationSurfaces(treatment.notes))
    }
    var cavityClass by remember(treatment.notes) {
        mutableStateOf(parseCavityClass(treatment.notes))
    }

    val toothNum = treatment.selectedTeeth.trim().ifBlank { "16" }
    val toothNumDigits = remember(toothNum) { Regex("""\d+""").find(toothNum)?.value ?: "16" }
    val toothInt = remember(toothNumDigits) { toothNumDigits.toIntOrNull() ?: 16 }
    val isAnterior = remember(toothInt) {
        (toothInt in 11..13) || (toothInt in 21..23) || (toothInt in 31..33) || (toothInt in 41..43) ||
        (toothInt in 51..53) || (toothInt in 61..63) || (toothInt in 71..73) || (toothInt in 81..83)
    }

    // Filter applicable G.V. Black Cavity Classes strictly by Tooth Type (Anterior vs Posterior)
    val applicableCavityClasses = remember(isAnterior) {
        if (isAnterior) {
            listOf("Class I", "Class III", "Class IV", "Class V")
        } else {
            listOf("Class I", "Class II (MOD)", "Class II (MO)", "Class II (DO)", "Class V")
        }
    }

    fun toggleSurface(code: String) {
        if (readOnly) return
        val newSet = selectedSurfaces.toMutableSet()
        if (newSet.contains(code)) newSet.remove(code) else newSet.add(code)
        selectedSurfaces = newSet
        val updatedNotes = encodeRestorationData(treatment.notes, newSet, cavityClass)
        viewModel.updateTreatment(treatment.copy(notes = updatedNotes))
    }

    fun selectCavityClass(cClass: String) {
        if (readOnly) return
        val newClass = if (cavityClass == cClass) "" else cClass
        cavityClass = newClass
        val newSurfaces = when (newClass) {
            "Class I" -> if (isAnterior) setOf("I") else setOf("O")
            "Class II (MO)" -> setOf("M", "O")
            "Class II (DO)" -> setOf("D", "O")
            "Class II (MOD)" -> setOf("M", "O", "D")
            "Class III" -> setOf("M", "D")
            "Class IV" -> setOf("I", "M", "D")
            "Class V" -> setOf("V")
            else -> selectedSurfaces
        }
        selectedSurfaces = newSurfaces
        val updatedNotes = encodeRestorationData(treatment.notes, newSurfaces, newClass)
        viewModel.updateTreatment(treatment.copy(notes = updatedNotes))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isPersian) {
                            if (isAnterior) "تصویر شماتیک و سطوح قدامی (دندان $toothNum)" else "تصویر شماتیک و سطوح خلفی (دندان $toothNum)"
                        } else {
                            if (isAnterior) "Anterior Caries Schematic & Surfaces (Tooth $toothNum)" else "Posterior Caries Schematic & Surfaces (Tooth $toothNum)"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (selectedSurfaces.isEmpty()) "انتخاب سطح" else selectedSurfaces.joinToString("-"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (expanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Display Interactive Caries Class Schematic Anatomy Diagram!
                        com.example.ui.components.CariesClassSchematicDiagram(
                            modifier = Modifier.fillMaxWidth(),
                            isAnterior = isAnterior,
                            toothNumber = toothInt,
                            selectedClass = cavityClass,
                            onClassSelected = { cClass ->
                                selectCavityClass(cClass)
                            }
                        )

                        // Quick Surface Chips Row
                        if (!readOnly) {
                            Text(
                                text = if (isPersian) "کلیدهای سریع انتخاب سطوح دندان:" else "Quick Surface Chips:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val allSurfaces = if (isAnterior) {
                                    listOf(
                                        "I" to (if (isPersian) "اینسیزال (I)" else "Incisal (I)"),
                                        "M" to (if (isPersian) "مزیال (M)" else "Mesial (M)"),
                                        "D" to (if (isPersian) "دیستال (D)" else "Distal (D)"),
                                        "B" to (if (isPersian) "لب/صورت (B)" else "Labial (B)"),
                                        "L" to (if (isPersian) "زبانی (L)" else "Lingual (L)"),
                                        "V" to (if (isPersian) "طوق (V - جینجیوال)" else "Gingival (V)")
                                    )
                                } else {
                                    listOf(
                                        "O" to (if (isPersian) "اکلوزال (O)" else "Occlusal (O)"),
                                        "M" to (if (isPersian) "مزیال (M)" else "Mesial (M)"),
                                        "D" to (if (isPersian) "دیستال (D)" else "Distal (D)"),
                                        "B" to (if (isPersian) "دهانی (B)" else "Buccal (B)"),
                                        "L" to (if (isPersian) "زبانی (L)" else "Lingual (L)"),
                                        "V" to (if (isPersian) "طوق (V - جینجیوال)" else "Gingival (V)")
                                    )
                                }

                                allSurfaces.forEach { (code, label) ->
                                    val isSel = selectedSurfaces.contains(code) || (code == "I" && selectedSurfaces.contains("O")) || (code == "O" && selectedSurfaces.contains("I"))
                                    val chipColor = when (code) {
                                        "L" -> Color(0xFFEF5350) // RED
                                        "B" -> Color(0xFFFF9800) // ORANGE
                                        "V" -> Color(0xFFAB47BC) // PURPLE (GINGIVAL)
                                        "I" -> Color(0xFF00ACC1) // CYAN
                                        else -> Color(0xFF1E88E5) // BLUE
                                    }

                                    FilterChip(
                                        selected = isSel,
                                        onClick = { toggleSurface(code) },
                                        label = { Text(label, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                            selectedLabelColor = chipColor
                                        ),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }

                            // Cavity Class Selection Filtered strictly by Anterior / Posterior
                            Text(
                                text = if (isPersian) "کلاس علمی پوسیدگی (Black's Classification) - ${if (isAnterior) "قدامی" else "خلفی"}:" else "Cavity Class (G.V. Black) - ${if (isAnterior) "Anterior" else "Posterior"}:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                applicableCavityClasses.forEach { cClass ->
                                    val isSel = cavityClass == cClass
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectCavityClass(cClass) },
                                        label = { Text(cClass, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EndoWorkingLengthCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsState()
    val isEndoTreatment = remember(treatment.title, treatment.notes) {
        val t = treatment.title.lowercase()
        val n = treatment.notes.lowercase()
        val hasEndoKeyword = t.contains("عصب") || t.contains("ریشه") || t.contains("rct") || t.contains("endo") || t.contains("پالپ") || n.contains("[endo_working_length:")
        val isPureRestoration = (t.contains("ترمیم") || t.contains("پرکردن") || t.contains("پر کردن") || t.contains("کامپوزیت") || t.contains("آمالگام") || t.contains("filling") || t.contains("restor")) && !t.contains("عصب") && !t.contains("ریشه") && !t.contains("endo") && !t.contains("rct")
        hasEndoKeyword && !isPureRestoration
    }

    if (!isEndoTreatment) return

    // Default accordion state: CLOSED BY DEFAULT for Patient Record view (readOnly = true), OPEN for Treatment session editing
    var expanded by remember { mutableStateOf(!readOnly) }
    var canalList by remember(treatment.notes, treatment.selectedTeeth) {
        mutableStateOf(parseEndoCanalData(treatment.notes, treatment.selectedTeeth))
    }

    val canalNameOptions = listOf("MB", "MB2", "DB", "P", "ML", "DL", "B", "L", "C")
    val stdMafOptions = listOf("#15", "#20", "#25", "#30", "#35", "#40")
    val largeMafOptions = listOf("#45", "#50", "#55", "#60", "#70", "#80")
    val rotaryMafOptions = listOf("F1", "F2", "F3", "F4", "F5", "20/.04", "25/.04", "25/.06", "30/.06")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Straighten,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isPersian) "🦷 طول کارکرد کانال‌های ریشه (Endo Length)" else "🦷 Root Canal Working Lengths",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${canalList.size} ${if (isPersian) "کانال" else "canals"}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (expanded) {
                // Tooth Graphic Schematic Diagram (Visible in both read-only and edit mode)
                ToothSchematicDiagram(
                    selectedTeeth = treatment.selectedTeeth,
                    canals = canalList,
                    isPersian = isPersian
                )

                // Interactive controls ONLY shown during treatment sessions (when readOnly = false)
                if (!readOnly) {
                    // Reset / Default Canals quick action button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "تنظیمات پیش‌فرض بر اساس دندان انتخاب‌شده:" else "Default Canals based on tooth:",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = {
                                val defaults = getDefaultCanalsForTeeth(treatment.selectedTeeth)
                                canalList = defaults
                                val newNotes = encodeEndoCanalData(treatment.notes, defaults)
                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(if (isPersian) "ریست پیش‌فرض" else "Reset Default", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Interactive List of canals for length adjustments during treatment sessions
                    canalList.forEachIndexed { index, canal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = when (canal.name) {
                                            "MB", "MB2" -> Color(0xFFE3F2FD)
                                            "DB" -> Color(0xFFE8F5E9)
                                            "P" -> Color(0xFFFFF3E0)
                                            "B" -> Color(0xFFE0F7FA)
                                            "L" -> Color(0xFFF3E5F5)
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "کانال ${canal.name}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (canal.name) {
                                                "MB", "MB2" -> Color(0xFF1565C0)
                                                "DB" -> Color(0xFF2E7D32)
                                                "P" -> Color(0xFFE65100)
                                                "B" -> Color(0xFF00838F)
                                                "L" -> Color(0xFF6A1B9A)
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Interactive length increment/decrement controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val currentVal = canal.lengthMm.toDoubleOrNull() ?: 21.0
                                                val newVal = (currentVal - 0.5).coerceAtLeast(canal.minLengthMm)
                                                val updated = canalList.toMutableList()
                                                updated[index] = canal.copy(lengthMm = String.format(java.util.Locale.US, "%.1f", newVal))
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("-0.5", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text(
                                                    text = "${canal.lengthMm} mm",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.1f", canal.minLengthMm)}–${String.format(java.util.Locale.US, "%.1f", canal.maxLengthMm)}",
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val currentVal = canal.lengthMm.toDoubleOrNull() ?: 21.0
                                                val newVal = (currentVal + 0.5).coerceAtMost(canal.maxLengthMm)
                                                val updated = canalList.toMutableList()
                                                updated[index] = canal.copy(lengthMm = String.format(java.util.Locale.US, "%.1f", newVal))
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("+0.5", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = canalList.toMutableList()
                                                updated.removeAt(index)
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isPersian) "سایز فایل نهائی (MAF):" else "MAF File:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Row 1: Standard Hand Files (#15 - #40)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۱. دستی (۱۵-۴۰):" else "1. Std:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            stdMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Large Hand Files (#45 - #80)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۲. ضخیم (۴۵-۸۰):" else "2. Large:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            largeMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 3: Rotary Standard Files
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۳. روتاری:" else "3. Rotary:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rotaryMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add Canal Options Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isPersian) "افزودن کانال جدید:" else "Add Canal:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            canalNameOptions.filter { opt -> canalList.none { it.name == opt } }.forEach { optName ->
                                OutlinedButton(
                                    onClick = {
                                        val updated = canalList.toMutableList()
                                        updated.add(EndoCanalData(name = optName, lengthMm = "21.0", refPoint = "Cusp Tip", mafFile = "#30"))
                                        canalList = updated
                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("+ $optName", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * لیست یکپارچه و جامع «موارد هشدار پزشکی و خوداظهاری سلامت بیمار».
 * پیش از این، این فهرست به‌صورت دو بخش جداگانه و ناهماهنگ (چک‌لیست هشدارها در یک تب،
 * و پرسشنامه بله/خیر در تب دیگر با فهرست متفاوت در دیالوگ افزودن در مقابل ویرایش بیمار) وجود داشت.
 * اکنون هر دو در یک فهرست واحد ادغام شده‌اند تا هم در ثبت بیمار جدید و هم ویرایش پرونده کاملاً یکسان باشند.
 * هر آیتم یک اسلاگ پایدار (برای ذخیره‌سازی JSON) و یک برچسب فارسی (برای نمایش/تطبیق با medicalAlerts) دارد.
 */
data class MedicalAlertItem(val slug: String, val labelFa: String)

val DENTAL_MEDICAL_ALERT_ITEMS = listOf(
    MedicalAlertItem("diabetes", "دیابت"),
    MedicalAlertItem("htn", "فشار خون بالا"),
    MedicalAlertItem("hypotension", "فشار خون پایین / افت فشار و سرگیجه یا غش حین تزریق"),
    MedicalAlertItem("cardiac", "بیماری قلبی / دریچه مصنوعی یا سابقه اندوکاردیت"),
    MedicalAlertItem("asthma", "آسم یا بیماری تنفسی مزمن"),
    MedicalAlertItem("epilepsy", "صرع و تشنج"),
    MedicalAlertItem("kidney", "بیماری کلیوی"),
    MedicalAlertItem("liver", "بیماری کبدی / هپاتیت / HIV"),
    MedicalAlertItem("thyroid", "اختلال تیروئید (پرکاری/کم‌کاری)"),
    MedicalAlertItem("bleeding_disorder", "اختلالات انعقاد خون / مصرف داروهای ضدانعقاد (وارفارین، آسپرین)"),
    MedicalAlertItem("bleeding_history", "سابقه خونریزی شدید پس از کشیدن دندان یا جراحی"),
    MedicalAlertItem("osteoporosis", "استئوپروز یا مصرف داروهای بیس‌فسفونات/دنوسوماب"),
    MedicalAlertItem("cancer", "سرطان یا سابقه شیمی‌درمانی/پرتودرمانی سر و گردن"),
    MedicalAlertItem("allergy", "حساسیت دارویی، غذایی یا به بی‌حسی موضعی (پنی‌سیلین، لاتکس)"),
    MedicalAlertItem("pregnancy", "بارداری یا شیردهی"),
    MedicalAlertItem("recent_surgery", "سابقه جراحی یا بستری اخیر در بیمارستان"),
    MedicalAlertItem("smoking", "مصرف دخانیات (سیگار / قلیان)")
)

fun isLabRelatedTreatment(tx: Treatment, services: List<LabService>): Boolean {
    val t = tx.title.lowercase()
    val n = tx.notes.lowercase()
    val labKeywords = listOf(
        "روکش", "crown", "لمینت", "laminate", "ونیر", "veneer", "بریج", "bridge", 
        "اینله", "آنله", "inlay", "onlay", "پروتز", "دست دندان", "denture", "ایمپلنت", 
        "implant", "الاینر", "aligner", "پلاک", "نایت گارد", "pfm", "zirconia", 
        "ssc", "لابراتوار", "lab", "سرامیک", "کست"
    )
    if (labKeywords.any { kw -> t.contains(kw) || n.contains(kw) }) return true
    return services.any { s -> s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true) }
}

fun Modifier.threeSecondLongPress(
    onLongClick: () -> Unit,
    onClick: (() -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var isLongPress = false
            val timerJob = this@coroutineScope.launch {
                delay(2000L) // Exactly 2 seconds of continuous holding
                isLongPress = true
                onLongClick()
            }
            val up = waitForUpOrCancellation()
            timerJob.cancel()
            if (!isLongPress && up != null) {
                onClick?.invoke()
            }
        }
    }
}

@Composable
fun ClinicalProtocolDialog(
    therapyTitle: String,
    patientMedicalHistory: String = "",
    patientMedicalAlerts: String = "",
    isPersian: Boolean = true,
    initialFontSizePt: Int = 11,
    onDismiss: () -> Unit
) {
    val initialProtocol = remember(therapyTitle) { TherapyKnowledgeBase.findProtocol(therapyTitle) }
    if (initialProtocol == null) return

    var currentProtocol by remember(therapyTitle) { mutableStateOf(initialProtocol) }
    val fontSizePt = initialFontSizePt
    var isEditMode by remember { mutableStateOf(false) }

    // Draft state for edit mode
    var editNameFa by remember(currentProtocol) { mutableStateOf(currentProtocol.nameFa) }
    var editSessionsText by remember(currentProtocol) { 
        mutableStateOf(currentProtocol.sessions.joinToString("\n---\n") { "${it.titleFa}\n${it.descFa}" }) 
    }
    var editPrecautionsText by remember(currentProtocol) { mutableStateOf(currentProtocol.precautions.joinToString("\n")) }
    var editPrescriptionsText by remember(currentProtocol) { mutableStateOf(currentProtocol.prescriptions.joinToString("\n")) }
    var editMaterialsText by remember(currentProtocol) { 
        mutableStateOf(
            if (currentProtocol.modernMaterials.isNotEmpty()) currentProtocol.modernMaterials.joinToString("\n")
            else "• بوندینگ نسل ۸ Universal\n• فایل‌های روتاری تخصصی heat-treated\n• رزین و بیوسرامیک‌های جدید\n• ماتریس و وج‌های سیلیکونی"
        ) 
    }
    var editComplicationsText by remember(currentProtocol) { 
        mutableStateOf(currentProtocol.complications.joinToString("\n") { "${it.first} : ${it.second}" }) 
    }
    var editPostOpText by remember(currentProtocol) { mutableStateOf(currentProtocol.postSessionInstructions.joinToString("\n")) }

    val historyFlags = remember(currentProtocol, patientMedicalHistory, patientMedicalAlerts) {
        TherapyKnowledgeBase.matchingHistoryFlags(
            currentProtocol,
            patientMedicalHistory,
            patientMedicalAlerts
        )
    }

    val bodySize = fontSizePt.sp
    val titleSize = (fontSizePt + 2).sp
    // این صفحه سایز فونت اختصاصی خودش را دارد و از لغزنده‌ی سراسری فونت (تنظیمات اختصاصی) مستثنی است
    val unscaledDensity = LocalDensity.current
    val protocolDensity = androidx.compose.ui.unit.Density(density = unscaledDensity.density, fontScale = 1f)

    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides protocolDensity) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) currentProtocol.nameFa else currentProtocol.nameEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizePt + 3).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edit Protocol",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (isPersian) "💡 لمس طولانی ۳ ثانیه‌ای برای فراخوانی شرح بالینی" else "💡 Hold for 3s to open clinical protocol",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isEditMode) {
                    // EDIT MODE FORM
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPersian) "✏️ حالت ویرایش شرح بالینی و متریال‌ها (تغییرات را پس از ویرایش ذخیره کنید):"
                            else "✏️ Edit Mode: Modify clinical guidelines & materials:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = editNameFa,
                        onValueChange = { editNameFa = it },
                        label = { Text("عنوان پروتکل درمانی") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editSessionsText,
                        onValueChange = { editSessionsText = it },
                        label = { Text("مراحل و شرح جلسات (جداسازی با ---)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = editMaterialsText,
                        onValueChange = { editMaterialsText = it },
                        label = { Text("🧪 متریال‌ها، تجهیزات و مواد مصرفی بروز") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = editPrecautionsText,
                        onValueChange = { editPrecautionsText = it },
                        label = { Text("احتیاط‌ها و هشدارهای بالینی") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editPrescriptionsText,
                        onValueChange = { editPrescriptionsText = it },
                        label = { Text("داروهای تجویزی (ADA/AHA)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editComplicationsText,
                        onValueChange = { editComplicationsText = it },
                        label = { Text("عوارض بالینی و راهکارها (فرمت: عارضه : راهکار)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editPostOpText,
                        onValueChange = { editPostOpText = it },
                        label = { Text("سفارشات ترخیص و مراقبت‌های بعدی") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Button(
                        onClick = {
                            val newSessions = editSessionsText.split("---").mapNotNull { block ->
                                val lines = block.trim().lines().filter { it.isNotBlank() }
                                if (lines.isNotEmpty()) {
                                    TherapySessionInfo(
                                        titleFa = lines.first(),
                                        descFa = lines.drop(1).joinToString("\n")
                                    )
                                } else null
                            }
                            val newPrecautions = editPrecautionsText.lines().filter { it.isNotBlank() }
                            val newPrescriptions = editPrescriptionsText.lines().filter { it.isNotBlank() }
                            val newMaterials = editMaterialsText.lines().filter { it.isNotBlank() }
                            val newComplications = editComplicationsText.lines().mapNotNull { line ->
                                if (line.contains(":")) {
                                    val parts = line.split(":", limit = 2)
                                    parts[0].trim() to parts[1].trim()
                                } else if (line.isNotBlank()) line to "" else null
                            }
                            val newPostOp = editPostOpText.lines().filter { it.isNotBlank() }

                            val updated = currentProtocol.copy(
                                nameFa = editNameFa,
                                sessions = if (newSessions.isNotEmpty()) newSessions else currentProtocol.sessions,
                                precautions = if (newPrecautions.isNotEmpty()) newPrecautions else currentProtocol.precautions,
                                prescriptions = if (newPrescriptions.isNotEmpty()) newPrescriptions else currentProtocol.prescriptions,
                                modernMaterials = newMaterials,
                                complications = if (newComplications.isNotEmpty()) newComplications else currentProtocol.complications,
                                postSessionInstructions = if (newPostOp.isNotEmpty()) newPostOp else currentProtocol.postSessionInstructions
                            )
                            TherapyKnowledgeBase.updateProtocol(updated)
                            currentProtocol = updated
                            isEditMode = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPersian) "ذخیره تغییرات شرح بالینی" else "Save Protocol Changes")
                    }
                } else {
                    // VIEW MODE WITH CUSTOMIZABLE FONT SIZE
                    if (historyFlags.isNotEmpty()) {
                        Text(
                            text = if (isPersian) "⚠️ ملاحظات اختصاصی سابقه این بیمار:" else "⚠️ Patient-Specific History Considerations:",
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSize,
                            color = MaterialTheme.colorScheme.error
                        )
                        historyFlags.forEach { flag ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (flag.severity == FlagSeverity.CONTRAINDICATION)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = flag.warningFa,
                                    fontSize = bodySize,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    Text(
                        text = if (isPersian) "📋 شرح جلسات و مراحل درمان:" else "📋 Sessions & Stage Breakdown:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.sessions.forEach { s ->
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(text = s.titleFa, fontWeight = FontWeight.Bold, fontSize = (fontSizePt + 0.5).sp)
                            Text(text = s.descFa, fontSize = bodySize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()

                    // SECTION: MODERN MATERIALS & EQUIPMENT
                    Text(
                        text = if (isPersian) "🧪 تجهیزات و متریال‌های بروز مصرفی:" else "🧪 Modern Materials & Equipment:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val displayMaterials = if (currentProtocol.modernMaterials.isNotEmpty()) currentProtocol.modernMaterials else listOf(
                        "• بوندینگ نسل جدید Universal / Self-Etch",
                        "• فایل‌های حرارتی و روتاری تخصصی Gold / Blue",
                        "• بیوسرامیک، MTA و سیلرهای برپایه کلسیم سیلیکات",
                        "• نایت گارد و تجهیزات ایزولاسیون رابردام"
                    )
                    displayMaterials.forEach { mat ->
                        Text(text = if (mat.startsWith("•") || mat.startsWith("-")) mat else "• $mat", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "🛡️ احتیاط‌ها و الزامات:" else "🛡️ Precautions & Requirements:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.precautions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "💊 داروهای تجویزی (ADA 2024 / AHA 2021):" else "💊 Prescribed Medications (ADA 2024 / AHA 2021):",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.prescriptions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "⚕️ عوارض بالینی و فوریت‌ها:" else "⚕️ Clinical Complications & Emergencies:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.complications.forEach { (comp, sol) ->
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(text = "▸ $comp", fontWeight = FontWeight.Bold, fontSize = bodySize, color = Color(0xFFB71C1C))
                            if (sol.isNotBlank()) {
                                Text(text = sol, fontSize = bodySize)
                            }
                        }
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "📝 سفارشات ترخیص و مراقبت:" else "📝 Post-Session Instructions:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.postSessionInstructions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "منابع: ${currentProtocol.sources.joinToString("، ")}" else "Sources: ${currentProtocol.sources.joinToString(", ")}",
                        fontSize = (fontSizePt - 2).coerceAtLeast(8).sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "بستن" else "Close")
            }
        }
    )
    }
}

@Composable
fun OfficialLabInvoiceDialog(
    order: com.example.data.LabOrder,
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    onDismiss: () -> Unit
) {
    val currency = viewModel.label("currency")
    var printStatusText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val neonPalette = LocalNeonPalette.current
    val accentColorInt = neonPalette.primary.toArgb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (isPersian) "صورت‌حساب و فاکتور رسمی سفارش لابراتوار" else "Official Lab Order Invoice",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Printable Card Layout
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isPersian) "مطب / کلینیک تخصصی دندانپزشکی" else "Dental Clinic Invoice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "شماره فاکتور: LAB-${order.id + 1000}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isPersian) "فاکتور رسمی" else "OFFICIAL INVOICE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        // Parties info
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "لابراتوار طرف قرارداد:" else "Laboratory:",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Text(order.labName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "نام بیمار / پرونده:" else "Patient:",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Text(order.patientName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Order Spec
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "نوع پروتز / خدمت:" else "Prosthesis:", fontSize = 11.sp, color = Color.Gray)
                                    Text(order.prosthesisType, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "شماره دندان:" else "Tooth No:", fontSize = 11.sp, color = Color.Gray)
                                    Text(if (order.toothNumber.isNotBlank()) order.toothNumber else "-", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تاریخ ثبت سفارش:" else "Order Date:", fontSize = 11.sp, color = Color.Gray)
                                    Text(viewModel.formatDateFullVerbal(order.orderDate), fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "سررسید تحویل نهایی:" else "Due Date:", fontSize = 11.sp, color = Color.Gray)
                                    Text(viewModel.formatDateFullVerbal(order.dueDate), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "وضعیت ساخت:" else "Status:", fontSize = 11.sp, color = Color.Gray)
                                    Text(viewModel.label("status_${order.status.lowercase()}"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        HorizontalDivider()

                        // Total Price Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isPersian) "مبلغ کل قابل پرداخت فاکتور:" else "Total Cost:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${formatAmount(order.cost)} $currency",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Seal & Signature Line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isPersian) "محل مهر و امضای لابراتوار" else "Lab Signature",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Text(
                                if (isPersian) "محل امضای دندانپزشک / مسئول کلینیک" else "Dentist Signature",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                if (printStatusText != null) {
                    Text(
                        text = printStatusText!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val file = ReportGenerators.labOrderInvoice(
                                context = context,
                                clinicName = settings.clinicName,
                                doctorName = settings.doctorName,
                                accent = accentColorInt,
                                currency = currency,
                                order = order,
                                dateFmt = { viewModel.formatDate(it) },
                                amountFmt = { formatAmount(it) }
                            )
                            printPdfFile(context, file, "فاکتور لابراتوار")
                            printStatusText = if (isPersian) "📄 فاکتور برای چاپ ارسال شد" else "Invoice sent to print"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "چاپ فاکتور" else "Print")
                    }

                    OutlinedButton(
                        onClick = {
                            val file = ReportGenerators.labOrderInvoice(
                                context = context,
                                clinicName = settings.clinicName,
                                doctorName = settings.doctorName,
                                accent = accentColorInt,
                                currency = currency,
                                order = order,
                                dateFmt = { viewModel.formatDate(it) },
                                amountFmt = { formatAmount(it) }
                            )
                            sharePdfToWhatsApp(context, file)
                            printStatusText = if (isPersian) "📄 فاکتور آماده اشتراک‌گذاری شد" else "Invoice ready to share"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "اشتراک/واتساپ" else "Share")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            }
        },
        confirmButton = {}
    )
}
