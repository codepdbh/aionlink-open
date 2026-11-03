# 0.22: el AION sólo declara proyección integrada (modo 4)

Análisis del 2 de noviembre de 2026 sobre el diagnóstico (24), tres fotografías de la
pantalla del coche hechas por el propietario y análisis estático de MiAuto 0.1.1 y Samsung
Auto 3.1.02.20. No se ejecutaron APK de terceros ni se cargaron bibliotecas. No se copió
código de fabricantes.

## Observado en el vehículo (0.21)

Diagnóstico `aionlink-diagnostics (24).txt`, 15 619 bytes, SHA-256
`07973590646f335640d5e3cc7f4c95150962d90791df2fc0ffbccafcc61ce039`.

**Texto del error en pantalla**, con el coche en 80 % y "Connecting SM-S938B":

> Your phone does not support integrated desktop screen casting.
> Please try upgrading your phone system.

Después aparece "Loading" a pantalla completa y luego la lista de receptores con "ICCOA
Carlink: SM-S938B connected" y un aviso de que el teléfono se desconectó de Bluetooth. El orden
de las fotografías lo deduce este análisis; no llevan hora. "Please remove the protective
film" parece impreso en una lámina física sobre la pantalla, no un mensaje del software.

| Dato | Intento 1 | Intento 2 |
|---|---|---|
| Captura preparada al llegar `PLAY` | no | sí |
| Vídeo | no empezó | 1306 unidades, 11 489 RTP, 1070 AAC |
| Fin | el coche cierra unos 10 s tras `CONFIRM_SENT` | sigue conectado al exportar, 44 s después |
| Método 4 (`notify_car_to_foreground`) | no | no |
| Método 23 (`disconnect`) | no | no |
| Métodos 26, 28 o 30 (apps) | no | no |

| Dato común | Valor |
|---|---|
| `GetUCarConfigResponse.workModes` (campo 18) | **4** |
| `AuthResponse.workModes` | **0x1** |
| `workModes` enviado por AionLink | 257 (1 + 256) |
| Pantalla / vídeo del coche | 1920x1080 / 1920x960 |
| SDK del coche | `v2.0.2-202503242133-f096966b` |
| Parámetros M3 | sin `iccoa_support_fusion` |

**Confirmado en el vehículo:** con vídeo el coche no aplica el cierre de unos 10 s, y sin
vídeo sí. El intento 2 no pidió `disconnect`, así que ese mensaje no acompaña siempre al error.

## Qué significa cada modo

`com/ucar/protocol/UCarWorkMode.java`: 1 `NORMAL_CAST`, 2 `DATA_TRANSFER`,
4 `APP_FULLSCREEN_CAST`, 8 `APP_WINDOW_CAST`. `UCarAdapter.isWorkInFusionCastMode` es cierto
para 4 y 8.

**Inferencia:** "integrated desktop" del error es este modo *fusion*. Es una correspondencia
por significado; el texto no está en ningún APK local, sino en el firmware del coche.

## Por qué sale el error

1. `UCarAuthService.java:94` calcula `request.workModes & soportados`. Con el valor que el
   coche declara en su configuración, 257 & 4 = 0: ningún modo en común.
2. `UCarAdapter.notifySelectWorkMode` parte de `NORMAL_CAST` y sólo lo cambia si el oyente
   devuelve un modo válido. Eso explica cómo el coche responde 0x1 sin declarar el modo 1.
3. El sink añade `iccoa_support_fusion: supported` a M3 sólo con el indicador de fusion
   (`libovmsink.so`, `+0xd5d` en `0x9d738`). No aparece en el diagnóstico, así que el coche
   quedó fuera de ese modo.
4. `UCarAdapter.startApp` en modo normal no envía nada al teléfono. En modo fusion envía los
   métodos 4 y 30. El coche no envió ninguno de los dos.

**Hipótesis principal, sin verificar:** el coche sólo presenta la proyección en modo 4. Al no
encontrarlo, avisa con este error y no muestra el flujo, aunque el enlace sigue activo. No se
ha visto el código del firmware que muestra el aviso. También podría depender de otra
comprobación, por ejemplo de la marca: la tarjeta del coche indica "Xiaomi, OPPO, vivo phones".

## Samsung tampoco lo implementa

`ShareLinkService.P()` declara sólo el modo 1 y desactiva 4 y 8 de forma explícita. Sus
manejadores de 26, 28 y 30 (`lb/g.java:t, u, v`) analizan el mensaje y no responden. La
versión 3.1.04.10 analizada en 0.17 declaraba 257. **No hay referencia local de un teléfono
con modo 4**, así que el lado teléfono de 0.22 se deriva de lo que el SDK del coche espera.
CarWith de Xiaomi no está en esta copia local.

## Qué espera el coche en modo fusion

| Paso | Método | Evidencia | Requisito |
|---|---|---|---|
| Lista de apps | 26 REQ → 27 RES | `ControlManager.getAppList` | 3 s; `total > 0` e igual a las entradas |
| Detalles | 28 REQ → 29 RES | `ControlManager.getAppDetailInfo` | Pide siempre el id 1, que usa como icono y nombre |
| Abrir app | 4, luego 30 | `UCarAdapter.startApp` | Si no llega 31 `STARTED` o `RESUMED` en 3 s, llama a `pauseCast` |
| Estado | 31 SEND | `handleAppStateChanged` | `state` dentro del enumerado |

En el sink, el indicador de fusion sólo se lee para esa línea de M3 (única lectura de `+0xd5d`),
así que RTSP, TS y RTP nativos no deberían cambiar. Lo que hagan otras capas del firmware se
desconoce.

## Cambios en 0.22

- AUTH declara `workModes` 261 (1 + 4 + 256). Sigue incluyendo los modos anteriores.
- CONTROL responde 26, 28 y 30 con una sola app (id 0, categoría MEDIA, como el sustituto de
  `UCarAdapter` en modo normal) que representa el espejo del teléfono. Nombre "AionLink" e
  icono propio de color sólido. No se envía la lista de apps instaladas ni datos personales.
- `AppStateChanged` copia id y `displayMode` de `InvokeApp`. Se detecta el cuerpo vacío, que
  es un `InvokeApp{0, START, 0}` válido en proto3.
- MEDIA y SENSOR siguen pasivos y no responden.

**Son mensajes nuevos hacia el coche**, con métodos y campos del SDK, y sólo se han probado con
un coche sintético. No se implementan ventanas por app, `displayMode` real, parada de vídeo
con `PAUSE` ni `STOP`, ni `NotifyMirrorState`.

El vídeo sigue a 1280x720. El coche declara `28` como formato nativo en
`wfd_video_formats` (índice CEA 5, 1280x720p30) pero `display=1920:960`, así que la imagen
podría verse deformada o con bandas. No se cambia aquí para probar una sola variable.

## Resultado en el vehículo (0.22)

Diagnóstico `aionlink-diagnostics (25).txt`, 9592 bytes, SHA-256
`e50f48a53742791be70fa00276b939ce11eb0def4cd805f9d60337d849fd8c46`, y una fotografía con
"ICCOA Carlink: SM-S938B connected" sin el aviso visible. El aviso podría ser temporal, así
que la fotografía sola no demuestra que no saliera.

**Confirmado en el vehículo:**

- AUTH responde `workModes=0x4` con resultado 0, y M3 incluye `iccoa_support_fusion=supported`:
  el coche entró en modo fusion. Esto valida la declaración del modo 4.
- 0,25 s después de `PLAY` el coche envía los métodos 26 y 28, y sin fallos de descifrado sigue
  conectado 31 s con heartbeats, así que lee o al menos tolera las respuestas.
- Vídeo y audio salen sin error (878 unidades, 11 222 RTP, 1418 AAC).

**No observado:** métodos 4 y 30. El coche no abrió la app en esos 31 s, y por eso no hay imagen.
Falta saber si espera que se pulse algo en pantalla. Sigue sin demostrarse proyección o sesión ICCOA.

## Segunda prueba y cambios en 0.23

Diagnóstico `aionlink-diagnostics (26).txt`, 9550 bytes, SHA-256
`bd0568971a364686a6da83c43325080f108e517052fdbeafb76b31e5adb77a2c`. Mismo resultado, con los
métodos 26 y 28 repetidos 0,17 s después. Siguen sin llegar 4 ni 30. El propietario informa
de que tras "connected" no aparece nada más, salvo un aviso breve de desconexión de Bluetooth.

**Aviso de Bluetooth, hipótesis:** ya salía en la prueba de 0.21, con modo normal, así que no
lo causa el modo 4. `UCarAdapter.handleBluetoothMac` avisa `onRethinkBluetoothConnect(true)`
al recibir el método 22 si no hay registro previo, y el parámetro se llama `isCarLinkConnect`.
El coche podría soltar el Bluetooth al pasar a CarLink, como parte del flujo normal. No está
comprobado, y el firmware que decide no está disponible.

**Qué abre la imagen:** `startApp` y `startCast` son API pública que sólo llama la aplicación
del coche; nada del SDK los invoca. Dos caminos del teléfono llegan a esa aplicación:

- Método 31 con `isNeedShow` → `IPhoneDataListener.onAppStateChanged`.
- Método 7 con estado 3 → `notifyConnectStateChanged(7)`. Está en el protocolo, pero Samsung
  (`lb/g.M`) sólo envía los estados 0, 1 y 2.

**0.23**, tras el primer método 28 y una vez por sesión, envía el método 31 en el siguiente
heartbeat y el método 7 en el siguiente; cada envío queda en el registro. **Es un experimento:
ningún cliente local lo hace**, y se desconoce qué hace el firmware con esas señales.

## Resultado de 0.24 y cambios en 0.25

Diagnósticos `aionlink-diagnostics (29).txt`, `(30).txt` y `(31).txt`, hashes en
`docs/PROGRESS.md`, y una fotografía del propietario.

**Confirmado en el vehículo:**

- El coche añade "AionLink Open" a su menú y muestra el icono "AionLink". Lee y usa la lista.
- Al pulsar el icono llegan los métodos 4 (tipo 0) y 30 (tipo 3), como en `UCarAdapter.startApp`.
- Tras el método 30, CONTROL falla 63 ms y 66 ms después, en dos intentos; RTSP, MEDIA y SENSOR
  se cierran en el mismo milisegundo o casi. Sin pulsar, 0.22 seguía conectada a los 31 s.
- Sin captura preparada, 0.24 bloquea el intento y avisa (02:12:47 en el diagnóstico 29).

**Defecto propio, confirmado por análisis estático:** `UCarControlProtocol.parseAppStateChangedMessage`
exige `SEND_SYNC`, y 0.22 a 0.24 enviaban `SEND`. El constructor no fija el tipo; lo hace
`SocketChannel.sendWithAck`, por eso el error no se veía leyendo `createAppStateChangedMessage`.
El coche nunca aceptó el mensaje.

**Causa del cierre, sin demostrar.** En MiAuto 0.1.1, `SocketChannel.onMessage` captura la
excepción, la registra y no envía ACK, así que el tipo no explica el cierre en esa versión.
El coche usa el SDK `v2.0.2-202503242133`, cuyo código no se ha visto. El cierre siguió a este
mensaje en 0.23 y 0.24. Otra posibilidad: el cierre lo causa abrir el vídeo (`startCast`) y no el
mensaje, aunque en 0.23 ocurrió sin pulsar nada.

**0.25** cambia sólo ese tipo y registra el envío con sus campos y los ACK del coche.

| Resultado de 0.25 | Lectura | Siguiente paso |
|---|---|---|
| Sin cierre, con imagen | el tipo era la causa | ajustar tamaño y táctil |
| Sin cierre, sin imagen, ACK tras el envío | el coche aceptó el mensaje | revisar decodificación |
| Cierre sin ACK tras el envío | sigue rechazando o cierra antes | probar a no responder a `InvokeApp` |
| Cierre con ACK tras el envío | aceptó el mensaje y falla lo siguiente | revisar `startCast`, SPS y tamaño |

## Resultado de 0.23 y cambios en 0.24

Diagnósticos `aionlink-diagnostics (27).txt` y `(28).txt`, hashes en `docs/PROGRESS.md`. El
propietario informa de un error "no compatible o algo así", sin texto exacto.

**Confirmado en el vehículo:**

- Intento 1: `app_state_changed` sale a las 01:53:40,873 y CONTROL falla a las 01:53:40,957, 84 ms
  después; RTSP, MEDIA, SENSOR y UIBC se cierran a la vez. El método 7 no llegó a enviarse.
- 0.22, sin ese aviso, seguía conectada a los 31 s. Id 2 tampoco impidió leer lista y detalles.
- Intento 2: `START_SecurityException` al crear la captura con un permiso ya usado. Sin vídeo,
  el coche cierra a los 10 s, como en pruebas anteriores.

**Inferencia:** el aviso provocó el cierre del intento 1, por la proximidad en el tiempo. Se
desconoce por qué lo rechaza el firmware. `onAppStateChanged` entrega el tamaño de la fuente, que
podría seguir sin fijar a los 2 s, pero no está comprobado. **Tampoco consta qué cierre mostró el
error.** En la prueba de 0.19 un texto parecido coincidía con el cierre a los 10 s.

**0.24:** se retiran ambos avisos y `AppStateChanged` sólo responde a `InvokeApp`. Con AUTH
automático no se inicia la conexión sin una captura sin usar. Si un `InvokeApp` real recibe esa
respuesta y el coche cierra, el problema está en ese mensaje y no en haberlo enviado sin petición.

| Resultado de 0.24 | Lectura | Siguiente paso |
|---|---|---|
| Cierre sin envíos | el id 2 u otro factor | volver al id 0 |
| Conectado sin 4 ni 30, también tras pulsar | el coche no reacciona a su interfaz | probar sólo el método 7, categoría y lista |
| Llegan 4 y 30 sin imagen | abre la app, pero no decodifica | revisar SPS, PTS y tamaño |
| Llegan 4 y 30 y luego cierre | rechaza la respuesta | revisar `AppStateChanged` |

## Cómo interpretar la prueba de 0.23

| Resultado | Lectura | Siguiente paso |
|---|---|---|
| Aparece imagen tras los envíos | el coche abre la proyección por estas señales | ver cuál basta y ajustar tamaño y táctil |
| Llegan 4 y 30 sin imagen | el coche abre la app, pero no decodifica | revisar SPS, PTS y tamaño |
| Pantalla negra o "Loading" nuevo | `startCast` activo sin imagen | revisar decodificación |
| Corte o desconexión justo tras un envío | la señal molesta en este firmware | retirar esa señal |
| Nada cambia | el coche sólo reacciona a su interfaz o a otro mensaje | revisar categoría y lista de apps |

## Cómo interpretar la siguiente prueba

| Resultado | Lectura | Siguiente paso |
|---|---|---|
| AUTH `workModes=0x1` y el mismo error | el coche no acepta 4 de este teléfono | revisar otras comprobaciones (modelo, `productName`, versión) |
| AUTH `workModes=0x4`, M3 con `iccoa_support_fusion` | el coche entró en modo fusion | seguir los métodos 26, 28 y 30 |
| 0x4 y resultado distinto de 0 | rechazo en AUTH | volver a 257 y revisar |
| 0x4 sin métodos 26 ni 30 | el coche espera otro mensaje del teléfono | revisar `NotifyMirrorState` y firmware |
| 26, 28, 30 y 4 sin imagen | el coche pidió la app pero no decodifica | revisar SPS, PTS y el tamaño |
| Imagen deformada o con bandas | proyección funcionando | ajustar tamaño de vídeo |
| Aparece un icono "AionLink" | el coche leyó la lista | abrirlo y anotar qué ocurre |

Sigue sin demostrarse sesión ICCOA o proyección en el AION hasta observarlo en el vehículo
estacionado con el propietario presente.
