# 0.10: separar red y puerto de sesión

## Evidencia y corrección de interpretación

Los dos intentos reales de 0.9 confirman escritura y respuesta BLE, pero sólo
clasifican el fallo como PORT. No se conoce su valor o formato real.

Análisis estático de Samsung Auto 3.1.04.10, SHA-256
5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917:
`com/share/connect/ble/e.java` líneas 71 y 81 lee String y **también aplica
Integer.parseInt** antes del observer. La observación anterior de String no
implica que Samsung admita listas de puertos ni texto arbitrario.
`com/share/connect/security/CarVerifier.java` líneas 62–64 conserva el entero
en CarPropertyServerPort. Su valor inicial es cero. No demuestra que cero sea
el valor enviado por el AION ni autoriza usarlo como endpoint.

En nuestro código, P2pConnector y AccessPointRequest forman la red con parámetros
SSID/PSK/MAC/frecuencia y modo; no consumen port ni abren sockets. Exigir un
endpoint válido antes de esa operación independiente era un acoplamiento local.

## Implementación independiente

- parseForNetwork valida todos los parámetros de red con las mismas restricciones
  anteriores. Clasifica port por separado: VALID, MISSING, EMPTY, NON_INTEGER,
  OUT_OF_RANGE o UNSUPPORTED_TYPE. Sólo conserva un número entre 1 y 65535;
  para el resto conserva null, nunca un sustituto ni el contenido recibido.
- parse sigue siendo estricto para futuros consumidores que requieran endpoint.
  No se inicia autenticación ni socket con un puerto nulo. No se inventan mensajes,
  UUID, puertos, identidades o credenciales.
- BLE normal y READ usan el parser de red. La confirmación de escritura sigue
  siendo condición para entregar una respuesta del intento normal. El diagnóstico
  incluye modo y categoría de puerto, sin valor ni payload.
- Espera acotada en segundo plano durante solicitud P2P además de AP para no
  cancelar por la ventana Android. Timeout de red 45 s y limpieza en fondo 50 s;
  Detener y cierre conservan limpieza y protección contra callbacks tardíos.
- Los errores de inicio P2P distinguen BUSY, P2P_UNSUPPORTED y ERROR.

Hipótesis: con datos de red válidos, diferir PORT permitirá intentar Wi-Fi donde
0.9 abortaba. No garantiza formar red: todavía pueden fallar los demás campos,
la autorización Android o la interoperabilidad con el receptor. El experimento
de metadatos anonimizados conserva consentimiento explícito por intento.

## Siguiente prueba del propietario

Con vehículo estacionado y propietario presente: abrir ICCOA, buscar el candidato,
Preparar conexión normal, consentir el experimento e introducir el código actual.
Esperar el resultado, aceptar el diálogo Android correspondiente si aparece y
volver a AionLink antes de 50 s. Guardar diagnóstico al terminar; no hace falta
seguir introduciendo códigos si el mismo error se repite.

Medidas: escritura confirmada, SERVER_RECEIVED con categoría de puerto, modalidad
P2P/AP, solicitud Android y estado final CONNECTED/ERROR. CONNECTED sólo verifica
red; autenticación, sesión ICCOA y proyección continúan pendientes.

Pruebas automatizadas usan fixtures sintéticos y APIs locales del teléfono;
no sustituyen la prueba física ni demuestran aceptación del PIN.
