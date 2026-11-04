#!/usr/bin/env bash
set -euo pipefail
log="$RUNNER_TEMP/nabd-native-ui-tests.log"
if ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 > "$log" 2>&1; then
  echo '::notice title=Native device UI tests::Auth validation, update progress/install gating and encrypted synthetic legacy-session import passed on Android.'
else
  python3 - "$log" <<'PY'
import pathlib, re, sys, xml.etree.ElementTree as ET
for path in pathlib.Path('app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        for case in ET.parse(path).iter('testcase'):
            for failure in list(case.findall('failure'))+list(case.findall('error')):
                message=(failure.get('message') or failure.text or 'test failure').splitlines()[0]
                message=re.sub(r'\d{5,}:[A-Za-z0-9_-]{15,}','[redacted]',message)[:1000]
                print('::error title=Native UI test::'+(case.get('name','test')+': '+message).replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
    except Exception: pass
lines=[s for s in pathlib.Path(sys.argv[1]).read_text(errors='replace').splitlines() if s.startswith('e: ') and '/native-android/' in s]
for line in lines[:12]: print('::error title=Native device test compilation::'+line[:1500].replace('%','%25'))
print('::error title=Native UI test gate::Android instrumentation or its build failed. Candidate remains unverified.')
PY
  exit 1
fi
