# Samsung DeX y prueba de pantalla virtual 0.31

El propietario quiere ver el escritorio real Samsung DeX en el AION sin perder
duplicación de pantalla y con la pantalla física del S25 apagada. En 0.31 no se
ha implementado ni mostrado Samsung DeX en el coche.

## Investigación

Samsung confirma [Wireless DeX en la serie S25](https://www.samsung.com/us/support/answer/ANS10001955/)
con un receptor compatible con Miracast, y [advierte](https://www.samsung.com/us/support/troubleshoot/TSG10001535/)
que el táctil del monitor no está soportado en esa ruta. No sabemos si la app
de duplicación del AION acepta DeX/Miracast o únicamente otro protocolo. El S25
no estaba conectado al coche durante esta revisión, por lo que una lista vacía
de pantallas no demuestra incompatibilidad.

Android permite crear una [pantalla virtual privada](https://developer.android.com/reference/android/hardware/display/DisplayManager)
para contenido propio. Esa API no garantiza que Samsung active DeX en ella y una
pantalla privada restringe qué procesos pueden mostrar ventanas. Una pantalla
virtual pública propia tampoco demuestra acceso al escritorio DeX; la captura
protegida y el autoespejo exigen permisos o consentimiento específicos. No se ha
localizado una API pública que inicie y entregue el escritorio DeX a una app normal
del S25 sin pantalla externa. La coexistencia de DeX con la red ICCOA, el audio,
la entrada táctil y la pantalla física apagada siguen sin verificar. Se respetan
los permisos del sistema y no se modifican el firmware ni el coche.

## Trabajo implementado

`VirtualDisplayVideoProbe` crea un display **privado de AionLink** con un patrón
de prueba propio y conecta su Surface a MediaCodec H.264. Cada cinco segundos
cuenta dibujos de la vista y unidades de vídeo, estado interactivo/bloqueo y edad
del último cuadro. Cuenta dibujos y salida por separado porque el encoder puede
repetir un cuadro anterior. El ensayo es local, no envía datos a ICCOA y no
inicia DeX. Tiene un límite de 11 minutos y se detiene con el botón de la sonda;
no usa un servicio foreground, por lo que Android puede cerrar el proceso y perder
el diagnóstico. `isInteractive` no es una medición independiente del estado físico
de todos los paneles.

La prueba instrumentada con pantalla encendida en el S25 produjo más de cinco
dibujos y más de cinco salidas H.264 en cuatro segundos, sin errores; no se
descodificaron los datos ni se probó el teléfono bloqueado. No se ha medido DeX,
ni se ha probado imagen o táctil con el coche. La ruta de espejo permanece sin
alteraciones. Antes de seguir con una integración hay que medir continuidad con
pantalla apagada y determinar una fuente legítima de DeX, evitando confundir
contenido propio con el escritorio de Samsung.
