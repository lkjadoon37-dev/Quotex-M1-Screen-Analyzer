
package com.example.quotexm1analyzer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class SignalNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "quotex_m1_signals"
        private const val NOTIFICATION_ID = 3701
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quotex M1 Signals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "M1 candle analysis signals"
            }

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    fun showSignal(
        signal: String,
        strength: Int,
        reason: String
    ) {
        val normalizedSignal = signal.uppercase()

        if (normalizedSignal !in listOf("CALL", "PUT", "WAIT")) {
            return
        }

        val safeStrength = strength.coerceIn(0, 100)

        if (Build.VERSION.SDK_INT >= 33) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) return
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Quotex AI Analyzer M1: $normalizedSignal")
            .setContentText(
                "Setup Strength: $safeStrength% | $reason"
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Signal: $normalizedSignal\n" +
                        "Setup Strength: $safeStrength%\n" +
                        "Reason: $reason"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        NotificationManagerCompat.from(context)
            .cancel(NOTIFICATION_ID)
    }
}
