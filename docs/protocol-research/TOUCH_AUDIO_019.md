# 0.19: táctil del vehículo (UIBC) y audio

Análisis estático del 30 de octubre de 2026 sobre Samsung Auto 3.1.04.10
(`5633d2f9…`). No se ejecutaron APK de terceros ni se copiaron fuentes o
bibliotecas; de `heycastcore` solo se leyeron cadenas imprimibles. Rutas
relativas a `analysis/java/<extracción>/sources/` (locales, fuera de Git).

## Táctil: UIBC

- `Q5/e.java:a` arranca UIBC en un hilo (`Q5/d.java` caso 1) junto al canal
  CONTROL. `Q5/f.java:i` crea un `ServerSocket` en el **puerto 4321** del
  teléfono y acepta al receptor: el coche se conecta al teléfono.
- Negociación WFD: si el receptor declara `wfd_uibc_capability`, la biblioteca
  responde en M4 con `wfd_uibc_capability: input_category_list=GENERIC,HIDC;
  generic_cap_list=SingleTouch, MultiTouch;hidc_cap_list=Keyboard/USB,
  Keyboard/BT;port=4321` (cadena literal). Si el receptor declara cifrado añade
  `;uibc_encrypted=true` y usa la clave `uibc_session_key` (igual a la de
  sesión, `KeyNegotiator.agreeOnSessionKey`). AionLink **no** añade ese sufijo.
- Formato (`Q5/f.java:c`): objetos JSON planos concatenados, separados al
  encontrar `}`. `type` 1 = toque: `action` (códigos MotionEvent), `width`,
  `height`, `count`, `trackID<i>`, `x<i>`, `y<i>`. Otro `type` = tecla:
  `action`, `keycode`, `metaState` (códigos KeyEvent).
- Coordenadas en el espacio del vídeo; Samsung las escala a la pantalla del
  teléfono (`f`, `g`) e inyecta con `InputManager.semInjectInputEvent`, una API
  de sistema que una app normal no puede usar.

Implementación propia: `projection/.../Uibc.kt` (servidor 4321, analizador y
mapeo con bandas negras al reflejar una pantalla vertical en vídeo apaisado) y
`app/.../TouchInjectionService.kt`, un **servicio de accesibilidad** que el
propietario activa en Ajustes. Reproduce el primer puntero como gesto
(toque, pulsación larga, deslizar/arrastrar al soltar) y las teclas atrás (4),
inicio (3) y recientes (187). Limitaciones: sin pellizco multitáctil, el gesto
se envía al levantar el dedo (latencia en arrastres largos) y no lee pantalla.

## Audio

- `RemoteDisplayNative.startAudioRecord` → `p062i5/c`: con "canal
  independiente" crea tres fuentes (`p062i5/a`): asistente 16 kHz
  (`AudioType` 3), música 48 kHz (16 `STREAM_CAST_MUSIC`) y navegación/TTS
  48 kHz (6). Captura con `AudioPlaybackCapture` (`j5/b`, `j5/c`): PCM 16 bits
  estéreo.
- Música: si el modelo del coche está en una lista remota usa el canal MEDIA;
  si no (caso por defecto), `p075k5/c` pasa el PCM a
  `RemoteDisplayNative.nativeAudioDataCallBack`, es decir, **dentro del flujo
  Wi-Fi Display**. La biblioteca registra "Using AAC audio." / "Using PCM
  audio." y declara `AAC 00000001 00` o `LPCM 00000002 00`.
- Canal MEDIA (`W4/b.java`): `SocketChannel(MEDIA 57229, servidor, cifrado)`;
  tramas RAW, SEND, categoría AUDIO (4), método = `AudioType`, cuerpo = audio.
  Antes se anuncia con `NotifyAudioPlayerState` (CONTROL, método 8).
- Canal SENSOR (`r5/j`, `W4/a`): `SocketChannel(SENSOR 57239, servidor,
  cifrado)`, abierto al conectar el display.

Implementación propia: `app/.../AudioCapture.kt` captura la reproducción
(USAGE_MEDIA, GAME, UNKNOWN: las únicas permitidas a apps normales) a 48 kHz
estéreo y la codifica a AAC-LC con cabecera ADTS. `TsRtpStreamer` la añade al
flujo como pista AAC (PID 0x1100, stream_type 0x0F, PES 0xC0 acotado) cuando
el coche acepta AAC. MEDIA y SENSOR se abren como escuchas cifradas
**pasivas** (aceptan, descifran y confirman SEND_SYNC) para que el coche
pueda conectarse.

Limitaciones: sin LPCM (si el coche solo acepta LPCM no se envía audio y el
diagnóstico lo indica); sin audio por MEDIA ni `NotifyAudioPlayerState`; las
indicaciones de navegación (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) y apps que
bloquean la captura no se oyen; el teléfono sigue reproduciendo el sonido en
paralelo; sin micrófono del coche (llamadas). Requiere permiso RECORD_AUDIO.

## Verificación

Pruebas propias con datos sintéticos: `UibcTest` (fragmentos, toque, tecla,
mapeo con bandas, servidor), `TsRtpStreamerTest` (PMT con audio, PES acotado,
ADTS), `WfdSourceTest` (M4 con UIBC, códec elegido) y `SessionControlServerTest`
(canal pasivo solo ACK). Nada verificado aún con el AION.
