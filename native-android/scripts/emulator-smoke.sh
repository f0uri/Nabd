#!/usr/bin/env bash
set -euo pipefail
# Fresh CI emulator only. No real login, account creation or vault initialization.
stage='download verified old APK'
trap 'echo "::error title=Native upgrade smoke::Failed during $stage. No verified candidate or account-migration claim."' ERR
mkdir -p "$RUNNER_TEMP/nabd-old-apk"
gh run download 41529655720 --name nabd-test-apk --dir "$RUNNER_TEMP/nabd-old-apk"
stage='ADB root readiness'
adb root >/dev/null
adb wait-for-device
stage='install old application'
adb install -r "$RUNNER_TEMP/nabd-old-apk/app-x86_64-release.apk" >/dev/null
adb shell mkdir -p /data/user/0/app.nabd.social/files
adb shell 'echo native-upgrade-preserves-files > /data/user/0/app.nabd.social/files/nabd-native-upgrade-marker'
stage='in-place Kotlin upgrade'
adb install -r native-android/app/build/outputs/apk/release/app-release.apk >/dev/null
adb shell cat /data/user/0/app.nabd.social/files/nabd-native-upgrade-marker | grep -x 'native-upgrade-preserves-files'
stage='launch native activity'
adb shell am start -W -n app.nabd.social/app.nabd.nativeapp.MainActivity >/dev/null
stage='native password proof'
ready=false
for attempt in $(seq 1 30); do
  adb exec-out cat /data/user/0/app.nabd.social/cache/nabd-kotlin-proof.json > "$RUNNER_TEMP/kotlin-proof.json" 2>/dev/null || true
  if python3 - "$RUNNER_TEMP/kotlin-proof.json" <<'PY'
import json, sys
try:
    p=json.load(open(sys.argv[1])); assert p['nativeKotlin'] and p['cryptoSelfTest'] and p['versionCode']==json.load(open('native-android/build-proof.json'))['versionCode']
except Exception: sys.exit(1)
PY
  then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then echo '::error::Actual Kotlin native password test did not pass'; exit 1; fi
stage='native auth visibility'
adb shell uiautomator dump /sdcard/nabd-native-ui.xml >/dev/null
adb exec-out cat /sdcard/nabd-native-ui.xml > "$RUNNER_TEMP/kotlin-ui.xml"
python3 - "$RUNNER_TEMP/kotlin-ui.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).iter('node'))
assert any('تسجيل الدخول' in (n.get('text','')+n.get('content-desc','')) for n in nodes), 'New auth UI did not appear'
PY
adb exec-out screencap -p > native-android/native-auth.png
python3 - <<'PY'
import json,os,pathlib
pathlib.Path('native-android/device-proof.json').write_text(json.dumps(dict(
    source=os.environ['GITHUB_SHA'], emulatorApi=30, nativeKotlin=True,
    oldReactNativeApkUpgradedWithoutUninstall=True, localFilePreserved=True,
    nativePasswordKnownAnswerTestPassed=True, newAuthVisible=True,
    realAccountLoginTested=False, physicalPhoneTested=False),indent=2))
PY
echo '::notice title=Native upgrade smoke::Kotlin startup/password KAT, auth visibility and in-place upgrade marker passed; no real account was used.'
