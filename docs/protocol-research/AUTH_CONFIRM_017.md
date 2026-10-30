# 0.17: correcciones de solicitud AUTH y confirmación observada

Análisis estático del 30 de octubre de 2026 sobre Samsung Auto 3.1.04.10
(`5633d2f9…`) y contraste con Xiaomi CarWith 4.0.14 (`c7888ec5…`). No se
ejecutaron APK de terceros ni se copiaron fuentes, bibliotecas o secretos.
Rutas relativas a `analysis/java/<extracción>/sources/` o `extracted/` (locales).

## Contexto

Los diagnósticos (16)–(20) muestran el mismo patrón: ClientInfo confirmado,
ServerInfo P2P, grupo conectado y pérdida de red a los ~18,8 s. El AUTH
experimental (0.15/0.16) aún no se había probado en el coche. Hipótesis (no
verificada): el receptor espera AUTH completo dentro de esa ventana y abandona.

## Defectos de la solicitud 0.15/0.16 frente a la evidencia

| Campo | 0.16 | Evidencia estática | Riesgo |
|---|---|---|---|
| Secuencia | entero aleatorio de 32 bits | `com/ucar/protocol/SequenceGenerator.java`: contador desde 1; `MessageHeader.java:239-247` rechaza `seqId <= 0` al fijar la secuencia de una respuesta | ~50 % de intentos con secuencia negativa como `int`; el receptor no podría construir la respuesta |
| workModes | 0 | `com/ucarmd/ShareLinkService.java:159` construye NORMAL_CAST (1) + MIRROR_WINDOW_CAST (256) = 257; `KeyNegotiator` define resultado 4 = WORK_MODE_NEGOTIATION_ERROR | respuesta con resultado 4 |
| Tras la respuesta | se cerraba el socket | `CarAuthClient.java:confirmSessionKey` envía AuthConfirm antes de cerrar | el receptor no recibía confirmación |

0.17 usa secuencia 1 (solicitud) y 2 (confirmación) y workModes 257. AionLink
todavía no implementa esos modos de proyección; el valor sólo reproduce lo que
el receptor espera negociar, y la siguiente etapa sigue pendiente.

## Derivación de sesión observada

Samsung `ClientKeyNegotiator.smali` (`agreeOnSessionKey`) y
`KeyNegotiator.java:agreeOnSessionKey`, con `X5/E.java:k`:

- ECDH P-256 entre la clave de acuerdo propia y la del receptor (ya verificada
  por firma).
- HKDF-SHA256 estándar (RFC 5869): IKM = secreto ECDH, salt =
  SHA-256(nonce local ‖ nonce receptor), info = `"session_key"`, 16 bytes.
- Samsung asigna la misma clave a los alias `session_key` y `uibc_session_key`.

Implementación propia en `ExperimentalAuthCrypto.hkdfSha256`, contrastada con
el caso 1 del RFC 5869. La prueba loopback deriva la clave del lado del par con
código de test independiente.

## AuthConfirm

- `CarAuthClient.java:confirmSessionKey(str, 0, …)` cifra UTF-8 `"%s:%d"`;
  `com/ucarmd/b.java` y `wifip2p/WifiP2pService.java` muestran que `str` es la
  IPv4 local del teléfono en el grupo y el entero es 0. Xiaomi
  `castfwk/negotiator/a.java:393` usa el mismo formato.
- `SecurityManager.java:encrypt`: AES-GCM, etiqueta 128 bits,
  `ivLen(BE32) ‖ iv ‖ cipherLen(BE32) ‖ cifrado+tag`. 0.17 usa IV de 12 bytes.
- `UCarAuthProtocol.java`: tipo SEND (0), categoría AUTH (3), método 3; campo
  protobuf 1 `cipher`. Cabecera propia: flags `0x03` (origen teléfono, PB3, SEND).
- `SocketChannel.java:onMessage` sólo envía ACK a SEND_SYNC; la respuesta AUTH
  (RES) no requiere ACK.

Tras enviar la confirmación, AionLink observa hasta 1,5 s sólo la cabecera de
una posible trama siguiente (categoría, método, tipo, origen) y cierra, como
Samsung tras `onSessionKeyConfirmed`. No se exporta payload, clave ni dirección.

## Lo que sigue (no implementado)

Samsung, tras la confirmación, llama `ShareLinkService.f` → `RemoteDisplayNative`
y `Q5/e.java:a`, que arranca el canal de control "NegotiationChannelV2"
(constante CONTROL 57219), UIBC y la sesión de display nativa (`heycastcore`).
Hipótesis pendiente de evidencia: `start(0, ipLocal)` en ese canal sugiere un
papel distinto al del canal AUTH; no se ha determinado quién escucha. Medios,
control y proyección siguen sin implementar.

## Cómo interpretar el resultado de la prueba

- `RESPONSE_BAD_VERSION_OR_RESULT; resultado=4`: modos rechazados.
- `RESPONSE_BAD_PROOF`: código/PIN distinto o derivación HMAC divergente.
- `RESPONSE_VERIFIED_PROOF_AND_SIGNATURE; … CONFIRM_SENT; tras confirmación=…`:
  la confirmación salió del teléfono. `CERRADO_POR_RECEPTOR`, `SIN_DATOS_1500MS`
  o `FRAME …` describen lo observado después; ninguno demuestra por sí solo
  sesión aceptada. Comparar con el porcentaje/estado en pantalla del coche y la
  duración del grupo P2P (antes ~18,8 s).
