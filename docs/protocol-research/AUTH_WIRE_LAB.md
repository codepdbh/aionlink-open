# Primer codec AUTH y simulador local

El código independiente `ExperimentalAuthWire` es de laboratorio: no está
conectado a la interfaz, a BLE ni a la red del vehículo. Su solicitud candidata
usa los tags AUTH observados en las clases `UCarProto.AuthRequest` de Samsung
Auto 3.1.04.10 y Xiaomi CarWith 4.0.14, cuyos hashes y límites figuran en
`CONNECTION_RESEARCH_2026-10-28.md`. La cabecera y tipo AUTH se basan en
`UCAR_OBSERVED_FORMAT.md`. No hay captura del AION que confirme esta codificación.

El encoder produce solicitud protobuf y cabecera candidata con CRC. El decoder
offline limita tamaños, comprueba CRC, origen, tipo, categoría, método y secuencia,
rechaza truncamientos, duplicados en campos conocidos y tipos wire no compatibles.
Devuelve datos sin calificarlos como autenticados: aún deben validarse campos,
estado del receptor, HMAC y firma, además de negociar/confirmar la clave.

La prueba de loopback usa un servidor sintético propio, puerto asignado por el
sistema y datos de prueba: lee solicitud y responde con un frame sintético.
Prueba transporte y correlación, **no valida criptografía entre extremos ni
simula todo el receptor ICCOA**. El lector protobuf estándar de Google empleado
en tests contrasta tags y valores del mensaje; no acredita semántica del AION.
Los tests incluyen error de secuencia, truncamiento, campos duplicados y tamaño.

Faltan todavía la máquina de autenticación que encadene estas primitivas,
el cálculo y confirmación segura de la sesión, el ciclo de vida del PIN, el
transporte ligado a la red y las pruebas negativas de seguridad integradas.
Posteriormente quedan el control y los medios. No recomendar prueba de vehículo
con esta versión de laboratorio como si añadiera una conexión funcional.
