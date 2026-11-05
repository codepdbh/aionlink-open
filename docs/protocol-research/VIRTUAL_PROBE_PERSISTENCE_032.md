# Prueba de pantalla virtual 0.32

La sonda de pantalla virtual propia de 0.31 sigue sin mostrar DeX ni enviar
vídeo al coche. Esta versión guarda la última prueba en almacenamiento privado
de la propia app de laboratorio; no se guardan píxeles, contenido de aplicaciones,
credenciales ni direcciones. Se conserva un máximo de 150 entradas sintéticas.
La exportación del diagnóstico incluye este historial aunque se haya creado un
nuevo ViewModel o proceso, siempre que Android haya completado la escritura.
Las escrituras usan `SharedPreferences.apply`: un cierre brusco puede perder
las últimas muestras pendientes. Al iniciar otra prueba virtual se reemplaza
el historial anterior.

Si no se guardó un cierre normal, el estado se muestra como
`EN_CURSO_O_INTERRUMPIDA`: no distingue por sí solo una prueba aún activa de un
proceso terminado. Cada muestra incluye el tiempo monotónico realmente transcurrido,
cuántas veces dibujó la presentación, cuántos buffers H.264 se produjeron y el
estado `ON`, `OFF`, `DOZE` u otro de la pantalla principal, interactividad y
bloqueo al final del intervalo. La salida H.264 podría repetir un cuadro anterior;
por ello debe leerse junto con el contador de dibujos. El estado al final de un
intervalo no demuestra qué ocurrió durante cada instante anterior.

La app impide iniciar las acciones de captura de pantalla mientras corre una sonda.
Esto no sustituye un servicio foreground: Android aún podría detener el proceso
o restringir la ejecución en segundo plano. Una prueba instrumentada en el S25
verificó muestras y escritura de datos sintéticos al archivo privado; no simuló
una muerte real del proceso, pantalla apagada, ni proyección al coche.

Para probar después: en **AionLink Sonda**, pulsar «Probar vídeo de pantalla
virtual propia», apagar la pantalla unos diez minutos, volver a abrir la misma app,
detener la prueba si sigue activa y guardar el diagnóstico. Si hubo interrupción,
guardar igualmente: el historial disponible permitirá ver hasta dónde llegó.
No indica que DeX, Android Auto o el AION ya funcionen con la pantalla apagada.
