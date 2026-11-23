# Arquitectura de Cámara

Carpeta = package (`android/app/src/main/java/com/zirakusa/camara/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Shots`: `Mode`, `Flash`, `Timer`, `Ratio`, `Quality`; `Lens` (botones de zoom según la cámara, etiqueta, pellizco, nombres `IMG_`/`VID_`, reloj de grabación); `Shot` y `Shots` (las últimas 100, sin imágenes); `Settings` y `KeyValues`. |
| `ui/` | `CamaraState` (único dueño del estado: modo, cámara, zoom, cuenta atrás, grabación, última foto y ajustes; el botón grande `press`) y `CamaraApp` (`AppActions` con la vista previa). |
| `ui/screens/` | `CameraScreen` (barra de arriba, imagen con su formato y tercios, zoom, FOTO · VIDEO, miniatura, disparador, cambiar cámara), `SettingsScreen`, `McpScreen`, `Parts`, `Chrome` y `SettingsParts`. |
| `ui/theme/` | `Theme`: `ThemeMode` y `Accent` (amarillo de fábrica). |
| `mcp/` | Modo MCP: `Tokens` (`cam_…`), `CameraTools` (`camera_settings`, `camera_recent`, `camera_shot`; con escritura `camera_settings_set`; a través de `CameraData`), `McpHandler`, `RelayLink`, `McpMode` (`LiveData`) y `HubLink` (módulo `camera_`). |
| `Camera.kt` | CameraX `LifecycleCameraController`: lente, flash, formato, calidad, zoom, foto y video a MediaStore DCIM/Camera, sonidos del sistema. |
| `Summary.kt` | `SummaryLines` (pura) y `SummaryProvider`: fotos y videos de hoy para Inicio. |
| `MainActivity.kt` | Crea estado y cámara, pide permisos, sigue los ajustes, botones de volumen, abre la última en Luma e «Ir al Hub». |
