package com.example.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

const val CHANNEL_APPOINTMENTS = "dentalpro_appointments"
const val CHANNEL_CHEQUES = "dentalpro_cheques"

/** Creates the two notification channels (safe to call repeatedly — Android ignores re-creation of an existing channel). */
fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_APPOINTMENTS, "یادآوری نوبت‌ها", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "یادآوری نوبت‌های نزدیک بیماران"
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_CHEQUES, "یادآوری سررسید چک", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "یادآوری چک‌های نزدیک به سررسید"
        }
    )
}

private fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, requestCode, intent, flags)
}

/** True if the app is allowed to post notifications on this device/OS version. */
fun canPostNotifications(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

fun showAppointmentReminder(context: Context, notificationId: Int, patientName: String, timeText: String) {
    if (!canPostNotifications(context)) return
    ensureNotificationChannels(context)
    val notification = NotificationCompat.Builder(context, CHANNEL_APPOINTMENTS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("یادآوری نوبت: $patientName")
        .setContentText("نوبت در $timeText")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(openAppPendingIntent(context, notificationId))
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

fun showChequeDueReminder(context: Context, notificationId: Int, patientName: String, amountText: String, dueText: String) {
    if (!canPostNotifications(context)) return
    ensureNotificationChannels(context)
    val notification = NotificationCompat.Builder(context, CHANNEL_CHEQUES)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("سررسید چک: $patientName")
        .setContentText("$amountText — سررسید $dueText")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(openAppPendingIntent(context, notificationId))
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}
