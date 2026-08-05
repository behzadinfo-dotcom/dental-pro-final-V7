import re

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'r') as f:
    content = f.read()

# Replace the signature
content = content.replace("    initialFontSizePt: Int = 11,\n    onFontSizeChange: (Int) -> Unit = {},\n    onDismiss: () -> Unit", "    initialFontSizePt: Int = 11,\n    onDismiss: () -> Unit")

# Replace the mutable state with val
content = content.replace("    // آخرین سایز فونت انتخاب‌شده توسط کاربر بارگذاری و با هر تغییر بلافاصله ذخیره می‌شود\n    var fontSizePt by remember { mutableIntStateOf(initialFontSizePt) }", "    val fontSizePt = initialFontSizePt")

# Remove onFontSizeChange={ ... } from callers
content = re.sub(r',\s*onFontSizeChange\s*=\s*\{\s*[^}]*\s*\}', '', content)

with open('app/src/main/java/com/example/ui/screens/DentalScreens.kt', 'w') as f:
    f.write(content)
