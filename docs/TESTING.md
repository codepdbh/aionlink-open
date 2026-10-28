# Pruebas y observación

Pruebas automatizadas independientes de hardware:

```powershell
python -m unittest discover -s tests/unit -v
cd android
./gradlew.bat :core:test :protocol-iccoa:test :transport-bluetooth:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console plain
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

## Pruebas 0.8

Estado actual: 6 Python, 40 JVM y 4 pruebas instrumentadas de interfaz en el S25.
Las secciones anteriores conservan resultados históricos. Las pruebas de interfaz
usan estados PREPARING/READY/BLOCKED y una clave ficticia, con callbacks de prueba;
no hacen búsquedas, conexiones Bluetooth o Wi-Fi ni autentican un vehículo.

Casos: campo visible con bloqueo; campo visible durante preflight; seis dígitos y
confirmación explícita para enviar al callback; cerrar/reabrir descarta la clave.
No interpretarlos como validación del intercambio normal contra el AION.

Tras Build-Android.ps1, compilar `:app:assembleDebugAndroidTest`, instalar nuestra
APK debug y app-debug-androidTest.apk con `adb -s SERIAL_AUTORIZADO install -r -t`.
Ejecutar:

```powershell
adb -s SERIAL_AUTORIZADO shell am instrument -w -e class org.aionlink.open.ConnectionCodeDialogTest org.aionlink.open.test/androidx.test.runner.AndroidJUnitRunner
```

Comprobar explícitamente `OK (4 tests)`: am instrument puede devolver exit code 0
aunque JUnit informe fallos. Limpiar después únicamente el paquete de pruebas
`org.aionlink.open.test` y volver a abrir nuestra MainActivity.

En Android API 37, Espresso 3.6.1 falló antes de ejecutar las interacciones por
InputManager.getInstance ausente. La corrección publicada por Android usa
getSystemService. Se fijaron versiones estables Espresso 3.7.0, runner 1.7.0 y
ext.junit 1.3.0, comprobadas en los metadatos oficiales de Google Maven. Las cuatro
pruebas pasaron después. Registros de ambas ejecuciones se conservan en private/.

## Pruebas actuales 0.9

Total final: 6 Python, 51 JVM (7 core, 40 protocolo, 4 Bluetooth) y 9 Android.
Las pruebas nuevas de protocolo cubren consentimiento, conservación del retorno
Android sin fabricar un sustituto, separación del endpoint remoto, modos 1001/1002
y errores de respuesta clasificados sin valores privados.

Las 9 pruebas Android se ejecutan sin filtro de clase con am instrument:

- Seis de interfaz: los cuatro casos anteriores más consentimiento experimental
  obligatorio y su descarte al cerrar. Usan el texto real del diálogo para comprobar
  que la casilla está visible en el S25, con código ficticio y sin envío remoto.
- Dos de construcción de NetworkRequest: Wi-Fi local sin Internet y rechazo de
  convertir una respuesta P2P en AP. Sólo construyen objetos; no solicitan redes.
- Una consulta pública de metadatos locales mediante P2pConnector. No escanea,
  crea grupos ni se conecta a otro equipo. Requiere Wi-Fi ya encendido y permiso
  normal concedido; puede omitirse si faltan. Para afirmar que ese teléfono ofrece
  el modo experimental debe aparecer LOCAL_P2P_METADATA_KIND=ANDROID_ANONYMIZED.

En el S25 se obtuvo esa clasificación y OK (9 tests), sin omisiones. No se imprimió
la dirección ni parámetros de red. Ninguno de estos tests prueba que el receptor
AION acepte ClientInfo anonimizado. Véase INTEROPERABILITY_EXPERIMENT_09.md.

## Pruebas 0.10: puerto independiente de formación de red

Se añaden cuatro pruebas JVM en NetworkPortIndependenceTest: formatos no
utilizables conservados como categoría/null, puerto ausente, límites válidos
y validación de parámetros de red que sigue rechazando datos inválidos.
Dos pruebas instrumentadas NetworkBootstrapParsingTest comprueban lo mismo
con JSONObject de Android real. Son fixtures sintéticos; no conectan radios.
Se conservan las pruebas de consentimiento, descarte, red AP y metadata local.
Véase protocol-research/NETWORK_BOOTSTRAP_010.md para evidencia y límites.

Resultado 0.10: 6 Python + 55 JVM + 11 instrumentadas = 72 correctas.
Instrumentación S25: OK (11 tests), 7,737 s, sin omisión de metadata local
(ANDROID_ANONYMIZED). Build/lint correctos: 0 errores, 11 advertencias.

## Pruebas 0.11

Se ejecutaron 6 pruebas Python, 58 JVM (9 core, 45 protocolo, 4 Bluetooth)
y 13 instrumentadas en el S25: 77 correctas. La instrumentación concluyó
`OK (13 tests)` en 10,126 s y la consulta local informó ANDROID_ANONYMIZED.
Las dos pruebas nuevas de estados usan los modelos reales de BLE y P2P/AP sin
solicitar radio, y cubren WAITING_SERVER, CONNECTING, CONNECTED y etapas inactivas.
La prueba de protocolo verifica la clasificación del puerto numérico sin
conservar su valor. No simulan la Activity ni el temporizador de 50 s: la
conservación efectiva en el AION requiere prueba física del propietario.
Build y lint correctos; 0 errores y 11 advertencias.

Para las primitivas AUTH offline: 6 Python, 61 JVM y 14 instrumentadas,
81 correctas. Vectores HMAC independientes, firmas y puntos sintéticos y
compatibilidad JCA en S25; no se prueba un handshake ni un receptor real.
Detalles y limitaciones en protocol-research/AUTH_CRYPTO_OFFLINE.md.

Corrección 2026-10-28: los vectores anteriores de HMAC incorporaban los nonce
erróneamente en el mensaje. Se recalcularon con Python tras contrastar Samsung y
Xiaomi; 6 Python, 61 JVM y 14 instrumentadas correctas. Es código offline.

Codec AUTH de laboratorio: 6 Python y 66 JVM correctas, incluidos cinco tests
de codec con fixture sintética, validación independiente por protobuf-java y
socket loopback con servidor de prueba. Build y lint correctos. No hay integración
Android ni autenticación entre extremos en esta prueba, ni prueba de vehículo.
