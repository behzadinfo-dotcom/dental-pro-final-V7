package com.example.util

/** Normalizes Persian/Arabic digits to ASCII so validation works regardless of keyboard. */
private fun normalizeDigits(input: String): String {
    val map = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    return input.map { map[it] ?: it }.joinToString("")
}

/** Accepts Iranian mobile numbers (09xxxxxxxxx / +989xxxxxxxxx) and common landline formats.
 * Blank is considered valid since phone is optional in several forms — callers that require
 * a phone should separately check isBlank(). */
fun isValidIranPhone(raw: String): Boolean {
    if (raw.isBlank()) return true
    val digits = normalizeDigits(raw).filter { it.isDigit() || it == '+' }
    val mobilePattern = Regex("^(\\+98|0)?9\\d{9}$")
    val landlinePattern = Regex("^(\\+98|0)?\\d{10,11}$")
    return mobilePattern.matches(digits) || landlinePattern.matches(digits)
}

/** True if the text is a valid non-negative amount (digits only, Persian digits allowed). */
fun isValidAmount(raw: String): Boolean {
    if (raw.isBlank()) return false
    val digits = normalizeDigits(raw).replace(",", "").replace("،", "")
    return digits.toLongOrNull()?.let { it >= 0 } ?: false
}

/** True if the text is a valid strictly-positive amount. */
fun isValidPositiveAmount(raw: String): Boolean {
    if (!isValidAmount(raw)) return false
    val digits = normalizeDigits(raw).replace(",", "").replace("،", "")
    return (digits.toLongOrNull() ?: 0L) > 0L
}

/**
 * Validates the core patient form fields.
 * @return a Persian error message, or null if the form is valid.
 */
fun validatePatientForm(name: String, phone: String): String? {
    if (name.isBlank()) return "نام بیمار الزامی است"
    if (!isValidIranPhone(phone)) return "شماره تلفن معتبر نیست"
    return null
}

/**
 * Validates a treatment entry.
 * @return a Persian error message, or null if valid.
 */
fun validateTreatmentForm(title: String, costText: String): String? {
    if (title.isBlank()) return "شرح درمان الزامی است"
    if (!isValidAmount(costText)) return "هزینه درمان باید یک عدد معتبر و غیرمنفی باشد"
    return null
}

/**
 * Validates a payment entry.
 * @return a Persian error message, or null if valid.
 */
fun validatePaymentForm(amountText: String): String? {
    if (!isValidPositiveAmount(amountText)) return "مبلغ پرداخت باید یک عدد معتبر و بزرگ‌تر از صفر باشد"
    return null
}
