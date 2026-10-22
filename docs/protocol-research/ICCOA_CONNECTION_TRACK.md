# Cliente ICCOA y experiencia propia — 2026-10-22

El propietario confirmó que el AION muestra **ICCOA CarLink y Huawei Car**.
CarbitLink no fue identificado como opción del receptor. Se prioriza un cliente
independiente ICCOA con interfaz propia, manteniendo la unidad del vehículo intacta.
No se asigna una probabilidad de éxito sin datos de interoperabilidad.

## Implementado en 0.3

Actualización 0.4: el enlace por cable tiene ahora cliente AOA propio y un modo
experimental opcional para los dos pedidos iniciales de canal observados. Véase
[USB/AOA](USB_AOA_OBSERVED.md). Sigue sin autenticación o proyección verificadas.

La app ya puede abrir una conexión BLE al dispositivo observado y seleccionado
por el propietario, llamar a discoverServices, reconocer UnionShare y registrar
metadatos de sus características conocidas. Usa APIs públicas, timeout de 15 segundos,
cancelación y cierre explícito del GATT. No lee valores de características ni
suscribe notificaciones, no escribe mensajes, no crea bonds ni abre sockets IP.

La pantalla pide confirmar que el dispositivo seleccionado es el vehículo propio
estacionado. La búsqueda se detiene antes del enlace; el usuario puede cancelar.
El resultado INSPECTED describe una inspección completada y un enlace ya cerrado.
No es una conexión de proyección persistente ni una sesión ICCOA.

Código: `android/transport-bluetooth/.../GattInspector.kt` y
`android/protocol-iccoa/.../GattInspection.kt`. El estado GATT se separa del estado
de sesión para impedir que un callback Bluetooth declare sesión autenticada.
La exportación omite nombre/MAC y todo payload; sólo contiene UUID conocidos,
propiedades, conteo de servicios, ID temporal, código de estado y etapa.

## Ruta técnica restante

| Paso | Evidencia / estado |
|---|---|
| Descubrimiento BLE FCFB | Implementado como indicio pasivo |
| Enlace GATT y servicio UnionShare | Implementado; pendiente de prueba con el AION |
| Características Client info, Server info y Client info_auth | Inventario de metadatos implementado |
| Mensaje inicial normal con código de seis caracteres | Estructura JSON observada estáticamente; envío pendiente |
| Selección de red y roles P2P/AP | Variantes observadas; falta comprobar las del receptor |
| Autenticación UCar | Cabecera y lector offline; autenticación remota pendiente |
| Formatos A/V y control | Pendientes de negociación real |
| Interfaz propia proyectada | Pendiente de un canal de medios verificado |
| Puente Android Auto | No implementado; necesita receptor/conversión comprobables |
| Huawei Car | Opción informada por el propietario; sin APK/SDK ni análisis específico |

En Samsung 3.1.04.10, `com/share/connect/ble/BluetoothLeService.java:99` construye
Client info como JSON con campos id, model, name, pinCodeOrAuthentication, band,
mac y type. El callback `ble/e.java:225` localiza UnionShare antes del intercambio.
El campo mac está vinculado al flujo de red del cliente; no se puede reemplazar por
un valor inventado o una identidad del fabricante. Tampoco se deben asumir valores
de band o topología sólo para forzar que un mensaje se envíe.

La respuesta Server info proporciona parámetros para la siguiente etapa. Estos
parámetros pueden ser sensibles; un futuro cliente debe usarlos sólo en memoria
para la conexión autorizada y excluirlos de logs/exportaciones. No se obtuvieron
valores reales ni claves del vehículo durante este trabajo.

## Prueba junto al vehículo

1. Estacionar, abrir ICCOA y abrir AionLink 0.3 en el teléfono.
2. Pulsar Buscar mi vehículo y esperar a que termine la búsqueda.
3. Seleccionar únicamente el BLE del vehículo propio. Pulsar Conectar y comprobar
   servicios y confirmar. No seleccionar otros equipos cercanos.
4. Esperar el resultado o usar Detener búsqueda / enlace. Exportar el diagnóstico.
5. Registrar si UnionShare aparece y qué características conocidas se descubren.
   Si hay sólo dispositivos Classic, no intentar mensajes RFCOMM con UUID supuestos.

Un timeout o un estado Android GATT de error no demuestra por sí solo incompatibilidad.
UnionShare presente confirma ese servicio en el equipo seleccionado, pero no identifica
su fabricante criptográficamente ni garantiza autenticación o proyección.
El código de seis dígitos no se introduce todavía en esta versión.

## Documentación oficial consultada

- [ICCOA: requisitos de interconexión](https://www.iccoa.cn/tech/68.html): anuncia
  requisitos de proyección, escritorio e interfaces cableadas/inalámbricas. La
  descarga oficial respondió «iniciar sesión primero»; no se intentó eludirla.
- [Carbit](https://www.carbit.com.cn/): presenta CarbitLink como producto propio de
  conectividad/proyección; no establece compatibilidad del AION del propietario.
- [BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)
  y [callbacks](https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback):
  base pública del enlace y descubrimiento implementados.

No se ha publicado código de fabricantes ni se ha instalado nada en el coche.
