# Diagnóstico del S25 por ADB — 2026-10-22

Estado: fallos de aplicación observados; conexión con AION NO reproducida.
El propietario confirmó estar lejos del vehículo durante esta inspección.
No se inició Samsung Auto, no se cambiaron permisos, CSC, root o firmware y no se
realizó un intento de conexión al coche. Se actualizó únicamente nuestra APK.

## Evidencia disponible

- S25 Ultra SM-S938B, Android API 37 según getprop y diagnóstico del propietario.
- Samsung Auto instalado: 3.1.04.10, versionCode 310400010, minSdk 35, targetSdk 36.
- APK base leída desde la instalación autorizada; SHA-256
  `5633d2f9c1bb4422df9c35b79af46cbddc940df0c3d512d6b9fc6546dee7d917`.
- Certificado público SHA-256
  `34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42`.
  Coincide con la APK 3.1.02.20 local; eso no acredita por sí solo procedencia oficial.
- `libheycastcore.so` arm64 tiene el mismo hash en esas dos APK Samsung.
- Apktool exit 0; JADX exit 3 con 38 errores: Java reconstruido incompleto.
- Colector reproducible: `scripts/collect_phone_evidence.py`; retiene sólo UID de
  las dos aplicaciones relevantes y un resumen acotado de permisos/versión.

Logs originales privados: `private/adb-2026-10-22/` y
`private/phone-evidence-20261022T131847370776Z/`. No se publican ni se copian aquí.
El resumen reproducible tiene 192 líneas retenidas para Samsung y ninguna para
nuestra app. Cero líneas no demuestra ausencia de problemas o una conexión exitosa.

## Fallos observados y alcance

| Evidencia | Interpretación respaldada | Lo que no demuestra |
|---|---|---|
| Dos FATAL EXCEPTION del 21 de octubre, a las 19:17:18 y 19:17:36 según el log | Cierres reales de com.samsung.android.carlink | No identifica la versión que produjo cada evento |
| NoSuchMethodError para AppOpsManager.semCheckOpSystemAlertWindow | La llamada OEM no encuentra el método en ese runtime | No identifica una prohibición por país |
| Stack: ICCOAMixingService.onBind → utilidad overlay → wrapper AppOps | El fallo afecta al enlace del servicio ICCOA en esos intentos | No demuestra que se alcanzara handshake remoto |
| Denegación setCutoutPolicy por MANAGE_ACTIVITY_TASKS el 22 de octubre | Uso de una operación privilegiada no autorizada | No implica que conceder permisos ordinarios resuelva todo |
| Denegación del proveedor Samsung por SEM_APP_RESTRICTION | Otra dependencia del entorno OEM | No prueba que sea imprescindible para el cliente mínimo |
| Servicios BLE y P2P enlazados; onOpenResult: true el 22 de octubre | Inicialización local de componentes ICCOA en un intento posterior | No es sesión con vehículo ni proyección |

Ninguno de esos cierres incluye una versión inequívoca de APK dentro de la traza.
El historial puede atravesar actualizaciones: no atribuir automáticamente todos
los eventos a la versión instalada actualmente. La versión actual sí contiene la
misma llamada OEM, contrastada en Smali:
`extracted/samsung-auto-3-1-04-10-s25-5633d2f9c1bb/smali_classes2/r2.1/b.smali:20`.

Permisos Bluetooth SCAN/CONNECT, ubicación y NEARBY_WIFI_DEVICES aparecen concedidos
en dumpsys. MANAGE_ACTIVITY_TASKS, MANAGE_USB, MODIFY_AUDIO_ROUTING e INJECT_EVENTS
no aparecen listados como concedidos en el resumen. Ausencia en ese listado no es
equivalente a una denegación observada; las dos denegaciones anteriores sí están en logs.

## Conclusión técnica

Existe evidencia de dependencia de APIs y privilegios OEM incompatibles con el
entorno del teléfono. La afirmación «no funciona fuera de China» sigue siendo una
hipótesis demasiado amplia: estos registros no contienen una decisión geográfica
que explique el fallo. Tampoco contienen un intercambio suficiente para concluir
que el receptor rechaza a un cliente independiente por identidad criptográfica.

El diagnóstico AionLink 0.1 proporcionado por el propietario confirma permisos,
estado DEVICE_FOUND y tres observaciones en una búsqueda completada. No identifica
al coche ni el protocolo; el informe viejo omitía detalle de transportes y UUID.
El código de seis dígitos fue observado por el propietario mientras Samsung Auto
intentaba enlazar; no consta en una captura de protocolo. No se solicitó su valor.

## Trabajo propio derivado

La versión 0.2 conserva las APIs públicas Android para descubrimiento y no depende
del método Samsung que falla. Añade indicios por UUID documentados, detalle de
observaciones, timestamps de eventos y un lector offline de cabeceras UCar.
No modifica ni parchea Samsung Auto. No concede permisos privilegiados por ADB.

Se consultaron las APIs públicas de [ScanRecord](https://developer.android.com/reference/android/bluetooth/le/ScanRecord)
y [ScanResult](https://developer.android.com/reference/android/bluetooth/le/ScanResult)
para leer servicios anunciados y conectabilidad. No se guarda service/manufacturer
data. El nombre y la MAC no se incluyen en la exportación.

## Próxima prueba

1. AION estacionado, propietario presente; abrir ICCOA y nuestra versión 0.2.
2. Buscar una vez, esperar fin y exportar. Repetir con ICCOA cerrado.
3. Comparar candidatos y UUID observados; ninguna coincidencia autoriza afirmar sesión.
4. Si hace falta captura del intento Samsung, iniciar colector antes y después sin
   borrar buffers; conservar evidencia local. El ajuste consultado
   bluetooth_btsnoop_log_mode devolvió null: no confirma que exista un archivo HCI
   recuperable. No se cambió ese ajuste ni se extrajo un bugreport completo.
5. Con evidencia del receptor, especificar mensajes GATT y negociación antes de enviar
   bytes desde un adaptador independiente. Si surge autenticación OEM obligatoria,
   buscar SDK/documentación/acceso legítimo, sin suplantación.
