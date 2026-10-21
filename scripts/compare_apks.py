"""Compare metadata by inventory folder, without exposing source or credentials."""
import argparse
import json
from pathlib import Path

def compare(a, b):
    result = {}
    for field in ('sha256', 'size', 'native_libraries', 'manifest'):
        if a.get(field) != b.get(field): result[field] = {'before':a.get(field), 'after':b.get(field)}
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    args = parser.parse_args()
    print(json.dumps(compare(json.loads(args.before.read_text(encoding='utf-8')), json.loads(args.after.read_text(encoding='utf-8'))), ensure_ascii=False, indent=2))
