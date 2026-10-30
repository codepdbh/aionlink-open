# 0.18: sesión posterior a AUTH y proyección Wi-Fi Display

Análisis estático del 30 de octubre de 2026 sobre Samsung Auto 3.1.04.10
(`5633d2f9…`), Samsung 3.1.02.20 y Xiaomi CarWith 4.0.14 (`c7888ec5…`).
No se ejecutaron APK de terceros ni se copiaron fuentes, bibliotecas o
secretos. Para la biblioteca nativa solo se extrajeron cadenas imprimibles
(script propio en Python); el código de AionLink es independiente. Rutas
relativas a `analysis/java/<extracción>/sources/` (locales, fuera de Git).

## Qué hace Samsung después de AuthConfirm

1. `com/ucarmd/a` → `ShareLinkService.f` → `RemoteDisplayNative.init` y
   `Q5/e.java:a(ipLocal)`.
2. **Canal CONTROL** (`r5/h.java`, `r5/f.java`): `SocketChannel(CONTROL,
   server=true, encrypted=true)`. `SocketChannel.start` en modo servidor hace
   `bind(ipLocal, 57219)` y `accept()`: **el coche se conecta al teléfono**.
   - Cifrado (`SocketChannel` envío, `SocketChannelReadTask` lectura): cabecera
     de 20 bytes en claro con la longitud actualizada; cuerpo = sobre AES-GCM de
     la clave `session_key` derivada en AUTH. Cuerpo vacío → sin sobre.
   - Al conectar (`r5/f.onConnect`): `BluetoothMacInfo` (método 22, SEND_SYNC,
     campo 1 op ADD=0, campo 2 SHA-256 hex del MAC BT o vacío).
   - Heartbeat (`B2/k.java`, `UCarControlProtocol.createHeartbeatMessage`):
     método 1, SEND, `Heartbeat{timestamp(1)=ms}`, cada 2 s.
   - ACK (`SocketChannel.onMessage`): solo para SEND_SYNC; RAW, RES,
     categoría ACK (7), método 0, misma secuencia, cuerpo vacío.
   - Mensajes del coche manejados (`p016b3/a.java` caso 18): 1 heartbeat,
     21 cámara, 23 desconexión, 26/28 lista/detalle de apps, entre otros.
3. **Display** (`A5/g.java`, `RemoteDisplayNative.listen`): biblioteca nativa
   `heycastcore`. Sus cadenas muestran una variante de AOSP
   `WifiDisplaySource` (Wi-Fi Display/Miracast): `OPTIONS * RTSP/1.0`,
   `Require: org.wfa.wfd1.0`, `GET_PARAMETER rtsp://localhost/wfd1.0`,
   `wfd_video_formats`, `wfd_audio_codecs`, `wfd_client_rtp_ports`,
   `wfd_uibc_capability`, `wfd_presentation_URL: rtsp://%s/wfd1.0/streamid=0 none`,
   `wfd_trigger_method:`, `Transport: RTP/AVP/%s;unicast;client_port=%d-%d;server_port=%d-%d`,
   `TSPacketizer` (MPEG-TS) y el añadido propio `wfd_car_display_mode`
   ("Sink doesn't report wfd_car_display_mode").
   - Puerto RTSP: Samsung 3.1.04.10 pasa a `nativeListen` "ip:57219"; Xiaomi
     `CarlinkStateMachine.java:516,548` escucha en `ChannelType.RTSP` (7236).
     AionLink usa 7236 (corroborado por Xiaomi) y deja 57219 para CONTROL.
     **Discrepancia abierta**; si el coche no conecta a 7236, es lo primero a revisar.
   - Puerto RTP del servidor: constante `ChannelType.RTP` 15550.
   - Capacidades de vídeo (`A5/g.java`): AVC 1280x720@60 por defecto; HEVC
     opcional; modos espejo 1920x1920 en algunos modelos.

## Implementación independiente en 0.18

| Componente | Archivo | Notas |
|---|---|---|
| Escritor de tramas UCar | `protocol-iccoa/.../UCarFrameWriter.kt` | Cabecera y CRC propios |
| CONTROL | `transport-wifi/.../SessionControlServer.kt` | Servidor 57219, cifrado, heartbeat 2 s, BluetoothMacInfo vacío, ACK |
| Fuente WFD | `projection/.../WfdSource.kt` | RTSP M1–M7, keep-alive, IDR, TEARDOWN |
| Formatos | `projection/.../WfdFormats.kt` | Tabla CEA pública; prefiere 720p30 CBP |
| TS + RTP | `projection/.../TsRtpStreamer.kt` | PAT/PMT/PES, PCR, PID 0x1011, RTP PT 33, 7 TS/datagrama |
| Captura | `app/.../ProjectionService.kt` | MediaProjection en servicio en primer plano, H.264 por hardware |
| Orquestación | `app/.../ProjectionSession.kt` | Escucha antes de AuthConfirm; vídeo tras PLAY |

Los servidores CONTROL y RTSP se abren **antes** de enviar AuthConfirm para que
el coche los encuentre al instante. La clave de sesión se entrega como copia y
se borra al cerrar. No se exportan claves, direcciones ni contenido; el
diagnóstico incluye `wfd_video_formats`, `wfd_audio_codecs` y
`wfd_car_display_mode` del receptor (describen la pantalla del coche, truncados
a 96 caracteres imprimibles).

## Limitaciones conocidas

- Sin audio: M4 declara un códec de la lista del receptor, pero no se envía pista.
- Sin UIBC (táctil, 4321) ni canal SENSOR; el coche puede exigirlos.
- `BluetoothMacInfo` va vacío: Android no expone el MAC BT a apps normales.
- Resolución CEA fija (720p30 preferida); `wfd_car_display_mode` solo se registra.
- Android 14+ permite un único VirtualDisplay por consentimiento: hay que volver
  a pulsar **Preparar captura de pantalla** en cada sesión.
- Nada de esto se ha verificado con el AION: las pruebas usan pares sintéticos.

## Pruebas propias

- `WfdSourceTest`: receptor sintético recorre M1→PLAY→TEARDOWN; comprueba M3
  (incluye `wfd_car_display_mode`), M4, M5, transporte SETUP y puerto 15550.
- `TsRtpStreamerTest`: cabecera RTP, secuencia, 188 bytes, CRC PSI, PCR,
  contadores de continuidad y reconstrucción exacta del flujo H.264.
- `WfdFormatsTest`: selección de modo, audio, puertos y saneado.
- `SessionControlServerTest`: coche sintético recibe BluetoothMacInfo y un
  heartbeat que descifra, envía SEND_SYNC y recibe ACK con su secuencia.
