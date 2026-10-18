#!/usr/bin/env python3
"""Fetch the exact dependency binaries used for FlowPlay 1.1.36."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    deps = ROOT / 'deps'
    deps.mkdir(exist_ok=True)
    entries = json.loads((ROOT / 'dependencies.lock.json').read_text())
    for item in entries:
        target = deps / item['file']
        if target.is_file() and digest(target) == item['sha256']:
            continue
        errors = []
        for url in item['urls']:
            try:
                req = urllib.request.Request(url, headers={'User-Agent': 'FlowPlay-build/1.1.36'})
                with urllib.request.urlopen(req, timeout=120) as response:
                    data = response.read()
                if hashlib.sha256(data).hexdigest() != item['sha256']:
                    raise ValueError('SHA-256 mismatch')
                temp = target.with_suffix(target.suffix + '.part')
                temp.write_bytes(data)
                temp.replace(target)
                print('Fetched ' + item['file'], flush=True)
                break
            except Exception as error:
                errors.append(str(error))
        else:
            raise SystemExit('Failed to fetch ' + item['file'] + ': ' + '; '.join(errors))
    print(f'Verified {len(entries)} locked dependencies.', flush=True)

if __name__ == '__main__':
    main()
