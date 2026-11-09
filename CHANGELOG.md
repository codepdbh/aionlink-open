# Cambios

## 0.41.0-research — 2026-11-08

- Con Open Headunit como fuente, el sonido de Android Auto llega a AionLink por el puente (pipe PCM) y se envía al coche en AAC; ya no suena en el teléfono ni requiere captura de audio.
- La prueba local informa los KiB recibidos por el puente, las tramas AAC y el pico.
- Requiere Open Headunit con `patches/open-headunit/bridge-audio.patch`. Verificado solo en el teléfono, sin el AION.

## 0.40.0-research — 2026-11-07

- El audio capturado informa su nivel cada 5 s o si pasa a silencio, para saber si llega sonido de Open Headunit al coche.
- Sin verificar con el AION; no instalada: falta la llave de firma compartida en este PC.

## 0.35.0-research — 2026-11-06

- Modo coche: panel propio de AionLink (reloj, música con controles, navegación de Maps/Waze) enviado al coche sin captura de pantalla, para usar con el teléfono apagado.
- Táctil del coche directo al panel; servicio en primer plano durante la sesión.
- Botón para abrir Android Auto con Open Headunit (self mode) y llevarlo al coche en modo espejo.

## 0.34.0-research — 2026-11-06

- Imagen más nítida: codifica a la resolución que pide el coche (1920x960) con bitrate proporcional (11 Mbps).
- Botón «Calidad de imagen» para volver al modo compatible 1280x720 si el coche lo necesita.
- La sonda de pantalla apagada corre en un servicio en primer plano, a 1920x960, con batería y temperatura.
- Firma opcional con `android/keystore.properties` y corrección de una carrera al cerrar RTSP.

## 0.28.0-research — 2026-11-03

- Lanzador propio con las apps que elige el propietario, abierto al pulsar AionLink en el coche; se puede desactivar para compartir pantalla sin más.
- Botones de atrás e inicio sobre la pantalla compartida, para usarlos desde el táctil del coche.
- Horizontal opcional durante la proyección, restaurado al terminar o tras un cierre inesperado.
- Sigue siendo pantalla compartida: el teléfono debe estar encendido y desbloqueado. Sin verificar con el AION.

## 0.27.0-research — 2026-11-03

- El botón de accesibilidad avisa si el táctil está inactivo, y el registro indica si llegan toques sin servicio activo.
- La sonda compara la vista en pantalla virtual con el dibujo directo, con el teléfono bloqueado.
- 0.26 confirmó en el AION unos 30 cuadros/s y mejor transmisión, según informa el propietario; 0.27 sin verificar con el AION.

## 0.26.0-research — 2026-11-03

- El codificador se limita a los fps negociados y pide baja latencia, para evitar la cola de cuadros en el coche.
- Brillo mínimo opcional durante la proyección, restaurado al terminar o tras un cierre inesperado.
- El diagnóstico añade cuadros/s, escrituras lentas, batería por sesión y encendido o apagado de la pantalla.
- Sonda local para saber si una pantalla propia sigue viva con el teléfono bloqueado.
- 0.25 confirmó en el AION proyección y táctil, según informa el propietario; 0.26 sin verificar con el AION.

## 0.25.0-research — 2026-11-02

- `AppStateChanged` se envía como `SEND_SYNC`, el único tipo que acepta el analizador del coche; antes iba como `SEND`.
- El registro añade los ACK del coche y el `AppStateChanged` enviado con sus campos.
- 0.24 confirmó en el AION el icono "AionLink" y los métodos 4 y 30 al pulsarlo, seguidos de cierre en unos 65 ms; 0.25 sin verificar con el AION.

## 0.24.0-research — 2026-11-02

- Se retiran los avisos no solicitados de 0.23: el AION cerró la sesión 84 ms después de `AppStateChanged`.
- Con AUTH automático, la conexión no empieza si la captura no está preparada, y cada permiso de captura sirve para una sola conexión.
- 0.24 sin verificar con el AION.

## 0.23.0-research — 2026-11-02

- Tras leer el coche los detalles de la app, el teléfono envía una vez `AppStateChanged` (STARTED, mostrar) y `NotifyMirrorState` (BACK_TO_FOREGROUND).
- La app usa el id 2; `AppStateChanged` deja la marca de tiempo en 0, porque el reproductor del SDK la compara con el tiempo del vídeo.
- 0.22 confirmó en el AION el modo 4 y la lectura de la lista de apps, pero sin imagen; 0.23 sin verificar con el AION.

## 0.22.0-research — 2026-11-02

- AUTH declara también el modo 4 (`APP_FULLSCREEN_CAST`), el único que el AION informa, por el error "integrated desktop screen casting".
- CONTROL responde a la lista de apps, sus detalles e `InvokeApp` con una única app propia que representa el espejo del teléfono.
- 0.21 confirmó en el AION el texto del error y `workModes=4`; 0.22 sin verificar con el AION.

## 0.21.0-research — 2026-11-02

- Al recibir `disconnect` (método 23) del coche se detienen vídeo y canales; antes se ignoraba.
- El método 4 (coche en primer plano) pide un fotograma clave.
- Solicitud de configuración del coche (método 24), con exportación limitada y sin dirección Bluetooth.
- El diagnóstico registra con hora los mensajes del coche y las peticiones RTSP, sin contenido, y los demás parámetros de M3.
- 0.20 confirmó en el AION RTP sobre TCP y `PLAYING`, pero sin imagen; 0.21 sin verificar con el AION.

## 0.20.0-research — 2026-11-02

- RTSP deja de detenerse en M3 cuando el receptor elige `RTP/AVP/TCP;unicast`.
- Vídeo y audio por TCP: el teléfono se conecta al `client_port` y enmarca con 16 bits de longitud.
- `server_port` siempre como par `15550-15551`; `SETUP` interleaved responde 461.
- El diagnóstico exporta `wfd_client_rtp_ports`; sin verificar con el AION.

## 0.19.0-research — 2026-10-30

- Táctil del vehículo: servidor UIBC (4321), JSON observado y gestos mediante un servicio de accesibilidad.
- Audio del teléfono capturado (AudioPlaybackCapture) y enviado como AAC dentro del flujo Wi-Fi Display.
- Escuchas cifradas pasivas MEDIA (57229) y SENSOR (57239); M4 declara UIBC y prefiere AAC.
- Sin LPCM, pellizco ni micrófono; sin verificar con el AION.

## 0.18.0-research — 2026-10-30

- Canal CONTROL del teléfono (57219) con cuerpos cifrados por la clave de sesión, heartbeat y ACK.
- Fuente Wi-Fi Display propia: RTSP M1–M7, MPEG-TS/RTP y vídeo H.264 desde MediaProjection.
- Los listeners se abren antes de AuthConfirm; botón para autorizar la captura de pantalla.
- Sin audio, táctil (UIBC) ni SENSOR; sin verificar con el AION.

## 0.17.0-research — 2026-10-30

- Solicitud AUTH con secuencia positiva y workModes 257 observados en Samsung.
- Derivación HKDF-SHA256 de la clave de sesión y envío de AuthConfirm cifrado.

## 0.4.0-research — 2026-10-22

- Módulo Android USB/AOA con detección, permiso explícito, apertura y cierre del descriptor.
- Registro del accesorio ICCOA/CarLink observado en Samsung y CarWith, sin permisos privilegiados.
- Lector multiplexado original incremental con límites y seis pruebas sintéticas.
- Inicio experimental opcional de canales RTSP/UIBC según evidencia estática, sin sesión simulada.
- Distinción entre alimentación USB, ADB/carga, dispositivos host y accesorio AOA.
- USB y Bluetooth disponibles por separado; sin imponer un rol Bluetooth todavía desconocido.

## 0.3.0-research — 2026-10-22

- Conexión GATT real al BLE seleccionado por el propietario y descubrimiento de servicios.
- Reconocimiento de UnionShare y metadatos de características conocidas, sin leer/escribir valores.
- Timeout, cancelación, protección frente a callbacks obsoletos y cierre automático del enlace.
- Acción explícita de conexión y resultado de inspección separados de la sesión ICCOA.
- Prueba de pantalla inicial y botón Detener en S25; 23 pruebas automatizadas aprobadas.
- Receptor objetivo aclarado por el propietario: ICCOA CarLink y Huawei Car; no CarbitLink.

## 0.2.0-research — 2026-10-22

- Lectura ADB de registros retenidos por UID y análisis de Samsung Auto 3.1.04.10 instalado.
- Diagnóstico documentado de fallos OEM; comparación de rutas Samsung/Xiaomi/OPPO/CarLife.
- Especificación estática de cabecera UCar y lector propio offline con CRC y límites.
- Indicios BLE por UUID observados, sin declarar compatibilidad o sesión.
- Exportación con transporte, señal, conectabilidad, UUID permitidos y timestamps.
- Protección frente a callbacks BLE de búsquedas anteriores; botón coherente con búsqueda activa.
- 6 pruebas Python y 14 JVM; APK compilada y actualizada por ADB en el S25.

## 0.1.0-research — 2026-10-21

- Inventario local SHA-256, firma pública, manifest, recursos, DEX y bibliotecas.
- Automatización Apktool/JADX con registros y salidas separadas por hash.
- Búsqueda con índices de rutas/líneas/términos y comparación de metadatos.
- Inspección ELF mediante LLVM del NDK, sin cargar bibliotecas.
- Base Android Kotlin modular, StateFlow, tests, Compose y descubrimiento Bluetooth.
- Perfil AION sin verificar y diagnósticos exportables sin nombres ni direcciones.
- Documentación de límites, evidencia y procedimiento de pruebas estacionarias.
