package com.example.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Opens the system print dialog for an already-generated PDF file. */
fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "سرویس چاپ در این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
        return
    }
    printManager.print(jobName, PdfPrintDocumentAdapter(file), PrintAttributes.Builder().build())
}

private class PdfPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

/** Generic share sheet for a PDF file (any app that accepts documents). */
fun sharePdf(context: Context, file: File, chooserTitle: String = "اشتراک‌گذاری سند") {
    val uri = fileProviderUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Shares a PDF straight to WhatsApp; falls back to the general share sheet if WhatsApp isn't installed. */
fun sharePdfToWhatsApp(context: Context, file: File) {
    try {
        val uri = fileProviderUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "واتساپ روی این دستگاه نصب نیست — منوی اشتراک‌گذاری عمومی باز شد", Toast.LENGTH_LONG).show()
            sharePdf(context, file)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در اشتراک‌گذاری PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/** Shares a rendered summary image (PNG) straight to WhatsApp — used for statement/treatment-plan cards. */
fun shareImageToWhatsApp(context: Context, imageFile: File) {
    try {
        val uri = fileProviderUri(context, imageFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "واتساپ روی این دستگاه نصب نیست — منوی اشتراک‌گذاری عمومی باز شد", Toast.LENGTH_LONG).show()
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(fallback, "اشتراک‌گذاری تصویر").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در اشتراک‌گذاری تصویر: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a WhatsApp chat directly with the given phone number (not a share —
 * just opens the conversation so the user can type freely). Normalizes
 * common Iranian phone formats (0912xxxxxxx, +98912xxxxxxx) to the
 * international format WhatsApp's wa.me links expect.
 */
fun openWhatsAppChat(context: Context, rawPhone: String) {
    val digits = rawPhone.filter { it.isDigit() }
    val normalized = when {
        digits.startsWith("0098") -> digits.removePrefix("00")
        digits.startsWith("98") -> digits
        digits.startsWith("0") -> "98${digits.removePrefix("0")}"
        else -> digits
    }
    if (normalized.isBlank()) {
        Toast.makeText(context, "شماره تلفن معتبر نیست", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = Uri.parse("https://wa.me/$normalized")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "واتساپ روی این دستگاه نصب نیست", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Generic "share as text" via the system chooser — lists every installed app
 * that accepts plain text (Telegram, Bale, Rubika, email, etc.) without
 * needing a dedicated integration per app.
 */
fun shareTextNativeChooser(context: Context, text: String, chooserTitle: String = "اشتراک‌گذاری") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری یافت نشد", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Generic "share as image" via the system chooser — lists every installed
 * app that accepts images (Telegram, Bale, Rubika, WhatsApp, etc.).
 */
fun shareImageNativeChooser(context: Context, imageFile: File, caption: String? = null, chooserTitle: String = "اشتراک‌گذاری تصویر") {
    val uri = fileProviderUri(context, imageFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری یافت نشد", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Renders a simple white card image (title + a few text lines, RTL-aware)
 * to a cache file — used for the "share as image" option so the same
 * summary that would be sent as text can also be sent as a picture.
 */
fun generateSimpleInfoCardImage(context: Context, title: String, lines: List<String>): File {
    val width = 900
    val lineHeight = 60
    val topPadding = 140
    val bottomPadding = 60
    val height = topPadding + lines.size * lineHeight + bottomPadding
    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FF8F00")
        textSize = 46f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
        isFakeBoldText = true
    }
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 34f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    val rightX = width - 40f
    canvas.drawText(title, rightX, 90f, titlePaint)
    canvas.drawLine(40f, 115f, width - 40f, 115f, android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 2f })
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, rightX, (topPadding + index * lineHeight).toFloat(), linePaint)
    }

    val file = File(context.cacheDir, "info_card_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out) }
    return file
}

/**
 * Opens the default SMS app with the recipient and a pre-filled short text
 * summary — the user reviews and taps send themselves. Deliberately does
 * NOT send silently (no SEND_SMS permission requested), which keeps this
 * safe for a healthcare app and avoids restricted-permission Play policy.
 */
fun sendSmsPrefilled(context: Context, phone: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه پیامک روی این دستگاه یافت نشد", Toast.LENGTH_SHORT).show()
    }
}
