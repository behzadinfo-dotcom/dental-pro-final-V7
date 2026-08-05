package com.example.ui.screens

fun String.toEnglishLong(): Long {
    if (this.isBlank()) return 0L
    val persianToEnglish = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    val converted = this.map { persianToEnglish[it] ?: it }.joinToString("")
    val clean = converted.replace(Regex("[^0-9]"), "")
    return clean.toLongOrNull() ?: 0L
}

fun String.toEnglishLongOrNull(): Long? {
    if (this.isBlank()) return null
    val persianToEnglish = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    val converted = this.map { persianToEnglish[it] ?: it }.joinToString("")
    val clean = converted.replace(Regex("[^0-9]"), "")
    return clean.toLongOrNull()
}
