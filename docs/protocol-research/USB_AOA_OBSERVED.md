# USB ICCOA/AOA — evidencia estática y cliente independiente

2026-10-22. El propietario observa reacción al conectar cable y necesidad de
Bluetooth; no se identificó aún la pantalla/evento exacto ni se capturó el AION.
USB+Bluetooth se prioriza como hipótesis, sin afirmar que Wi-Fi esté ausente.

## Rutas inspeccionadas

- Samsung 3.1.04.10: IccoaUsbConnectionActivity recibe USB_ACCESSORY_ATTACHED,
  comprueba permisos y evita competir con el intento inalámbrico.
- Samsung y CarWith registran manufacturer=ICCOA, model=CarLink, version=1.0.0 en
  res/xml/accessory_filter.xml. Son identificadores observados, no inventados.
- Samsung p018b5/a.java abre UsbManager.openAccessory y obtiene streams; no basta
  con el broadcast USB_STATE connected/adb para declarar una sesión.
- Samsung p018b5/c.java y d.java implementan canales multiplexados. La ruta acaba
  iniciando CarAuthClient en loopback, después de preparar canales. AOA no elimina
  autenticación ni implica proyección.
- CarWith AOAActivity distingue ICCOA, Baidu/CarLife y Carbit por manufacturer/model;
  no los trata como el mismo protocolo. No se reutiliza el flujo Carbit para AION.

Prefijo Samsung: analysis/java/samsung-auto-3-1-04-10-s25-5633d2f9c1bb/sources/.
Endian contrastado en p005a5/a.java:d y p; control en p018b5/d.java:d y lector c.java.

## Formato candidato de multiplexación

Cabecera 8 bytes: uint32 big endian canal, uint32 big endian longitud declarada.
Canales aceptados por el lector observado: 1..240. La lectura observada de payload
consume max(longitud,8), aunque sólo longitud bytes tienen significado declarado.
Esta peculiaridad de padding requiere validación contra el receptor real.

Canal 1: control de apertura, payload 10 bytes. Offset 0 int32 puerto; offset 4
int32 channelId (-1 para solicitud); byte8 rol socket; byte9 tipo de mensaje.
Samsung emite inicialmente RTSP 7236 y UIBC 4321, channelId=-1, rol=1, tipo=2.
El código propio reproduce solamente esos hechos de formato y esos dos pedidos
estáticos opcionales; no fabrica aceptación ni implementa una sesión completa.

## Implementación propia

La versión 0.4 enumera accesorios, solicita permiso y abre el descriptor sólo al
confirmarlo el propietario. Ofrece observación sin envío o inicio experimental de
los dos canales anteriores. El segundo modo sólo se habilita para el identificador
ICCOA/CarLink/1.0.0 observado, y sigue sin estar probado contra el AION.

Lector incremental original con límites de memoria, tamaño, tiempo y frames; nunca
guarda payload, PIN o datos de autenticación. Usa buffers de lectura amplios y no
supone que una lectura de USB corresponda a un frame completo. Los errores cierran
el descriptor; background/detach/cancelación detienen la operación.

No abre servidores loopback, no responde automáticamente a órdenes remotas de
crear sockets, no concede MANAGE_USB ni cambia USB functions/ADB/CSC. La aplicación
normal del teléfono no convierte por sí sola un host de carga/ADB en AOA.

## APIs y límites

En [Android Open Accessory](https://source.android.com/docs/core/interaction/accessories/aoa)
el accesorio externo actúa como host e inicia el modo. La app usa las
[APIs públicas de accesorio USB](https://developer.android.com/develop/connectivity/usb/accessory).
Un ordenador conectado por ADB no es evidencia de un accesorio ICCOA. La app
distingue alimentación USB, dispositivos host y accesorios AOA.

Bluetooth conserva su búsqueda/inspección independiente; su rol en la sesión
cableada (audio, pairing o control) aún es desconocido. No se habilita una búsqueda
Bluetooth ni emparejamiento automáticamente al conectar el cable.
