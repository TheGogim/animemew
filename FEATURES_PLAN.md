# Plan de Features — AnimeMew

**Última actualización:** 2026-09-09  
**Estado:** Post-Fase 3 (AniList fix + AiringController rediseñado + Pestaña Horarios)

---

## ✅ Features que ya están implementadas (no tocar)

| Feature | Dónde está | Notas |
|---|---|---|
| Skip OP/ED en player | `PlayerScreen.kt` | Botón "Saltar OP" visible. Solo manual, no auto-skip. |
| Auto-play siguiente episodio | `PlayerViewModel.kt` | Siempre que haya siguiente disponible. |
| Android TV support | `MainAppScreen.kt` (NavigationRail) | En landscape usa rail izquierdo. Funciona en TV. |
| Cast (Chromecast) | `cast/` | Con NanoHTTPD embebido. |
| Sync en la nube | `data/sync/` | Cifrado AES + backend propio. |
| Pestaña Horarios | `ScheduleScreen.kt` | Por día de la semana, hora Colombia, 2 tabs. |
| AiringController rediseñado | `data/airing/AiringController.kt` | Detecta nuevos episodios, marca `hasNewEpisode` sin tocar `episodeNumber`. |
| Crash overlay | `ui/components/CrashOverlay.kt` | Captura errores, botón copiar. |
| Fix de AniList | `network/AniListClient.kt` | Headers de navegador para bypasear bloqueo de bots. |

---

## 🚀 Features aprobadas para implementar AHORA

### A2. Picture-in-Picture (PiP) + background audio

- **Qué hace**: Al salir de la app con un video reproduciéndose, se muestra ventana flotante PiP. Modo "audio background" permite seguir escuchando con pantalla apagada.
- **Dificultad**: Baja (ExoPlayer lo soporta nativo, solo declarar `supportsPictureInPicture` en manifest + `onUserLeaveHint`).
- **Casos de uso**: Ver anime mientras se usa WhatsApp/TikTok. Escuchar anime como podcast mientras camina.
- **Archivos a tocar**: `AndroidManifest.xml`, `MainActivity.kt`, `PlayerScreen.kt`, `PlayerViewModel.kt`.
- **Estado**: ✅ Aprobado, pendiente de implementar.

### A7. Gestos avanzados del player

- **Qué hace**: Deslizar verticalmente en mitad izquierda = brillo, en mitad derecha = volumen. Doble tap izquierda/derecha = retroceder/avanzar 10s. Long-press = velocidad 2x temporal.
- **Dificultad**: Media (`GestureDetector` + `ScaleGestureDetector` sobre el `PlayerView`).
- **Plataformas**: Solo móvil. TV queda igual (D-pad).
- **Archivos a tocar**: `PlayerScreen.kt` (nuevo composable `GestureOverlay`).
- **Estado**: ✅ Aprobado, pendiente de implementar.

### A8. Velocidad de reproducción con pitch correction

- **Qué hace**: Selector de velocidad de 0.5x hasta **1.5x máximo** (tu límite), con corrección de tono.
- **Dificultad**: Baja (`Player.setPlaybackParameters(PlaybackParameters(speed, pitch))`).
- **Archivos a tocar**: `PlayerScreen.kt` (nuevo botón en top bar o settings overlay), `PlayerViewModel.kt` (estado de velocidad).
- **Estado**: ✅ Aprobado, pendiente de implementar.

### A9. Lock de pantalla y orientación

- **Qué hace**: Botón "lock" en el player que bloquea todos los controles táctiles y fija la orientación. Solo se desbloquea con botón específico.
- **Dificultad**: Baja (overlay `FrameLayout` + `requestedOrientation`).
- **Archivos a tocar**: `PlayerScreen.kt`.
- **Estado**: ✅ Aprobado, pendiente de implementar.

### C1. Notificaciones push de nuevos episodios

- **Qué hace**: Cuando el `AiringController` detecta que se publicó un nuevo episodio de un anime que el usuario tiene en "Viendo" o "Favoritos", lanza una notificación local con acción "Ver ahora" que abre el reproductor en ese episodio.
- **Cómo funciona sin app en background**: 
  - **Sí requiere algo en background**, pero ya lo tenés: el `SyncWorker` (WorkManager) corre cada 15 min. Lo único que falta es que después de `checkAllWaiting()` llame a `NotificationManager` para los animes donde `hasNewEpisode` pasó de `false` a `true`.
  - **No requiere FCM ni servidor push**. Las notificaciones son locales (no push remotas). El WorkManager despierta la app cada 15 min, hace el check, y si hay nuevos, lanza notificación.
  - **Limitación**: si el usuario mata la app (force-stop) o reinicia el dispositivo y no abre más la app, no se va a ejecutar. Pero esto es comportamiento estándar de Android.
- **Dificultad**: Media.
- **Archivos a tocar**: `SyncWorker.kt` (enganchar a `checkAllWaiting()`), nuevo `NotificationsManager.kt`, `AndroidManifest.xml` (permiso `POST_NOTIFICATIONS`).
- **Estado**: ✅ Aprobado, pendiente de implementar.

### C4. Notificación de segundas temporadas anunciadas

- **Qué hace**: Cuando AniList actualiza un anime con `Media.relations` nuevo de tipo SEQUEL o cuando su `status` cambia a `NOT_YET_RELEASED` con fecha, avisar a los usuarios que lo tienen en "Visto".
- **Dificultad**: Media.
- **Casos de uso**: Recuperar usuarios inactivos (vieron un anime hace 2 años, no saben que salió segunda temporada).
- **Archivos a tocar**: Nuevo `SequelDetectorWorker.kt` (corre 1 vez por semana), `NotificationsManager.kt` (compartido con C1).
- **Estado**: ✅ Aprobado, pendiente de implementar.

### A5 (parcial). Visor de episodios en el player

- **Qué hace**: En el player, agregar un panel con todos los episodios (lista o cuadrícula con números como en detalles pero más chiquitos) para cambiar rápido sin ir uno por uno.
- **Dificultad**: Baja-Media.
- **Archivos a tocar**: `PlayerScreen.kt` (nuevo `BottomSheet` con `LazyVerticalGrid` de episodios).
- **Estado**: ✅ Aprobado, pendiente de implementar.

### D3. Comentarios en detalle de anime

- **Qué hace**: Sección de comentarios en el detalle del anime, después de la paginación de capítulos. Empieza siempre en el tab "Episodios" pero hay un tab "Comentarios" al lado.
- **Dificultad**: Media-Alta (requiere backend CRUD + auth).
- **Archivos a tocar**: Nuevo endpoint en tu backend (`/api/comments`), nuevo `CommentsRepository.kt`, nueva sección en `AnimeDetailScreen.kt`.
- **Estado**: ✅ Aprobado pero requiere tocar backend. **Pospuesto hasta que arregles tu server MySQL.**

### B1+B2. Recomendaciones + info enriquecida (personajes, staff)

- **Qué hace**: 
  - B1: Sección "Recomendado para ti" en Home basada en historial.
  - B2: En detalle, pestañas con personajes, seiyuus, staff, estudio.
- **Cómo se hace SIN IA**: 
  - **AniList GraphQL ya trae `Media.recommendations`** — son recomendaciones curadas por la comunidad AniList para cada anime. Tomás las del anime más reciente que el usuario vio y las mostrás.
  - Para personalizar más: tomás los géneros más vistos del usuario (count de `genres` en animes con `episodeNumber > 0` en watch_history) y hacés query a AniList `Page(genre_in: [...], sort: POPULARITY_DESC)`.
  - **No se necesita IA ni tokens.** Todo es query a AniList.
- **Dificultad**: Baja-Media.
- **Archivos a tocar**: Nueva query GraphQL `GetRecommendations`, nuevo `RecommendationsViewModel.kt`, sección en `HomeScreen.kt` y pestañas en `AnimeDetailScreen.kt`.
- **Estado**: ✅ Aprobado, pendiente de implementar.

---

## 🔮 Features aprobadas para FUTURO (no ahora)

### E1-E3. Descarga offline + gestión + auto-download

- **Por qué no ahora**: Complejidad alta, manejo de almacenamiento + cifrado + expiración + sync con la nube. Mejor hacerlo cuando la app esté más madura.
- **Estado**: 🟡 Pendiente. Volver a evaluar en 3-6 meses.

### G2. Dashboard de estadísticas personales

- **Qué hace**: Sección "Mi actividad" con episodios vistos, horas totales, top animes, top géneros, heatmap de actividad semanal (estilo GitHub contribution graph), racha de días consecutivos.
- **Por qué no ahora**: Requiere precomputar datos en WorkManager nocturno + visualización con charts. Es trabajo de UI considerable.
- **Estado**: 🟡 Pendiente. Es buen candidato para después de las notificaciones.

### G3. Wrapped anual con share card

- **Qué hace**: A finales de diciembre, Stories animadas con "Viste X caps este año", "Tu anime más visto", "Top géneros", etc. Cada slide exportable para compartir.
- **Por qué no ahora**: Es estacional (solo tiene sentido en diciembre). Planificar para Q4.
- **Estado**: 🟡 Pendiente. Implementar en octubre-noviembre de cada año.

### G4. Marcaje visual de episodios vistos

- **Qué hace**: En el detalle del anime, los episodios ya vistos aparecen con una palomita (✓) arriba a la derecha del cuadrito de episodio.
- **Por qué no ahora**: Tu app no trackea por-episodio actualmente, solo por anime. Habría que agregar una tabla nueva `watched_episodes` o extender `watch_history`.
- **Estado**: 🟡 Pendiente. Requiere cambio de modelo de datos.

### A6. Sleep timer

- **Qué hace**: Programar apagado del player en N minutos o "al final del episodio".
- **Por qué no ahora**: Sobra por ahora, no es prioritario.
- **Estado**: 🟡 Pendiente. Posible implementación rápida futura.

### D2. Watch party

- **Qué hace**: Ver en sincronización con amigos.
- **Por qué no ahora**: Requiere WebSocket backend en tiempo real.
- **Estado**: 🟡 Pendiente. Mucho trabajo de backend.

### F3. Modo incógnito

- **Qué hace**: Toggle que no registra en historial.
- **Por qué no ahora**: Lo ves innecesario, el usuario puede ver y eliminar después.
- **Estado**: 🟡 Pendiente. Fácil de implementar si cambia de opinión.

### F4. Atajos dinámicos en launcher

- **Qué hace**: Long-press al ícono de la app muestra "Continuar viendo [último anime]", "Favoritos", "Horarios de hoy", "Buscar".
- **Por qué no ahora**: No sabías qué era. Es chiquito pero reduce fricción.
- **Estado**: 🟡 Pendiente. Fácil cuando tengamos tiempo.

### H5. Proxy DLNA / Web Video Caster

- **Qué hace**: Convertir la app en servidor DLNA/UPnP para streaming a TVs no-Chromecast.
- **Por qué no ahora**: Ya tenés app para TV, este es bonus.
- **Estado**: 🟡 Pendiente. Posible implementación futura.

### C2 (descartado). Widget de calendario en home screen

- **Por qué descartado**: Ya tenés la pestaña Horarios que cumple la misma función. Un widget duplicaría sin aportar mucho.
- **Estado**: ❌ Descartado (a menos que el usuario lo pida explícitamente).

---

## ❌ Features descartadas

| Feature | Razón |
|---|---|
| A1 Skip OP/ED automático | Ya está implementado (skip manual) |
| A3 Reproductor externo | Denegado, ya hay Cast para enviar a PC |
| A4 Personalización ASS completo | Muy complejo, no prioritario |
| B3 Búsqueda por imagen (trace.moe) | No interesa |
| B4 Vista de temporada con trailers | YouTube embebido bloquea por copyright |
| B5 Filtrado avanzado | Posible pero no ahora |
| C3 Alertas de temporada con IA | No queremos nada de IA |
| D1 Discord Rich Presence | Innecesario por ahora |
| D4 Tarjeta de progreso compartible | No interesa |
| D5 Hilos de Reddit integrados | No |
| F1 Android TV support | Ya está implementado |
| F2 Material You | Diseños actuales son buenos |
| F5 Búsqueda por voz | No |
| G1 Sync MAL/AniList/Kitsu | No |
| H1 Sistema de extensiones | No |
| H2 Backup local exportable | Quitado cuando se creó la nube |
| H3 Sync WebDAV/Google Drive | No aplica |
| H4 Detección de VPN/región | No |
| H6 Bloqueador de anuncios en webviews | No hay webviews visibles, scrapers bloquean todo |

---

## 🎯 Orden de implementación sugerido (mi recomendación)

### Batch 1 — Player improvements (más rápidos, más visibles)

| # | Feature | Tiempo estimado |
|---|---|---|
| 1 | A2 PiP + background audio | 1 día |
| 2 | A7 Gestos avanzados (brillo/volumen/seek) | 1-2 días |
| 3 | A8 Velocidad hasta 1.5x | Medio día |
| 4 | A9 Lock de pantalla | Medio día |
| 5 | A5 Visor de episodios en player | 1 día |

**Total Batch 1**: ~4-5 días de trabajo. Todos son mejoras al player, no tocan backend.

### Batch 2 — Notificaciones (alto impacto)

| # | Feature | Tiempo estimado |
|---|---|---|
| 6 | C1 Notificaciones de nuevos episodios | 1-2 días |
| 7 | C4 Notificación de segundas temporadas | 1 día |

**Total Batch 2**: ~2-3 días. Requiere WorkManager + NotificationManager.

### Batch 3 — Descubrimiento (mediano impacto)

| # | Feature | Tiempo estimado |
|---|---|---|
| 8 | B1+B2 Recomendaciones + info enriquecida | 2 días |

**Total Batch 3**: 2 días. Solo queries AniList GraphQL + UI nueva.

### Batch 4 — Futuro (evaluar en 3-6 meses)

- D3 Comentarios (cuando arregles MySQL)
- G2 Dashboard de estadísticas
- G3 Wrapped anual (en octubre-noviembre)
- G4 Marcaje visual de episodios vistos (requiere cambio de modelo)
- E1-E3 Descarga offline (cuando la app esté más madura)
- F4 Atajos dinámicos en launcher
- H5 Proxy DLNA/Web Video Caster

---

## 📝 Notas técnicas importantes

### Sobre C1 (notificaciones)

Tu pregunta: *"¿seguro necesita que la app esté ejecutándose siempre en segundo plano?"*

**Respuesta**: Sí, pero ya la tenés corriendo en background. El `SyncWorker` con WorkManager se ejecuta cada 15 min automáticamente, incluso si el usuario "cerró" la app (con swipe up). Android mantiene vivo el WorkManager mientras no haga force-stop desde Settings.

Lo único que falta es:
1. En `SyncWorker.doWork()`, después de `airingController.checkAllWaiting()`, comparar el estado nuevo con el anterior (cuáles pasaron de `hasNewEpisode=false` a `true`).
2. Para cada uno, llamar `NotificationManager.notify(...)` con un `PendingIntent` que abre el player en ese episodio.
3. Pedir permiso `POST_NOTIFICATIONS` en Android 13+ (runtime permission).

### Sobre B1 (recomendaciones sin IA)

AniList GraphQL ya trae `Media.recommendations` para cada anime. Son recomendaciones curadas por la comunidad AniList (los usuarios hacen "recomendar este anime si te gustó X"). No necesitas IA.

Para personalizar más, podés:
1. Tomar los géneros más vistos del usuario (top 3 géneros de animes con `episodeNumber > 0` en `watch_history`).
2. Hacer query AniList `Page(genre_in: [...], sort: POPULARITY_DESC, perPage: 20)`.
3. Excluir los que ya están en su lista de vistos/viendo.
4. Mostrar los 10 primeros en Home como "Para ti".

Sin IA, sin tokens, sin costos.

### Sobre G4 (marcaje visual de episodios vistos)

Actualmente tu app trackea por-anime, no por-episodio. Para implementar esto necesitarías:
- Nueva tabla `watched_episodes(animeId, episodeNumber, timestamp)`.
- O extender `watch_history` con un array de episodios vistos (no recomendado, mala normalización).
- Mejor: nueva tabla, foreign key a `local_anime`.

Es trabajo chico pero requiere migración de DB + cambios en DetailScreen y PlayerViewModel.

---

## 📦 Próximos ZIPs a entregar

1. **Fase 4**: Batch 1 completo (PiP + gestos + velocidad + lock + visor de episodios)
2. **Fase 5**: Batch 2 completo (notificaciones de nuevos episodios + segundas temporadas)
3. **Fase 6**: Batch 3 completo (recomendaciones + info enriquecida)
4. Fases futuras: según demanda y tiempo

---

## 🔄 Cómo priorizar dentro de cada batch

Si tenés prisa y querés solo una feature de cada batch:
- Batch 1: **A7 Gestos** (la más sentida por los usuarios)
- Batch 2: **C1 Notificaciones** (la más impactante para retención)
- Batch 3: **B1 Recomendaciones** (la más visible en Home)

Si querés que empiece por algún batch distinto, decime y lo armo.
