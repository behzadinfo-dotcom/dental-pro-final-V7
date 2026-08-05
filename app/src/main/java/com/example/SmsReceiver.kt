package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.ui.DentalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val bundle = intent.extras ?: return
            try {
                val pdus = bundle.get("pdus") as? Array<*> ?: return
                val format = bundle.getString("format")
                val messages = pdus.map {
                    @Suppress("DEPRECATION")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(it as ByteArray, format)
                    } else {
                        SmsMessage.createFromPdu(it as ByteArray)
                    }
                }
                
                val smsBody = messages.joinToString("\n") { it.messageBody }
                if (smsBody.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        DentalViewModel.incomingSmsFlow.emit(smsBody)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
