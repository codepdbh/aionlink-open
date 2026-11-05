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

## Comparación Samsung/Xiaomi/CarLife y corrección HMAC — 2026-10-28

Investigación cruzada, hashes, rutas y prioridades en
protocol-research/CONNECTION_RESEARCH_2026-10-28.md. Samsung 3.1.04.10 y
Xiaomi 4.0.14 comparten familia UCar, canal AUTH 57209 y construcción normal
observada; no prueba recepción del AION. CarLife receptor usa otra familia.
Xiaomi produce HKDF-SHA256 y Samsung usa AES-GCM en SecurityManager; siguen
pendientes derivación y confirmación interoperables, red/sockets, medios y control.
Se consultaron páginas públicas ICCOA, Baidu y documentación oficial Android;
MiAuto es receptor con SDK precompilado ausente del repo, no cliente S25.

Corrección de un error en el trabajo offline anterior: KeyNegotiator de ambas
APK usa nonce para derivar la clave HMAC, no los añade al mensaje HMAC inicial;
en respuesta usa ambos nonce en la derivación y ambas claves en el mensaje.
El código previo y sus vectores probaban una fórmula errónea, aunque consistente
consigo misma. Se corrigió y se recalcularon vectores independientemente con
Python hashlib/hmac. Nunca se enviaron esas pruebas al vehículo. La firma ECDSA
estudiada sí incluye ambas claves de acuerdo y ambos nonce. El informe original
AUTH_CRYPTO_OFFLINE.md está corregido; los registros anteriores se preservan.

Validación: 6 Python, 61 JVM (9 core, 48 protocolo, 4 Bluetooth), 14 instrumentadas:
81 correctas. S25 OK (14 tests), 5,658 s; ANDROID_ANONYMIZED. Build/lint correctos,
0 errores/11 advertencias; apksigner verify correcto. Python 3.14.2, Gradle 8.13,
AGP 8.9.1, Kotlin 2.1.20, JBR 21, SDK/build-tools 35 y ADB 37.0.0. Logs privados:
artifacts/reports/android-auth-hmac-correction.log, android-auth-hmac-correction-ui.log
y private/ui-tests-auth-hmac-correction.txt. El primer apply_patch falló por un
contexto de documentación que no coincidía; se reaplicó sin modificación parcial.

APK recompilada de investigación, versión 0.11 sin cambio funcional de conexión:
24 643 751 bytes, SHA-256
`f77c956c5b6f4673159a07b62da3cb7e50cb6ef41b11f73b2bc055c2fe4973cd`.
Instalada por ADB; paquete de tests retirado, MainActivity COLD/Status ok en
415 ms, cero FATAL del UID propio. No se inició conexión con el coche ni se
ejecutaron APK originales, copiaron fuentes/nativos o extrajeron secretos.
Queda explícitamente incompleto el objetivo de sesión/proyección funcional.

## Codec AUTH de laboratorio — 2026-10-28

Se contrastaron campos AuthRequest/AuthResponse de Samsung 3.1.04.10 y Xiaomi
4.0.14 con cabecera UCar observada. ExperimentalAuthWire implementa encoder de
solicitud y parser limitado de respuesta, con CRC, secuencia, origen, categoría,
tipos y controles de longitud. Datos propios sintéticos; no se copiaron fuentes,
credenciales o librerías de fabricantes. El lector protobuf-java 3.25.5 se usa
sólo en tests para contrastar los tags/valores. Prueba de loopback usa servidor
local sintético y un puerto aleatorio; no verifica autenticación criptográfica
entre extremos ni es una captura del coche. Más detalles en
protocol-research/AUTH_WIRE_LAB.md.

Se ejecutaron 6 Python y 66 JVM (9 core, 53 protocolo, 4 Bluetooth), 72 correctas.
Build Android y lint correctos, 0 errores/11 advertencias. Logs privados en
artifacts/reports/android-auth-wire-verified-final.log; Gradle 8.13, AGP 8.9.1,
Kotlin 2.1.20, JBR 21, SDK/build-tools 35, Python 3.14.2, ADB 37.0.0.
La consulta de módulo Python google.protobuf falló (no instalado); se contrastó
en cambio con protobuf-java en tests de Gradle. Builds repetidos tras añadir
validación de enteros/tipos y una prueba independiente; no fueron fallos del
vehículo. APK local 24 653 383 bytes, SHA-256
`25ec95dd1933112432066eb436381829a568febd02c34e320dd7896d9eb664a3`.

No se instaló la APK porque el codec sigue desconectado de la app; el S25 conserva
0.11. No se inició comunicación con el AION y no corresponde pedir otra prueba
física por este cambio. Falta integrar la negociación y verificar criptografía,
confirmación, transporte, control y medios: objetivo funcional incompleto.
Commit a main previamente autorizado, sólo código y documentación propios.

## 0.12 — diagnóstico TCP AUTH opcional — 2026-10-28

Solicitud: una APK con mejores logs de la fase posterior a Wi-Fi. Ante evidencia
estática de canal AUTH 57209 de Samsung/Xiaomi se añade comprobación opcional
del propietario, sólo tras grupo P2P validado, con diálogo separado. Android
aporta dirección de propietario e interfaz; se requiere Network que coincida con
la interfaz y se vincula el socket a esa Network. Si no existe, UNAVAILABLE y
ninguna conexión. Un intento TCP, plazo 2,5 s, sin leer/escribir payload; se
cierra al finalizar o perderse el grupo. No se exportan dirección, interfaz,
PIN ni excepciones. Estados/tiempo se exportan y se impide exportar mientras
corre. OPEN sólo significa TCP abierto; ningún estado significa sesión ICCOA.
Puede afectar al receptor y el diálogo lo indica. AP no implementado para esta
prueba. Diseño y fuentes en protocol-research/AUTH_PROBE_012.md.

Validación: 6 Python, 69 JVM (9 core, 53 protocolo, 4 Bluetooth, 3 Wi-Fi),
15 Android instrumentadas: 90 correctas. Wifi usa loopback propio para comprobar
una sola conexión sin bytes y rechazar fallo de bind sin conectarse; diálogo usa
callbacks sin red. Instrumentación S25 OK (15 tests), 6,225 s, metadata local
ANDROID_ANONYMIZED. Build y lint correctos, 0 errores/11 advertencias. Se añadió
el módulo Wi-Fi JVM al script de build. Logs privados: artifacts/reports/
android-build-0.12-final.log, android-ui-build-0.12-final.log y
private/ui-tests-0.12-final.txt. JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20,
SDK/build-tools 35, Python 3.14.2 y ADB 37.0.0; apksigner verify correcto.

APK 0.12.0-research, versionCode 12, 24 831 123 bytes, SHA-256
`34eb92ee6934da75cab4a605077b63471234247ae3fc7457817124eaea13989e`.
ADB install Success, versión confirmada; paquete de tests retirado; MainActivity
COLD/Status ok, 370 ms, sin FATAL en buffer UID propio. No se probó vehículo
desde ordenador ni se ejecutaron APK originales. En consulta web, la referencia
ConnectivityManager agotó tiempo; otras páginas API confirmaron Network y P2P.
Los builds se repitieron tras mover el botón al inicio y añadir prueba UI. No se
sabe si Android expone la Network P2P al S25; sólo una prueba física lo aclarará.
Autenticación y medios siguen incompletos. Commit a main ya autorizado; datos
privados, APK originales y artefactos no se publican.

## 0.13 — diagnósticos 0.12 y origen local P2P — 2026-10-29

Dos TXT nuevos (16)/(17), del mismo proceso y con historial solapado, copiados
en private/vehicle-tests-2026-10-29/. Hashes, horas y cronología en
protocol-research/VEHICLE_DIAGNOSTICS_012_2026-10-28.md. Dos escrituras BLE y
respuestas confirmadas; port ZERO y dos grupos P2P conectados, durante 18,694225
y 18,856022 s. Primer diagnóstico AUTH: UNAVAILABLE por no exponer Android una
Network asociada; no se intentó socket. Segundo intento sin prueba AUTH. Ambas
desconexiones tienen causa desconocida. La exportación de 0.12 conservó la red
en segundo plano. Red formada no implica sesión ICCOA.

0.13 conserva Network.bindSocket cuando hay Network correspondiente. Si falta,
consulta direcciones de la interfaz P2P pública informada por el grupo, exige
una única IPv4 local válida que comparta subred /16..30 con el propietario y
vincula el socket a dicha dirección. Si no puede determinarla, UNAVAILABLE sin
conexión. No se muestra dirección ni interfaz; se reporta ruta NETWORK o
P2P_SOURCE. Bind de dirección local no garantiza por sí mismo interfaz de salida
ante rutas ambiguas; destino sigue siendo el propietario del grupo verificado.
Prueba permanece manual y consentida, TCP único 2,5 s sin payload. No se han
verificado disponibilidad de interfaz ni interoperabilidad en el AION; OPEN sólo
significa apertura TCP. Autenticación y proyección siguen sin implementar.

Validación: 6 Python, 72 JVM (9 core, 53 protocolo, 4 Bluetooth, 6 Wi-Fi),
15 Android: 93 correctas. Selección sintética prueba subred, ambigüedad,
direcciones y prefijos inválidos; loopback prueba bind local y ausencia de payload.
Instrumentación S25 OK (15 tests), 9,196 s, ANDROID_ANONYMIZED. Build/lint
correctos, 0 errores/11 advertencias. Logs privados artifacts/reports/
android-build-0.13-final.log, android-ui-build-0.13-final.log y
private/ui-tests-0.13-final.txt. JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20,
SDK/build-tools 35, Python 3.14.2, ADB 37.0.0; apksigner verify correcto.

APK 0.13.0-research, versionCode 13, 24 676 231 bytes, SHA-256
`d8c0d92b28ec0917e6223a6295be6ab37960d43f4c78f517eb06b93d3dcc327a`.
Instalación ADB Success, versión confirmada; paquete de tests retirado;
MainActivity COLD/Status ok 435 ms; cero FATAL de UID propio. Primer build repetido
tras limitar prefijos aceptados. Fuentes Android públicas consultadas; no se
iniciaron conexiones al coche ni se ejecutaron APK de terceros. Diagnósticos y
APK originales se conservan fuera de Git; commit a main autorizado previamente.

## 0.14 — canal TCP observado y negociación offline — 2026-10-29

Diagnóstico (18) recuperado por ADB, 6802 bytes, SHA-256
`208417d632eee8aa0622c2334ecb2d5a80b202373a55ef0ffa049df107189d33`,
guardado en private/vehicle-tests-2026-10-29-b/. Cronología y límites en
protocol-research/VEHICLE_DIAGNOSTICS_013_2026-10-29.md. Primer intento:
BLE y P2P CONNECTED, TCP AUTH OPEN ruta=P2P_SOURCE en 35 ms, sin payload; red
se perdió a los 18,828590 s. Segundo intento confirmó escritura BLE y recibió
GATT 19 sin ServerInfo; causa/PIN no inferibles. El OPEN es socket, no sesión;
bind de origen local no acredita por sí solo interfaz de salida ante rutas ambiguas.

El resumen exportado 0.14 conserva contador y última prueba AUTH por proceso
entre intentos, evitando que IDLE oculte el OPEN previo. Error GATT añade estado
Android, etapa previa y confirmación de escritura, sin atribución de causa.
ExperimentalNormalAuth agrega generación de claves P-256 propias y verificación
de prueba/firma de un peer simulado, con copia temporal del PIN borrada al cerrar.
No usa red ni está conectado al flujo de la app; no deriva o confirma sesión ni
implementa medios. No se envió autenticación al coche ni se ejecutaron originales.

Validación: 6 Python, 75 JVM (9 core, 56 protocolo, 4 Bluetooth, 6 Wi-Fi),
17 Android = 98 correctas. Tests negativos con PIN simulado, firma alterada,
campos faltantes y segundo uso; fixtures de peer propias sin red de vehículo.
Android OK (17 tests), 8,985 s, ANDROID_ANONYMIZED; pruebas adicionales de JCA
y conservación de resultado sin red. Build/lint correctos, 0 errores/11 avisos.
Logs privados artifacts/reports/android-build-0.14-final.log,
android-ui-build-0.14-final.log y private/ui-tests-0.14-final.txt. JBR 21,
Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2,
ADB 37.0.0; apksigner verify correcto.

APK 0.14.0-research, versionCode 14, 24 709 051 bytes; SHA-256
`9211dcbb9abd0c6224bc51ecc6a24ae8ec716a32bd355ae6aad0865148504743`.
Instalación Success, versión comprobada, paquete de tests retirado; MainActivity
COLD/Status ok 394 ms y cero FATAL del UID propio. Búsqueda inicial bajo /sdcard
no siguió el enlace simbólico y no devolvió archivos; se localizó (18) en Download.
Build repetido después de mejorar limpieza del PIN, detalle GATT y test JCA.
No hubo prueba remota en el vehículo; no corresponde repetir la prueba sólo por
el negociador offline. Commit a main autorizado, TXT y APK originales privados.

## 0.15 — solicitud AUTH experimental — 2026-10-30

Diagnóstico (19) de 0.14, 7952 bytes, SHA-256
`6faf90284d5447679f63758aaa92bc32d96591bd936ea63e221eec19a0e4585a`,
recuperado a private/vehicle-tests-2026-10-30/. Dos intentos BLE confirmados y
dos grupos P2P, con duraciones 18,890490 y 18,776853 s. Primera prueba TCP AUTH
OPEN P2P_SOURCE, 59 ms, sin payload. Causas de desconexión desconocidas; no hay
sesión confirmada. Cronología, fuentes, condiciones y límites en
protocol-research/AUTH_EXCHANGE_015.md.

Tras seleccionar P2P conectado, el propietario puede solicitar y confirmar
AUTH experimental con el código actual. Una solicitud basada en observaciones
Samsung/Xiaomi usa claves efímeras propias, identidad propia, HMAC y firma;
workModes=0 porque no hay modos de proyección implementados. Se liga a Network
o a la dirección local inequívoca del grupo y se dirige a propietario/puerto
observado. Un intento con límite total 8 s; descarta buffers y no registra PIN,
claves, direcciones ni contenido. Respuesta con tamaño, CRC, tipo y secuencia
validados; HMAC/firma verificados. Resultado sólo informa pruebas de respuesta:
ECDH, confirmación de sesión, medios y proyección siguen sin implementar.
La solicitud puede fallar o cerrar el enlace; UI expresa consentimiento y límite.
No se contactó el vehículo desde el ordenador ni se ejecutaron APK originales.

Validación: 6 Python, 77 JVM (9 core, 56 protocolo, 4 Bluetooth, 8 Wi-Fi) y
18 Android: 101 correctas. Servidor loopback independiente valida HMAC/firma
de solicitud y genera respuesta con protobuf Google; PIN simulado incorrecto,
fallo de bind y limpieza verificados. Android OK (18 tests), 11,323 s; metadata
ANDROID_ANONYMIZED. Build/lint correctos, 0 errores/11 advertencias. Logs privados:
artifacts/reports/android-build-0.15-fixed.log, android-ui-build-0.15-fixed2.log,
private/ui-tests-0.15-fixed2.txt. JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20,
SDK/build-tools 35, Python 3.14.2, ADB 37.0.0; apksigner verify correcto.

Las dos primeras instrumentaciones fallaron una aserción de visibilidad de la
casilla anterior tras usar el nuevo diálogo. Se cerró el diálogo del fixture y
se hizo scroll explícito antes de exigir visibilidad; la tercera pasó todo.
Los logs fallidos se conservan. Se repitió el build después de reforzar limpieza
de buffers y clasificar resultados del receptor, y sólo se instaló tras build.
APK 0.15.0-research, versionCode 15, 24 739 599 bytes; SHA-256
`022902c854282b78474ca2001f44d73b26084569e2bddc56a550538765527627`.
Instalación ADB Success, versión confirmada; paquete de tests retirado;
MainActivity COLD/Status ok 470 ms, cero FATAL en UID propio. Prueba real AUTH
pendiente del propietario; commit a main autorizado. TXT y originales privados.

## 0.16 - diagnostico 0.15 e inicio AUTH automatico - 2026-10-30

Diagnostico (20), 5351 bytes, SHA-256
`1e9fbaa2703d8855c856380cfce04861faab63799631c440d2a2567c0f1ea7c7`,
conservado en private/vehicle-tests-2026-10-30/. ClientInfo confirmado,
ServerInfo P2P con port ZERO, grupo conectado y prueba TCP sin payload OPEN
P2P_SOURCE en 61 ms. AUTH experimental quedo IDLE; exportacion con red todavia
conectada. No se conoce la causa del 40%, ni se ha verificado sesion. Cronologia
y limites en protocol-research/AUTH_AUTO_016.md.

El formulario ofrece autorizacion opcional, desmarcada inicialmente, para un
solo AUTH experimental automatico al conectar P2P del intento, usando el mismo
codigo. La copia mutable se elimina al cancelar, fallar o tras 60 s, o se
transfiere al intercambio que la limpia; Android puede retener Strings temporales.
La prueba TCP ahora se distingue como sin autenticacion. El resultado real y la
interoperabilidad estan pendientes; la app todavia no deriva ni confirma una
sesion, ni implementa medios. No se inicio conexion con el coche desde el PC.

Validacion: 6 Python, 77 JVM (9 core, 56 protocolo, 4 Bluetooth, 8 Wi-Fi) y
20 instrumentadas Android = 103 correctas; pruebas UI de consentimiento y
limpieza de almacenamiento temporal. Android OK (20 tests), 13,03 s; dato
local ANDROID_ANONYMIZED. Build y lint correctos, 0 errores/11 advertencias.
JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python
3.14.2, ADB 37.0.0. Logs: artifacts/reports/android-build-0.16-final2.log,
android-ui-build-0.16.log y private/ui-tests-0.16.txt. El primer comando Python
discover apunto a tests/ y ejecuto cero pruebas; se corrigio a tests/unit.
La compilacion se repitio tras cambios del codigo y correccion de caracteres.
Apksigner verify correcto, con avisos de acceso nativo restringido del JDK.

APK 0.16.0-research, versionCode 16, 24 915 255 bytes; SHA-256
`23fe857969e3ee0cf3eee694e247a69ae85f13076b29627c89250ff58595a498`.
Instalacion ADB Success; paquete de tests retirado, MainActivity COLD/Status ok,
504 ms. Prueba de coche queda a cargo del propietario estacionado. Diagnosticos
y APK originales permanecen privados; commit a main autorizado previamente.

## 0.17 - solicitud AUTH corregida y AuthConfirm observado - 2026-10-30

Sin diagnostico nuevo: (20) sigue siendo el ultimo y no envio AUTH. Revision
estatica de Samsung 3.1.04.10 con contraste Xiaomi 4.0.14; detalle, rutas y
limites en protocol-research/AUTH_CONFIRM_017.md. Tres defectos de 0.15/0.16:
secuencia aleatoria de 32 bits (Samsung cuenta desde 1 y el par rechaza
seqId <= 0 al responder), workModes=0 (Samsung envia 257 = NORMAL_CAST |
MIRROR_WINDOW_CAST; resultado 4 = negociacion de modos fallida) y cierre del
socket sin AuthConfirm. Hipotesis no verificada: el receptor abandona el grupo
a los ~18,8 s por no completar AUTH.

0.17 envia secuencia 1 y workModes 257; tras verificar prueba y firma deriva
HKDF-SHA256(ECDH, SHA-256(nonce local || nonce receptor), "session_key", 16),
cifra "<IPv4 local del grupo>:0" con AES-GCM en el sobre observado y envia
AuthConfirm (SEND, AUTH, metodo 3, secuencia 2). Observa hasta 1,5 s solo la
cabecera de una trama siguiente y cierra. Plazo total 10 s (antes 8 s). No se
exportan clave, codigo, direccion ni payload. No se implementan canal CONTROL,
medios ni proyeccion; el envio de AuthConfirm no demuestra sesion aceptada.

Validacion: 6 Python y 80 JVM (9 core, 59 protocolo, 4 Bluetooth, 8 Wi-Fi)
correctas. Nuevas: vector RFC 5869 caso 1, sobre AES-GCM con rechazo de
manipulacion, cabecera AuthConfirm y secuencias invalidas, uso unico del
confirm; loopback con par independiente que comprueba secuencia, workModes,
deriva la clave y descifra el confirm. Build y lint correctos, 0 errores/11
advertencias. Log: artifacts/reports/android-build-0.17.log. Instrumentadas
Android no se ejecutaron en esta version (solo cambiaron textos de dialogos).
JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python
3.14.2, ADB 37.0.0. La primera edicion escribio CRLF; se normalizo a LF.

APK 0.17.0-research, versionCode 17, 24 694 632 bytes, SHA-256
`af80796c2c0a7165a98ba367bb02fd41da06ef16535e40c3f2911b01d0bc6715`.
Instalacion ADB Success; MainActivity COLD/Status ok 299 ms; cero FATAL del UID
propio. Prueba en coche pendiente del propietario con vehiculo estacionado.

## 0.18 - CONTROL, fuente Wi-Fi Display y captura de pantalla - 2026-10-30

Solicitud del propietario: implementar la etapa posterior a AUTH a partir de
las APK y hacer commit. Analisis estatico de Samsung 3.1.04.10/3.1.02.20 y
Xiaomi 4.0.14; detalle y procedencia en protocol-research/PROJECTION_018.md.
Hallazgos: tras AuthConfirm el telefono es servidor CONTROL en IP local:57219
con cuerpos cifrados por la clave de sesion, heartbeat cada 2 s,
BluetoothMacInfo al conectar y ACK de SEND_SYNC; la proyeccion es una variante
de AOSP WifiDisplaySource (RTSP Wi-Fi Display, MPEG-TS sobre RTP) con el
parametro propio wfd_car_display_mode. Puerto RTSP: Xiaomi usa 7236 y Samsung
3.1.04.10 pasa 57219 a la biblioteca nativa; discrepancia abierta, se usa 7236.

Implementado de forma independiente: UCarFrameWriter, SessionControlServer,
WfdSource (M1-M7, keep-alive, IDR, TEARDOWN), WfdFormats, TsRtpStreamer,
ProjectionService (MediaProjection en primer plano, H.264 hardware) y
ProjectionSession. Los listeners se abren antes de enviar AuthConfirm. Nuevo
boton "Preparar captura de pantalla"; la sesion de proyeccion se conserva en
segundo plano hasta Detener. Sin audio, UIBC ni SENSOR. No verificado con el
AION; ninguna prueba usa capturas del vehiculo.

Validacion: 6 Python y 86 JVM (9 core, 59 protocolo, 5 proyeccion, 4
Bluetooth, 9 Wi-Fi) correctas; 20 instrumentadas en el S25 correctas. Build y
lint correctos, 0 errores/11 advertencias (el primer build tuvo 14; se
corrigieron 3 avisos nuevos de ProjectionService). Logs:
artifacts/reports/android-build-0.18.log y android-ui-build-0.18.log. JBR 21,
Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2,
ADB 37.0.0. Un heredoc de bash fallo por comillas al crear tests; se
escribieron con el editor. Un comando PowerShell se ejecuto desde un
subdirectorio y no encontro la ruta del log; se repitio desde la raiz.

APK 0.18.0-research, versionCode 18, 25 046 851 bytes, SHA-256
`00fea06f62ff5f99aa5326d713c8127b72fdb57c637824f45c25afe1a0be2151`.
Instalacion ADB Success; paquete de tests retirado; MainActivity COLD/Status ok
383 ms; cero FATAL del UID propio. El dialogo de captura no se probo por ADB
para no interferir con el telefono. Prueba en coche pendiente del propietario
con vehiculo estacionado.

## 0.19 - tactil del vehiculo (UIBC) y audio - 2026-10-30

Solicitud del propietario: dejar listos el tactil del coche y el audio.
Analisis estatico de Samsung 3.1.04.10; procedencia en
protocol-research/TOUCH_AUDIO_019.md. UIBC: el telefono escucha TCP 4321,
el coche envia JSON plano (toques type 1 con x/y en espacio del video, teclas
con keycode); M4 declara la capacidad literal de la biblioteca. Audio: la ruta
por defecto de musica es captura de reproduccion PCM 48 kHz dentro del flujo
Wi-Fi Display (AAC o LPCM); MEDIA 57229 y SENSOR 57239 son servidores cifrados.

Implementado: UibcServer/UibcParser/UibcMapper, TouchInjectionService
(accesibilidad, gestos y teclas atras/inicio/recientes), AudioCapture
(AudioPlaybackCapture + AAC-LC/ADTS), pista AAC en TsRtpStreamer, M4 con UIBC
y preferencia AAC, escuchas pasivas MEDIA/SENSOR, permiso RECORD_AUDIO y boton
de accesibilidad. Sin LPCM, pellizco, microfono, audio por MEDIA ni avisos de
navegacion. No verificado con el AION.

Validacion: 6 Python, 92 JVM (9 core, 59 protocolo, 10 proyeccion, 4
Bluetooth, 10 Wi-Fi) y 20 instrumentadas en el S25 correctas. Build/lint
correctos, 0 errores/11 advertencias. Logs: artifacts/reports/
android-build-0.19.log y android-ui-build-0.19.log. Mismas versiones de
herramientas que 0.18. Dos ediciones con Python fallaron por escapes de \r\n y
\s en el texto buscado; se aplicaron con el editor sin cambios parciales.

APK 0.19.0-research, versionCode 19, 24 827 625 bytes, SHA-256
`04631d9a3e225bfb816832c6bbc4a642d4db379742674a482f6b100d35ec21f3`.
Instalacion ADB Success; paquete de tests retirado; MainActivity COLD/Status ok
364 ms; cero FATAL; dumpsys accessibility lista TouchInjectionService como
servicio instalado (no activado). Prueba en coche pendiente del propietario.

## 0.20 - RTP sobre TCP tras bloqueo local en M3 - 2026-11-02

Diagnosticos de 0.19 copiados a private/vehicle-tests-2026-11-02/: (21), 5254
bytes, SHA-256 `66eb9e09fab2fe43ae866c1cf9dc78dfdb3bcb1aaa7a708a74dd532ca5f74f47`
(GATT 19 esperando ServerInfo) y (22), 15 640 bytes, SHA-256
`0e54e0ce64e04dc64b48ae2378f8d0e7c6071e41c9688cdd863f6a880dab11a1`. En (22) AUTH
verifica prueba y firma con resultado 0, CONTROL recibe heartbeats descifrados y
RTSP llega a M3_UNSUPPORTED (puertoRTP=false); AionLink no envia M4 y el coche
cierra a los ~10,07 s. El propietario informa 80 % y un mensaje de dispositivo no
compatible, sin texto literal capturado. El logcat por UID salio vacio (0 bytes).

Analisis estatico de MiAuto 0.1.1, cuyo sink ucarhusdk coincide en tres literales
con lo reportado por el coche; misma familia es inferencia, no version confirmada.
Ese sink elige RTP/AVP/TCP;unicast, escucha en client_port, enmarca con 16 bits de
longitud y exige server_port par-impar. En su SDK 80 % = canales iniciados, 90 % =
sink con video y hay temporizador de conexion de 10 s. Que el mensaje del coche
derive de ese temporizador es hipotesis. Detalle y direcciones en
protocol-research/RTP_TCP_020.md. El valor real de wfd_client_rtp_ports no consta
en 0.19 y ahora se exporta. Sesion y proyeccion siguen sin verificar en el AION.

Implementado: transporte TCP unicast, conexion al coche en SETUP, enmarcado,
server_port siempre en par y diagnostico ampliado. Interleaved no implementado.

Validacion: 6 Python y 95 JVM (9 core, 59 protocolo, 13 proyeccion, 4 Bluetooth,
10 Wi-Fi) correctas; las 3 nuevas de WfdSource usan sink sintetico por loopback.
Build y lint correctos, 0 errores/10 advertencias. Log:
artifacts/reports/android-build-0.20.log. Instrumentadas Android no ejecutadas.
Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, JBR de Android Studio, Python 3.14, ADB
37.0.0; apktool 3.0.3 y JADX 1.5.6 con SHA-256 verificado; capstone 5.0.3 y
pyelftools 0.31 solo en el scratchpad de la sesion. Gradle instalo automaticamente
SDK Platform 35 y Build-Tools 35.0.0.

APK 0.20.0-research, versionCode 20, 24 844 005 bytes, SHA-256
`44e804872e6e3544d5d99b42fe751c3e8b3919cb853f3fad59ff658719bf0698`, certificado
debug `e1092cde24dac3e15ebb0cafb70378157de13d5c8dfc696b12f4fed0479fc573`.

Incidencias: ADB aparecio unauthorized hasta que el propietario autorizo. El primer
escaneo de cadenas con regex se detuvo por lento y se sustituyo por busqueda directa.
Dos llamadas al listado de ensamblado fallaron por faltar argumentos y se repitieron.
JADX sobre MiAuto termino con 18 errores y salida parcial util (5183 clases). Esta
copia es un ZIP sin .git, private/ ni artifacts/ anteriores. `adb install -r` fallo con
INSTALL_FAILED_UPDATE_INCOMPATIBLE: la 0.19 instalada tiene otra clave debug. No se
desinstalo sin autorizacion; instalacion y prueba en coche quedan pendientes.

Actualizacion: el propietario autorizo desinstalar e instalar. Se reemplazo la 0.19;
se perdieron permisos y accesibilidad de la app, y siguen los 23 TXT de Download.
La verificacion en el S25 mostro v0.19.0 en pantalla: la version estaba fija en dos
textos de MainActivity (uno en la cabecera del diagnostico) y en la cabecera RTSP
Server, que aun decia 0.18. Se corrigieron y se repitio el ciclo.

Binario final 0.20.0-research, versionCode 20, 25 146 880 bytes, SHA-256
`de8bdf24422bd567794a8947bc7701f5e05c13b369d1712691ef1d898d4abf37`, sustituye al
indicado arriba; mismo certificado debug y apksigner verify correcto. Validacion
final: 6 Python, 95 JVM y 20 instrumentadas en el S25, OK (20 tests), 12,143 s; 121
correctas. Build y lint correctos, 0 errores/10 advertencias. Logs:
artifacts/reports/android-build-0.20-final.log, android-ui-build-0.20-final.log y
private/ui-tests-0.20-final.txt. ADB install Success; paquete de tests retirado;
MainActivity COLD/Status ok 285 ms; XML private/ui-0.20-final.xml con 25 nodos
propios y v0.20.0; cero lineas en el buffer crash del UID propio. Ese buffer se habia
vaciado con logcat -c antes de la primera apertura: afecto a todo el telefono y
elimino trazas de crash retenidas de otras apps, contra la practica del colector.
No se inicio busqueda, GATT, P2P ni AUTH; la prueba con el AION sigue pendiente.

## 0.21 - Desconexion pedida por el coche con video en curso - 2026-11-02

Diagnostico de 0.20 copiado a private/vehicle-tests-2026-11-02/: (23), 8182 bytes,
SHA-256 `f7a100c48c4301f1398b86b96f4665c8eb10da8861fb870c08607b5c77329901`. Hardware:
el receptor elige RTP/AVP/TCP;unicast, RTSP llega a PLAYING y el flujo TCP no falla
(1159 unidades, 12 376 RTP, 1668 AAC a los 36 s). El propietario informa de un error
en pantalla, sin texto capturado, y de que no se transmitio nada: no hay proyeccion
ni sesion ICCOA verificada. CONTROL muestra dos metodos 23 (disconnect), que 0.20
ignoraba; el logcat por UID volvio a salir vacio (0 bytes). Causa del error sin
determinar. Detalle en protocol-research/CAR_DISCONNECT_021.md.

JADX 1.5.6 sobre Samsung Auto 3.1.02.20 (SHA-256 `0f57eec4...f755`): 5816 archivos en
extracted/, 44 errores de decompilacion, salida parcial util, exit 1. Sin ejecutar APK.

Incidencias: primer Gradle fallo con JDK 25 (no admitido por Gradle 8.13) y despues por
falta de SDK; resuelto con JAVA_HOME del JBR 21.0.10 de Android Studio y ANDROID_HOME,
sin crear local.properties. :transport-usb:lintDebug da 2 errores NewApi previos en
UsbAccessoryClient.kt:156 (Os.fcntlInt requiere API 30, minSdk 29), archivo no tocado y
sin corregir: esa ruta USB fallaria en Android 10. El "lint correcto" de 0.20 solo
cubria :app. Lint 0.21 de :app 0 errores/11 advertencias; :transport-wifi sin avisos.

Binario 0.21.0-research, versionCode 21, 24 860 393 bytes, SHA-256
`1aa90fa547181db411eaea7fd7a7d273b212df6073d5643b6b1c9a4d1d6cdbb5`, mismo certificado
debug. Validacion: 98 JVM y 20 instrumentadas en el S25 (OK, 20 tests), con fixtures
sinteticos. Instalacion con install -r: permisos y accesibilidad conservados; paquete
de tests retirado; MainActivity COLD/Status ok 446 ms; v0.21.0 visible; cero lineas en
el buffer crash del UID propio. Logs: artifacts/reports/android-build-0.21-final.log,
android-ui-build-0.21.log y private/ui-tests-0.21.txt.

Incompleto: la solicitud de configuracion (metodo 24) y la detencion por metodo 23 solo
se probaron con coche sintetico. No se inicio busqueda, GATT, P2P ni AUTH; la prueba
con el AION sigue pendiente. Commit a main pedido por el propietario; TXT, APK y
extracted/ siguen privados.

## 0.22 - Modo integrado (workModes 4) tras el error en pantalla - 2026-11-02

Diagnostico de 0.21 copiado por ADB a private/vehicle-tests-2026-11-02/: (24), 15 619
bytes, SHA-256 `07973590646f335640d5e3cc7f4c95150962d90791df2fc0ffbccafcc61ce039`, y tres
fotografias del propietario (no copiadas al repositorio). Hardware: el coche muestra "Your
phone does not support integrated desktop screen casting" a 80 %, declara workModes=4 en
su configuracion y responde 0x1 en AUTH. Sin video cierra a unos 10 s; con video seguia
conectado a los 44 s, sin metodos 4, 23, 26, 28 ni 30. Sigue sin imagen y sin sesion
ICCOA verificada. Hipotesis y evidencia en protocol-research/FUSION_MODE_022.md.

Analisis estatico con llvm-objdump del NDK 28.2.13676358 sobre libovmsink.so extraida al
scratchpad, sin cargarla. Samsung 3.1.02.20 declara solo el modo 1 y no responde a 26, 28
ni 30: no hay referencia local de telefono con modo 4.

Incidencias: `adb` no esta en PATH (se usa la ruta del SDK). El primer recuento de JVM dio
118 porque incluia 16 resultados antiguos de testReleaseUnitTest; el valido es 102. Sigue
sin corregir el error NewApi de UsbAccessoryClient.kt:156 anotado en 0.21.

Binario 0.22.0-research, versionCode 22, 24 860 389 bytes, SHA-256
`eb615ec1fb25d22b5f03e878c5001afef71fb171cb79f073960de4bf079da602`. Validacion: 6 Python y
102 JVM (82 + 20) correctas, con fixtures sinteticos. Lint de :app 0 errores/11
advertencias; :transport-wifi sin avisos. Las 20 instrumentadas no se repitieron: la
interfaz solo cambia el numero de version. Instalacion con install -r: permisos y
accesibilidad conservados; MainActivity WARM/Status ok 242 ms; v0.22.0 visible; cero
lineas en el buffer crash del UID propio.

Incompleto: workModes 261 y las respuestas a 26, 28 y 30 solo se probaron con coche
sintetico. No se inicio busqueda, GATT, P2P ni AUTH desde el PC; la prueba con el AION
sigue pendiente. Commit a main pedido por el propietario; TXT, fotografias, APK y
extracted/ siguen privados. El coche no admite instalar aplicaciones: todo el codigo
corre en el telefono y el receptor ICCOA de fabrica no se modifica.

Prueba de 0.22 en el AION: diagnostico (25), 9592 bytes, SHA-256
`e50f48a53742791be70fa00276b939ce11eb0def4cd805f9d60337d849fd8c46`. Hardware: AUTH responde
workModes=0x4, M3 trae iccoa_support_fusion y el coche envia los metodos 26 y 28. No envia
4 ni 30 en 31 s: sin imagen y sin sesion ICCOA verificada. Falta probar si se abre al pulsar
en la pantalla del coche. El propietario informa de que en esta prueba no aparecio el aviso
"integrated desktop" ni ninguna otra cosa tras "connected".

## 0.23 - El telefono anuncia la app al coche - 2026-11-02

Diagnostico de 0.22 copiado por ADB: (26), 9550 bytes, SHA-256
`bd0568971a364686a6da83c43325080f108e517052fdbeafb76b31e5adb77a2c`. Hardware: workModes=0x4
otra vez y metodos 26 y 28 dos veces; sin 4 ni 30, sin imagen y sin sesion ICCOA verificada.
Fotografia del propietario: las tarjetas con "connected" y el aviso de desconexion de
Bluetooth, ya presente en la prueba de 0.21. No consta en el diagnostico si se pulso algo.

Defectos propios corregidos: 0.22 enviaba la hora en AppStateChanged.timestamp, que
VideoPlayerV2 compara con el tiempo del video, y usaba id 0, el sustituto del modo normal.

Binario 0.23.0-research, versionCode 23, 24 860 393 bytes, SHA-256
`0f44f816d08a485ffdea569cdbaf42b44ab2dc1b205d75400a15018d33df6aa2`. Validacion: 6 Python y
102 JVM (82 + 20) correctas, con fixtures sinteticos. Lint de :app 0 errores/11
advertencias; :transport-wifi sin avisos. Instrumentadas no repetidas.

Incompleto: ningun cliente local envia estos anuncios sin peticion; es un experimento, solo
probado con coche sintetico. Prueba con el AION pendiente. Sin commit de esta version.

## 0.24 - Retirada del anuncio y captura obligatoria - 2026-11-02

Diagnosticos de 0.23 copiados por ADB: (27), 10 389 bytes, SHA-256
`3009a717e4b99c53e46c6dd8f5ef6a0efd9fff2ddf1ceb220fe66c8d60699691`, y (28), 16 112 bytes,
SHA-256 `275ba1f4ce6560943cbd25a0a20756b56c8c2d4faf78123774fb798f45387cf6`. Hardware: el
intento 1 se cierra 84 ms despues de enviar AppStateChanged; el intento 2 da
START_SecurityException, sin video, y se cierra a los 10 s. El propietario informa de un
error "no compatible o algo asi", sin texto exacto ni foto: no consta a cual de los dos
cierres corresponde. 0.23 fue un retroceso respecto a 0.22, que seguia conectada a los 31 s.

Binario 0.24.0-research, versionCode 24, 24 860 389 bytes, SHA-256
`bac0e107b06063c65338d9c0f33603ff8266b61af8ace1bedf6f06054c887b52`. Validacion: 6 Python y
102 JVM (82 + 20) correctas, con fixtures sinteticos. Lint de :app 0 errores/11
advertencias; :transport-wifi sin avisos. Instrumentadas no repetidas. Instalacion con
install -r: permisos y accesibilidad conservados; MainActivity COLD/Status ok 292 ms.

Incompleto: el bloqueo por captura sin preparar no tiene prueba automatica ni se ha
ejecutado en el telefono. Sigue sin saberse si el coche abre la app al pulsar su pantalla.
Sin imagen y sin sesion ICCOA verificada. Sin commit de 0.23 ni 0.24.

## 0.25 - Tipo de AppStateChanged tras abrir la app en el coche - 2026-11-02

Diagnosticos de 0.24 copiados por ADB: (29), 11 675 bytes, SHA-256
`672dfb8e7e1521094164f25f619f0d019cfd6e765f57d09dcef3129273e8b23d`; (30), 10 537 bytes,
SHA-256 `f22517e8b3d6221d807a9e9789869fb4d32393b35505f3a44d2b2913e159e91d`; (31), 10 969
bytes, SHA-256 `7719549bba176ab4ae6d30e47f5792c2e7936158f93fe28c26c85367aebe35b8`.

Hardware: el coche muestra "AionLink Open" y el icono "AionLink" (foto del propietario, no
copiada). Al pulsarlo envia los metodos 4 y 30, y cierra toda la sesion 63 ms y 66 ms despues
en dos intentos. El bloqueo de 0.24 sin captura se ejecuto en el telefono y funciono. Sigue
sin imagen y sin sesion ICCOA verificada.

Defecto propio: AppStateChanged iba como SEND y parseAppStateChangedMessage exige SEND_SYNC.
Causa del cierre sin demostrar: en MiAuto 0.1.1 esa excepcion se captura sin cerrar el canal;
el coche usa SDK v2.0.2. Detalle en protocol-research/FUSION_MODE_022.md.

Binario 0.25.0-research, versionCode 25, 24 860 393 bytes, SHA-256
`64adaadd33308cc220e5a4edd136409f5e58e25b7627649d738713a518092787`. Validacion: 6 Python y
102 JVM (82 + 20) correctas, con fixtures sinteticos. Lint de :app 0 errores/11
advertencias; :transport-wifi sin avisos. Instrumentadas no repetidas. Instalacion con
install -r: permisos y accesibilidad conservados; MainActivity WARM/Status ok 291 ms; cero
lineas en el buffer crash del UID propio. Prueba con el AION pendiente. Commit a main de
0.23, 0.24 y 0.25 pedido por el propietario, sin esperar a esa prueba; TXT, fotografias, APK
y extracted/ siguen privados.

### Prueba de 0.25 en el AION - 2026-11-03

Diagnosticos copiados por ADB a private/vehicle-tests-2026-11-03/: (32), 2758 bytes, SHA-256
`53a457d9e938a47ffb7ccdf7d122867ddeeec79414c35c268cf28392c575d375`, sin sesion; y
aionlink-diagnostics2.txt, 11 390 bytes, SHA-256
`98b807b1ab5ac9373e8b26a52cb1cdd0bb550224f4238db5f2041d01da715f05`. Hardware: el propietario
informa de que la proyeccion funciono "casi perfecto"; ACK de AppStateChanged en 7 ms y 57 s
de sesion hasta Detener. UIBC 0 eventos. Verificacion de imagen por informe del propietario.

Pendiente: el propietario pide ahorrar bateria, con la pantalla del telefono apagada, mediante
un modo Android Auto. Android Auto exige un receptor certificado por Google; emularlo requiere
credenciales privadas y queda fuera de AGENTS.md, por lo que no se implementa. Samsung Auto
declara permisos de sistema (CAPTURE_VIDEO_OUTPUT, INTERNAL_SYSTEM_WINDOW, ADD_TRUSTED_DISPLAY,
DEVICE_POWER) que explican su pantalla independiente y no estan al alcance de una app normal.
Alternativas sin implementar, a decision del propietario; sin cambios de codigo en esta entrada.

## 0.26 - Lag, ahorro y sonda de pantalla propia - 2026-11-03

El propietario confirma que el tactil funciona, aunque el diagnostico anterior registra 0 eventos
UIBC: no consta por que; esa sesion pudo no incluir toques. Descarta Shizuku y pide seguir con el
plan sin eliminar el compartir pantalla. Open-headunit se descarta: incluye certificado y clave
privada (res/raw/cert y privkey, contenido no abierto) y licencia AGPL-3.0. Detalle en
protocol-research/LAG_SAVINGS_026.md.

Binario 0.26.0-research, versionCode 26, 24 893 193 bytes, SHA-256
`afebd093da1cfdf62eb9a75173ccad9ceb71161b7294c7eca7ccc171ac1c1610`. Validacion: 6 Python, 102
JVM (82 + 20) y 3 instrumentadas nuevas en el S25 (OK, 3 tests): codificador 90,99 cuadros/s sin
limite y 30,00 con limite. Las 20 instrumentadas anteriores no se repitieron. Lint de :app 0
errores/16 advertencias (11 previas y avisos de estilo en PhoneSavings); :transport-wifi sin
comprobar en detalle, compilado sin error. WRITE_SETTINGS se concedio con appops solo para la prueba y
se devolvio a default; brillo 70 y modo 1 iguales al final; paquete de tests retirado. Instalacion
con install -r: permisos y accesibilidad conservados; MainActivity COLD/Status ok 321 ms; cero
lineas en el buffer crash del UID propio.

Incompleto: ninguna mejora probada en el coche. Tamano de imagen sin cambiar a falta de evidencia.
Sonda sin probar con el telefono bloqueado. Sin commit de 0.26.

## 0.27 - Tactil caido por la prueba y sonda de dibujo directo - 2026-11-03

Diagnosticos de 0.26 copiados por ADB a private/vehicle-tests-2026-11-03/:
aionlink-diagnostics3.txt, 23 618 bytes, SHA-256
`4e42d7ddae43f0faae8ef60d87ec9ef93639efbff8f049b929bf55a7735e77c0`, y aionlink-diagnostics4.txt,
24 192 bytes, SHA-256 `51f26b407697e9aa2ef78dc65a1829ed01d81bd4a89733c8630c38b207fef818`. Hardware:
mejor transmision segun el propietario, 31 cuadros/s y 0 escrituras lentas; 159 eventos UIBC y 0
aplicados; video detenido con el telefono bloqueado. Detalle en protocol-research/LAG_SAVINGS_026.md.

Error propio: las instrumentadas de 0.26 dejaron TouchInjectionService en Crashed services
(inferencia por `am instrument`). Tras instalar 0.27 esta en Bound services y Crashed services esta
vacio; tactil sin comprobar en el coche.

Binario 0.27.0-research, versionCode 27, 24 893 213 bytes, SHA-256
`91fabf05b991ec54bdf36728bdc89b556119b0bb01c0cf185ec32d8f3882dc50`. Validacion: 6 Python y 102 JVM
(82 + 20). Lint de :app 0 errores/16 advertencias. Instrumentadas no ejecutadas a proposito; compiladas.
MainActivity WARM/Status ok; cero lineas en el buffer crash del UID propio. Incompleto: dibujo directo sin
ejecutar. Sin commit de 0.26 ni 0.27.

## 0.28 - Lanzador propio sobre la pantalla compartida - 2026-11-03

Binario 0.28.0-research, versionCode 28, 24 958 929 bytes, SHA-256
`f5cafbbb5e1d04b33ca4e2a60736e3c0a01ac607f1d5fd5c12faa8dd732041fb`. Validacion: 6 Python y 104 JVM
(84 + 20). Lint de :app 0 errores/26 advertencias. Instrumentadas no ejecutadas a proposito; compiladas.
Instalacion con install -r: v0.28.0 visible; servicio tactil activo y Crashed services vacio; cero
lineas en el buffer crash del UID propio.

Incidencias: durante la comprobacion por ADB el lanzador aparecio en modo edicion tras un solo toque y
user_rotation leyo 1 un momento; despues leia 0 con el telefono en vertical, ninguna app elegida y otra
app en primer plano. Pudo deberse a que el propietario usaba el telefono; sin confirmar. La captura de
pantalla fallo por la redireccion de PowerShell y no se repitio para no interferir.

Incompleto: lanzador, botones superpuestos y horizontal sin probar en el coche. Sonda de dibujo directo
sin ejecutar. Commit a main de 0.26, 0.27 y 0.28 pedido por el propietario, sin esperar a esa
prueba; TXT, fotografias, APK y extracted/ siguen privados.

## 2026-11-05 - diagnostico 0.28 y plan de pantalla apagada / Android Auto

Diagnostico `aionlink-diagnostics5.txt` recuperado del S25, 11 503 bytes, SHA-256
`31203205e5d15148aa9ea1ae30395481977e3113a6f4cfd40ce0001e7a62e14b`, preservado en
private/vehicle-tests-2026-11-05/. Snapshot 2026-11-04 03:10:23 UTC (3 de noviembre,
23:10 en La Paz). versionCode instalado 28. Registra prueba/firma AUTH verificadas y
CONFIRM_SENT, CONTROL conectado, RTSP PLAYING, 1740 unidades de video a 30 fps, 2709
tramas AAC, 35 eventos tactiles aplicados de 35, y lanzador abierto; al exportar el
enlace seguia activo. Son registros del telefono, no prueba independiente de la
imagen, audibilidad o estabilidad posterior. El cierre del socket AUTH despues de
CONFIRM no prueba por si solo fallo de los canales siguientes.

Se elaboro `architecture/PLAN_ANDROID_AUTO_Y_PANTALLA_APAGADA.md`: conserva espejo,
prioriza requisito de pantalla fisica apagada y evalua Android Auto real mediante
criterio GO/NO-GO; interfaz propia alternativa no se presenta como Android Auto.
Fuentes oficiales enlazadas en el plan. No se ejecutaron simulaciones ni se cambio
codigo, APK o configuracion del telefono; no se corrieron tests de codigo. ADB
37.0.0, analisis de archivos locales y documentacion publica. Sin errores conocidos.

## 0.29 - inicio de la sonda H264 con pantalla apagada - 2026-11-05

Se inicio el plan de pantalla apagada sin modificar la ruta existente de proyeccion.
`DirectEncoderProbe` dibuja un patron sintetico en un Surface de MediaCodec H.264,
sin MediaProjection ni red. La sonda local informa por intervalo cuadros enviados al
encoder, cuadros codificados, edad de la ultima salida, timestamps no crecientes,
estado interactivo y bloqueo; los contadores de entrada y salida permiten distinguir
el posible repetido automatico de un cuadro. Maximo 11 minutos, bloqueo parcial de
CPU acotado a 12 minutos y boton de parada. `isInteractive` es una aproximacion al
estado de uso, no prueba independiente del estado fisico de cada pantalla. Si Android
termina el proceso en segundo plano, se puede perder el diagnostico: no hay servicio
foreground de la sonda. No se realizo aun una prueba larga con pantalla apagada.

Por incompatibilidad de firma, `adb install -r` de la APK normal sobre el paquete
existente devolvio INSTALL_FAILED_UPDATE_INCOMPATIBLE. No se desinstalo ni cambio la
app original. Una variante opcional `-PaionlinkLab`, paquete org.aionlink.open.lab,
etiqueta AionLink Sonda, se instalo por separado y queda para prueba local; no es
Android Auto ni un modo de proyeccion. El paquete de pruebas lab se retiro; paquete
original permanece versionCode 28, servicio tactil Bound y Crashed services vacio.

Validacion: 6 Python, 104 JVM (9 core, 59 protocolo, 16 proyeccion, 4 Bluetooth,
16 Wi-Fi), compilacion y lint app 0 errores/26 advertencias. Una prueba instrumentada
de encoder real sintetico en el paquete lab paso en 4,07 s con pantalla encendida,
incluidos cuadros generados/codificados y orden de timestamps. No verifica pantalla
apagada, proyeccion, sonido ni vehiculo. Log private/ui-test-0.29-final.txt; builds
artifacts/reports/android-build-0.29-final3.log y android-lab-build-0.29-final3.log.
JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2,
ADB 37.0.0. Se repitio build tras cambios de fuente/etiqueta. APK normal 24 958 925
bytes SHA-256 `bbb5e95786c1a6e05a78ea139678531a2f924fda6116912ab671d81dd40ed9f8`,
no instalada; APK laboratorio 24 958 933 bytes SHA-256
`f02cd8e1e3fd5ce563499889bd53a9da2bf5fa88108061948e43bfce62f6b3b0`, instalada.
El puente Android Auto y el modo de interfaz propia en el coche siguen pendientes.

## 2026-11-05 - investigacion Android Auto self mode

Consulta documental de Google DHU y proyecto independiente Open Headunit, sin ejecutar
terceros ni abrir o copiar secretos. Open Headunit afirma soportar un receptor en el
mismo telefono mediante servidor Android Auto de desarrollo, sujeto a version; esto
corrige la impresion previa de que solo existian receptores de ejemplo en PC. AARE
documenta restricciones de autenticacion TLS: son observaciones de terceros, no
especificacion oficial ni verificacion en el S25. Falta una via autorizada para
identidad del receptor y video/audio/entrada, y comprobar pantalla apagada. Detalle y
fuentes en protocol-research/ANDROID_AUTO_SELF_MODE_2026-11-05.md; no se implemento
Android Auto ni se ejecuto una simulacion. ADB 37.0.0 no detecto dispositivo al consultar;
version Android Auto del S25 desconocida. Sin cambios de codigo o APK, tests no ejecutados.

## 2026-11-05 - simulacion inicial de fuente para puente

El S25 se reconecto: dumpsys publica Android Auto 17.9.664044-release y AionLink
Sonda 0.29. No se encontro nuevo diagnostico de sonda, por tanto pantalla apagada
sigue sin probar. No se inicio una conexion real a Android Auto ni al coche.

Se agrego `ExperimentalEncodedBridge`, prototipo JVM aislado que recibe frames
supuestamente H.264 Annex-B y AAC ADTS y los entrega al multiplexor TS/RTP propio,
con sesiones aisladas, controles de tamano, secuencia y timestamps. No se ha conectado
al flujo de proyeccion ni a la interfaz de la app: no altera el espejo que funciona.
Tests sinteticos comprueban PID de audio/video y cabeceras, rechazo de frames fuera
de orden y callbacks de sesiones anteriores. No validan codec, decodificacion,
sincronizacion real ni pantalla apagada. Limitaciones y procedencia en
protocol-research/ENCODED_BRIDGE_SIMULATION_2026-11-05.md.

Validacion: Python 6, JVM 106 (9 core, 59 protocolo, 18 proyeccion, 4 Bluetooth,
16 Wi-Fi), build y lint app correctos: 0 errores/26 advertencias. El test de
proyeccion se repitio al reforzar la comprobacion de PID; resultados sin fallos.
Logs artifacts/reports/android-bridge-sim-0.29.log y android-bridge-test-final.log.
JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2,
ADB 37.0.0. APK normal de build 24 975 309 bytes SHA-256
`cfcaf0be7c8535e8f22b3f269dd6e11a34a66d6cfd23113762866a71b41d2ffe`; no instalada:
la firma sigue siendo diferente de la app original. No se cambiaron paquetes, no se
ejecutaron APK de terceros ni pruebas hardware o simulaciones de Android Auto real.

## 2026-11-05 - servidor Android Auto de desarrollo iniciado por el propietario

El propietario activo manualmente el servidor para desarrolladores. Lectura ADB del
S25: Android Auto 17.9.664044-release, servicio DeveloperHeadUnitNetworkService en
foreground y puerto TCP 5277 en escucha con UID de Android Auto. Una conexion local
unica mediante `toybox nc -z -w 2` devolvio exit 0, sin enviar mensajes de aplicacion;
el puerto seguia escuchando despues. La primera consulta de ayuda `nc -h` fallo
(opcion desconocida); `nc --help` aclaro las opciones. ADB 37.0.0, toybox 0.8.13.
Esto confirma disponibilidad TCP local, no negociacion, autenticacion, video, audio,
entrada ni pantalla apagada. No se detecto un diagnostico nuevo de la sonda. No se
ejecutaron APK de terceros desde el ordenador, ni se cambiaron codigo o APK; no hubo
conexion al coche ni extraccion de credenciales. Tests de codigo no aplican.

## 2026-11-05 - respuesta de version Android Auto del S25

Implementada sonda aislada de version Android Auto, sin TLS ni credenciales, basada en
observaciones de aasdk y documentacion publica, con longitudes y tiempo limitados;
ver protocol-research/AA_VERSION_PROBE_2026-11-05.md. No se integra al espejo ni
implementa receptor completo. Primera prueba local TIMEOUT 2,5 s, sin identificacion
de fase; se solicito al propietario reiniciar servidor y acepto. Segunda prueba con
la sonda de fase obtuvo RESPONSE 1.7, result=0 en 0,059 s. Este resultado solo prueba
respuesta de version, no autenticacion, sesion, video ni pantalla apagada.

Validacion: Python 6; JVM 108 (9 core, 59 protocolo, 20 proyeccion, 4 Bluetooth,
16 Wi-Fi), build y lint app 0 errores/26 avisos. Instrumentacion de hardware opt-in
en paquete lab: primer timeout (no es exito de protocolo); segunda prueba OK (1 test).
Paquete de pruebas retirado, original 0.28 preservado y servicio tactil Bound, sin
Crashed services. Fuentes sinteticas prueban respuesta, fragmentacion de lectura y
rechazo de cabeceras invalidas o excesivas. No se inicio vehiculo ni APK de terceros,
no se uso identidad ajena ni se extrajeron secretos. Logs privados
private/aa-version-probe-2026-11-05.txt y aa-version-probe-2026-11-05-second.txt;
build artifacts/reports/android-aa-version-final.log y android-aa-version-lab-final.log.
JBR 21, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2,
ADB 37.0.0. APK normal 24 975 309 bytes SHA-256
`c0eac2a9d128a6689624b34c69cb58b3c41f25e9fb0997a6a7f929e24c698e1b`, no instalada
por firma distinta. APK lab 24 975 317 bytes SHA-256
`64c8411d1a1542ffc79b09ed98c8c571b342dd93be1f7a0351bff38dc57334bd`, instalada.
Autenticacion legitima, receptor de medios, puente y pantalla apagada pendientes.

## 0.30 - opcion de compartir una ventana y diagnostico - 2026-11-05

Documentacion oficial MediaProjection: Android 14+ ofrece compartir una app o
pantalla completa por defecto; bloquear puede detener la sesion. La captura de
audio depende del uso y politica del emisor. Fuentes y limites en
protocol-research/APP_CAPTURE_030.md. El usuario propone enviar Open Headunit al
AION: es una hipotesis de captura, no se ha ejecutado, comprobado ni integrado.

UI anade opcion experimental que informa al propietario y abre el selector Android
existente; el espejo anterior sigue disponible. Se registra etapa, tamano y
visibilidad informados por MediaProjection, sin identidad ni contenido. Se protege
la referencia activa frente al callback de fin de una captura anterior. No se
adapta automaticamente el formato ni el tactil de una ventana; no se ha demostrado
pantalla apagada ni receptor Android Auto. No se ejecutaron APK de terceros ni se
modifico coche. No aparecio diagnostico nuevo de la sonda anterior.

Validacion: 6 Python, 108 JVM (9 core, 59 protocolo, 20 proyeccion, 4 Bluetooth,
16 Wi-Fi), lint app 0 errores/26 advertencias; test Android de dialogo OK (1 test)
en S25, sin conceder captura ni probar coche. JBR 21, Gradle 8.13, AGP 8.9.1,
Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2, ADB 37.0.0. Logs en
artifacts/reports/android-build-0.30.log, android-lab-build-0.30.log y
private/ui-test-0.30-guide.txt. El primer parche no se aplico por incluir el mismo
archivo dos veces; se dividio sin cambios parciales.

APK normal versionCode 30, 24 991 693 bytes SHA-256
`aabe8dc2cb4e0bd032402675c708e8ea2c974b517d3219e404c4a19af924afaf`, no instalada
por incompatibilidad de firma. APK lab 24 991 697 bytes SHA-256
`91f9810524d2f0d54a50e4598e7def36f8db8daf2e80f68f0d59fa0fb3322f67`, instalada;
paquete de tests retirado, app original 0.28 y servicio tactil Bound conservados.

## 0.31 - pantalla virtual propia, investigacion DeX - 2026-11-05

Samsung documenta DeX inalambrico para el S25 con receptor Miracast; Android
documenta restricciones de displays privados. No se ha identificado API publica
que entregue a una app normal el escritorio DeX en display virtual privado; tampoco
se sabe si el AION admite Miracast. `dumpsys display` no mostro pantallas Wi-Fi
activas, sin coche cerca; no significa incompatibilidad. Fuentes y limites en
protocol-research/DEX_VIRTUAL_PROBE_031.md. No se ejecuto DeX, APK de terceros,
ni se cambio la configuracion de Samsung o el vehiculo.

Implementada sonda separada: Presentation propia en display privado a un encoder
H264, contadores de dibujos/salida e interactividad cada 5 s, duracion maxima
11 min. No es DeX, no se transmite al coche, no solicita MediaProjection y no
cambia el espejo. Sin servicio foreground: Android podria terminar el proceso y
perder datos. Primera prueba instrumentada con pantalla encendida obtuvo mas de
cinco dibujos y cuadros H264 en cuatro segundos: OK (1 test), 4,178 s. El usuario
indico que aun no hizo la prueba larga de pantalla apagada; ese resultado sigue
pendiente. El servicio tactil de la app original sigue Bound, Crashed services vacio.

Validacion: Python 6, 108 JVM (9 core, 59 protocolo, 20 proyeccion, 4 Bluetooth,
16 Wi-Fi); build y lint app 0 errores/26 advertencias. JBR 21, Gradle 8.13,
AGP 8.9.1, Kotlin 2.1.20, SDK/build-tools 35, Python 3.14.2, ADB 37.0.0.
Logs artifacts/reports/android-build-0.31.log y android-lab-build-0.31.log;
instrumentacion privada private/ui-test-0.31-virtual.txt. APK normal 25 008 081
bytes SHA-256 `cee952984a15d4d2629369c28e7b1702449d71af4e563489ed907e4bb893f122`,
no instalada por firma distinta. APK lab 25 008 085 bytes SHA-256
`f13e15e6bbc8b32ac44fcafe0eee37c1be302dfc757e61ff51f6a32b4084b004`, instalada;
paquete de tests retirado y app original 0.28 preservada. El puente real DeX,
audio/tactil y proyeccion con pantalla apagada siguen sin implementar o verificar.
