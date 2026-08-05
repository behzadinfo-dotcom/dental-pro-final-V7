import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

# 1. Observe allAppointments and currentAppt
pattern_start = r'fun SessionDetailsDialog\(\s*appointment: com\.example\.data\.Appointment,\s*viewModel: com\.example\.ui\.DentalViewModel,\s*isPersian: Boolean,\s*onDismiss: \(\) -> Unit\s*\)\s*\{'
replacement_start = """fun SessionDetailsDialog(
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
"""
content = re.sub(pattern_start, replacement_start, content)

# 2. Update sessionTreatments dependency and fallback
pattern_session = r'val sessionTreatments = androidx\.compose\.runtime\.remember\(allTreatments, appointment\.plannedTreatmentIds, appointment\.patientId\) \{\s*val ids = appointment\.plannedTreatmentIds\.split\("\,"\)\[.*?\]\s*if \(ids\.isNotEmpty\(\)\) \{\s*allTreatments\.filter \{ it\.id in ids \}\s*\} else \{\s*allTreatments\.filter \{ it\.patientId == appointment\.patientId \}\s*\}\s*\}'
replacement_session = """val sessionTreatments = androidx.compose.runtime.remember(allTreatments, currentAppt.plannedTreatmentIds, currentAppt.patientId) {
        val ids = currentAppt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        if (ids.isNotEmpty()) {
            allTreatments.filter { it.id in ids }
        } else {
            emptyList() // Only list selected therapies
        }
    }"""
content = re.sub(r'val sessionTreatments = androidx\.compose\.runtime\.remember\(allTreatments, appointment\.plannedTreatmentIds, appointment\.patientId\) \{.*?\n\s*\}\s*\}', replacement_session, content, flags=re.DOTALL)

# 3. Add the "Edit Therapies" button
pattern_row = r'Text\(text = viewModel\.formatDate\(appointment\.date\) \+ " - " \+ appointment\.treatmentType, color = Color\.Gray, fontSize = 12\.sp\)'
replacement_row = """Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = viewModel.formatDate(currentAppt.date) + " - " + currentAppt.treatmentType, color = Color.Gray, fontSize = 12.sp)
                    OutlinedButton(onClick = { showManageTherapiesDialog = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (isPersian) "ویرایش تراپی‌های جلسه" else "Edit Session Therapies", fontSize = 10.sp)
                    }
                }"""
content = content.replace(pattern_row, replacement_row)

# 4. Filter stages based on initial completed
pattern_stages = r'val completedSet = treatment\.completedStages\.split\("\,"\)\[.*?\]\.toSet\(\)\s*Column\('
replacement_stages = """val completedSet = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                    val initialCompletedSet = remember(treatment.id) { completedSet }
                                    val remainingStages = allStages.filter { !initialCompletedSet.contains(it) }
                                    
                                    Column("""
content = re.sub(r'val completedSet = treatment\.completedStages\.split\("\,"\)\[.*?\]\.toSet\(\)\n\s*Column\(', replacement_stages, content)

# 5. Loop over remainingStages instead of allStages
content = content.replace('allStages.forEach { stage ->', 'remainingStages.forEach { stage ->')

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
