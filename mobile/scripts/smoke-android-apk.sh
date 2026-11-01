#!/usr/bin/env bash
set -euo pipefail
apk="${1:?APK path required}"
# Never enter real credentials or write to the shared vault. The app runs only
# its fixed native known-answer test (210,000 rounds, NUL + Arabic + emoji).
adb install -r "$apk" >/dev/null
adb logcat -c
adb shell am start -W -n app.nabd.social/.MainActivity >/dev/null
found=false
for attempt in $(seq 1 60); do
  if adb logcat -d -s ReactNativeJS:I '*:S' | grep -q 'NABD_NATIVE_CRYPTO_SELF_TEST_OK'; then
    found=true
    break
  fi
  sleep 2
done
if [ "$found" != true ]; then
  echo '::error::Actual Android native password self-test did not pass. Refusing to publish this APK.'
  # Only fixed non-secret diagnostics; never dump full application logs.
  adb logcat -d -s ReactNativeJS:I '*:S' | grep -oE 'NABD_NATIVE_CRYPTO_(UNAVAILABLE|SELF_TEST_FAILED)' | sort -u || true
  exit 1
fi
echo 'Verified actual Android emulator: native PBKDF2-SHA256 210000-round known-answer test passed.'
python3 mobile/scripts/verify-apk-content.py "$apk" "$RUNNER_TEMP/nabd-apk-proof.json"
