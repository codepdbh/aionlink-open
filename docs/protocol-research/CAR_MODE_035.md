# 0.35: Modo coche con pantalla apagada y Android Auto vía Open Headunit

6 de noviembre de 2026. Nada de esto se ha probado aún en el AION.

## Modo coche

Basado en la prueba de pantalla apagada (QUALITY_PROBE_034.md: 604 s en DOZE y
bloqueado, H.264 continuo, 0 paquetes inválidos).

- `CarModeDisplay`: pantalla virtual privada al tamaño que pide el coche
  (1920x960 en el AION), densidad 240, con `CarDashboardView` (reloj, fecha,
  batería, música con ⏮ ⏯ ⏭ y la indicación de navegación). Su Surface alimenta
  el mismo codificador y `TsRtpStreamer` que el espejo; no usa MediaProjection.
- `CarModeService`: servicio en primer plano `connectedDevice` y wake lock
  parcial de hasta 4 h, mientras el coche reproduce.
- `CarInfoListener`: acceso a notificaciones concedido por el propietario. Lee la
  sesión multimedia activa (título, artista, estado, controles) y la notificación
  en curso de Maps/Waze (título, texto e icono). No se registra ni se exporta.
- Táctil: las coordenadas UIBC están en el cuadro codificado, que es la propia
  pantalla virtual; se envían como MotionEvent a las vistas del panel, sin
  accesibilidad. En modo coche no se abre el lanzador del teléfono.
- Audio: la captura de reproducción necesita MediaProjection; en modo coche es
  opcional («Preparar audio por AionLink») y no se ha comprobado si sigue con el
  teléfono bloqueado. Por defecto el sonido va por el Bluetooth del coche
  (`audio=POR_BLUETOOTH_DEL_COCHE`).
- Conectar en modo coche no exige «Preparar captura».

Límites: Android no permite abrir apps de terceros (Maps, Spotify) en una
pantalla virtual privada; el panel muestra sus datos. Con el panel quieto el
codificador entrega cuadros solo al cambiar algo más repeticiones: en el S25,
58 unidades en 5 s (~12 fps), nunca por encima de los 30 negociados. No se ha
comprobado cómo lo muestra el coche ni la red Wi-Fi Direct con la pantalla
apagada durante una sesión real.

## Android Auto con Open Headunit

Open Headunit (paquete `com.andrerinas.headunitrevived`, AGPL-3.0) documenta una
API de automatización (headunit.andrerinas.com/guides/automation). El propietario
lo instaló (4.0.0-beta2); `dumpsys` confirma que `AutomationReceiver` registra
`ACTION_START_SELF_MODE` y `ACTION_DISCONNECT`.

`OpenHeadunit.startSelfMode` abre la app y envía el broadcast documentado. En modo
ESPEJO, AionLink lleva su pantalla al coche con el táctil por accesibilidad y el
audio capturado. AionLink no incluye su código ni participa en la autenticación de
Android Auto; si el self mode funciona depende de Open Headunit y de la versión de
Android Auto (17.9.664044 en el S25; su documentación avisa que 17.4+ puede
bloquearlo). Requiere la pantalla encendida (MediaProjection). No se abrió Open
Headunit desde el ordenador.

## Verificación y errores

- JVM 111 y Python 6 correctas; build y lint 0 errores.
- `CarModeDisplayTest` en el S25 (pantalla encendida) generó vídeo 1920x960 y
  procesó toques sin errores, pero falló el umbral de cuadros (58 en 5 s frente a
  ≥100 esperados). Se ajustó el umbral al comportamiento medido; no se repitió.
- **Incidente:** la ejecución con `am instrument` desactivó los servicios de
  accesibilidad (táctil) de AionLink y de la Sonda. Restaurarlos por ADB fue
  denegado por política de seguridad; el propietario debe reactivarlos en Ajustes.
  No volver a ejecutar instrumentación sin avisar al propietario.

## Configuración y prueba de Open Headunit (6 de noviembre de 2026, a petición del propietario)

Ajustes cambiados en Open Headunit 4.0.0-beta2 por ADB (`input tap`, con capturas
de pantalla) y guardados: **orientación Vertical → Horizontal (0°)** y **límite de
FPS 60 → 30**. Ya estaban bien: modo inalámbrico «Headunit Server», resolución
1080p, códec H.264 y «Receptor de audio» activado (Open Headunit reproduce el
sonido de Android Auto y AionLink lo captura). El servidor de desarrollador de
Android Auto estaba activo (TCP 5277 en escucha).

- Primer intento de self mode: versión 1.7 y TLS completados, pero Android Auto
  cerró su proceso de proyección con `IllegalStateException: Already connected`
  en `DeveloperHeadUnitNetworkService` (sesión anterior colgada).
- Segundo intento, tras el reinicio automático de ese proceso: `session state
  projecting`. Android Auto se ve en el S25 en horizontal.

Si aparece «Conexión perdida – Reintentando», volver a pulsar el botón (o reiniciar
el servidor de unidad principal en Android Auto). No se ha probado aún en el coche.
