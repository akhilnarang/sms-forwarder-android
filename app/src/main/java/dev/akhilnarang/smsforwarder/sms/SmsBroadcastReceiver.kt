package dev.akhilnarang.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.akhilnarang.smsforwarder.SmsForwarderApp
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val incomingSms = SmsParser.parse(intent) ?: return
        val app = context.applicationContext as SmsForwarderApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                app.container.smsProcessor.handleIncomingSms(incomingSms)
                Log.d(TAG, "Stored incoming SMS and scheduled forwarding.")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsBroadcastReceiver"
    }
}
