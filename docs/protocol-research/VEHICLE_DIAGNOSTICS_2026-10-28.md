# Pruebas del propietario del 27 de octubre: primer intercambio confirmado

Revisión: 2026-10-28, America/La_Paz. Dos TXT nuevos de AionLink 0.9
recuperados por ADB 37.0.0 del teléfono autorizado. Originales conservados;
copias en `private/vehicle-tests-2026-10-28/`, excluidas de Git.

| Exportación | Bytes | SHA-256 |
|---|---:|---|
| (12) | 4231 | b96fa25d0c92dcd6b2c45b13be72eae4af6a04a3b37d4a6f018fcb99c66e0a6c |
| (13) | 6387 | 341d6db26073fec7cd8cd98b81789b4360250bcffa0541d6def42ee3f18bcb30 |

Ambos son snapshots del mismo proceso; el segundo contiene el historial del
primero. Son **dos intentos**, no tres. Las horas siguientes son locales del
27 de octubre (UTC menos cuatro horas).

| Intento | Escritura confirmada | Respuesta / fallo |
|---|---|---|
| 1 | 22:56:56.478; 139 bytes | 22:56:57.903; 123 bytes, parser PORT |
| 2 | 22:57:34.340; 139 bytes | 22:57:35.653; 123 bytes, parser PORT |

## Hechos

- Ambos usaron consentimiento explícito y metadatos locales anonimizados por
  Android. MTU 512; callback confirma escritura ClientInfo.
- Llegaron respuestas aproximadamente 1,42 y 1,31 segundos después del ACK.
- El cliente rechazó ambas por `ServerInfoFault.PORT` y canceló la preparación.
  No llegó a solicitar conexión Wi-Fi. No fue cancelación por exportación.
- USB: alimentación presente, cero accesorios AOA y cero bytes.
- Las fotos aportadas muestran el modelo SM-S938B, progreso 30% y un error
  genérico de conexión inalámbrica. No permiten asignar porcentaje a una fase
  concreta del protocolo ni atribuir la pantalla exclusivamente a nuestra app.

## Interpretación y límite

Es el primer envío y respuesta BLE confirmados en estos diagnósticos. El retorno
anonimizado no impidió recibir esa respuesta inicial; no demuestra que sirva para
las fases siguientes ni aceptación del PIN/autenticación. No hay red, sesión
ICCOA autenticada o proyección verificadas.

El parser propio convierte `port` a entero y exige 1..65535. PORT puede significar
tipo/contenido no convertible o valor fuera del rango. Los TXT omiten el valor:
no puede afirmarse que fuera cero, vacío, una lista u otro formato. La conversión
ocurre antes de las validaciones finales de SSID/PSK/MAC/frecuencia: tampoco
puede afirmarse que esos parámetros hayan pasado todas las comprobaciones.

Análisis estático local de Samsung Auto 3.1.04.10 (SHA-256
5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917):
`com/share/connect/ble/e.java`, línea 71, lee port como String y lo pasa a sus
observadores. Esto justifica investigar consumidores y formato antes de imponer
un entero único; no proporciona el valor enviado por este AION. No se copiaron
fuentes ni se ejecutó la APK de investigación.

Siguiente trabajo: determinar semántica de port mediante sus consumidores
estáticos y mejorar el diagnóstico estructural sin exportar credenciales.
No sustituirlo por un puerto inventado ni llamar autenticación al intercambio.
Revisión documental: no se cambió ni reinstaló la app, ni se hicieron conexiones
remotas al coche. No corresponde repetir tests de código sin cambios.
