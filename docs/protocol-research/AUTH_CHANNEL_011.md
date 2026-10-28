# Investigación estática de la fase posterior a Wi-Fi — 0.11

Las observaciones siguientes provienen exclusivamente del análisis estático local
de Samsung Auto 3.1.04.10, SHA-256
`5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917`.
No son una captura de la comunicación del AION ni una especificación pública.

- `com/ucarmd/b.java`, callback `onConnected`, crea CarAuthClient con la
  dirección que recibe del grupo cuando el teléfono no es el propietario.
  `S4/h.java` obtiene el address del propietario de WifiP2pInfo.
- `CarAuthClient.java` crea un canal de tipo AUTH y llama `start(0, dirección)`.
  `ChannelType.java` asigna 57209 a AUTH y `SocketChannel.java`, métodos
  `getServerPort` y `start`, usa el puerto del tipo para canales no CUSTOM.
  En esta ruta estática el puerto de AUTH es independiente del campo `port`
  de ServerInfo; ese campo se entrega a CarVerifier como CarPropertyServerPort.
  Esto no demuestra que la unidad AION exponga ese canal ni establece un socket.
- `UCarAuthProtocol.java` asigna a AUTH los métodos 1, 2 y 3 para solicitud,
  respuesta y confirmación respectivamente. Los cuerpos son protobuf. Los detalles
  del framing y de cada rama criptográfica deben verificarse antes de enviar.
- `ClientKeyNegotiator.java` describe modo normal con claves EC generadas por
  el cliente, nonce, HMAC basado en PIN y firma. La respuesta debe verificarse
  antes de derivar una clave de sesión y emitir confirmación. La reconstrucción
  Java del método que procesa esa respuesta está incompleta; no equivale a un
  método fallido del APK. No se accedió a claves privadas ni se copiaron fuentes.

## Implicaciones para el AION

Los diagnósticos 0.10 muestran grupo P2P, pero no petición/respuesta AUTH ni
proyección. Es plausible que la falta de la fase siguiente contribuya al corte
del primer intento, pero se desconoce la causa y el receptor podría exigir
otras condiciones. La categoría OUT_OF_RANGE del puerto de ServerInfo no
demuestra que el puerto AUTH sea inválido ni cuál fue el número recibido.

La 0.11 no abre sockets ni envía mensajes AUTH: faltan reconstrucción completa,
verificación de identidad del receptor, codificación y pruebas con fixtures
independientes. El siguiente avance debe implementar autenticación verificable
sin sustituir comprobaciones ni considerar un callback de escritura como
aceptación remota. Una conexión TCP por sí sola tampoco sería sesión ICCOA.

## Cambios aplicados

Se mantiene el enlace BLE activo o la red P2P/AP en conexión o conectada durante
un cambio de Activity, hasta 50 segundos en segundo plano. Volver cancela el
temporizador; Detener y el vencimiento liberan recursos. Android puede finalizar
el proceso en segundo plano: no hay servicio persistente. Se añade categoría
segura ZERO/NEGATIVE/ABOVE_MAX/OVERFLOW para puertos fuera de rango, sin exportar
valor. Estos ajustes no implementan autenticación ni prometen evitar un corte
originado por el receptor.
