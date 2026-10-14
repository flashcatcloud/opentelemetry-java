#!/usr/bin/env python3
"""Download SHA256-pinned build dependencies and the official comparison SDK."""
from pathlib import Path
import hashlib,json,urllib.request
root=Path(__file__).resolve().parent
for d in json.loads((root/'dependencies.json').read_text()):
    p=root/'build/deps'/d['group']/d['file'];p.parent.mkdir(parents=True,exist_ok=True)
    if not p.exists():
        temporary=p.with_suffix('.download')
        urllib.request.urlretrieve(d['url'],temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest()!=d['sha256']:
            temporary.unlink();raise RuntimeError('Checksum mismatch: '+d['file'])
        temporary.replace(p)
    if hashlib.sha256(p.read_bytes()).hexdigest()!=d['sha256']:
        raise RuntimeError('Checksum mismatch: '+d['file'])
print('Verified pinned dependencies')
