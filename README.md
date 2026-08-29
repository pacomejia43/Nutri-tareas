# Nutri-Tareas

App nativa de Android (Kotlin + Jetpack Compose) que funciona como asistente de tareas:

1. **Lee tareas desde un PDF** y ayuda a elaborarlas — Claude recibe el PDF completo (no solo
   texto plano) y lo desarrolla como lo haría una tutora, tarea por tarea.
2. El asistente **siempre se dirige a la usuaria como "mi amor"** (definido en el prompt de
   sistema, ver `AssistantPersona.kt`).
3. Cuando falta información para completar una tarea, **la pide en el chat**; al terminar,
   genera un **documento .docx editable** para revisar antes de entregarlo.

No se distribuye por Google Play: vive únicamente en este repositorio de GitHub y **se
actualiza a sí misma leyendo los releases de GitHub** (ver [Actualizaciones](#actualizaciones)).

## Arquitectura

Un solo módulo (`:app`), sin Room ni Hilt — el proyecto es pequeño y ambos habrían sido
peso muerto. En su lugar:

- **UI**: Jetpack Compose + Material 3. `ChatScreen` (conversación), `SettingsScreen` (clave de
  API y modelo), `UpdateDialog` (actualizaciones). Cada pantalla tiene su `ViewModel`, con un
  `AppContainer` (`di/AppContainer.kt`) como contenedor de dependencias manual.
- **Asistente**: `data/assistant/ClaudeAssistantClient.kt` usa el
  [SDK oficial de Anthropic para Java/Kotlin](https://github.com/anthropics/anthropic-sdk-java)
  con streaming. El PDF se adjunta como bloque `document` nativo (con *prompt caching*) en el
  primer turno de usuario; el resto de la conversación se reenvía en cada llamada, como exige la
  Messages API al no tener estado en el servidor.
- **PDF**: `data/pdf/PdfTextExtractor.kt` usa PdfBox-Android solo para validar el archivo y
  contar páginas — la lectura real la hace Claude directamente sobre el PDF.
- **Documento editable**: `data/docx/DocxGenerator.kt` construye un `.docx` (OOXML) válido a
  mano con `java.util.zip`, sin depender de Apache POI ni librerías similares (poco adecuadas
  para Android). Tiene pruebas unitarias en `app/src/test/.../DocxGeneratorTest.kt`.
- **Almacenamiento**: la clave de API se cifra con una llave de Android Keystore
  (`data/crypto/CryptoManager.kt`, AES-256-GCM) y se guarda vía DataStore
  (`androidx.security:security-crypto` está deprecado, por eso no se usa). El historial de la
  conversación se guarda como un JSON plano en almacenamiento privado de la app.
- **Actualizaciones**: `data/update/UpdateChecker.kt` y `UpdateInstaller.kt` consultan
  `GET /repos/pacomejia43/Nutri-tareas/releases/latest` y, si hay una versión más nueva,
  descargan el APK adjunto y abren el instalador del sistema.

## Compilar en Android Studio

1. Clonar el repositorio y abrir la carpeta en Android Studio (Ladybug o más reciente).
2. Dejar que Gradle sincronice — usa Android Gradle Plugin 8.13, Kotlin 2.3.20, compileSdk 36,
   minSdk 26. El `.jar` del Gradle Wrapper no está en el repositorio (es binario); Android
   Studio lo resuelve solo a partir de `gradle/wrapper/gradle-wrapper.properties`, así que no
   hace falta ningún paso extra.
3. Ejecutar en un dispositivo o emulador con Android 8.0 (API 26) o superior.

Para compilar por línea de comandos en vez de Android Studio, genera el `.jar` una vez con
cualquier Gradle instalado (`gradle wrapper --gradle-version 8.14.3`) antes de usar `./gradlew` —
es exactamente lo que hace el primer paso de ambos workflows en `.github/workflows/`.

No hace falta ninguna clave ni configuración especial para compilar: la clave de API de
Anthropic la introduce cada usuaria dentro de la app (Ajustes), no vive en el código ni en el
repositorio.

## Configurar el asistente

Dentro de la app, en **Ajustes**:

- **Clave de API de Anthropic**: se obtiene gratis en [console.anthropic.com](https://console.anthropic.com).
  Se guarda cifrada solo en el dispositivo.
- **Modelo de Claude**: Opus 5 (predeterminado, el más capaz), Sonnet 5 (equilibrado), Haiku 4.5
  (más económico), o un ID personalizado.

## Actualizaciones

La app comprueba `releases/latest` de este repositorio al abrirse (en silencio; solo avisa si
hay algo nuevo que no se haya descartado antes) y también con el botón "Buscar
actualizaciones" en Ajustes. Para publicar una versión nueva:

1. Actualizar `versionName`/`versionCode` no es necesario a mano: los define el tag.
2. Crear y subir un tag `vMAJOR.MINOR.PATCH`, por ejemplo:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

3. El workflow `.github/workflows/android-release.yml` compila un APK firmado, lo sube como
   asset de un GitHub Release nuevo, y desde ahí la app lo detecta sola.

El APK se firma siempre con la misma clave (`release-signing/`, ver su README) para que
instalar una actualización nunca pida desinstalar la versión anterior. `android-ci.yml`
además compila un APK debug en cada push, como verificación continua.

## Estructura del repositorio

```
app/src/main/kotlin/com/nutritareas/app/
  data/        # PDF, Claude, DOCX, ajustes cifrados, actualizaciones - sin depender de Compose
  ui/          # Pantallas Compose + ViewModels (chat, ajustes, actualización)
  di/          # Contenedor de dependencias manual
.github/workflows/
  android-ci.yml       # build + tests + lint en cada push
  android-release.yml  # build firmado + GitHub Release al subir un tag vX.Y.Z
release-signing/       # Keystore de conveniencia para firmar releases (no es secreta)
```
