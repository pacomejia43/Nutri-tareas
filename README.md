# Nutri-Tareas

App nativa de Android (Kotlin + Jetpack Compose) que funciona como asistente de tareas:

1. **Lee tareas desde un PDF o desde capturas de pantalla/fotos del celular** y ayuda a
   elaborarlas — el modelo recibe el PDF completo o las imágenes (no texto plano) y lo desarrolla
   como lo haría una tutora, tarea por tarea. Cuando lo que llega es una imagen, primero confirma
   con la usuaria qué entendió antes de ponerse a trabajar.
2. El asistente **se llama Paco y siempre se dirige a la usuaria como "mi amor"** (definido en el
   prompt de sistema, ver `AssistantPersona.kt`).
3. Cuando falta información para completar una tarea, **la pide en el chat**; al terminar,
   genera un **documento .docx editable** para revisar antes de entregarlo.
4. También funciona como asistente general: puede **generar cualquier texto** (correos, resúmenes,
   cartas, publicaciones...) con las características que se le pidan, no solo tareas escolares.
5. **Funciona con Claude o con Gemini**, a elección — cada uno con su propia clave de API guardada
   por separado; dos botones en Ajustes alternan cuál está activo sin tener que volver a escribir
   ninguna clave.
6. **Puede editar una plantilla de Word/Google Docs ya existente** (mismo diseño siempre, solo
   cambian datos como materia, actividad y fecha): se adjunta el archivo `.docx`, se conversa en
   el chat sobre los cambios y la app genera una copia editada conservando el diseño original
   intacto (fuentes, estilos, logo...), sin depender de la API de Google ni de credenciales OAuth.

No se distribuye por Google Play: vive únicamente en este repositorio de GitHub y **se
actualiza a sí misma leyendo los releases de GitHub** (ver [Actualizaciones](#actualizaciones)).

## Arquitectura

Un solo módulo (`:app`), sin Room ni Hilt — el proyecto es pequeño y ambos habrían sido
peso muerto. En su lugar:

- **UI**: Jetpack Compose + Material 3, en tonos blancos y rosa pastel (`ui/theme/Color.kt`).
  `ChatScreen` (conversación), `SettingsScreen` (proveedor de IA, claves y modelo),
  `UpdateDialog` (actualizaciones). Cada pantalla tiene su `ViewModel`, con un
  `AppContainer` (`di/AppContainer.kt`) como contenedor de dependencias manual.
- **Asistente**: `data/assistant/AssistantClient.kt` define la interfaz común; hay dos
  implementaciones intercambiables según el proveedor activo (`data/settings/AssistantProvider.kt`):
  - `ClaudeAssistantClient.kt` usa el
    [SDK oficial de Anthropic para Java/Kotlin](https://github.com/anthropics/anthropic-sdk-java)
    con streaming.
  - `GeminiAssistantClient.kt` habla directamente por HTTP con la API REST de Gemini
    (`generativelanguage.googleapis.com`, streaming por SSE) usando OkHttp + kotlinx.serialization,
    sin depender de un SDK adicional.

  Ambos son estado-menos por turno: cada llamada reenvía la conversación completa. El PDF (si hay)
  y cualquier captura/foto adjuntada se reenvían como bloques nativos (documento/imagen) en el
  turno donde se adjuntaron por primera vez, no como texto plano — con *prompt caching* en el caso
  de Claude.
- **PDF**: `data/pdf/PdfTextExtractor.kt` usa PdfBox-Android solo para validar el archivo y
  contar páginas — la lectura real la hace el modelo directamente sobre el PDF.
- **Capturas/fotos**: `data/image/ImageProcessor.kt` decodifica la imagen elegida, la reduce a
  como máximo ~1568px de lado largo y la recomprime a JPEG antes de enviarla — de sobra para que
  el modelo lea texto en pantalla, sin mandar varios MB por foto.
- **Documento editable desde cero**: `data/docx/DocxGenerator.kt` construye un `.docx` (OOXML)
  válido a mano con `java.util.zip`, sin depender de Apache POI ni librerías similares (poco
  adecuadas para Android). Tiene pruebas unitarias en `app/src/test/.../DocxGeneratorTest.kt`.
- **Plantilla existente**: `data/docx/DocxTemplateReader.kt` lee cualquier `.docx` picada
  (por ejemplo, exportada desde Google Docs) guardando cada entrada del zip byte a byte y
  extrayendo el texto de cada párrafo de `word/document.xml` por índice. `DocxTemplateWriter.kt`
  reescribe solo los párrafos indicados (manteniendo el formato del primer *run* de cada uno) y
  reempaqueta el resto del `.docx` sin tocarlo, así que estilos, fuentes y logo sobreviven
  intactos. `TemplateEditParser.kt` interpreta la respuesta del modelo (bloques `[[PARRAFO N]]`)
  para saber qué párrafos reemplazar. Pruebas unitarias en
  `app/src/test/.../DocxTemplateEditorTest.kt`.
- **Almacenamiento**: la clave de API de cada proveedor se cifra por separado con una llave de
  Android Keystore (`data/crypto/CryptoManager.kt`, AES-256-GCM) y se guarda vía DataStore
  (`androidx.security:security-crypto` está deprecado, por eso no se usa), junto con cuál de los
  dos proveedores está activo. El historial de la conversación se guarda como un JSON plano en
  almacenamiento privado de la app.
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

No hace falta ninguna clave ni configuración especial para compilar: cada usuaria introduce sus
propias claves de API (Claude, Gemini, o ambas) dentro de la app (Ajustes); no viven en el código
ni en el repositorio.

## Configurar el asistente

Dentro de la app, en **Ajustes**, hay dos botones para elegir el **proveedor de IA** activo
(Claude o Gemini) y, debajo, una sección independiente para cada uno con su propia clave y modelo.
Se pueden guardar las dos claves a la vez y alternar entre ellas cuando convenga, sin volver a
escribirlas.

- **Claude**: la clave se obtiene gratis en [console.anthropic.com](https://console.anthropic.com).
  Modelo: Opus 5 (predeterminado, el más capaz), Sonnet 5 (equilibrado), Haiku 4.5 (más
  económico), o un ID personalizado.
- **Gemini**: la clave se obtiene gratis en [aistudio.google.com](https://aistudio.google.com).
  Modelo: Gemini Pro (predeterminado, el más capaz), Gemini Flash (equilibrado), Gemini
  Flash-Lite (más económico), o un ID personalizado — útil si Google publica un modelo nuevo y
  el nombre por defecto todavía no se actualiza aquí.

Ambas claves se guardan cifradas solo en el dispositivo (ver "Almacenamiento" arriba).

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
  data/        # PDF, imágenes, Claude, Gemini, DOCX, ajustes cifrados, actualizaciones - sin
               # depender de Compose
  ui/          # Pantallas Compose + ViewModels (chat, ajustes, actualización)
  di/          # Contenedor de dependencias manual
.github/workflows/
  android-ci.yml       # build + tests + lint en cada push
  android-release.yml  # build firmado + GitHub Release al subir un tag vX.Y.Z
release-signing/       # Keystore de conveniencia para firmar releases (no es secreta)
```
