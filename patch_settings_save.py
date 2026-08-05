import re

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'r') as f:
    content = f.read()

# Update the save button
old_save = """                    Button(
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
                        },"""
new_save = """                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                clinicName = clinicName,
                                doctorName = doctorName,
                                staffNames = staffNames,
                                showSummary = showSummary,
                                showQuick = showQuick,
                                showCharts = showCharts
                            )
                            viewModel.updateGlobalFontScale(fontScaleIdx)
                            viewModel.updateClinicalFontSize(clinicalFontIdx + 8)
                            if (advancedSettingsRevealed) showHideAdvancedConfirm = true else onBack()
                        },"""
content = content.replace(old_save, new_save)

with open('app/src/main/java/com/example/ui/screens/AdvancedErpScreens.kt', 'w') as f:
    f.write(content)
