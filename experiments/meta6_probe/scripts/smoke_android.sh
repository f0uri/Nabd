#!/usr/bin/env bash
set -euo pipefail
apk="${1:?APK path required}"
package=app.nabd.meta6probe
export ANDROID_SERIAL=emulator-5554
sdk="${ANDROID_HOME:?Android SDK required}"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"
export ANDROID_AVD_HOME="$RUNNER_TEMP/meta6-avd"
mkdir -p "$ANDROID_AVD_HOME"
log="$RUNNER_TEMP/meta6-emulator.log"
fail() { echo "::error title=Meta6 startup gate::${1:-Android startup failed; no carrier result is claimed.}"; exit 1; }
trap 'adb emu kill >/dev/null 2>&1 || true' EXIT
"$sdk/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'system-images;android-30;default;x86_64' > "$log" 2>&1
printf 'no\n' | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --force --name meta6 --path "$ANDROID_AVD_HOME/meta6.avd" --package 'system-images;android-30;default;x86_64' >> "$log" 2>&1
printf 'avd.ini.encoding=UTF-8\npath=%s/meta6.avd\ntarget=android-30\n' "$ANDROID_AVD_HOME" > "$ANDROID_AVD_HOME/meta6.ini"
adb start-server >> "$log" 2>&1
"$sdk/emulator/emulator" -avd meta6 -port 5554 -no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader -memory 2048 -cores 2 -camera-back none -camera-front none >> "$log" 2>&1 &
ready=false
for attempt in $(seq 1 180); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot" = 1 ] && adb shell pm path android >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
[ "$ready" = true ] || fail 'Emulator did not boot.'
rooted=false
for attempt in $(seq 1 8); do
  adb root >/dev/null 2>&1 || true
  timeout 20s adb wait-for-device >/dev/null 2>&1 || true
  uid=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)
  if [ "$uid" = 0 ]; then rooted=true; break; fi
  sleep 2
done
[ "$rooted" = true ] || fail 'CI emulator root transport not ready.'
adb install -r "$apk" > "$RUNNER_TEMP/meta6-install.txt" 2>&1 || fail 'APK could not be installed.'
probe="/data/user/0/$package/cache/meta6-ready.json"
adb shell rm -f "$probe"
adb shell am start -W -n "$package/.MainActivity" >/dev/null || fail 'Activity could not start.'
passed=false
for attempt in $(seq 1 45); do
  adb shell cat "$probe" > "$RUNNER_TEMP/meta6-startup.json" 2>/dev/null || true
  if python3 -c 'import json,sys;p=json.load(open(sys.argv[1]));sys.exit(0 if all(p.get(k) is True for k in ["ready","database","notifications","workmanager"]) else 1)' "$RUNNER_TEMP/meta6-startup.json" 2>/dev/null; then passed=true; break; fi
  sleep 2
done
[ "$passed" = true ] || fail 'Flutter/database/notification/worker startup proof missing.'
adb shell pidof "$package" >/dev/null || fail 'App process did not remain alive.'
echo '::notice title=Meta6 startup verified::Actual Android release initialized Flutter, SQLite, notifications and Workmanager. This is NOT a test of Meta credentials, delivery or the Moroccan *6 network.'
