# 0.20: el coche elige RTP sobre TCP y AionLink se detenía en M3

Análisis del 2 de noviembre de 2026 sobre el diagnóstico (22) del propietario y
análisis estático de MiAuto 0.1.1 y Samsung Auto 3.1.02.20. No se ejecutaron APK
de terceros ni se cargaron bibliotecas: JADX para Java, y cadenas y desensamblado
AArch64 de `libovmsink.so` leyendo bytes. No se copió código de fabricantes.

## Observado en el vehículo (0.19, 31 de octubre)

Diagnóstico `aionlink-diagnostics (22).txt`, 15 640 bytes, SHA-256
`0e54e0ce64e04dc64b48ae2378f8d0e7c6071e41c9688cdd863f6a880dab11a1`, con tres
intentos. Los dos primeros terminan igual; el tercero se exportó antes de terminar:

1. ClientInfo confirmado, ServerInfo P2P y grupo conectado.
2. `RESPONSE_VERIFIED_PROOF_AND_SIGNATURE`, resultado 0, `workModes=0x1`, `CONFIRM_SENT`.
3. El coche se conecta a CONTROL, MEDIA y RTSP. CONTROL recibe heartbeats descifrados.
4. RTSP M1 y M2 correctos; después `M3_UNSUPPORTED; vídeo=true; puertoRTP=false`.
5. AionLink no envía M4. Unos 10,07 s después de iniciar AUTH, el coche cierra todo.

El propietario informa de 80 % en pantalla y luego un mensaje de dispositivo no
compatible. Ese texto no se ha capturado literalmente ni se ha localizado.

**Hecho:** el bloqueo de M3 es local; `WfdFormats.clientRtpPort` sólo aceptaba UDP.
**Límite:** 0.19 no exportaba `wfd_client_rtp_ports`, así que el valor exacto del
coche no consta. 0.20 lo exporta.

## Relación entre MiAuto y este vehículo

MiAuto incluye `com.ucar.vehiclesdk` y `libovmsink.so`, compilada desde
`ucarhusdk/.../wifi-display/WifiDisplaySink.cpp`, el SDK del lado del coche.

| Campo | Diagnóstico del coche | Literal en `libovmsink.so` |
|---|---|---|
| Modo de pantalla | `mode=0;display=1920:960;dpi=160;fps=30` | `mode=0;display=%d:%d;dpi=%d;fps=%d` |
| Audio | `AAC 0000000F 00` | `wfd_audio_codecs: AAC 0000000F 00` |
| UIBC | `input_category_list=GENERIC;generic_cap_list=Keyboard, Mouse, SingleTouch;hidc_cap_list=none;por…` | la misma cadena, con `port=none` |

**Inferencia:** el receptor del AION pertenece a la misma familia. No demuestra que
sea la misma versión del SDK ni que su firmware se comporte igual en todo.

## Evidencia estática del sink

Direcciones de `lib/arm64-v8a/libovmsink.so`:

- **Transporte** (`0x9d484`–`0x9d4ac`, `0x9d55c`–`0x9d5e4`): al responder a M3 pone
  el indicador TCP a 1 e interleaved a 0, y emite `RTP/AVP/TCP;unicast 19000 0 mode=play`.
  Un coche con este código sería rechazado por nuestro parser de 0.19, lo que
  coincide con `puertoRTP=false`.
- **Quién escucha** (`0xa6660`–`0xa6678`): `RTPReceiver` crea la sesión TCP con la
  forma de escucha (dirección, puerto, notificador, id). El teléfono se conecta al
  `client_port` que el coche indica en SETUP.
- **Enmarcado** (`0x90b08`–`0x90ba0`): en modo datagrama reserva `tamaño + 2` y escribe
  el tamaño en big-endian antes del paquete; límite 65 535.
- **`server_port`** (`0x9c718`–`0x9c7d4`): exige `%d-%d` con RTCP = RTP + 1. Si no,
  registra `Invalid server_port description` y devuelve error. La respuesta de 0.19
  a un `client_port` sin RTCP (`server_port=15550`) habría fallado aquí.
- **M4 sin `ovm_control_capability`** (`0x9e108`–`0x9e30c`): se registra el error y
  se omite el resto del análisis de capacidades; ese camino no apunta a un rechazo.
  Samsung contiene `ovm_control_capability:port=%d`, sin valor de puerto conocido, y
  AionLink no lo envía. Si M4 o SETUP fallan, es de lo primero a revisar.

`libheycastcore.so` de Samsung acepta los mismos tres formatos de
`wfd_client_rtp_ports` (UDP unicast, TCP unicast y TCP interleaved).

## Qué significa el 80 %

`UCarAdapter.sProgressMap` asocia pasos a porcentajes. En este SDK:

| Paso | % | Evento |
|---|---|---|
| 10 | 80 | `handleChannelStarted`: canales iniciados tras recibir la dirección del cliente |
| 11 | 90 | `onWfdSinkReady`: el sink ya recibió vídeo y conoce formato y tamaño |
| 12 | 100 | `trySetDeviceConnected`: con ≥ 90 %, en el siguiente heartbeat |

`TimerManager.CONNECT_TIME_OUT_MS` es 10 000 ms. `onAuthenticationOk` lo reinicia y
sólo `trySetDeviceConnected` lo cancela al llegar a ese punto; al vencer emite
`ERROR_CONNECT_TIMEOUT` (10006).

**Hipótesis, compatible con los tiempos observados:** el coche queda en 80 % porque
nunca recibe vídeo, vence el temporizador de 10 s y su interfaz lo presenta como
dispositivo no compatible. No es un rechazo por modelo demostrado. La cadena no
aparece en MiAuto ni en `libovmsink.so`; la interfaz del AION no está disponible
para comprobarlo.

## Cambios en 0.20

- `WfdFormats.clientRtpPorts` acepta UDP y TCP unicast. Interleaved sigue sin
  implementar y `SETUP` con interleaved responde 461.
- `WfdSource` conecta al `client_port` del coche durante SETUP, desde la dirección
  local del grupo, con plazo de 2,5 s; si falla publica `M6_TCP_FAILED` y responde 500.
- `server_port` se envía siempre como `15550-15551`, también en UDP.
- `TsRtpStreamer.tcpFrame` antepone 16 bits de longitud; `ProjectionSession` envía
  por TCP cuando corresponde y detiene el flujo con `RTP_TCP_IO` si falla la escritura.
- El diagnóstico añade `wfd_client_rtp_ports`, y el extracto de valores del receptor
  pasa de 96 a 160 caracteres para no cortar el `port=` de UIBC.

## Cómo interpretar la siguiente prueba

| Resultado | Lectura | Siguiente paso |
|---|---|---|
| `wfd_client_rtp_ports` distinto de TCP o UDP unicast | hipótesis de transporte incorrecta | implementar el formato real observado |
| `M4_REJECTED` o sin `M6_SETUP` | el coche exige algo más en M4 | revisar `ovm_control_capability` y `uibc_encrypted` |
| `M6_TCP_FAILED` | no se pudo abrir el puerto del coche | revisar ruta P2P y momento de conexión |
| `PLAYING`, unidades y datagramas > 0, sin imagen | el coche no acepta el flujo | revisar TS, perfil H.264 y modo 1920:960 |
| 90 % o 100 % en pantalla | el coche recibe vídeo | validar imagen, táctil y audio |

Sigue sin demostrarse sesión ICCOA o proyección en el AION hasta observarlo en el
vehículo estacionado con el propietario presente.
