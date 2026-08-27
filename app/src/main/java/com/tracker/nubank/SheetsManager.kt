package com.tracker.nubank

import android.content.Context
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SheetsManager(private val context: Context) {

    companion object {
        // ⚠️ REEMPLAZA CON TU ID DE SHEET (de Fase 1.4)
        private const val SPREADSHEET_ID = "1bTXJMqm0y9rJtBKFki9EdgoKEz_BAx6z2-XI9tUutXc"
        private const val RANGE = "A:D"
    }

    private fun getSheetsService(): Sheets {
        val inputStream = context.assets.open("credentials.json")
        val credentials = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf(SheetsScopes.SPREADSHEETS))

        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        return Sheets.Builder(
            httpTransport,
            jsonFactory,
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName("NubankTracker")
            .build()
    }

    suspend fun appendTransaction(
        monto: String,
        comercio: String,
        notificacionOriginal: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getSheetsService()

            val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            val values = listOf(
                listOf(fecha, monto, comercio, notificacionOriginal)
            )

            val body = ValueRange().setValues(values)

            service.spreadsheets().values()
                .append(SPREADSHEET_ID, RANGE, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
