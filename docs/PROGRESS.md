# Progreso — 2026-10-21

## Resultado de esta etapa

Objetivo: preparar el entorno, analizar los archivos presentes y construir una base
Android independiente. Estado global: PARCIAL; conexión ICCOA real aún pendiente.

Se preservaron los cinco originales en la raíz, con SHA-256 comprobados nuevamente
al finalizar. Git se inicializó localmente. El propietario autorizó posteriormente
crear el repositorio GitHub y publicar el código propio con documentación.
Se verificó que APK, extracción, Java/Smali, bibliotecas y artefactos quedan ignorados.

| Hito | Estado | Evidencia / pendiente |
|---|---|---|
| 1 Entorno | COMPLETADO | Apktool/JADX funcionales, SDK/JDK/Gradle verificados, scripts y tests ejecutados |
| 2 APK | PARCIAL | Cinco inventarios y manifiestos; decompilación incompleta; falta receptor ICCOA identificado y revisión exhaustiva |
| 3 Protocolo | PARCIAL | Mapa estático Samsung BLE/P2P/Auth/JNI; sin mensajes observados en AION |
| 4 Primera APK | PARCIAL | APK debug compilada/verificada, UI y descubrimiento implementados; ejecución física y UI runtime pendientes |
| 5 Descubrimiento AION | PENDIENTE | No se ha probado con hardware |
| 6 Sesión ICCOA | PENDIENTE | Framing y negociación reales no verificados |
| 7 Proyección | PENDIENTE | Módulo explícitamente sin implementación |
| 8 Versión pública de la aplicación | PENDIENTE | Publicación del código autorizada; versión funcional, revisión de dependencias y compatibilidad pendientes |

## Entorno verificado

Windows; Python 3.14.2; Git 2.52.0; Java en PATH Oracle 26.0.2.1.
Para Gradle y análisis se usó JBR OpenJDK 21.0.10 de Android Studio, sin cambiar
configuración global. SDK local: `%LOCALAPPDATA%/Android/Sdk`, plataformas 34/35/36,
Build Tools 34.0.0/35.0.0/36.0.0/36.1.0, ADB 37.0.0, NDK 28.2.13676358.
Gradle 8.13 disponible y Wrapper generado. No había Apktool/JADX en raíz o PATH;
se descargaron sólo dentro de tools/ desde sus releases oficiales.

- Apktool 3.0.3 SHA-256: `dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423`.
- JADX 1.5.6 ZIP SHA-256: `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`.
- Coincidencia con digests de assets de GitHub; respuestas originales guardadas en artifacts/reports/.
- Inventario reproducible: `artifacts/reports/environment.json`.

## Hallazgos

Samsung contiene una ruta ICCOA distinta de CarLife, con BLE, Wi-Fi P2P, mensajes
de autenticación Protobuf, generación local de claves y frontera JNI heycastcore.
Se revisaron llamadas Java y referencias Smali del negociador que JADX no recuperó.
No hay prueba todavía de un requisito obligatorio de certificado OEM ni de su ausencia.
CarWith incluye castfwk, negociador UCarProto, Easyconn y privilegios MIUI/Android.
OPPO/HeyTap no es Samsung; el receptor CarLife no es evidencia del receptor ICCOA
del AION. Las dependencias se clasifican inicialmente, sin afirmar causalidad del fallo.

Se inventariaron 88 entradas lib/*.so entre cinco APK. LLVM inspeccionó 91 candidatos
incluyendo assets: 87 aceptados y cuatro rechazados por no ser ELF válido reconocible.
Detalles en los dos informes de analysis/reports/ y la investigación de docs/.

## Verificación de implementación

- Python: 3 pruebas aprobadas (inventario, manifiesto, archivo corrupto).
- JVM core: 5 pruebas aprobadas, 0 fallos/errores.
- Gradle: `:core:test :app:assembleDebug :app:lintDebug` finalizó BUILD SUCCESSFUL.
- Lint: 0 errores, 8 advertencias (incluye SDK/dependencias más nuevas, atributo
  condicionado por API y configuración de publicación). No se trata de una versión publicable.
- APK debug: `artifacts/builds/aionlink-open-debug.apk`, 24 316 558 bytes.
- SHA-256: `bec27c99bc751a2253700c05d0ab04cafa61c6f1e31e939e7f0bcc9a1c60e622`.
- apksigner: firma v2 verificada; aapt2: org.aionlink.open, versión 0.1.0-research,
  minSdk 29, targetSdk 35. Instalación posterior autorizada: ADB devolvió Success
  en Galaxy S25 Ultra (SM-S938B) y pm path confirmó org.aionlink.open.
  Ejecución de la UI y descubrimiento físico todavía sin verificar.
- Evidencia: `artifacts/reports/android-build.log`, `debug-signature.txt`,
  `debug-badging.txt`, `android/core/build/test-results/test/`, informe lint de app.

## Errores y tratamiento

Apktool extrajo las cinco APK con exit 0. JADX emitió errores: plugin CarLife 130,
receptor CarLife 6, OPPO 148 y Samsung 44. Xiaomi agotó el heap de 2 GiB al final;
su salida se conserva como parcial. No se ocultaron errores ni se consideró Java
reconstruido como fuente autoritativa. Se añadió descompilación dirigida fallback
en carpeta separada y se usa Smali para métodos incompletos. La ejecución dirigida
de `com.miui.carlink.castfwk.negotiator.ClientKeyNegotiator` terminó con exit 0,
heap 1 GiB, modo fallback; evidencia local en
`analysis/targeted/c7888ec5018b/com.miui.carlink.castfwk.negotiator.ClientKeyNegotiator/`.
Esto recupera instrucciones de esa clase, no convierte el análisis completo en éxito.
El primer inventario PowerShell falló por java -version en stderr con ErrorAction
Stop; se corrigió a --version y volvió a ejecutarse correctamente.
Los cuatro rechazos ELF se registraron; no se cargaron ni ejecutaron archivos .so.

## Límites y siguiente actividad

No hay Bluetooth/USB/Wi-Fi del vehículo observado desde este entorno ni APK receptora
ICCOA identificada. Se instaló únicamente la APK propia en el teléfono con autorización;
no se modificó firmware ni la unidad multimedia.
La aplicación propia sólo descubre dispositivos, muestra diagnósticos y exporta
eventos controlados. Negociación, pairing, PIN, A/V, reconexión y USB/Wi-Fi siguen
pendientes; traducción inglesa completa y pruebas UI/Android físicas también.

Siguiente experimento: búsqueda comparada con ICCOA abierto/cerrado en el AION
estacionado, versión del receptor y observaciones locales. Si se consigue un enlace
con cliente legítimo, capturar HCI del intervalo autorizado para relacionar los
UUID y mensajes estáticos con el receptor concreto. Véase docs/TESTING.md.

## Fuentes oficiales consultadas

- [Apktool](https://github.com/iBotPeaches/Apktool) y [JADX](https://github.com/skylot/jadx).
- [Compatibilidad AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
- [Permisos Bluetooth Android](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
- [Compose Compiler con Kotlin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html).

Estas fuentes sustentan el uso de herramientas/APIs; no documentan el wire protocol
del receptor AION. Todos los hallazgos ICCOA anteriores provienen del análisis local.

## Etapa 2026-10-22 — registros S25 y reconstrucción independiente inicial

**Objetivo:** investigar el fallo de Samsung Auto con evidencia del teléfono y
avanzar implementaciones propias a partir de protocolos observados.

**Trabajo realizado:** lectura ADB por UID de las dos aplicaciones; copia local
de la APK Samsung instalada (3.1.04.10); inventario, firma y extracción; contraste
Java/Smali de llamada OEM, filtro BLE y cabeceras; comparación de seis APK contando
ambas versiones Samsung. Se mantienen separados los originales de investigación.

**Hallazgos:** dos cierres retenidos por método Samsung ausente al enlazar ICCOA;
denegaciones de MANAGE_ACTIVITY_TASKS y SEM_APP_RESTRICTION; en un intento posterior
se abren servicios locales BLE/P2P. No hay confirmación de rechazo por región ni
de sesión con receptor. Las trazas históricas no identifican inequívocamente qué
versión produjo cada cierre. La versión actual contiene la llamada OEM fallida.
El propietario confirmó estar lejos del coche durante esta inspección.

**Implementación original 0.2:** lector offline UCar (20 bytes, CRC-16/ARC, límites,
lectura fragmentada, valores desconocidos preservados); indicios FCFB/UnionShare
obtenidos de UUID anunciados; diagnóstico exportable de observaciones sin nombre,
MAC, PIN ni service/manufacturer data; timestamps y control de callbacks BLE tardíos.
No hay encoder ni envío de autenticación. El adaptador de sesión sigue Unsupported.

**Evidencias:** docs/protocol-research/S25_DIAGNOSIS.md,
UCAR_OBSERVED_FORMAT.md y PROTOCOL_COMPARISON.md; metadatos nuevos en
analysis/metadata/samsung-auto-3-1-04-10-s25-5633d2f9c1bb/; registros privados bajo
private/phone-evidence-20261022T131847370776Z/. Colector no inicia apps, no limpia logs,
no concede permisos y no cambia HCI. El ajuste HCI consultado devolvió null, lo
cual no demuestra que no exista ninguna configuración equivalente del fabricante.

**Validación:** 6 pruebas Python y 14 JVM (5 core + 9 protocolo), todas aprobadas.
Compilación/lint BUILD SUCCESSFUL, 0 errores y 8 advertencias de lint. Pruebas de
protocolo usan fixtures sintéticas explícitas, no respuestas fabricadas de un coche.
Evidencia: artifacts/reports/android-build-0.2.log y XML de test-results por módulo.
Inventario nativo actualizado: 102 candidatos, 98 aceptados y 4 rechazos ELF conocidos.

**Entrega:** artifacts/builds/aionlink-open-debug.apk, versión 0.2.0-research,
versionCode 2, 24 336 562 bytes. SHA-256:
`2710f351c9fcb731831a925d106c0680f8ab01ce4a82c678150de3c49a465d22`.
Firma debug verificada; instalación actualizada por ADB con Success y versión
comprobada en dumpsys. No se abrió automáticamente la aplicación ni se inició
una búsqueda desde el ordenador. No se modificó la app Samsung.

**Errores y límites:** los logs iniciales con -t y filtro UID devolvieron vacío;
leer por UID sin limitar la cola global recuperó eventos retenidos. La nueva APK
Samsung produjo 38 errores JADX; su extracción Apktool fue exitosa. No hay captura
GATT ni red de una sesión real del AION. No se han recreado todas las aplicaciones
ni sus protocolos completos. Los canales similares de Samsung/CarWith no justifican
mezclar CarLife o rutas de llave/control del paquete OPPO con proyección ICCOA.

**Estado:** etapa parcial con componentes propios probados. Hitos 5–7 pendientes.
**Siguiente actividad:** comparación de búsquedas 0.2 con ICCOA abierto/cerrado,
vehículo estacionado y propietario presente; obtener mensajes reales antes de
implementar escritura GATT, topología de red y autenticación. Logs, APK extraída y
diagnóstico del usuario siguen excluidos de Git; no se enviaron a servicios externos.

## Etapa 2026-10-22 — cliente de enlace 0.3 e interfaz propia como objetivo

El propietario pidió una app con posibilidades reales de enlace, interfaz propia
o puente Android Auto. Aclaró que el receptor muestra ICCOA CarLink y Huawei Car;
CarbitLink no fue identificado. Se priorizó ICCOA con código propio. No se promete
probabilidad de éxito o conversión Android Auto sin interoperabilidad verificable.

**Trabajo realizado:** GattInspector conecta al BluetoothDevice observado que el
propietario seleccione, descubre servicios, filtra UnionShare y tres características
conocidas, y cierra el enlace. Timeout 15 segundos, cancelación, callbacks serializados
en main y generaciones para descartar eventos tardíos. UI de selección y confirmación
para vehículo propio estacionado. Resultado GATT separado del estado de sesión.
No hay lectura de valores, notificaciones, escritura de PIN/JSON, bond ni sockets.

**Investigación adicional:** se localizaron los nombres de campos JSON Client info
y el intercambio posterior en la ruta BLE Samsung. La especificación completa,
campos de red y variante del AION aún no están validados. La página pública del
estándar ICCOA fue recuperada tras un timeout del navegador, pero su descarga oficial
respondió que requiere login; no se utilizó cuenta ni se eludió el requisito.
Una salida de consola con texto chino falló por cp1252 y se repitió con Python -X utf8.

**Validación:** compilación y lint BUILD SUCCESSFUL, 0 errores/8 advertencias;
6 pruebas Python y 17 JVM (5 core, 9 framing, 3 filtrado GATT), todas aprobadas.
El adaptador GATT Android no se probó contra hardware remoto: el propietario está
lejos del coche. No se conectó ni escaneó ningún dispositivo desde el ordenador.

**Entrega:** AionLink Open 0.3.0-research, versionCode 3, instalada por ADB con Success,
versión comprobada en dumpsys; firma debug validada. APK:
artifacts/builds/aionlink-open-debug.apk, 24 332 951 bytes. SHA-256:
`715718a0f85656f0c99807e24a7a2f68ff93757a37380497567032d8c1014422`.
Log de compilación: artifacts/reports/android-build-0.3.log.

La app propia se abrió en el S25 para comprobar la pantalla. Jerarquía UI confirmó
v0.3.0, estado IDLE, botones y estado GATT. Pulsar Detener cambió el estado a
DISCONNECTED sin acciones de radio. Evidencias locales privadas:
private/ui-0.3.png, ui-0.3.xml y ui-0.3-stopped.xml. No se mostró ninguna ventana en Windows.

**Estado y siguiente paso:** enlace GATT implementado, pendiente prueba con receptor
propio; autenticación ICCOA y proyección aún pendientes. Hitos 5–7 continúan sin
completar. Seguir docs/protocol-research/ICCOA_CONNECTION_TRACK.md: abrir ICCOA,
buscar, seleccionar el BLE del coche, comprobar servicios y exportar el resultado.
La interfaz de proyección propia se implementará sobre una sesión real; no se crea
un escritorio ficticio ni se llama Android Auto a una interfaz distinta.

## Etapa 2026-10-22 — APK 0.4 con rutinas USB/AOA independientes

**Objetivo:** priorizar la reacción por cable informada por el propietario y
recrear de forma independiente comportamientos de transporte observados en Samsung
y CarWith. La necesidad de Bluetooth es una observación del propietario; su rol
concreto y el eventual uso de Wi-Fi aún no están confirmados.

**Análisis:** se siguieron IccoaUsbConnectionActivity, UsbConnectReceiver, el setup
AOA, lector y multiplexor Samsung; AOAActivity y filtros de CarWith. Ambas apps
identifican ICCOA/CarLink/1.0.0; CarWith distingue además CarLife y Carbit. Samsung
prepara canales por USB y después inicia autenticación UCar sobre loopback. No
se copió código ni bibliotecas; especificación en USB_AOA_OBSERVED.md.

**Implementado:** transport-usb convertido en módulo Android real; enumeración de
accesorios y dispositivos host, alimentación reportada USB, attach/detach y filtro
de accesorio ICCOA. Permiso explícito comprobado con UsbManager, apertura del
descriptor AOA, lectura incremental con límites y cierre. En modo opcional y sólo
para identidad observada, emite los dos pedidos iniciales estáticos RTSP/UIBC.
El modo de observación no envía datos. Ninguno declara sesión ICCOA o proyección.
No hay proxy loopback, respuesta a creación de sockets ni autenticación aún.

Los intentos se cancelan al salir a background, detener o retirar el accesorio;
callbacks de permiso antiguos no autorizan intentos nuevos. IO no bloqueante con
poll, tiempo máximo 15 segundos y límites de bytes/frames. Se procesan datos
pendientes incluso si el peer cierra. Los payloads nunca se guardan ni exportan.
Se informa errno numérico cuando Android devuelve un error de E/S, sin datos privados.

**Pruebas:** 6 Python y 23 JVM (5 core, 18 protocolo), todas aprobadas. Las seis
pruebas nuevas USB usan fixtures sintéticas para formato, fragmentación, padding,
concatenación, validación y aislamiento. Build/lint SUCCESSFUL: 0 errores y 8
advertencias. Evidencia: artifacts/reports/android-build-0.4.log y test-results.

**Entrega final:** APK debug 0.4.0-research, versionCode 4, 24 498 373 bytes;
SHA-256 `65e2de5e078777cd7ab8ff266e975eb5a3e0a70f38d0d2827e93ea7237f695ff`.
Ruta: artifacts/builds/aionlink-open-debug.apk. Firma verificada, instalación ADB
Success y versión comprobada. En el S25 conectado al PC, la UI y Actualizar USB
mostraron alimentación USB=true, accesorios AOA=0, frames=0 y solicitudes=0.
No se envió ningún bootstrap al ordenador. Evidencia local: private/ui-0.4.xml,
ui-0.4-refreshed.xml y ui-0.4-final.xml.

La primera comprobación inmediatamente después del último reemplazo no encontró
nodos de nuestra app; no hubo traza AndroidRuntime por su UID. Se repitió con
am start -W: arranque COLD con Status ok, proceso activo y pantalla 0.4 verificada.
No se atribuyó el cambio transitorio de pantalla a un crash sin evidencia.

**Estado:** transporte inicial cableado implementado, comportamiento del receptor
AION todavía sin validar. No se recrearon todos los protocolos ni la proyección.
No se cambió el modo USB/ADB del teléfono ni preferencias de otras aplicaciones.
Los originales permanecen intactos; logs y artefactos siguen privados.

**Siguiente prueba:** AION estacionado, ICCOA abierto, puerto de datos y cable al
S25; elegir AionLink en el selector Android si aparece, o abrirla manualmente.
Actualizar USB, observar accesorio y autorizar Abrir USB y observar. Exportar al
final. Si coincide ICCOA/CarLink/1.0.0, el propietario puede probar por separado
Iniciar canales USB (experimental). No introducir PIN ni afirmar autenticación
completada a partir de bytes/frames o un descriptor abierto.
