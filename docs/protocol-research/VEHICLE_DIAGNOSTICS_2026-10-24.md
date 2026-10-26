# Diagnósticos revisados el 26 de octubre — versión 0.6

## Procedencia y resultado

Se obtuvieron por ADB las exportaciones Download/aionlink-diagnostics (3).txt y
(4).txt del teléfono autorizado. Copias locales privadas:
private/vehicle-tests-2026-10-26/diagnostic-4.txt y diagnostic-5.txt.

| Archivo local | Bytes | SHA-256 |
|---|---:|---|
| diagnostic-4.txt | 2647 | `2816332e269713f6f8ed8449116c2c770ddb1c857b8ef8aa50af22e71f7867fa` |
| diagnostic-5.txt | 2735 | `db82f78ad15453def6974d26b4c90c42a9c83c7851d282e5d6de3880da4eb8c9` |

Los dos archivos muestran el mismo intento. El segundo añade el evento de
exportación del primero; no acredita una segunda conexión. Las marcas UTC son
del 24 de octubre, equivalentes a 15:54–15:55 hora boliviana. Se respeta esa fecha
registrada sin convertir la referencia informal del propietario en otra fecha.

Secuencia registrada:

1. Descubrimiento filtrado: un candidato FCFB conectable, RSSI final −64 dBm.
2. A las 19:55:00.664 UTC se solicita preparar el enlace normal.
3. Android oculta la dirección P2P local. Nuestra 0.6 cambia a consulta READ sin
   ClientInfo ni PIN, aun si el propietario ya introdujo el código.
4. BLE conecta, negocia MTU 512 y llega a READING_SERVER a las 19:55:00.944 UTC.
5. CANCELLED a las 19:55:25.403 UTC: unos 24,46 s después de iniciar la lectura,
   antes del timeout global de 30 s. Cero bytes de escritura o respuesta registrados.
6. Evento de exportación a las 19:55:26.113 UTC.

No hay confirmación de escritura, recepción válida ServerInfo, formación P2P,
intento de autenticación ni rechazo del código. USB final: IDLE, cero accesorios,
alimentación USB=false y cero tráfico. Esto es un snapshot, no historial de cableado.

## Causa respaldada y límites

El código introducido no se envió: la rama READ de 0.6 no incluye ClientInfo.
La aplicación solicitaba el PIN antes de conocer la elegibilidad y cambiaba de
ruta sin exigir una elección separada. Fue un defecto de flujo de la aplicación.

La consulta no obtuvo respuesta antes de cancelarse. No puede afirmarse que el
receptor hubiera rechazado READ ni que el timeout de 30 s hubiera vencido.
onStop de MainActivity cancelaba los transportes sin registrar motivo específico.
Abrir el selector de archivos puede producir onStop, y export() se evaluaba al
volver del selector. La proximidad temporal respalda esa explicación como
hipótesis, pero los TXT no distinguen selector, cambio de app o botón Detener.

La limitación de identidad local está documentada por Android en requestDeviceInfo.
No se resuelve copiando el código Samsung/Xiaomi: el método M de
com/miui/carlink/castfwk/wireless/bt/t.java también incorpora y valida metadatos
locales antes de generar ClientInfo. No se encontró aquí una ruta comprobada
que permita omitir ese dato en este receptor. No se añadieron permisos
privilegiados, root, MAC inventadas ni métodos privados del sistema.

## Correcciones 0.7

- Preflight antes de abrir el formulario PIN. Si falta identidad P2P utilizable,
  la conexión normal queda bloqueada con explicación visible; no cambia a READ.
- Lectura experimental como acción separada, confirmada para el vehículo propio.
  Plazo de lectura de 15 s y resultado explícito sin afirmar rechazo de autenticación.
- Diagnóstico congelado antes del selector, con hora de snapshot y número de intento;
  cancelación con motivo y etapa anterior, modo READ/NORMAL y escritura confirmada.
- Se retira la cancelación desde DisposableEffect, que también podía ejecutarse
  por recomposición/desmontaje. Se conserva la limpieza explícita de Activity y
  ViewModel, con motivo, y la caducidad de preparación PIN a 60 s.
- Se borra la referencia preparada al cancelar y se valida que la red siga READY
  antes de aceptar el código. No se mantienen PIN en preferencias o exportaciones.

Estas correcciones evitan repetir un flujo engañoso y mejoran la evidencia. **No
resuelven el acceso restringido a la MAC local ni completan autenticación o
proyección.** La ruta de red sigue sin validación con el AION. No se realizaron
conexiones de prueba desde el ordenador durante esta revisión.
