# NubankTracker

An Android app that **automatically logs your NuBank spending to a Google Sheet**.

It runs a notification-listener in the background, watches for notifications from the
NuBank apps (Brazil 🇧🇷, Mexico 🇲🇽, Colombia 🇨🇴), parses the **amount** and
**transaction type** from each one, and appends a row to **your** Google Sheet — no
manual entry, no extra apps.

> **How it works at a glance**
> NuBank sends a notification → the app reads it → parses `R$ 90,00` / `$1,234.56` →
> appends `Fecha | Monto | Comercio | Notificación` to your sheet.

---

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Build the APK](#build-the-apk)
- [One-time Google setup](#one-time-google-setup)
- [Install & configure the app](#install--configure-the-app)
- [How tracking works](#how-tracking-works)
- [Troubleshooting](#troubleshooting)
- [Security notes](#security-notes)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)

---

## Features

- 📲 **Automatic tracking** — a foreground `NotificationListenerService` captures NuBank
  notifications without you opening anything.
- 💱 **Currency-aware parsing** — understands every NuBank market:
  - Brazil: `R$ 1.234,56` → `1234.56`
  - Mexico: `$1,234.56` → `1234.56`
  - Colombia: `$85.000` (85 thousand) → `85000.00`
  - Currency is **auto-detected** from the NuBank app that sent the notification, with a
    manual override (Settings → Moneda → Auto / BRL / MXN / COP).
- 🔐 **Your own credentials** — you connect **your own** Google Sheet using either:
  - a **service-account JSON** (recommended, no expiry), or
  - your **Google account (OAuth)**.
- 📤 **Offline queue** — if there's no connection, failed rows are saved and retried
  automatically when connectivity returns.
- 🧹 **No duplicates** — repeated notifications are deduplicated.
- 🎨 **Material 3 UI** in NuBank purple, with a setup screen that checks your status.

---

## Requirements

- **Android 8.0+** (min SDK 26)
- **Android Studio** (Ladybug or newer) with the **Android SDK (platform 34)** and a
  **JDK 17+**
- A **Google account** (for the sheet + service account)
- The **NuBank app** installed on the same phone (BR / MX / CO variant)

---

## Build the APK

### Option A — Android Studio
1. Open the project in Android Studio.
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. The APK is written to:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B — Command line
```bash
./gradlew assembleDebug
# tests:
./gradlew testDebugUnitTest
```
The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

> **Note for machines whose default JDK is a JRE** (no `javac`): set the Gradle
> toolchain to a full JDK, e.g. in `~/.gradle/gradle.properties`:
> ```
> org.gradle.java.installations.paths=/path/to/a/full/jdk
> nubank.javaToolchain=<its java version, e.g. 25>
> ```
> On a normal machine with a standard JDK this isn't needed.

---

## One-time Google setup

This is done **once per user** (you or anyone you share the app with) and takes ~10
minutes. It tells Google "this app may write to *my* spreadsheet".

### 1. Create your Google Sheet
1. Go to [sheets.google.com](https://sheets.google.com) → **Blank spreadsheet**.
2. Name it (e.g. `Mis gastos NuBank`). Column headers are optional.
3. Copy the **spreadsheet ID** from the URL:
   ```
   https://docs.google.com/spreadsheets/d/<ESTE_ES_EL_ID>/edit
   ```
   The long string between `/d/` and `/edit` is the ID.

### 2. Create a service account (this produces your `credentials.json`)
1. Go to [console.cloud.google.com](https://console.cloud.google.com) and sign in with
   the same Google account.
2. **Create a project** (or select one), e.g. `nubank-tracker`.
3. Enable the Sheets API: **APIs & Services → Library → search "Google Sheets API" →
   Enable**.
4. **IAM & Admin → Service Accounts → Create service account** — give it any name, then
   Create/Done.
5. Click the new service account → **Keys → Add key → Create new key → JSON**.
   A file (`*.json`) downloads — **that file is your `credentials.json`**.

### 3. Share your sheet with the service account
1. Open your sheet → **Share**.
2. Paste the **email** that appears inside the JSON (`"client_email"`, it looks like
   `name@project.iam.gserviceaccount.com`) → grant **Editor**.
3. Save.

> ⚠️ A service account is **not** a normal Google login — you must share the sheet with
> its email exactly as above, or the app can't see the sheet.

---

## Install & configure the app

### Install the APK
Either:
- **ADB** (recommended for testing): enable **Developer options → USB debugging** on the
  phone, connect via USB, then:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Installing via `adb` usually skips the "unknown app" warnings.
- **Or copy the file**: transfer the APK to the phone and tap it. On Samsung, you may
  need to temporarily disable **Auto Blocker** (Settings → Security & privacy) and
  **Play Protect** (Play Store → profile → Play Protect → Settings → scan off) because
  the app requests notification + account access.

### Configure (first run)
1. Open **NubankTracker**.
2. Tap **Configurar** (gear).
3. **Moneda** — leave *Automática* to detect the country from the installed NuBank app,
   or pick BRL / MXN / COP.
4. **Google Sheets**:
   - Paste the **spreadsheet ID** from step 1.
   - Paste the **entire content** of `credentials.json` (from `{` to `}`) into the
     *"Contenido de credentials.json"* field.
5. Tap **Guardar**, then **Probar conexión** → you should see **✅ Conexión exitosa** and a
   `TEST` row appear in your sheet.
6. Back on the home screen, enable the two setup items:
   - **Permiso de notificaciones** (lets the app read NuBank notifications)
   - **Desactivar optimización de batería** (keeps it running in the background)

That's it — the next NuBank notification is logged automatically.

---

## How tracking works

- The app only looks at notifications from the NuBank apps
  (`com.nu.production`, `com.nu.production.mx`, `com.nu.production.co`); everything else
  is ignored.
- For each notification it extracts the **amount** and the **transaction type**
  (Envío / Recibido / Compra / Pago / Retiro) and writes a row:
  ```
  Fecha | Monto | Comercio | Notificación
  ```
- **Currency detection**: in *Automática* mode the country is inferred from which NuBank
  app posted the notification (BR / MX / CO packages map to their number formats). If you
  use a manual currency, it applies to all notifications.
- If the phone is offline when a transaction arrives, the row is queued and retried when
  connectivity returns (or on the next notification).

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| "Probar conexión" fails | Check: (1) spreadsheet ID is correct, (2) the sheet is shared with the `client_email` in your JSON as **Editor**, (3) the JSON pasted is complete, (4) the **Google Sheets API** is enabled on the project. |
| Amounts look wrong (`$90.00` → `9000`) | This was an old bug; rebuild from this repo. Currency should auto-detect — if not, set **Moneda** manually in Settings. |
| Install blocked on Samsung ("App blocked…") | Temporarily disable **Auto Blocker** (Settings → Security & privacy) and **Play Protect** (Play Store → Play Protect → Settings), install, then re-enable. |
| No rows in the sheet | Grant **notification access** (Permiso de notificaciones) and confirm a NuBank notification actually appears; check the app's status card shows all ✅. |
| OAuth signs out every ~7 days | Google expires OAuth tokens while the consent screen is in "Testing" mode. Prefer the **service-account** method, which has no expiry. |
| App doesn't start the service | Android may have killed it — the battery-optimization exemption plus the foreground notification keep it alive. |

---

## Security notes

- The service-account JSON contains a **private key**. Anyone with the APK can extract a
  bundled key, so **don't ship a shared key in the app** — each user should paste their
  own. If you plan to distribute the app, create a **release build signed with your own
  keystore** (not the debug key) to reduce install warnings.
- The app sets `allowBackup="false"` and encrypts the stored JSON with the Android
  Keystore, so credentials don't leak through cloud backups.
- Notification text (which includes merchant + amount) is uploaded to your Google Sheet —
  by design, keep the sheet private to you.

---

## Project structure

```
app/src/main/java/com/tracker/nubank/
├── NubankNotificationService.kt   # notification listener + foreground service
├── NotificationParser.kt          # currency-aware amount/type parsing
├── Country.kt                     # BRL / MXN / COP formats + package mapping
├── SheetsManager.kt               # Google Sheets API (service-account or OAuth)
├── SettingsRepository.kt          # settings storage (encrypted JSON)
├── SecurePrefs.kt                 # Android Keystore AES/GCM encryption
├── OfflineQueue.kt                # persistent retry queue
├── RecentKeys.kt                  # notification deduplication
├── MainActivity.kt                # home screen + status
└── SettingsActivity.kt            # currency + credentials configuration
```

---

## Tech stack

- **Kotlin** 2.0, **Material 3**, ViewBinding
- Google Sheets API v4 (`google-api-client-android`, `google-auth-library-oauth2-http`)
- Google OAuth via `AccountManager` + `GoogleAccountCredential` (no `google-services.json`)
- Kotlin Coroutines
