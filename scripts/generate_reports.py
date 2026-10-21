"""Generate factual, source-free summaries from local static analysis records."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
records = [json.loads(p.read_text(encoding='utf-8')) for p in sorted((ROOT/'analysis/metadata').glob('*/metadata.json'))]
report = ['# Informe inicial de APK — 2026-10-21', '',
          'Estado: análisis estático inicial completado para los cinco archivos presentes; revisión semántica exhaustiva PENDIENTE. No se han ejecutado APK ni probado el vehículo.', '',
          'Las firmas se verificaron con apksigner del SDK. Su validez interna no acredita origen oficial ni compatibilidad.', '',
          '| Paquete observado | Versión | min / target SDK | DEX | Bibliotecas en lib/ |',
          '|---|---|---|---|---|']
native = ['# Inventario nativo — 2026-10-21', '',
          'Inventario ZIP con SHA-256 por biblioteca en los metadatos locales. Inspección ELF con LLVM del NDK 28.2.13676358; ninguna biblioteca fue cargada o ejecutada.', '']
for record in records:
    b = record.get('badging', {})
    report.append(f"| {b.get('package')} | {b.get('versionName')} ({b.get('versionCode')}) | {b.get('minSdk')} / {b.get('targetSdk')} | {len(record['dex'])} | {len(record['native_libraries'])} |")
for record in records:
    b = record.get('badging', {})
    key = next(p.parent.name for p in (ROOT/'analysis/metadata').glob('*/metadata.json') if json.loads(p.read_text(encoding='utf-8'))['sha256'] == record['sha256'])
    directory = ROOT/'analysis/metadata'/key
    manifest = record.get('manifest', {})
    report += ['', '## '+str(b.get('package')), '', f"Original: `{record['input']}` ({record['size']} bytes).", '',
               f"SHA-256: `{record['sha256']}`.", '',
               'Certificado público SHA-256: '+', '.join('`'+x+'`' for x in record.get('public_certificate_sha256',[]))+'.', '',
               f"Permisos solicitados: {len(manifest.get('permissions',[]))}. sharedUserId: {manifest.get('sharedUserId') or 'no declarado'}.", '',
               'Componentes: '+', '.join(f'{kind}={len(items)}' for kind,items in manifest.get('components',{}).items())+'.', '',
               f"Errores/resultado de comandos: `{json.dumps(record['commands'],ensure_ascii=False)}`."]
    jadx_log = (directory/'jadx.log').read_text(encoding='utf-8',errors='replace') if (directory/'jadx.log').exists() else ''
    errors = re.findall(r'finished with errors, count: (\d+)',jadx_log)
    if errors: report += ['', f'JADX finalizó con {errors[-1]} errores; Java reconstruido es incompleto.']
    if 'OutOfMemoryError' in jadx_log: report += ['', 'JADX agotó el heap de 2 GiB: salida parcial, no descompilación completa. Conservar el fallo; usar Smali y análisis dirigido.']
    report += ['', f"Índice de localización: {record.get('keyword_locations','pendiente')} líneas con términos; cada coincidencia requiere validación.", '',
               f"Evidencia local: `analysis/metadata/{key}/metadata.json`, `badging.txt`, `signature.txt`, `apktool.log`, `jadx.log`, `keyword-index.jsonl`; manifiesto/Smali en `extracted/{key}/`."]
    native += ['## '+str(b.get('package')), '', '| Entrada | Bytes | SHA-256 |', '|---|---:|---|']
    for lib in record['native_libraries']:
        native.append(f"| {lib['path']} | {lib['size']} | `{lib['sha256']}` |")
    if not record['native_libraries']: native += ['| Ninguna en lib/ | — | — |']
    native += ['']
native_index = ROOT/'analysis/native/index.json'
if native_index.exists():
    entries = json.loads(native_index.read_text())
    failed = [e for e in entries if e['exit_code']]
    native += [f"Se inspeccionaron {len(entries)} candidatos *.so (incluye assets); {len(entries)-len(failed)} aceptados por readelf y {len(failed)} rechazados.", '',
               'Rechazados (la extensión no prueba formato ELF):', '']
    native += ['- `'+e['library']+'`' for e in failed]
native += ['', 'Samsung: `RemoteDisplayNative` carga `heycastcore`; la copia arm64 es ELF64 AArch64 y declara dependencias de mediandk, binder_ndk, android, log y libc++_shared. Esto localiza una frontera JNI de proyección; no prueba formatos aceptados por el AION.', '',
           'CarWith incluye `libmicastcore.so`, `libECSocksServer.so` y bibliotecas de sistema empaquetadas; no atribuirles semántica ICCOA por nombre. Los detalles ELF/símbolos permanecen locales en `analysis/native/`. No se copian al cliente independiente.']
report += ['', '## Interpretación y límites', '',
           '- Samsung contiene servicios ICCOA y CarLife separados; seguir la ruta ICCOA antes de atribuir resultados al protocolo objetivo.',
           '- CarWith solicita privilegios MIUI/Android y contiene Easyconn; obligatoriedad de cada dependencia aún incierta.',
           '- El archivo Carlink 16.4.2 es OPPO/HeyTap; contiene rutas de llave/control que no deben confundirse con proyección.',
           '- CarLife+ Receiver contiene clases/protobuf CarLife; no se identificó como receptor ICCOA del AION.',
           '- No hay APK receptora del vehículo identificada ni capturas reales. Hito 2 general permanece parcial.',
           '- Véase `docs/protocol-research/ICCOA_RESEARCH.md` para evidencias semánticas revisadas y `OPEN_QUESTIONS.md` para pendientes.']
dest = ROOT/'analysis/reports'; dest.mkdir(parents=True,exist_ok=True)
(dest/'APK_ANALYSIS_REPORT.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
(dest/'NATIVE_LIBRARIES_REPORT.md').write_text('\n'.join(native)+'\n',encoding='utf-8')
print(f'Generated two reports for {len(records)} APKs')
