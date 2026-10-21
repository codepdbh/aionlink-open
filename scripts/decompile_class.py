"""Bounded-memory fallback for one class; preserves full-decompilation output."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('class_name')
parser.add_argument('--java', default=r'C:\Program Files\Android\Android Studio\jbr\bin\java.exe')
args = parser.parse_args()
apk = args.apk.resolve()
if not apk.is_relative_to(ROOT) or apk.suffix.lower() != '.apk': parser.error('APK must be local to project')
if not re.fullmatch(r'[A-Za-z0-9_.$]+', args.class_name): parser.error('Invalid class name')
with apk.open('rb') as stream: digest = hashlib.file_digest(stream,'sha256').hexdigest()
output = ROOT/'analysis/targeted'/digest[:12]/args.class_name
if output.exists(): parser.error('Output already exists; preserving previous analysis')
output.mkdir(parents=True)
command = [args.java, '-Xmx1g', '-cp', str(ROOT/'tools/jadx-1.5.6/lib/jadx-1.5.6-all.jar'), 'jadx.cli.JadxCLI', '-j', '1', '--no-res', '--decompilation-mode', 'fallback', '--single-class', args.class_name, '--single-class-output', str(output/'source.java'), '-d', str(output/'support'), str(apk)]
with (output/'jadx.log').open('w',encoding='utf-8') as log:
    try: code = subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=180).returncode
    except subprocess.TimeoutExpired: code = -1
(output/'result.json').write_text(json.dumps({'sha256':digest,'class':args.class_name,'mode':'fallback','heap':'1g','exit_code':code},indent=2),encoding='utf-8')
print(f'Class analysis exit={code}, output={output.relative_to(ROOT)}')
