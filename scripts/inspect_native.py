"""ELF headers, dependencies and exported symbol inventory, never loads libraries."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
tool = Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
output = ROOT/'analysis/native'
output.mkdir(parents=True,exist_ok=True)
results = []
for library in sorted((ROOT/'extracted').rglob('*.so')):
    relative = library.relative_to(ROOT/'extracted')
    target = output/relative.with_suffix('.elf.txt')
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open('w',encoding='utf-8') as stream:
        proc = subprocess.run([str(tool), '--file-header', '--dynamic', '--dyn-syms', str(library)],stdout=stream,stderr=subprocess.STDOUT,timeout=120)
    results.append({'library':str(relative),'exit_code':proc.returncode,'report':str(target.relative_to(ROOT))})
(output/'index.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(f'Inspected {len(results)} libraries with {tool.name}; failures: {sum(r["exit_code"] != 0 for r in results)}')
