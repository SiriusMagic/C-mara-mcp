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
