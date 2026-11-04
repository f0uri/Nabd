# Nabd Kotlin migration candidate

Real Android application in **Kotlin + Jetpack Compose**. No WebView, embedded
React Native runtime, JavaScript bundle, Flutter engine or copied HTML login.
The previous Expo project remains in `mobile/` as a working rollback/reference;
keeping its source is not a claim that production has already migrated.

## Status: candidate, NOT a completed production replacement

Package `app.nabd.social`, version code 6, version name
`2.0.0-native-candidate.3`, Android **8.0/API 26+**. The older Expo application
supports older Android releases; this candidate must not be offered to those
phones as a compatible update. There is no measured physical-phone smoothness
or ARM end-to-end login result yet.

### Implemented natively

- RTL dark/red visual design using the user's supplied `NabdHome.tsx` as a visual
  reference, **not** importing its synthetic users, posts, conversations or fake
  media. Native lazy lists have stable IDs; IO, image compression, crypto and APK
  verification run off the UI thread. This is an architecture improvement, not
  an invented frame-rate/latency benchmark.
- New login/registration form inspired by the supplied rounded HTML card:
  animated accent border, pill-shaped fields, proper validation and password
  visibility. The old character/mascot is absent. No sample external links,
  `window.open`, fake login or non-existent password-recovery redirect.
- Existing Telegram v1 accounts, password hash (PBKDF2-HMAC-SHA256, 210,000
  rounds, UTF-8 including NUL), namespace, session format and vault schema.
  No Firebase/new empty vault and **no automatic vault initialization**.
- Existing-account login, registration, encrypted native session persistence;
  one-time **read-only** import from the old `RKStorage` session key. It never
  deletes or modifies that SQLite row, account data or old application files.
- Real text/photo feed, posting with bounded JPEG compression, friendship/search,
  profile name/handle/bio, likes and comments, deletion of own posts, basic text
  conversations and reading existing messages/images. No mock data fallback.
- Shared JSON extensions are preserved. Before every mutation, the complete
  v1 schema is validated; writes serialize locally and check the live pin before
  upload, before pinning and after confirmation. As in the existing transport,
  this detects **some**, not all cross-device races. It is not an atomic database.
- Chat refreshes every 15 seconds only while its screen is resumed, with manual
  refresh still available. Failed read receipts are not retried in an endless
  busy/idle loop. This is polling, not a claim of WebSocket delivery.
- `UpdateDialog`: native styled panel resembling the supplied screenshots, using
  actual release notes, byte progress, cancel, verification and install states.
  Android 12+ blurs the background layer; older Android uses a filled fallback.

### Still required before declaring a complete migration

- Real-device regression of the newly added chat reply/edit/delete/reactions,
  image sending/read receipts, comment likes/moderation, avatar selection/removal,
  all six post reactions and Android text/photo sharing. These are now wired to
  the real Telegram repository, not just UI placeholders.
- Local stories are now supported with account-scoped atomic persistence and
  read-only import from `nabd.stories.v1`. Like the old implementation they are
  local, not server-published; unreadable old image URIs cannot be magically
  recovered. Imported private image files are compressed into native storage.
  The local story limit is 80 entries / 16MiB. Import failures never clear old
  rows. Deleting a story removes the native copy only; the confirmation explicitly
  explains that the original legacy row is retained during migration.
- Native GraphQL backend adapter; the current candidate explicitly implements
  the active Telegram backend only. No silent backend switch or invented content.
- Complete end-to-end regression of every existing feature and a real physical
  phone/account upgrade test. Synthetic migration/interop tests are not that test.
- Product acceptance of minimum Android support and a production signing plan.
  The existing certificate is a **public development key**; matching it enables
  upgrades but does not make it a private production signing identity.

Do **not** replace a user's daily app with this candidate and call the migration
finished. Android version codes prohibit a normal downgrade; a rollback build
would need a higher code and the original matching certificate. No uninstall or
clear-data workaround is appropriate for preserving user data.

## Update delivery (not Firebase push)

`ApkUpdater` checks GitHub Releases shortly after startup and from Menu → Update.
It is not a silent install or a claim that already-installed Expo APKs suddenly
support this native updater. Users need a first compatible Kotlin APK first.

Only a `nabd-native-update.json` asset on a non-draft, non-prerelease release is
accepted. It must describe the same package and a newer code; the client selects
an ABI asset or the universal APK. HTTPS/repository download paths, redirect
hosts, size limit (150 MiB), exact byte count, SHA256, actual APK package/version
and the **installed signing certificate** are checked. Partial downloads are
never installable. Progress is real and throttled; cancellation closes the HTTP
call, removes partial data and prevents a pending installer launch.

Install is enabled only after verification and re-verifies before handing a
FileProvider URI to Android's installer. Unknown-source permission uses the OS
settings page; the user must return and tap Install again. Android asks for final
confirmation. The UI never reports successful installation just because an
Intent was opened. No background auto-install or verification bypass exists.

Manifest shape (values below are a schema illustration, not a live update):

```json
{
  "schema": 1,
  "packageName": "app.nabd.social",
  "versionCode": 6,
  "versionName": "2.0.1",
  "notes": ["تحسينات الواجهة"],
  "apks": {
    "universal": {
      "url": "https://github.com/f0uri/Nabd/releases/download/kotlin-v2.0.1/nabd-2.0.1-universal.apk",
      "sha256": "<actual lowercase SHA256 of the signed APK>",
      "size": 123456
    }
  }
}
```

The producer is `scripts/publish-native-release.sh`. CI publishes only after the
complete native build/device job succeeds, only on `main`, only with explicit
repository variable `NABD_NATIVE_RELEASE_ENABLED=true`, and only with a stable
numeric `versionName`. The current `*-candidate` version is deliberately rejected.
The first accepted stable build must also use a code **greater than 6** so candidate installations can upgrade.
No native production release has been authorized or published by adding this
code. The native and old Expo release pipelines remain distinct.

## Build and verify

JDK 17, Android SDK 35 and the official Gradle 8.11.1 wrapper. The wrapper jar and
script came from `gradle/gradle` tag `v8.11.1` (Apache-2.0); jar SHA256
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
CI obtains the original public Expo SDK 53 development keystore into ignored
`signing/`; its certificate must equal
`fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
Never put private keys or tokens in Git/chat. Direct-mode bot configuration
continues the existing explicitly extractable-token design; Kotlin does not
make a shared bot token secret or the shared vault confidential.

```sh
# With the existing signing key available at signing/debug.keystore:
./gradlew testReleaseUnitTest assembleRelease
# Then from the repository root (synthetic fixture, no network/account write):
node native-android/scripts/check-interop.cjs
```

`Kotlin Android candidate` CI also:

1. Checks the APK is native-only and has the original package/certificate.
2. Installs the old verified x86 APK, writes a local test marker and upgrades with
   `adb install -r`, never uninstalling/clearing data.
3. Runs the actual native known-answer hash and checks the new auth is visible.
4. Tests native login validation, disabled installation at 24% and enabled ready
   state, and synthetic legacy-session import with Android Keystore encryption.
5. Captures auth and **synthetic update-state UI** screenshots. The update UI
   screenshot is not proof of downloading/installing a real future release.

Source checks alone are not compilation/device proof. See the actual CI result
before calling an APK verified. No local Android SDK/JDK is available in the
coding sandbox; actual compilation and device tests run in GitHub Actions.

## Feed visual correction (candidate.3)

- Compact red/charcoal header/dock, bounded content cards, one row of post actions,
  contextual reactions/menu and no standalone refresh text or unavailable video
  filter in the feed. Local story identity is still explicit, not a fake social
  story tray. No seeded posts are added to production.
- Tajawal Regular/Bold from `google/fonts/ofl/tajawal`, SIL Open Font License;
  the copyright/license is included both in the source tree and APK assets.
- The existing inline `data:image/...;base64` avatars/media now decode into bytes
  on IO before Coil loading. Loading/invalid images retain a visible fallback.
  Account avatars fall back to their actual name initial, not an empty circle.
- Instrumented visual fixtures exercise inline avatar decoding, malformed-image
  fallback and normal/1.3x text rendering. Screenshots use an explicitly labelled
  synthetic account and the same native feed/header/dock widgets as production.
