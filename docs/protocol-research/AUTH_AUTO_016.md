# 0.16: inicio AUTH autorizado en un solo intento

Diagnostico (20) de 0.15, 5351 bytes, SHA-256
`1e9fbaa2703d8855c856380cfce04861faab63799631c440d2a2567c0f1ea7c7`,
conservado en `private/vehicle-tests-2026-10-30/`. Hora UTC 2026-10-30:
ClientInfo confirmado 17:42:31.999, ServerInfo P2P/port ZERO 17:42:33.278,
P2P conectado 17:42:33.881, prueba TCP sin datos OPEN por P2P_SOURCE en
61 ms a las 17:42:44.987, exportacion 17:42:50.339 mientras P2P conectado.
`Solicitud AUTH experimental: IDLE` indica que no se envio AUTH en este intento.
La imagen del 40% no identifica una etapa concreta ni prueba sesion. El snapshot
no muestra la perdida de red ni su causa. El resultado OPEN confirma TCP, no AUTH.

## Cambio independiente

La casilla opcional del formulario autoriza una unica solicitud AUTH cuando la
red P2P seleccionada y verificada este lista. Usa el codigo del mismo intento,
sin segunda entrada. Esta desmarcada inicialmente, se descarta al cerrar y no se
reutiliza entre intentos. El codigo se conserva en memoria mutable hasta 60 s,
se borra al cancelar, fallar, vencer el plazo o entregarse al intercambio; como
en versiones anteriores, Android puede conservar copias String transitorias. El
intercambio limpia sus buffers. No se registran codigo, direcciones o payload.
Una red no P2P no dispara esta prueba. Si la red desaparece, la solicitud se
cancela. El puerto de diagnostico separado se rotula como prueba sin autenticacion.

No se ha modificado el protocolo ni se ha recibido AUTH del coche. La verificabilidad
real de esta mejora depende de otra prueba del propietario con coche estacionado.
Los resultados RESPONSE_* solo indican el resultado del analisis de la respuesta;
ninguno demuestra una sesion completa. La derivacion y confirmacion de sesion,
video, audio y controles siguen pendientes. La ruta local P2P_SOURCE no garantiza
por si sola la interfaz de salida ante rutas ambiguas. Procedencia y limites del
mensaje candidato se mantienen en [AUTH_EXCHANGE_015.md](AUTH_EXCHANGE_015.md).
