# Plan: Android Auto o interfaz propia, conservando la duplicación

Fecha: 5 de noviembre de 2026. Documento de planificación; no anuncia funciones terminadas.

## Objetivo y condiciones

En el mismo S25 y sin adaptador externo, ofrecer dos opciones: **Duplicar pantalla**,
conservar la ruta ICCOA actual, y **Modo coche**, que mantenga una interfaz útil en
el AION con la pantalla física del teléfono realmente apagada. La preferencia es
Android Auto real; si no existe un camino público y autorizado viable, el modo coche
será una interfaz propia, identificada como tal. Brillo mínimo no satisface el
requisito de pantalla apagada. La prueba de bloqueo es con el propietario presente,
vehículo estacionado y sin modificar el sistema del coche.

Base de referencia: 0.25 mostró imagen según el propietario; 0.26 mejoró la fluidez.
El diagnóstico privado `diagnostics5.txt` de 0.28 (11 503 bytes, SHA-256
`31203205e5d15148aa9ea1ae30395481977e3113a6f4cfd40ce0001e7a62e14b`) registra
PLAYING 1280x720p30, 1740 unidades de vídeo, 2709 tramas AAC, 35 eventos táctiles
aplicados de 35 y lanzador abierto, con la conexión aún activa al exportar. Estas
son mediciones del lado del teléfono, no una evaluación independiente de la imagen
o de la audibilidad en el coche. Snapshot 2026-11-04 03:10:23 UTC, 3 de noviembre
23:10 hora de La Paz. La prueba 0.26 documentó que el espejo se detenía al bloquear.

## Viabilidad de Android Auto real

Android Auto proyectado necesita un receptor compatible. La [documentación oficial
del Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu)
describe un receptor de **desarrollo en el ordenador**, que se conecta a Android
Auto en el teléfono mediante USB o un túnel ADB. Es útil como referencia para
una simulación controlada; no demuestra que ese receptor pueda funcionar dentro
de una app Android normal en el mismo teléfono, ni autoriza redistribuirlo o usar
sus interfaces internas como API de producción. La [Car App Library](https://developer.android.com/training/cars/apps)
permite crear apps para un host existente, no proporciona un receptor general.

**Bloqueante GO/NO-GO AA:** encontrar y documentar una interfaz de integración de
receptor disponible y autorizada, ejecutable sin root ni hardware externo en el
mismo S25, que entregue vídeo, audio y eventos bidireccionales a una app propia.
Open Headunit afirma soportar el mismo teléfono mediante el servidor de desarrollo,
pero no hemos comprobado su integración autorizada ni la pantalla apagada. Detalles en
[investigación de self mode](../protocol-research/ANDROID_AUTO_SELF_MODE_2026-11-05.md).
Investigar documentación pública
y licencias, sin ejecutar APK de terceros, obtener claves privadas, copiar
bibliotecas propietarias, suplantar identidades ni eludir autenticación. Si no se
demuestra el acceso legítimo y al mismo dispositivo, NO-GO para prometer Android
Auto real. El DHU en PC puede apoyar una prueba manual independiente bajo sus
condiciones de uso, pero un puente PC→coche no cumple el requisito final.

## Arquitectura propuesta

```text
                 BLE + P2P + AUTH + CONTROL ICCOA existentes
                                      │
                      RTSP → H.264/AAC → TS/RTP → coche
                                      ↑
       ┌──────────────────────────────┼───────────────────────────┐
       │                              │                           │
  Duplicar pantalla         Interfaz propia             Android Auto real
  MediaProjection           Surface propia              Adaptador candidato
  ruta existente            y motor propio              SOLO si pasa GO/NO-GO
       ↑                              ↑                           ↑
       └────────────── eventos de toque / teclas ────────────────┘
```

Diseñar una interfaz de fuente de vídeo, audio y entrada, con estados STARTING,
READY, ACTIVE, STOPPING, STOPPED y ERROR, propiedad explícita de superficies,
cancelación y liberación. La fuente de duplicación envuelve la implementación
existente, sin cambiar formato, FPS, negociación, permisos ni flujo de conexión
como parte de la primera fase. Una única fuente activa y un único propietario de
MediaCodec, audio y RTSP por sesión; el cambio de modo se inicia detenido y puede
requerir una nueva autorización o reconexión. Si falla un modo, detenerlo limpiamente
y permitir elegir duplicación, sin activar captura ni reconectar sin consentimiento.
El selector debe dejar claro qué modo es Android Auto real, cuál es interfaz propia,
y si la pantalla física tiene que estar encendida.

Los puntos actuales a encapsular son `ProjectionSession`, `ProjectionService`,
`ScreenEncoder`, `AudioCapture`, `WfdSource` y `TsRtpStreamer`. Táctil y teclas
ya transitan por UIBC y accesibilidad: enrutar eventos a la fuente activa, con
coordenadas normalizadas, sin inyectar gestos al teléfono cuando la interfaz propia
los consume. Revisar autenticación y origen de los sockets UIBC/RTSP antes de
ampliar capacidades; restringir al receptor esperado no sustituye autenticación.

## Fases y criterios de salida

1. **Línea base y seguridad.** Fijar el comportamiento de 0.28 con pruebas de
   regresión sintéticas del flujo AUTH→CONTROL→RTSP→RTP, audio y tacto; conservar
   la configuración y selección de apps. Identificar peer de cada listener y
   documentar garantías y límites de autenticación. Añadir controles de origen
   compatibles con la topología real. Aceptación: ninguna caída de las pruebas
   existentes, modo espejo disponible por defecto, sin tráfico nuevo por selección
   de modo, y confirmación posterior del propietario en el vehículo.
2. **Prueba de pantalla físicamente apagada sin coche.** Extender la sonda local
   `OwnDisplayProbe` para crear contenido gráfico propio en Surface/pantalla
   virtual privada y contar cuadros producidos y codificados con pantalla encendida,
   apagada y bloqueada; probar al menos 10 minutos apagada, con interrupciones,
   reconexión y medición de batería/temperatura cuando sea posible. Distinguir
   pantalla apagada de bloqueo seguro, sueño del proceso y brillo mínimo. Registrar
   si la sesión y red sobreviven; no asumir que un wake lock garantice el resultado.
   Aceptación: cuadros nuevos verificables y tiempo monotónico continuo, recursos
   liberados al detener y sin despertar la pantalla. Esto solo prueba contenido propio.
3. **Fuente propia y simulación.** Implementar una fuente propia de patrón animado,
   después interfaz mínima de reloj, estado, medios y navegación accesibles mediante
   APIs públicas. Encaminamiento táctil directo al motor propio, no al display
   físico; validar orientación y rotación. Usar superficie compatible con MediaCodec
   y canal ICCOA existente. No asumir que Google Maps u otras apps se pueden ejecutar
   en un display privado o con teléfono bloqueado: probar disponibilidad, permisos,
   licencia de SDK/datos y experiencia individualmente. La captura de audio de otras
   apps puede estar prohibida, y las indicaciones de navegación no entran en la
   captura general de reproducción. Aceptación: salida reconocible del simulador,
   toques verificados y diez minutos con pantalla física apagada, antes de coche.
4. **Experimento específico AA real.** Solo si la fase de viabilidad encuentra API
   legítima: prototipo aislado de receptor con vídeo, audio, tacto, foco y ciclo de
   vida; demostrar primero que Android Auto real se inicia en el mismo S25 y que
   funciona con pantalla apagada. No registrar material sensible. Después conectar
   al contrato de fuentes y validar transporte, licencia y privacidad. Aceptación:
   teléfono único, sin PC/adaptador, interacción bidireccional, audio y vídeo de
   Android Auto reales durante 10 minutos con pantalla física apagada. Si falla
   cualquiera, el modo no se presenta como funcional ni como Android Auto.
5. **Validación con AION.** En coche estacionado: 10 ciclos iniciar/parar y 10
   alternancias controladas, prueba de reconexión, 30 minutos con pantalla apagada,
   orientación, audio, enfoque, pérdida de red y regreso al espejo. Comparar
   latencia extremo a extremo y pérdida de cuadros con 0.28, sin fijar cifras
   arbitrarias antes de medir. Registrar versión, consentimientos, fallos, métricas
   y observaciones del propietario. Continuidad de audio, entrada y navegación deben
   confirmarse por separado. Cualquier cambio que rompa espejo se revierte o aísla.

## Simulación reproducible

- **JVM sin teléfono:** receptor ICCOA sintético local con frames generados por el
  proyecto, así como fake de fuente (patrón con contador/timestamp), audio y toques.
  Comprobar arranque, orden y continuidad de timestamps, límites de cola, cortes,
  cambios de modo, fugas, rechazos, RTSP y transporte RTP; no se afirman conexiones
  auténticas al coche ni a Android Auto. Probar peer incorrecto y desconexiones.
- **Android en S25, sin coche:** probar encoder real y Surface propia, registro
  de estado físico de pantalla y bloqueo, tiempo de vídeo y consumo; usar datos
  sintéticos, sin controlar apps del propietario. Instrumentación de accesibilidad
  puede dejar el servicio caído: verificar y restaurar su estado con cuidado, y no
  ejecutarla inmediatamente antes de una prueba de vehículo.
- **DHU oficial en PC, opcional y manual:** verificar un teléfono real con Android
  Auto y su interfaz en el entorno de desarrollo oficial; anotar versiones y
  condiciones. No confundir esta referencia con el puente en el propio S25 ni
  capturar o redistribuir sus secretos o componentes. No ejecutar herramientas que
  interrumpan la sesión del propietario; la guía advierte que el modo USB puede
  interferir con ADB en Windows.
- **Vehículo:** solo después de los criterios anteriores, propietario presente y
  coche estacionado. Exportar diagnóstico anonimizado; no registrar imágenes,
  audio, PIN, direcciones, ubicaciones ni credenciales.

## Riesgos y decisiones abiertas

La [API de pantallas virtuales](https://developer.android.com/reference/android/hardware/display/DisplayManager)
restringe qué apps pueden usar una pantalla privada; contenido protegido puede
aparecer negro. [MediaProjection](https://developer.android.com/media/grow/media-projection)
exige consentimiento por sesión y Android 14+ restringe reutilizar sus tokens.
La [captura de audio](https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration)
excluye usos y apps que no permitan captura. La interfaz propia, navegación y
reproducción de aplicaciones ajenas tienen viabilidad y permisos por verificar;
no equivalen automáticamente a Google Maps, Spotify o Android Auto. Video y
controles complejos requieren una política explícita de uso estacionado; sin una
señal de estado fiable no debe inferirse que el vehículo está detenido.

**Resultado actual:** plan y análisis, no implementación ni simulación ejecutada.
