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

## Diagnósticos exportados del propietario — 2026-10-22

Se localizaron por ADB dos archivos con nombre aionlink en Download; una búsqueda
por ese nombre hasta cinco niveles de almacenamiento compartido no encontró otros.
Se copiaron únicamente esos archivos a private/vehicle-tests-2026-10-22/.
El primero corresponde a 0.1; el segundo a 0.4, no a dos intentos nuevos 0.4.

El diagnóstico 0.4 tiene 13 observaciones en el snapshot final, incluidos Classic
y BLE. Una observación BLE conectable anuncia FCFB, el UUID de filtro ICCOA
localizado en Samsung, y tiene RSSI -51 dBm. Es evidencia de un candidato relevante
en el entorno de la prueba; no prueba por sí sola identidad AION, GATT UnionShare,
autenticación ni proyección. No se asigna compatibilidad confirmada al vehículo.

La inspección GATT está IDLE, sin peer seleccionado ni servicios descubiertos;
el log no registra solicitud de conexión GATT. USB está IDLE, accesorios AOA=0,
bytes/frames/solicitudes=0, alimentación reportada USB=true. Esto no confirma que
el cable estuviera en el coche durante la exportación ni que el receptor entrara
en AOA. Las búsquedas de las 18:45–18:46 UTC sí finalizaron (14, 14 y 13 observaciones).

La explicación respaldada del resultado visible es que se ejecutó descubrimiento,
pero no consta un enlace GATT o AOA iniciado en ese diagnóstico. No atribuirlo a
rechazo de autenticación o bloqueo regional. Próximo paso: seleccionar en la UI el
candidato marcado por UUID, comprobar que es el vehículo propio estacionado y
ejecutar Conectar y comprobar servicios. Para USB falta observar el accesorio
durante el cableado al AION. Los TXT crudos siguen excluidos de Git.

## 0.5 — descubrimiento dirigido y candidatos visibles — 2026-10-22

El propietario informa que no vio candidatos. La revisión del código 0.4 encontró
dos problemas de presentación: USB precedía los resultados y cada anuncio movía
su tarjeta al final. No se afirma que esto reproduzca exactamente la pantalla de
la prueba anterior. El TXT conservado sí contiene un indicio FCFB; no identifica
por sí solo al AION.

Cambios independientes:

- Búsqueda principal BLE filtrada por FCFB, baja latencia, 15 segundos y sin
  descubrimiento Classic simultáneo. Evidencia estática Samsung 3.1.04.10:
  com/share/connect/ble/d.java:375–393. Alternativa general Classic/BLE separada.
- Panel destacado de candidatos antes de USB; otros equipos desplegables,
  posición estable por primera observación y conservación de indicios por búsqueda.
- Selección GATT disponible durante el escaneo: se detiene antes de conectar,
  conservando la confirmación de vehículo propio estacionado. Sin conexión automática.
- Exportación con número de candidatos y eventos del modo de búsqueda.
- Cuatro pruebas JVM de actualización/retención de evidencia. Build-Android.ps1
  incluye ahora las pruebas del módulo Bluetooth.
- Mapa de servicios y límites en protocol-research/SERVICE_REIMPLEMENTATION.md.
  No se copiaron servicios, clases ni bibliotecas de fabricantes. P2P/AP,
  ClientInfo, autenticación y proyección continúan pendientes.

Validación: 6 pruebas Python, 27 JVM (5 core, 18 protocolo, 4 Bluetooth), todas
correctas. assembleDebug y lintDebug correctos; lint 0 errores, 8 advertencias.
Registro privado artifacts/reports/android-build-0.5.log. Herramientas: Python
3.14.2, Gradle 8.13, AGP/lint 8.9.1, Kotlin 2.1.20, JBR 21, SDK/build-tools 35,
ADB 1.0.41 / 37.0.0-14910828. apksigner verify correcto; su runtime emitió aviso
de acceso nativo de Conscrypt, sin fallo de verificación.

APK 0.5.0-research, versionCode 5, 24 548 605 bytes, SHA-256
`3147bd22d60804a3ef002afb27ea6ded37ab8ad48de2ba9c566e2b911576effb`.
Instalación ADB: Success; versión 5/0.5.0-research comprobada en el S25.
am start -W devolvió Status ok (COLD y después WARM), con proceso presente.
La verificación UI quedó incompleta: el primer dump mostró otro paquete y el
segundo devolvió null root node; no se usa el XML antiguo como prueba visual.
Buffer crash filtrado por UID propio: cero FATAL EXCEPTION. No se inició escaneo,
GATT ni bootstrap USB durante esta revisión del teléfono lejos del vehículo.
La presentación con candidato real y el comportamiento del AION siguen sin validar.

Incidencias de herramientas corregidas: primera lectura usó src/main/java en vez
de kotlin; una sustitución de texto por stdin falló por codificación y se rehízo
con anclas ASCII; un glob de rg de Windows se reemplazó con -g; dumpsys no devolvió
el campo userId esperado y se obtuvo UID con cmd package list packages -U.
No se modificaron originales ni se publicaron archivos privados.

## Revisión de nuevos diagnósticos — 2026-10-23

Se copiaron por ADB tres exportaciones de Download a
private/vehicle-tests-2026-10-23/. Dos hashes coinciden con los diagnósticos previos.
La nueva exportación 0.5 (2053 bytes), SHA-256
`98d00367faf45619f0da0c36203bf24d3b273a247bb1efeb000bea459454e61d`,
registra dos inspecciones GATT correctas el 22 de octubre, 19:03:48–19:03:55
hora boliviana. Ambas ven cinco servicios y UnionShare. El snapshot final incluye
ClientInfo con propiedades 8 y ServerInfo con 18; no incluye ClientAuthInfo.
Se contrastaron las propiedades con la referencia oficial Android.

Esto supera el descubrimiento pasivo 0.4, pero no verifica autenticación ni sesión.
El cierre está implementado explícitamente en nuestro GattInspector tras descubrir
servicios. USB sólo informa snapshot IDLE, AOA=0 y alimentación USB=false. No hay
evidencia de rechazo regional ni de fallo de autenticación. Análisis detallado:
protocol-research/VEHICLE_DIAGNOSTICS_2026-10-22.md.

Herramientas: ADB existente y SHA-256 de PowerShell. No se ejecutaron pruebas de
código porque sólo se añadieron documentos. La consulta inicial de fuentes SDK
android-35 no encontró BluetoothGattCharacteristic.java; se usó documentación
oficial online. No se inició conexión, escaneo o instalación; originales y TXT
privados permanecen intactos. Autenticación, intercambio de red y proyección siguen
incompletos. No se publicó esta revisión.

## 0.6 — intercambio BLE y conexión de red experimental — 2026-10-23

Solicitud del propietario: avanzar más allá del inventario para conectar. Se
implementaron ClientInfo/ServerInfo y un cliente Wi-Fi Direct con código propio;
no se completaron autenticación UCar o proyección. Detalle, procedencia y límites:
protocol-research/BLE_BOOTSTRAP_06.md.

Cambios:

- BleNegotiator separado del inspector: mantiene GATT durante MTU, servicios,
  suscripción, escritura y espera; confirma escritura antes de entregar una
  respuesta, con control de carreras, callbacks antiguos y timeout de 30 s.
- Identidad local propia de seis caracteres, modelo real, capacidad de 5 GHz y
  PIN del propietario; no suplanta Samsung. PIN sólo en memoria y no exportado.
- Validadores ClientInfo y ServerInfo, límite de tamaño, UTF-8 estricto, errores
  sin contenido remoto y objetos con toString redactado.
- Restricción confirmada en documentación oficial: requestDeviceInfo oculta la
  MAC local sin LOCAL_MAC_ADDRESS. No se solicita ni concede permiso privilegiado.
  Si no existe MAC utilizable o no hay PIN, se prueba únicamente READ ServerInfo,
  propiedad observada en el receptor. La disponibilidad de parámetros por esa
  lectura antes de ClientInfo es hipótesis sin validar con AION, no éxito supuesto.
- P2pConnector usa config público con los parámetros de la respuesta; rechaza
  grupos preexistentes y compara grupo/propietario antes de declarar red conectada.
  Timeout 45 s, cancelación y cierre en fondo. No abre sockets ni envía autenticación.
- Botón principal Conectar BLE + Wi-Fi Direct, inspección anterior secundaria,
  permisos bajo demanda y diagnóstico con etapas y conteos sin parámetros secretos.
- Módulo transport-wifi convertido de marcador JVM a librería Android. org.json
  20240303 se usa sólo para compilar/probar el protocolo JVM; Android aporta su JSON.

Validación: 6 Python y 36 JVM (5 core, 27 protocolo, 4 Bluetooth), todas correctas;
9 pruebas nuevas cubren campos y límites, redacción, datos inválidos, topología y
la carrera respuesta/confirmación de escritura. Build y lint correctos: 0 errores,
10 advertencias. Registro privado artifacts/reports/android-build-0.6-final.log.
Toolchain: Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, JBR 21, SDK/build-tools 35,
Python 3.14.2 y ADB 37.0.0. git diff --check correcto.

Primer lint falló por CoarseFineLocation al ampliar FINE hasta API 32. Corregido
declarando y solicitando COARSE junto a FINE en API 29–32. Consulta web inicial de
WifiP2pConfig.Builder agotó tiempo; reintento oficial correcto. apksigner emitió
el aviso de acceso nativo Conscrypt ya conocido, con verificación correcta.

APK final 0.6.0-research, versionCode 6, 24 662 752 bytes; SHA-256
`830b3a7b3eab87625e94dc2f4cd8ed9338693fee065fe7704444aa21a06138dc`.
Instalación final por ADB: Success. am start -W: Status ok; se entregó el intent
a la instancia superior ya activa. En una compilación intermedia 0.6, arranque
COLD correcto y 31 nodos UI propios mostraron versión 0.6, candidato=0,
intercambio=IDLE/red=IDLE. Buffer crash propio: cero FATAL EXCEPTION. Los caracteres
acentuados se degradaron al imprimir en consola, pero XML y fuente UTF-8 no contienen
caracteres U+FFFD; no se atribuye a corrupción de la interfaz.

No se iniciaron búsquedas o conexiones remotas desde el ordenador. La nueva ruta
normal, la consulta READ y el grupo P2P no están verificados con el vehículo. La
prueba del propietario debe ser con AION estacionado, ICCOA abierto y el nuevo
botón, exportando después. No afirmar que la petición «todo lo necesario» esté
completa: faltan autenticación, negociación/canales y motor de proyección; USB
mantiene el alcance anterior. APK originales, librerías OEM y TXT privados no se
modificaron ni publicaron. Estos cambios permanecen locales, sin push automático.

## 0.7 — corrección tras introducir PIN sin intercambio — 2026-10-26

Se leyeron dos TXT nuevos de Download por ADB; ambos registran el mismo intento
del 24 de octubre a las 15:55 hora boliviana. Copias privadas, hashes y análisis
en protocol-research/VEHICLE_DIAGNOSTICS_2026-10-24.md. El segundo archivo añade
el evento de exportación del primero; no se cuentan dos conexiones distintas.

Hallazgo: nuestra 0.6 pidió PIN, Android devolvió identidad P2P oculta y la app
cambió a READ sin enviar ClientInfo/PIN. BLE llegó a MTU 512 y READING_SERVER,
sin respuesta registrada. Cancelación a los 24,46 s de lectura, antes de vencer
el timeout global. La cercanía de la exportación y la implementación de onStop
hacen plausible cancelación al abrir el selector; el TXT no prueba ese motivo.
No hay evidencia de PIN rechazado, autenticación, red o sesión ICCOA.

Correcciones propias:

- Elegibilidad normal antes de solicitar PIN; bloqueo visible si falta dirección
  P2P real. Se elimina la alternativa READ silenciosa. Resultado fijado arriba y
  desplazamiento al aviso cuando cambia, para que no quede enterrado en la UI.
- READ separado, con confirmación del vehículo propio estacionado y límite de
  15 segundos. No se interpreta falta de respuesta como rechazo de autenticación.
- Preparación PIN con caducidad de 60 s y verificación de READY al confirmar.
- Cada intento reinicia el estado de intercambio y tiene número en diagnóstico.
  Modo explícito READ/NORMAL, confirmación real de escritura GATT y cancelación
  con causa de código y etapa anterior; sin registrar el valor del PIN.
- DiagnosticExport conserva la captura previa al selector Android y la consume
  una vez. Un proceso recreado sin captura no abre/trunca el destino. No se
  presenta el estado destruido al volver como si fuera el estado previo al exportar.
- Se retira DisposableEffect con limpieza implícita. Activity.onStop, ViewModel
  finalizado, Detener y nueva búsqueda mantienen limpieza explícita, con motivos.

Se revisó además ClientInfo de Xiaomi en
com/miui/carlink/castfwk/wireless/bt/t.java:M (evidencia local): también incorpora
y valida metadatos locales. No se encontró una ruta probada sin ese requisito
para el AION. No se inventaron mensajes, UUID ni MAC y no se solicitaron permisos
privilegiados. La restricción P2P, autenticación y proyección siguen sin resolver.

Validación: 6 pruebas Python y 40 JVM (7 core, 29 protocolo, 4 Bluetooth), sin
fallos. Cuatro pruebas nuevas cubren captura anterior a onStop, consumo de
exportaciones, identidad local anonimizada y elegibilidad normal. assembleDebug
y lintDebug correctos; lint 0 errores/10 advertencias. Registro privado:
artifacts/reports/android-build-0.7-final.log. Toolchain conservado: Gradle 8.13,
AGP 8.9.1, Kotlin 2.1.20, JBR 21, SDK/build-tools 35, Python 3.14.2, ADB 37.0.0.
git diff --check correcto; avisos de normalización CRLF a LF sin error. apksigner
verify correcto, con aviso Conscrypt de acceso nativo conocido.

APK final 0.7.0-research, versionCode 7, 24 697 984 bytes; SHA-256
`c86e2b8258eb83351bc3fc645a7b52676258625f5b73a80267ebbb6b8ea2d9fa`.
ADB install -r: Success; am start -W COLD, Status ok, 370 ms. Paquete instalado
7/0.7.0 verificado. XML fresco private/ui-0.7.xml: 29 nodos propios, versión 0.7
y explicación de preflight visibles. Buffer crash propio: cero FATAL EXCEPTION.
No se inició búsqueda, GATT, P2P ni USB remoto desde el ordenador; validación con
vehículo pendiente. Se preservaron cambios locales 0.6, APK originales y logs;
no hubo commit/push ni publicación de material privado.

## 0.8 — campo de clave visible y pruebas UI reales — 2026-10-26

Se revisaron los ocho TXT del teléfono y se conservaron copias privadas en
private/vehicle-tests-2026-10-26-b/. Los cinco primeros mantienen sus hashes;
los tres nuevos corresponden a 0.7. Análisis y procedencia en
protocol-research/VEHICLE_DIAGNOSTICS_2026-10-26.md.

Se deduplicaron timestamp/mensaje de los tres nuevos: 191 eventos únicos visibles,
20 preparaciones normales bloqueadas, dos lecturas canceladas, tres inspecciones
UnionShare correctas y un error GATT 133 aislado. No consta envío de PIN ni sesión.
La clave en pantalla del coche es observación del propietario, no evidencia de
intercambio completo con AionLink. El formulario faltaba por diseño de nuestra 0.7.

Se investigó cómo obtener datos locales reales mediante accesos ADB de diagnóstico:
cmd wifip2p help rechazó UID shell; ip link sobre p2p0 denegó permiso; el filtro
de dumpsys no encontró el campo local. No se elevaron permisos, intentó root,
extrajeron credenciales ni se imprimieron valores de dirección o configuración.
No se consiguió una identidad utilizable. El bloqueo normal sigue sin resolver.

Cambios independientes:

- ConnectionCodeDialog visible desde el inicio del preflight, incluso si falla.
  Permite introducir seis dígitos en memoria y muestra requisito y estado de
  transmisión; Enviar sólo se habilita con datos reales READY y longitud válida.
- Cerrar elimina el valor, cancela el preflight aún pendiente y evita que un
  callback tardío vuelva a abrirlo. Caducidad y revalidación anteriores se conservan.
- El historial exportable aumenta a 500 eventos, muestra contador de descartados
  e ID aleatorio del proceso. UI renderiza sólo los 20 últimos eventos.
- Cuatro pruebas instrumentadas independientes de red/vehículo: campo con bloqueo,
  campo durante preflight, confirmación de seis dígitos y descarte al cerrar/reabrir.
  Usan una clave ficticia y callbacks de prueba; no simulan una sesión ICCOA.

Validación final: 6 Python, 40 JVM y 4 instrumentadas en el S25 (50 correctas).
assembleDebug y lintDebug correctos, 0 errores/11 advertencias. Registros privados:
artifacts/reports/android-build-0.8-final.log, android-ui-build-0.8-fixed.log y
private/ui-tests-0.8-fixed.txt, que concluye explícitamente OK (4 tests).

Incidencia resuelta: las cuatro pruebas UI iniciales fallaron antes de interactuar
por NoSuchMethodException InputManager.getInstance de Espresso 3.6.1 en API 37.
Se contrastó la corrección oficial y los metadatos Google Maven; se fijaron Espresso
3.7.0, runner 1.7.0 y ext.junit 1.3.0. La repetición pasó las cuatro pruebas.
La consulta de metadatos XML por web no funcionó; urllib leyó los XML oficiales.
No confundir ese fallo del runner con el error GATT del propietario.

Toolchain: Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, JBR 21, SDK/build-tools 35,
Compose BOM 2025.04.01, Python 3.14.2 y ADB 37.0.0. apksigner verify correcto,
con aviso Conscrypt conocido; git diff --check correcto.

APK final 0.8.0-research, versionCode 8, 24 723 911 bytes; SHA-256
`7b35463b5a49ed4525acff96a063e142f005c5689b9841c264e21a946c9f3b91`.
Instalación final Success. Se desinstaló sólo nuestro paquete de pruebas y se
restauró MainActivity: COLD, Status ok, 404 ms. XML fresco private/ui-0.8.xml con
29 nodos propios y versión 0.8 visible. Buffer crash propio: cero FATAL EXCEPTION.

No se hicieron escaneos o conexiones remotas desde el ordenador. Faltan identidad
P2P accesible, autenticación, canales y proyección. La actualización corrige UI y
evidencia, no acredita conexión completa. El propietario autorizó commit a main
al finalizar; se incluyen código propio, pruebas y documentación, preservando
originales, logs, artefactos y extracción privada fuera de la publicación.

## Revisión 0.8 sin nuevo avance de enlace — 2026-10-27

Se revisaron las capturas aportadas y cuatro TXT nuevos, copiados por ADB a
private/vehicle-tests-2026-10-27/. Procedencia, hashes y cronología:
protocol-research/VEHICLE_DIAGNOSTICS_2026-10-27.md.

79 eventos únicos tras deduplicar exportaciones: seis bloqueos normales por
identidad P2P oculta; una consulta READ, MTU 512, sin respuesta registrada,
cancelada por Activity en segundo plano a los 12,09 s. El snapshot anterior al
selector conserva READING_SERVER, por lo que la corrección de captura funciona.
No hay escritura de PIN confirmada, red o sesión. AOA=0 en todos los snapshots.

El botón gris es una condición local de nuestra implementación; no prueba rechazo
del código por el AION. Mostrar el campo en 0.8 no resolvió el requisito y las
pruebas de interfaz no eran pruebas de interoperabilidad. No se generó otra APK
cosmética ni se pidió repetir el mismo intento como si fuera a conectar.

Sólo documentación; no cambios de código ni pruebas necesarias. Herramientas:
ADB 37.0.0 existente, Python hashlib para SHA-256 y comparación local de eventos.
No se accedió al valor de la clave enmascarada, se iniciaron conexiones, se cambió
el vehículo ni se publicó material privado. Identidad P2P legítima, autenticación,
canales y proyección siguen pendientes.

## 0.9 — ClientInfo consentido y modalidades de red observadas — 2026-10-27

Solicitud: investigar y mejorar una vía funcional para la siguiente prueba.
Investigación y límites completos: protocol-research/INTEROPERABILITY_EXPERIMENT_09.md.

**Corrección de interpretación:** la API oculta la dirección local, pero exigir
una dirección no anonimizada antes de todo envío era una política de nuestro
cliente, no un requisito del receptor demostrado por los logs. Samsung incorpora
el dato local sin esa validación; Xiaomi M/C valida no vacío. No se afirma que el
AION acepte el valor anonimizado: se prepara un experimento explícito para saberlo.

Implementación propia:

- PlatformP2pAddress conserva exactamente device.deviceAddress de la API pública.
  Clasifica AVAILABLE, ANDROID_ANONYMIZED o UNAVAILABLE; no crea un valor sustituto
  para null/vacío/inválido. El valor no se guarda ni exporta. El retorno anonimizado
  sólo puede entrar en ClientInfo con consentimiento por intento; no se presenta
  como dirección real ni permite usar un endpoint remoto anonimizado.
- El diálogo ofrece casilla sin preselección y confirmación separada. Con código
  válido y consentimiento puede intentarse la escritura del esquema ya observado.
  No se añaden UUID, campos o identidades OEM. El modo del diagnóstico diferencia
  este experimento de normal/READ y confirma la escritura sólo por callback GATT.
- Se aceptan respuestas 1001/P2P y 1002/AP observadas en Samsung ble/e y S4/n.
  El modo AP usa WifiNetworkSpecifier exacto SSID/BSSID, permiso normal
  CHANGE_NETWORK_STATE, callback y timeout. Sin Internet, bindProcess global,
  red persistente o sockets. La ventana Android tiene espera acotada de 45 s y
  limpieza en fondo a 50 s sin retorno. No modifica modo preferido del mensaje
  ClientInfo ni inventa que el receptor eligió AP: sigue su type recibido.
- Exportación aplazada mientras la operación está pendiente; Detener sigue
  disponible. Evita el cierre por selector visto en el intento anterior.
- Conteo de respuesta incluso si el parser la rechaza, con categorías seguras
  SIZE/UTF8/JSON/MODE/REQUIRED_FIELDS/SSID/PSK/MAC/FREQUENCY/PORT. Sin valores ni
  atribuir una respuesta inválida a rechazo de autenticación.

Investigación adicional: las páginas públicas ICCOA describen integración SDK;
no aportaron un SDK telefónico abierto utilizable aquí. MiAuto es receptor y
depende de SDK precompilado; no se incorpora ni instala en el coche. El AOA sigue
sin accesorio observado y no se fuerza USB. La autenticación normal independiente
se localiza en CarAuthClient/ClientKeyNegotiator con claves propias y algoritmos
estándar; su implementación/verificación, canales y medios siguen pendientes.
No se ejecutaron APK originales ni se copiaron bibliotecas, credenciales o fuentes.

Validación final: 6 Python, 51 JVM (7 core, 40 protocolo, 4 Bluetooth), 9 Android
en el S25: **66 correctas**. Las instrumentadas son 6 UI sin envío remoto, 2 de
construcción de solicitud AP sin enviarla y 1 consulta pública del propio teléfono.
Esta última confirmó LOCAL_P2P_METADATA_KIND=ANDROID_ANONYMIZED, sin omisiones,
sin leer una dirección oculta ni iniciar descubrimiento/grupo/red con otro equipo.
Registro privado private/ui-tests-0.9-final.txt: OK (9 tests), 7,963 s.

assembleDebug y lintDebug correctos, 0 errores/11 advertencias. Logs privados:
artifacts/reports/android-build-0.9-final.log y android-ui-build-0.9-final.log.
Toolchain conservado: Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, JBR 21, SDK 35,
Python 3.14.2, ADB 37.0.0 y librerías de test corregidas en 0.8. Incidencia menor:
un parche de documentación no coincidió con su contexto y se reaplicó sin cambios
de código. Avisos CRLF/LF y Conscrypt ya conocidos, sin fallo de firma/build.

APK final 0.9.0-research, versionCode 9, 24 751 739 bytes; SHA-256
`6e5204838b1147dab11fde6b2f81ec94a99d46d41f8fd278c962f44e17eb1213`.
Se verificó firma y presencia de los modelos nuevos en DEX propio. Instalación
final Success, paquete de pruebas retirado, MainActivity COLD/Status ok en 356 ms.
XML fresco private/ui-0.9.xml: 29 nodos propios y versión 0.9 visible. Cero
FATAL EXCEPTION en buffer crash del UID propio. No hubo prueba con el AION.

La próxima prueba mide escritura confirmada, ServerInfo válido y modalidad/red
obtenida. No se promete autenticación ni proyección: el receptor puede rechazar
el experimento. Se mantiene la autorización previa de commit a main; publicación
limitada a código propio, pruebas y documentos, nunca datos/artefactos privados.

## Revisión de pruebas 0.9 — 2026-10-28

Dos nuevos TXT recuperados por ADB 37.0.0 y conservados en privado; hashes,
horas y límites en protocol-research/VEHICLE_DIAGNOSTICS_2026-10-28.md.
Dos intentos del propietario del 27 de octubre: ClientInfo de 139 bytes con
escritura confirmada, MTU 512, respuesta de 123 bytes en ambos. Nuestro parser
rechaza PORT y cancela antes de Wi-Fi. Primer intercambio BLE confirmado;
no demuestra aceptación del PIN, autenticación o proyección. AOA sigue en cero.
Samsung estático lee port como String; su semántica y el valor real del AION
siguen sin determinar. No se inventó un puerto ni se relajaron validaciones.
Sólo documentación; sin pruebas de código, instalación o conexiones al coche.
Incidencias de herramientas: primera lectura usó ruta de paquete incorrecta;
se localizó con rg --files y se corrigió. Búsqueda PowerShell demasiado amplia
por comillas se sustituyó por lectura dirigida; sin impacto en los originales.

## 0.10 — red independiente del puerto de sesión — 2026-10-28

Evidencia, corrección de interpretación y próximo ensayo:
protocol-research/NETWORK_BOOTSTRAP_010.md. Samsung lee String pero también hace
Integer.parseInt antes del observer; no hay evidencia de listas ni del valor PORT
real del AION. Nuestra formación de red no utiliza ese endpoint. Se separó
parseForNetwork de parse estricto: parámetros de red siguen validados; puerto
inutilizable queda null con categoría segura, sin sustitución ni sockets.
BLE normal/READ registran modo y categoría. La espera acotada de ventana Android
cubre P2P además de AP; errores P2P distinguen BUSY/P2P_UNSUPPORTED/ERROR.
Autenticación y proyección siguen sin implementar/verificar. Ninguna prueba
remota al coche se inició desde el ordenador; originales y material privado
permanecen excluidos de Git.

Validación: 6 Python, 55 JVM (7 core, 44 protocolo, 4 Bluetooth) y 11 Android,
72 correctas. Instrumentación real: OK (11 tests), 7,737 s, metadata local
ANDROID_ANONYMIZED. Fixtures de red no solicitan conexiones. Build y lint
correctos, 0 errores/11 advertencias. Logs privados android-build-0.10-final.log,
android-ui-build-0.10-final.log y private/ui-tests-0.10-final.txt.
Toolchain conservado: Python 3.14.2, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20,
JBR 21, SDK/build-tools 35, ADB 37.0.0. Firma apksigner verificada.

APK 0.10.0-research, versionCode 10, 24 766 231 bytes, SHA-256
`e6ca6002f86a8de9ae1ef01e8ad2ec38e37430237e6ab3d82540563f3ee7847d`.
ADB instalación Success, versión instalada comprobada; sólo paquete de pruebas
retirado. MainActivity COLD/Status ok en 346 ms. La captura UI posterior no
contiene nodos propios: no se declara verificación visual del arranque final
ni se manipuló la pantalla ajena. Las 11 pruebas instrumentadas sí pasaron.

Incidencias: una edición Python por pipe PowerShell convirtió un acento en ?
y dos sustituciones con acentos no coincidieron; corregidas mediante apply_patch
y verificadas con diff antes del build final. Consultar un log aún no creado
produjo error de lectura sin impacto. Builds repetidos sólo tras cambios reales.
Avisos CRLF/LF conocidos; git diff --check sin errores. Autorización previa de
commit a main conservada; publicación sólo de código propio y documentación.

## Prueba física 0.10: red P2P confirmada — 2026-10-28

Dos exportaciones nuevas (14)/(15), mismo proceso e historial solapado,
conservadas en privado. Hashes y cronología en
protocol-research/VEHICLE_DIAGNOSTICS_010_2026-10-28.md.
Cinco preparaciones, cuatro escrituras confirmadas, tres ServerInfo y tres redes
P2P CONNECTED verificadas por grupo/rol/SSID/MAC. Primer enlace duró 18,851177 s
antes de pérdida de grupo, sin cancelación local previa; causa remota desconocida.
Otros dos duraron 1,894929 y 5,145336 s, cancelados por nuestra política de
segundo plano. Otra espera BLE cancelada y un formulario cerrado sin envío.
Todos los ServerInfo recibidos clasifican puerto OUT_OF_RANGE. No revela el
valor exacto ni habilita endpoint/auth. Progreso 40% reportado por propietario
no equivale a autenticación; red es el nuevo hito físico confirmado.
Autenticación, medios y proyección siguen pendientes. Herramientas ADB 37.0.0,
Get-FileHash SHA-256 y revisión estática del código propio; sin incidencias.
Sólo documentación, no cambios de APK ni pruebas de código necesarias, ni
conexiones al coche iniciadas desde el ordenador. Commit a main previamente
autorizado, conservando originales y exportaciones fuera de Git.

## 0.11 — continuidad del enlace e investigación AUTH — 2026-10-28

Los diagnósticos 0.10 identificaron cancelación local de dos redes P2P conectadas
y una espera BLE cuando la Activity pasó a segundo plano. La nueva política
conserva BLE activo y P2P/AP CONNECTING o CONNECTED hasta 50 s en segundo plano;
al volver cancela el temporizador. Detener y caducidad liberan recursos. No hay
servicio persistente y Android puede terminar el proceso. El primer corte de
18,85 s no tuvo cancelación previa local: causa desconocida y no se promete
que la nueva política lo resuelva.

OUT_OF_RANGE se subdivide en ZERO, NEGATIVE, ABOVE_MAX y OVERFLOW, sin guardar
ni exportar el valor del puerto. No se asigna un endpoint ni se abre un socket.
Investigación estática adicional en protocol-research/AUTH_CHANNEL_011.md:
el cliente Samsung vincula AUTH a 57209 por tipo de canal, distinto del campo
ServerInfo entregado a CarVerifier. No existe evidencia de puerto AUTH abierto
en el AION. Faltan implementación y verificación del handshake, confirmación,
sesión y medios; no se extrajeron secretos ni se copiaron fuentes propietarias.

Validación: 6 Python, 58 JVM (9 core, 45 protocolo, 4 Bluetooth), 13 Android
instrumentadas; total 77 correctas. S25: OK (13 tests), 10,126 s, consulta
local ANDROID_ANONYMIZED, fixtures sin conexiones. Pruebas de estados no cubren
el temporizador real ni la Activity, pendiente validación del propietario.
assembleDebug y lintDebug correctos, 0 errores/11 advertencias; logs locales
artifacts/reports/android-build-0.11.log, android-ui-build-0.11.log y
private/ui-tests-0.11.txt. Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, JBR 21,
SDK/build-tools 35, Python 3.14.2, ADB 37.0.0; apksigner verify correcto.

APK 0.11.0-research, versionCode 11, 24 579 944 bytes; SHA-256
`c583b720572faccd1efdf2256ce552a65885b895c07689071caa2c6425a449f3`.
Instalación ADB Success y versión verificada; sólo paquete de tests retirado;
MainActivity COLD/Status ok, 408 ms, sin FATAL en buffer del UID propio. No
se iniciaron conexiones desde el ordenador ni pruebas con el AION. Incidencia:
primer apply_patch rechazó dos operaciones sobre el mismo archivo; se aplicó
de nuevo agrupando cambios. Sin modificación parcial. Advertencias CRLF/LF
existentes; commit a main autorizado, sin publicar diagnósticos ni APK originales.

## Primitivas AUTH offline sobre 0.11 — 2026-10-28

Se investigó Smali de ClientKeyNegotiator 3.1.04.10 para resolver la rama
normal que JADX no reconstruyó. El HMAC de respuesta combina ambas claves de
autenticación y ambos nonce; la firma del acuerdo combina ambas claves de
acuerdo y ambos nonce. Véanse fuentes y orden en
protocol-research/AUTH_CRYPTO_OFFLINE.md. Código propio añade verificación de
puntos P-256, HMAC inicial, prueba HMAC del peer y firma ECDSA, con comparación
constante y sin persistencia de PIN. Todavía son componentes aislados: no se
conectaron a la app ni envían mensajes. No hay autenticación, confirmación ni
proyección completas. No se extrajeron claves o credenciales ni copiaron fuentes.

Validación: 6 Python, 61 JVM (9 core, 48 protocolo, 4 Bluetooth) y 14 Android,
81 correctas. Vectores HMAC sintéticos calculados por separado con Python 3.14.2;
pruebas de claves, firmas y alteración, y prueba JCA del S25 con datos sintéticos.
Instrumentación OK (14 tests), 5,590 s; metadata ANDROID_ANONYMIZED. Build y lint
correctos, 0 errores/11 advertencias; apksigner verify correcto. Logs locales:
artifacts/reports/android-auth-primitives-final.log, android-auth-primitives-ui.log
y private/ui-tests-auth-primitives.txt. Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20,
JBR 21, SDK/build-tools 35, ADB 37.0.0. Sin incidencias relevantes.

Recompilación instalada en S25 conserva versionCode 11/0.11.0-research porque
no cambia el comportamiento de conexión. Nuevo binario 24 643 923 bytes,
SHA-256 `f9116eb1f265ed9b9eea62c5522399a9a86a6965f0456734cacfada7c37b36f6`.
ADB install Success; paquete de tests retirado; MainActivity COLD/Status ok
343 ms; cero FATAL de UID propio. No se hicieron conexiones al vehículo y no se
solicita repetir la misma prueba por estos componentes offline. Commit a main
autorizado, exclusivamente implementación original, tests y documentación.
