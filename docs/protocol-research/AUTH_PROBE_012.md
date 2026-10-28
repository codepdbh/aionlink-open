# 0.12: comprobación opcional del canal AUTH

Las pruebas 0.10 formaron tres grupos P2P con el AION, pero no abrieron el canal
posterior. Los clientes Samsung 3.1.04.10 y Xiaomi 4.0.14 identifican un canal
TCP AUTH 57209; fuentes y hashes en `CONNECTION_RESEARCH_2026-10-28.md`. Esa
evidencia estática no garantiza que este receptor lo exponga.

Tras conectar y verificar el grupo del receptor (SSID/MAC, teléfono cliente),
Android proporciona dirección del propietario e interfaz. En 0.12 el usuario
puede pulsar **Comprobar canal AUTH** y confirmar expresamente en un diálogo.
Sólo entonces se intenta una conexión TCP a la dirección suministrada por Android
y puerto observado, mediante `Network.bindSocket` sobre la red cuya interfaz
coincide con el grupo. Si Android no expone dirección/interfaz/red coincidente,
se informa UNAVAILABLE y no se conecta por otra ruta. La selección de red no
modifica la conexión global de otras apps. Esta comprobación sólo soporta P2P.

La prueba hace una conexión de hasta 2,5 segundos, una sola vez, sin leer ni
escribir datos de aplicación; cierra el socket al terminar o al detenerse el
grupo. El cierre puede influir en el receptor; se explica antes de confirmar.
No se transmiten PIN, mensajes AUTH o payload. Tampoco se registra dirección,
nombre de interfaz, configuración ni mensajes de excepción. Se exportan etapa,
categoría y duración. `OPEN` indica sólo apertura TCP, no autenticación, sesión
ICCOA ni proyección; CONNECT_FAILED no prueba PIN incorrecto y TIMEOUT no
identifica por sí solo al causante. `BIND_FAILED` indica que no pudo vincularse
el socket a la red; sin red coincidente no se intenta abrirlo.

El botón aparece junto al estado inicial cuando la red P2P está conectada. Si el
receptor cierra el grupo antes de que se pulse, no puede ejecutarse: exportar el
diagnóstico igualmente. Si la prueba termina, guardar diagnóstico; se bloquea
la exportación mientras está activa para conservar resultado. No hay reintentos
automáticos, exploración de puertos, suplantación o ejecución de APK ajenas.

Pruebas: servidor loopback propio comprueba conexión y EOF sin datos; prueba
de fallo de enlace comprueba que no hay conexión; prueba UI usa callbacks
sintéticos y confirma que cancelar no inicia nada. Ninguna conecta al AION.

Referencias API: [WifiP2pInfo](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pInfo),
[WifiP2pGroup](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pGroup)
y [Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.Socket)).
El cliente AUTH completo, validación de respuesta y medios siguen pendientes.
