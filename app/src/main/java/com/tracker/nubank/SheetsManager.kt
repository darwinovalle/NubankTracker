package com.tracker.nubank

import android.content.Context
import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes rows to a Google Sheet.
 *
 * Auth is resolved at runtime from [SettingsRepository]:
 * - **Service account**: the user-pasted JSON is turned into a GoogleCredentials.
 * - **OAuth**: the selected Google account is used via [GoogleAccountCredential],
 *   which auto-refreshes tokens through AccountManager (no google-services.json).
 *
 * The Sheets service is built once and cached; call [invalidateCache] after the
 * user changes their credentials or spreadsheet.
 */
class SheetsManager(
    context: Context,
    private val settings: SettingsRepository
) {
    companion object {
        private const val TAG = "NubankTracker"
        private const val RANGE = "A:D"
    }

    private val appContext = context.applicationContext

    @Volatile
    private var cachedService: Sheets? = null
    private val lock = Any()

    fun invalidateCache() {
        synchronized(lock) { cachedService = null }
    }

    private fun getSheetsService(): Sheets {
        cachedService?.let { return it }
        synchronized(lock) {
            cachedService?.let { return it }
            val httpTransport = AndroidHttp.newCompatibleTransport() // Android-compatible TLS
            val jsonFactory = GsonFactory.getDefaultInstance()

            val credential = when (settings.authMode) {
                AuthMode.OAUTH -> {
                    val email = settings.oauthEmail
                        ?: throw IllegalStateException("No se configuró una cuenta de Google")
                    GoogleAccountCredential.usingOAuth2(
                        appContext,
                        listOf(SheetsScopes.SPREADSHEETS)
                    ).apply { selectedAccountName = email }
                }
                AuthMode.SERVICE_ACCOUNT -> {
                    val json = settings.serviceAccountJson
                        ?: throw IllegalStateException("No se configuró un service account")
                    val credentials = GoogleCredentials
                        .fromStream(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
                        .createScoped(listOf(SheetsScopes.SPREADSHEETS))
                    HttpCredentialsAdapter(credentials)
                }
            }

            val service = Sheets.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("NubankTracker")
                .build()
            cachedService = service
            return service
        }
    }

    /**
     * Appends a row with the current timestamp to columns A:D.
     * @return true on success, false on any failure (caller may queue the row).
     */
    suspend fun appendTransaction(
        monto: String,
        comercio: String,
        notificacionOriginal: String
    ): Boolean {
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        return appendRow(fecha, monto, comercio, notificacionOriginal)
    }

    /**
     * Appends a previously-queued row, preserving its original [fecha].
     * @return true on success, false on any failure.
     */
    suspend fun appendQueuedRow(
        fecha: String,
        monto: String,
        comercio: String,
        notificacionOriginal: String
    ): Boolean = appendRow(fecha, monto, comercio, notificacionOriginal)

    private suspend fun appendRow(
        fecha: String,
        monto: String,
        comercio: String,
        notificacionOriginal: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = settings.spreadsheetId
                ?: throw IllegalStateException("No se configuró el ID de la hoja")

            val values = listOf(listOf(fecha, monto, comercio, notificacionOriginal))
            val body = ValueRange().setValues(values)

            getSheetsService().spreadsheets().values()
                .append(spreadsheetId, RANGE, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            true
        } catch (e: UserRecoverableAuthIOException) {
            // OAuth token invalid / account removed → ask the user to sign in again.
            Log.w(TAG, "OAuth requiere re-autenticación: ${e.message}")
            settings.clearOAuth()
            invalidateCache()
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error guardando en Sheets: ${e.message}")
            false
        }
    }
}
