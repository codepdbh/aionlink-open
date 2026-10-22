# Comparación de rutas — 2026-10-22

Alcance: seis archivos APK (dos versiones Samsung, CarWith, OPPO, componente y
receptor CarLife). No se afirma revisión exhaustiva de cada clase o biblioteca.
Estos hallazgos describen las rutas inspeccionadas; no prueban que otras no existan.

| Archivo / paquete | Ruta observada | Evidencia local Java | Implicación para AionLink |
|---|---|---|---|
| Samsung 3.1.04.10 / com.samsung.android.carlink | ICCOA BLE → P2P/Auth → JNI display; también CarLife separado | com/share/connect/ble/d.java, com/ucar/protocol/MessageHeader.java, UCarAuthProtocol.java, com/ucarmd/i.java | Prioridad para especificación independiente del receptor candidato |
| Samsung 3.1.02.20 | ICCOA y CarLife separados | com/ucarmd/ShareLinkService.java, com/share/connect/security/CarAuthClient.java | Útil para nombres menos ofuscados; no sustituye análisis de versión instalada |
| Xiaomi / com.miui.carlink | castfwk, UCarProto, negociador y transporte | com/miui/carlink/castfwk/negotiator/ClientKeyNegotiator.java, databus/protocol/channel/socket/ChannelType.java, da/d.java | Contraste estático de framing/canales; dependencias MIUI separadas |
| OPPO / com.heytap.opluscarlink | SDK de control, binding/estado/llave y gestores ICCOA | com/heytap/opluscarlink/carcontrol/carlink/sdk/CarLinkSDKManager.java, com/oplus/carlink/data/iccoa/ICCOADigitalKeyManager.java | No trasladar control o llave digital a una implementación de proyección |
| CarLife Component / com.baidu.carlife.samsung | Componente CarLife con bibliotecas nativas y UI | Inventario de manifiesto/lib y clases CarLife | Familia separada; no evidencia de receptor ICCOA |
| CarLife Receiver / com.carlife.autodex | Canales socket y BluetoothSocket, protobuf CarLife | d/b/a/a/o/i/f/c.java, a.java, d/b/a/a/o/h/a.java, com/baidu/carlife/protobuf/ | No es prueba del receptor ICCOA del AION; no tratar su framing como UCar |

Los prefijos completos por APK y hashes se encuentran en analysis/reports/APK_ANALYSIS_REPORT.md.
Todo Java/Smali reconstruido permanece en el área privada excluida de Git.

## Coincidencias útiles y diferencias

Samsung nuevo y CarWith muestran cabeceras de 20 bytes y CRC sobre los primeros 18,
con longitudes/secuencias en offsets comunes. CarWith: `da/d.java:46` en adelante;
Samsung: `com/ucar/protocol/MessageHeader.java`. Eso justifica un lector de análisis
compartido candidato; no prueba que el AION acepte ambas implementaciones.

AUTH 57209, CONTROL 57219, MEDIA 57229, SENSOR 57239 y CERT 57249 aparecen como
canales en ambos clientes inspeccionados. CarWith añade SHARE 10113; Samsung nuevo
enumera VIDEO 57259. No son puertos escaneados ni negociados en nuestro vehículo.

CarLife tiene un modelo de mensajes distinto en sus propias clases, con tamaño de
cabecera dependiente del canal. Requiere una especificación separada, fixtures
reales autorizadas y justificación de utilidad antes de implementar otro adaptador.

## Qué se recreó independientemente

- Lectura offline de cabecera UCar, CRC bit a bit, límites y lectura fragmentada.
- Reconocimiento pasivo de los UUID FCFB/UnionShare como indicios no autenticados.
- Diagnósticos de búsqueda suficientes para comparar observaciones futuras.
- Colector ADB limitado a aplicaciones relevantes y resumen sin contenido de logs.

## Qué falta

No se recrearon todas las apps ni todos sus protocolos. Faltan mensajes BLE
completos, topología real, variante/versionado del receptor, autenticación y su
confirmación remota, codec de payloads y medios negociados. No hay un receptor AION
disponible durante este trabajo. No se copian implementaciones propietarias ni
se redistribuye heycastcore, micastcore u otras bibliotecas como atajo.
