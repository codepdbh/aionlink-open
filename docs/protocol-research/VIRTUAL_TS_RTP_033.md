# Pantalla virtual propia a TS/RTP: 0.33

## Objetivo y alcance

El propietario solicita el escritorio real Samsung DeX mediante AionLink Open en
el AION, conservando el espejo y con pantalla del teléfono apagada. Esta versión
**no proporciona DeX ni transmite al coche**. Samsung documenta Wireless DeX con
un receptor compatible con [Miracast](https://www.samsung.com/us/support/answer/ANS10001955/).
En la revisión no se halló una API pública que garantice activar y entregar el
escritorio DeX a la pantalla virtual privada de una app; la
[documentación Android](https://developer.android.com/reference/android/hardware/display/DisplayManager)
restringe qué procesos pueden mostrar contenido en una pantalla privada. No se
debe inferir imposibilidad absoluta ni utilizar permisos privilegiados, eludir
restricciones o modificar el coche. El propietario indicó que el AION no tenía
una función independiente de screen mirroring; la proyección observada fue AionLink.

## Implementación

La sonda local de 0.33 conecta `VirtualDisplayVideoProbe` a
`ExperimentalEncodedBridge` y `TsRtpStreamer`: una presentación privada con patrón
propio genera vídeo H.264 y el multiplexor crea datagramas RTP con MPEG-TS en un
consumidor **en memoria**, sin direcciones o sockets. Se conserva la ruta habitual
de MediaProjection sin cambios y no se comparte contenido real del teléfono. El
diagnóstico añade cuadros aceptados por el multiplexor, paquetes, rechazos y
comprobaciones básicas de cabecera RTP y sincronización TS; se guarda en el diario
privado de la sonda. La señal de dibujos sigue separada del vídeo, que puede repetir
el último fotograma. Las muestras registran el estado de pantalla al final del
intervalo, no de forma continua.

Se propaga la configuración H.264 que devuelve el encoder a los fotogramas clave
cuando hace falta, igual que la ruta propia de vídeo existente. Los frames aceptados
son los del patrón sintético; la sonda no descodifica el contenido ni garantiza que
el AION lo reproduzca. El consumidor en memoria no mide latencia ni congestión de
red, RTSP, audio, entrada o compatibilidad de DeX. Todavía se necesita una fuente
legítima y demostrada de escritorio DeX y un adaptador de entrada para el coche.

## Pruebas

Pruebas JVM y Android del proyecto; en el S25 una prueba instrumentada de seis
segundos confirmó múltiples dibujos, frames H.264 y frames/paquetes TS/RTP de la
fuente propia, con cero paquetes malformados según las comprobaciones básicas.
La pantalla física apagada no se probó: el propietario hará esa prueba más tarde.
No se concedió MediaProjection ni se ejecutó Samsung DeX ni APK de terceros, y no
se inició ningún vehículo. El paquete original y el servicio táctil se conservaron.

Para medir pantalla apagada, abrir **AionLink Sonda**, elegir **Probar vídeo de
pantalla virtual propia**, apagar la pantalla unos diez minutos, regresar a la app,
detener la prueba si continúa y guardar el diagnóstico. Este experimento no debe
describirse como una conexión DeX o una validación en el coche.
