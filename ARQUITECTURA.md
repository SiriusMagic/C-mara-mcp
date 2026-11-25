# Arquitectura de Cámara

Carpeta = package (`android/app/src/main/java/com/zirakusa/camara/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Shots`: `Mode`, `Flash`, `Timer`, `Ratio`, `Quality`; `Lens` (botones de zoom según la cámara, etiqueta, pellizco, nombres `IMG_`/`VID_`, reloj de grabación); `Shot` y `Shots` (las últimas 100, sin imágenes); `Settings` y `KeyValues`. `Scenes`: `Scene` (modos), `ColorStyle`, `Pixels` (color, documento, cuadrado, marca de agua), `Pro`, `Balance`, `Portrait`. |
| `ui/` | `CamaraState` (único dueño del estado: modo, cámara, zoom, cuenta atrás, grabación, última foto y ajustes; el botón grande `press`) y `CamaraApp` (`AppActions` con la vista previa). |
| `ui/screens/` | `CameraScreen` (imagen con su formato, tercios y marco de documento, zoom o 24/35/50 mm, miniatura, disparador, cambiar cámara), `CameraBars` (barra de arriba, ajustes rápidos, flash), `CameraModes` (modos, «Más» en cuadrícula o lista con iconos, qué hace cada modo y Ajustes, Pro), `SettingsScreen`, `McpScreen`, `Parts`, `Chrome` y `SettingsParts`. |
| `ui/theme/` | `Theme`: `ThemeMode` y `Accent` (amarillo de fábrica). |
| `mcp/` | Modo MCP: `Tokens` (`cam_…`), `CameraTools` (`camera_settings`, `camera_recent`, `camera_shot`; con escritura `camera_settings_set`; a través de `CameraData`), `McpHandler`, `RelayLink`, `McpMode` (`LiveData`) y `HubLink` (módulo `camera_`). |
| `Camera.kt` | CameraX `LifecycleCameraController` y, para «Video dual», `ProcessCameraProvider` con dos cámaras a la vez (`ConcurrentCamera` + `CompositionSettings`: una imagen y un video con las dos; reglas en `data/Dual.kt`): lente, Extensions (Retrato, Noche, HDR), Camera2 (Pro, estabilización), flash y linterna, formato, calidad, zoom, foto y video a MediaStore DCIM/Camera, sonidos del sistema. |
| `PhotoLook.kt` | Lo que se hace a la foto en memoria antes de guardarla: girar, espejo frontal, cuadrado, estilo de color o documento, marca de agua. |
| `Summary.kt` | `SummaryLines` (pura) y `SummaryProvider`: fotos y videos de hoy para Inicio. |
| `MainActivity.kt` | Crea estado y cámara, pide permisos, sigue los ajustes, botones de volumen, miniatura de la última (`thumbnail`: la de Android, o una foto reducida o un fotograma), abre la última en Luma e «Ir al Hub». |
