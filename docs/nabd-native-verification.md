# Nabd native Kotlin candidate: verified, not a completed migration

## Successful build/device gate

- Tested application source: `3c9c0e2a203ea026c1376f7cfbf7fd638ba0bfe9`.
- Full successful run: https://github.com/f0uri/Nabd/actions/runs/41820842008
- Candidate APK ZIP: https://github.com/f0uri/Nabd/actions/runs/41820842008/artifacts/13316493748
- Device proof and UI screenshots ZIP: https://github.com/f0uri/Nabd/actions/runs/41820842008/artifacts/13316907472
- PR 38 remains open and is not a production-ready migration.

The APK artifact contains `app-release.apk` and `build-proof.json`. The device
artifact contains `device-proof.json`, `native-auth.png`, and
`native-update-ui.png`. The update screenshot deliberately uses a synthetic
future release fixture; it does not announce an actual published update.

## Actual release-APK inspection

| Property | Result |
| --- | --- |
| Package | `app.nabd.social` |
| Version | `2.0.0-native-candidate`, code `4` |
| Size | 2,606,155 bytes |
| SHA-256 | `270005177fbb7a57190e21856df277bd840c75830fcd325cb9964da9b1b67a83` |
| Original signing certificate SHA-256 | `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c` |
| Native-only gate | Compiled Android payload, no React Native bundle/React Native or Hermes libraries |

The existing public development signing key preserves upgrade identity. It is
**not a private production signing security boundary**.

## What passed

- Kotlin compilation and JVM protocol, bounded-reader and update-policy tests.
- Native-generated synthetic vault read by the unchanged legacy TypeScript reader.
- Actual release APK package/version/certificate/native-only inspection.
- Fresh Android 30 x86_64 emulator: install the old RN APK, write a private-file
  marker, install the Kotlin release APK with `adb install -r`, preserve the
  marker, execute the native password known-answer test and display the new login.
- Android instrumentation: native login validation/callback; disabled Install at
  24% download and enabled Install in a synthetic ready state; encrypted import
  of a synthetic legacy session without modifying its original SQLite row.
- Actual login and update-dialog screenshots collected successfully.

Earlier failed runs exposed missing baseline artifact-read permissions and
screenshot lifetime/shell-command mistakes. Those are fixed in the successful
run above; failed runs were not treated as verified builds.

## What has NOT been established

- Physical-phone performance, real-account login/session migration or complete
  preservation of every visible feature.
- A real future-release download and Android installer completion end to end.
- Rich chat/media/read/reply/edit/delete parity, local stories migration, complete
  avatar/comment/reaction/share controls or a native GraphQL adapter.

Do not install this candidate over a daily-use account yet. Android 8+ is
required. Code 4 normally cannot downgrade to the old code 3; do not uninstall or
clear data to bypass that. The first accepted stable native release must use a
higher code. Native production publication remains explicitly gated off; no
merge, stable release or incompatible Expo OTA was performed.

The sandbox cannot fetch Azure-hosted Actions artifact bodies, so the ZIPs and
screenshots were not downloaded or visually inspected here. The successful job,
artifact IDs and APK proof above were read through GitHub's API. See
`native-android/README.md` for architecture, update policy and remaining limits.
