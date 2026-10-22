# Cambios

## 0.4.0-research — 2026-10-22

- Módulo Android USB/AOA con detección, permiso explícito, apertura y cierre del descriptor.
- Registro del accesorio ICCOA/CarLink observado en Samsung y CarWith, sin permisos privilegiados.
- Lector multiplexado original incremental con límites y seis pruebas sintéticas.
- Inicio experimental opcional de canales RTSP/UIBC según evidencia estática, sin sesión simulada.
- Distinción entre alimentación USB, ADB/carga, dispositivos host y accesorio AOA.
- USB y Bluetooth disponibles por separado; sin imponer un rol Bluetooth todavía desconocido.

## 0.3.0-research — 2026-10-22

- Conexión GATT real al BLE seleccionado por el propietario y descubrimiento de servicios.
- Reconocimiento de UnionShare y metadatos de características conocidas, sin leer/escribir valores.
- Timeout, cancelación, protección frente a callbacks obsoletos y cierre automático del enlace.
- Acción explícita de conexión y resultado de inspección separados de la sesión ICCOA.
- Prueba de pantalla inicial y botón Detener en S25; 23 pruebas automatizadas aprobadas.
- Receptor objetivo aclarado por el propietario: ICCOA CarLink y Huawei Car; no CarbitLink.

## 0.2.0-research — 2026-10-22

- Lectura ADB de registros retenidos por UID y análisis de Samsung Auto 3.1.04.10 instalado.
- Diagnóstico documentado de fallos OEM; comparación de rutas Samsung/Xiaomi/OPPO/CarLife.
- Especificación estática de cabecera UCar y lector propio offline con CRC y límites.
- Indicios BLE por UUID observados, sin declarar compatibilidad o sesión.
- Exportación con transporte, señal, conectabilidad, UUID permitidos y timestamps.
- Protección frente a callbacks BLE de búsquedas anteriores; botón coherente con búsqueda activa.
- 6 pruebas Python y 14 JVM; APK compilada y actualizada por ADB en el S25.

## 0.1.0-research — 2026-10-21

- Inventario local SHA-256, firma pública, manifest, recursos, DEX y bibliotecas.
- Automatización Apktool/JADX con registros y salidas separadas por hash.
- Búsqueda con índices de rutas/líneas/términos y comparación de metadatos.
- Inspección ELF mediante LLVM del NDK, sin cargar bibliotecas.
- Base Android Kotlin modular, StateFlow, tests, Compose y descubrimiento Bluetooth.
- Perfil AION sin verificar y diagnósticos exportables sin nombres ni direcciones.
- Documentación de límites, evidencia y procedimiento de pruebas estacionarias.
