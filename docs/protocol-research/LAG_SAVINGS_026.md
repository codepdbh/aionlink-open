# 0.26: lag de la proyección, ahorro y sonda de pantalla propia

Análisis del 3 de noviembre de 2026. El propietario informa de que en 0.25 proyección y táctil
funcionan, con algo de lag y la pantalla del teléfono encendida. No se ejecutaron APK de terceros.

## Causa probable del lag

**Observado en el vehículo:** el diagnóstico `aionlink-diagnostics2.txt` registra 3875 unidades de
vídeo en unos 59 s, unas 66 por segundo, con 30 fps negociados.

**Análisis estático de MiAuto 0.1.1**, el mismo SDK de sink, aunque el coche usa v2.0.2:

- `VideoPlayerV2` descodifica en orden, muestra cada cuadro al terminarlo y sugiere `operating-rate`
  igual a fps + 15, es decir 45.
- `VideoQueueManager` guarda hasta 512 cuadros y sólo vacía la cola en un fotograma clave con 50 o
  más en espera.

**Inferencia:** si llegan más cuadros de los que descodifica, la cola crece hasta unos 50 antes de
vaciarse. A 45 fps son más de 1 s de retraso. No se ha medido el retraso en el coche ni la velocidad
real de su decodificador.

## Cambios en 0.26

- `KEY_MAX_FPS_TO_ENCODER` limita el codificador a los fps negociados; `KEY_PRIORITY` 0 y
  `KEY_LATENCY` 1 piden baja latencia.
- **Sin cambios:** 1280x720, 6 Mbps, fotograma clave cada segundo, RTSP, TS, audio y táctil.
- El diagnóstico añade cuadros/s y escrituras TCP de 50 ms o más.
- Botón opcional de brillo mínimo, desactivado por defecto. Requiere el permiso de ajustes del
  sistema. Guarda el brillo antes de cambiarlo y lo restaura al terminar, o al reabrir si la app murió.
- Cada sesión registra consumo de batería y encendido o apagado de la pantalla con las unidades de
  vídeo. El contador de Android avanza en pasos gruesos y no sirve si el coche carga el teléfono.
- `OwnDisplayProbe` dibuja en una pantalla virtual privada y cuenta cuadros con el estado de bloqueo.
  No envía nada al coche; sólo responde si una pantalla propia sigue viva con el teléfono bloqueado.

## Medido en el S25 sin coche

`StreamingSavingsTest` sobre el codificador real, con una vista animada:

| Prueba | Resultado |
|---|---|
| Sin límite | 90,99 cuadros/s |
| Con `KEY_MAX_FPS_TO_ENCODER` | 30,00 cuadros/s |
| Sonda, teléfono desbloqueado | 479 cuadros en 5 s |
| Brillo mínimo, final normal y proceso reiniciado | restaurado |

La tasa sin límite corresponde a la animación de prueba, no a la pantalla duplicada de una sesión.

## Sin verificar en el vehículo

| Pregunta | Dónde verlo |
|---|---|
| ¿Bajó el lag? | Propietario; `cuadros/s` cerca de 30; escrituras lentas |
| ¿Cuánto ahorra el brillo mínimo? | `Batería de la sesión`, con y sin él, sin cargar |
| ¿Sigue el vídeo al bloquear? | Unidades de vídeo tras `Pantalla del teléfono apagada` |
| ¿Es viable una pantalla propia? | Líneas de la sonda con `bloqueado=true` |

## Imagen

No se cambia el tamaño. El coche declara `display=1920:960` y se envía 1280x720, pero no consta
deformación ni bandas, y el propietario no ha enviado foto. Cambiarlo pondría en riesgo una
proyección que funciona, así que queda pendiente de esa evidencia.

## Resultado de 0.26 en el vehículo

Diagnósticos `aionlink-diagnostics3.txt` y `aionlink-diagnostics4.txt`, hashes en `docs/PROGRESS.md`.

**Lag:** el propietario informa de que mejoró la transmisión. El diagnóstico registra 31 cuadros/s
y 0 escrituras lentas, frente a unos 66 en 0.25. Esto apoya la causa propuesta.

**Táctil, defecto causado por la prueba:** el coche envió 159 eventos UIBC y se aplicaron 0, con
`SIN_ACCESIBILIDAD`. `dumpsys accessibility` mostraba `TouchInjectionService` en `Crashed services` y
`accessibility_enabled=0`. **Inferencia:** `am instrument` de 0.26 mató el proceso y Android dejó el
servicio como caído. Instalar 0.27 lo vació y el servicio volvió a `Bound services`. No se ha
comprobado de nuevo el táctil en el coche. Las 159 entradas prueban además que la ruta UIBC funciona.
**No conviene ejecutar pruebas instrumentadas contra la app justo antes de una prueba en el coche.**

**Bloqueo:** al apagar la pantalla, las unidades de vídeo pasan de 615 a 616 en 2,6 s. **La duplicación
se detiene con el teléfono bloqueado**, como informa el propietario. MediaProjection no sirve para eso.

**Sonda:** el bloqueo duró 2,6 s, muy poco para concluir. Por cálculo, la vista dibujó unos 16 cuadros
bloqueada, frente a unos 118 por segundo encendida, lo que sugiere que se frena. Es una estimación.

**Batería:** 29 mAh en 57 s y otros 29 en 85 s reflejan el paso grueso del contador, no un consumo real.

## Cambios en 0.27

- El botón de accesibilidad indica si el táctil está inactivo, y el registro avisa si llegan toques
  sin servicio activo.
- La sonda añade un hilo que dibuja directamente en una superficie, con `PARTIAL_WAKE_LOCK`, sin depender
  de la pantalla. Si sigue con el teléfono bloqueado, una pantalla propia podría alimentar así al
  codificador. Falta comprobar que Wi-Fi Direct y el codificador sigan activos. Sin ejecutar todavía.

## Lanzador en 0.28

El propietario pide una app propia donde colocar Google Maps, MX Player, la galería y otras.

`CarLauncherActivity` es una actividad normal, en horizontal, con las apps elegidas. Al abrirla, las
apps se ejecutan en la pantalla del teléfono y llegan al coche por la misma pantalla compartida. **Por
eso el teléfono debe seguir encendido y desbloqueado.** Ejecutarlas en una pantalla virtual aparte, con
el teléfono bloqueado, requiere permisos de sistema como los que declara Samsung Auto; no se ha probado
en este teléfono. La elección queda en el teléfono: no se envía al coche, y el diagnóstico sólo cuenta cuántas son.

- El primer método 4 de la sesión abre el lanzador mediante el servicio de accesibilidad. Se puede desactivar.
- `TYPE_ACCESSIBILITY_OVERLAY` muestra atrás e inicio, porque el coche no tiene esos botones.
- `ACCELEROMETER_ROTATION` y `USER_ROTATION` fuerzan horizontal con el permiso de ajustes. Las apps que
  fijan vertical pueden no obedecer.

**Sin verificar en el vehículo:** apertura al pulsar en el coche, botones superpuestos con toques del
coche, horizontal forzado y mapeo táctil en horizontal. **Comprobado en el teléfono sin coche:** abre y
entra en edición sin cierres inesperados. No se vio una captura; la disposición sigue sin revisar.

## Sin implementar

Android Auto no se implementa: requiere credenciales privadas, también en open-headunit, que incluye
`res/raw/cert` y `res/raw/privkey`. Una pantalla propia sólo mostraría contenido de AionLink.
