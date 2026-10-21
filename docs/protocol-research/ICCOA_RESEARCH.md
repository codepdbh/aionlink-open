# ICCOA CarLink: investigación independiente inicial

Fecha: 2026-10-21. No hay captura ni sesión con el receptor del AION.
CONFIRMADO estático significa observable en los archivos indicados, no probado
en hardware. PROBABLE indica interpretación; no es una especificación wire validada.

## Fuentes locales

Samsung Auto 3.1.02.20, paquete `com.samsung.android.carlink`, SHA-256
`0f57eec4b036ca151fcf40a28c77dd7cc192629e853c1b167daeb33618e7f755`.
Prefijo Java: `analysis/java/Samsung-Auto-v3-1-02-20-0f57eec4b036/sources/`.
Prefijo Smali: `extracted/Samsung-Auto-v3-1-02-20-0f57eec4b036/smali_classes2/`.
Las rutas siguientes son relativas a estos prefijos. No se reproduce código fuente.

| Hallazgo | Evidencia | Confianza / alcance |
|---|---|---|
| Entrada ICCOA WLAN delega al coordinador f6.h | `com/samsung/android/carlink/iccoa/IccoaWlanConnectService.java:60` | CONFIRMADO estático |
| Coordinador enlaza ShareLinkService y llama connect | `f6/h.java:270`, `:325`, `:388` | CONFIRMADO estático |
| Servicios BLE y Wi-Fi P2P se enlazan por separado | `com/ucarmd/ShareLinkService.java:920`, `:943` | CONFIRMADO estático |
| Cliente GATT y callbacks de características | `com/share/connect/ble/BluetoothLeService.java:543`, `:594`, `:676` | CONFIRMADO estático; orden AION DESCONOCIDO |
| Negociación AuthRequest/AuthResponse y confirmación AuthConfirm | `com/share/connect/security/CarAuthClient.java:63`, `:104` y su Smali `:159`, `:249` | CONFIRMADO estático; mensajes wire no capturados |
| Respuesta contiene versión, claves públicas, firma/HMAC, nonce y resultado | `ClientKeyNegotiator.java:54` y `ClientKeyNegotiator.smali:63` | CONFIRMADO estático; significado completo PROBABLE |
| El negociador genera claves locales con AndroidKeyStore y usa ECDH/ECDSA/HMAC | `com/share/connect/security/KeyNegotiator.java:80`, `:129`, `:137`, `:143`, `:161` | CONFIRMADO estático; requisitos OEM no determinados |
| Hay modos NORMAL, QUICK y QUICK_W_UC | `ClientKeyNegotiator.java:16` | CONFIRMADO estático; requisitos por modo DESCONOCIDOS |
| JNI de display carga heycastcore | `com/ucarmd/connectly/remoteapi/RemoteDisplayNative.java:24`, `:62` | CONFIRMADO estático |
| Ruta de display contiene referencia RTSP | `com/ucarmd/ShareLinkService.java:903` y `RemoteDisplayNative.java:45` | PROBABLE RTSP para esa ruta; no universal ni probado con AION |
| Samsung declara privilegios USB/Wi-Fi/audio/video y usa SemSystemProperties | AndroidManifest.xml y `com/samsung/android/carlink/common/util/u2.java:28` | CONFIRMADO estático; necesidad individual INCIERTA |
| Referencia CSC encontrada en utilidad CarLife | `com/samsung/android/carlink/carlife/util/c.java:66` | CONFIRMADO estático; no demuestra bloqueo regional ICCOA |

JADX no reconstruyó `ClientKeyNegotiator.agreeOnSessionKey`. El Smali conserva
la rutina (línea 149 en adelante), llamadas de parseo y derivación; queda pendiente
revisar cada rama. No interpretar el UnsupportedOperationException del Java
reconstruido como una excepción real de la aplicación original.

## Modelo provisional por evidencia

```text
Entrada ICCOA Samsung → coordinador f6.h → ShareLinkService
                                     ├─ BluetoothLeService (GATT)
                                     ├─ WifiP2pService
                                     ├─ CarAuthClient → negociación → confirmación
                                     └─ RemoteDisplayNative → heycastcore (display)
```

Este es un mapa de llamadas estáticas, NO un orden temporal de handshake del AION.
El callback de confirmación en `ShareLinkService.java:552` entrega el resultado a
otro callback y cierra el canal auth; falta determinar qué estado del receptor
constituye aceptación y cuándo se abren datos/control/medios.

## Identificadores observados, todavía sin validación AION

`com/share/connect/ble/c.java` asocia el UUID
`2abcc850-9935-4f8a-ba84-123456789100` con UnionShare y enumera características
de información cliente/servidor. Es una constante de la APK analizada, no un UUID
confirmado del vehículo. La aplicación propia no lo usa para filtrar o conectar.
No se deducen UUID por el nombre CarLink ni por pertenencia a una marca.

CarWith también contiene un negociador `com.miui.carlink.castfwk.negotiator.ClientKeyNegotiator`
y `com.miui.carlink.databus.proto.UCarProto`. Su extracción dirigida fallback terminó
con exit 0 tras el agotamiento de memoria del análisis completo. Es una pista para
comparar ambas implementaciones, no evidencia de que tengan la misma versión wire.

## Mensajes candidatos: no implementados

| Nombre en cliente | Dirección inferida | Evidencia | Estructura / respuesta | Estado |
|---|---|---|---|---|
| AuthRequest | teléfono → receptor | CarAuthClient.agreeOnSecurityKeys | Protobuf candidato; versión, material público, nonce y modo; espera AuthResponse | PROBABLE, sólo estático |
| AuthResponse | receptor → teléfono | ClientKeyNegotiator.parseServerInfo | Versión, material público, prueba de autenticación y resultado | PROBABLE, sólo estático |
| AuthConfirm | teléfono → receptor | CarAuthClient.confirmSessionKey | Campo cipher; callback de escritura, no prueba aceptación remota | PROBABLE, sólo estático |

Transporte socket candidato según canal AUTH; framing, puertos efectivos, longitudes,
tags completos, endian, temporización y respuesta del AION: DESCONOCIDOS.
No se han implementado codecs ni enviado mensajes para rellenar esta tabla.

## Autenticación e impedimentos

Hay autenticación de aplicación y negociación de claves separadas del emparejamiento
Bluetooth. El uso de claves generadas localmente hace razonable investigar un flujo
legítimo basado en consentimiento/PIN, pero no demuestra que el receptor admita
cualquier cliente ni que no exija identidad/registro de fabricante en otra capa.
No se extrajeron claves privadas, PIN reales o credenciales.

**Impedimento demostrado actualmente:** falta evidencia del receptor concreto,
framing y negociación verificados. **No está demostrado** que un certificado OEM
inaccesible sea obligatorio. Tampoco está demostrado que APIs públicas basten para
reemplazar todas las llamadas privilegiadas de Samsung. No recomendar modificar
CSC ni firmware basándose en estas referencias.

Próximo experimento: observación comparada de anuncios con ICCOA abierto/cerrado,
propietario presente y vehículo estacionado. Con una conexión legítima funcional,
captura HCI local acotada para relacionar GATT, Wi-Fi y canal de autenticación.
Sin enlace funcional: obtener versión del receptor y documentación/SDK del proveedor.

## Dependencias: clasificación inicial

| Dependencia | Samsung | CarWith | Evaluación |
|---|---|---|---|
| Bluetooth público | rutas GATT | castfwk/wireless/bt | Observado; obligatoriedad por variante INCIERTA |
| Wi-Fi P2P | servicio dedicado | ruta inalámbrica | Observado; rol y privilegios necesarios INCIERTOS |
| Framework OEM | APIs Samsung y permisos de sistema | permisos MIUI y servicios castfwk | Presente; obligatoriedad para cliente mínimo INCIERTA |
| Bixby / voz / mapa | integraciones presentes | integraciones propias | Candidatas OPCIONALES para cliente mínimo; no probado |
| JNI propietario | heycastcore | micastcore y otras | Presente; no reutilizable en implementación libre |
| Root / cambio CSC | no ejecutado | no ejecutado | NO REQUERIDO para las operaciones iniciales propias; sesión aún desconocida |
| Autenticación OEM | sin requisito confirmado | sin requisito confirmado | INCIERTA; no sustituir identidades |
