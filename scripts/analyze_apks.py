"""Local-only static APK inventory/decode. Never installs or executes APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
PATTERN = re.compile(r'ICCOA|CarLink|CarWith|CarLife|BluetoothGatt|BluetoothSocket|WifiP2pManager|WifiManager|UsbManager|UsbAccessory|MediaProjection|MediaCodec|MediaFormat|AudioRecord|AudioTrack|ServerSocket|SSLSocket|WebSocket|Protobuf|JNI|Pairing|Authentication|Handshake|Session|Projection|Mirroring|Vehicle|认证|握手|投屏|车联|蓝牙|配对', re.I)

def sha256(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def run(args, log):
    with open(log, 'w', encoding='utf-8') as out:
        try:
            result = subprocess.run([str(x) for x in args], stdout=out, stderr=subprocess.STDOUT, timeout=1800)
            return result.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            out.write(str(error))
            return -1

def manifest_metadata(path):
    root = ET.parse(path).getroot()
    sdk = root.find('uses-sdk')
    app = root.find('application')
    result = {'package': root.get('package'), 'versionName': root.get(ANDROID+'versionName'), 'versionCode': root.get(ANDROID+'versionCode'),
              'sdk': {} if sdk is None else dict(sdk.attrib),
              'permissions': [dict(p.attrib) for p in root.findall('uses-permission')],
              'declared_permissions': [dict(p.attrib) for p in root.findall('permission')],
              'sharedUserId': root.get(ANDROID+'sharedUserId'), 'components': {}}
    if app is not None:
        result['application'] = dict(app.attrib)
        for kind in ('activity','activity-alias','service','receiver','provider','uses-library'):
            result['components'][kind] = [dict(e.attrib) for e in app.findall(kind)]
    return result

def inventory(apk):
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        return {'native_libraries': [{'path': e.filename, 'size': e.file_size, 'sha256': hashlib.sha256(archive.read(e)).hexdigest()} for e in entries if e.filename.startswith('lib/') and e.filename.endswith('.so')],
                'dex': [e.filename for e in entries if re.fullmatch(r'classes\d*\.dex', e.filename)],
                'resource_formats': sorted({Path(e.filename).suffix for e in entries if e.filename.startswith(('res/','assets/'))}),
                'entries': len(entries), 'uncompressed_bytes': sum(e.file_size for e in entries)}

def search_tree(folder, output):
    # Index paths/line numbers and matched terms, never whole source lines/secrets.
    count = 0
    with output.open('w', encoding='utf-8') as stream:
        for path in sorted(folder.rglob('*')):
            if path.suffix not in ('.java','.kt','.smali','.xml','.proto'): continue
            with path.open(encoding='utf-8', errors='replace') as source:
                for number, line in enumerate(source, 1):
                    terms = sorted(set(m.group(0) for m in PATTERN.finditer(line)))
                    if terms:
                        stream.write(json.dumps({'file': str(path.relative_to(ROOT)), 'line': number, 'terms': terms}, ensure_ascii=False)+'\n')
                        count += 1
    return count

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=r'C:\Program Files\Android\Android Studio\jbr\bin\java.exe')
    parser.add_argument('--sdk', default=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    parser.add_argument('--skip-jadx', action='store_true')
    parser.add_argument('--apk', type=Path, help='Analyze one local APK; preserve other per-APK metadata')
    options = parser.parse_args()
    sdk = Path(options.sdk)
    bt = sorted((sdk/'build-tools').iterdir(), key=lambda p: tuple(int(n) for n in p.name.split('.')))[-1]
    apks = sorted(set(ROOT.glob('*.apk')) | set((ROOT/'apks/original').glob('*.apk')))
    if options.apk:
        selected = options.apk.resolve()
        if selected not in [p.resolve() for p in apks]: parser.error('--apk must select an original inside the project')
        apks = [selected]
    all_meta = []
    for apk in apks:
        digest = sha256(apk)
        key = re.sub('[^a-zA-Z0-9_-]+','-',apk.stem)[:65]+'-'+digest[:12]
        report = ROOT/'analysis/metadata'/key
        report.mkdir(parents=True, exist_ok=True)
        previous = json.loads((report/'metadata.json').read_text(encoding='utf-8')) if (report/'metadata.json').exists() else {}
        decoded = ROOT/'extracted'/key
        java_dir = ROOT/'analysis/java'/key
        print('Analyzing', apk.name, flush=True)
        metadata = {**previous, 'input': apk.name, 'sha256': digest, 'size': apk.stat().st_size, 'utc': datetime.now(timezone.utc).isoformat(), **inventory(apk), 'commands': dict(previous.get('commands', {}))}
        metadata['commands']['badging'] = run([bt/'aapt2.exe','dump','badging',apk],report/'badging.txt')
        # Public certificate fingerprints only; no key extraction.
        metadata['commands']['signature'] = run([options.java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',apk],report/'signature.txt')
        badging = (report/'badging.txt').read_text(encoding='utf-8', errors='replace')
        metadata['badging'] = {}
        for field, pattern in {'package':r"package: name='([^']+)'", 'versionName':r"versionName='([^']+)'", 'versionCode':r"versionCode='([^']+)'", 'minSdk':r"sdkVersion:'([^']+)'|minSdkVersion:'([^']+)'", 'targetSdk':r"targetSdkVersion:'([^']+)'"}.items():
            match = re.search(pattern, badging, re.I)
            if match: metadata['badging'][field] = next(g for g in match.groups() if g is not None)
        signature = (report/'signature.txt').read_text(encoding='utf-8', errors='replace')
        metadata['public_certificate_sha256'] = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', signature)
        stamp = report/'apktool-success.json'
        if not (stamp.exists() and decoded.exists()):
            if decoded.exists():
                metadata['commands']['apktool'] = 'partial output exists; preserve it and inspect log'
            else:
                code = run([options.java,'-Xmx2g','-jar',ROOT/'tools/apktool_3.0.3.jar','d','-p',ROOT/'tools/apktool-framework','-o',decoded,apk],report/'apktool.log')
                metadata['commands']['apktool'] = code
                if code == 0: stamp.write_text(json.dumps({'sha256':digest}),encoding='utf-8')
        else: metadata['commands']['apktool'] = 'cached successful decode'
        if (decoded/'AndroidManifest.xml').exists():
            try: metadata['manifest'] = manifest_metadata(decoded/'AndroidManifest.xml')
            except ET.ParseError as error: metadata['manifest_error'] = str(error)
        if not options.skip_jadx:
            stamp = report/'jadx-result.json'
            if not stamp.exists():
                if java_dir.exists():
                    metadata['commands']['jadx'] = 'partial output exists; inspect before retry'
                else:
                    code = run([options.java,'-Xmx2g','-cp',ROOT/'tools/jadx-1.5.6/lib/jadx-1.5.6-all.jar','jadx.cli.JadxCLI','-j','2','--no-res','-d',java_dir,apk],report/'jadx.log')
                    metadata['commands']['jadx'] = code
                    stamp.write_text(json.dumps({'sha256':digest,'exit_code':code}),encoding='utf-8')
            else: metadata['commands']['jadx'] = json.loads(stamp.read_text())
            metadata['keyword_locations'] = search_tree(java_dir,report/'keyword-index.jsonl')
        (report/'metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
        all_meta.append(metadata)
        print('Finished', apk.name, metadata['commands'], flush=True)
    # Rebuild combined inventory from all completed per-input records.
    all_meta = [json.loads(p.read_text(encoding='utf-8')) for p in sorted((ROOT/'analysis/metadata').glob('*/metadata.json'))]
    output = ROOT/'analysis/metadata/inventory.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(all_meta,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Inventory:', output, flush=True)

if __name__ == '__main__': main()
