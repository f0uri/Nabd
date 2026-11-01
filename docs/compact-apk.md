# Compact Android APKs

The original verified universal APK is 117,386,321 bytes (SHA256
`c4eb074807c33e3ea5ae294dc19a3e625cf8038c0d3f070140941bf4b61e20bf`).
Do not report a new size or download link until CI publishes actual assets.

## Build policy

- Expo config plugin survives clean prebuild; no hand-edited ignored native project.
- Release R8 code shrinking/minification and resource shrinking; preserve Nitro JNI names.
- Single-ABI arm64-v8a and armeabi-v7a phone APKs. Separate x86_64 CI APK, not a universal download.
- Compress native libraries (legacy extraction) and JS bundle. This reduces download size,
  not necessarily installed size: native libraries are extracted during installation.
- Recompress deflated ZIP entries at level 9, retain required STORED resources, zipalign,
  re-sign with the same generated build key, and keep the result only if smaller.
- No feature removal, media downsampling, crypto weakening, account/provider migration,
  launcher redesign or data reset. Public config stays selected by the existing CI job.
- New native configuration gets its own normal Expo fingerprint. Never force an older
  runtime or send an incompatible OTA to existing installations.

## Publication gates

1. Existing TypeScript, UI/navigation/account/session/crypto tests.
2. Generated Gradle options must really be consumed by the SDK53 template.
3. Real x86_64 release APK must boot and pass the 210000-round native known-answer test.
4. Each phone APK must contain only its intended native ABI, compressed native libraries,
   native crypto dependencies, the real launcher/adaptive icons, app markers and canonical runtime.
5. All phone DEX and assets must exactly match the emulator-tested build payload.
6. APK signatures must verify and match the original verified APK certificate. No uninstall
   or data clearing is needed as a workaround for a changed key.
7. Both phone downloads must be at least 50% smaller than the original exact-hash baseline.
8. Publish real phone APKs, checksums and explicit proof to a digest-addressed release.

The proof distinguishes actual x86_64 execution from static/shared-code checks on ARM
variants. Physical-device ARM execution, full login and timing are not claimed.

For most modern Android ARM phones use arm64-v8a. A 32-bit Android ARM OS requires
armeabi-v7a even if its processor itself supports 64-bit. Device model/ABI must not be
invented; retain both downloads when the user has not specified their phone.

## Verification-tooling follow-up

Run 41336289110 completed the fresh compact native build, level-9 re-signing,
canonical runtime checks and the actual x86_64 native test. The signing parser
then rejected the SDK's certificate output before comparing certificates. Accept
SDK-range labels/repeated identical digests while still rejecting distinct keys;
only public certificate metadata is emitted for diagnosis. The compiled candidate
may be reused only when ancestry/application source, actual runtime, ABI and all
embedded public provider values match, followed by the same real native and
upgrade-signature gates. Script-only diagnostics never require a blind rebuild.

Run 41338733589 reused the exact compact binary but hit a first-command adbd root
transport failure after boot. Root setup now retries boundedly and still requires
UID 0 plus a working package manager before touching the probe. PR inspection also
checks actual candidate/baseline certificate metadata early; no app logs are read.
