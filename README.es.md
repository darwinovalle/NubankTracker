# NubankTracker

**🌐 Idiomas: [English](README.md) · [Español](README.es.md)**

Una aplicación Android que **registra automáticamente tus gastos de NuBank en una hoja de cálculo de Google**.

Ejecuta un receptor de notificaciones en segundo plano, observa las notificaciones de las
apps de NuBank (Brasil 🇧🇷, México 🇲🇽, Colombia 🇨🇴), extrae el **monto** y el **tipo de
transacción** de cada una y añade una fila a **tu** hoja de Google — sin captura manual ni
apps adicionales.

> **Cómo funciona en una mirada**
> NuBank envía una notificación → la app la lee → interpreta `R$ 90,00` / `$1,234.56` →
> añade `Fecha | Monto | Comercio | Notificación` a tu hoja.

---

## Índice

- [Características](#características)
- [Requisitos](#requisitos)
- [Generar el APK](#generar-el-apk)
- [Configuración de Google (una sola vez)](#configuración-de-google-una-sola-vez)
- [Instalar y configurar la app](#instalar-y-configurar-la-app)
- [Cómo funciona el registro](#cómo-funciona-el-registro)
- [Solución de problemas](#solución-de-problemas)
- [Notas de seguridad](#notas-de-seguridad)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Tecnologías](#tecnologías)

---

## Características

- 📲 **Registro automático** — un servicio en primer plano (`NotificationListenerService`)
  captura las notificaciones de NuBank sin que tengas que abrir nada.
- 💱 **Interpretación según la moneda** — entiende cada mercado de NuBank:
  - Brasil: `R$ 1.234,56` → `1234.56`
  - México: `$1,234.56` → `1234.56`
  - Colombia: `$85.000` (85 mil) → `85000.00`
  - La moneda se **detecta automáticamente** según la app de NuBank que envió la
    notificación, con opción de elegirla manualmente (Ajustes → Moneda → Auto / BRL /
    MXN / COP).
- 🔐 **Tus propias credenciales** — conectas **tu propia** hoja de Google usando:
  - un **service account (JSON)** (recomendado, sin vencimiento), o
  - tu **cuenta de Google (OAuth)**.
- 📤 **Cola sin conexión** — si no hay internet, las filas que fallan se guardan y se
  reintentan automáticamente cuando vuelve la conexión.
- 🧹 **Sin duplicados** — las notificaciones repetidas se ignoran.
- 🎨 **Interfaz Material 3** en morado NuBank, con una pantalla de configuración que
  comprueba tu estado.

---

## Requisitos

- **Android 8.0 o superior** (SDK mínimo 26)
- **Android Studio** (Ladybug o superior) con el **SDK de Android (plataforma 34)** y un
  **JDK 17+**
- Una **cuenta de Google** (para la hoja y el service account)
- La **app de NuBank** instalada en el mismo teléfono (variante BR / MX / CO)

---

## Generar el APK

### Opción A — Android Studio
1. Abre el proyecto en Android Studio.
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. El APK queda en:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Opción B — Línea de comandos
```bash
./gradlew assembleDebug
# pruebas:
./gradlew testDebugUnitTest
```
El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

> **Nota para máquinas cuyo JDK por defecto es un JRE** (sin `javac`): apunta el toolchain
> de Gradle a un JDK completo, p. ej. en `~/.gradle/gradle.properties`:
> ```
> org.gradle.java.installations.paths=/ruta/a/un/jdk/completo
> nubank.javaToolchain=<su versión de java, p. ej. 25>
> ```
> En una máquina normal con un JDK estándar no hace falta.

---

## Configuración de Google (una sola vez)

Esto se hace **una vez por usuario** (tú o cualquiera con quien compartas la app) y tarda
unos **10 minutos**. Le indica a Google "esta app puede escribir en *mi* hoja de cálculo".

### 1. Crea tu hoja de cálculo
1. Ve a [sheets.google.com](https://sheets.google.com) → **Hoja de cálculo en blanco**.
2. Ponle un nombre (p. ej. `Mis gastos NuBank`). Los encabezados de columna son opcionales.
3. Copia el **ID de la hoja** desde la URL:
   ```
   https://docs.google.com/spreadsheets/d/<ESTE_ES_EL_ID>/edit
   ```
   La cadena larga entre `/d/` y `/edit` es el ID.

### 2. Crea un service account (esto genera tu `credentials.json`)
1. Ve a [console.cloud.google.com](https://console.cloud.google.com) e inicia sesión con la
   misma cuenta de Google.
2. **Crea un proyecto** (o elige uno), p. ej. `nubank-tracker`.
3. Habilita la API de Hojas de cálculo: **APIs y servicios → Biblioteca → busca "Google
   Sheets API" → Habilitar**.
4. **IAM y administración → Cuentas de servicio → Crear cuenta de servicio** — ponle
   cualquier nombre y pulsa Crear/Listo.
5. Pulsa sobre el service account → **Claves → Añadir clave → Crear clave nueva → JSON**.
   Se descarga un archivo (`*.json`) — **ese archivo es tu `credentials.json`**.

### 3. Comparte tu hoja con el service account
1. Abre tu hoja → **Compartir**.
2. Pega el **correo** que aparece dentro del JSON (`"client_email"`, se ve así
   `nombre@proyecto.iam.gserviceaccount.com`) → dale **Editor**.
3. Guarda.

> ⚠️ Un service account **no** es un inicio de sesión normal de Google — es obligatorio
> compartir la hoja con su correo exactamente como se indica, o la app no podrá ver la hoja.

---

## Instalar y configurar la app

### Instala el APK
De una de estas dos formas:
- **ADB** (recomendado para probar): activa **Opciones de desarrollador → Depuración USB**
  en el teléfono, conéctalo por USB y ejecuta:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Instalar con `adb` normalmente evita los avisos de "aplicación desconocida".
- **O copiando el archivo**: transfiere el APK al teléfono y púlsalo. En Samsung, quizá
  tengas que desactivar temporalmente **Auto Blocker** (Ajustes → Seguridad y privacidad)
  y **Play Protect** (Play Store → perfil → Play Protect → Ajustes → desactivar análisis),
  porque la app solicita acceso a notificaciones y a la cuenta.

### Configuración (primer uso)
1. Abre **NubankTracker**.
2. Toca **Configurar** (icono de ajustes).
3. **Moneda** — deja *Automática* para detectar el país según la app de NuBank instalada,
   o elige BRL / MXN / COP.
4. **Google Sheets**:
   - Pega el **ID de la hoja** del paso 1.
   - Pega **todo el contenido** de `credentials.json` (desde `{` hasta `}`) en el campo
     *"Contenido de credentials.json"*.
5. Toca **Guardar** y luego **Probar conexión** → deberías ver **✅ Conexión exitosa** y una
   fila `TEST` en tu hoja.
6. De vuelta en la pantalla principal, activa los dos elementos de configuración:
   - **Permiso de notificaciones** (permite que la app lea las notificaciones de NuBank)
   - **Desactivar optimización de batería** (mantiene la app funcionando en segundo plano)

Listo — la siguiente notificación de NuBank se registrará automáticamente.

---

## Cómo funciona el registro

- La app solo observa las notificaciones de las apps de NuBank
  (`com.nu.production`, `com.nu.production.mx`, `com.nu.production.co`); todo lo demás se
  ignora.
- De cada notificación extrae el **monto** y el **tipo de transacción**
  (Envío / Recibido / Compra / Pago / Retiro) y escribe una fila:
  ```
  Fecha | Monto | Comercio | Notificación
  ```
- **Detección de moneda**: en modo *Automática*, el país se deduce de qué app de NuBank
  envió la notificación (los paquetes BR / MX / CO se corresponden con sus formatos de
  número). Si usas una moneda manual, se aplica a todas las notificaciones.
- Si el teléfono está sin conexión cuando llega una transacción, la fila se encola y se
  reintenta cuando vuelve la conexión (o con la siguiente notificación).

---

## Solución de problemas

| Síntoma | Causa probable / solución |
|---|---|
| "Probar conexión" falla | Comprueba: (1) que el ID de la hoja sea correcto, (2) que la hoja esté compartida con el `client_email` de tu JSON como **Editor**, (3) que el JSON pegado esté completo, (4) que la **API de Google Sheets** esté habilitada en el proyecto. |
| Los montos salen mal (`$90.00` → `9000`) | Era un error antiguo; vuelve a compilar desde este repositorio. La moneda debería detectarse sola; si no, elige **Moneda** manualmente en Ajustes. |
| Instalación bloqueada en Samsung ("App bloqueada…") | Desactiva temporalmente **Auto Blocker** (Ajustes → Seguridad y privacidad) y **Play Protect** (Play Store → Play Protect → Ajustes), instala y vuelve a activarlos. |
| No aparecen filas en la hoja | Concede **acceso a las notificaciones** (Permiso de notificaciones) y confirma que llega una notificación de NuBank; revisa que la tarjeta de estado de la app muestre todo ✅. |
| OAuth cierra sesión cada ~7 días | Google caduca los tokens OAuth mientras la pantalla de consentimiento está en modo "Pruebas". Prefiere el método con **service account**, que no caduca. |
| El servicio no arranca | Android puede haberlo detenido: la exención de optimización de batería y la notificación en primer plano lo mantienen activo. |

---

## Notas de seguridad

- El JSON del service account contiene una **clave privada**. Cualquiera con el APK puede
  extraer una clave incrustada, así que **no distribuyas una clave compartida dentro de la
  app** — cada usuario debe pegar la suya. Si piensas distribuir la app, genera un **build
  de release firmado con tu propio keystore** (no la clave de depuración) para reducir los
  avisos de instalación.
- La app usa `allowBackup="false"` y cifra el JSON guardado con el Keystore de Android, de
  modo que las credenciales no se filtren por las copias de seguridad en la nube.
- El texto de la notificación (que incluye comercio y monto) se sube a tu hoja de Google —
  es así por diseño; mantén la hoja privada.

---

## Estructura del proyecto

```
app/src/main/java/com/tracker/nubank/
├── NubankNotificationService.kt   # receptor de notificaciones + servicio en primer plano
├── NotificationParser.kt          # interpretación de monto/tipo según la moneda
├── Country.kt                     # formatos BRL / MXN / COP + mapeo de paquetes
├── SheetsManager.kt               # API de Google Sheets (service account u OAuth)
├── SettingsRepository.kt          # almacenamiento de ajustes (JSON cifrado)
├── SecurePrefs.kt                 # cifrado AES/GCM con el Keystore de Android
├── OfflineQueue.kt                # cola persistente de reintentos
├── RecentKeys.kt                  # deduplicación de notificaciones
├── MainActivity.kt                # pantalla principal + estado
└── SettingsActivity.kt            # configuración de moneda y credenciales
```

---

## Tecnologías

- **Kotlin** 2.0, **Material 3**, ViewBinding
- API v4 de Google Sheets (`google-api-client-android`, `google-auth-library-oauth2-http`)
- OAuth de Google mediante `AccountManager` + `GoogleAccountCredential` (sin
  `google-services.json`)
- Corrutinas de Kotlin
