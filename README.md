# Cámara (cámara MCP)

Cámara de fotos y video para Android, con acento amarillo. Parte de la familia de Stone, Luma, Calculadora, Notas, Filem,
Brújula, Nivel, Reloj, Calendario, Inicio, QR, Clima, Redes y Contactos. Por MCP tu IA ve los ajustes y la lista de fotos,
pero **nunca puede hacer fotos ni grabar**.

## Estado (0.1.0)

- **Foto y Video** (FOTO · VIDEO abajo), con el botón grande: blanco para fotos, rojo para video (cuadrado al grabar, con el
  tiempo arriba).
- **Flash** (no · auto · sí), **temporizador** (3 o 10 s, con cuenta atrás grande; tocar de nuevo cancela), **formato**
  4:3 o 16:9, **cuadrícula** de tercios.
- **Zoom:** botones 0,5× (si hay gran angular), 1×, 2× y 5× hasta el máximo de la cámara; también pellizcando.
  **Tocar enfoca.** Cambiar entre cámara trasera y frontal.
- **Botones de volumen** como disparador; sonido del obturador opcional.
- **Video:** HD, Full HD o 4K (baja sola si el teléfono no puede), con o sin sonido.
- Todo se guarda en la **galería, DCIM/Camera** (`IMG_…jpg`, `VID_…mp4`), como la cámara del teléfono. La miniatura abre la
  última en **Luma** (o la galería del teléfono).
- Responde al botón de cámara y a los accesos «abrir cámara» del teléfono.
- **Ajustes:** cuadrícula, sonido, botones de volumen, calidad y sonido del video, **Modo MCP**, Temas, privacidad y versión.
- **Modo MCP:** `camera_settings`, `camera_recent`, `camera_shot` y, con token de lectura y escritura, `camera_settings_set`.
  Solo nombres y horas, nunca las imágenes ni dónde están. Tokens `cam_…`; módulo `camera_` para Gateway Hub.
- **Resumen para Inicio:** «3 fotos hoy · 1 video» en la pantalla «Hoy».
- Sin anuncios ni estadísticas; internet solo para el Modo MCP (apagado de fábrica).

APK: [`android/Camara-0.1.0.apk`](android/Camara-0.1.0.apk) (`com.zirakusa.camara`, firmado como el resto de la familia y
Gateway Hub).

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

## Siguiente

Ver [`ROADMAP.md`](ROADMAP.md).
