package com.example.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.*

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.*
import com.example.reports.*
import com.example.ui.DentalViewModel
import com.example.ui.SmsParseResult
import com.example.ui.theme.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import java.io.File
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.ui.RadiographyScanResult
import com.example.ui.RadiographyFinding
import com.example.ui.GeminiClient

// ==========================================
fun formatToothLabel(fdiNumber: String, system: String): String {
    return when (system) {
        "UNIVERSAL" -> {
            when (fdiNumber) {
                "18" -> "1"; "17" -> "2"; "16" -> "3"; "15" -> "4"; "14" -> "5"; "13" -> "6"; "12" -> "7"; "11" -> "8"
                "21" -> "9"; "22" -> "10"; "23" -> "11"; "24" -> "12"; "25" -> "13"; "26" -> "14"; "27" -> "15"; "28" -> "16"
                "31" -> "17"; "32" -> "18"; "33" -> "19"; "34" -> "20"; "35" -> "21"; "36" -> "22"; "37" -> "23"; "38" -> "24"
                "48" -> "32"; "47" -> "31"; "46" -> "30"; "45" -> "29"; "44" -> "28"; "43" -> "27"; "42" -> "26"; "41" -> "25"
                else -> fdiNumber
            }
        }
        "PALMER" -> {
            when (fdiNumber) {
                "18" -> "8┘"; "17" -> "7┘"; "16" -> "6┘"; "15" -> "5┘"; "14" -> "4┘"; "13" -> "3┘"; "12" -> "2┘"; "11" -> "1┘"
                "21" -> "└1"; "22" -> "└2"; "23" -> "└3"; "24" -> "└4"; "25" -> "└5"; "26" -> "└6"; "27" -> "└7"; "28" -> "└8"
                "31" -> "┌1"; "32" -> "┌2"; "33" -> "┌3"; "34" -> "┌4"; "35" -> "┌5"; "36" -> "┌6"; "37" -> "┌7"; "38" -> "┌8"
                "48" -> "8┐"; "47" -> "7┐"; "46" -> "6┐"; "45" -> "5┐"; "44" -> "4┐"; "43" -> "3┐"; "42" -> "2┐"; "41" -> "1┐"
                else -> fdiNumber
            }
        }
        else -> fdiNumber
    }
}

// 1. INTERACTIVE DENTAL CHART (32 TEETH)
// ==========================================
@Composable
fun InteractiveToothChart(
    selectedTeeth: String,
    onTeethChanged: (String) -> Unit,
    system: String = "FDI",
    toothStatusMap: Map<String, String> = emptyMap() // Map of tooth number -> treatment type label
) {
    val selectedList = remember(selectedTeeth) {
        if (selectedTeeth.isBlank()) emptyList() else selectedTeeth.split(",").map { it.trim() }
    }

    // Standard FDI 32 tooth numbering:
    val upperRight = listOf("18", "17", "16", "15", "14", "13", "12", "11")
    val upperLeft = listOf("21", "22", "23", "24", "25", "26", "27", "28")
    val lowerLeft = listOf("31", "32", "33", "34", "35", "36", "37", "38")
    val lowerRight = listOf("48", "47", "46", "45", "44", "43", "42", "41")

    fun toggleTooth(number: String) {
        val newList = if (selectedList.contains(number)) {
            selectedList - number
        } else {
            selectedList + number
        }
        onTeethChanged(newList.joinToString(","))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "چارت دندانی تعاملی ۳ بعدی و تفکیک وضعیت",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "استاندارد: $system",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Legend Bar for Tooth Color Coding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToothLegendItem(color = Color(0xFFE53935), label = "عصب‌کشی (🔴)")
                ToothLegendItem(color = Color(0xFF1E88E5), label = "پرکردن (🔵)")
                ToothLegendItem(color = Color(0xFFFFB300), label = "روکش (🟡)")
                ToothLegendItem(color = Color(0xFF546E7A), label = "ایمپلنت (🩶)")
                ToothLegendItem(color = Color(0xFF43A047), label = "کشیدن/جرم‌گیری (🟢)")
            }

                        // Upper Arch (Maxillary)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "فک بالا (Maxillary)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val upperArch = listOf("18", "17", "16", "15", "14", "13", "12", "11", "21", "22", "23", "24", "25", "26", "27", "28")
                    upperArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (d * 3.5f - 24.0f).dp
                        Box(modifier = Modifier.offset(y = yOff)) {
                            ToothButton(
                                number = toothNum,
                                displayLabel = formatToothLabel(toothNum, system),
                                isSelected = selectedList.contains(toothNum),
                                statusType = toothStatusMap[toothNum],
                                onClick = { toggleTooth(toothNum) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        // Lower Arch (Mandibular)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "فک پایین (Mandibular)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lowerArch = listOf("48", "47", "46", "45", "44", "43", "42", "41", "31", "32", "33", "34", "35", "36", "37", "38")
                    lowerArch.forEachIndexed { index, toothNum ->
                        val d = Math.abs(index - 7.5f)
                        val yOff = (24.0f - d * 3.5f).dp
                        Box(modifier = Modifier.offset(y = yOff)) {
                            ToothButton(
                                number = toothNum,
                                displayLabel = formatToothLabel(toothNum, system),
                                isSelected = selectedList.contains(toothNum),
                                statusType = toothStatusMap[toothNum],
                                onClick = { toggleTooth(toothNum) }
                            )
                        }
                    }
                }
            }

            Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val formattedSelected = selectedList.sorted().map { formatToothLabel(it, system) }.joinToString(", ")
                    Text(
                        text = "دندان‌های انتخاب شده ($system): $formattedSelected",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

@Composable
fun ToothLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToothButton(
    number: String,
    displayLabel: String = number,
    isSelected: Boolean,
    statusType: String? = null,
    onClick: () -> Unit
) {
    val statusColor = remember(statusType) {
        when {
            statusType == null -> null
            statusType.contains("عصب") || statusType.contains("root") || statusType.contains("rct", ignoreCase = true) -> Color(0xFFE53935)
            statusType.contains("پر") || statusType.contains("ترمیم") || statusType.contains("fill", ignoreCase = true) -> Color(0xFF1E88E5)
            statusType.contains("روکش") || statusType.contains("پروتز") || statusType.contains("crown", ignoreCase = true) -> Color(0xFFFFB300)
            statusType.contains("ایمپلنت") || statusType.contains("implant", ignoreCase = true) -> Color(0xFF546E7A)
            statusType.contains("کشیدن") || statusType.contains("جرم") || statusType.contains("clean", ignoreCase = true) -> Color(0xFF43A047)
            else -> Color(0xFF8E24AA)
        }
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        statusColor != null -> statusColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        statusColor != null -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected && statusColor != null) 2.dp else 0.dp,
                color = if (isSelected && statusColor != null) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ==========================================
// 2. CLINIC FINANCIAL GRADIENT CHART
// ==========================================
@Composable
fun ClinicFinancialGradientChart(
    revenue: Long,
    outstanding: Long,
    expenses: Long,
    isPersian: Boolean
) {
    val maxVal = maxOf(revenue, outstanding, expenses, 1_000_000L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersian) "آنالیز و نمودار پویای مالی مطب" else "Dynamic Financial Dashboard Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            // Visual Bars
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientChartBar(
                    label = if (isPersian) "کل دریافتی مطب (وصول شده)" else "Total Income (Received)",
                    value = revenue,
                    maxVal = maxVal,
                    colors = listOf(Color(0xFF4CAF50), Color(0xFF388E3C))
                )

                GradientChartBar(
                    label = if (isPersian) "کل مطالبات بیماران (بدهکاری‌ها)" else "Outstanding Patient Balances",
                    value = outstanding,
                    maxVal = maxVal,
                    colors = listOf(Color(0xFFE57373), Color(0xFFD32F2F))
                )

                GradientChartBar(
                    label = if (isPersian) "هزینه‌های جاری مطب (تدارکات و قبوض)" else "Current Expenses (Materials & Bills)",
                    value = expenses,
                    maxVal = maxVal,
                    colors = listOf(Color(0xFFFFB74D), Color(0xFFF57C00))
                )
            }
        }
    }
}

@Composable
fun GradientChartBar(
    label: String,
    value: Long,
    maxVal: Long,
    colors: List<Color>
) {
    val fraction = (value.toDouble() / maxVal.toDouble()).coerceIn(0.01, 1.0).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Text(text = "${String.format(java.util.Locale.US, "%,d", value)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                    )
            )
        }
    }
}

// ==========================================
// 4. TEMPLATES MANAGER SCREEN
// ==========================================
@Composable
fun TemplatesManagerScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val templates by viewModel.templates.collectAsState()
    val currencyUnit by viewModel.currencyUnit.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<com.example.data.TreatmentTemplate?>(null) }
    var protocolTitleToShow by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var stageList by remember { mutableStateOf(mutableStateListOf<String>()) }
    var newStageInput by remember { mutableStateOf("") }
    var isStarredDraft by remember { mutableStateOf(true) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (isPersian) "مدیریت الگوهای آماده درمان" else "Manage Treatment Templates",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = { 
                    title = ""
                    costStr = ""
                    notes = ""
                    stageList.clear()
                    newStageInput = ""
                    isStarredDraft = true
                    showAddDialog = true 
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (isPersian) "الگوی جدید" else "New Template", fontWeight = FontWeight.Bold)
            }
        }

        if (templates.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = if (isPersian) "هیچ الگوی آماده‌ای ثبت نشده است" else "No treatment templates declared yet", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(templates) { temp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (temp.isStarred) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = temp.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.threeSecondLongPress(
                                                onLongClick = {
                                                    protocolTitleToShow = temp.title
                                                }
                                            )
                                        )
                                    }
                                    if (temp.defaultNotes.isNotBlank()) {
                                        Text(text = temp.defaultNotes, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = "${formatAmount(temp.defaultCost)} $currencyUnit",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 13.sp
                                    )
                                    IconButton(onClick = { viewModel.toggleTemplateStar(temp) }) {
                                        Icon(
                                            imageVector = if (temp.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Star",
                                            tint = if (temp.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingTemplate = temp
                                            title = temp.title
                                            costStr = temp.defaultCost.toString()
                                            notes = temp.defaultNotes
                                            isStarredDraft = temp.isStarred
                                            stageList.clear()
                                            stageList.addAll(temp.stages.split(" - ").filter { it.isNotBlank() })
                                            newStageInput = ""
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Template", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            // Dynamic Stage Steps Timeline
                            val stageItems = temp.stages.split(" - ").filter { it.isNotBlank() }
                            if (stageItems.isNotEmpty()) {
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Text(
                                    text = if (isPersian) "مراحل و گام‌های درمان (تراپی):" else "Therapy Steps & Workflow:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    stageItems.forEachIndexed { idx, stageText ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = stageText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
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
    }

    // Protocol knowledge dialog on long-press
    protocolTitleToShow?.let { pTitle ->
        ClinicalProtocolDialog(
            therapyTitle = pTitle,
            isPersian = isPersian,
            onDismiss = { protocolTitleToShow = null }
        )
    }

    // Edit Existing Template Dialog
    editingTemplate?.let { activeTemp ->
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPersian) "ویرایش الگوی درمان" else "Edit Treatment Template", fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        viewModel.deleteTemplate(activeTemp)
                        editingTemplate = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Template", tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isPersian) "تراپی مهم (نمایش در منوهای سریع):" else "Starred / Important Therapy:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = isStarredDraft,
                                onCheckedChange = { isStarredDraft = it }
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(if (isPersian) "عنوان درمان" else "Treatment Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = costStr,
                            onValueChange = { costStr = it },
                            label = { Text(if (isPersian) "هزینه پیش‌فرض ($currencyUnit)" else "Default Cost ($currencyUnit)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(if (isPersian) "توضیحات و جزئیات خدمت" else "Details / Notes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = if (isPersian) "مراحل و گام‌های درمان:" else "Define Therapy Steps:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    itemsIndexed(stageList) { idx, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "جلسه ${idx + 1}: $step",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { stageList.removeAt(idx) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newStageInput,
                                onValueChange = { newStageInput = it },
                                label = { Text(if (isPersian) "عنوان مرحله جدید" else "New Step Title") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (newStageInput.isNotBlank()) {
                                        stageList.add(newStageInput.trim())
                                        newStageInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isPersian) "افزودن" else "Add")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cost = costStr.toEnglishLong()
                        if (title.isNotEmpty() && cost >= 0) {
                            val compiledStages = stageList.joinToString(" - ")
                            val updated = activeTemp.copy(
                                title = title,
                                defaultCost = cost,
                                defaultNotes = notes,
                                stages = compiledStages,
                                isStarred = isStarredDraft
                            )
                            viewModel.updateTemplate(updated)
                            editingTemplate = null
                        }
                    }
                ) {
                    Text(if (isPersian) "بروزرسانی تغییرات" else "Update Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplate = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (isPersian) "تعریف طرح و مراحل درمان" else "Add Treatment Template & Steps", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(if (isPersian) "عنوان درمان (مثلاً روکش زیرکونیا)" else "Treatment Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = costStr,
                            onValueChange = { costStr = it },
                            label = { Text(if (isPersian) "هزینه پیش‌فرض ($currencyUnit)" else "Default Cost ($currencyUnit)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(if (isPersian) "توضیحات و جزئیات خدمت" else "Details / Notes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = if (isPersian) "تعریف دستی مراحل تراپی و درمان:" else "Define Therapy Steps Manually:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Dynamic list of added steps
                    itemsIndexed(stageList) { idx, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "جلسه ${idx + 1}: $step",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { stageList.removeAt(idx) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newStageInput,
                                onValueChange = { newStageInput = it },
                                label = { Text(if (isPersian) "عنوان مرحله جدید (مثلا: قالب‌گیری)" else "New Step Title") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (newStageInput.isNotBlank()) {
                                        stageList.add(newStageInput.trim())
                                        newStageInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isPersian) "افزودن" else "Add")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cost = costStr.toEnglishLong()
                        if (title.isNotEmpty() && cost >= 0) {
                            val compiledStages = stageList.joinToString(" - ")
                            viewModel.addTemplate(title, cost, notes, compiledStages)
                            showAddDialog = false
                            title = ""
                            costStr = ""
                            notes = ""
                            stageList.clear()
                        }
                    }
                ) {
                    Text(if (isPersian) "ذخیره الگوی کامل" else "Save Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}


// ==========================================
@Composable
fun ClinicalDocumentsTab(
    patient: Patient,
    viewModel: DentalViewModel,
    isPersian: Boolean
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showBeforeAfterDialog by remember { mutableStateOf(false) }
    var activeAiScanResult by remember { mutableStateOf<RadiographyScanResult?>(null) }
    var isAnalyzingRadiograph by remember { mutableStateOf(false) }
    var activePostCareText by remember { mutableStateOf<String?>(null) }
    var isGeneratingPostCare by remember { mutableStateOf(false) }

    var selectedScannerTemplate by remember { mutableStateOf("OPG Panoramic Scan") }
    var noteInput by remember { mutableStateOf("") }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val docList = remember(patient.documentUris) {
        if (patient.documentUris.isBlank()) emptyList() else patient.documentUris.split(",")
    }

    val templates = listOf(
        "OPG Panoramic Scan",
        "Intraoral Camera Shot",
        "Bitewing Left Molar X-Ray",
        "Bitewing Right Canine Scan",
        "Cone Beam Computed Tomography (CBCT)"
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val b64 = saveUriToLocalFile(context, it)
            if (b64 != null) {
                selectedImageBase64 = b64
            }
        }
    }

    val launchCamera = rememberSafeCameraLauncher(context) { path ->
        selectedImageBase64 = path
    }

    fun handleAddScan() {
        val imgStr = selectedImageBase64 ?: ""
        val scanDescriptor = "$selectedScannerTemplate|${System.currentTimeMillis()}|$noteInput|$imgStr"
        val newList = docList + scanDescriptor
        viewModel.updatePatient(patient.copy(documentUris = newList.joinToString(",")))
        showAddDialog = false
        noteInput = ""
        selectedImageBase64 = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isPersian) "رادیوگرافی‌ها، رضایت‌نامه‌ها و مدارک بالینی" else "Clinical Radiographs & Records Store",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "ثبت تصویر" else "Add Scan", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showConsentDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "امضای رضایت" else "Consent", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showBeforeAfterDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "قبل و بعد" else "Compare", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        if (docList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.LightGray)
                    Text(text = if (isPersian) "هیچ رادیوگرافی یا امضایی ثبت نشده است" else "No imaging records attached to file", color = Color.Gray, fontSize = 11.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                docList.forEach { doc ->
                    val parts = doc.split("|")
                    val scanName = parts.getOrNull(0) ?: "Scan"
                    val timestamp = parts.getOrNull(1)?.toEnglishLongOrNull() ?: System.currentTimeMillis()
                    val scanNote = parts.getOrNull(2) ?: ""
                    val base64Image = parts.getOrNull(3)

                    val bitmap = remember(base64Image) {
                        if (!base64Image.isNullOrBlank()) base64ToBitmap(base64Image) else null
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF07141C))
                                    .clickable { if (!base64Image.isNullOrBlank()) viewModel.currentViewingImage.value = base64Image },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Radiography Scan",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(0f, h * 0.7f)
                                            quadraticTo(w * 0.25f, h * 0.65f, w * 0.5f, h * 0.75f)
                                            quadraticTo(w * 0.75f, h * 0.8f, w, h * 0.7f)
                                            lineTo(w, h)
                                            lineTo(0f, h)
                                            close()
                                        }
                                        drawPath(path, Color.White.copy(alpha = 0.15f))
                                        for (i in 2..8) {
                                            val cx = w * (i * 0.11f)
                                            val cy = h * 0.5f
                                            drawCircle(Color.White.copy(alpha = 0.2f), radius = 18f, center = androidx.compose.ui.geometry.Offset(cx, cy))
                                            drawLine(Color.White.copy(alpha = 0.35f), start = androidx.compose.ui.geometry.Offset(cx, cy), end = androidx.compose.ui.geometry.Offset(cx, cy + 28f), strokeWidth = 3f)
                                        }
                                    }
                                    Text(
                                        text = "SCAN_SAMPLE_SECURED_XRAY",
                                        fontSize = 9.sp,
                                        color = Color.Green.copy(alpha = 0.4f),
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                    )
                                    Icon(
                                        Icons.Default.HealthAndSafety,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(50.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = scanName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = viewModel.formatDate(timestamp), fontSize = 10.sp, color = Color.Gray)
                                    if (scanNote.isNotEmpty()) {
                                        Text(text = if (isPersian) "یادداشت: $scanNote" else "Notes: $scanNote", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val remaining = docList - doc
                                        viewModel.updatePatient(patient.copy(documentUris = remaining.joinToString(",")))
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                }
                            }

                            // AI & Action buttons for document/radiograph
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!base64Image.isNullOrBlank()) {
                                    Button(
                                        onClick = {
                                            isAnalyzingRadiograph = true
                                            viewModel.analyzeRadiographyScan(base64Image) { success, result ->
                                                isAnalyzingRadiograph = false
                                                if (success && result != null) {
                                                    activeAiScanResult = result
                                                } else {
                                                    android.widget.Toast.makeText(context, "خطا در اتصال به Gemini یا تحلیل عکس", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isAnalyzingRadiograph,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isAnalyzingRadiograph) "در حال تحلیل..." else "🤖 تحلیل AI",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        isGeneratingPostCare = true
                                        viewModel.generatePostTreatmentCare(patient.name, scanName, "", scanNote) { success, care ->
                                            isGeneratingPostCare = false
                                            if (success && care != null) {
                                                activePostCareText = care
                                            } else {
                                                android.widget.Toast.makeText(context, "خطا در تولید دستورالعمل مراقبت", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isGeneratingPostCare,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isGeneratingPostCare) "تولید..." else "💬 مراقبت درمان", fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConsentDialog) {
        DigitalConsentSignatureDialog(
            patient = patient,
            isPersian = isPersian,
            onDismiss = { showConsentDialog = false },
            onSaveSignature = { templateTitle, b64Sig ->
                val recordDescriptor = "امضای رضایتنامه: $templateTitle|${System.currentTimeMillis()}|امضا شده توسط بیمار|$b64Sig"
                val newList = docList + recordDescriptor
                viewModel.updatePatient(patient.copy(documentUris = newList.joinToString(",")))
                showConsentDialog = false
            }
        )
    }

    if (showBeforeAfterDialog) {
        BeforeAfterComparisonDialog(
            docList = docList,
            isPersian = isPersian,
            onDismiss = { showBeforeAfterDialog = false }
        )
    }

    if (activeAiScanResult != null) {
        AiRadiographyScanDialog(
            scanResult = activeAiScanResult!!,
            isPersian = isPersian,
            onDismiss = { activeAiScanResult = null }
        )
    }

    if (activePostCareText != null) {
        AiPostTreatmentCareDialog(
            patientName = patient.name,
            patientPhone = patient.phone,
            initialCareText = activePostCareText!!,
            isPersian = isPersian,
            onDismiss = { activePostCareText = null }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (isPersian) "اسکن رادیوگرافی و تصویربرداری پرونده" else "Capture Clinical Radiography Scan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = if (isPersian) "نوع تصویر یا اسکن دندانپزشکی را انتخاب کنید:" else "Choose Scan/Capture Modality Template:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        templates.forEach { temp ->
                            InputChip(
                                selected = selectedScannerTemplate == temp,
                                onClick = { selectedScannerTemplate = temp },
                                label = { Text(temp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text(if (isPersian) "یادداشت تشخیصی پزشک" else "Diagnostic notes / report") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(text = if (isPersian) "انتخاب منبع تصویر مدرک:" else "Choose Document Photo Source:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "دوربین عکاسی" else "Camera", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "انتخاب از گالری" else "Gallery", fontSize = 11.sp)
                        }
                    }

                    selectedImageBase64?.let { b64 ->
                        val previewBitmap = remember(b64) { base64ToBitmap(b64) }
                        if (previewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = if (isPersian) "تصویر انتخاب شد" else "Image selected",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(4.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { handleAddScan() }) {
                    Text(if (isPersian) "ثبت در پرونده" else "Record & Store File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

// ==========================================
// 7. ERP CLINIC SETTINGS PANEL (DEDICATED HUB)
// ==========================================
@Composable
fun ClinicSettingsScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val advancedSettingsRevealed by viewModel.advancedSettingsRevealed.collectAsState()
    var showHideAdvancedConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Export/Import — الگوهای آماده درمان و سرفصل‌های شخصی (CSV، قابل ویرایش در اکسل/ویندوز/تبلت)
    var showImportTemplatesPicker by remember { mutableStateOf(false) }
    var showImportSubAccountsPicker by remember { mutableStateOf(false) }
    var pendingImportedTemplates by remember { mutableStateOf<List<TreatmentTemplate>?>(null) }
    var pendingImportedSubAccounts by remember { mutableStateOf<List<PersonalSubAccount>?>(null) }

    val importTemplatesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val parsed = readTemplatesFromCsvUri(context, uri)
            if (parsed != null) pendingImportedTemplates = parsed
            else android.widget.Toast.makeText(context, if (isPersian) "فایل CSV نامعتبر است" else "Invalid CSV file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val importSubAccountsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val parsed = readSubAccountsFromCsvUri(context, uri)
            if (parsed != null) pendingImportedSubAccounts = parsed
            else android.widget.Toast.makeText(context, if (isPersian) "فایل CSV نامعتبر است" else "Invalid CSV file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(showImportTemplatesPicker) {
        if (showImportTemplatesPicker) { importTemplatesLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "*/*")); showImportTemplatesPicker = false }
    }
    LaunchedEffect(showImportSubAccountsPicker) {
        if (showImportSubAccountsPicker) { importSubAccountsLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "*/*")); showImportSubAccountsPicker = false }
    }
    val currencyUnit by viewModel.currencyUnit.collectAsState()
    val isPinSecurityEnabled by viewModel.isPinSecurityEnabled.collectAsState()
    val securityPinCode by viewModel.securityPinCode.collectAsState()
    val autoBackupInterval by viewModel.autoBackupInterval.collectAsState()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsState()
    val dentalChartSystem by viewModel.dentalChartSystem.collectAsState()
    val calendarType by viewModel.calendarType.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var clinicName by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("") }
    var staffNames by remember { mutableStateOf("") }
    var showSummary by remember { mutableStateOf(true) }
    var showQuick by remember { mutableStateOf(true) }
    var showCharts by remember { mutableStateOf(true) }

    var newPinInput by remember { mutableStateOf(securityPinCode) }
    var backupJsonPreview by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var restoreStatusMsg by remember { mutableStateOf<String?>(null) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        clinicName = settings.clinicName
        doctorName = settings.doctorName
        staffNames = settings.staffNames
        showSummary = settings.showSummaryCard
        showQuick = settings.showQuickActions
        showCharts = settings.showCharts
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (advancedSettingsRevealed) showHideAdvancedConfirm = true else onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (isPersian) "تنظیمات اختصاصی نرم‌افزار مطب" else "Dedicated Clinic App Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showHideAdvancedConfirm) {
            AlertDialog(
                onDismissRequest = { showHideAdvancedConfirm = false },
                title = { Text(if (isPersian) "گزینه‌های اختصاصی دوباره مخفی شوند؟" else "Hide advanced options again?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (isPersian) "برای دسترسی دوباره باید ۲ ثانیه روی چرخ‌دنده لمس نگه دارید."
                        else "You'll need to hold the settings icon for 2 seconds to see them again.",
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.advancedSettingsRevealed.value = false
                        showHideAdvancedConfirm = false
                        onBack()
                    }) { Text(if (isPersian) "بله، مخفی شود" else "Yes, hide") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showHideAdvancedConfirm = false
                        onBack()
                    }) { Text(if (isPersian) "خیر، باز بماند" else "No, keep visible") }
                }
            )
        }

        if (pendingImportedTemplates != null) {
            val importedList = pendingImportedTemplates!!
            AlertDialog(
                onDismissRequest = { pendingImportedTemplates = null },
                title = { Text(if (isPersian) "ورودی الگوهای درمان" else "Import Templates", fontWeight = FontWeight.Bold) },
                text = { Text(if (isPersian) "${importedList.size} الگو در فایل پیدا شد. آیا به لیست فعلی اضافه شود یا کل لیست فعلی جایگزین شود؟" else "${importedList.size} templates found. Add to the current list, or replace it entirely?") },
                confirmButton = {
                    Button(onClick = {
                        importedList.forEach { viewModel.addTemplate(it.title, it.defaultCost, it.defaultNotes, it.stages, it.isStarred) }
                        pendingImportedTemplates = null
                    }) { Text(if (isPersian) "افزودن" else "Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.templates.value.forEach { viewModel.deleteTemplate(it) }
                            importedList.forEach { viewModel.addTemplate(it.title, it.defaultCost, it.defaultNotes, it.stages, it.isStarred) }
                            pendingImportedTemplates = null
                        }) { Text(if (isPersian) "جایگزینی کامل" else "Replace All", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { pendingImportedTemplates = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
                    }
                }
            )
        }

        if (pendingImportedSubAccounts != null) {
            val importedList = pendingImportedSubAccounts!!
            AlertDialog(
                onDismissRequest = { pendingImportedSubAccounts = null },
                title = { Text(if (isPersian) "ورودی سرفصل‌های شخصی" else "Import Sub-Accounts", fontWeight = FontWeight.Bold) },
                text = { Text(if (isPersian) "${importedList.size} سرفصل در فایل پیدا شد. آیا به لیست فعلی اضافه شود یا کل لیست فعلی جایگزین شود؟" else "${importedList.size} sub-accounts found. Add to the current list, or replace it entirely?") },
                confirmButton = {
                    Button(onClick = {
                        importedList.forEach { viewModel.addPersonalSubAccount(it.name, it.category) }
                        pendingImportedSubAccounts = null
                    }) { Text(if (isPersian) "افزودن" else "Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.personalSubAccounts.value.forEach { viewModel.deletePersonalSubAccount(it) }
                            importedList.forEach { viewModel.addPersonalSubAccount(it.name, it.category) }
                            pendingImportedSubAccounts = null
                        }) { Text(if (isPersian) "جایگزینی کامل" else "Replace All", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { pendingImportedSubAccounts = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ----------------------------------------------------
            // SECTION 1: LANGUAGE & CURRENCY
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "تنظیمات زبان و واحد پولی" else "Language & Currency Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Language Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (isPersian) "زبان برنامه (Persian / English)" else "App Language", fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = isPersian,
                                    onClick = { if (!isPersian) viewModel.toggleLanguage() },
                                    label = { Text("فارسی", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = !isPersian,
                                    onClick = { if (isPersian) viewModel.toggleLanguage() },
                                    label = { Text("English", fontSize = 11.sp) }
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Currency Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (isPersian) "واحد پولی نمایش مبالغ" else "Display Currency Unit", fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("تومان", "ریال", "دلار ($)").forEach { unit ->
                                    FilterChip(
                                        selected = currencyUnit == unit,
                                        onClick = { viewModel.setCurrencyUnit(unit) },
                                        label = { Text(unit, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Calendar Type Selector (Shamsi, Qamari, Miladi)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "نظام تقویم و نمایش تاریخ‌ها در سیستم" else "Calendar System & Date Display Standard",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "SHAMSI" to "هجری شمسی",
                                    "QAMARI" to "هجری قمری",
                                    "MILADI" to "میلادی"
                                ).forEach { (calKey, calLabel) ->
                                    ElevatedFilterChip(
                                        selected = calendarType == calKey,
                                        onClick = { viewModel.setCalendarType(calKey) },
                                        label = { Text(if (isPersian) calLabel else calKey, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // SECTION 1.5: APPEARANCE — NEON THEME PICKER
            // ----------------------------------------------------
            item {
                val activeThemeId = settings.themeName
                val activePalette = neonPaletteById(activeThemeId)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, activePalette.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "ظاهر و تم نئونی برنامه" else "App Appearance & Neon Theme",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (isPersian) "یک تم نئونی را برای رنگ‌بندی کامل برنامه انتخاب کنید. تغییر بلافاصله اعمال می‌شود."
                                   else "Pick a neon theme to recolor the whole app. Changes apply instantly.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AllNeonPalettes.forEach { palette ->
                                val isSelected = palette.id == activeThemeId
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(76.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (isSelected) Modifier.neonBorder(palette.glow, RoundedCornerShape(14.dp))
                                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                        )
                                        .background(palette.background)
                                        .clickable { viewModel.updateTheme(palette.id) }
                                        .padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                brush = Brush.sweepGradient(
                                                    palette.swatchDots + palette.swatchDots.first()
                                                )
                                            )
                                            .then(
                                                if (isSelected) Modifier.neonGlow(palette.glow, CircleShape, elevation = 8.dp)
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(palette.background),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = palette.glow,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isPersian) palette.labelFa else palette.labelEn,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) palette.glow else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = activePalette.primary.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPersian) "حالت تیره" else "Dark Mode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPersian) "مستقل از تم رنگی — می‌توانید حالت روشن را نیز انتخاب کنید" else "Independent of the color theme",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                            Switch(
                                checked = settings.isDarkMode,
                                onCheckedChange = { viewModel.setDarkMode(it) }
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // SECTION 2: SECURITY & LOGIN PROTECTION
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "تنظیمات امنیتی و ورود به برنامه" else "Security & App Lock Protection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = if (isPersian) "فعال‌سازی قفل ورود (پین / بیومتریک)" else "Enable Security Lock (PIN/Biometric)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(text = if (isPersian) "در هنگام ورود به برنامه رمز یا اثرانگشت خواسته می‌شود" else "Require authentication on launch", fontSize = 10.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isPinSecurityEnabled,
                                onCheckedChange = { viewModel.setPinSecurityEnabled(it, newPinInput) }
                            )
                        }

                        if (isPinSecurityEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPinInput,
                                    onValueChange = {
                                        if (it.length <= 4) {
                                            newPinInput = it
                                            viewModel.setPinSecurityEnabled(true, it)
                                        }
                                    },
                                    label = { Text(if (isPersian) "رمز ورود ۴ رقمی" else "4-Digit PIN") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { viewModel.lockApp() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isPersian) "قفل الان" else "Lock Now", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // SECTION 3: BACKUP & RESTORE
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "پشتیبان‌گیری، بازگردانی و اتوبکاپ" else "Backup, Restore & Auto-Backup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (isPersian) "زمان‌بندی اتوبکاپ:" else "Auto-Backup Interval:", fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                mapOf("DAILY" to "روزانه", "WEEKLY" to "هفتگی", "MONTHLY" to "ماهانه", "DISABLED" to "غیرفعال").forEach { (key, title) ->
                                    FilterChip(
                                        selected = autoBackupInterval == key,
                                        onClick = { viewModel.setAutoBackupInterval(key) },
                                        label = { Text(if (isPersian) title else key, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val json = viewModel.generateBackupJson()
                                        backupJsonPreview = json
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPersian) "ایجاد فایل بک‌آپ" else "Export Backup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    restoreJsonInput = ""
                                    restoreStatusMsg = null
                                    showRestoreDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPersian) "بازگردانی فایل بک‌آپ" else "Restore Backup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (lastBackupTimestamp > 0) {
                            Text(
                                text = if (isPersian) "آخرین بک‌آپ موفق: ${viewModel.formatDate(lastBackupTimestamp)}" else "Last Backup: ${viewModel.formatDate(lastBackupTimestamp)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // SECTION 4: CLINIC BRANDING & DASHBOARD CUSTOMIZATION
            // (فقط با لمس ۲ثانیه‌ای چرخ‌دنده — بخشی از «گزینه‌های بیشتر تنظیمات»)
            // ----------------------------------------------------
            if (advancedSettingsRevealed) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "اطلاعات مطب و چیدمان داشبورد" else "Clinic Info & Dashboard Preferences",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = clinicName,
                            onValueChange = { clinicName = it },
                            label = { Text(if (isPersian) "نام مطب / کلینیک" else "Clinic Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = doctorName,
                            onValueChange = { doctorName = it },
                            label = { Text(if (isPersian) "نام پزشک معالج" else "Dentist Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = staffNames,
                            onValueChange = { staffNames = it },
                            label = { Text(if (isPersian) "نام و مشخصات پرسنل مطب" else "Staff & Assistants") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isPersian) "نمایش کارت خلاصه وضعیت مالی" else "Display Financial Summary Card", fontSize = 11.sp)
                            Switch(checked = showSummary, onCheckedChange = { showSummary = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isPersian) "نمایش کلیدهای ثبت سریع" else "Display Quick Shortcuts Row", fontSize = 11.sp)
                            Switch(checked = showQuick, onCheckedChange = { showQuick = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isPersian) "نمایش نمودارهای تحلیلی مالی" else "Display Data Analytical Charts", fontSize = 11.sp)
                            Switch(checked = showCharts, onCheckedChange = { showCharts = it })
                        }
                    }
                }
            }
            }

            // ----------------------------------------------------
            // SECTION 5: DENTAL CHART NOTATION STANDARD (ILLUSTRATED)
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "استاندارد شماره‌گذاری چارت دندان" else "Dental Chart Notation System",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = if (isPersian) "سیستم شماره‌گذاری دندان‌ها را برای نمایش در پرونده بیمار و چارت تعاملی انتخاب کنید:" else "Select dental chart numbering system:",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        // Selector Chips for FDI, Universal, Palmer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("FDI" to "بین‌المللی (FDI)", "UNIVERSAL" to "یونیورسال (Universal)", "PALMER" to "پالمر (Palmer)").forEach { (sysKey, labelStr) ->
                                ElevatedFilterChip(
                                    selected = dentalChartSystem == sysKey,
                                    onClick = { viewModel.setDentalChartSystem(sysKey) },
                                    label = { Text(if (isPersian) labelStr else sysKey, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Visual Illustrated Preview Diagrams for all 3 Systems
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isPersian) "نمای مصور سیستم انتخاب شده ($dentalChartSystem):" else "Illustrated Preview ($dentalChartSystem):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                when (dentalChartSystem) {
                                    "UNIVERSAL" -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("فک بالا (۱ تا ۱۶): 1 .. 8 | 9 .. 16", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("1", "2", "3", "4", "5", "6", "7", "8", "|", "9", "10", "11", "12", "13", "14", "15", "16").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (d * 2.2f - 17.6f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                            Text("فک پایین (۱۷ تا ۳۲): 32 .. 25 | 24 .. 17", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("32", "31", "30", "29", "28", "27", "26", "25", "|", "24", "23", "22", "21", "20", "19", "18", "17").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (17.6f - d * 2.2f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "PALMER" -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("فک بالا ( Palmer Grid ┘ └ ): 8┘ .. 1┘ | └1 .. └8", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("8┘", "7┘", "6┘", "5┘", "4┘", "3┘", "2┘", "1┘", "|", "└1", "└2", "└3", "└4", "└5", "└6", "└7", "└8").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (d * 2.2f - 17.6f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                            Text("فک پایین ( Palmer Grid ┐ ┌ ): 8┐ .. 1┐ | ┌1 .. ┌8", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("8┐", "7┐", "6┐", "5┐", "4┐", "3┐", "2┐", "1┐", "|", "┌1", "┌2", "┌3", "┌4", "┌5", "┌6", "┌7", "┌8").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (17.6f - d * 2.2f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> { // FDI
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("فک بالا (رباعی ۱ و ۲): 18 .. 11 | 21 .. 28", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("18", "17", "16", "15", "14", "13", "12", "11", "|", "21", "22", "23", "24", "25", "26", "27", "28").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (d * 2.2f - 17.6f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                            Text("فک پایین (رباعی ۴ و ۳): 48 .. 41 | 31 .. 38", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                                listOf("48", "47", "46", "45", "44", "43", "42", "41", "|", "31", "32", "33", "34", "35", "36", "37", "38").forEachIndexed { index, num ->
                                                    val d = Math.abs(index - 8)
                                                    val yOff = (17.6f - d * 2.2f).dp
                                                    Surface(
                                                        modifier = Modifier.offset(y = yOff),
                                                        color = if (num == "|") Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(num, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
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
            }

            // ----------------------------------------------------
            // SECTION 5.5: تنظیمات اختصاصی — الگوهای درمان، درآمد ثابت، سایز فونت
            // (این بخش قبلاً در جاهای مختلف برنامه پخش بود؛ اکنون همگی اینجا و فقط با
            // لمس ۲ثانیه‌ای چرخ‌دنده بالای صفحه در دسترس است)
            // ----------------------------------------------------
            if (advancedSettingsRevealed) {
            item {
                var showFixedIncomeFromSettings by remember { mutableStateOf(false) }
                var showSubAccountsFromSettings by remember { mutableStateOf(false) }
                var fontScaleIdx by remember(settings.globalFontScaleIndex) { mutableIntStateOf(settings.globalFontScaleIndex) }
                var clinicalFontIdx by remember(settings.clinicalFontSizePt) { mutableIntStateOf((settings.clinicalFontSizePt - 8).coerceIn(0, 19)) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "تنظیمات اختصاصی نرم‌افزار مطب" else "Advanced App Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showFixedIncomeFromSettings = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "درآمد ثابت ماهانه" else "Fixed Monthly Income", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { viewModel.currentTab.value = "templates" }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "الگوهای آماده درمان" else "Treatment Templates", fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showSubAccountsFromSettings = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "سرفصل‌های شخصی" else "Personal Sub-Accounts", fontSize = 11.sp)
                            }
                        }

                        // Export/Import — الگوهای آماده درمان (یکجا)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { exportTemplatesToCsv(context, viewModel.templates.value) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "خروجی الگوهای درمان" else "Export Templates", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { showImportTemplatesPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "ورودی الگوهای درمان" else "Import Templates", fontSize = 10.sp)
                            }
                        }
                        // Export/Import — سرفصل‌های شخصی (یکجا)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { exportSubAccountsToCsv(context, viewModel.personalSubAccounts.value) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "خروجی سرفصل‌ها" else "Export Sub-Accounts", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { showImportSubAccountsPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "ورودی سرفصل‌ها" else "Import Sub-Accounts", fontSize = 10.sp)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isPersian) "سایز فونت کلی برنامه" else "App-wide Font Size",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPersian) "روی صفحات دستورالعمل بالینی اثر ندارد — آن‌ها سایز فونت اختصاصی خودشان را دارند."
                                       else "Does not affect the clinical guideline pages — those have their own font control.",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Slider(
                                value = fontScaleIdx.toFloat(),
                                onValueChange = {
                                    val newIdx = it.toInt()
                                    fontScaleIdx = newIdx
                                    viewModel.updateGlobalFontScale(newIdx)
                                },
                                valueRange = 0f..19f,
                                steps = 18
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isPersian) "سایز فونت صفحات دستورالعمل/شرح بالینی (۲۰ مرحله)" else "Clinical Guideline Pages Font Size (20 steps)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = clinicalFontIdx.toFloat(),
                                onValueChange = {
                                    val newIdx = it.toInt()
                                    clinicalFontIdx = newIdx
                                    viewModel.updateClinicalFontSize(newIdx + 8)
                                },
                                valueRange = 0f..19f,
                                steps = 18
                            )
                        }
                    }
                }

                if (showFixedIncomeFromSettings) {
                    ManageFixedIncomesDialog(viewModel = viewModel, isPersian = isPersian, onDismiss = { showFixedIncomeFromSettings = false })
                }
                if (showSubAccountsFromSettings) {
                    ManageSubAccountsDialog(viewModel = viewModel, isPersian = isPersian, onDismiss = { showSubAccountsFromSettings = false })
                }
            }
            }

            // ----------------------------------------------------
            // SECTION 6: پاکسازی تمام اطلاعات ورودی و دیتاها
            // (فقط با لمس ۲ثانیه‌ای چرخ‌دنده — بخشی از «گزینه‌های بیشتر تنظیمات»)
            // ----------------------------------------------------
            if (advancedSettingsRevealed) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = if (isPersian) "پاکسازی تمام اطلاعات ورودی و دیتاها" else "Clear All Input Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = if (isPersian) "حذف کامل کلیه بیماران فرضی، هزینه ها، فاکتورها، جلسات، درآمدهای آزمایشی جهت شروع به کار واقعی مطب"
                                   else "Purge all dummy patient profiles, treatments, payments, invoices, sessions and expenses.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { showClearDataConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPersian) "پاکسازی تمام اطلاعات ورودی و دیتاها" else "Clear All Input Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.updateSettings(
                            clinicName = clinicName,
                            doctorName = doctorName,
                            staffNames = staffNames,
                            showSummary = showSummary,
                            showQuick = showQuick,
                            showCharts = showCharts
                        )
                        if (advancedSettingsRevealed) showHideAdvancedConfirm = true else onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isPersian) "ذخیره تمامی تنظیمات مطب" else "Save All Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // BACKUP PREVIEW DIALOG
    if (backupJsonPreview != null) {
        AlertDialog(
            onDismissRequest = { backupJsonPreview = null },
            title = { Text(if (isPersian) "پشتیبان‌گیری کامل انجام شد" else "Backup Created Successfully", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) "فایل نسخه پشتیبان دیتابیس شامل بیماران، درمان‌ها، نوبت‌ها و فاکتورها آماده است:" else "Backup JSON data successfully serialized:",
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = backupJsonPreview ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { backupJsonPreview = null }) {
                    Text(if (isPersian) "تایید و بستن" else "OK")
                }
            }
        )
    }

    // RESTORE BACKUP DIALOG
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(if (isPersian) "بازگردانی دیتابیس از فایل بک‌آپ" else "Restore Database from Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) "متن فایل JSON بک‌آپ را در کادر زیر وارد کنید:" else "Paste backup JSON text content below:",
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    if (restoreStatusMsg != null) {
                        Text(
                            text = restoreStatusMsg!!,
                            color = if (restoreStatusMsg!!.contains("موفق")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.restoreFromBackupJson(restoreJsonInput)
                            if (success) {
                                restoreStatusMsg = if (isPersian) "اطلاعات با موفقیت بازگردانی شد." else "Database restored successfully!"
                            } else {
                                restoreStatusMsg = if (isPersian) "خطا در فرمت یا ساختار فایل JSON بک‌آپ." else "Invalid JSON format."
                            }
                        }
                    }
                ) {
                    Text(if (isPersian) "شروع بازگردانی" else "Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    // CONFIRM DATA CLEAR / DUMMY PURGE DIALOG
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text(if (isPersian) "تایید پاکسازی تمام اطلاعات ورودی و دیتاها" else "Confirm Clear All Input Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    if (isPersian) "آیا مطمئن هستید که می‌خواهید تمام بیماران، جلسات، فاکتورها، پرونده‌ها و هزینه‌های ثبت شده فرضی را حذف کرده و همه مبالغ را صفر کنید؟ این عمل غیرقابل بازگشت است."
                    else "Are you sure you want to delete all dummy patients, appointments, treatments, payments, invoices and zero out all values? This action cannot be undone.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllClinicalData()
                        showClearDataConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "بله، پاکسازی و صفر شود" else "Yes, Clear All Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}


// ==========================================
// 8. COGNITIVE CALENDAR SCHEDULER BOARD
// ==========================================
@Composable
fun InteractiveAppointmentsCalendar(
    viewModel: DentalViewModel,
    isPersian: Boolean
) {
    val appointments by viewModel.appointments.collectAsState()

    val todayCal = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
    }

    val rollingDays = remember(todayCal, isPersian) {
        (0..6).map { dayOffset ->
            val c = (todayCal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayOfWeekName = when (c.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SATURDAY -> if (isPersian) "شنبه" else "Sat"
                java.util.Calendar.SUNDAY -> if (isPersian) "یکشنبه" else "Sun"
                java.util.Calendar.MONDAY -> if (isPersian) "دوشنبه" else "Mon"
                java.util.Calendar.TUESDAY -> if (isPersian) "سه‌شنبه" else "Tue"
                java.util.Calendar.WEDNESDAY -> if (isPersian) "چهارشنبه" else "Wed"
                java.util.Calendar.THURSDAY -> if (isPersian) "پنج‌شنبه" else "Thu"
                java.util.Calendar.FRIDAY -> if (isPersian) "جمعه" else "Fri"
                else -> ""
            }
            val label = when (dayOffset) {
                0 -> if (isPersian) "امروز ($dayOfWeekName)" else "Today ($dayOfWeekName)"
                1 -> if (isPersian) "فردا ($dayOfWeekName)" else "Tomorrow ($dayOfWeekName)"
                else -> "$dayOfWeekName (${viewModel.formatDate(c.timeInMillis)})"
            }
            Triple(dayOffset, label, c)
        }
    }

    var selectedApptForReschedule by remember { mutableStateOf<Appointment?>(null) }
    var sessionApptToShow by remember { mutableStateOf<Appointment?>(null) }

    if (sessionApptToShow != null) {
        SessionDetailsDialog(
            appointment = sessionApptToShow!!,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { sessionApptToShow = null }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersian) "تقویم هفتگی و زمان‌بندی نوبت‌ها (از امروز)" else "Weekly Board (From Today)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isPersian) "۷ روز آینده" else "Next 7 Days",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (selectedApptForReschedule != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "جابجایی نوبت ${selectedApptForReschedule?.patientName}. یک روز را برای انتقال لمس کنید:"
                            else "Rescheduling ${selectedApptForReschedule?.patientName}. Tap any day row to move:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { selectedApptForReschedule = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // 7 Rolling Days from Today
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rollingDays.forEach { (_, dayLabel, dayCal) ->
                    val matchedAppts = appointments.filter { appt ->
                        val apptCal = java.util.Calendar.getInstance().apply { timeInMillis = appt.date }
                        apptCal.get(java.util.Calendar.YEAR) == dayCal.get(java.util.Calendar.YEAR) &&
                        apptCal.get(java.util.Calendar.DAY_OF_YEAR) == dayCal.get(java.util.Calendar.DAY_OF_YEAR)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedApptForReschedule != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .clickable {
                                val targetAppt = selectedApptForReschedule
                                if (targetAppt != null) {
                                    val apptCal = java.util.Calendar.getInstance().apply { timeInMillis = targetAppt.date }
                                    val newCal = (dayCal.clone() as java.util.Calendar).apply {
                                        set(java.util.Calendar.HOUR_OF_DAY, apptCal.get(java.util.Calendar.HOUR_OF_DAY))
                                        set(java.util.Calendar.MINUTE, apptCal.get(java.util.Calendar.MINUTE))
                                    }
                                    viewModel.deleteAppointment(targetAppt)
                                    viewModel.addAppointment(targetAppt.copy(date = newCal.timeInMillis))
                                    selectedApptForReschedule = null
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(110.dp)
                        )

                        if (matchedAppts.isEmpty()) {
                            Text(
                                text = if (isPersian) "بدون نوبت" else "Free Slot",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                matchedAppts.forEach { appt ->
                                    val timeStr = viewModel.formatTime(appt.date)
                                    val cleanTxType = if (appt.treatmentType != "Selected Treatments" && appt.treatmentType != "طرح های انتخابی" && appt.treatmentType.isNotBlank()) " • ${appt.treatmentType}" else ""
                                    
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { sessionApptToShow = appt }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "[$timeStr] ${appt.patientName}$cleanTxType",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            IconButton(
                                                onClick = { selectedApptForReschedule = appt },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Reschedule",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
        }
    }
}

// ==========================================
// 10. DYNAMIC BANK ACCOUNTS MANAGER
// ==========================================
@Composable
fun BankAccountsManagerScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var bankName by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf("") }
    var cardNo by remember { mutableStateOf("") }
    var accNo by remember { mutableStateOf("") }
    var balanceStr by remember { mutableStateOf("") }

    var accountToEdit by remember { mutableStateOf<BankAccount?>(null) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (isPersian) "مدیریت حساب‌ها و کارت‌های بانکی" else "Bank Cards & Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(if (isPersian) "افزودن کارت" else "New Card", fontWeight = FontWeight.Bold)
            }
        }


        if (bankAccounts.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = if (isPersian) "هیچ حساب بانکی اضافه نشده است" else "No bank accounts found", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bankAccounts) { account ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column {
                                        Text(text = account.bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "صاحب کارت: ${account.cardHolder}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                IconButton(onClick = { accountToEdit = account }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Account", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "شماره کارت: ${account.cardNumber}", fontSize = 11.sp, color = Color.Gray)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = if (isPersian) "موجودی حساب:" else "Available Balance:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = "${formatAmount(account.balance)} تومان",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF388E3C),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (accountToEdit != null) {
        EditBankAccountDialog(
            account = accountToEdit!!,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { accountToEdit = null }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (isPersian) "افزودن کارت یا حساب بانکی" else "Add Bank Card / Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(if (isPersian) "نام بانک (مثلاً ملی، ملت)" else "Bank Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = cardHolder, onValueChange = { cardHolder = it }, label = { Text(if (isPersian) "نام دارنده کارت" else "Card Holder Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = cardNo, onValueChange = { cardNo = it }, label = { Text(if (isPersian) "شماره ۱۶ رقمی کارت" else "16-Digit Card Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = accNo, onValueChange = { accNo = it }, label = { Text(if (isPersian) "شماره حساب" else "Account Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = balanceStr, onValueChange = { balanceStr = it }, label = { Text(if (isPersian) "موجودی اولیه (تومان)" else "Starting Balance (Toman)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = balanceStr.toEnglishLong()
                        if (bankName.isNotEmpty() && cardHolder.isNotEmpty()) {
                            viewModel.addBankAccount(
                                bankName = bankName,
                                cardHolder = cardHolder,
                                cardNumber = cardNo,
                                accountNumber = accNo,
                                balance = bal,
                                smsKeywords = ""
                            )
                            showAddDialog = false
                            bankName = ""
                            cardHolder = ""
                            cardNo = ""
                            accNo = ""
                            balanceStr = ""
                        }
                    }
                ) {
                    Text(if (isPersian) "ذخیره حساب" else "Save Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun EditBankAccountDialog(
    account: BankAccount,
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    var bankName by remember { mutableStateOf(account.bankName) }
    var cardHolder by remember { mutableStateOf(account.cardHolder) }
    var cardNo by remember { mutableStateOf(account.cardNumber) }
    var accNo by remember { mutableStateOf(account.accountNumber) }
    var balanceStr by remember { mutableStateOf(account.balance.toString()) }
    var smsKeywords by remember { mutableStateOf(account.smsKeywords) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isPersian) "تایید حذف حساب بانکی" else "Confirm Account Deletion", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(if (isPersian) "آیا از حذف کارت/حساب بانکی ${account.bankName} - ${account.cardHolder} اطمینان دارید؟" else "Are you sure you want to delete this bank card/account?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBankAccount(account)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف قطعی" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "ویرایش کارت / حساب بانکی" else "Edit Bank Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(if (isPersian) "نام بانک" else "Bank Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = cardHolder, onValueChange = { cardHolder = it }, label = { Text(if (isPersian) "نام دارنده کارت" else "Card Holder") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = cardNo, onValueChange = { cardNo = it }, label = { Text(if (isPersian) "شماره کارت (۱۶ رقمی)" else "Card Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = accNo, onValueChange = { accNo = it }, label = { Text(if (isPersian) "شماره حساب" else "Account Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = balanceStr, onValueChange = { balanceStr = it }, label = { Text(if (isPersian) "موجودی حساب (تومان)" else "Balance (Toman)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = smsKeywords, onValueChange = { smsKeywords = it }, label = { Text(if (isPersian) "کلمات کلیدی پیامک بانک (با ویرگول)" else "SMS Keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPersian) "حذف این کارت / حساب بانکی" else "Delete Bank Account", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balanceStr.toEnglishLong()
                    if (bankName.isNotEmpty() && cardHolder.isNotEmpty()) {
                        viewModel.updateBankAccount(
                            account.copy(
                                bankName = bankName,
                                cardHolder = cardHolder,
                                cardNumber = cardNo,
                                accountNumber = accNo,
                                balance = bal,
                                smsKeywords = smsKeywords
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
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

@Composable
fun SessionDetailsDialog(
    appointment: com.example.data.Appointment,
    viewModel: com.example.ui.DentalViewModel,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val allAppointments by viewModel.appointments.collectAsState()
    val currentAppt = allAppointments.find { it.id == appointment.id } ?: appointment
    var showManageTherapiesDialog by remember { mutableStateOf(false) }
    
    if (showManageTherapiesDialog) {
        com.example.ui.screens.AppointmentSessionDialog(
            appointment = currentAppt,
            viewModel = viewModel,
            onDismiss = { showManageTherapiesDialog = false }
        )
    }

    val allTreatments by viewModel.allTreatments.collectAsState()
    var treatmentToEditInSession by remember { mutableStateOf<Treatment?>(null) }
    var txToDeleteInSession by remember { mutableStateOf<Treatment?>(null) }
    var clinicalProtocolTitleInSession by remember { mutableStateOf<String?>(null) }
    val sessionTreatments = androidx.compose.runtime.remember(allTreatments, currentAppt.plannedTreatmentIds, currentAppt.patientId) {
        val ids = currentAppt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        if (ids.isNotEmpty()) {
            allTreatments.filter { it.id in ids }
        } else {
            emptyList() // Only list selected therapies
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isPersian) "جزییات جلسه: ${appointment.patientName}" else "Session: ${appointment.patientName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Text(text = viewModel.formatDate(appointment.date) + " - " + appointment.treatmentType, color = Color.Gray, fontSize = 12.sp)
                
                if (sessionTreatments.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (isPersian) "هیچ درمان ثبت شده‌ای برای این جلسه یافت نشد" else "No planned treatments found for this session", color = Color.Gray)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessionTreatments) { treatment ->
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = treatment.title,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.threeSecondLongPress(
                                            onLongClick = {
                                                clinicalProtocolTitleInSession = treatment.title
                                            }
                                        )
                                    )
                                    if (treatment.selectedTeeth.isNotEmpty()) {
                                        Text(text = if (isPersian) "دندان‌ها: ${treatment.selectedTeeth}" else "Teeth: ${treatment.selectedTeeth}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    val displayNotes = treatment.notes.cleanNotesForDisplay()
                                    if (displayNotes.isNotEmpty()) {
                                        Text(text = displayNotes, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    val rawStages = treatment.stages.ifBlank {
                                        "جلسه اول: معاینه و آماده‌سازی - جلسه دوم: اجراء و درمان - جلسه سوم: تحویل و نهایی‌سازی"
                                    }
                                    val allStages = parseStagesList(rawStages)
                                    val completedSet = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                    val initialCompletedSet = remember(treatment.id) { completedSet }
                                    val remainingStages = allStages.filter { !initialCompletedSet.contains(it) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isPersian) "مراحل و گام‌های تراپی درمانی:" else "Therapy Stages Breakdown:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val progressPercent = if (allStages.isNotEmpty()) (completedSet.size * 100) / allStages.size else 0
                                            Text(
                                                text = "${completedSet.size} از ${allStages.size} انجام شد ($progressPercent٪)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (progressPercent == 100) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        remainingStages.forEach { stage ->
                                            val isChecked = completedSet.contains(stage)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .clickable {
                                                        viewModel.toggleTreatmentStage(treatment, stage)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        viewModel.toggleTreatmentStage(treatment, stage)
                                                    }
                                                )
                                                Text(
                                                    text = stage,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatAmount(treatment.cost - treatment.discount)} ${viewModel.label("currency")}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        OutlinedButton(
                                            onClick = { treatmentToEditInSession = treatment },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "ویرایش" else "Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var showConfirmDeleteAppt by remember { mutableStateOf(false) }
                var showNextSessionDialog by remember { mutableStateOf(false) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showNextSessionDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "برنامه‌ریزی جلسه بعدی" else "Schedule Next Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showConfirmDeleteAppt = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "حذف نوبت" else "Delete Appt", fontSize = 11.sp)
                    }
                }

                if (showConfirmDeleteAppt) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDeleteAppt = false },
                        title = { Text(if (isPersian) "تایید حذف نوبت" else "Confirm Delete Appointment", fontWeight = FontWeight.Bold) },
                        text = { Text(if (isPersian) "آیا از حذف این نوبت از تقویم اطمینان دارید؟ این عملیات غیرقابل بازگشت است." else "Are you sure you want to delete this appointment?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteAppointment(appointment)
                                    showConfirmDeleteAppt = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(if (isPersian) "حذف قطعی" else "Confirm Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDeleteAppt = false }) {
                                Text(if (isPersian) "انصراف" else "Cancel")
                            }
                        }
                    )
                }

                if (showNextSessionDialog) {
                    var nextDaysOffset by remember { mutableStateOf("7") }
                    var nextNote by remember { mutableStateOf("ادامه مراحل درمان") }

                    AlertDialog(
                        onDismissRequest = { showNextSessionDialog = false },
                        title = { Text(if (isPersian) "برنامه‌ریزی جلسه بعدی درمانی" else "Schedule Next Session", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "بیمار: ${appointment.patientName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = nextDaysOffset,
                                    onValueChange = { nextDaysOffset = it },
                                    label = { Text(if (isPersian) "چند روز آینده؟ (مثلاً ۷)" else "Days from today") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = nextNote,
                                    onValueChange = { nextNote = it },
                                    label = { Text(if (isPersian) "یادداشت جلسه بعد" else "Next session note") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val days = nextDaysOffset.toLongOrNull() ?: 7L
                                    val targetDate = System.currentTimeMillis() + (days * 86400000L)
                                    viewModel.addAppointment(
                                        com.example.data.Appointment(
                                            patientId = appointment.patientId,
                                            patientName = appointment.patientName,
                                            date = targetDate,
                                            treatmentType = if (isPersian) "ادامه تراپی درمانی" else "Therapy Follow-up",
                                            note = nextNote
                                        )
                                    )
                                    showNextSessionDialog = false
                                }
                            ) {
                                Text(if (isPersian) "ثبت نوبت بعدی" else "Save Next Appointment")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNextSessionDialog = false }) {
                                Text(if (isPersian) "انصراف" else "Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    if (treatmentToEditInSession != null) {
        EditTreatmentDialog(
            treatment = treatmentToEditInSession!!,
            viewModel = viewModel,
            onDelete = {
                txToDeleteInSession = treatmentToEditInSession
                treatmentToEditInSession = null
            },
            onDismiss = { treatmentToEditInSession = null }
        )
    }
    if (txToDeleteInSession != null) {
        AlertDialog(
            onDismissRequest = { txToDeleteInSession = null },
            title = { Text(if (isPersian) "تایید حذف طرح درمان" else "Confirm Delete Treatment", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف درمان «${txToDeleteInSession!!.title}» اطمینان دارید؟ این عملیات غیرقابل بازگشت است." else "Delete treatment '${txToDeleteInSession!!.title}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTreatment(txToDeleteInSession!!)
                        txToDeleteInSession = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { txToDeleteInSession = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    if (clinicalProtocolTitleInSession != null) {
        ClinicalProtocolDialog(
            therapyTitle = clinicalProtocolTitleInSession!!,
            isPersian = isPersian,
            onDismiss = { clinicalProtocolTitleInSession = null }
        )
    }
}

@Composable
fun SessionsArchiveScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val appointments by viewModel.appointments.collectAsState()
    val allTreatments by viewModel.allTreatments.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedApptForDetails by remember { mutableStateOf<Appointment?>(null) }

    val filteredAppointments = remember(appointments, searchQuery, selectedFilter) {
        appointments.filter { appt ->
            val matchesQuery = appt.patientName.contains(searchQuery, ignoreCase = true) || appt.treatmentType.contains(searchQuery, ignoreCase = true) || appt.note.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "COMPLETED" -> appt.treatmentType.contains("انجام شده", ignoreCase = true) || appt.treatmentType.contains("Completed", ignoreCase = true)
                "IN_PROGRESS" -> appt.treatmentType.contains("در حال انجام", ignoreCase = true) || appt.treatmentType.contains("In Progress", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesFilter
        }.sortedByDescending { it.date }
    }

    if (selectedApptForDetails != null) {
        SessionDetailsDialog(
            appointment = selectedApptForDetails!!,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { selectedApptForDetails = null }
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (isPersian) "آرشیو و سوابق کامل جلسات درمانی" else "Treatment Sessions Archive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${filteredAppointments.size} " + if (isPersian) "جلسه ثبت شده" else "sessions",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isPersian) "جستجو در آرشیو نوبت‌ها (نام بیمار، نوع درمان...)" else "Search sessions archive...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text(if (isPersian) "همه جلسات" else "All Sessions", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "COMPLETED",
                onClick = { selectedFilter = "COMPLETED" },
                label = { Text(if (isPersian) "تکمیل شده" else "Completed", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "IN_PROGRESS",
                onClick = { selectedFilter = "IN_PROGRESS" },
                label = { Text(if (isPersian) "در حال انجام" else "In Progress", fontSize = 11.sp) }
            )
        }

        if (filteredAppointments.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    Text(if (isPersian) "هیچ جلسه‌ای در آرشیو یافت نشد" else "No sessions found in archive", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredAppointments) { appt ->
                    val apptTreatments = remember(allTreatments, appt.plannedTreatmentIds, appt.patientId) {
                        val ids = appt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
                        if (ids.isNotEmpty()) allTreatments.filter { it.id in ids } else allTreatments.filter { it.patientId == appt.patientId }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedApptForDetails = appt },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = appt.patientName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = viewModel.formatTime(appt.date),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${viewModel.formatDate(appt.date)} • ${if (appt.treatmentType.isNotBlank()) appt.treatmentType else (if (isPersian) "جلسه درمانی" else "Therapy Session")}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                if (apptTreatments.isNotEmpty()) {
                                    Text(
                                        text = if (isPersian) "درمان‌های مرتبط: ${apptTreatments.joinToString(", ") { it.title }}" else "Treatments: ${apptTreatments.joinToString(", ") { it.title }}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CLINICAL & AI DIALOG COMPONENTS
// ==========================================

@Composable
fun DigitalConsentSignatureDialog(
    patient: Patient,
    isPersian: Boolean,
    onDismiss: () -> Unit,
    onSaveSignature: (templateName: String, base64Image: String) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf("رضایتنامه عمومی درمان و عصب‌کشی دندان") }
    val templates = listOf(
        "رضایتنامه عمومی درمان و عصب‌کشی دندان",
        "رضایتنامه جراحی دندان عقل و ایمپلنت",
        "رضایتنامه درمان ترمیمی، زیبایی و روکش",
        "فرم خوداظهاری تاریخچه پزشکی و سوابق دارویی"
    )

    val pathList = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var drawTrigger by remember { mutableIntStateOf(0) }
    // اندازه واقعی کادر امضا روی صفحه (پیکسل) — برای جلوگیری از برش خوردن امضا هنگام ذخیره
    var signatureBoxSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val legalText = when (selectedTemplate) {
        "رضایتنامه جراحی دندان عقل و ایمپلنت" -> "اینجانب ${patient.name} با آگاهی کامل از تمامی عوارض، خطرات احتمالی، هزینه‌ها و مراحل درمان جراحی و ایمپلنت، رضایت خود را جهت انجام این اقدام درمانی توسط پزشک معالج اعلام می‌دارم."
        "فرم خوداظهاری تاریخچه پزشکی و سوابق دارویی" -> "اینجانب ${patient.name} صحت کامل اطلاعات پزشکی، سوابق بیماری‌های زمینه‌ای و مصرف داروهای خود را تأیید نموده و مسئولیت هرگونه عدم اظهار صریح را می‌پذیرم."
        else -> "اینجانب ${patient.name} با آگاهی کامل از روند درمان، عوارض احتمالی و هزینه‌های مربوطه، رضایت کامل خود را جهت انجام درمان‌های دندانپزشکی مربوطه اعلام می‌دارم."
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(
                            text = if (isPersian) "امضای دیجیتال رضایتنامه لمسی (تمام‌صفحه)" else "Digital Consent Signature (Full Screen)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(text = "انتخاب نوع فرم / رضایتنامه:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(selectedTemplate, fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        templates.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t, fontSize = 12.sp) },
                                onClick = {
                                    selectedTemplate = t
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "متن حقوقی رضایتنامه:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(text = legalText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "بیمار: ${patient.name} | تاریخ: ${java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date())}", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✍️ کادر بزرگ امضای لمسی (با انگشت یا قلم):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "ترسیم زنده و همزمان", fontSize = 10.sp, color = Color.Gray)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .onSizeChanged { signatureBoxSizePx = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newP = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath = newP
                                    drawTrigger++
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPath?.let { p ->
                                        p.lineTo(change.position.x, change.position.y)
                                    }
                                    drawTrigger++
                                },
                                onDragEnd = {
                                    currentPath?.let { p ->
                                        pathList.add(p)
                                        currentPath = null
                                    }
                                    drawTrigger++
                                }
                            )
                        }
                ) {
                    // Force recomposition on drawTrigger change to show line live in real-time
                    val dummy = drawTrigger
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (dummy >= 0) {
                            pathList.forEach { path ->
                                drawPath(path = path, color = Color.Blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                            }
                            currentPath?.let { path ->
                                drawPath(path = path, color = Color.Blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                            }
                        }
                    }
                    if (pathList.isEmpty() && currentPath == null) {
                        Text(
                            text = "✍️ امضای بیمار را روی این صفحه بزرگ ثبت نمایید",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            if (pathList.isNotEmpty()) {
                                pathList.removeAt(pathList.size - 1)
                                drawTrigger++
                            }
                        },
                        enabled = pathList.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بازگشت یک مرحله", fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = {
                            pathList.clear()
                            drawTrigger++
                        },
                        enabled = pathList.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("پاک‌کردن امضا", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "انصراف" else "Cancel")
                    }
                    Button(
                        onClick = {
                            // ذخیره امضا دقیقاً با ابعاد واقعی کادر روی صفحه (نه اندازه ثابت هاردکد)
                            // تا هیچ بخشی از امضا برش نخورد
                            val bmpWidth = if (signatureBoxSizePx.width > 0) signatureBoxSizePx.width else 800
                            val bmpHeight = if (signatureBoxSizePx.height > 0) signatureBoxSizePx.height else 400
                            val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLUE
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 8f
                                isAntiAlias = true
                            }
                            pathList.forEach { p ->
                                canvas.drawPath(p.asAndroidPath(), paint)
                            }
                            val stream = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                            val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                            onSaveSignature(selectedTemplate, b64)
                        },
                        enabled = pathList.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "ثبت و ذخیره امضا" else "Save Signature")
                    }
                }
            }
        }
    }
}

@Composable
fun BeforeAfterComparisonDialog(
    docList: List<String>,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var docListState by remember { mutableStateOf(docList.toMutableList()) }

    var beforeDoc by remember(docListState) { mutableStateOf(docListState.getOrNull(0)) }
    var afterDoc by remember(docListState) { mutableStateOf(docListState.getOrNull(1) ?: docListState.getOrNull(0)) }
    var comparisonMode by remember { mutableIntStateOf(0) } // 0 = Side by Side, 1 = Split Slider
    var sliderRatio by remember { mutableFloatStateOf(0.5f) }

    // Camera and Gallery Launchers for uploading new before/after photos
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val b64 = saveUriToLocalFile(context, it)
            if (b64 != null) {
                val newDoc = "تصویر بالینی|درمان|${System.currentTimeMillis()}|$b64"
                val updated = docListState.toMutableList().apply { add(newDoc) }
                docListState = updated
                android.widget.Toast.makeText(context, "تصویر با موفقیت به مقایسه افزوده شد", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberSafeCameraLauncher(context) { path ->
        val newDoc = "تصویر دوربین|درمان|${System.currentTimeMillis()}|$path"
        val updated = docListState.toMutableList().apply { add(newDoc) }
        docListState = updated
        android.widget.Toast.makeText(context, "تصویر دوربین ثبت شد", android.widget.Toast.LENGTH_SHORT).show()
    }

    val beforeBmp = remember(beforeDoc) {
        val b64 = beforeDoc?.split("|")?.getOrNull(3)
        if (!b64.isNullOrBlank()) base64ToBitmap(b64) else null
    }

    val afterBmp = remember(afterDoc) {
        val b64 = afterDoc?.split("|")?.getOrNull(3)
        if (!b64.isNullOrBlank()) base64ToBitmap(b64) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (isPersian) "مقایسه هوشمند تصاویر قبل و بعد درمان" else "Before & After Photo Comparison")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Camera & Gallery Buttons to immediately capture or select before/after photos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("عکس با دوربین", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("انتخاب از گالری", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = comparisonMode == 0,
                        onClick = { comparisonMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("پهلو به پهلو", fontSize = 10.5.sp)
                    }
                    SegmentedButton(
                        selected = comparisonMode == 1,
                        onClick = { comparisonMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("اسلایدر تعاملی", fontSize = 10.5.sp)
                    }
                }

                if (comparisonMode == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("قبل از درمان (Before)", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (beforeBmp != null) {
                                    Image(bitmap = beforeBmp.asImageBitmap(), contentDescription = "Before", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text("تصویر قبل انتخاب نشده", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(color = Color(0xFF43A047), shape = RoundedCornerShape(4.dp)) {
                                Text("بعد از درمان (After)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (afterBmp != null) {
                                    Image(bitmap = afterBmp.asImageBitmap(), contentDescription = "After", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text("تصویر بعد انتخاب نشده", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⬅️ انگشت خود را روی اسلایدر برای مقایسه جابه‌جا کنید ➡️", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (beforeBmp != null && afterBmp != null) {
                                Image(bitmap = afterBmp.asImageBitmap(), contentDescription = "After Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val splitX = size.width * sliderRatio
                                    clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                                        drawImage(
                                            image = beforeBmp.asImageBitmap(),
                                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                                        )
                                    }
                                    drawLine(
                                        color = Color.White,
                                        start = androidx.compose.ui.geometry.Offset(splitX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(splitX, size.height),
                                        strokeWidth = 4f
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 16f,
                                        center = androidx.compose.ui.geometry.Offset(splitX, size.height / 2f)
                                    )
                                }

                                Text(
                                    text = "BEFORE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "AFTER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color(0xFF43A047).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            } else {
                                Text("جهت مقایسه اسلایدر، عکس قبل و بعد را از گالری یا دوربین بالا اضافه نمایید", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }

                        Slider(
                            value = sliderRatio,
                            onValueChange = { sliderRatio = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isPersian) "بستن" else "Close")
            }
        }
    )
}

@Composable
fun AiRadiographyScanDialog(
    scanResult: RadiographyScanResult,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (isPersian) "تحلیل هوشمند رادیوگرافی (Gemini Flash)" else "AI Radiograph Scan Report")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("خلاصه تشخیص اولیه AI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(scanResult.overallSummary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                if (scanResult.suspectedTeeth.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("دندان‌های نیازمند معاینه بالینی:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        scanResult.suspectedTeeth.forEach { tooth ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "دندان $tooth",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Text("یافته‌های تفکیکی رادیوگرافی:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                scanResult.findings.forEach { finding ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "دندان ${finding.toothNumber} - ${finding.condition}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = when (finding.severity.lowercase()) {
                                        "high" -> Color(0xFFE53935)
                                        "medium" -> Color(0xFFFFB300)
                                        else -> Color(0xFF1E88E5)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = finding.severity,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (finding.description.isNotEmpty()) {
                                Text(text = finding.description, fontSize = 10.5.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                if (scanResult.recommendedTreatments.isNotEmpty()) {
                    Text("توصیه درمانی AI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(scanResult.recommendedTreatments, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isPersian) "بستن گزارش" else "Close Report")
            }
        }
    )
}

@Composable
fun AiPostTreatmentCareDialog(
    patientName: String,
    patientPhone: String,
    initialCareText: String,
    preferredMessenger: String = "واتساپ",
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    var careText by remember { mutableStateOf(initialCareText) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (isPersian) "دستورالعمل هوشمند مراقبت‌های پس از درمان" else "Post-Treatment Instructions")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "متن مراقبت‌های اختصاصی برای $patientName:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = careText,
                    onValueChange = { careText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
                )

                Text(
                    text = "ارسال مستقیم به پیام‌رسان بیمار (پیش‌فرض: $preferredMessenger):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 5 Direct Messaging Channels: WhatsApp, Telegram, Rubika, Bale, SMS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (patientPhone.isNotBlank()) openWhatsAppChatWithMessage(context, patientPhone, careText)
                                else android.widget.Toast.makeText(context, "شماره تماس بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("🟢 واتساپ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (patientPhone.isNotBlank()) openTelegramChatWithMessage(context, patientPhone, careText)
                                else android.widget.Toast.makeText(context, "شماره تماس بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("🔵 تلگرام", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (patientPhone.isNotBlank()) openRubikaChatWithMessage(context, patientPhone, careText)
                                else android.widget.Toast.makeText(context, "شماره تماس بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("🟣 روبیکا", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (patientPhone.isNotBlank()) openBaleChatWithMessage(context, patientPhone, careText)
                                else android.widget.Toast.makeText(context, "شماره تماس بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("🟢 بله", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (patientPhone.isNotBlank()) com.example.reports.sendSmsPrefilled(context, patientPhone, careText)
                                else android.widget.Toast.makeText(context, "شماره تماس بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text("💬 پیامک (SMS)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Post Care Instructions", careText)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "متن در حافظه کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("کپی متن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "بستن" else "Close")
            }
        }
    )
}

fun openWhatsAppChatWithMessage(context: Context, rawPhone: String, message: String) {
    val digits = rawPhone.filter { it.isDigit() }
    val normalized = when {
        digits.startsWith("0098") -> digits.removePrefix("00")
        digits.startsWith("98") -> digits
        digits.startsWith("0") -> "98${digits.removePrefix("0")}"
        else -> digits
    }
    if (normalized.isBlank()) {
        android.widget.Toast.makeText(context, "شماره تلفن معتبر نیست", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val encodedMsg = Uri.encode(message)
    val uri = Uri.parse("https://wa.me/$normalized?text=$encodedMsg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        setPackage("com.whatsapp")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (e2: Exception) {
            android.widget.Toast.makeText(context, "واتساپ روی این دستگاه نصب نیست", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

fun openTelegramChatWithMessage(context: Context, rawPhone: String, message: String) {
    val encodedMsg = Uri.encode(message)
    val uri = Uri.parse("https://t.me/share/url?url=&text=$encodedMsg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "اپلیکیشن تلگرام نصب نیست", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun openBaleChatWithMessage(context: Context, rawPhone: String, message: String) {
    val encodedMsg = Uri.encode(message)
    val uri = Uri.parse("https://ble.ir/share?text=$encodedMsg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "اپلیکیشن بله نصب نیست", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun openRubikaChatWithMessage(context: Context, rawPhone: String, message: String) {
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, message)
        type = "text/plain"
        `package` = "ir.resaneh1.iptv"
    }
    try {
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        val generalShare = android.content.Intent.createChooser(
            android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                type = "text/plain"
            }, "ارسال پیام"
        )
        context.startActivity(generalShare)
    }
}

fun openMessengerChat(context: Context, rawPhone: String, message: String, messenger: String) {
    when (messenger.trim()) {
        "تلگرام", "Telegram" -> openTelegramChatWithMessage(context, rawPhone, message)
        "بله", "Bale" -> openBaleChatWithMessage(context, rawPhone, message)
        "روبیکا", "Rubika" -> openRubikaChatWithMessage(context, rawPhone, message)
        "پیامک", "SMS" -> com.example.reports.sendSmsPrefilled(context, rawPhone, message)
        else -> openWhatsAppChatWithMessage(context, rawPhone, message)
    }
}
