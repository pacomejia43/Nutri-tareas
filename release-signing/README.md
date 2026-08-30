# Clave de firma para releases

Los APK de release (los que publica `.github/workflows/android-release.yml`) se firman con
una clave que **no vive en este repositorio** - vive solo como secretos de GitHub Actions,
para que instalar una actualización sobre una versión anterior siga funcionando (Android
exige que la firma coincida) sin exponer la clave a quien pueda leer el código.

## Generarla (una sola vez)

```
keytool -genkeypair -v \
  -keystore nutri-tareas-release.keystore \
  -alias nutritareas-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "<contraseña que tú elijas>" -keypass "<la misma>" \
  -dname "CN=Nutri-Tareas, OU=Nutri-Tareas, O=Nutri-Tareas, L=NA, S=NA, C=MX"
```

Guarda ese archivo `.keystore` en un lugar privado tuyo (gestor de contraseñas, disco
cifrado) - es la única copia real; si lo pierdes, no podrás publicar una actualización que
las instalaciones existentes acepten como tal (tendrían que desinstalar la app primero).

## Configurarla en GitHub

En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**.
Crea estos cuatro:

- `RELEASE_KEYSTORE_BASE64`: el archivo `.keystore` codificado en base64 (`base64 -w0
  nutri-tareas-release.keystore`, o en PowerShell:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("nutri-tareas-release.keystore"))`).
- `RELEASE_KEYSTORE_PASSWORD`: la contraseña del keystore.
- `RELEASE_KEY_ALIAS`: el alias que usaste (`nutritareas-release` en el ejemplo).
- `RELEASE_KEY_PASSWORD`: la contraseña de la clave (normalmente la misma que la del keystore).

`.github/workflows/android-release.yml` los usa automáticamente en cada release. No hace
falta tocar `app/build.gradle.kts`.

## Compilaciones locales

Sin esos secretos disponibles (cualquier build local), `app/build.gradle.kts` genera y
reutiliza una clave temporal propia (`release-signing/local/`, ignorada por git) para que
`assembleRelease` siga funcionando en tu máquina. Su firma no coincide con la de un release
real - solo importa si intentas instalar un build local encima de uno publicado por CI.

## Si la clave llegara a filtrarse

Genera una nueva con los pasos de arriba y reemplaza los cuatro secretos. Quien ya tenga
instalada una versión firmada con la clave anterior deberá desinstalarla antes de instalar
una firmada con la nueva - no hay forma de evitar eso, es la garantía que da la firma.
