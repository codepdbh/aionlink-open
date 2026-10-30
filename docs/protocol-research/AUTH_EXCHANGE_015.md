# 0.15: solicitud AUTH experimental al receptor

El diagnóstico (19), de 7952 bytes, SHA-256
`6faf90284d5447679f63758aaa92bc32d96591bd936ea63e221eec19a0e4585a`,
se conservó en `private/vehicle-tests-2026-10-30/`. Dos intentos 0.14
del 29 de octubre, hora de La Paz: ambos confirmaron ClientInfo, respuesta
BLE, port ZERO y grupo P2P; el primero abrió TCP AUTH por P2P_SOURCE en 59 ms,
sin enviar datos. La red duró 18,890490 s en el primer intento y 18,776853 s
en el segundo. Causas de pérdida desconocidas; ni TCP ni P2P son sesión ICCOA.

## Lo implementado

En 0.15, cuando el grupo P2P seleccionado esté conectado, el propietario puede
elegir **Intentar AUTH experimental**, volver a introducir el código actual y
confirmar. El cliente abre sólo el puerto AUTH observado en Samsung/Xiaomi y
envía una solicitud candidata con claves efímeras propias, HMAC y firma. Se usa
la identidad propia ya transmitida en ClientInfo. El request declara workModes=0
porque AionLink aún no implementa modos de proyección; el receptor puede rechazarlo.
La solicitud no suplanta un fabricante ni inventa un modo compatible.

Se lee una respuesta UCar limitada a 4096 bytes, se comprueban CRC, origen,
tipo, método y secuencia, y se verifican HMAC y firma con el código introducido.
El código y buffers de solicitud/respuesta se limpian al terminar; la UI Android
usa un String transitorio que no es posible borrar de forma garantizada. No se
exportan PIN, direcciones, claves, nonce, mensajes ni contenido de errores.
Hay plazo total de ocho segundos y cierre al detener o perder el grupo. La prueba
es manual, de un solo intento, puede fallar o provocar desconexión, y requiere
propietario presente y coche estacionado. Sólo se ejecuta en grupo P2P validado.

`RESPONSE_VERIFIED_PROOF_AND_SIGNATURE` indica únicamente que esas dos pruebas
pasaron para la respuesta; no se ha derivado o confirmado una sesión, ni se ha
demostrado proyección. Otros resultados distinguen error de ruta/conexión,
timeout, trama inválida, respuesta incompleta, resultado/versión, prueba o firma.
No interpretar esos errores como causa segura ni como PIN rechazado sin evidencia.
No se implementan ECDH/confirmación ni canales de vídeo/control todavía.

## Ensayo local

Servidor loopback propio con claves generadas para la prueba valida HMAC y firma
de la solicitud, responde con protobuf producido por la biblioteca Google y
construye prueba y firma propias. Se comprueba respuesta correcta, PIN simulado
incorrecto, bind fallido y borrado de buffers. No son capturas del AION ni prueba
del protocolo real. La verificación de la respuesta del coche queda pendiente.
