import re

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'r') as f:
    content = f.read()

# Add showManageTherapiesDialog state
var_decl = r'var sessionFinishConfirmed by remember\(appt\.id\) \{ mutableStateOf\(appt\.isCompleted\) \}'
new_var_decl = """var sessionFinishConfirmed by remember(appt.id) { mutableStateOf(appt.isCompleted) }
        var showManageTherapiesDialog by remember { mutableStateOf(false) }
        if (showManageTherapiesDialog) {
            AppointmentSessionDialog(
                appointment = appt,
                viewModel = viewModel,
                onDismiss = { showManageTherapiesDialog = false }
            )
        }"""
content = content.replace(var_decl, new_var_decl)

# Add "Manage Therapies" button and filter patientTreatments
old_header = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "طرح‌های درمان پرونده بیمار & مراحل تراپی:" else "Patient Treatment Plans & Stages:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (patientTreatments.isEmpty()) {"""
new_header = """                    Row(
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

                    if (sessionTreatments.isEmpty()) {"""
content = content.replace(old_header, new_header)

# Change patientTreatments.forEach to sessionTreatments.forEach
content = content.replace("patientTreatments.forEach { tx ->", "sessionTreatments.forEach { tx ->")
content = content.replace("هیچ طرح درمانی برای این بیمار ثبت نشده است.", "هیچ طرح درمانی برای این جلسه انتخاب نشده است. لطفاً از طریق دکمه بالا تراپی‌ها را انتخاب کنید.")

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'w') as f:
    f.write(content)
