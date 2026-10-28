# Primitivas de autenticación: investigación y pruebas offline

Fuente estática local: Samsung Auto 3.1.04.10, SHA-256
`5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917`.
`KeyNegotiator.java`, `ClientKeyNegotiator.java` y Smali de este último en
`smali_classes2` permiten identificar componentes de la negociación NORMAL.
No se copió código propietario ni se extrajeron claves, PIN o credenciales.

La construcción inicial observada autentica la clave pública del cliente usando
HMAC-SHA-256 con una clave derivada por SHA-256 de nonce y PIN; el mensaje
abarca clave pública y nonce. En la rama normal de respuesta, el Smali combina
ambas claves de autenticación y ambos nonce para comprobar el HMAC del peer.
Después comprueba la firma de la clave de acuerdo del peer junto a la clave
de acuerdo local y los dos nonce. Sólo después intenta ECDH y guardar el peer.
El orden exacto de concatenación se expresa en el código independiente y en
las pruebas; no se presupone compatibilidad con el firmware del AION.

`ExperimentalAuthCrypto` implementa únicamente esas primitivas: validación de
un punto P-256 X||Y, HMAC inicial, verificación constante de HMAC del peer y
comprobación de firma ECDSA del acuerdo. Acepta datos como parámetros y no
almacena PIN ni claves privadas. Los buffers internos derivados se limpian;
el propietario del buffer de PIN debe borrarlo. No está conectado a BLE, red,
UI ni al vehículo. No crea un usuario OEM ni simula autorización.

Las pruebas incluyen dos vectores HMAC sintéticos calculados independientemente
con `hashlib`/`hmac` de Python 3.14.2, generación de claves propias de prueba,
verificación y alteración de firmas, punto fuera de curva y longitud incorrecta.
Otra prueba ejecuta las primitivas con los proveedores JCA del S25. Son pruebas
de unidades, no una sesión ICCOA o evidencia de recepción del vehículo.

Siguen pendientes la codificación y encuadre interoperables, correlación de
solicitudes, adquisición y manejo seguro de identidad y PIN por intento,
derivación y confirmación completas, verificación del resultado del receptor,
transporte ligado a la red seleccionada, y control/medios. Los componentes no
deben conectarse a un socket mientras estas comprobaciones no estén completas;
el puerto candidato de AUTH no demuestra disponibilidad en este AION.
