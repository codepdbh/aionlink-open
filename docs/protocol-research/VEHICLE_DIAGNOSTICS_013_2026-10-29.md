# Diagnóstico 0.13: canal TCP abierto y cierre BLE posterior

Archivo (18), 6802 bytes, SHA-256
`208417d632eee8aa0622c2334ecb2d5a80b202373a55ef0ffa049df107189d33`.
Recuperado por ADB y conservado en `private/vehicle-tests-2026-10-29-b/`;
no se publica el TXT. Horas locales America/La_Paz, 29 de octubre de 2026.

El proceso registra dos intentos:

1. ClientInfo de 139 bytes con escritura confirmada; respuesta BLE de 123 bytes,
   P2P, port ZERO. Red CONNECTED 13:49:38.984105. Comprobación explícita TCP
   AUTH `OPEN; ruta=P2P_SOURCE` a las 13:49:50.522765, duración 35 ms. No se
   enviaron datos de aplicación. La red se perdió a las 13:49:57.812695,
   18,828590 s después de CONNECTED. Causa no registrada.
2. Escritura ClientInfo confirmada a las 13:52:35.710437; error GATT 19 a las
   13:52:40.863986 antes de respuesta ServerInfo. No hubo red ni prueba TCP en
   este intento. El estado GATT no prueba que el PIN fuese incorrecto.

La comprobación TCP se dirigió a la IP del propietario del grupo reportada por
Android y se vinculó a una dirección local de la interfaz del grupo. El sistema
informa conexión abierta; no prueba identidad criptográfica, autenticación o
aceptación ICCOA. El bind de dirección de origen no garantiza por sí solo la
interfaz de salida ante rutas ambiguas. No se puede atribuir la desconexión a la
comprobación TCP ni a ausencia de autenticación sin más evidencia.

El resumen de 0.13 muestra únicamente IDLE de AUTH en el segundo intento aunque
el evento OPEN del primero sigue en el historial. La 0.14 conserva adicionalmente
contador y último resultado AUTH por proceso en el resumen exportado. También
registra etapa previa, estado Android y confirmación de escritura al fallar GATT,
sin inventar causa. No se persiste el historial entre procesos.

## Laboratorio de autenticación

Se añadió una negociación normal **offline**, con claves P-256 efímeras propias,
prueba HMAC y firma de solicitud, y verificación de la prueba y firma de respuesta.
Los tests usan un peer sintético independiente y rechazan PIN simulado equivocado,
firma alterada y campos faltantes. No se conecta al canal del coche, no deriva ni
confirma clave de sesión y no habilita proyección. Se borra la copia del PIN al
cerrar/consumir intento; el llamador debe borrar su propio buffer. El resultado
de verificar prueba y firma no equivale a una sesión autenticada completa.

Para integrar con el AION faltan correlación real y prueba integral de transporte,
ciclo de vida del PIN, derivación/confirmación, negociación de control y medios.
No se ejecutaron APK propietarias, usaron claves privadas ajenas ni modificó el
vehículo. No se solicitó una nueva prueba de coche por el componente offline.
