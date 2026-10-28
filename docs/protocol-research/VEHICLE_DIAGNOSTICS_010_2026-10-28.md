# 0.10: primera formación de red Wi-Fi Direct confirmada

Revisión del 28 de octubre de 2026, America/La_Paz. Exportaciones (14) y (15)
recuperadas por ADB 37.0.0 del S25 autorizado, sin modificar originales. Copias
privadas: `private/vehicle-tests-2026-10-28-b/`, excluidas de Git.

| TXT | Bytes | SHA-256 |
|---|---:|---|
| (14) | 4587 | a3ef01f59d0fa480d5dad6bcd2ab1061e0bac75f59de1c0eb4f44638f9413bcf |
| (15) | 13225 | af4aa7af382b75becfb41b847fa4701a5637f1f1ca06ff4f46928a08741c24bb |

Mismo proceso; (15) incluye los eventos de (14). Cinco preparaciones numeradas,
cuatro escrituras ClientInfo confirmadas y tres respuestas/redes. No sumar los
snapshots como pruebas independientes. Horas locales: UTC menos cuatro horas.

| Intento | Resultado |
|---|---|
| 1 | Red P2P CONNECTED 13:37:05.349660; desconexión 13:37:24.200837, duración 18,851177 s |
| 2 | Red P2P CONNECTED 13:37:51.301513; cancelación local por segundo plano 13:37:53.196442, duración 1,894929 s |
| 3 | Escritura confirmada; cancelación local en WAITING_SERVER al pasar a segundo plano |
| 4 | Formulario cancelado; no envío |
| 5 | Red P2P CONNECTED 13:38:44.655900; cancelación local por segundo plano 13:38:49.801236, duración 5,145336 s |

En los tres intentos con respuesta: MTU 512, escritura de 139 bytes confirmada,
ServerInfo de 123 bytes, modo P2P, puerto OUT_OF_RANGE. La implementación sólo
declara CONNECTED tras comprobar grupo formado, teléfono cliente, coincidencia
de SSID y MAC de propietario con los parámetros del receptor. Es evidencia de
formación de red, **no autenticación o sesión ICCOA**. El experimento con retorno
anonimizado no impidió formar estas redes; no acredita las fases posteriores.

El primer corte no tiene evento de cancelación local previo. El registro indica
pérdida de grupo, sin causa ni iniciador identificable. Timeout del receptor por
falta de negociación posterior es una hipótesis compatible con la autenticación
ausente, no un diagnóstico probado. No se atribuye a PIN rechazado o región.

Los intentos 2 y 5 sí tienen causa local registrada: el tratamiento de Activity
en segundo plano cancela P2P una vez CONNECTED. La espera acotada de 0.10 sólo
cubre CONNECTING para P2P y CONNECTING/CONNECTED para AP. Corregir esa asimetría
evitaría estos cortes locales; no resolvería por sí sola el primer intento.

OUT_OF_RANGE acota el problema a un entero fuera de 1..65535 o desbordamiento
numérico; no permite inferir el valor exacto, cero, signo o semántica. No se ha
inventado un puerto de sustitución. Debe investigarse negociación y endpoint
antes de abrir un canal de autenticación legítimo.

Inspección adicional: seis servicios GATT, UnionShare presente, propiedades
ClientInfo WRITE y ServerInfo READ/NOTIFY. USB: sin alimentación reportada en
estos snapshots, cero accesorios AOA y cero bytes.

El propietario informa progreso del coche hasta 40%. No existe correspondencia
documentada entre ese porcentaje y una fase concreta; los registros prueban
la red por separado. No hay autenticación, canales de medios o proyección.

Próximos trabajos: conservación acotada y cancelable del intento P2P conectado
en segundo plano, registro de duración/causa local frente a pérdida de grupo,
y análisis de la fase de autenticación y del puerto anunciado. No repetir sin
cambios como si bastara esperar más. Revisión documental; APK 0.10 conservada,
sin conexiones al vehículo iniciadas desde el ordenador ni pruebas de código
innecesarias. Sólo documentación propia publicable; TXT privados preservados.
