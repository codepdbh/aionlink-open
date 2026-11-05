# Simulación local de fuente codificada para un futuro puente

Este avance no recibe Android Auto y no modifica la conexión o duplicación que ya
usa el coche. `ExperimentalEncodedBridge` es un componente JVM aislado, sin conexión
de red ni selección desde la app. Acepta datos que un productor futuro ya haya
convertido a H.264 Annex-B y AAC con ADTS, y los entrega a `TsRtpStreamer`, el
multiplexor propio de MPEG-TS/RTP. El componente confía en que el productor entregue
esos formatos: no analiza ni valida el contenido de los códecs.

Cada sesión recibe un identificador opaco. Rechaza callbacks de sesiones antiguas,
audio desactivado, paquetes vacíos o demasiado grandes, timestamps no crecientes y
vídeo anterior al primer fotograma clave. El cambio de sesión crea un multiplexor
nuevo y no envía datos desde la sesión previa. El modelo es síncrono y sus límites
de cola, bloqueo por red y recuperación de errores requieren diseño antes de usarlo
en producción. El consumidor es responsable de garantizar SPS/PPS, AAC válido,
sincronía de audio y vídeo, negociación de formatos y liberación del transporte.

## Simulación ejecutada

`ExperimentalEncodedBridgeTest` introduce frames sintéticos que no son contenidos
de Android Auto y examina cabeceras RTP y paquetes TS, incluidos PID de vídeo y
audio. Prueba orden de fotogramas, timestamps, cierre, identidades y reemplazo de
sesión; no descodifica vídeo ni audio. Los tests no conectan al coche y no validan
reproducción, latencia o funcionamiento con la pantalla apagada. La ruta habitual de
MediaProjection no invoca este prototipo.

## Estado del S25 y siguientes límites

ADB reportó Android Auto 17.9.664044-release y AionLink Sonda 0.29 instalados.
No apareció un diagnóstico posterior a `aionlink-diagnostics5.txt`; la prueba de
pantalla apagada queda sin resultado. Según [Open Headunit](https://github.com/andreknieriem/open-headunit),
desde Android Auto 17.4 el servidor de desarrollo puede ser necesario para
self mode; es información de sus autores, no una prueba en este teléfono. La
[guía de Google](https://developer.android.com/training/cars/testing/dhu) documenta
el servidor para pruebas con Desktop Head Unit en un ordenador.

Para completar el objetivo todavía hay que obtener una integración autorizada con
un receptor real de Android Auto en el mismo teléfono, resolver autenticación y
licencias, adaptar realmente vídeo/audio/tacto y demostrar que funciona con pantalla
física apagada. No se ejecutaron apps ni APK de terceros, no se usaron credenciales
privadas y no se inició conexión al coche. Ver
[investigación self mode](ANDROID_AUTO_SELF_MODE_2026-11-05.md).
