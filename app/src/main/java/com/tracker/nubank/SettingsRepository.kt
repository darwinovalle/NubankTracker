package com.tracker.nubank

import android.content.Context
import android.content.SharedPreferences

/** How the app authenticates with the Google Sheets API. */
enum class AuthMode { SERVICE_ACCOUNT, OAUTH }

/** Currency selection: auto-detect from the NuBank app, or a fixed country. */
enum class CurrencyMode { AUTO, MANUAL }

/**
 * Persists user settings (auth, spreadsheet id, currency) in SharedPreferences.
 *
 * The service-account JSON is the only secret and is stored Keystore-encrypted via
 * [SecurePrefs]. The OAuth path stores no secret at all — the refresh token lives in
 * the OS account store.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = SecurePrefs()

    var authMode: AuthMode
        get() = AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, AuthMode.SERVICE_ACCOUNT.name)!!)
        set(value) = prefs.edit().putString(KEY_AUTH_MODE, value.name).apply()

    /** Service-account JSON; `null` when not configured or not decryptable. */
    var serviceAccountJson: String?
        get() = prefs.getString(KEY_SA_JSON, null)?.let { securePrefs.decrypt(it) }
        set(value) {
            val stored = value?.takeIf { it.isNotBlank() }?.let { securePrefs.encrypt(it) }
            prefs.edit().putString(KEY_SA_JSON, stored).apply()
        }

    var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_ID, value?.trim()).apply()

    var oauthEmail: String?
        get() = prefs.getString(KEY_OAUTH_EMAIL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_OAUTH_EMAIL, value?.trim()).apply()

    var currencyMode: CurrencyMode
        get() = CurrencyMode.valueOf(prefs.getString(KEY_CURRENCY_MODE, CurrencyMode.AUTO.name)!!)
        set(value) = prefs.edit().putString(KEY_CURRENCY_MODE, value.name).apply()

    var manualCurrency: Country
        get() = try {
            Country.valueOf(prefs.getString(KEY_MANUAL_CURRENCY, Country.BRL.name)!!)
        } catch (e: IllegalArgumentException) {
            Country.BRL
        }
        set(value) = prefs.edit().putString(KEY_MANUAL_CURRENCY, value.name).apply()

    /** Whether Sheets can currently be written to with the configured auth. */
    fun isSheetsConfigured(): Boolean {
        if (spreadsheetId == null) return false
        return when (authMode) {
            AuthMode.OAUTH -> oauthEmail != null
            AuthMode.SERVICE_ACCOUNT -> serviceAccountJson != null
        }
    }

    /**
     * Resolves the currency to use for a notification: the manually chosen country
     * when [CurrencyMode.MANUAL], otherwise the country of the NuBank package that
     * posted the notification (falling back to the manual default when unknown).
     */
    fun resolveCurrency(packageName: String?): Country {
        return if (currencyMode == CurrencyMode.MANUAL) {
            manualCurrency
        } else {
            packageName?.let { Country.fromPackage(it) } ?: manualCurrency
        }
    }

    fun clearOAuth() {
        oauthEmail = null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "nubank_tracker_settings"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_SA_JSON = "sa_json"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_OAUTH_EMAIL = "oauth_email"
        private const val KEY_CURRENCY_MODE = "currency_mode"
        private const val KEY_MANUAL_CURRENCY = "manual_currency"
    }
}
