#!/usr/bin/env python3
"""Emit update metadata from the signed artifact, never from a release title/versionName."""
import hashlib
import json
import pathlib
import re
import subprocess
import sys

apk, channel, aapt = sys.argv[1:]
p = pathlib.Path(apk)
badging = subprocess.check_output([aapt, 'dump', 'badging', apk], text=True)
m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
if not m or m[1] != 'io.github.jbyjre.glint' or channel not in ('stable', 'preview'):
    raise SystemExit('Unexpected package/channel')
asset = 'pro.apk' if channel == 'stable' else 'pro-preview.apk'
print(json.dumps(dict(channel=channel, versionCode=int(m[2]), versionName=m[3],
    url=f'https://github.com/Jbyjre/pro3/releases/download/pro-{channel}-{m[2]}/{asset}',
    sha256=hashlib.file_digest(p.open('rb'), 'sha256').hexdigest(), size=p.stat().st_size)))
