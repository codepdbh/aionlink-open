# Android Auto en el mismo teléfono: investigación preliminar

Fecha: 5 de noviembre de 2026. Investigación documental; no es un receptor
implementado ni una prueba en el S25. No se ejecutaron APK de terceros, ni se
leyeron, extrajeron o copiaron claves o certificados privados.

## Hallazgos y procedencia

1. **Google documenta una herramienta de desarrollo.** El [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu)
   ejecuta el receptor en un ordenador y usa el servidor para desarrolladores de
   Android Auto en el teléfono. Documenta un túnel ADB a TCP 5277. Esta guía no
   afirma que un receptor Android de terceros en el mismo teléfono sea una API
   soportada de producción ni que pueda redistribuirse el DHU.
2. **Un proyecto independiente afirma que existe self mode.** [Open Headunit](https://github.com/andreknieriem/open-headunit)
   declara que puede actuar como receptor en un teléfono y proyectar Android Auto
   del mismo dispositivo; describe activar manualmente el servidor de desarrollo.
   También informa de cambios de compatibilidad en Android Auto 17.4 y posteriores.
   Esto es una afirmación de sus mantenedores, no una verificación propia en este
   S25 ni una garantía de disponibilidad futura o de autorización de integración.
   La [guía de automatización](https://headunit.andrerinas.com/guides/automation/)
   documenta controles de sesión y self mode, pero no una API pública para extraer
   sus fotogramas y audio hacia AionLink. No se ha comprobado ningún acuerdo o
   licencia para integrar ese proyecto.
3. **La autenticación es una incógnita crítica.** La documentación independiente
   de [AARE](https://github.com/prototux/AARE/blob/main/docs/certificates.md)
   describe autenticación TLS mutua y restricciones de confianza de Google Android
   Auto. Es investigación de terceros, no una especificación oficial o una prueba
   de las versiones de este S25. Encontrar un servicio en TCP no elimina el requisito
   de identidad aceptada. No usar ni recuperar secretos de terceros, suplantar un
   receptor o eludir la autenticación. Se requiere una vía legítima y autorizada.
4. **Licencia y arquitectura.** Open Headunit declara licencia AGPL-3.0. AionLink
   tiene código propio GPL-3.0-or-later: la adopción o combinación requiere una
   revisión concreta de licencias y procedencia; no se asume compatibilidad automática
   ni se copia código o bibliotecas a este proyecto. El análisis de comportamiento
   no convierte código ajeno en implementación propia.

## Qué habría que demostrar

```text
Android Auto en S25 → receptor local legítimo → vídeo/audio/entrada
                     → adaptador de fuentes de AionLink → ICCOA → AION
```

El receptor debe ofrecer imagen, audio y eventos de entrada de forma disponible y
autorizada para el proceso correspondiente. Se necesitarían adaptar resolución,
timestamps, tacto/teclas, enfoque de audio, micrófono y reconexión. Debe convivir
con Wi-Fi Direct ICCOA; una conexión local por sí sola no prueba esa convivencia.
La captura de una ventana del receptor podría servir como experimento separado,
pero no demuestra continuidad con pantalla apagada, salida de audio completa, ni
permiso de captura de contenido protegido. No se usaría una pantalla segura sin
autorización ni se eludirían restricciones de Android.

**Pantalla apagada:** el historial de Open Headunit menciona recuperación de imagen
al volver de pantalla apagada, no demuestra que siga entregando fotogramas durante
10 minutos bloqueado. El objetivo del propietario exige probar explícitamente que
la salida continúa, la pantalla física está apagada y el tacto/audio siguen vivos.
El resultado de la sonda H.264 propia no demostrará por sí mismo que Android Auto o
un receptor ajeno funcionen así.

## Próximos experimentos sin alterar el coche

1. Obtener el resultado de AionLink Sonda con pantalla apagada; observar frames de
   entrada y salida, bloqueo y tiempo, no sólo una cifra de FPS.
2. Cuando el S25 vuelva a estar conectado, leer únicamente versión pública de
   Android Auto y estado de nuestra app. No se detectó un dispositivo ADB durante
   esta investigación, por lo que su versión actual queda desconocida.
3. Consultar fuentes oficiales y condiciones de distribución sobre integración de
   receptores de desarrollo. Registrar si existe una interfaz soportada y una
   identidad del receptor que podamos usar legítimamente, sin extraer secretos.
4. Si existe esa vía, construir primero un fake local sin claves que ejercite
   formatos de vídeo, audio y gestos y el cambio entre fuentes, seguido por una
   prueba del receptor real por separado. Un fake o un DHU en PC no cuenta como
   Android Auto real funcionando en el S25.
5. Mantener la duplicación ICCOA actual como modo independiente y comprobarla con
   el propietario; no modificar el vehículo. Si la vía AA no supera autorización y
   pruebas de pantalla apagada, avanzar con la interfaz propia como opción distinta.

## Estado

La ruta de self mode es una **hipótesis técnica más concreta** que la suposición
previa de que sólo existían ejemplos en PC. Su funcionamiento en el S25, las
credenciales aceptables, la integración y la continuidad con la pantalla apagada
siguen abiertos. No hay Android Auto integrado ni se ha ejecutado una simulación en
esta investigación.
