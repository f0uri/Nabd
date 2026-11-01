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
stage='SDK installation'
echo "::notice title=Meta6 startup stage::$stage"
trap 'echo "::error title=Meta6 emulator stage::Failed during $stage at line $LINENO."' ERR
emulator_pid=''
cleanup() {
  # Isolate emulator shutdown from the runner shell's process group.
  if [ -n "$emulator_pid" ]; then kill -- "-$emulator_pid" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
signal_failure() {
  trap - TERM EXIT
  echo "::error title=Meta6 process signal::SIGTERM during $stage"
  # Emulator/SDK output only, not app logs or compiled provider settings.
  python3 - "$log" <<'PYCODE'
import pathlib,re,sys
lines=pathlib.Path(sys.argv[1]).read_text(errors='replace').splitlines()
errors=[s for s in lines if re.search(r'error|fatal|crash|panic|killed|vulkan|signal',s,re.I)]
for line in (errors or lines)[-5:]:
    line=re.sub(r'https?://\S+','[url]',line)[:700]
    print('::error title=Meta6 emulator diagnostic::'+line.replace('%','%25'))
PYCODE
  exit 143
}
trap signal_failure TERM
"$sdk/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'system-images;android-30;default;x86_64' > "$log" 2>&1
stage='AVD creation'
echo "::notice title=Meta6 startup stage::$stage"
printf 'no\n' | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --force --name meta6 --path "$ANDROID_AVD_HOME/meta6.avd" --package 'system-images;android-30;default;x86_64' >> "$log" 2>&1
printf 'avd.ini.encoding=UTF-8\npath=%s/meta6.avd\ntarget=android-30\n' "$ANDROID_AVD_HOME" > "$ANDROID_AVD_HOME/meta6.ini"
stage='ADB server'
echo "::notice title=Meta6 startup stage::$stage"
adb start-server >> "$log" 2>&1
stage='Android boot'
echo "::notice title=Meta6 startup stage::$stage"
setsid "$sdk/emulator/emulator" -avd meta6 -port 5554 -no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader -feature -Vulkan -memory 2048 -cores 2 -camera-back none -camera-front none >> "$log" 2>&1 &
emulator_pid=$!
ready=false
for attempt in $(seq 1 180); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot" = 1 ] && adb shell pm path android >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
[ "$ready" = true ] || fail 'Emulator did not boot.'
stage='root reconnect'
echo "::notice title=Meta6 startup stage::$stage"
rooted=false
for attempt in $(seq 1 8); do
  adb root >/dev/null 2>&1 || true
  timeout 20s adb wait-for-device >/dev/null 2>&1 || true
  uid=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)
  if [ "$uid" = 0 ]; then rooted=true; break; fi
  sleep 2
done
[ "$rooted" = true ] || fail 'CI emulator root transport not ready.'
stage='APK install'
echo "::notice title=Meta6 startup stage::$stage"
adb install -r "$apk" > "$RUNNER_TEMP/meta6-install.txt" 2>&1 || fail 'APK could not be installed.'
probe="/data/user/0/$package/cache/meta6-ready.json"
adb shell rm -f "$probe" "/data/user/0/$package/cache/meta6-worker-ready.json"
stage='activity start'
echo "::notice title=Meta6 startup stage::$stage"
adb shell am start -W -n "$package/.MainActivity" --ez enable-impeller false --ez enable-software-rendering true >/dev/null || fail 'Activity could not start.'
stage='UI proof'
echo "::notice title=Meta6 startup stage::$stage"
passed=false
for attempt in $(seq 1 45); do
  adb shell cat "$probe" > "$RUNNER_TEMP/meta6-startup.json" 2>/dev/null || true
  if python3 -c 'import json,sys;p=json.load(open(sys.argv[1]));sys.exit(0 if all(p.get(k) is True for k in ["ready","database","notifications","workmanager"]) else 1)' "$RUNNER_TEMP/meta6-startup.json" 2>/dev/null; then passed=true; break; fi
  sleep 2
done
[ "$passed" = true ] || fail 'Flutter/database/notification/worker startup proof missing.'
stage='worker proof'
echo "::notice title=Meta6 startup stage::$stage"
worker=false
for attempt in $(seq 1 60); do
  adb shell cat "/data/user/0/$package/cache/meta6-worker-ready.json" > "$RUNNER_TEMP/meta6-worker-startup.json" 2>/dev/null || true
  if python3 -c 'import json,sys;p=json.load(open(sys.argv[1]));sys.exit(0 if all(p.get(k) is True for k in ["worker","database","notifications","secureStorage"]) else 1)' "$RUNNER_TEMP/meta6-worker-startup.json" 2>/dev/null; then worker=true; break; fi
  sleep 2
done
[ "$worker" = true ] || fail 'Actual background worker/plugin health proof missing.'
stage='final process check'
echo "::notice title=Meta6 startup stage::$stage"
adb shell pidof "$package" >/dev/null || fail 'App process did not remain alive.'
echo '::notice title=Meta6 startup verified::Actual Android release initialized Flutter and executed a real background Workmanager job with SQLite, notifications and secure storage. This is NOT a test of Meta credentials, delivery or the Moroccan *6 network.'
