package com.tracker.nubank

import android.Manifest
import android.accounts.AccountManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tracker.nubank.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsRepository
    private lateinit var sheetsManager: SheetsManager

    /** Set while we wait for the GET_ACCOUNTS permission, then open the picker. */
    private var pendingSignIn = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingSignIn) {
                pendingSignIn = false
                launchAccountPicker()
            }
            updateOAuthUi()
        }

    private val accountPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (email != null) {
                    settings.oauthEmail = email
                    sheetsManager.invalidateCache()
                    updateOAuthUi()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)
        sheetsManager = SheetsManager(this, settings)

        populateFromSettings()
        setupListeners()
    }

    private fun populateFromSettings() {
        binding.radioCurrency.check(
            when (settings.currencyMode) {
                CurrencyMode.AUTO -> R.id.radioCurrencyAuto
                CurrencyMode.MANUAL -> when (settings.manualCurrency) {
                    Country.BRL -> R.id.radioCurrencyBrl
                    Country.MXN -> R.id.radioCurrencyMxn
                    Country.COP -> R.id.radioCurrencyCop
                }
            }
        )
        binding.radioAuth.check(
            if (settings.authMode == AuthMode.OAUTH) R.id.radioAuthOAuth else R.id.radioAuthServiceAccount
        )
        binding.editSpreadsheetId.setText(settings.spreadsheetId.orEmpty())
        binding.editSaJson.setText(settings.serviceAccountJson.orEmpty())
        updateSectionVisibility()
        updateOAuthUi()
    }

    private fun setupListeners() {
        binding.radioAuth.setOnCheckedChangeListener { _, checkedId ->
            updateSectionVisibility()
            if (checkedId == R.id.radioAuthOAuth) ensureAccountsPermission()
        }
        binding.btnSignInGoogle.setOnClickListener { signInWithGoogle() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun updateSectionVisibility() {
        val oauth = binding.radioAuth.checkedRadioButtonId == R.id.radioAuthOAuth
        binding.layoutSaSection.isVisible = !oauth
        binding.layoutOAuthSection.isVisible = oauth
    }

    private fun updateOAuthUi() {
        val email = settings.oauthEmail
        binding.textOAuthStatus.text = if (email != null) {
            getString(R.string.oauth_connected, email)
        } else {
            getString(R.string.oauth_not_connected)
        }
    }

    private fun ensureAccountsPermission() {
        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
        }
    }

    private fun signInWithGoogle() {
        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            pendingSignIn = true
            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
        } else {
            launchAccountPicker()
        }
    }

    private fun launchAccountPicker() {
        // 7-arg API 23+ overload: (Account, List<Account>, String[], String, String, String[], Bundle).
        // (The deprecated 8-arg one takes a boolean 4th param — avoid it.) Result carries KEY_ACCOUNT_NAME.
        val intent = AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"), null, null, null, null
        )
        accountPickerLauncher.launch(intent)
    }

    private fun save() {
        when (binding.radioCurrency.checkedRadioButtonId) {
            R.id.radioCurrencyAuto -> settings.currencyMode = CurrencyMode.AUTO
            R.id.radioCurrencyBrl -> {
                settings.currencyMode = CurrencyMode.MANUAL
                settings.manualCurrency = Country.BRL
            }
            R.id.radioCurrencyMxn -> {
                settings.currencyMode = CurrencyMode.MANUAL
                settings.manualCurrency = Country.MXN
            }
            R.id.radioCurrencyCop -> {
                settings.currencyMode = CurrencyMode.MANUAL
                settings.manualCurrency = Country.COP
            }
        }

        val oauth = binding.radioAuth.checkedRadioButtonId == R.id.radioAuthOAuth
        settings.authMode = if (oauth) AuthMode.OAUTH else AuthMode.SERVICE_ACCOUNT

        settings.spreadsheetId = binding.editSpreadsheetId.text?.toString()
        settings.serviceAccountJson = binding.editSaJson.text?.toString()

        if (oauth && settings.oauthEmail == null) ensureAccountsPermission()

        sheetsManager.invalidateCache()
        binding.textStatus.text = getString(R.string.settings_saved)
    }

    private fun testConnection() {
        save() // test uses freshly-saved values

        if (!settings.isSheetsConfigured()) {
            binding.textStatus.text = getString(R.string.error_not_configured)
            return
        }

        binding.textStatus.text = getString(R.string.testing)
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                sheetsManager.appendTransaction(
                    monto = "0.01",
                    comercio = "TEST - Eliminar esta fila",
                    notificacionOriginal = "Prueba de conexión desde la app"
                )
            }
            binding.textStatus.text = getString(
                if (success) R.string.test_ok else R.string.test_fail
            )
        }
    }
}
