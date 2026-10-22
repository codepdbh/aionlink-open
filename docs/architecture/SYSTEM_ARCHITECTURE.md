# Arquitectura del sistema

La evidencia propietaria permanece en el área local de análisis. La implementación
Android se escribió independientemente sobre APIs públicas, sin importar código o
bibliotecas de las APK investigadas.

| Módulo | Responsabilidad y estado |
|---|---|
| app | Compose Material 3, ViewModel, permisos bajo demanda, búsqueda y exportación SAF |
| core | StateFlow de sesión, descubrimiento, errores y timeout de negociación |
| protocol-api | Peer, TransportKind, Transport y contrato ProtocolAdapter |
| protocol-iccoa | Adaptador Unsupported; indicios UUID y lector de cabecera offline; no envía bytes |
| transport-bluetooth | Descubrimiento Classic/BLE y enlace GATT de inspección; sin sockets ni pairing |
| transport-usb | Cliente AOA: enumeración, permiso, apertura, lectura limitada y bootstrap opcional |
| transport-wifi | Marcador pendiente; topología y endpoints desconocidos |
| projection | Marcador pendiente; no captura pantalla/audio |
| vehicle-profiles | Perfil GAC separado, cero funciones o transportes verificados |

El coordinador acepta descubrimiento y observación de dispositivos; no permite
asignar desde la UI SESSION_ESTABLISHED ni PROJECTION_ACTIVE. Estos nombres existen
en el modelo, pero no hay una transición a ellos mientras no se implemente una
negociación demostrable. Tampoco se usa el flujo hipotético del megaprompt como
especificación del orden real. El contrato de resultados se ampliará con evidencia
de sesión sólo cuando se conozca qué confirmación da el receptor.

La búsqueda dura 15 segundos, usa IDs aleatorios nuevos por búsqueda y mantiene
MAC únicamente en memoria para deduplicación. La UI muestra el nombre anunciado,
pero la exportación contiene sólo eventos controlados, API Android, permisos y
estado. No exporta nombres, MAC, VIN, PIN, credenciales ni contenido del protocolo.
El usuario elige dónde guardar mediante Storage Access Framework. No hay permiso
INTERNET, almacenamiento general, captura, micrófono ni servicio en primer plano.

El ViewModel conserva resultados frente a recomposición; la salida de la pantalla
detiene los escáneres. No existe reconexión, transferencia A/V ni inyección de input.
Las futuras implementaciones requieren interfaces separadas para framing,
autenticación legítima, autorización del usuario y medios negociados. Una API BLE
disponible no implica que ICCOA permita clientes independientes.

No se implementan control remoto del vehículo, llave digital ni operaciones CAN.

Desde 0.2 el transporte Bluetooth informa UUID de servicios reconocidos por una
lista explícita de constantes observadas en APK, cantidad de servicios y atributo
conectable. No conserva datos crudos de anuncios ni exporta UUID ajenos a esa lista.
El lector de cabecera es independiente del transporte y sólo acepta streams locales;
comprueba CRC y límites antes de asignar payload. No puede producir aceptación de
sesión. Los callbacks BLE de búsquedas anteriores quedan invalidados por generación.

Desde 0.3 GattInspector conecta sólo al BluetoothDevice observado seleccionado por
el propietario. Los handles y direcciones permanecen en memoria. Su ciclo tiene
CONNECTING, LINK_CONNECTED, DISCOVERING_SERVICES y un resultado terminal; la conexión
se cierra tras obtener el inventario o al fallar/cancelar/agotarse el timeout.
Estos estados son independientes de SessionCoordinator. No hay transición desde
GATT a SESSION_ESTABLISHED. La metadata exportada se filtra por constantes observadas
y no contiene valores de características ni UUID ajenos.

Desde 0.4 UsbAccessoryClient enumera AOA con UsbManager y recibe attach/detach públicos.
El usuario solicita permiso y elige observar o emitir los dos pedidos de canal
documentados. El consentimiento pertenece a un intento; callbacks obsoletos no
pueden iniciar el siguiente. El descriptor se instala/cancela bajo lock y se cierra
al detener, retirar, salir a background, agotar 15 segundos o fallar. El lector usa
poll no bloqueante y alimenta un decoder incremental sin guardar payload.

UsbMuxDecoder y UsbChannelStartup están en protocol-iccoa, con especificación y
pruebas sintéticas. El transporte no interpreta órdenes remotas como autorización
para crear sockets. No hace de proxy loopback ni autentica UCar aún. USB COMPLETE
describe la observación terminada, nunca SESSION_ESTABLISHED.
