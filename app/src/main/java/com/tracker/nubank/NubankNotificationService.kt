package com.tracker.nubank

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
    private val recentKeys = RecentKeys()

    private lateinit var settings: SettingsRepository
    private lateinit var sheetsManager: SheetsManager
    private lateinit var offlineQueue: OfflineQueue
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        sheetsManager = SheetsManager(this, settings)
        offlineQueue = OfflineQueue(getSharedPreferences("nubank_tracker_queue", MODE_PRIVATE))
        startForegroundService()
        registerConnectivityCallback()
        drainQueue()
        Log.d(TAG, "Servicio iniciado")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterConnectivityCallback()
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

        // Requires targetSdk 34: the specialUse subtype is declared in the manifest.
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (packageName !in NUBANK_PACKAGES) return

        // Dedupe: Android may re-post the same notification (some ROMs, re-posts).
        if (recentKeys.wasRecentlySeen(sbn.key)) {
            Log.d(TAG, "Notificación duplicada, ignorada: ${sbn.key}")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullNotification = "$title | $text"

        Log.d(TAG, "Notificación de NuBank detectada: $fullNotification")

        val country = settings.resolveCurrency(packageName)
        val transactionData = NotificationParser.parse(title, text, country)

        if (transactionData == null) {
            Log.d(TAG, "No es una transacción, ignorada")
            return
        }

        serviceScope.launch {
            val success = sheetsManager.appendTransaction(
                monto = transactionData.monto,
                comercio = transactionData.comercio,
                notificacionOriginal = fullNotification
            )

            if (success) {
                Log.d(TAG, "✅ Guardado en Sheets: ${transactionData.monto}")
            } else {
                Log.w(TAG, "❌ Error guardando, encolando: ${transactionData.monto}")
                offlineQueue.add(
                    listOf(
                        timestamp(),
                        transactionData.monto,
                        transactionData.comercio,
                        fullNotification
                    )
                )
            }
            drainQueue()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    /** Flushes pending rows when connectivity is available. */
    private fun drainQueue() {
        serviceScope.launch {
            var drained = 0
            var row = offlineQueue.peek()
            while (row != null && drained < OfflineQueue.MAX_ROWS_PER_DRAIN) {
                val ok = sheetsManager.appendQueuedRow(
                    fecha = row[0],
                    monto = row[1],
                    comercio = row[2],
                    notificacionOriginal = row[3]
                )
                if (ok) {
                    offlineQueue.removeFirst(1)
                    drained++
                } else {
                    break // still offline / auth problem → try again later
                }
                row = offlineQueue.peek()
            }
        }
    }

    private fun registerConnectivityCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = drainQueue()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) drainQueue()
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun unregisterConnectivityCallback() {
        networkCallback?.let {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(it)
            networkCallback = null
        }
    }

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
}
