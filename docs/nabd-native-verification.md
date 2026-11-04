# Nabd native Kotlin candidate: verified, not a completed migration

## Successful build/device gate

- Tested application source: `51ec6fa3736ae560f508e033c0ee1107aa0c74bb`.
- Full successful run: https://github.com/f0uri/Nabd/actions/runs/41890121475
- Earlier corrected-layout run: https://github.com/f0uri/Nabd/actions/runs/41832602161
- Previous verified social candidate: https://github.com/f0uri/Nabd/actions/runs/41826397358 (`7175a19`)
- Candidate APK ZIP: https://github.com/f0uri/Nabd/actions/runs/41890121475/artifacts/13353940922
- Direct APK download (pre-release): https://github.com/f0uri/Nabd/releases/download/native-candidate.3/app-release.apk
- Device proof, feed screenshots and font license ZIP: https://github.com/f0uri/Nabd/actions/runs/41890121475/artifacts/13353966444
- PR 38 remains open and is not a production-ready migration.

The APK artifact contains `app-release.apk` and `build-proof.json`. The device
artifact contains `device-proof.json`, `native-auth.png`, `native-update-ui.png`,
`native-feed-ui.png` and `native-feed-large-text.png`. The update screenshot deliberately uses a synthetic
future release fixture; it does not announce an actual published update.

## Actual release-APK inspection

| Property | Result |
| --- | --- |
| Package | `app.nabd.social` |
| Version | `2.0.0-native-candidate.3`, code `6` |
| Size | 2,731,688 bytes |
| SHA-256 | `77c861203edab6727c4b16af8353cd2a81f17ad69a191a77c62d0a5414554605` |
| Original signing certificate SHA-256 | `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c` |
| Native-only gate | Compiled Android payload, no React Native bundle/React Native or Hermes libraries |

The existing public development signing key preserves upgrade identity. It is
**not a private production signing security boundary**.

## What passed

- Kotlin compilation and 15 JVM protocol, rich social-action, bounded-reader and update-policy test methods.
- Native-generated synthetic vault read by the unchanged legacy TypeScript reader.
- Actual release APK package/version/certificate/native-only inspection.
- Fresh Android 30 x86_64 emulator: install the old RN APK, write a private-file
  marker, install the Kotlin release APK with `adb install -r`, preserve the
  marker, execute the native password known-answer test and display the new login.
- Android instrumentation: native login validation/callback; disabled Install at
  24% download and enabled Install in a synthetic ready state; encrypted import
  of a synthetic legacy session without modifying its original SQLite row.
- Additional instrumentation (twelve test methods in total) exercises rich-chat
  reply/edit/delete/reaction callbacks; comment moderation/likes; explicit avatar
  removal versus ordinary save; story form validation, read-only owned-story
  import, account isolation and persistence; scoped read-only image sharing;
  and synthetic feed visual fixtures (inline avatar decoding, malformed-image
  fallback, normal and 1.3x text rendering).
- The unchanged legacy TypeScript reader now also accepts native replies, image
  messages, edited messages and liked comments. JVM tests check denied ownership
  actions do not write, deletion tombstones clear media/reactions, repeated reads
  do not write, comment deletion cleans its likes index and dates retain JS-style
  millisecond precision for legacy feed sorting.

## Native functionality added since the first candidate

- Real Telegram-backed reply/edit/delete/reactions, chat photo sending and read
  receipts. Chat polls every 15 seconds only while its screen is resumed; failed
  receipts do not cause an automatic retry loop.
- Comment likes and author/post-owner deletion; all six post reactions.
- Avatar selection/removal and Android text/photo sharing via scoped content
  URIs with read access only. Opening a chooser never fakes a completed share.
- Local story text/photo creation, viewing and deletion. Atomic files are scoped
  per account/namespace; legacy SQLite is opened read-only. The 80-story/16MiB
  bound and import failures never delete original rows. Deletion confirmation
  explains that the old legacy copy is retained. Stories remain LOCAL, as before.
- Actual login and update-dialog screenshots collected successfully.

Earlier failed runs exposed missing baseline artifact-read permissions and
screenshot lifetime/shell-command mistakes. Those are fixed in the successful
run above; failed runs were not treated as verified builds.

## What has NOT been established

- Physical-phone performance, real-account login/session migration or complete
  preservation of every visible feature.
- A real future-release download and Android installer completion end to end.
- Native GraphQL adapter: still unimplemented. No automatic backend switch.
- Complete real-user regression of rich chat, old photo URI migration, the actual
  photo picker/receiving application and background/foreground polling. Synthetic
  fixtures and UI callbacks are not a live Telegram account or phone test.

Do not install this candidate over a daily-use account yet. Android 8+ is
required. Code 5 normally cannot downgrade to old codes 3/4; do not uninstall or
clear data to bypass that. The first accepted stable native release must use a
higher code. Native production publication remains explicitly gated off; no
merge, stable release or incompatible Expo OTA was performed.

The sandbox cannot fetch Azure-hosted Actions artifact bodies, so the ZIPs and
screenshots were not downloaded or visually inspected here. The successful job,
artifact IDs and APK proof above were read through GitHub's API. See
`native-android/README.md` for architecture, update policy and remaining limits.

## History

The first, narrower code-4 candidate was verified in run
https://github.com/f0uri/Nabd/actions/runs/41820842008. It is not the latest APK.
The expanded social candidate first passed in run
https://github.com/f0uri/Nabd/actions/runs/41825192400; the latest source/run above
also includes lifecycle-bound chat refresh, date compatibility and the explicit
legacy-story deletion disclosure.

## Reference glass interface (verified in run 41869131895)

The home feed layout, typography and colour hierarchy were rebuilt after the
first social candidate was rejected as loose, pink and blank-avatared.

- Header, filter chips, composer, post cards and bottom dock now use compact
  bounded rows; post actions are one aligned row and reactions/links live in a
  contextual menu instead of loose text buttons.
- Tajawal Regular/Bold (SIL Open Font License, from `google/fonts/ofl/tajawal`)
  ships inside the APK; no CDN font download and no default system Arabic look.
- Account avatars and posts use bundled decoding for the existing
  `data:image/...;base64` values. Instrumented fixtures assert two inline images
  decode and that a malformed inline photo shows a visible fallback rather than
  an empty circle.
- Verified automatically on a 1080x1920 Android 30 emulator: brand red accents
  and decoded inline avatars are present in the captured synthetic screenshot.

## Layout ideas taken from the NabdHome.tsx reference

Reference only: no seeded people or posts were added to production.

- Animated نبض wordmark with a sweeping ECG pulse line drawn on Canvas.
- Burger on the left, circular raised search/chat buttons and a red circular
  create button on the right of a 64dp header.
- Floating rounded dock: selected tab expands to 1.35 weight with a raised pill,
  translucent background, white hairline border and elevation.
- Filter chips use a light selected chip with dark text instead of a red fill.
- Composer row: avatar, "فيم تفكر؟" pill field and an accent-tinted photo button.
- Local story tray uses 106x172 rounded cards with a "أنشئ ستوري" card, a red
  ring avatar and a bottom name label for each real local story.
- Post cards: 17sp/28sp body text, reaction dot with count plus comment count,
  divided three-action row and an inline "اكتب تعليقاً..." row.
- Profile screen: cover band, overlapping circular avatar, name/bio, real counts
  (posts/friends/people) and edit/compose buttons.

Synthetic only: the screenshot uses an explicitly labelled test account and the
same production widgets. It is **not** a physical-phone capture and it does not
prove account migration, media picker, share or installer completion.

## What the reference interface changed (run 41869131895)

The supplied Nabd Pro reference (`facebook.html`) replaced the previous
charcoal/red design. Applied natively:

- Light blue canvas gradient with translucent white "glass" cards; the whole app
  switched to a light Material colour scheme with dark status/navigation icons.
- Header: floating glass bar with a beating red dot before the word نبض (the only
  animated brand element kept), plus circular create/search/messages icon buttons.
  The long ECG wave was removed as requested.
- Composer, 84x130 local story cards, post cards (13sp text, 135dp media, reaction
  count row, three action buttons) and a floating pill tab dock, all per the
  reference geometry.
- Facebook-style reactions: the classic thumbs-up button is gone; the نبضة button
  reacts with ❤️, long-press opens a floating emoji dock (❤️ 😂 😮 😢 😡) and the
  chosen emoji flies up. Only protocol-supported reaction keys are written.
- Icons replace emoji glyphs for app actions (create, search, messages, photo,
  refresh, delete, public/private audience); reaction emojis stay emoji as in the
  reference.

Not implemented on purpose, because the reference data behind them is fictional:
the points balance, "+25 نقطة" rewards and the Instagram verification badges.
No points system exists in the app and none was faked.

## Floating-card and vector-icon pass (verified in run 41890121475)

- Every screen uses the `#F0F4F9` canvas; posts, conversations, alerts and menu
  rows are separate white cards with a 22dp radius, 10dp elevation and outer
  margins. No flat white screen with divider lines: dividers were removed.
- App bar: animated blue ECG wave drawn as a vector path beside نبض and before
  the beating red dot. The wide search field is hidden and only appears when the
  search icon is pressed.
- Post actions are vector-only: نبضة (filled heart with a pulse line inside),
  تعليق and مشاركة. Long-pressing نبضة opens a floating bar with six vector
  reaction icons; pressing مشاركة copies the post reference and shows
  "تم نسخ رابط المنشور"; long-pressing مشاركة opens the real Android share sheet.
- No emoji glyph remains in the interface, and no AI-style or technical text is
  shown: the version/stack/service footer was removed from the menu, and the
  reel/alert screens show short plain sentences.
- The حسابي tab opens the profile screen directly; التنبيهات is built from real
  unread incoming messages, not invented notifications.
