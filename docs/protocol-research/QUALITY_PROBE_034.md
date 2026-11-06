# 0.34: nitidez de imagen y sonda en servicio en primer plano

Análisis del 6 de noviembre de 2026. Fuentes: diagnóstico privado 0.28
(`private/vehicle-tests-2026-11-05/diagnostics5.txt`, SHA-256 `31203205…`),
cadenas imprimibles de `libheycastcore.so` de Samsung Auto 3.1.04.10 (script
propio, sin copiar la biblioteca) y el diario de la sonda leído por ADB.

## Por qué la imagen se veía borrosa

El coche pide claramente otra resolución:

| Fuente (diagnóstico 0.28) | Valor |
|---|---|
| Configuración UCar del coche | pantalla 1920x1080, **vídeo 1920x960**, 30 fps, dpi 160 |
| `wfd_car_display_mode` | `mode=0;display=1920:960;dpi=160;fps=30` |
| `wfd_video_formats` | nativo CEA 720p30, todas las máscaras CEA/VESA/HH |
| Lo que enviaba AionLink | **1280x720p30, 6 Mbps** |

Con el lanzador en horizontal (2340x1080), a 1280x720 la imagen útil era
~1280x591 y el coche la ampliaba 1,5 veces, además de la diferencia de
proporción 16:9 frente a 2:1. A 1920x960 la imagen útil es ~1920x886.

Evidencia de cómo lo hace Samsung (cadenas de la biblioteca nativa):
`mode=%d;display=%d:%d;dpi=%d;fps=%d` (formato con el que lee el valor),
`car display mode = %d,w=%d,h=%d,dpi=%d,fps=%d`, `change video_bitrate to %d
as 1080P` y `setting video bitrate to %d bps`. Inferencia: Samsung codifica al
tamaño de pantalla que declara el coche y sube el bitrate para 1080p. No se ha
visto el código que lo hace; es una inferencia a partir de esas cadenas.

## Cambios en 0.34

- `WfdFormats.parseCarDisplayMode` lee el modo de pantalla del coche;
  `encodeTarget` elige el tamaño de codificación: en **ALTA** (por defecto) el
  de la pantalla del coche, redondeado a múltiplos de 16; en **COMPATIBLE** el
  1280x720 que funcionó en 0.25–0.28. Los fps nunca superan los negociados.
- Bitrate proporcional (~0,2 bits/píxel/cuadro, entre 4 y 16 Mbps):
  1280x720p30 → 5,5 Mbps; **1920x960p30 → 11 Mbps**.
- Nivel H.264 calculado (Tabla A-1): 1920x960p30 → nivel 4.0; antes 3.1 fijo.
- Fotograma clave cada 2 s (antes 1 s): el enlace es TCP y el coche puede pedir
  `wfd_idr_request`, así que los bits van a detalle.
- Si el codificador del teléfono no admite el tamaño del coche, vuelve a CEA
  (`origen=CEA_POR_CODIFICADOR`). El S25 admite 1920x960p30 (prueba instrumentada).
- El M4 **no cambia** (sigue declarando CEA 720p30): solo cambia el tamaño del
  flujo H.264. El receptor configura su decodificador con su propia pantalla y
  el SPS. **Sin verificar en el coche**; si el coche rechaza o deforma la imagen,
  el botón «Calidad de imagen» vuelve a COMPATIBLE.
- El táctil usa el tamaño codificado; en ALTA coincide con la pantalla del coche.

## Sonda en servicio en primer plano

El diario que había en el teléfono (5 de noviembre, 19:05–19:06 en La Paz)
recogía 66 s con la pantalla **encendida** en todas las muestras: 1983 cuadros
H.264 (30 fps) y 0 paquetes TS/RTP inválidos. No había ninguna prueba con la
pantalla apagada.

La sonda corría en la Activity, sin servicio, así que con la pantalla apagada
Android podía congelarla y un fallo no habría distinguido «la pantalla virtual se
detiene» de «Android durmió la app». En 0.34 `VirtualProbeService` la ejecuta en
un servicio en primer plano de tipo `connectedDevice`, el que usaría un futuro
modo coche, durante un máximo de 12 minutos, a **1920x960**. Las muestras
añaden batería, temperatura y si el servicio sigue en primer plano. La prueba
sobrevive al cierre de la Activity; el diario se incluye en la exportación.

## Firma de la app instalada

La 0.28 instalada está firmada con una clave de depuración distinta
(certificado SHA-256 `e1092cde…`) de la de este PC (`53f31cba…`). Por eso desde
la 0.29 ninguna APK puede actualizarla (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
No se encontró esa clave en el perfil del propietario; no se revisaron perfiles
de otros usuarios. `app/build.gradle.kts` acepta ahora un `android/keystore.properties`
local (excluido de Git) para firmar siempre con la misma clave en cualquier PC.

## Verificación

- JVM: 111 pruebas (23 de proyección, incluidas lectura del modo de pantalla,
  elección de tamaño/bitrate y redondeo).
- S25, paquete Sonda: `VirtualProbeServiceTest` (servicio en primer plano,
  1920x960, >100 H.264 por intervalo de 5 s, diario, parada) y
  `VirtualDisplayVideoProbeTest`: 2 de 2 correctas, con pantalla encendida.
- Se encontró y corrigió una carrera en `WfdSource.close()` que hacía fallar
  `tcpUnicastConnectsToSinkListenerAndFramesPackets` 2 de 4 veces; tras el
  arreglo, 6 de 6.

Pendiente: prueba de 10 minutos con pantalla apagada (propietario) e imagen
1920x960 en el AION.

## Resultado de la prueba con pantalla apagada (6 de noviembre de 2026)

Con autorización del propietario se inició la sonda en AionLink Sonda 0.34 desde
su botón (vía ADB `input tap`), se apagó la pantalla con `KEYCODE_SLEEP`, se
esperaron 600 s y se leyó el diario por `run-as`. Diario privado:
`private/vehicle-tests-2026-11-06/screen-off-probe-journal.txt`.

| Estado al final del intervalo | Muestras | Tiempo | Dibujos/s | H.264/s | TS/RTP/s |
|---|---|---|---|---|---|
| ON (antes de apagar) | 1 | 5 s | 119 | 29,7 | 29,7 |
| OFF (transición) | 1 | 5 s | 113 | 29,8 | 29,8 |
| **DOZE, bloqueado, no interactivo** | **120** | **604 s** | **30,0** | **35,3** | **35,3** |

Sin intervalos sin vídeo: edad máxima del último H.264 56 ms, 0 paquetes
inválidos, 0 descartados, el servicio siguió en primer plano en las 122
muestras y la pantalla no volvió a encenderse. El teléfono estaba conectado por
USB (batería 74 % → 75 %, 28,1 °C), así que el consumo no está medido.

**Conclusión:** con la pantalla física apagada y bloqueada, una pantalla virtual
propia en un servicio en primer plano sigue dibujando (~30/s) y el codificador
sigue produciendo H.264 → TS/RTP durante al menos 10 minutos. Esto habilita un
Modo coche con interfaz propia. No prueba red ICCOA, coche, táctil ni audio con
pantalla apagada, ni el consumo sin cargador.

Observación: con pantalla apagada el codificador entregó 35 cuadros/s, por
encima de los 30 configurados (posiblemente por la repetición de cuadros). Antes
de enviarlo al coche hay que limitarlo a la tasa negociada para no reproducir el
retraso documentado en LAG_SAVINGS_026.
