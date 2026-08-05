package com.example.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** One line item shown on a statement or treatment-plan card. */
data class SummaryLineItem(val title: String, val subtitle: String, val amountText: String)

/**
 * Renders a branded, shareable PNG card — used when sending a patient
 * statement or treatment plan over WhatsApp (image beats a wall of text
 * inside a chat). Colors come from the currently active neon theme so the
 * image matches the app's look.
 */
fun buildSummaryImage(
    context: Context,
    cardTitle: String,
    clinicName: String,
    doctorName: String,
    patientName: String,
    items: List<SummaryLineItem>,
    footerLines: List<Pair<String, String>>, // label to value, last one is emphasized total
    accentColor: Int,
    backgroundColor: Int,
    fileNamePrefix: String
): File {
    val width = 1000
    val margin = 56
    val rowHeight = 74
    val headerHeight = 260
    val footerHeight = 60 + footerLines.size * 54
    val height = headerHeight + items.size * rowHeight + footerHeight + 80

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    // Top accent gradient band
    val bandPaint = Paint().apply { color = accentColor; alpha = 235 }
    canvas.drawRect(0f, 0f, width.toFloat(), 14f, bandPaint)

    var y = 70f
    val clinicPaint = TextPaint().apply {
        isAntiAlias = true; color = accentColor; textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(clinicName, width - margin.toFloat(), y, clinicPaint)
    y += 40f
    val docPaint = TextPaint().apply { isAntiAlias = true; color = Color.LTGRAY; textSize = 20f; textAlign = Paint.Align.RIGHT }
    canvas.drawText(doctorName, width - margin.toFloat(), y, docPaint)
    y += 52f

    val titlePaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    canvas.drawText(cardTitle, width - margin.toFloat(), y, titlePaint)
    y += 40f
    val patientPaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.RIGHT }
    canvas.drawText("بیمار: $patientName", width - margin.toFloat(), y, patientPaint)
    y += 30f

    val dividerPaint = Paint().apply { color = accentColor; alpha = 150; strokeWidth = 2f }
    canvas.drawLine(margin.toFloat(), y, width - margin.toFloat(), y, dividerPaint)
    y += 46f

    val itemTitlePaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    val itemSubPaint = TextPaint().apply { isAntiAlias = true; color = Color.LTGRAY; textSize = 18f; textAlign = Paint.Align.RIGHT }
    val itemAmountPaint = TextPaint().apply { isAntiAlias = true; color = accentColor; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
    val rowBgAlt = Paint().apply { color = Color.WHITE; alpha = 12 }

    items.forEachIndexed { index, item ->
        if (index % 2 == 1) {
            canvas.drawRect(margin.toFloat(), y - 44f, width - margin.toFloat(), y + 22f, rowBgAlt)
        }
        canvas.drawText(item.title, width - margin.toFloat(), y, itemTitlePaint)
        canvas.drawText(item.amountText, margin.toFloat(), y, itemAmountPaint)
        if (item.subtitle.isNotBlank()) {
            canvas.drawText(item.subtitle, width - margin.toFloat(), y + 26f, itemSubPaint)
        }
        y += rowHeight
    }

    y += 16f
    canvas.drawLine(margin.toFloat(), y, width - margin.toFloat(), y, dividerPaint)
    y += 46f

    footerLines.forEachIndexed { index, (label, value) ->
        val isLast = index == footerLines.lastIndex
        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            color = if (isLast) accentColor else Color.LTGRAY
            textSize = if (isLast) 30f else 22f
            isFakeBoldText = isLast
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(labelPaint).apply { textAlign = Paint.Align.LEFT; color = if (isLast) accentColor else Color.WHITE }
        canvas.drawText(label, width - margin.toFloat(), y, labelPaint)
        canvas.drawText(value, margin.toFloat(), y, valuePaint)
        y += if (isLast) 54f else 44f
    }

    val brandPaint = TextPaint().apply { isAntiAlias = true; color = Color.GRAY; textSize = 16f; textAlign = Paint.Align.CENTER }
    canvas.drawText("تولید شده توسط دنتال‌پرو", width / 2f, height - 24f, brandPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "${fileNamePrefix}_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** Short SMS-safe Persian summary of a patient's account balance. */
fun buildStatementSmsText(
    clinicName: String,
    patientName: String,
    currency: String,
    totalCostText: String,
    totalPaidText: String,
    balanceText: String,
    isDebtor: Boolean
): String {
    val balanceLine = if (isDebtor) "مانده بدهی: $balanceText $currency" else "مانده بستانکاری: $balanceText $currency"
    return "$clinicName\nصورت‌حساب $patientName\nجمع درمان: $totalCostText $currency\nجمع پرداختی: $totalPaidText $currency\n$balanceLine"
}

/** Short SMS-safe Persian summary of a proposed treatment plan and price. */
fun buildTreatmentPlanSmsText(
    clinicName: String,
    patientName: String,
    currency: String,
    treatmentTitles: List<String>,
    totalPriceText: String
): String {
    val itemsText = if (treatmentTitles.size <= 4) {
        treatmentTitles.joinToString("، ")
    } else {
        treatmentTitles.take(4).joinToString("، ") + " و ${treatmentTitles.size - 4} مورد دیگر"
    }
    return "$clinicName\nطرح درمان پیشنهادی برای $patientName:\n$itemsText\nهزینه کل تخمینی: $totalPriceText $currency"
}
