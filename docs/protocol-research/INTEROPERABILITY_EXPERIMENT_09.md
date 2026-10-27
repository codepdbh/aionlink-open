# 0.9 — experimento de ClientInfo con metadatos de Android

## Corrección de una suposición anterior

En 0.6–0.8, AionLink exigía una dirección local real antes de enviar ClientInfo.
La API Android oculta ese dato a aplicaciones ordinarias, pero que esa política
local fuese un requisito obligatorio del receptor AION **no estaba demostrado**.
Los logs sólo probaban bloqueos de nuestra app, no rechazo remoto.

Evidencia estática local, sin ejecutar ni copiar código OEM:

- Samsung 3.1.04.10, BluetoothLeService.java:a: incorpora el valor guardado de MAC
  en ClientInfo; d.java:setP2pDeviceMac lo recibe sin validar que sea una dirección
  no anonimizada. El generador no añade una validación equivalente a la de AionLink.
- Xiaomi CarWith, wireless/bt/t.java:M y C: incorpora el valor local y comprueba
  que no sea nulo/vacío. Esa comprobación no acredita que un receptor acepte un
  valor anonimizado, pero tampoco exige la política estricta añadida por nosotros.
- Samsung ble/f.java: el parser receptor lee el campo mac y lo transmite al
  observador; g.java conserva ese dato para la sesión. No es el software del AION
  y no demuestra que el dato sea prescindible en etapas posteriores.

La [API oficial requestDeviceInfo](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#requestDeviceInfo(android.net.wifi.p2p.WifiP2pManager.Channel,%20android.net.wifi.p2p.WifiP2pManager.DeviceInfoListener))
documenta el anonimizado a 02:00:00:00:00:00. La privacidad de Android se mantiene:
no se obtiene la dirección oculta ni se solicita LOCAL_MAC_ADDRESS.

## Hipótesis que se podrá probar

El AION podría aceptar un ClientInfo con el valor anonimizado devuelto por la API
y avanzar hasta ServerInfo, especialmente si no necesita identificar al cliente
por MAC para esa etapa. **No está verificado.** También puede rechazarlo o necesitar
el dato real más adelante; no se declara conectado en esos casos.

El experimento sólo se ofrece si la API devuelve exactamente su valor de
anonimizado documentado. PlatformP2pAddress conserva el retorno; no reemplaza null,
vacío o datos inválidos con una dirección fabricada. El propietario debe activar
una casilla sin preselección y confirmar el envío con su código y vehículo parado.
El consentimiento no se guarda ni sobrevive a cerrar el diálogo.

No se modifican los campos observados de ClientInfo, ni se añaden campos propios
al mensaje remoto. Identidad y nombre siguen siendo los de nuestro cliente, sin
suplantar Samsung/Xiaomi ni utilizar claves OEM. El único cambio de esa variante
es permitir, con consentimiento, el retorno anonimizado real de la API. La ruta
normal con dato real sigue disponible y un dato ausente sigue bloqueado.

La validación del **receptor remoto** conserva el rechazo de direcciones nulas,
multicast o anonimizadas. El experimento no habilita endpoints inválidos ni evita
autenticación. Nunca se convierte una escritura GATT confirmada en sesión ICCOA.

## Dos modalidades de red observadas

Samsung ble/e.java lee type de ServerInfo, por defecto 1001. S4/n.java:v distingue
1001/P2P y 1002/AP; esta última usa WifiNetworkSpecifier con aprobación Android.
0.9 admite ambas respuestas conocidas, sin inventar ni forzar el modo remoto:

- P2P: mismo enlace de grupo, SSID DIRECT- y verificación de propietario/grupo.
- AP: solicitud exacta por SSID y BSSID, credenciales sólo en memoria, sin pedir
  Internet, guardar una red ni cambiar el enrutamiento global del proceso.
  Conectada sólo tras onAvailable de la solicitud concreta. Denegación o pérdida
  liberan callback y recursos. No se solicita una red antes de recibir ServerInfo.

La API pública y el diálogo están documentados en
[Wi-Fi Network Request](https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap)
y [ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager).
Se añade CHANGE_NETWORK_STATE, permiso normal, no WRITE_SETTINGS o privilegios.
Si la ventana Android provoca segundo plano, se conserva exclusivamente esa
solicitud con timeout de 45 s y limpieza adicional a 50 s sin retorno a la app.
No se añade un servicio permanente ni se toca la unidad del vehículo.

## Evitar otra interrupción al exportar

Los logs del 27 de octubre mostraron READ cancelado a los 12 s por segundo plano.
Guardar diagnóstico queda temporalmente deshabilitado mientras hay operación de
transporte pendiente, con explicación visible y botón Detener siempre disponible.
La captura previa al selector sigue vigente para estados terminados.

Los datos recibidos se cuentan aunque el parser rechace el contenido, para no
confundir respuesta inválida con ausencia de respuesta. Nunca se exporta el cuerpo.
Los errores identifican sólo una categoría permitida (UTF8, JSON, MODE, campos
ausentes, SSID, PSK, MAC, frecuencia o puerto), sin revelar sus valores ni
etiquetar una respuesta desconocida como PIN rechazado.

## Investigación de otras vías

- AOA sigue requiriendo que el receptor externo inicie modo accesorio. Los logs
  del propietario no muestran ningún accesorio; no se inventa una sesión USB ni
  se cambian USB functions para forzarla. Véase USB_AOA_OBSERVED.md.
- La [documentación pública ICCOA](https://www.iccoa.cn/suit_1.html) describe SDK e
  integración, pero no aportó aquí una biblioteca telefónica abierta utilizable.
- MiAuto se presenta como receptor para instalar en una unidad Android y depende
  de un SDK precompilado ausente de Git. No es un cliente para el S25 ni se
  incorpora ese SDK. No se propone instalar nada en el vehículo.
- La ruta normal CarAuthClient/ClientKeyNegotiator usa claves propias generadas,
  ECDH P-256, HMAC-SHA256 y confirmación de sesión separada; se localizaron esas
  operaciones estáticamente sin leer claves privadas de un dispositivo. El método
  agreeOnSessionKey está parcialmente decompilado; no se interpreta ese fallo de
  JADX como comportamiento real. La autenticación aún no está implementada.

## Criterio de la próxima prueba

Vehículo propio estacionado, ICCOA abierto, Bluetooth y Wi-Fi activos en el
teléfono. Seleccionar el receptor y preparar conexión. Si aparece la alternativa
anonimizada, leerla, autorizarla sólo si se desea el experimento, introducir la
clave actual y pulsar Probar conexión experimental. Esperar resultado o timeout;
si Android solicita aceptar una red, comprobar que corresponde al vehículo.
Exportar después de terminar. No hace falta compartir el PIN ni contraseña.

Hitos que el diagnóstico debe distinguir:

1. ClientInfo solicitado y escritura confirmada: transporte BLE, no autenticación.
2. SERVER_RECEIVED: respuesta válida y modo de red reconocido.
3. CONNECTED P2P/AP: red obtenida, no sesión ICCOA.
4. Autenticación UCar, canales y proyección: siguen pendientes.

Ni los tests locales ni esta hipótesis permiten prometer proyección en la próxima
prueba. Esta versión añade una transacción real verificable que antes bloqueábamos,
no una afirmación de compatibilidad.
