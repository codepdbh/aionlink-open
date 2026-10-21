# Pruebas y observación

Pruebas automatizadas independientes de hardware:

```powershell
python -m unittest discover -s tests/unit -v
cd android
./gradlew.bat :core:test :app:assembleDebug :app:lintDebug --no-daemon --console plain
```

Las pruebas Python usan archivos sintéticos y cubren inventario ZIP, manifiesto y
rechazo de archivos corruptos. No contienen mensajes ICCOA fabricados.
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

Para declarar sesión ICCOA: intercambio bidireccional reproducible, confirmación
del receptor identificada, estabilidad y desconexión limpia. Para proyección:
vídeo realmente visible y evidencia de formatos negociados. No hay esas pruebas aún.
