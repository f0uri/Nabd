#!/usr/bin/env bash
set -euo pipefail
out="$RUNNER_TEMP/nabd-compact-release"
mkdir -p "$out"
for abi in arm64-v8a armeabi-v7a; do
  cp "apk/app-$abi-release.apk" "$out/nabd-1.1.0-$abi.apk"
done
cp "$RUNNER_TEMP/nabd-compact-proof.json" "$out/nabd-compact-proof.json"
(cd "$out" && sha256sum ./*.apk > SHA256SUMS)
hash=$(sha256sum "$out/nabd-1.1.0-arm64-v8a.apk" | cut -d ' ' -f1)
tag="android-${hash:0:12}"
python3 - "$out" <<'PY'
import json, pathlib, sys
out=pathlib.Path(sys.argv[1]); p=json.loads((out/'nabd-compact-proof.json').read_text())
lines=['Nabd 1.1.0 — compact APKs (same features and accounts).', '',
       'Use arm64-v8a for 64-bit Android phones; armeabi-v7a is for 32-bit ARM phones.',
       'Install over the existing app. Do not uninstall or clear data.', '',
       'R8/resource shrinking, ABI-specific APKs and compressed native libraries/JS.',
       f'Actual source: {p["source"]}', f'Native runtime: {p["runtime"]}', '']
for abi, v in p['phoneVariants'].items():
    lines.append(f'{abi}: {v["sizeBytes"]} bytes; {v["reductionPercent"]}% smaller than the verified universal APK; SHA256 {v["apkSha256"]}.')
lines += ['', 'The real 210000-round native password test passed on the x86_64 Android emulator.',
          'Phone APKs have identical tested DEX/assets, verified native libraries/icons/runtime, and the same signing certificate as the original APK.',
          'ARM-specific execution and end-to-end login on a physical phone have not been measured.']
(out/'notes.md').write_text('\n'.join(lines)+'\n')
PY
files=("$out"/*.apk "$out/SHA256SUMS" "$out/nabd-compact-proof.json")
if gh release view "$tag" >/dev/null 2>&1; then
  test "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$tag" --jq .object.sha)" = "$APK_SOURCE_SHA"
  gh release upload "$tag" "${files[@]}" --clobber
else
  gh release create "$tag" "${files[@]}" --target "$APK_SOURCE_SHA" --prerelease --title 'Nabd 1.1.0 — compact Android APKs' --notes-file "$out/notes.md"
fi
for abi in arm64-v8a armeabi-v7a; do
  echo "::notice title=Verified compact APK::$abi: https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/nabd-1.1.0-$abi.apk"
done
