# Open Headunit en una pantalla virtual de AionLink (0.36)

Objetivo experimental: actividad de Open Headunit → pantalla virtual privada de
AionLink → codificador H.264 y transporte existente al receptor ICCOA. No es
Samsung DeX. No presupone que el coche tenga su propio receptor Miracast.

## Evidencia y límites

La fuente local, identificada por su `versionName` 4.0.0-beta2, declara la
actividad `com.andrerinas.openheadunit.aap.AapProjectionActivity` exportada y
`singleTask`. Se añadió `android:allowEmbedded="true"` a esa actividad. El
archivo modificado tiene SHA-256
`d90cde7b6d7a8e6b1ecb0be6fb9d76ac204784787bb21cc77c211f195113b089`.
La carpeta recibida no contiene identidad de revisión upstream verificable. El
valor `GIT_SHA` que imprime su Gradle detecta el repositorio padre y **no**
identifica la revisión upstream. Parche reproducible:
`patches/open-headunit/allow-embedded.patch`. Fuente de terceros no incluida en
el repositorio de AionLink; conservar su licencia AGPL y avisos al distribuirla.

AionLink ofrece un selector explícito para la próxima conexión en modo coche.
Cuando el receptor solicita vídeo, crea una pantalla privada propia sin el
panel y su superficie se conecta al codificador existente. Un botón del usuario
solicita abrir la actividad exportada en ese display usando `ActivityOptions` y
comprueba `isActivityStartAllowedOnDisplay`. `allowEmbedded` y el permiso de
lanzamiento son requisitos que dependen del sistema; no garantizan que haya
imagen o que la actividad permanezca visible al bloquear el teléfono. Según
[Android](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)),
el lanzamiento a una pantalla privada está sujeto a comprobaciones del sistema.

Pendiente: comprobar self mode y que la actividad siga en el display solicitado,
ver imagen y pantalla apagada, ciclo de vida, superficie protegida, latencia y
reinicio. No existe entrada táctil hacia la actividad de otro proceso: los toques
se registran como no implementados, no como inyectados. El audio depende del
Bluetooth u otra ruta compatible y no se ha comprobado en este modo. No se han
realizado pruebas de vehículo ni ejecutado APK de terceros en esta fase. Nunca
se deben inferir estas capacidades de una compilación satisfactoria.

La aplicación original de Open Headunit de tienda puede tener otra firma. Una
compilación debug no puede actualizarla si las firmas difieren. No desinstalarla:
su configuración se perdería. La variante modificada aún necesita una vía de
instalación compatible y autorización de prueba según AGENTS.md.

## Medido en el S25 (6 de noviembre de 2026, 0.37)

Open Headunit modificada instalada por el propietario (16:19); `cmd package
resolve-activity` confirma `flags=0x80410200` en `AapProjectionActivity`
(incluye `FLAG_ALLOW_EMBEDDED`). Configuración rehecha por ADB con capturas:
modo autónomo, horizontal, 30 FPS, receptor de audio activado. El propietario
aceptó los términos y concedió la ubicación.

- `isActivityStartAllowedOnDisplay` devolvió falso desde AionLink, aunque el
  lanzamiento real a la pantalla privada sí es posible; se elimina esa
  comprobación previa y decide el propio `startActivity`.
- La actividad abierta **sin sesión** se cierra sola («opened with no session and
  none on its way»). Con el self mode enviado **antes** que la actividad y ~1 s de
  margen, Open Headunit crea su propia tarea en la pantalla del teléfono y la
  reutiliza (`singleTask`).
- Secuencia que funciona (desde ADB): `ACTION_STOP_SERVICE` → 3 s →
  self mode `no_ui=true` → 0,3 s → actividad en la pantalla virtual. Resultado:
  única ventana en la pantalla 43, superficie **1920x960**, sesión `projecting`,
  AionLink codifica 161-182 H.264 cada 5 s.
- **Pantalla apagada:** siguió ~15 s y al bloquearse el teléfono Open Headunit
  detuvo su decodificador (`Decoder stopped: activity_stopped`). Con la pantalla
  encendida pero el bloqueo visible siguió a 0 cuadros. Android detiene las
  actividades de otras apps en pantallas virtuales no confiables mientras el
  teléfono está bloqueado; el panel propio de AionLink (Presentation) no se ve
  afectado. AionLink no puede cambiarlo con APIs públicas; dependería de que la
  actividad de Open Headunit declare `showWhenLocked` o de no bloquear el teléfono.
- La prueba desde AionLink (no desde ADB) queda pendiente de repetir con el
  teléfono desbloqueado: Android limita abrir actividades desde segundo plano.

## Prueba desde la propia app (6 de noviembre de 2026, teléfono desbloqueado)

Desde AionLink (no ADB) el lanzamiento a su pantalla virtual privada fue
rechazado: `Permission Denial: starting Intent { … AapProjectionActivity } from
… org.aionlink.open.lab … with launchDisplayId=…`. Se probaron, sin éxito:

1. Actividad anfitriona propia de AionLink con `allowEmbedded=true` en la misma
   pantalla (también denegada, aunque AionLink es la dueña del display).
2. Ventana de presencia (Presentation) de AionLink antes del lanzamiento: Android
   no la cuenta como presencia.

Conclusión para Android 17 en el S25: en una pantalla virtual no confiable,
colocar **cualquier** actividad exige el permiso de sistema `ACTIVITY_EMBEDDING`
o tener ya una actividad propia allí, y una app normal no puede crear la primera.
ADB sí puede (por eso funcionó a 1920x960). El código de AionLink lo intenta y
lo informa sin efectos secundarios; la vía práctica restante sería una
herramienta que actúe con identidad de shell en el propio teléfono (por ejemplo
Shizuku por depuración inalámbrica), no evaluada ni integrada. El bloqueo del
teléfono seguiría deteniendo la actividad salvo pantalla de confianza o
`showWhenLocked`. Configuración de AionLink Sonda restaurada a modo espejo.

## Puente de superficie (0.39, 6 de noviembre de 2026)

El propietario sustituyó la pantalla virtual por un puente: `HeadunitSurfaceBridge`
(AionLink) se enlaza al `AionLinkSurfaceService` de su Open Headunit, protegido
por un permiso de firma (ambas apps firmadas con la misma clave `adb60f`), y le
entrega la superficie de entrada del codificador. No usa actividades ni
pantallas virtuales, así que no le afectan las restricciones anteriores.

Prueba local en el S25 (Open Headunit con el parche instalado 17:25, permiso
`AIONLINK_SURFACE` concedido): «Superficie aceptada», self mode, sesión
`projecting`, Android Auto negocia 1920x1080 y configura el canal VIDEO
(`STATUS_READY`), pero **no envía cuadros** (H.264 = 0) y a los 15 s Open
Headunit corta con `WiFi read timeout (15000ms) - connection lost`.

Causa: Android Auto espera que el receptor le conceda el foco de vídeo
(`VideoFocusEvent(gain = true)`). En Open Headunit lo hace la actividad de
proyección al hacerse visible; en modo puente no hay actividad. Pendiente en el
lado de Open Headunit (responsabilidad del propietario): tras `startReading`,
llamar a `commManager.retakeVideoFocusForKeyframe()` y repetirlo si no llega
imagen.

Contrato de táctil añadido en AionLink: mensaje 3 con `action` (código
MotionEvent), `ids`, `xs`, `ys` (`IntArray`, un valor por dedo) y `width`/
`height` del cuadro codificado. Open Headunit debe traducirlo a su evento táctil
de Android Auto. Sin ese manejador AionLink informa `TACTIL_PUENTE_SIN_CONEXION`
o el toque se ignora en el receptor.

## Puente funcionando con pantalla apagada y bloqueada (6 de noviembre de 2026)

El propietario añadió a `AionLinkSurfaceService` el foco de vídeo en
`TransportStarted` (con reintentos a 1 y 3 s) y el manejo del mensaje táctil 3
(`AionLinkTouchMapper`). Open Headunit recompilada (`assembleGithubDebug`,
17:41) e instalada como actualización.

1. Con la superficie del codificador entregada directamente, Open Headunit
   descodificaba a 29-30 fps pero el codificador de AionLink no producía nada:
   1920x1080 del decodificador sobre una superficie de codificador 1920x960.
2. AionLink añade `SurfaceRelay`: SurfaceTexture propia → OpenGL ES 2 →
   superficie del codificador escalada a 1920x960 con marcas de tiempo
   monótonas. Resultado: relé 150 cuadros/5 s, H.264 y TS/RTP continuos.
3. **Pantalla apagada y teléfono bloqueado** (`isKeyguardShowing=true`,
   interactivo=false), 90 s: Open Headunit 150 cuadros/5 s sin pérdidas, relé
   150/5 s, AionLink 5089 unidades H.264 y 54 292 datagramas en total.
4. El codificador añadía repeticiones (~41 fps); en modo relé se desactiva
   `KEY_REPEAT_PREVIOUS_FRAME_AFTER` para quedarse en los 30 fps del coche
   (instalado, pendiente de medir).

Pendiente: prueba en el AION (imagen, táctil por el puente, audio), latencia y
consumo. El audio de Android Auto lo reproduce Open Headunit en el teléfono;
llega al coche por Bluetooth o por la captura opcional de AionLink.
