# 📱 Fase 2: Auth Android — Guía de instalación

Esta guía te dice **exactamente dónde poner cada archivo** y qué líneas tocar.

---

## 📋 Resumen

| Acción | Archivo |
|--------|---------|
| **Modificar** | `app/build.gradle.kts` |
| **Modificar** | `app/src/main/java/com/mew/animemew/MainActivity.kt` |
| **Modificar** | `app/src/main/java/com/mew/animemew/ui/screens/MainAppScreen.kt` |
| **Modificar** | `app/src/main/java/com/mew/animemew/ui/screens/SettingsScreen.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/data/auth/AuthApiModels.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/data/auth/AuthService.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/data/auth/SessionManager.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/data/auth/AuthRepository.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/data/remote/ApiClient.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/ui/viewmodels/AuthViewModel.kt` |
| **Crear nuevo** | `app/src/main/java/com/mew/animemew/ui/screens/AuthScreen.kt` |

---

## 1️⃣ build.gradle.kts — añadir dependencias

Abre `app/build.gradle.kts`. En el bloque `dependencies { ... }`, añade al final (antes de la llave de cierre `}`):

```kotlin
// === Cloud Sync — Fase 2: Auth ===
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.google.code.gson:gson:2.11.0")
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// === Fase 3: Sync Manager (lo añadimos ya) ===
implementation("androidx.work:work-runtime-ktx:2.9.1")
```

**Sync Gradle** (Android Studio te lo pedirá con un banner amarillo → "Sync Now").

---

## 2️⃣ Crear directorios nuevos

Si no existen, crea estos directorios:

```bash
app/src/main/java/com/mew/animemew/data/auth/
app/src/main/java/com/mew/animemew/data/remote/
```

En Android Studio: click derecho en `com.mew.animemew.data` → New → Package → `auth`. Igual con `remote`.

---

## 3️⃣ Crear los 4 archivos en `data/auth/`

Copia estos archivos a `app/src/main/java/com/mew/animemew/data/auth/`:

- `AuthApiModels.kt`
- `AuthService.kt`
- `SessionManager.kt`
- `AuthRepository.kt`

---

## 4️⃣ Crear el archivo en `data/remote/`

Copia a `app/src/main/java/com/mew/animemew/data/remote/`:

- `ApiClient.kt`

### ⚠️ IMPORTANTE: cambia la URL

Abre `ApiClient.kt` y verifica esta línea:

```kotlin
private const val BASE_URL = "https://animemew-api.duckdns.org/"
```

Si tu dominio de DuckDNS es distinto, cámbialo. **Debe terminar con `/`** (slash final).

---

## 5️⃣ Crear el ViewModel

Copia a `app/src/main/java/com/mew/animemew/ui/viewmodels/`:

- `AuthViewModel.kt`

---

## 6️⃣ Crear el AuthScreen

Copia a `app/src/main/java/com/mew/animemew/ui/screens/`:

- `AuthScreen.kt`

---

## 7️⃣ Reemplazar los 4 archivos existentes

Sobrescribe estos archivos con las versiones nuevas:

- `MainActivity.kt` — añade `SessionManager` y lo pasa a `MainAppScreen`
- `MainAppScreen.kt` — añade la ruta `"auth"` y pasa `sessionManager` a `SettingsScreen`
- `SettingsScreen.kt` — añade la sección "Cuenta" con login/logout

---

## 8️⃣ Verificar permiso de internet

En `app/src/main/AndroidManifest.xml` debe estar:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Si ya lo tienes (de AniList), no toques nada.

---

## 9️⃣ Compilar y probar

1. **Build → Make Project** (Ctrl+F9)
2. Si hay errores, pégalos y los resolvemos
3. **Run** en tu celular o emulador

### Flujo de prueba

1. Abre la app → ves el Home (sin login, todo funciona igual)
2. Ve a **Ajustes** → verás la nueva sección **"CUENTA"** arriba con "Iniciar sesión"
3. Toca "Iniciar sesión" → abre AuthScreen
4. Cambia a tab **"Crear cuenta"**
5. Registra: `test@test.com` / `test1234` / `test1234`
6. Si todo va bien → vuelve a Settings automáticamente
7. Ahora verás tu email + "Sincronización en la nube activa" + "Cerrar sesión"
8. Toca "Cerrar sesión" → confirmación → vuelve al estado sin sesión

### Test desde el server

Mientras pruebas, puedes ver los logs del server en tiempo real:

```bash
$ sudo journalctl -u animemew-api -f
```

Verás algo como:
```
INFO: Registro OK: user_id=1 email=test@test.com
INFO: Login OK: user_id=1 email=test@test.com
```

---

## 🔍 Estructura final de archivos

```
app/src/main/java/com/mew/animemew/
├── MainActivity.kt                          [MODIFICADO]
├── data/
│   ├── auth/
│   │   ├── AuthApiModels.kt                 [NUEVO]
│   │   ├── AuthService.kt                   [NUEVO]
│   │   ├── SessionManager.kt                [NUEVO]
│   │   └── AuthRepository.kt                [NUEVO]
│   ├── remote/
│   │   └── ApiClient.kt                     [NUEVO]
│   └── local/
│       └── ThemePreferences.kt              [sin tocar]
└── ui/
    ├── viewmodels/
    │   ├── AuthViewModel.kt                 [NUEVO]
    │   └── SettingsViewModel.kt             [sin tocar]
    └── screens/
        ├── AuthScreen.kt                    [NUEVO]
        ├── MainAppScreen.kt                 [MODIFICADO]
        ├── SettingsScreen.kt                [MODIFICADO]
        └── ... (resto sin tocar)
```

---

## 🧪 Tests finales

- [ ] App compila sin errores
- [ ] App abre normalmente (sin login, todo funciona)
- [ ] Settings muestra sección "CUENTA"
- [ ] Tap "Iniciar sesión" → abre AuthScreen
- [ ] Tab switch funciona (Login ↔ Register)
- [ ] Registrar usuario nuevo → vuelve a Settings logueado
- [ ] Cerrar sesión → vuelve a estado sin sesión
- [ ] Logs del server muestran las peticiones
- [ ] `https://animemew-api.duckdns.org/docs` → prueba endpoints manualmente

---

## 🚨 Troubleshooting

### Error: "Unable to resolve host"
Tu celular no tiene internet, o la URL en `ApiClient.kt` está mal escrita.

### Error: "SSL handshake failed"
El certificado SSL no se instaló bien. Prueba:
```bash
$ curl https://animemew-api.duckdns.org/health
```
Desde tu celular con datos móviles (no WiFi local). Si da error de SSL, corre certbot de nuevo:
```bash
$ sudo certbot --apache -d animemew-api.duckdns.org
```

### Error: "404 Not Found" en /auth/register
Revisa que `BASE_URL` termine con `/`. Si no tiene slash, Retrofit concatena mal.

### Error de compilación: "Unresolved reference: NeonGradient"
Falta importar. Verifica que `Color.kt` tenga `NeonGradient`, `AppBackgroundBrush`, `TopBarGlowBrush`, `LogoGlowBrush`, `SectionAccentBrush` definidos (los añadimos en la Fase 1).

### Crash al abrir Settings: "SessionManager not provided"
Verifica que `MainAppScreen` reciba `sessionManager` como parámetro y lo pase a `SettingsScreen`. Revisa `MainActivity.kt` que crea el `SessionManager`.

---

## ✅ Siguiente paso

Cuando tengas todos los tests en verde, avísame y pasamos a la **Fase 3: Sync Manager** — donde conectamos el auth con tus Room DBs (listas, historial, favoritos) para que todo se sincronice en la nube automáticamente.

¡Vamos! 🚀
