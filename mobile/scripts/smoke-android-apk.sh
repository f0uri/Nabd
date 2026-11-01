#!/usr/bin/env bash
set -euo pipefail
apk="${1:?APK path required}"
# Only a fresh CI emulator, never a user's phone/account/credential or live write.
# Release JS console forwarding is not an oracle: read the fixed cache probe.
# The emulator action's animation settings can race a temporarily offline ADB.
# Wait for a usable Android shell and package manager before touching the app.
ready=false
for attempt in $(seq 1 30); do
  if adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
    if adb shell pm path android >/dev/null 2>&1; then ready=true; break; fi
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo '::error title=Android emulator tooling::Android shell/package manager did not become ready.'
  exit 1
fi
if ! adb root >/dev/null; then
  echo '::error title=Android emulator tooling::The CI image did not allow adb root; no native probe was executed.'
  exit 1
fi
adb wait-for-device
if [ "$(adb shell id -u | tr -d '\r')" != 0 ]; then
  echo '::error title=Android emulator tooling::A rootable AOSP emulator is required to read the fixed cache probe.'
  exit 1
fi
if ! adb install -r "$apk" > "$RUNNER_TEMP/nabd-install.txt" 2>&1; then
  echo '::error title=Android emulator tooling::APK installation failed before the native probe.'
  exit 1
fi
probe=/data/user/0/app.nabd.social/cache/nabd-native-crypto-proof.json
adb shell rm -f "$probe"
adb logcat -c
adb shell am start -W -n app.nabd.social/.MainActivity >/dev/null
found=false
for attempt in $(seq 1 60); do
  adb shell cat "$probe" > "$RUNNER_TEMP/nabd-native-probe.json" 2>/dev/null || true
  if python3 -c 'import json,sys;sys.exit(0 if json.load(open(sys.argv[1])).get("state")=="passed" else 1)' "$RUNNER_TEMP/nabd-native-probe.json" 2>/dev/null; then
    found=true
    break
  fi
  if grep -qE '"(failed|unavailable)"' "$RUNNER_TEMP/nabd-native-probe.json"; then break; fi
  sleep 2
done
if [ "$found" != true ]; then
  echo '::error::Actual Android native password self-test did not pass. Refusing to publish this APK.'
  # The probe has only fixed library-initialization/self-test errors; no user data.
  # Still sanitize URLs/tokens and workflow command escapes before annotation.
  adb logcat -d -s AndroidRuntime:E ReactNativeJS:E '*:S' > "$RUNNER_TEMP/nabd-crash-log.txt"
  python3 mobile/scripts/report-native-probe.py "$RUNNER_TEMP/nabd-native-probe.json" "$RUNNER_TEMP/nabd-crash-log.txt"
  exit 1
fi
echo 'Verified actual Android emulator: native PBKDF2-SHA256 210000-round known-answer test passed.'
python3 mobile/scripts/verify-apk-content.py "$apk" "$RUNNER_TEMP/nabd-apk-proof.json"
