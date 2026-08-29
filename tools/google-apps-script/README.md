# Sincronización con la plantilla de Google Docs

`plantilla-sync.gs` es el puente que deja al botón **Plantilla** (arriba a la derecha
en el chat) leer y editar directamente el Google Doc fijo de la app
(`https://docs.google.com/document/d/1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA`),
sin que la app necesite iniciar sesión con Google: el script corre bajo tu propia
cuenta, y la app solo necesita la URL (secreta) de su implementación como "aplicación
web".

## Publicarlo (una sola vez)

1. Abre el documento y ve a **Extensiones → Apps Script**.
2. Borra el código de ejemplo y pega el contenido de `plantilla-sync.gs`.
3. Guarda el proyecto (por ejemplo, con el nombre "Nutri-Tareas Plantilla Sync").
4. **Implementar → Nueva implementación**.
5. Tipo: **Aplicación web**. Ejecutar como: **Yo**. Quién tiene acceso: **Cualquier
   usuario**.
6. Autoriza los permisos que pida (acceso a tus Documentos de Google).
7. Copia la URL que termina en `/exec`.
8. Pégala en Nutri-Tareas, en **Ajustes → Plantilla de Google Docs**.

## Actualizarlo

Si cambias `plantilla-sync.gs`, pégalo de nuevo en el editor de Apps Script y crea
**otra vez** una implementación nueva (Implementar → Nueva implementación) - guardar el
proyecto solo no republica el código que la URL sirve.

## Sobre la seguridad de la URL

Trátala como una contraseña: cualquiera que la tenga puede leer y modificar ese
documento a través del script (aunque no pueda ver tu cuenta de Google ni nada más).
Nutri-Tareas la guarda cifrada en el dispositivo, igual que las claves de Claude y
Gemini. Si alguna vez se filtra, borra la implementación en Apps Script y crea una
nueva para invalidar la URL anterior.
