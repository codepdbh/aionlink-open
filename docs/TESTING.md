# Pruebas y observación

Pruebas automatizadas independientes de hardware:

```powershell
python -m unittest discover -s tests/unit -v
cd android
./gradlew.bat :core:test :protocol-iccoa:test :app:assembleDebug :app:lintDebug --no-daemon --console plain
```

Las pruebas Python usan archivos sintéticos y cubren inventario ZIP, manifiesto y
rechazo de archivos corruptos. No contienen mensajes ICCOA fabricados.
Desde 0.2 también validan clasificación de logs sin revelar su contenido y resumen
acotado de permisos. El módulo ICCOA tiene 9 pruebas con cabeceras explícitamente
sintéticas y vector CRC estándar; no validan un intercambio con el AION.
Las pruebas JVM verifican transición inválida, descubrimiento sin sesión,
negociación no soportada, timeout y fallo de transporte. Sus adaptadores son dobles
de prueba del coordinador, no receptores ICCOA. Los resultados se guardan en
`android/core/build/test-results/test/` y el lint en `android/app/build/reports/`.

## Prueba manual de nuestra APK

Pendiente de ejecución física. Instalar sólo en el teléfono elegido por su dueño.
Probar denegación de permisos, Bluetooth apagado/ausente, lista vacía, Classic/BLE,
timeout de búsqueda, detener, rotación, segundo plano, revocar permisos y exportación.
Confirmar que ninguna búsqueda cambia el estado a sesión/proyección.
La exportación debe abrirse y revisarse localmente antes de compartirla.

## Próximo experimento con AION

Vehículo estacionado, propietario presente, sin instalación en la unidad.
Registrar versión del teléfono, firmware mostrado, pantalla ICCOA, hora y pasos.
Abrir ICCOA en la unidad y buscar con nuestra aplicación. Anotar nombre anunciado,
transporte, RSSI y comportamiento visible; no deducir protocolo por el nombre.
Repetir con ICCOA cerrado para distinguir anuncios del receptor de otros equipos.
No conectarse automáticamente a dispositivos encontrados.

Si el propietario obtiene un enlace funcional con un cliente legítimo, preparar
una captura HCI local activada manualmente en ese teléfono y registro Android
del intervalo acotado. Estos archivos pueden contener datos privados; mantenerlos
en `private/`, no subirlos ni exportarlos automáticamente. No ejecutar `bugreport`
general o `logcat` global como parte de los scripts iniciales.
No intentar saltarse TLS, extraer claves ni alterar aplicaciones.

## Recolección ADB limitada

```powershell
python scripts/collect_phone_evidence.py --serial <serial-del-telefono-autorizado>
```

Lee logs retenidos por UID de Samsung Auto y AionLink, guarda resultados en private/
y no inicia ninguna app. No limpia buffers ni cambia permisos o HCI. Una captura
vacía no significa éxito. No compartir los archivos crudos: pueden contener PIN,
identificadores u otros datos personales registrados por la aplicación de terceros.
La versión 0.2 exporta cantidad de observaciones, transporte, RSSI e indicios UUID
permitidos; omite nombre, MAC y datos crudos. Hacer exportación tras finalizar la búsqueda.

## Enlace GATT 0.3

Las tres pruebas adicionales de GattEvidence verifican filtrado de UUID privados,
distinción servicio/características y rechazo de evidencia fuera de UnionShare.
Son pruebas del intérprete de metadatos, no de la radio ni callbacks Android.
Las 17 pruebas JVM y 6 Python pasan. En el S25 se verificó apertura de la pantalla
0.3 y acción Detener mediante jerarquía UI, sin buscar ni conectar dispositivos.

Pendiente con coche estacionado: seleccionar el BLE propio, conectar, comprobar
servicios, timeout, cancelación durante conexión, pérdida de radio y salida de pantalla.
No utilizar la inspección para afirmar una sesión ICCOA. Véase ICCOA_CONNECTION_TRACK.md.

## Cable USB/AOA 0.4

Seis pruebas nuevas verifican formato de solicitudes estáticas, cabeceras fragmentadas,
concatenación, padding observado, tamaños/canales inválidos y aislamiento de identidades.
Las fixtures son sintéticas; no son respuestas del AION. Total: 23 JVM + 6 Python.

En S25 conectado al ordenador se comprobó pantalla 0.4: alimentación reportada USB=true,
cero accesorios AOA, cero frames y cero solicitudes. Es un caso negativo real:
ADB/carga no se confunden con ICCOA. No se cambian USB functions ni configuración ADB.

Pendientes con accesorio real: autorización concedida/denegada, attach/detach,
canal ocupado por otra aplicación, recepción parcial, cancelación durante IO y
respuesta del receptor al bootstrap experimental. No se usan los modos experimentales
en equipos ajenos. Ningún resultado de apertura sustituye autenticación/proyección.

Para declarar sesión ICCOA: intercambio bidireccional reproducible, confirmación
del receptor identificada, estabilidad y desconexión limpia. Para proyección:
vídeo realmente visible y evidencia de formatos negociados. No hay esas pruebas aún.
