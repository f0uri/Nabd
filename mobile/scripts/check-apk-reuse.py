"""Reuse only byte-identical app source + native runtime + embedded public config.
No tokens/URLs are printed; any unknown/missing input forces a full fresh build.
"""
import json,os,zipfile
from pathlib import Path
apk=Path('mobile/android/app/build/outputs/apk/release/app-release.apk')
source=os.environ['GITHUB_SHA'];reuse=False
try:
    diff=Path(os.environ['RUNNER_TEMP'],'nabd-source-diff.txt').read_text().splitlines()
    allowed=all(p.startswith(('.github/workflows/','mobile/scripts/','mobile/tests/','docs/')) for p in diff)
    if apk.exists() and allowed and os.environ.get('EXPO_PUBLIC_DATA_SOURCE')=='telegram':
        with zipfile.ZipFile(apk) as z:
            native=z.read('assets/fingerprint').decode().strip()
            bundle=z.read('assets/index.android.bundle')
            expected=json.loads(Path(os.environ['RUNNER_TEMP'],'nabd-runtime.json').read_text())['runtime']
            values=[os.environ.get(k,'') for k in ('EXPO_PUBLIC_TELEGRAM_BOT_TOKEN','EXPO_PUBLIC_TELEGRAM_CHAT_ID','EXPO_PUBLIC_API_URL')]
            reuse=native==expected and all(v and v.encode() in bundle for v in values)
    if reuse: source='c6a4191a177f54fbde11638eeaede0e299de2430'
except Exception:
    reuse=False
if not reuse and apk.exists(): apk.unlink()
with open(os.environ['GITHUB_OUTPUT'],'a') as f:
    f.write(f'reuse={str(reuse).lower()}\nsource={source}\n')
print('Verified unchanged-source APK candidate; skipping redundant native rebuild.' if reuse else 'Application/native/config inputs require a fresh APK build.')
