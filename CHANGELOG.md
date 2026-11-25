# Cámara 0.3.0

- **La miniatura muestra tu última foto o video** (junto al disparador), como la cámara del teléfono, y cambia en cuanto
  haces otra. Los videos llevan un ▶. Tocarla la abre en Galería, igual que antes.
- **«Más» con todos los modos y sus iconos**: Video, Foto, Retrato, Noche, Pro y Documentos. Arriba a la derecha puedes
  verlos **como lista**, con lo que hace cada uno, o como cuadrícula (se recuerda), y el engranaje abre **Ajustes**.
- **Iconos nuevos**: 3D (el de fábrica), Óleo, Píxel, Lente y Dulce, en Ajustes › Icono de la app. Se quitan los
  anteriores; si tenías uno elegido, pasa solo al nuevo que ocupa su lugar.
- 15 pruebas.

# Cámara 0.2.2

- **«Oye Lucía, toma una foto»**: después de tu «sí», Cámara se abre con una cuenta atrás de 3 segundos que un toque
  cancela (también «tómame una selfie», con la cámara frontal). Solo la voz del usuario a través de Lucía (misma firma);
  sigue sin existir una herramienta MCP para hacer fotos.

# Cámara 0.2.1

- **Conectar todas** (Gateway Hub 0.0.21): con un toque en el Hub se activa aquí «Redireccionar a Hub» y la app queda
  vinculada, sin entrar en ella. Solo lo puede pedir una app firmada por el mismo desarrollador.
- **Lucía** (la voz del Hub) usa las herramientas de esta app en el teléfono, **sin internet**, con las mismas reglas que
  por MCP. Solo mientras «Redireccionar a Hub» esté activado.
- **Icono de la app**: 5 iconos para elegir (Original, Claro, Dorado, Azul y Verde) en Ajustes › Icono de la app. Tras esta
  actualización quizá tengas que volver a poner el icono en tu pantalla de inicio (una sola vez).

# Cámara 0.2.0

Modos y barras como las cámaras de los teléfonos.
- Modos: VIDEO · FOTO · RETRATO · NOCHE y, en «Más», PRO y DOCUMENTOS. Se abre en el último modo usado.
- Retrato con 24 / 35 / 50 mm y desenfoque del fondo; Noche y HDR con el procesado del fabricante cuando el teléfono lo
  ofrece (CameraX Extensions). Sin él, Retrato y Noche hacen la foto con la máxima calidad.
- Pro: EV, ISO, obturador (1/1000–1/4 s) y balance de blancos; «Automático» lo devuelve todo.
- Documentos: marco para encuadrar la hoja; sale en blanco y negro limpio.
- Barra de arriba: flash con Apagado · Activado · Auto · Linterna, HDR, ajustes rápidos (˅), estilo de color y formato
  (4:3, 16:9, 1:1). En video: linterna, estabilización y calidad (720P, 1080P, 4K).
- Ajustes rápidos: Ajustes, formato, HDR, estilo de color, temporizador, cuadrícula, marca de agua y flash.
- Estilos de color (Natural, Vivo, Cálido, Frío, B/N, Sepia) en la vista previa (Android 12+) y en la foto; marca de agua
  «Cámara · fecha».
- MCP: `camera_settings` y `camera_settings_set` también con modo, HDR, estilo de color, marca de agua y estabilización.
  La IA sigue sin poder hacer fotos.
- 12 pruebas.

# Cámara 0.1.0

Primera versión: cámara de fotos y video con acento amarillo.
- Foto y video; flash, temporizador 3/10 s, 4:3 o 16:9, cuadrícula; zoom con botones y pellizco; tocar enfoca; frontal.
- Botones de volumen como disparador; video HD, Full HD o 4K con o sin sonido.
- Guarda en DCIM/Camera; la miniatura abre la última en Luma.
- Modo MCP (`cam_`, módulo `camera_`): `camera_settings`, `camera_recent`, `camera_shot`, `camera_settings_set`. La IA nunca
  hace fotos.
- Resumen para la pantalla «Hoy» de Inicio.
- 9 pruebas.
