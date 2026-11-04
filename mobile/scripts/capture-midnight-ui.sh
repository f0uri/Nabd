#!/usr/bin/env bash
set -euo pipefail
# Fresh CI emulator only. No account creation, login, backend write or phone data.
# A network-gated build may show the connection gate instead of authentication.
xml="$RUNNER_TEMP/nabd-midnight-startup.xml"
screen=''
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/nabd-midnight-startup.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/nabd-midnight-startup.xml > "$xml" 2>/dev/null || true
  detected=$(python3 - "$xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    nodes=list(ET.parse(sys.argv[1]).iter('node'))
    auth=any('nabd-midnight-auth-v1' in n.get('resource-id','') for n in nodes)
    login=any('تسجيل الدخول' in (n.get('text','')+' '+n.get('content-desc','')) for n in nodes)
    gate=any('nabd-midnight-connection-v1' in n.get('resource-id','') for n in nodes)
    print('auth' if auth and login else 'connection' if gate else '')
except Exception:
    print('')
PY
  )
  if [ "$detected" = auth ]; then screen=auth; break; fi
  if [ "$detected" = connection ] && [ "$attempt" -ge 3 ]; then screen=connection; break; fi
  sleep 2
done
if [ -z "$screen" ]; then
  echo '::error title=Midnight UI smoke::The actual new startup screen did not become visible. No APK publication.'
  exit 1
fi
adb exec-out screencap -p > "$RUNNER_TEMP/nabd-midnight-startup.png"
python3 - "$RUNNER_TEMP/nabd-midnight-startup.png" "$screen" <<'PY'
from pathlib import Path
import json, struct, sys
image=Path(sys.argv[1]); b=image.read_bytes()
assert b[:8]==b'\x89PNG\r\n\x1a\n', 'Emulator screenshot missing'
w,h=struct.unpack('>II',b[16:24])
assert w>=320 and h>=480, 'Invalid emulator screenshot size'
(image.parent/'nabd-midnight-ui-proof.json').write_text(json.dumps(dict(
    nativeStartupScreenVisible=True, screen=sys.argv[2], accountLoginPerformed=False,
    authenticatedFeedAndDockDeviceTested=False, screenshot=image.name, width=w, height=h),indent=2))
print('Actual Android Midnight startup screen visible: '+sys.argv[2]+'. No account login performed.')
PY
