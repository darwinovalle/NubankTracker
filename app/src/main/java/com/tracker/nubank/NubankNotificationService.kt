package com.tracker.nubank

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NubankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NubankTracker"
        private const val CHANNEL_ID = "nubank_tracker_channel"
        private const val NOTIFICATION_ID = 1001

        private val NUBANK_PACKAGES = setOf(
            "com.nu.production",
            "com.nu.production.mx",
            "com.nu.production.co"
        )
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var sheetsManager: SheetsManager

    override fun onCreate() {
        super.onCreate()
        sheetsManager = SheetsManager(this)
        startForegroundService()
        Log.d(TAG, "Servicio iniciado")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "Servicio destruido")
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NuBank Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoreando gastos de NuBank"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NuBank Tracker Activo")
            .setContentText("Monitoreando notificaciones...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (packageName !in NUBANK_PACKAGES) {
            return
        }

        Log.d(TAG, "Notificación de NuBank detectada")

        val extras = sbn.notification.extras

        // Capturar TÍTULO y TEXTO
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d(TAG, "Título: $title")
        Log.d(TAG, "Texto: $text")

        // Combinar título + texto para tener toda la información
        val fullNotification = "$title | $text"

        val transactionData = NotificationParser.parse(title, text)

        if (transactionData != null) {
            serviceScope.launch {
                val success = sheetsManager.appendTransaction(
                    monto = transactionData.monto,
                    comercio = transactionData.comercio,
                    notificacionOriginal = fullNotification
                )

                if (success) {
                    Log.d(TAG, "✅ Guardado en Sheets: ${transactionData.monto}")
                } else {
                    Log.e(TAG, "❌ Error guardando en Sheets")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }
}