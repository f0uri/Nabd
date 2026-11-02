# Nabd Midnight — YTXTube-inspired mobile UI

## Source and scope

Requested reference: [Koustubh12345/YTXTube](https://github.com/Koustubh12345/YTXTube), inspected at [`15ad04036173bb3c81f67b45b68cf5b761e7218a`](https://github.com/Koustubh12345/YTXTube/tree/15ad04036173bb3c81f67b45b68cf5b761e7218a).
The reference is a Kotlin/Jetpack Compose application licensed GPL-3.0.
Its home/settings screenshots informed this **independent React Native implementation**:
near-black canvas, large white headings, red accents, rounded charcoal chips,
wide media and an outlined floating dock. No Kotlin code, logos, icons, screenshots,
fonts, shaders or other reference assets were copied into Nabd. This is not a port
of the reference's refraction/shader implementation or its YouTube features.

## Implemented

- Shared Midnight tokens and synchronized `design/tokens.json`; historical HTML
  prototypes retain their own older CSS, clearly labelled in the design guide.
- Original Nabd pulse/wordmark header and existing working menu, compose, search
  and chat shortcuts. Six real bottom destinations, profile avatar, unread badge,
  RTL behavior and 44dp minimum navigation/filter targets remain.
- Native `expo-blur ~14.1.5` (Expo SDK 53), dark tint and Android's opt-in
  `dimezisBlurView` backend **only on the bottom dock**. Cards are filled surfaces,
  not claimed to be native glass. Opaque fallback while checking reduced
  transparency, on a failed accessibility query, when reduced transparency is
  enabled, and on web. This is blur, not a claim of pixel-identical Liquid Glass.
- Dock overlays the screen, mounted after its contents. Scroll clearance accounts
  for safe-area bottom and accessibility font scaling in feed, friends, menu,
  notifications, profile and search. Reels metadata/actions and toast clear it too.
  Video uses TextureView so it can compose behind Android blur.
- Real feed chips: all / friends / photos / videos. Filtering uses loaded post
  metadata without mutating shared state. Legacy image records without `kind`
  remain photos. A visible hint explains the loaded-post scope; filtered pages
  retain an explicit load-more button even if every current post is excluded.
  Filtered views do not auto-fetch indefinitely to find a match. Reset restores
  the complete feed and normal infinite paging.
- Feed media spans the available width; responsive auth layout, original mascot,
  registration photo, password controls, errors and loading behavior are retained
  in the new colors. Connection gate, chat, profile, notifications, menu and
  administration surfaces were audited for hardcoded light backgrounds.
- Video feed cards now load native controls only after an explicit tap, instead
  of attempting to render a video URI with an Image. No automatic playback;
  leaving the app pauses playback, unmount releases the player.
- Measured contrast regression gates retained: body text >=4.5:1, interactive
  boundaries >=3:1 and icon foreground >=7:1 on shared control surfaces. Reaction
  glyphs use separate standalone and badge inks so amber badges stay legible.

Dark appearance is intentional for this adaptation. No nonfunctional theme/palette
selector, mini-player, music tab or downloader was added.

## Upgrade safety

Version **1.2.0 / Android code 3**. Package remains `app.nabd.social`.
No auth/storage keys, account schemas, API endpoints, transport or data migrations
were changed. Install the new signed APK **over** the existing application;
do not uninstall it or clear its data. The existing CI signing-certificate gate
must pass before any APK is offered. Session preservation is the intended
upgrade path, not a claim of a physical-phone migration test.

The added native dependency and native configuration change the fingerprint
runtime. `runtimeVersion: { policy: 'fingerprint' }` remains unchanged; do not
force this build into an old runtime or claim existing APKs receive native blur
through OTA. The legacy native-compatibility check remains fail-closed.
Release filename/title now derives from the application version instead of
hardcoding 1.1.0.

## Verification

Local verification performed during implementation:

- `npm run typecheck`.
- `node --test tests/*.test.cjs`: **169 passed** (including eight new Midnight
  behavior tests; subsequent edits are also revalidated by CI).
- `python3 -m unittest discover -s tests -p 'test_*.py'`: **9 passed**.
- Server `node --test tests/*.test.js`: **74 passed**; no server code changes.
- `EXPO_OFFLINE=1 npx expo export --platform android` and the existing
  `node scripts/verify-release-bundle.cjs`: passed.
- Shell syntax checks on capture, emulator-smoke and release scripts.

Native build is deliberately an exact-source fresh Gradle build, not reuse of
an earlier APK. CI retains runtime, native password known-answer, launcher,
size, ABI, identical-DEX/assets and original signing-certificate gates.
The fresh emulator now also checks visibility of the new auth screen **or**
its network-dependent connection gate and captures `nabd-midnight-startup.png`.
`nabd-midnight-ui-proof.json` records which screen was actually reached; it
never counts a connection screen as a successful login. Both proofs and the
screenshot are saved as `nabd-native-verification` artifacts.

Authenticated feed/dock appearance, blur performance, ARM-specific execution,
physical-phone upgrade/login and end-to-end social operations are **not** proven
by Node component tests or a startup screenshot. No account was created or used
for this UI task. Internal `arena/**` push builds produce exact-commit APK/proof artifacts; fork
PRs still only inspect, and automatic publication remains main-only. This also
avoids depending on manual Actions dispatch permissions.

Native run/download details must be reported only after CI
actually finishes; this document alone is not an APK publication claim.


## Native result — verified build

[Android run 41529655720](https://github.com/f0uri/Nabd/actions/runs/41529655720)
**succeeded** for exact source `cadced8cab7fabf4032b5babcd16406e70eda214`.
Fresh Gradle APK build: 22m13s; actual emulator/signature/content verification:
1m28s. Canonical native runtime:
`ae74f976f4eeef4baa930d5db11ace4fb270d4a4` (not an old APK runtime).

The API-accessible check annotations confirm the native emulator password test
passed and both phone variants passed the original-certificate, shared-code,
icons, native libraries, ABI, size and runtime gates. Signing certificate SHA256
remains `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.

| File in the artifact ZIP | Bytes | SHA256 |
| --- | ---: | --- |
| `app-arm64-v8a-release.apk` | 20,494,520 | `79e97b0c7ac8a26568fbd7ddd3a5c41c593ba0ac2b1faf1d5a207150ab4436d4` |
| `app-armeabi-v7a-release.apk` | 19,039,406 | `9acc1a7831d28db91798b1d5a1fa6b891adc9a4298ebaafae88288e190b32a82` |

- [APK artifact ZIP](https://github.com/f0uri/Nabd/actions/runs/41529655720/artifacts/13181943797)
  (GitHub sign-in may be required): extract and choose the matching ARM APK.
  The ZIP also contains an x86_64 emulator build; do not choose that on an ARM phone.
- [Verification JSONs and actual startup screenshot](https://github.com/f0uri/Nabd/actions/runs/41529655720/artifacts/13181564875).

The workflow's startup-visibility/screenshot gate passed. However, this sandbox
could not download the artifacts because their Azure storage host is outside
its outbound network allowlist; the image and the auth-vs-connection JSON were
**not locally inspected**. This is not an authenticated feed/dock or physical
phone test. The files remain downloadable through GitHub in the user's browser.
No public release or main-branch merge was performed. These are the verified
branch-build artifacts, not a renamed old APK.
