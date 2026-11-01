"""Resolve compiled icon resources, including AGP-shortened ZIP paths."""
import os
from pathlib import Path
import re
import subprocess


def icon_paths(resource_dump, archive_names):
    entries = list(re.finditer(r'(?m)^\s*resource\s+0x[0-9a-fA-F]+\s+(\S+)', resource_dump))
    resources = {}
    for index, match in enumerate(entries):
        end = entries[index + 1].start() if index + 1 < len(entries) else len(resource_dump)
        name = match.group(1).split(':')[-1]
        paths = re.findall(r'\(file\)\s+(\S+)', resource_dump[match.end():end])
        resources[name] = [path for path in paths if path in archive_names]
    launcher = resources.get('mipmap/ic_launcher', [])
    foreground = [path for name, paths in resources.items()
                  if name in ('mipmap/ic_launcher_foreground', 'drawable/ic_launcher_foreground')
                  for path in paths]
    assert launcher, 'Compiled launcher resource is missing or has no packaged files'
    assert foreground, 'Compiled adaptive foreground is missing or has no packaged files'
    return launcher, foreground


def verify_icons(apk, archive):
    tools = sorted((Path(os.environ['ANDROID_HOME']) / 'build-tools').glob('*/aapt2'))
    assert tools, 'Android resource inspector is unavailable'
    dump = subprocess.run([str(tools[-1]), 'dump', 'resources', str(apk)],
                          check=True, capture_output=True, text=True).stdout
    launcher, foreground = icon_paths(dump, set(archive.namelist()))

    def raster(path):
        data = archive.read(path)
        return data.startswith(b'\x89PNG\r\n\x1a\n') or (data[:4] == b'RIFF' and data[8:12] == b'WEBP')

    assert any(raster(path) for path in launcher), 'Compiled launcher has no raster icon'
    assert any(raster(path) for path in foreground), 'Adaptive foreground has no raster icon'
    adaptive = False
    for path in launcher:
        if raster(path):
            continue
        tree = subprocess.run([str(tools[-1]), 'dump', 'xmltree', str(apk), '--file', path],
                              check=True, capture_output=True, text=True).stdout
        if re.search(r'E: adaptive-icon\b', tree) and re.search(r'E: foreground\b', tree):
            adaptive = True
    assert adaptive, 'Compiled adaptive launcher XML is missing'
