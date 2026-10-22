# Preguntas abiertas

Actualización 2026-10-22: ya hay logs del S25, APK Samsung 3.1.04.10, filtro BLE
FCFB y cabecera UCar candidatos especificados estáticamente. Sigue pendiente
validarlos con el receptor AION y establecer el orden real. Véase UCAR_OBSERVED_FORMAT.md.

1. ¿Cuál es el paquete, versión y firma pública del receptor ICCOA real del AION?
   Las APK de teléfono y el receptor CarLife no responden esta pregunta.
2. ¿Qué anuncia la unidad al abrir ICCOA: BLE, Classic, Wi-Fi, USB, QR/PIN?
   Obtener observaciones del propietario con el coche estacionado.
3. ¿Qué ruta Samsung corresponde a ICCOA y cuál a Baidu CarLife?
   Seguir llamadas desde UI hasta transporte; contrastar Java con Smali y JNI.
4. ¿Hay dependencias Samsung necesarias sólo para la interfaz o también para el wire protocol?
5. ¿La autenticación exige certificado/clave OEM o SDK licenciado? No está probado.
   Solicitar documentación de interoperabilidad al proveedor antes de sustituir identidad.
6. ¿Cómo se separan pairing Bluetooth, autorización y negociación de aplicación?
7. ¿Quién crea la red Wi-Fi y qué mensajes/puertos/framing se observan realmente?
8. ¿Qué formatos A/V y control acepta esa versión concreta del receptor?
9. ¿Qué llamadas MIUI de CarWith son imprescindibles? Permisos solicitados no prueban
   que cada ruta los necesite ni explican por sí solos el fallo en HyperMint.
10. ¿Qué mensajes pueden implementarse con APIs públicas, sin privilegios de sistema?

No hay mensajes confirmados por captura del receptor. No hay justificación aún
para crear un codec ICCOA, enviar paquetes de prueba o pedir permisos de captura.
