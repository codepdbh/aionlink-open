# Arquitectura del sistema

La evidencia propietaria permanece en el área local de análisis. La implementación
Android se escribió independientemente sobre APIs públicas, sin importar código o
bibliotecas de las APK investigadas.

| Módulo | Responsabilidad y estado |
|---|---|
| app | Compose Material 3, ViewModel, permisos bajo demanda, búsqueda y exportación SAF |
| core | StateFlow de sesión, descubrimiento, errores y timeout de negociación |
| protocol-api | Peer, TransportKind, Transport y contrato ProtocolAdapter |
| protocol-iccoa | Adaptador que responde Unsupported; no envía bytes |
| transport-bluetooth | Descubrimiento real Classic/BLE, no sockets ni pairing |
| transport-usb | Marcador pendiente; rol USB desconocido |
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
