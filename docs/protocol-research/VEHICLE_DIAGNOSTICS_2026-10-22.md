# Análisis de diagnósticos del 22 de octubre de 2026

Revisado el 23 de octubre, zona America/La_Paz (UTC−04:00).

## Procedencia

Se localizaron tres TXT `aionlink-diagnostics*` mediante ADB en Download del
teléfono autorizado. Copias locales en `private/vehicle-tests-2026-10-23/`,
excluidas de Git. Los dos primeros coinciden por SHA-256 con los ya analizados;
hay un archivo nuevo, no tres pruebas nuevas.

| Exportación | Versión | Bytes | SHA-256 |
|---|---|---:|---|
| Sin sufijo | 0.1 | 320 | `bdd304ff3d4f0e17a3a0eeef3b54910ef0c9616febf8b81b5a3fe958ef52dbce` |
| Sufijo (1) | 0.4 | 3363 | `8c40e29cadb04365c4101cba37bd0fac37759d8ab3f8da8a719885e63fbbf56a` |
| Sufijo (2) | 0.5 | 2053 | `98d00367faf45619f0da0c36203bf24d3b273a247bb1efeb000bea459454e61d` |

## Resultado nuevo: BLE e inventario GATT completados

El archivo 0.5 registra dos solicitudes de enlace y dos inspecciones terminadas,
ambas con cinco servicios y UnionShare presente. Horas locales del 22 de octubre:

| Intento | Solicitud GATT | Resultado | Tiempo registrado hasta resultado |
|---|---|---|---:|
| 1 | 19:03:48.526 | 19:03:49.342, INSPECTED | ~816 ms |
| 2 | 19:03:55.060 | 19:03:55.186, INSPECTED | ~126 ms |

Son tiempos entre eventos de la aplicación, no latencias de radio, autenticación
ni prueba de descubrimiento remoto sin caché Android. Los ID temporales cambian
entre búsquedas; no permiten afirmar que fueron dos vehículos ni acreditar que
fue el mismo equipo físico.

El snapshot final contiene un candidato BLE conectable, RSSI −53 dBm, anunciando
FCFB. Dentro del servicio UnionShare se registraron:

| Característica conocida | Terminación UUID | Propiedades | Interpretación |
|---|---|---:|---|
| ClientInfo | 9101 | 8 (0x08) | Permite escritura |
| ServerInfo | 9102 | 18 (0x12) | Permite lectura y notificación |

La interpretación de bits se contrastó con la [referencia oficial de Android](https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic).
Las propiedades no demuestran que una lectura/escritura haya ocurrido o esté
autorizada sin requisitos adicionales. Los UUID completos están definidos en
AdvertisementEvidence.kt y se corresponden con la evidencia estática Samsung.

ClientAuthInfo (terminación 9104) no figura en el inventario filtrado final.
Esto no prueba que la autenticación sea imposible o que nunca aparezca en otra
fase. No se exporta el inventario completo de los otros servicios.

## Por qué termina sin proyectar

GattInspector.kt llama a discoverServices, resume metadatos y después ejecuta
disconnect/close mediante finish/cleanup. No escribe ClientInfo, no habilita
notificaciones ServerInfo, no envía PIN ni negocia una red o una sesión. Por eso
el cierre registrado es el comportamiento implementado, no un rechazo demostrado
del receptor. `código=null` es el campo opcional no rellenado en el resultado
correcto; no significa un error GATT.

DEVICE_FOUND es el estado del coordinador de descubrimiento; INSPECTED pertenece
a la inspección GATT. Ninguno indica sesión ICCOA. Las búsquedas terminan tras
unos 3,6 segundos porque la selección detiene el escaneo antes de conectar; no
hay un timeout de búsqueda registrado en esos eventos.

USB final: IDLE, cero accesorios AOA, cero bytes/frames/solicitudes y alimentación
USB=false. Esto describe el momento de exportación, no todo el historial de
cableado. No acredita rechazo AOA, fallo de cable ni que se probara el bootstrap.

## Comparación y siguiente hito

La exportación 0.4 sólo acreditaba anuncio FCFB; GATT seguía IDLE. La 0.5 acredita
que el cliente internacional puede completar un enlace BLE de inspección y ver
el servicio y dos características esperados. La identidad AION depende todavía
del contexto de selección del propietario; no existe identificación criptográfica.
No hay rechazo de autenticación o bloqueo regional demostrado.

El siguiente hito de implementación es mantener un enlace para el intercambio
normal ClientInfo/ServerInfo con metadatos de red propios y una topología
documentada; después vendrían la conexión de red y autenticación. No basta con
repetir la inspección ni emparejar Bluetooth. USB requiere evidencia independiente
del accesorio AOA durante una prueba junto al vehículo estacionado.

Esta revisión sólo leyó archivos y código; no ejecutó APK de terceros, no inició
conexiones ni modificó la app o el vehículo.
