#!/usr/bin/env bash
# Own the emulator process directly: avoid upstream wrapper shell/ADB startup races.
set -euo pipefail
apk="${1:?APK path required}"
sdk="${ANDROID_HOME:?Android SDK required}"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"
export ANDROID_SERIAL=emulator-5554
# New avdmanager/emulator releases can choose different default AVD homes.
# Give both tools the same explicit home and a canonical descriptor.
export ANDROID_AVD_HOME="$RUNNER_TEMP/nabd-avd"
mkdir -p "$ANDROID_AVD_HOME"
log="$RUNNER_TEMP/nabd-emulator.txt"
report_failure() {
  echo '::error title=Android emulator tooling::Standalone emulator verification failed; APK publication remains blocked.'
  # Emulator/SDK-only output, not app logs or compiled configuration.
  python3 - "$log" <<'PY'
import pathlib,re,sys
p=pathlib.Path(sys.argv[1])
for line in (p.read_text(errors='replace').splitlines() if p.exists() else [])[-50:]:
    if not re.search(r'error|fatal|panic|invalid|failed|cannot|unsupported',line,re.I): continue
    line=re.sub(r'https?://\S+','[url]',line)[:1000]
    print('::error title=Emulator process::'+line.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
}
cleanup() { adb -s "$ANDROID_SERIAL" emu kill >/dev/null 2>&1 || true; }
trap cleanup EXIT
trap report_failure ERR
manager="$sdk/cmdline-tools/latest/bin/sdkmanager"
"$manager" --install 'platform-tools' 'emulator' 'system-images;android-30;default;x86_64' > "$log" 2>&1
printf 'no\n' | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --force --name nabd-ci --path "$ANDROID_AVD_HOME/nabd-ci.avd" --package 'system-images;android-30;default;x86_64' >> "$log" 2>&1
test -s "$ANDROID_AVD_HOME/nabd-ci.avd/config.ini"
printf 'avd.ini.encoding=UTF-8\npath=%s/nabd-ci.avd\ntarget=android-30\n' "$ANDROID_AVD_HOME" > "$ANDROID_AVD_HOME/nabd-ci.ini"
"$sdk/emulator/emulator" -list-avds | grep -qx nabd-ci
adb kill-server >/dev/null 2>&1 || true
adb start-server >> "$log" 2>&1
# No nested sh -c/background quoting, no per-line wrapper parser, no early
# animation/unlock commands. Use numeric memory and current SwiftShader mode.
"$sdk/emulator/emulator" -avd nabd-ci -port 5554 -no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader -memory 2048 -cores 2 -camera-back none -camera-front none >> "$log" 2>&1 &
ready=false
for attempt in $(seq 1 180); do
  boot=$(adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot" = 1 ] && adb -s "$ANDROID_SERIAL" shell pm path android >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then report_failure; exit 1; fi
bash mobile/scripts/smoke-android-apk.sh "$apk"
