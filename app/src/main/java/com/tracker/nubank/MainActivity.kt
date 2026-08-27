package com.tracker.nubank

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnPermissions: Button
    private lateinit var btnBattery: Button
    private lateinit var btnTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnBattery = findViewById(R.id.btnBattery)
        btnTest = findViewById(R.id.btnTest)

        btnPermissions.setOnClickListener {
            openNotificationSettings()
        }

        btnBattery.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        btnTest.setOnClickListener {
            testSheetsConnection()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasPermission = isNotificationServiceEnabled()
        val hasBatteryExemption = isBatteryOptimizationDisabled()

        val status = buildString {
            appendLine("=== Estado del Tracker ===")
            appendLine()
            appendLine("Permiso de notificaciones: ${if (hasPermission) "✅ Activo" else "❌ Inactivo"}")
            appendLine("Optimización batería: ${if (hasBatteryExemption) "✅ Desactivada" else "⚠️ Activa"}")
            appendLine()
            if (hasPermission && hasBatteryExemption) {
                appendLine("🎉 Todo listo!")
                appendLine("Las notificaciones de NuBank se guardarán automáticamente.")
            } else {
                appendLine("⚠️ Configura los permisos arriba")
            }
        }

        statusText.text = status
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
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun testSheetsConnection() {
        statusText.text = "Probando conexión con Google Sheets..."

        Thread {
            val sheetsManager = SheetsManager(this)
            kotlinx.coroutines.runBlocking {
                val success = sheetsManager.appendTransaction(
                    monto = "0.01",
                    comercio = "TEST - Eliminar esta fila",
                    notificacionOriginal = "Prueba de conexión desde la app"
                )

                runOnUiThread {
                    if (success) {
                        statusText.text = "✅ Conexión exitosa!\n\nRevisa tu Google Sheet."
                    } else {
                        statusText.text = "❌ Error de conexión.\n\nVerifica:\n- credentials.json\n- SPREADSHEET_ID\n- Permisos del Sheet"
                    }
                }
            }
        }.start()
    }
}
