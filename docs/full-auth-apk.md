# Nabd 1.1.0 — full supplied auth UI and native password worker

The user explicitly reported that PR19 had not resolved their phone's login
problem and requested the **whole supplied interface**, original launcher icon,
and a new APK download link. This supersedes the partial UI adaptation in PR19;
we do not treat its synthetic scheduler improvement as proof of phone success.

## Complete auth screen port

The Flutter attachment is reimplemented in the existing React Native/Expo app:
full-width blue/deep gradient, rounded 40px header, ECG/circles decoration,
centered greeting, original 52px logo, large 150px vector character (112px with
keyboard), eye covering/gaze/error poses, overlapping 32px white card, segmented
tabs, optional avatar picker, name/unique-handle preview, real credential fields,
field validation, four-part live password-strength meter, password visibility,
recovery-unavailable explanation, gradient action button and account-mode footer.
The supplied visual layout/palette/character geometry replaces the former small
side-by-side hero. The SVG port uses focus-driven poses rather than perpetual
JavaScript redraw loops; no promise of pixel-identical Flutter animation timing.

Real auth handlers/session persistence remain authoritative. No dummy 1.6/2s
wait, 1.4s fake success, Firebase switch or placeholder recovery email was copied.
Busy state disables fields/tabs/avatar/duplicate submission. Errors remain
visible; no navigation on failed auth. Keyboard compaction, screen scrolling,
RTL, safe insets and screen-reader labels are preserved. Other social screens,
chat/navigation and the compact one-tap update UI are unchanged.

## Approved native addition

The requested new APK bundles async `react-native-quick-crypto@1.1.9` with
`react-native-nitro-modules@0.33.2`, matching its generated/development version,
and `react-native-quick-base64@3.0.0`. Expo SDK53-compatible linear-gradient and
SVG libraries render the supplied design. **Never use QuickCrypto 0.x**: the
NUL-truncation advisory GHSA-wrf3-fwx3-8jrv affects it.

Only async native PBKDF2 is used (a C++ worker), never pbkdf2Sync and never a
replacement global crypto shim. Passwords are converted using the existing UTF-8
encoder and salts decoded to the exact original 16 bytes. **SHA256, 210,000
rounds, 32 derived bytes** and all accounts/session/vault schemas are unchanged.
No passwords or user-derived keys are cached, logged or stored by this adapter.

A fixed non-secret NUL + Arabic + emoji known-answer test runs once at startup,
using the complete 210,000-round native path. Its promise is shared with login;
a bad native output prevents credentials being hashed with incompatible bytes.
Only a fixed success marker is logged. Missing bindings in older shells/Expo Go
retain the exact existing WebCrypto/JS fallback; broken native operations fail
closed rather than accepting a wrong password. Network/vault checks are intact.

## APK distribution and evidence

- Expo/app version **1.1.0**, Android versionCode **2**, same package
  `app.nabd.social`, original red/white launcher and adaptive icon artwork.
- APK workflow builds the real release APK, checks its packaged runtime against
  the independently captured native fingerprint, then installs/launches that
  exact APK on an Android 35 x86_64 emulator with KVM.
- Publication is blocked unless the actual native known-answer test succeeds.
  The smoke test never enters user credentials or writes to the shared vault.
- Actual APK bytes must contain ARM64/x86_64 QuickCrypto/Nitro libraries,
  launcher/adaptive resources and the full-auth/chat/native-probe bundle markers.
- The verified APK, SHA256 and allowlisted proof JSON are published as a GitHub
  prerelease from the exact main source SHA. A direct `.apk` download link is
  given to the user, not merely an Actions ZIP.
- Native dependency changes intentionally create a **new runtime**. Never force
  this bundle onto old fingerprints. The legacy publisher skips incompatible
  native cohorts. Future compatible JS-only changes can again use OTA.

Node tests use a Node crypto binding to verify adapter behavior/encoding; these
are NOT a substitute for the mandatory Android emulator native test. Neither
proves end-to-end login timing on the user's physical phone. Network latency,
bot permissions, vault conflicts and phone receipt/installation remain separate.
Do not claim this UI change itself fixes all possible login failures. Install
over the existing app; do not advise uninstalling/clearing data.

### Emulator observation/retry hardening

The first build completed and passed packaged-runtime checks, but its release
logcat success-marker check did not observe success; that APK was NOT published.
Release console forwarding is not a reliable native-work oracle. The app now
writes a fixed non-secret cache probe (pending/passed/unavailable/failed) after
the same real known-answer test. A fresh rooted CI emulator reads only that
probe, never an account/password. Missing or failed probes still block release;
initialization/startup failures are sanitized and emitted as check annotations.
Build and verification are separate jobs, retaining the built APK artifact so a
verification-only retry does not need another native rebuild. Runtime capture
is passed between jobs and checked against the APK's actual fingerprint.

The automatic safe prior-log diagnostic observed `NABD_NATIVE_CRYPTO_UNAVAILABLE`,
not a successful native derivation. The follow-up therefore also isolates the
pinned library's **Pbkdf2 async Nitro hybrid** directly (its published native
spec), rather than importing its entire Node/browser compatibility barrel and
initializing unrelated hash/base64/global shims. This does not replace or rewrite
the cryptographic algorithm. Inputs are exact-sized ArrayBuffers and the same
full-strength known-answer gate remains mandatory on the real emulator.


The second APK also built and passed its packaged-runtime guard, with an artifact
retained, but emulator tooling exited before a probe result was reported. The
verification runner now uses an explicitly rootable AOSP API30 image. Reusing
that exact APK is allowed only when git ancestry/application-source bytes are
unchanged, the fresh native fingerprint matches, and all embedded public build
configuration values match the current build. Otherwise a fresh build is forced.
The release is tagged with the **APK's original source SHA**, not falsely with
later CI-only commits. Actual native probe success still gates every publication.


The wrapped emulator action continued to exit before invoking the app probe.
The verifier now owns SDK setup, AVD launch, ADB and boot readiness directly in
one Bash process, avoiding nested shell/background quoting and wrapper startup
races. The actual APK, application source and full-strength native known-answer
publication gate are unchanged. A missing/failed probe still blocks release.

### Compiled icon verification

The emulator now boots and the real full-strength native password test passed in
run 41334053678. Publication still stopped at the old icon filename assertion.
Android resource optimization can shorten ZIP filenames, so icon validation now
resolves `mipmap/ic_launcher` and its foreground through `aapt2 dump resources`,
checks that their referenced raster files really exist, and inspects the compiled
adaptive-icon XML. This retains the actual-APK gate without depending on source
filenames surviving packaging. Offline resource-parser tests are not native proof.
