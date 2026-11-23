# Cámara (cámara MCP): reglas para agentes

- Lee `ARQUITECTURA.md` (y el contrato común `FAMILIA.md` del repo de Inicio) y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos.
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/Camara-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Reglas puras (zoom, nombres de archivo, reloj de grabación, lista de fotos, ajustes) en `data/`, con pruebas. Las
  pantallas solo pintan y avisan. Todo lo de CameraX vive en `Camera.kt`.
- La IA **nunca** hace fotos ni graba: no existe ni existirá una herramienta para eso. Por MCP solo salen ajustes, nombres
  y horas; nunca imágenes ni rutas (`uri`).
- Privacidad: fotos y videos van a la galería del teléfono (DCIM/Camera). Nada sale salvo por el Modo MCP que el usuario
  active; internet es solo para eso.
- Cada herramienta MCP nueva lleva su prueba en `McpTest`: lista de herramientas, llamada correcta, argumentos malos y
  «no encontrado». Las que cambian algo exigen token de lectura y escritura (`READ_ONLY` si no).
- Prohibido: `eval`, motores de scripts, SQL concatenado.
- Firma: la de depuración del entorno, igual que el resto de la familia y Gateway Hub.
