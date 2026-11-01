"""Reuse compiled compact APKs only for identical source, runtime and embedded config."""
import json
import os
from pathlib import Path
import zipfile

SOURCE = 'ab49a3d8b9739056e4af9b72c26c4195853a0eb7'
DIRECTORY = Path('mobile/android/app/build/outputs/apk/release')
abis = ('arm64-v8a', 'armeabi-v7a', 'x86_64')
reuse = False
try:
    diff = Path(os.environ['RUNNER_TEMP'], 'nabd-compact-diff.txt').read_text().splitlines()
    assert all(p.startswith(('.github/workflows/', 'mobile/scripts/', 'mobile/tests/', 'docs/')) for p in diff)
    assert os.environ.get('EXPO_PUBLIC_DATA_SOURCE') == 'telegram'
    expected = json.loads(Path(os.environ['RUNNER_TEMP'], 'nabd-runtime.json').read_text())['runtime']
    values = [os.environ.get(key, '') for key in ('EXPO_PUBLIC_TELEGRAM_BOT_TOKEN', 'EXPO_PUBLIC_TELEGRAM_CHAT_ID', 'EXPO_PUBLIC_API_URL')]
    assert all(values)
    for abi in abis:
        with zipfile.ZipFile(DIRECTORY / f'app-{abi}-release.apk') as archive:
            assert archive.read('assets/fingerprint').decode().strip() == expected
            bundle = archive.read('assets/index.android.bundle')
            assert all(value.encode() in bundle for value in values)
            architectures = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
            assert architectures == {abi}
    reuse = True
except Exception:
    for apk in DIRECTORY.glob('*.apk'):
        apk.unlink()
with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
    output.write(f'reuse={str(reuse).lower()}\nsource={SOURCE if reuse else os.environ["GITHUB_SHA"]}\n')
print('Identical compact source/runtime/public configuration verified.' if reuse else 'Fresh compact build required.')
