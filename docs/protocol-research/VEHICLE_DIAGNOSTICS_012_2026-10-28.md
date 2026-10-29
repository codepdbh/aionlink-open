# Pruebas 0.12 del 28 de octubre y cambio de ruta 0.13

Dos exportaciones recuperadas el 29 de octubre, almacenadas en privado;
ambas corresponden al mismo proceso y la segunda incluye la primera.
No se suman como intentos separados.

| TXT | Bytes | SHA-256 |
|---|---:|---|
| (16) | 4990 | `b00f5528056d35fa08a98ffed28d3b638d773a421232859b8006daa76b414ce4` |
| (17) | 8275 | `e1d10cb486803702ac09aff8c76d059644a5564d8e9f0465408d474325a32335` |

Horas locales America/La_Paz, 28 de octubre: primer intento BLE confirmado,
respuesta de 123 bytes con `port=ZERO`, P2P CONNECTED 21:01:20.387768 y
desconexión 21:01:39.081993, 18,694225 s. A las 21:01:23.407920 el propietario
confirmó el diagnóstico AUTH; la app devolvió UNAVAILABLE porque no encontró un
objeto `Network` asociado a la interfaz P2P. No se abrió ningún socket; este
resultado no es rechazo del vehículo ni prueba del estado del puerto. Exportar
pasó la app a segundo plano y la red se conservó hasta la desconexión.

Segundo intento: BLE confirmado, respuesta con `port=ZERO`, P2P CONNECTED
21:02:09.986359 y ERROR de desconexión 21:02:28.842381, 18,856022 s. No hay
prueba AUTH en este intento. Causa de ambos cortes desconocida; la ausencia de
autenticación completa es una posible explicación, no una conclusión demostrada.
No hay sesión ICCOA o proyección. USB sin accesorios ni datos.

## Corrección 0.13

La 0.12 imponía `Network` para la comprobación TCP. La documentación pública
de Android describe P2P con sockets estándar y dirección del propietario de
grupo. La 0.13 mantiene como primera opción `Network.bindSocket`. Si Android no
expone una `Network` coincidente, busca en la interfaz informada por el grupo
una única dirección IPv4 local que comparta subred con la IP del propietario.
Sólo se admiten prefijos /16 a /30, direcciones distintas y no multicast,
loopback o comodín. El socket se vincula a esa dirección local antes de conectar.
Si no hay dirección inequívoca o Android no la permite, no se conecta.

`ruta=NETWORK` o `ruta=P2P_SOURCE` se registra sin IP o nombre de interfaz.
La segunda opción vincula **dirección de origen**, pero esto no garantiza por
sí solo la interfaz de salida elegida por el sistema si existen rutas ambiguas;
el destino sigue siendo exclusivamente el propietario del grupo ya verificado.
No hay cambio global de red ni ruta o IP inventada. Un clic y confirmación del
propietario siguen siendo necesarios; un solo TCP de 2,5 s sin payload. El
receptor podría cerrar la red. OPEN sólo significa TCP; autenticación pendiente.

Fuentes: [Android Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifi-direct),
[NetworkInterface](https://developer.android.com/reference/java/net/NetworkInterface),
y evidencia Samsung/Xiaomi en `CONNECTION_RESEARCH_2026-10-28.md`.
El acceso de una app a direcciones puede ser parcial; no está verificado si el
S25 expondrá esa interfaz al estar conectado al AION. Pruebas con direcciones
sintéticas y loopback no demuestran interoperabilidad del receptor.
