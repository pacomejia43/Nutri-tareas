# Clave de firma para releases

`nutri-tareas-release.keystore` es la clave con la que se firman los APK de release
(local y en GitHub Actions). Está **incluida a propósito en el repositorio** y **no es
secreta**: su único objetivo es que todas las versiones se firmen siempre con la misma
clave, para que instalar una actualización sobre una versión anterior funcione (Android
exige que la firma coincida para actualizar una app sin desinstalarla primero).

No representa un riesgo real: quien pueda modificar este repositorio ya tiene control
total sobre el código que se publica, con o sin esta clave. Publicar una actualización
maliciosa requeriría de todas formas acceso de escritura al repositorio.

Contraseña del keystore y de la clave: `nutritareas-update-key` (alias `nutritareas`).
Configurado en `app/build.gradle.kts`.

El archivo del keystore es binario, así que en este repositorio se guarda como
`nutri-tareas-release.keystore.base64` (texto). `app/build.gradle.kts` lo decodifica
solo la primera vez que hace falta - no requiere ningún paso manual.

## Usar una clave de firma real (opcional)

Si en algún momento se prefiere una clave de verdad en vez de esta de conveniencia,
genera una y guárdala como secretos del repositorio en GitHub (Settings → Secrets and
variables → Actions):

- `RELEASE_KEYSTORE_PATH`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

`app/build.gradle.kts` los usa automáticamente en cuanto existan (ver `signingConfigs`),
sin tocar el código. Ten en cuenta que cambiar de clave de firma rompe la cadena de
actualizaciones: quien tenga instalada una versión firmada con la clave anterior deberá
desinstalarla antes de instalar una firmada con la nueva.
