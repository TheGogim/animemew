# 🐱 AnimeMew

> Streaming de anime con sincronización en la nube, soporte multi-temporada y experiencia premium en móvil, tablet y Android TV.

![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-blue)
![Language](https://img.shields.io/badge/Language-Kotlin-purple)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-orange)
![Backend](https://img.shields.io/badge/Backend-FastAPI%20%2B%20MariaDB-green)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## ✨ Características

### 🎬 Reproductor premium
- **Reproductor personalizado** con controles de glassmorfismo
- **Múltiples servidores** por episodio (selección manual)
- **Reanudar reproducción** desde donde lo dejaste
- **Auto-avance** al siguiente episodio
- **Optimizado para TV** con navegación D-Pad completa
- **Barra de progreso navegable** con D-Pad (seek de 5%)
- **Soporte HLS y MP4** con buffer optimizado

### ☁️ Sincronización en la nube
- **Cuenta de usuario** con registro/login
- **Sync cifrado de extremo a extremo** (AES-256-GCM)
- **Historial de visualización** sincronizado entre dispositivos
- **Listas personalizadas** (Favoritos, Vistos, Viendo + custom)
- **Backup automático** cada 15 minutos vía WorkManager

### 📺 Multi-temporada inteligente
- **Cadena de temporadas** automática (detecta secuelas/prequelas)
- **Etiquetado correcto** (T1 E5, T2 E1, etc.)
- **Auto-avance entre temporadas** sin intervención
- **Detección de OVAs, películas y especiales**

### 🔄 Sistema "En espera" para animes en emisión
- Detecta automáticamente cuando terminas el último episodio disponible
- Verifica disponibilidad real antes de habilitar el siguiente
- Buffer de 3 horas para respetar horarios de emisión
- Reintentos automáticos cada hora si el episodio aún no está disponible
- **NUNCA** marca como "visto" automáticamente — el usuario siempre decide

### 📱 Diseño premium
- **Tema neón** morado/magenta con gradientes
- **Modo oscuro/claro/sistema**
- **Splash screen** personalizado
- **Edge-to-edge** con soporte para notches
- **Animaciones suaves** y transiciones fluidas
- **Skeleton loading** con shimmer

### 📺 Android TV
- **NavigationRail** en landscape
- **D-Pad navigation** completa en todas las pantallas
- **Focus management** con escala y bordes neón
- **Banner ads** compatibles (sin interstitials intrusivos)
- **Soporte leanback** completo

### 🔒 Versionado forzado
- Control de versión mínimo desde el servidor
- Pantalla de bloqueo con changelog en markdown
- Forzar actualización obligatoria cuando hay cambios críticos

### 📊 Home configurable remotamente
- Secciones dinámicas desde el backend (sin actualizar la app)
- Tipos: popular, trending, por género, en emisión
- Caché local con fallback

---

## 🏗️ Arquitectura

### App Android
```
├── data/
│   ├── auth/          → SessionManager, AuthRepository, EncryptedSharedPreferences
│   ├── remote/        → Retrofit ApiClient
│   ├── local/         → Room Database (v11), DAOs, Entities
│   ├── season/        → SeasonChainResolver (AniList PREQUEL/SEQUEL)
│   ├── sync/          → SyncManager, SnapshotBuilder, CryptoHelper, SyncWorker
│   ├── airing/        → AiringController ("En espera" system)
│   ├── ads/           → AdManager (Start.io SDK), BannerAdView
│   ├── version/       → VersionChecker (forced updates)
│   └── HomeRepository → Remote config con caché
├── scraper/           → Episode resolution (multi-source)
├── ui/
│   ├── screens/       → Home, Search, Lists, Settings, Detail, Player, Auth
│   ├── components/    → AnimeCard, FocusUtils (TV), KeepScreenOn
│   └── viewmodels/    → MVVM con StateFlow
└── network/           → AniList GraphQL client (Apollo)
```

### Backend
```
backend/
├── main.py            → FastAPI app
├── schema.sql         → MariaDB schema
├── requirements.txt   → Python deps
├── animemew-api.service → systemd service
└── animemew-api.conf  → Apache reverse proxy
```

**Stack:**
- **FastAPI** + **MariaDB** + **JWT** + **bcrypt**
- **AES-256-GCM** para cifrado de snapshots (client-side)
- **PBKDF2** para derivación de clave desde password
- Deploy en Debian con Apache + Let's Encrypt + DuckDNS

---

## 🚀 Instalación

### Requisitos
- Android Studio Hedgehog o superior
- Android SDK 24+ (minSdk)
- Kotlin 1.9+
- Cuenta en [AniList](https://anilist.co) (gratuita, para metadatos)

### Compilar la app
```bash
# 1. Clonar el repo
git clone https://github.com/tu-usuario/animemew.git
cd animemew

# 2. Abrir en Android Studio
# 3. Sync Gradle
# 4. Run en dispositivo/emulador
```

### Configurar backend
Mira la documentación en `backend/` para desplegar el servidor en Debian/Ubuntu.

---

## 🎯 Uso

### Primer inicio
1. **Pantalla de bienvenida** → splash screen
2. **Registro/Login** (opcional pero recomendado para sync)
3. **Home** con secciones: Populares, Tendencias, Géneros, En emisión
4. **Buscar** animes por título o género
5. **Ver detalles** → marcar como favorito, añadir a listas, reproducir

### Reproducir
1. Click en un anime → detalles
2. Click en un episodio → player
3. **Controles:**
   - Tap/click en pantalla → mostrar/ocultar controles
   - D-Pad izq/der → seek 5% (en TV)
   - Botón play/pausa, siguiente/anterior, servidores
4. Al terminar (80%+) → avanza automáticamente o marca "En espera"

### Android TV
- Navega con D-Pad
- **OK/Center** = seleccionar
- **Back** = volver
- Controles del player se ocultan a los 5s, reviven con cualquier botón

---

## 🔧 Configuración

### Cambiar el servidor backend
En `data/remote/ApiClient.kt`:
```kotlin
private const val BASE_URL = "https://tu-dominio.duckdns.org/"
```

### Cambiar la config del Home
Edita el endpoint `/api/home/config` en `backend/main.py`.

### Forzar actualización de la app
1. Sube el APK a GitHub Releases
2. En `backend/main.py`, endpoint `/api/version`:
   - Sube `min_version`
   - Actualiza `download_url`
   - Escribe el changelog en `description` (markdown)
3. Reinicia: `sudo systemctl restart animemew-api`

### Desactivar anuncios a un usuario
```bash
curl -X PUT https://tu-dominio.duckdns.org/admin/users/USER_ID/ads \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ads_enabled": false}'
```

---

## 🛡️ Privacidad

- **Sincronización cifrada**: tus datos se suben cifrados con AES-256, ni nosotros podemos verlos
- **Password nunca se envía**: solo se usa para derivar la clave de cifrado localmente
- **AniList**: metadatos de anime (títulos, sinopsis, scores) vienen de AniList
- **Start.io**: anuncios (pueden recopilar datos anónimos para publicidad)
- **Sin tracking intrusivo**: no usamos analytics ni seguimiento de comportamiento

---

## 📦 Dependencias principales

| Librería | Uso |
|----------|-----|
| Jetpack Compose | UI declarativa |
| Compose Material3 | Componentes Material You |
| Navigation Compose | Navegación entre pantallas |
| Room | Base de datos local |
| Apollo GraphQL | Cliente AniList |
| Retrofit + OkHttp | Cliente HTTP para backend |
| EncryptedSharedPreferences | Storage seguro de tokens |
| WorkManager | Sync periódico en background |
| Coil | Carga de imágenes |
| ExoPlayer (Media3) | Reproductor de video |
| Start.io SDK | Anuncios |
| Coroutines + Flow | Programación async |

---

## 📱 Dispositivos soportados

- ✅ **Móvil** (portrait/landscape)
- ✅ **Tablet** (landscape optimizado)
- ✅ **Android TV** (D-Pad completo)
- ✅ **Foldables** (adaptive layout)

---

## 🤝 Contribuir

Por ahora es un proyecto personal, pero si quieres contribuir:

1. Fork el repo
2. Crea un branch (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -m 'Add nueva funcionalidad'`)
4. Push al branch (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

---

## 📄 Licencia

MIT License — libre uso, modificación y distribución.

---

## 🙏 Agradecimientos

- **[AniList](https://anilist.co)** — Por su excelente API pública de metadatos de anime
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Por hacer la UI declarativa en Android
- **[FastAPI](https://fastapi.tiangolo.com)** — Por el backend rápido y moderno

---

## ⚠️ Aviso legal

AnimeMew es una aplicación de streaming que agrega contenido de fuentes públicas. No aloja ningún contenido de video. Todo el contenido es proporcionado por terceros y AnimeMew no se responsabiliza de su disponibilidad o legalidad.

Los metadatos de anime (títulos, sinopsis, scores, imágenes de portada) son proporcionados por la API pública de AniList bajo sus términos de uso.

---

<div align="center">
  <p>Hecho con 💜 por el equipo de AnimeMew</p>
  <p>¿Disfrutas la app? ¡Considera apoyar con un premium sin anuncios!</p>
</div>
