# Comparación de conexión: Samsung, Xiaomi y CarLife

Investigación estática del 28 de octubre de 2026; no se ejecutaron aplicaciones
de terceros, ni se accedió a secretos. Las observaciones del AION proceden de
los diagnósticos 0.10 del propietario, no de estas APK. El Java reconstruido es
incompleto; Xiaomi agotó 2 GiB en JADX y se examinó la salida parcial y Smali
cuando correspondía. Los archivos y resultados privados permanecen fuera de Git.

## Fuentes y alcance

| Fuente | Identidad / SHA-256 | Alcance |
|---|---|---|
| Samsung Auto | 3.1.04.10 / `5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917` | Cliente telefónico ICCOA y CarLife, clases Java/Smali disponibles |
| Xiaomi CarWith | 4.0.14 / `c7888ec5018b4e279ed3d3395b8c855d2a3571f06ae26b96849a61ad6dbef00e` | Cliente ICCOA, Java parcial y Smali |
| CarLife receptor | 4.0.0 / `6cc4287f01516abe895f47b2876d657a5131b35c3c7024def85b698a9be5456e` | Receptor de otra familia, análisis Java parcial |
| CarLife componente | 8.6.1 / `21bee403961de66d3e40d610b2c0d2101d75874e251ef75ba4314daab0e54902` | Plugin Samsung CarLife; no receptor AION |
| OPPO CarLink | 16.4.2 / `8e162d53eb6bc60a713224cb1b888edc8b66354149470e3d5b26b203543cc6f9` | Análisis parcial; las clases de llave/control no son evidencia de proyección |

Rutas relativas bajo los directorios de extracción por hash de `analysis/java/`
o `extracted/`; ver `analysis/reports/APK_ANALYSIS_REPORT.md` para herramienta,
errores y nombres completos locales. Son APK del usuario, no fuentes publicadas.

## Orden y separación de protocolos

1. **BLE:** Samsung y Xiaomi construyen información de cliente y esperan datos
   de red; véase `com/share/connect/ble/e.java` y
   `com/miui/carlink/castfwk/wireless/bt/t.java`. Nuestro AION respondió a BLE.
2. **Red:** Samsung `S4/h.java` obtiene la IP del propietario de Wi-Fi Direct;
   `com/ucarmd/b.java:onConnected` inicia CarAuthClient para el teléfono como
   cliente del grupo. Tres grupos coincidentes se formaron en pruebas 0.10.
3. **AUTH:** Samsung `CarAuthClient.java`, `UCarAuthProtocol.java` y Xiaomi
   `castfwk/negotiator/a.java`, `ClientKeyNegotiator.java` usan canal AUTH,
   solicitud/respuesta/confirmación y claves negociadas. Ambos enumeran AUTH
   57209; no se ha comprobado ese puerto en el AION. El campo `port` de ServerInfo
   se pasa al verificador de propiedades en Samsung, no a ese selector de canal.
4. **Después:** ambos contienen canales de control, medios y sensores; hay
   constantes comunes y también diferencias. La escritura local de AuthConfirm
   es un callback de envío, no prueba por sí misma aceptación por el receptor.
5. **Display:** Samsung `RemoteDisplayNative.java` carga `heycastcore`; Xiaomi
   `CastController.java` carga `micastcore` y utiliza display virtual. Sus
   detalles nativos no quedaron explicados por esas referencias. No se pueden
   copiar esas bibliotecas ni inferir que un display virtual aislado entregue
   vídeo compatible. Aún se necesita negociación de medios, vídeo, audio y entrada.

CarLife es otra familia: `d/b/a/a/o/i/f/c.java` lee/escribe sus propios frames,
`d/b/a/a/o/h/a.java` maneja cabeceras según tipo de canal y
`d/b/a/a/p/o/e/c.java` enumera sus propios puertos. Los protobuf CarLife incluyen
mensajes de versión, autenticación, vídeo y entrada. Esto demuestra arquitectura
de conexión de esa muestra, no que el menú ICCOA/Huawei del AION acepte CarLife.
El proyecto público [MiAuto](https://github.com/PiggyBackBayonet/MiAuto) es un
receptor para el coche que depende de un SDK ICCOA precompilado no incluido en
Git; no es un cliente telefónico independiente para este S25.

## Autenticación: coincidencias y diferencia importante

Samsung `KeyNegotiator.java:145,216` y Xiaomi `t9/e.java:164,242` coinciden en
que el HMAC inicial utiliza SHA-256(nonce || PIN) como clave y **sólo la clave
pública de autenticación** como mensaje. En respuesta normal, el mensaje es
clave pública del peer || clave pública local; la derivación usa nonce del peer
|| nonce local || PIN. La firma de respuesta incluye ambas claves de acuerdo y
ambos nonce; secuencia observada en Smali Samsung `ClientKeyNegotiator`, método
`agreeOnSessionKey`. Se corrige un error anterior de AionLink que agregaba los
nonce también al mensaje HMAC. Era código offline y no se envió al coche.

Ambos negociadores usan P-256, ECDSA, ECDH y versión 1793 en las rutas
examinadas. Xiaomi `t9/e.java:a` llama a su helper HKDF-SHA256 (`y7/e.java`) con
secreto ECDH, hash de los nonce y etiqueta de propósito para generar 16 bytes;
Samsung `KeyNegotiator.java:89` usa una llamada equivalente candidata. Samsung
asigna su clave resultante a dos alias, mientras Xiaomi en la ruta inspeccionada
calcula por separado una derivación con otra etiqueta: no se concluye que todos
los canales o versiones sean intercambiables. Samsung `SecurityManager.java`
usa AES-GCM con campos de longitud e IV; los detalles y límites deben probarse
antes de interoperar. Ninguna clave privada o secreto de una APK fue extraído.

## Bloqueos concretos de AionLink

- El PIN del formulario se usa para ClientInfo y no queda disponible para la
  negociación posterior; falta ciclo de vida acotado y borrado tras éxito/error.
- El conector P2P comprueba grupo y propietario pero no entrega todavía al
  cliente AUTH la dirección del propietario; AP necesita conservar el `Network`
  y usar una dirección del receptor validada. Sin dirección verificada no se
  debe abrir conexión a un destino supuesto.
- El lector UCar es offline; faltan encoder, protobuf AUTH propio basado en tags
  corroborados, correlación de secuencia, timeouts y pruebas con servidor simulado.
- Faltan claves propias, derivación de sesión y cifrado confirmados, gestión de
  identidad y rechazo ante HMAC/firma inválidos, cierre limpio y pruebas integradas.
- No hay implementación de medios/control. Android requiere consentimiento por
  sesión y servicio apropiado para captura de pantalla:
  [MediaProjection](https://developer.android.com/media/grow/media-projection).
  Si se dispone de un `Network` AP, Android permite vincular un socket concreto
  sin cambiar la red global: [Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.Socket)).

## Orden de desarrollo y criterios de éxito

1. Codec AUTH y framing independientes con fixtures sintéticos y comprobación de
   campos/protobuf frente a ambas muestras; sin enviar tráfico real.
2. Negociador normal completo con claves propias y pruebas de validación,
   manipulación, rechazo, tiempos y borrado del PIN. Nunca omitir comprobaciones.
3. Servidor de prueba local independiente que verifique ambos extremos, incluidos
   fragmentación, respuesta y limpieza. Éxito local no demuestra compatibilidad.
4. Integrar sólo con grupo verificado, dirección de propietario obtenida de Android,
   timeout/cancelación y diagnóstico sin datos privados. Primer ensayo físico por
   el propietario presente y vehículo estacionado; registrar conexión AUTH,
   respuesta verificable y resultado sin declarar sesión por TCP o envío.
5. Tras verificar sesión, investigar negociación de control y medios, captura con
   consentimiento, codificación de vídeo/audio e interacción; probar por separado.

[ICCOA publica la descripción del estándar](https://www.iccoa.cn/tech/68.html)
y [Baidu describe acceso a documentos/SDK para CarLife](https://carlife.baidu.com/carlife/caroem/start);
ninguna de esas páginas es una especificación wire descargada o confirmación de
compatibilidad con el AION. El producto no está completo y no se pidió una nueva
prueba de coche por correcciones que permanecen offline.
