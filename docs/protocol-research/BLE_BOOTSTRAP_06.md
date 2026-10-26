# Intercambio BLE y Wi-Fi Direct — 0.6

## Evidencia y alcance

La exportación del propietario del 22 de octubre confirma UnionShare, ClientInfo
con WRITE y ServerInfo con READ/NOTIFY. Confirma inspección GATT, no intercambio,
red ni sesión autenticada. Véase VEHICLE_DIAGNOSTICS_2026-10-22.md.

La implementación nueva está escrita independientemente. No incorpora clases,
fuentes, bibliotecas nativas, identidades ni claves de fabricantes.

Evidencia estática local Samsung 3.1.04.10 (hash del original registrado en PROGRESS):

- com/share/connect/ble/BluetoothLeService.java:99–120: campos ClientInfo;
  d.java:285–302: identidad de hasta seis bytes; d.java:337–352: banda, MAC P2P y modo.
- com/share/connect/ble/e.java: negociación MTU, inventario, notificación local,
  escritura ClientInfo y respuesta ServerInfo. El cliente OEM no escribe un CCCD
  en ese método; AionLink sí lo solicita si el descriptor estándar está expuesto,
  y usa notificación local si no existe. Interoperabilidad todavía por probar.
- com/ucarmd/connly/d.java: ruta que acepta PIN manual como argumento de connect.
- S4/j.java y S4/r.java: dirección obtenida de requestDeviceInfo y banda 1 si
  is5GHzBandSupported; 0 en caso contrario.
- S4/n.java y T4/b.java: ruta P2P con parámetros de grupo; 1002 identifica AP.
  AionLink solicita únicamente 1001/P2P; no cambia de topología silenciosamente.

Los nombres y esquemas observados describen comportamiento interoperable; no se
copió la implementación OEM. La autenticación posterior CarAuthClient utiliza
negociación de claves y confirmación separadas; no se implementó ni se sustituyó
por un resultado positivo ficticio.

## Restricción de Android y alternativa experimental

[requestDeviceInfo](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#requestDeviceInfo(android.net.wifi.p2p.WifiP2pManager.Channel,%20android.net.wifi.p2p.WifiP2pManager.DeviceInfoListener))
anonimiza la dirección local a 02:00:00:00:00:00 sin LOCAL_MAC_ADDRESS. No se declara
ese permiso privilegiado ni se intenta concederlo por ADB. Tener permiso normal
de Wi-Fi cercano no garantiza acceso a la dirección real.

Si Android entrega una dirección utilizable y el propietario introduce el código,
se construye ClientInfo con identidad aleatoria propia de seis caracteres,
modelo real y capacidad de banda del teléfono. No se usan MAC inventadas ni marca
Samsung como identidad de aplicación.

Si falta dirección o código, se intenta exclusivamente READ sobre ServerInfo,
porque el receptor observado declara esa propiedad. Que permita lectura no prueba
que devuelva los parámetros antes de ClientInfo: **esta alternativa es una hipótesis
de interoperabilidad pendiente de prueba**, no una ruta de negociación confirmada.
Un valor vacío, rechazo o formato desconocido termina con error explícito; no se
fabrican parámetros ni se reintenta eludir el rechazo. No hay autenticación en esa
lectura, ni una declaración de sesión por haberla completado.

## Comportamiento implementado

- Selección explícita del vehículo propio estacionado. PIN opcional de seis dígitos,
  sólo en memoria; nunca se guarda en preferencias ni diagnóstico.
- Permisos de Wi-Fi bajo demanda; detección de Wi-Fi apagado y grupos preexistentes.
- Conexión BLE, MTU, descubrimiento, suscripción y escritura secuenciales. En la
  ruta normal se exige confirmación de escritura antes de entregar la respuesta.
- Android moderno recibe buffers en callbacks seguros; ruta anterior para API 29–32.
  Límite de 30 s, descarte de callbacks antiguos y limpieza de buffers propios.
- ServerInfo: UTF-8 estricto, límite 4096 bytes, validación de SSID DIRECT-, PSK,
  dirección unicast, frecuencia y puerto. Errores sin contenido remoto.
- Wi-Fi Direct mediante config público con SSID, contraseña, MAC y frecuencia que
  entregó el receptor. No se inventan endpoints, se abren sockets ni se envía UCar.
- Límite de red de 45 s; éxito sólo tras groupFormed y coincidencia de propietario
  y nombre de red. onSuccess de connect no se trata como red conectada.
- Se rechazan grupos existentes; no se eliminan redes ajenas. Stop/fondo cierran
  los recursos de AionLink y solicitan retirar sólo un grupo atribuido al intento
  y comprobado. Si la formación no pudo atribuirse, no se borra un grupo desconocido.
- Diagnóstico: modo y etapas, MTU, conteos y errores controlados. Sin PIN, MAC,
  SSID, PSK, IP, serial ni payload. Parámetros de red recibidos sólo en memoria.

La inspección anterior sigue disponible con el botón «Solo comprobar servicios
BLE». El botón principal nuevo es «Conectar BLE + Wi-Fi Direct».

## Qué falta

No se ha probado el nuevo intercambio, la alternativa READ o la red contra el AION.
Faltan autenticación normal UCar, confirmación de clave, canales y negociación de
medios, codificación/proyección e integración Android Auto. El transporte USB
anterior no recibe un proxy o autenticación nuevos en esta versión. Una red
conectada no basta para proyectar. La autorización o formato de ClientInfo aceptado
por este receptor concreto todavía puede necesitar más evidencia.

Referencias públicas de implementación:
[BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt),
[Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifi-direct),
[WifiP2pConfig.Builder](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder).
