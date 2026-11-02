# 0.21: el coche pide `disconnect` con vídeo en curso

Análisis del 2 de noviembre de 2026 sobre el diagnóstico (23) del propietario y
análisis estático de MiAuto 0.1.1 y Samsung Auto 3.1.02.20. No se ejecutaron APK
de terceros ni se cargaron bibliotecas. No se copió código de fabricantes.

## Observado en el vehículo (0.20)

Diagnóstico `aionlink-diagnostics (23).txt`, 8182 bytes, SHA-256
`f7a100c48c4301f1398b86b96f4665c8eb10da8861fb870c08607b5c77329901`. El propietario
informa de un error en la pantalla del coche, sin texto capturado, y de que no se
transmitió nada.

| Dato | Valor |
|---|---|
| `wfd_client_rtp_ports` | `RTP/AVP/TCP;unicast 19000 0 mode=play` |
| RTSP | M1 a M5, `M6_SETUP` por TCP y `PLAYING` 0,4 s después de iniciar AUTH |
| Conexión TCP al `client_port` | correcta; `M6_TCP_FAILED` no aparece |
| Flujo a los 36 s | 1159 unidades de vídeo, 12 376 paquetes RTP, 1668 tramas AAC, sin error de escritura |
| CONTROL | 23 recibidos, 19 heartbeats, 2 ACK, 0 fallos de descifrado |
| Últimos métodos | `1001, 1001, 1001, 1023, 1001, 1001, 1023, 1001` |
| UIBC | conectado, 0 eventos |

**Confirmado en el vehículo:** el receptor eligió TCP unicast, lo que valida la
causa del bloqueo en M3 que corrigió 0.20.

## Qué significa 1023

`categoría*1000+método`: categoría CONTROL, método 23. `UCarControlProtocol` en
MiAuto y `xa/s` en Samsung lo llaman `disconnect`. El mensaje `Disconnect` no tiene
campos, así que no lleva motivo.

- **MiAuto:** `ControlManager.sendDisconnect` sólo se alcanza desde
  `UCarAdapter.disconnect()`, API pública del SDK que la aplicación del coche invoca.
  Los temporizadores internos del SDK usan otro camino (`mConnectProxy.disconnect`).
- **Samsung:** `lb/g.G` responde al método 23 con
  `RemoteDisplayNative.notifyDisplayError(23)`; al 4 con `resumeDisplay()` y al 5 con
  `pauseDisplay()`.
- **AionLink 0.20:** sólo contaba el mensaje y seguía transmitiendo.

**Estimación por los heartbeats:** la primera petición llega unos 30 s después de
conectar y la segunda unos 4 s más tarde. No hay marcas de tiempo por mensaje.

## Qué cambió respecto a 0.19

En 0.19 el coche cerraba unos 10,07 s después de AUTH. En 0.20 siguió enviando
heartbeats más de 36 s.

**Inferencia:** en el SDK sólo `trySetDeviceConnected` cancela el temporizador de
10 s, y exige 90 %, que depende de `onCastVideoInitialized`. Si el firmware se
comporta igual, el coche reconoció el formato de vídeo. No está demostrado: el
porcentaje en pantalla no se registró.

## Hipótesis descartadas

Direcciones de `lib/arm64-v8a/libovmsink.so`:

- **Paquetes crudos (`wfd_av_raw_packet`):** `mIsAvRawPackets` (`+0xd64`) se inicia a 0
  (`0x98d18`) y sólo se escribe y se registra en M4 (`0x9e26c`–`0x9e2c4`). No se
  encontró otra lectura, así que no explica el fallo en esta versión del SDK.
- **Cifrado UIBC ausente en M4:** `Source do not support uibc encprted` (`0x9de58`) sólo
  se registra y continúa.
- **`ovm_control_capability` ausente en M4:** se registra y el coche respondió M4 200.

## Sin determinar

No se sabe qué dice el error ni qué lo provoca. Tampoco por qué el coche pidió
desconectar: pudo ser un temporizador de su interfaz o una acción en pantalla. Se
desconocen dos de los 23 mensajes, los que no entran en los ocho últimos, y no consta
si el coche llegó a enviar el método 4. Samsung pide también la configuración del
coche (24), lo que 0.20 no hacía; no consta que sea requisito.

La imagen se envía a 1280x720 (16:9) y el coche declara `display=1920:960` (2:1).
Samsung contiene `mode=%d;display=%d:%d;dpi=%d;fps=%d`, pero no se ha demostrado que
la diferencia de tamaño cause un error.

## Cambios en 0.21

- El método 23 detiene vídeo y canales con `COCHE_PIDIO_DESCONEXION`.
- El método 4 pide un fotograma clave.
- Cada mensaje del coche que no sea heartbeat queda en el registro con hora, categoría,
  método, nombre y tipo, hasta 40 por canal. Sin contenido.
- RTSP registra método y nombres de parámetros de lo que pide el receptor, sin valores,
  y el estado del keepalive.
- El diagnóstico añade los demás parámetros de M3, salvo `wfd_presentation_URL`.
- Tras el primer heartbeat se envía una `GetUCarConfigRequest` (24, REQ, cuerpo vacío),
  como `lb/g.d` en Samsung. **Es un mensaje nuevo hacia el coche.** De la respuesta sólo
  se exportan pantalla, vídeo, dpi, fps, micrófono, baja latencia, `workModes`, HEVC y
  versión del SDK. La dirección Bluetooth (campo 1) y el campo propio (16) se omiten.

## Cómo interpretar la siguiente prueba

| Resultado | Lectura | Siguiente paso |
|---|---|---|
| Sin método 4 antes del error | el coche no empezó a mostrar el flujo | revisar la decodificación y el tamaño de vídeo |
| Método 4 y después método 23 | el coche empezó a mostrarlo y lo abandonó | revisar TS, PTS y SPS |
| Método 23 poco después de tocar la pantalla | desconexión por acción en pantalla | repetir sin tocar nada |
| `vídeoAncho` y `vídeoAlto` distintos de 1280x720 | el coche espera otro tamaño | probar ese tamaño si M3 lo permite |
| `wfd_idr_request` repetido | el decodificador no encuentra fotogramas clave | revisar SPS, PPS e IDR |
| El coche se cierra tras la solicitud 24 | la solicitud molesta en este firmware | retirarla |

Sigue sin demostrarse sesión ICCOA o proyección en el AION hasta observarlo en el
vehículo estacionado con el propietario presente.
