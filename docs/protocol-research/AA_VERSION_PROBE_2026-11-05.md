# Android Auto: primera respuesta de versión del S25

Se implementó una prueba limitada y separada de la proyección de ICCOA. Envía una
solicitud de versión 1.1, lee una respuesta acotada y cierra. No abre TLS, no envía
credenciales ni activa vídeo, audio, táctil o una sesión de Android Auto completa.
El acceso de hardware es solo al servidor local de desarrollo que el propietario
inició; la prueba instrumentada requiere la opción explícita `allow_local_aa_probe`.

## Procedencia y límites

La elección de canal, mensaje de versión y solicitud 1.1 procede del análisis estático
de [aasdk ControlServiceChannel](https://github.com/f1xpl/aasdk/blob/master/src/Channel/Control/ControlServiceChannel.cpp)
y de la [documentación independiente de intercambio de versión](https://github.com/mrmees/open-android-auto/blob/main/docs/interactions/02-version-ssl-auth.md).
La [referencia de framing](https://github.com/mrmees/open-android-auto/blob/main/docs/channels/architecture.md)
describe cabecera y longitudes. Hay contradicciones internas en esa documentación
sobre los flags del intercambio; se usó el modo `SPECIFIC`, sin cifrar y no
fragmentado observado en la implementación aasdk. El código de AionLink se escribió
independientemente; no se copiaron bibliotecas ni claves de terceros.

## Resultados observados el 5 de noviembre de 2026

- La primera prueba recibió TIMEOUT tras 2,5 s; esa versión de la sonda no distinguía
  la fase del timeout. Instrumentación finalizó, pero eso no significa éxito del
  protocolo. El socket del servidor quedó en CLOSE_WAIT: no establece la causa.
- El propietario detuvo y volvió a iniciar el servidor de desarrollo. Con la sonda
  que registra la fase, la segunda prueba completó en 0,059 s y recibió
  `RESPONSE; major=1; minor=7; result=0`.
- Esto verifica una respuesta de versión de Android Auto 17.9.664044-release en ese
  S25. No acredita identidad del receptor, negociación TLS, autenticación, vídeo,
  audio, pantalla apagada o interoperabilidad con el AION. `result=0` se consigna
  literalmente, sin atribuir más significado del respaldado por la fuente.

La prueba se ejecutó en `org.aionlink.open.lab`, sin reemplazar la aplicación original
ni iniciar una conexión al coche. Instrumentación propia retirada; el servicio táctil
original seguía activo. Salidas privadas: `private/aa-version-probe-2026-11-05.txt`
y `private/aa-version-probe-2026-11-05-second.txt`.

## Pendiente

Necesitamos una vía legítima para autenticar el receptor, validar la identidad del
servidor y negociar canales, además de verificar permisos y licencias. Sin ello no se
han implementado vídeo/audio/controles de Android Auto ni su conexión a ICCOA. La
duplicación sigue siendo independiente; la prueba física con pantalla apagada continúa
pendiente. Open Headunit puede ser una fuente visible para espejo con pantalla
encendida, pero no se ha demostrado que funcione apagada ni se ha ejecutado aquí.
