# Capturas y cuatro exportaciones 0.8 — 2026-10-27

Los pantallazos del propietario muestran el motivo del botón deshabilitado:
falta identidad Wi-Fi Direct local utilizable. La clave está enmascarada; no se
intentó reconstruirla. Esta pantalla no es evidencia de rechazo de clave remoto.

Se copiaron por ADB cuatro exportaciones nuevas, sufijos (8) a (11), a
private/vehicle-tests-2026-10-27/. No se modificaron los originales.

| Copia local | Bytes | SHA-256 |
|---|---:|---|
| diagnostic-9.txt | 3935 | `c5232088c60605f992894967a4cf93f69460f48f1c7781c88060c3dd4e8fd161` |
| diagnostic-10.txt | 5886 | `f534ae93ded284fd7dd134361831a64589e5963afb1661c1aba68aa2d5a99e8f` |
| diagnostic-11.txt | 8082 | `1d3b82ad3f1f924def3d52b9462ab1eccb0dec3ff5c9a3834c1b08c635d95577` |
| diagnostic-12.txt | 9410 | `8275a852039cee35084ef0e392b62792176674ade33cfd662720fc4424af1980` |

Snapshots a las 14:18:24, 14:19:09, 14:20:04 y 14:23:39 hora boliviana.
Se deduplicaron eventos por timestamp y mensaje: 79 eventos únicos visibles,
seis bloqueos de preparación normal y una consulta READ ServerInfo.

## Resultado

La ruta normal no alcanza escritura BLE: cero bytes enviados y confirmación=false.
El botón deshabilitado corresponde a la condición de nuestra aplicación, no a
una validación de PIN del receptor. El requisito local sigue sin resolverse;
hacer visible el campo en 0.8 no añadió interoperabilidad a esa etapa.

La consulta READ llega a MTU 512 y READING_SERVER a las 18:18:58.024 UTC.
El segundo snapshot a las 18:19:09.560 conserva correctamente el estado previo
al selector. A las 18:19:10.116 se registra cancelación por Activity en segundo
plano, unos 12,09 s después de iniciar READ y antes de su plazo de 15 segundos.
No hay respuesta válida registrada. La cercanía a la exportación respalda que
el selector pudiera haber provocado onStop; el evento identifica segundo plano,
no el componente Android concreto que lo causó.

Todos los snapshots tienen cero accesorios AOA y cero tráfico USB. Alimentación
USB=true en los tres primeros y false en el último no demuestra modo accesorio,
identidad del host ni fallo del cable.

No constan red formada, autenticación, intercambio de medios o sesión ICCOA.
No hay evidencia de que el vehículo rechace la clave o de bloqueo regional.

## Decisión de esta revisión

No se publica otra APK que sólo cambie el formulario ni se habilita Enviar con
metadatos falsos. Las pruebas UI de 0.8 verificaban comportamiento de pantalla,
no conexión funcional al AION. La aplicación sigue siendo un cliente incompleto.

La vía normal necesita una solución legítima al dato local requerido o evidencia
de una variante de protocolo que no lo necesite. La vía cableada necesita primero
un accesorio real y después autenticación/canales. No se encontró aquí evidencia
nueva que resuelva esos requisitos. Repetir el mismo ingreso de clave en 0.8 no
permitirá completarlos.

Actualización posterior: la investigación de 0.9 revisa la exigencia local de MAC
no anonimizada y prepara un experimento con el retorno real anonimizado de Android,
con consentimiento. No prueba que el AION lo acepte. Véase
[experimento 0.9](INTEROPERABILITY_EXPERIMENT_09.md); no confundirlo con los intentos
0.8 analizados en este documento.

Se añadieron sólo documentos. No se inició radio, conexión remota, instalación o
prueba junto al vehículo; no se ejecutaron pruebas de código por no haber cambios
de implementación. Las exportaciones crudas permanecen privadas.
