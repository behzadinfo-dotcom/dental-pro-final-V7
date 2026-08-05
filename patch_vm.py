import re

with open('app/src/main/java/com/example/ui/DentalViewModel.kt', 'r') as f:
    content = f.read()

vm_new = """    fun updateSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) {
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
    }"""

# Using regex to replace the old methods
pattern = r'fun updateSettings\(.*?\).*?fun updateGlobalFontScale\(.*?\).*?\}'
content = re.sub(pattern, vm_new, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/DentalViewModel.kt', 'w') as f:
    f.write(content)
