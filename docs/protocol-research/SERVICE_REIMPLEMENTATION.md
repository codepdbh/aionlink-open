# Servicios observados y equivalentes independientes

## Revisión 0.5 — 2026-10-22

El objetivo es reproducir comportamientos interoperables con código propio. Los
nombres de servicios OEM no son interfaces públicas ni una prueba de que se puedan
ejecutar en un teléfono internacional. No se copian clases ni bibliotecas.

| Evidencia estática local | Comportamiento independiente | Límite actual |
|---|---|---|
| Samsung 3.1.04.10, `com/share/connect/ble/d.java:375–393`: filtro FCFB y modo de escaneo 2 | `BluetoothDiscovery`: búsqueda BLE filtrada, baja latencia y resultados inmediatos; alternativa general Classic/BLE | Un anuncio no demuestra identidad ni sesión |
| Samsung, `com/share/connect/ble/e.java`: descubrimiento UnionShare y características conocidas | `GattInspector`: selección explícita, conexión, inventario y cierre con timeout | No suscribe ServerInfo ni envía ClientInfo/autenticación |
| Samsung, manifiesto: `IccoaWlanConnectService`, `ShareLinkService`, `ICCOAMixingService` | Coordinador con estados separados de descubrimiento y transporte | P2P/AP, autenticación y motor de proyección pendientes; no se reemplazan con servicios vacíos |
| Samsung y Xiaomi: filtros AOA ICCOA/CarLink; Samsung `p018b5` | `UsbAccessoryClient`: permiso AOA, apertura, lectura acotada y dos peticiones de canal opcionales documentadas en USB_AOA_OBSERVED.md | Sin proxy completo, autenticación ni medios |
| Receptor/plugin Baidu: servicios CarLife | No se mezclan con el descubrimiento ICCOA | CarLife no acredita compatibilidad con este receptor AION |

Las fuentes citadas están en investigación local excluida de Git. Los hashes de
las APK originales y sus diferencias están registrados en los documentos de
investigación previos. Las observaciones de Samsung no se atribuyen automáticamente
a Xiaomi, OPPO, Huawei Car o CarbitLink.

## Corrección del flujo visible

En 0.4, cada anuncio eliminaba y añadía su tarjeta al final, y los candidatos
aparecían después de USB y de otros dispositivos. Esto explica un riesgo real de
que el propietario no viera el indicio registrado; no demuestra exactamente qué
pantalla tuvo delante durante la prueba.

En 0.5 el panel de candidatos precede USB, conserva el orden inicial y acumula
UUID públicos dentro de la misma búsqueda. Otras observaciones se despliegan
aparte. La comprobación puede seleccionarse durante el escaneo y detiene la
búsqueda antes de abrir GATT. El diagnóstico registra modo y número de candidatos.
No se conecta automáticamente a un equipo cercano.

La negociación ClientInfo necesita metadatos de red propios y coherentes con la
topología elegida. No se rellena con MAC inventadas ni identidades OEM. La sesión
autenticada y la proyección siguen siendo hitos incompletos.
