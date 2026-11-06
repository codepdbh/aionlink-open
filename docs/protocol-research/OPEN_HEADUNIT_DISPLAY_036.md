# Open Headunit en una pantalla virtual de AionLink (0.36)

Objetivo experimental: actividad de Open Headunit → pantalla virtual privada de
AionLink → codificador H.264 y transporte existente al receptor ICCOA. No es
Samsung DeX. No presupone que el coche tenga su propio receptor Miracast.

## Evidencia y límites

La fuente local, identificada por su `versionName` 4.0.0-beta2, declara la
actividad `com.andrerinas.openheadunit.aap.AapProjectionActivity` exportada y
`singleTask`. Se añadió `android:allowEmbedded="true"` a esa actividad. El
archivo modificado tiene SHA-256
`d90cde7b6d7a8e6b1ecb0be6fb9d76ac204784787bb21cc77c211f195113b089`.
La carpeta recibida no contiene identidad de revisión upstream verificable. El
valor `GIT_SHA` que imprime su Gradle detecta el repositorio padre y **no**
identifica la revisión upstream. Parche reproducible:
`patches/open-headunit/allow-embedded.patch`. Fuente de terceros no incluida en
el repositorio de AionLink; conservar su licencia AGPL y avisos al distribuirla.

AionLink ofrece un selector explícito para la próxima conexión en modo coche.
Cuando el receptor solicita vídeo, crea una pantalla privada propia sin el
panel y su superficie se conecta al codificador existente. Un botón del usuario
solicita abrir la actividad exportada en ese display usando `ActivityOptions` y
comprueba `isActivityStartAllowedOnDisplay`. `allowEmbedded` y el permiso de
lanzamiento son requisitos que dependen del sistema; no garantizan que haya
imagen o que la actividad permanezca visible al bloquear el teléfono. Según
[Android](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)),
el lanzamiento a una pantalla privada está sujeto a comprobaciones del sistema.

Pendiente: comprobar self mode y que la actividad siga en el display solicitado,
ver imagen y pantalla apagada, ciclo de vida, superficie protegida, latencia y
reinicio. No existe entrada táctil hacia la actividad de otro proceso: los toques
se registran como no implementados, no como inyectados. El audio depende del
Bluetooth u otra ruta compatible y no se ha comprobado en este modo. No se han
realizado pruebas de vehículo ni ejecutado APK de terceros en esta fase. Nunca
se deben inferir estas capacidades de una compilación satisfactoria.

La aplicación original de Open Headunit de tienda puede tener otra firma. Una
compilación debug no puede actualizarla si las firmas difieren. No desinstalarla:
su configuración se perdería. La variante modificada aún necesita una vía de
instalación compatible y autorización de prueba según AGENTS.md.
