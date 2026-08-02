# Guía de Personalización UI/UX - AnimeMew

¡Has llegado a un punto en el que el motor (Lógica, Base de Datos, Reproductor, Scraper) de AnimeMew funciona perfectamente! Ahora, si deseas cambiar cómo se ve la aplicación (colores, fuentes, márgenes, tamaños, fondos) sin dañar la programación, este archivo es tu manual de instrucciones.

> [!IMPORTANT]
> Modificar la UI en Jetpack Compose se hace a través de los modificadores (`Modifier`), los colores (`Color`), fuentes (`Typography`), formas (`Shape`) y componentes visuales. **No elimines variables como `viewModel`, estados (`collectAsState`), ni bloques de lógica (`LaunchedEffect`)**.

A continuación se detalla la ubicación y función de cada pantalla para que puedas personalizar su estética.

---

## 1. Archivos Globales de Diseño (Design System)

Antes de modificar pantalla por pantalla, te recomiendo que ajustes los colores y fuentes globales. Si lo haces aquí, muchas pantallas cambiarán automáticamente.

- **Ubicación:** `app/src/main/java/com/mew/animemew/ui/theme/`
- **Archivos Clave:**
  - `Color.kt`: Aquí se definen los colores básicos (por ejemplo, `NeonPurple`, `BackgroundDark`).
  - `Theme.kt`: Configura el esquema de colores global y los modos claro/oscuro.
  - `Type.kt`: Define las fuentes y sus tamaños (Typography).

---

## 2. Pantalla de Inicio (Home)
Donde se muestran las listas de tendencias, populares y "Seguir Viendo".

- **Archivo:** `app/src/main/java/com/mew/animemew/ui/screens/HomeScreen.kt`
- **Qué Modificar (UI):**
  - **Color de fondo:** Dentro del componente `Scaffold(containerColor = MaterialTheme.colorScheme.background, ...)`
  - **El Logo / TopBar:** Modifica `CenterAlignedTopAppBar` para cambiar el estilo de la cabecera.
  - **Componente `AnimeCarouselSection`:** Modifica el título y el espacio entre las tarjetas (`Arrangement.spacedBy(16.dp)`).
  - **Tarjetas de Seguir Viendo (`WatchHistoryCard`):** Puedes cambiar los degradados, esquinas redondeadas (`RoundedCornerShape(8.dp)`), colores de la barra de progreso, etc.

---

## 3. Pantalla de Búsqueda (Search)
Donde el usuario busca anime por nombre.

- **Archivo:** `app/src/main/java/com/mew/animemew/ui/screens/SearchScreen.kt`
- **Qué Modificar (UI):**
  - **Barra de búsqueda (`OutlinedTextField` / `SearchBar`):** Cambiar los colores de los bordes, el texto interno, esquinas redondeadas, el ícono de la lupa.
  - **Resultados de Búsqueda:** Se renderizan como una cuadrícula (`LazyVerticalGrid`). Ajusta el número de columnas o el espacio en `horizontalArrangement = Arrangement.spacedBy(...)`.

---

## 4. Tarjetas de Anime (Anime Card)
Este componente se usa en *casi todas* las pantallas (Inicio, Buscar, Listas) para mostrar un anime (Cover + Título + Tipo + Estrellas).

- **Archivo:** `app/src/main/java/com/mew/animemew/ui/components/AnimeCard.kt`
- **Qué Modificar (UI):**
  - Si quieres hacer las tarjetas más altas, más delgadas, cambiar el diseño de la puntuación (la estrella) o el degradado de abajo, ¡hazlo aquí! El cambio se verá reflejado en toda la App automáticamente.

---

## 5. Pantalla de Detalles (Anime Details)
La pantalla que se abre al tocar un anime, donde se lee la sinopsis, y salen los episodios.

- **Archivo:** `app/src/main/java/com/mew/animemew/ui/screens/AnimeDetailScreen.kt`
- **Qué Modificar (UI):**
  - **Banner Superior e Imagen:** La lógica del `AsyncImage` y su contenedor.
  - **Títulos y Etiquetas:** El color de los géneros (`AssistChip`), el estilo del texto de la sinopsis.
  - **Lista de Episodios (`EpisodesSection`):** Aquí puedes cambiar el diseño del botón de "Episodio X" o cómo se muestran los botones si decides cambiarlo por una lista.
  - > [!TIP]
    > Si el botón "Episodio X" no te convence, puedes usar tarjetas largas completas, agregarles imágenes miniatura (si las consigues de la API), etc.

---

## 6. Pantallas de Listas y Configuración
Donde el usuario ve sus "Viendo", "Completados", etc., y donde configura la App.

- **Archivos:**
  - `ListsScreen.kt`: `app/src/main/java/com/mew/animemew/ui/screens/ListsScreen.kt`
  - `ConfigScreen.kt` (o Settings): `app/src/main/java/com/mew/animemew/ui/screens/SettingsScreen.kt` (si existe, según cómo esté estructurado tu proyecto, probablemente configurada en `MainAppScreen.kt`).
- **Qué Modificar (UI):**
  - Estructura de pestañas (`ScrollableTabRow` / `TabRow`).
  - Los switches, deslizadores de volumen o las descripciones en Configuración.

---

## 7. Estructura Principal / Navegación Inferior (Bottom Bar)
Si quieres cambiar cómo se ve la barra con los botones "Inicio", "Buscar", "Listas".

- **Archivo:** `app/src/main/java/com/mew/animemew/ui/screens/MainAppScreen.kt`
- **Qué Modificar (UI):**
  - **`NavigationBar` / `NavigationBarItem`:** Cambia el color de la barra entera, o el color de los iconos (cuando están seleccionados o deseleccionados).
  - **Rutas y Animaciones:** Puedes agregar `AnimatedVisibility` para que haya transiciones al cambiar de pestaña.

---

> [!CAUTION]
> **REGLA DE ORO AL MODIFICAR LA UI:**
> 1. Si cambias un `Text(text = "...")`, puedes cambiar libremente `fontSize`, `fontWeight`, `color`. ¡Pero **NO BORRES** si dice `text = anime.title`!
> 2. Si cambias un contenedor (`Box`, `Column`, `Row`), puedes modificar su `modifier`. ¡Pero **NO BORRES** el `clickable { ... }` si tiene uno, porque romperás la navegación!
> 3. Siempre es recomendable reconstruir (`./gradlew assembleDebug`) luego de modificar un archivo grande de UI para asegurar de que no falte un paréntesis `}`.
