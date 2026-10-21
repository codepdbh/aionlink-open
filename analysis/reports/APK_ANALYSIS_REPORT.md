# Informe inicial de APK — 2026-10-21

Estado: análisis estático inicial completado para los cinco archivos presentes; revisión semántica exhaustiva PENDIENTE. No se han ejecutado APK ni probado el vehículo.

Las firmas se verificaron con apksigner del SDK. Su validez interna no acredita origen oficial ni compatibilidad.

| Paquete observado | Versión | min / target SDK | DEX | Bibliotecas en lib/ |
|---|---|---|---|---|
| com.baidu.carlife.samsung | 8.6.1 (253) | 23 / 30 | 5 | 52 |
| com.carlife.autodex | 4.0.0 (16) | 21 / 30 | 1 | 0 |
| com.heytap.opluscarlink | 16.4.2 (1604002) | 28 / 35 | 2 | 0 |
| com.miui.carlink | 4.0.14-20260801 (104000014) | 29 / 33 | 8 | 25 |
| com.samsung.android.carlink | 3.1.02.20 (310200020) | 33 / 34 | 2 | 11 |

## com.baidu.carlife.samsung

Original: `CarLife+ Component Plugin.apk` (82836780 bytes).

SHA-256: `21bee403961de66d3e40d610b2c0d2101d75874e251ef75ba4314daab0e54902`.

Certificado público SHA-256: `509e03e376b676b25adc41c3dbda6dfa5d0ff12dba1c8c0ca32ad110eea78667`.

Permisos solicitados: 73. sharedUserId: no declarado.

Componentes: activity=53, activity-alias=0, service=14, receiver=7, provider=8, uses-library=1.

Errores/resultado de comandos: `{"badging": 0, "signature": 0, "apktool": "cached successful decode", "jadx": 3}`.

JADX finalizó con 130 errores; Java reconstruido es incompleto.

Índice de localización: 43531 líneas con términos; cada coincidencia requiere validación.

Evidencia local: `analysis/metadata/CarLife-Component-Plugin-21bee403961d/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/CarLife-Component-Plugin-21bee403961d/`.

## com.carlife.autodex

Original: `CarLife+ Receiver.apk` (3983751 bytes).

SHA-256: `6cc4287f01516abe895f47b2876d657a5131b35c3c7024def85b698a9be5456e`.

Certificado público SHA-256: `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.

Permisos solicitados: 15. sharedUserId: no declarado.

Componentes: activity=3, activity-alias=0, service=3, receiver=0, provider=1, uses-library=0.

Errores/resultado de comandos: `{"badging": 0, "signature": 0, "apktool": "cached successful decode", "jadx": 3}`.

JADX finalizó con 6 errores; Java reconstruido es incompleto.

Índice de localización: 8424 líneas con términos; cada coincidencia requiere validación.

Evidencia local: `analysis/metadata/CarLife-Receiver-6cc4287f0151/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/CarLife-Receiver-6cc4287f0151/`.

## com.heytap.opluscarlink

Original: `carlink-16-4-2.apk` (23005112 bytes).

SHA-256: `8e162d53eb6bc60a713224cb1b888edc8b66354149470e3d5b26b203543cc6f9`.

Certificado público SHA-256: `64aafaf1d5bc9155a9e417a849e4f8eda1d0d1341667c28ed7c443c76f820b9a`.

Permisos solicitados: 30. sharedUserId: no declarado.

Componentes: activity=32, activity-alias=0, service=11, receiver=5, provider=25, uses-library=2.

Errores/resultado de comandos: `{"badging": 0, "signature": 0, "apktool": "cached successful decode", "jadx": 3}`.

JADX finalizó con 148 errores; Java reconstruido es incompleto.

Índice de localización: 16235 líneas con términos; cada coincidencia requiere validación.

Evidencia local: `analysis/metadata/carlink-16-4-2-8e162d53eb6b/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/carlink-16-4-2-8e162d53eb6b/`.

## com.miui.carlink

Original: `com.miui.carlink_4.0.14-20260801-104000014_minAPI29(arm64-v8a)(nodpi)_apkmirror.com.apk` (55952823 bytes).

SHA-256: `c7888ec5018b4e279ed3d3395b8c855d2a3571f06ae26b96849a61ad6dbef00e`.

Certificado público SHA-256: `c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025`.

Permisos solicitados: 92. sharedUserId: no declarado.

Componentes: activity=72, activity-alias=0, service=28, receiver=8, provider=6, uses-library=6.

Errores/resultado de comandos: `{"badging": 0, "signature": 0, "apktool": "cached successful decode", "jadx": 1}`.

JADX agotó el heap de 2 GiB: salida parcial, no descompilación completa. Conservar el fallo; usar Smali y análisis dirigido.

Índice de localización: 28347 líneas con términos; cada coincidencia requiere validación.

Evidencia local: `analysis/metadata/com-miui-carlink_4-0-14-20260801-104000014_minAPI29-arm64-v8a-nod-c7888ec5018b/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/com-miui-carlink_4-0-14-20260801-104000014_minAPI29-arm64-v8a-nod-c7888ec5018b/`.

## com.samsung.android.carlink

Original: `Samsung Auto v3.1.02.20.apk` (74208987 bytes).

SHA-256: `0f57eec4b036ca151fcf40a28c77dd7cc192629e853c1b167daeb33618e7f755`.

Certificado público SHA-256: `34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42`.

Permisos solicitados: 149. sharedUserId: no declarado.

Componentes: activity=73, activity-alias=0, service=19, receiver=18, provider=6, uses-library=2.

Errores/resultado de comandos: `{"badging": 0, "signature": 0, "apktool": "cached successful decode", "jadx": 3}`.

JADX finalizó con 44 errores; Java reconstruido es incompleto.

Índice de localización: 10248 líneas con términos; cada coincidencia requiere validación.

Evidencia local: `analysis/metadata/Samsung-Auto-v3-1-02-20-0f57eec4b036/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/Samsung-Auto-v3-1-02-20-0f57eec4b036/`.

## Interpretación y límites

- Samsung contiene servicios ICCOA y CarLife separados; seguir la ruta ICCOA antes de atribuir resultados al protocolo objetivo.
- CarWith solicita privilegios MIUI/Android y contiene Easyconn; obligatoriedad de cada dependencia aún incierta.
- El archivo Carlink 16.4.2 es OPPO/HeyTap; contiene rutas de llave/control que no deben confundirse con proyección.
- CarLife+ Receiver contiene clases/protobuf CarLife; no se identificó como receptor ICCOA del AION.
- No hay APK receptora del vehículo identificada ni capturas reales. Hito 2 general permanece parcial.
- Véase `docs/protocol-research/ICCOA_RESEARCH.md` para evidencias semánticas revisadas y `OPEN_QUESTIONS.md` para pendientes.
