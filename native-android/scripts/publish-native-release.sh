#!/usr/bin/env bash
set -euo pipefail
# Run from repository root, ONLY after the complete native build/device job passes.
# Deliberately refuses migration candidates and non-main publication.
test "${GITHUB_REF:-}" = refs/heads/main
test "${GITHUB_REPOSITORY:-}" = f0uri/Nabd
test "${NABD_NATIVE_RELEASE_ENABLED:-}" = true
python3 - <<'PY'
import hashlib,json,os,pathlib,re
root=pathlib.Path('native-android'); apk=root/'release/app-release.apk'
p=json.loads((root/'release/build-proof.json').read_text())
d=json.loads((root/'device/device-proof.json').read_text())
assert p['source']==d['source']==os.environ['GITHUB_SHA']
assert d['oldReactNativeApkUpgradedWithoutUninstall'] and d['nativePasswordKnownAnswerTestPassed']
assert hashlib.sha256(apk.read_bytes()).hexdigest()==p['apkSha256']
config=(root/'app/build.gradle.kts').read_text()
version=re.search(r'versionName = "([^"]+)"',config).group(1)
code=int(re.search(r'versionCode = (\d+)',config).group(1))
assert re.fullmatch(r'\d+\.\d+\.\d+',version), 'Candidates cannot be published as production updates'
assert code==p['versionCode']
tag='kotlin-v'+version
name='nabd-'+version+'-universal.apk'
apk.rename(root/'release'/name)
notes=(root/'release-notes.txt').read_text().strip().splitlines()
assert notes and len(notes)<=15
manifest=dict(schema=1,packageName='app.nabd.social',versionCode=code,versionName=version,
              source=p['source'],notes=notes,apks=dict(universal=dict(
                  url=f'https://github.com/f0uri/Nabd/releases/download/{tag}/{name}',sha256=p['apkSha256'],size=p['size'])))
(root/'release/nabd-native-update.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))

PY
NABD_NATIVE_VERSION=$(python3 -c "import json;print(json.load(open('native-android/release/nabd-native-update.json'))['versionName'])")
NABD_NATIVE_TAG="kotlin-v$NABD_NATIVE_VERSION"
# Existing tags are never silently moved or overwritten.
gh release create "$NABD_NATIVE_TAG" native-android/release/*.apk native-android/release/nabd-native-update.json native-android/release/build-proof.json native-android/device/device-proof.json --target "$GITHUB_SHA" --title "Nabd $NABD_NATIVE_VERSION — Kotlin Android" --notes-file native-android/release-notes.txt
