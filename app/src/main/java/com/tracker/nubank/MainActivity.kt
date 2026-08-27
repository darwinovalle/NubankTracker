package com.tracker.nubank

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tracker.nubank.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnPermissions.setOnClickListener { openNotificationSettings() }
        binding.btnBattery.setOnClickListener { requestBatteryOptimizationExemption() }
        binding.btnTest.setOnClickListener { testSheetsConnection() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Android 13+ needs POST_NOTIFICATIONS at runtime for the foreground notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateStatus() {
        val hasPermission = isNotificationServiceEnabled()
        val hasBatteryExemption = isBatteryOptimizationDisabled()
        val sheetsReady = settings.isSheetsConfigured()
        val currencyLabel = when (settings.currencyMode) {
            CurrencyMode.AUTO -> getString(R.string.currency_auto)
            CurrencyMode.MANUAL -> settings.manualCurrency.name
        }

        val status = buildString {
            appendLine("=== Estado del Tracker ===")
            appendLine()
            appendLine(
                "Permiso de notificaciones: ${if (hasPermission) "✅ Activo" else "❌ Inactivo"}"
            )
            appendLine(
                "Optimización batería: ${if (hasBatteryExemption) "✅ Desactivada" else "⚠️ Activa"}"
            )
            appendLine()
            appendLine("Moneda: $currencyLabel")
            appendLine("Google Sheets: ${if (sheetsReady) "✅ Configurado" else "❌ Falta configurar"}")
            appendLine()
            if (hasPermission && hasBatteryExemption && sheetsReady) {
                appendLine("🎉 Todo listo!")
                appendLine("Las notificaciones de NuBank se guardarán automáticamente.")
            } else {
                appendLine("⚠️ Revisa permisos y Ajustes")
            }
        }

        binding.statusText.text = status
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val componentName = ComponentName(this, NubankNotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    // Note: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS would block Google Play distribution.
    // Kept intentionally for personal / sideloaded use.
    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun testSheetsConnection() {
        if (!settings.isSheetsConfigured()) {
            binding.statusText.text = getString(R.string.error_not_configured)
            return
        }

        binding.statusText.text = getString(R.string.testing)
        val sheetsManager = SheetsManager(this, settings)
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                sheetsManager.appendTransaction(
                    monto = "0.01",
                    comercio = "TEST - Eliminar esta fila",
                    notificacionOriginal = "Prueba de conexión desde la app"
                )
            }
            binding.statusText.text = getString(
                if (success) R.string.test_ok else R.string.test_fail
            )
        }
    }
}
