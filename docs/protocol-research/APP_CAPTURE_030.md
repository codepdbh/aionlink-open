# Compartir una app con AionLink 0.30

Android 14 y posteriores permiten elegir una ventana o toda la pantalla en el
selector de [MediaProjection](https://developer.android.com/media/grow/media-projection).
La opción nueva **Compartir una aplicación (experimental)** explica esta elección
y abre el mismo selector del sistema que ya usaba el espejo. La elección real y el
consentimiento los controla Android, no AionLink. La función no inicia ni instala
Open Headunit y no verifica qué aplicación eligió el usuario.

Una ventana que muestre Android Auto podría ser capturada si Android y la app lo
permiten, y luego usar la misma fuente ICCOA de vídeo existente. Esto es una
hipótesis sin verificación en este S25 o el AION. El usuario puede elegir pantalla
completa también; no se impone un tipo de selección. Las ventanas protegidas pueden
estar en negro. Un lanzador automático de AionLink puede cubrir la app elegida.

La captura de una app puede tener proporciones distintas de la pantalla del S25.
La versión 0.30 agrega al diagnóstico tamaño y visibilidad comunicados por Android,
sin nombre ni contenido de la app. No adapta automáticamente el tamaño de vídeo ni
el mapeo de toques a esa ventana, por lo que posición, táctil y formato no están
verificados. La selección de otra sesión no deja que una notificación tardía de la
captura anterior borre la referencia a la nueva. Si la captura termina, el estado
deja de indicar READY.

**Pantalla apagada:** la documentación de Android indica que el bloqueo puede detener
MediaProjection; la prueba anterior 0.26 observó que el espejo se detuvo. Nada en
esta versión demuestra vídeo con pantalla apagada ni lo promete. La interfaz propia
de pantalla apagada y el receptor Android Auto real siguen pendientes. La
[captura de audio](https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration)
está limitada por el tipo de audio y la política de la app emisora; el flujo actual
no garantiza indicaciones de navegación, llamadas o voz.

Verificación de 0.30: diálogo y cancelación con UI test aislado en S25, pruebas JVM
y build. No se concedió MediaProjection durante las pruebas automatizadas, no se
ejecutó Open Headunit ni se probó la función en el coche. Solo se instaló el paquete
de laboratorio; el paquete original y sus datos se conservaron por firma distinta.
