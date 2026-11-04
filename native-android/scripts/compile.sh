#!/usr/bin/env bash
set -euo pipefail
log="${RUNNER_TEMP:-/tmp}/nabd-kotlin-compile.log"
if ./gradlew testReleaseUnitTest assembleRelease --no-daemon --max-workers=2 > "$log" 2>&1; then
  echo 'Kotlin compilation and JVM tests passed.'
else
  python3 - "$log" <<'PY'
import pathlib,re,sys
text=pathlib.Path(sys.argv[1]).read_text(errors='replace')
lines=[line for line in text.splitlines() if line.startswith('e: ') and ('/native-android/' in line or 'Unresolved reference' in line)]
for line in lines[:20]:
    line=re.sub(r'\d{5,}:[A-Za-z0-9_-]{15,}', '[redacted]', line)[:1600]
    print('::error title=Kotlin compilation::'+line.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
if not lines:
    import xml.etree.ElementTree as ET
    for report in pathlib.Path('app/build/test-results').rglob('*.xml'):
        try:
            for case in ET.parse(report).iter('testcase'):
                for failure in list(case.findall('failure'))+list(case.findall('error')):
                    msg=' | '.join((failure.get('message') or failure.text or 'failure').splitlines()[:6])[:1400]
                    msg=re.sub(r'\d{5,}:[A-Za-z0-9_-]{15,}', '[redacted]', msg)
                    print('::error title=Native JVM test::'+(case.get('name','test')+': '+msg).replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
        except Exception: pass
    print('::error title=Native compile gate::Gradle or JVM tests failed. Native candidate is not verified. Inspect build logs.')
PY
  exit 1
fi
