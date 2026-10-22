# UCar: formato observado en Samsung Auto 3.1.04.10

2026-10-22. Especificación de análisis estático; NO validada contra el receptor AION.
La implementación propia sólo decodifica bytes locales y clasifica anuncios. No
abre sockets, envía autenticación ni produce una sesión simulada.

Fuente: APK obtenida mediante `pm path` y `adb pull` desde el teléfono autorizado.
SHA-256 y metadatos completos en el área local `analysis/metadata/`.
Prefijo Java: `analysis/java/samsung-auto-3-1-04-10-s25-5633d2f9c1bb/sources/`.
Se contrastaron offsets con `smali_classes2/com/ucar/protocol/MessageHeader.smali`.

## Descubrimiento

`com/share/connect/ble/i.java:9` define FCFB
(`0000fcfb-0000-1000-8000-00805f9b34fb`). `ble/d.java:376` lo usa como filtro
de UUID de servicio anunciado. El servicio GATT UnionShare está definido por
`2abcc850-9935-4f8a-ba84-123456789100` (`ble/i.java:32`, `ble/j.java:25`).
Una coincidencia indica un candidato observado, nunca compatibilidad verificada.
El nombre Bluetooth no basta. No se interpretan contenidos de service data, PIN,
manufacturer data ni identidades a partir de estos UUID.

## Cabecera de mensajes

Fuentes: `com/ucar/protocol/MessageHeader.java`, `ByteUtil.java`, `Crc16.java`,
`SourceDevice.java`, `DataFormat.java`, `MessageType.java`, `CmdCategory.java`.
Cabecera de 20 bytes; enteros en big endian; longitud incluye cabecera.

| Offset | Bytes | Significado observado |
|---|---|---|
| 0 | 4 | Longitud total |
| 4 | 4 | Secuencia |
| 8 | 4 | Timestamp (segundos según la implementación) |
| 12 | 1 | bit 0 origen; bits 1–2 formato; bits 3–4 tipo; bits altos sin interpretar |
| 13 | 1 | Categoría |
| 14 | 2 | Método |
| 16 | 2 | Reservado, conservar sin semántica asumida |
| 18 | 2 | CRC de bytes 0–17 |

CRC inferido de tabla y actualización: CRC-16/ARC, polinomio reflejado 0xA001,
inicio 0, xor final 0. El código propio usa cálculo bit a bit; no copia la tabla.
Se contrasta contra el vector estándar ASCII 123456789 → 0xBB3D.

Origen: coche=0, teléfono=1. Formato: raw=0, PB3=1. Tipo: envío=0, solicitud=1,
respuesta=2, envío síncrono=3. Categorías observadas: control=1, sensor=2,
autenticación=3, audio=4, vídeo=5, certificado=6, ACK=7. Valores nuevos deben
conservarse como desconocidos; no se debe forzar una interpretación compatible.

`UCarAuthProtocol.java` distingue métodos auth 1 (solicitud), 2 (respuesta),
3 (confirmación). La confirmación local del envío no prueba aceptación del receptor.
La implementación independiente sólo etiqueta cabeceras; no interpreta material
criptográfico ni crea mensajes de autenticación.

## Canales candidatos

`com/ucar/protocol/channel/ChannelType.java`: UIBC 4321, RTSP 7236, RTP 15550,
AUTH 57209, CONTROL 57219, MEDIA 57229, SENSOR 57239, CERT 57249, VIDEO 57259.
Son constantes del cliente, no puertos comprobados en el AION. No se escanean.
CarWith declara canales comunes y SHARE 10113, pero no VIDEO en esa enumeración:
`com/miui/carlink/databus/protocol/channel/socket/ChannelType.java`.
Esta diferencia impide declarar que ambos protocolos/versiones son intercambiables.

## Código de seis caracteres

`com/ucarmd/i.java:69` comprueba longitud seis para modo normal antes de llamar al
componente BLE. Es una validación estática de longitud, no prueba de que el código
del AION sea ese mismo valor, ni de que sea pairing Bluetooth. No se solicita,
almacena ni envía un PIN en nuestra versión actual.

## Pruebas propias

Fixtures sintéticas de cabecera validan offsets, CRC, longitudes, fragmentación,
desbordamientos y valores desconocidos. Se etiquetan como sintéticas y NO son
capturas ICCOA ni pruebas de interoperabilidad. Falta una captura autorizada del
receptor real para contrastar el framing, versión y secuencia temporal.
