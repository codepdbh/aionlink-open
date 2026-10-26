# Ocho exportaciones revisadas — 2026-10-26

## Procedencia

Se copiaron los ocho TXT aionlink-diagnostics de Download mediante el ADB ya
autorizado. Copias privadas: private/vehicle-tests-2026-10-26-b/diagnostic-1.txt
hasta diagnostic-8.txt. Los cinco primeros mantienen los hashes de revisiones
anteriores. Versiones: una 0.1, una 0.4, una 0.5, dos 0.6 y tres 0.7.
No se publican los TXT. Los tres nuevos son:

| Archivo local | Bytes | SHA-256 |
|---|---:|---|
| diagnostic-6.txt | 6788 | `2b893e025fa5dcde8ba1a17384c0e20dfa3d8ad2cd8dec4258b4b1360359716e` |
| diagnostic-7.txt | 11255 | `59dd355b6748243bd696f258ec79f08c266e439a34f929a1b7ee252bd23e52f2` |
| diagnostic-8.txt | 10107 | `e22a1ff266c53a3fcaef495f57ec3a6d381b70f2a74111a7449ab9cf855597de` |

Los snapshots son del 26 de octubre, 13:45:18, 13:48:50 y 13:49:45 hora boliviana.
Al combinar eventos se deduplicaron timestamp y mensaje: hay 191 eventos únicos
visibles entre los tres archivos. Los archivos comparten historial; no se suman
como intentos independientes. El límite anterior de 100 eventos por exportación
impide acreditar que el historial visible abarque todo lo sucedido.

## Observaciones nuevas

- 20 solicitudes normales y 20 bloqueos por dirección local P2P anonimizada.
  No alcanzan ClientInfo ni el formulario PIN de 0.7. Cero escrituras confirmadas.
- Dos consultas READ ServerInfo sin respuesta registrada. Una cancelada por
  Detener a los 13,39 s; otra por Activity en segundo plano a los 8,78 s. Ninguna
  acredita que venciera el timeout de lectura de 15 segundos.
- Tres inspecciones correctas, cada una con cinco servicios y UnionShare presente.
  Se repiten ClientInfo WRITE=8 y ServerInfo READ/NOTIFY=18.
- Un error GATT 133 anterior a las inspecciones correctas. No demuestra rechazo
  del PIN, bloqueo regional ni incompatibilidad permanente.
- Alimentación USB=true en los primeros snapshots nuevos y false en el último;
  accesorios AOA=0 y tráfico=0. No se inició un canal USB de sesión.

El propietario informa de una clave en ICCOA. El registro de nuestra app no
acredita haber solicitado esa clave al receptor, recibido su valor o enviado un
PIN. El formulario ausente se explica por la condición deliberada de 0.7: sólo
se mostraba si requestDeviceInfo entregaba una dirección local utilizable.

## Investigación de datos locales por ADB

Se evaluaron sólo accesos de lectura/diagnóstico, sin modificar el sistema:

- cmd wifip2p help: SecurityException para UID shell 2000; exige acceso root.
  No se intentó root ni se reintentaron comandos de control con privilegios.
- dumpsys wifip2p con filtro ejecutado en el teléfono para emitir únicamente la
  dirección del dispositivo local: no se encontró ese campo. Conteo posterior:
  146 líneas, cero marcadores de dispositivo local y cero denegaciones indicadas.
  No se imprimieron ni guardaron configuraciones de grupo o credenciales.
- ip -o link show dev p2p0: denegación de permiso. No se intentó elevar permisos.

Estos resultados no proporcionan un valor real con el que completar ClientInfo.
No se añadió una dirección falsa, una interfaz oculta ni un permiso privilegiado.
La ruta normal sigue bloqueada por ese requisito.

## Corrección 0.8 y límites

Se muestra el diálogo al elegir preparar la conexión. El campo permite introducir
los seis dígitos incluso si el preflight falla; el propio diálogo muestra el
requisito pendiente y mantiene Enviar deshabilitado. El texto distingue clave
introducida en memoria de clave transmitida. Cerrar elimina la entrada y cancela
también un preflight en curso, para que un callback tardío no reabra el formulario.

El PIN sigue siendo efímero: no se guarda/restaura, no se exporta y nunca se
sustituye silenciosamente por una lectura. Las nuevas pruebas de interfaz usan
un código ficticio y callbacks locales, sin conectar Bluetooth o Wi-Fi.

El historial exportable pasa de 100 a 500 eventos e informa cuántos descartó;
la pantalla sólo renderiza los 20 últimos. Se incluye ID aleatorio del proceso
para distinguir reinicios y números de intento repetidos.

Esto corrige el campo ausente y mejora las pruebas, **no elimina el requisito de
identidad P2P ni completa la autenticación/proyección**. No se debe solicitar al
propietario repetir el ingreso de clave como si ese bloqueo estuviera resuelto.
