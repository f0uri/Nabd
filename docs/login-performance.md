# Responsive login without changing credentials

Historical PR19 JavaScript-only release. The subsequent user-requested native
APK/full-interface release is documented in [full-auth-apk.md](full-auth-apk.md).

This release is JavaScript-only and preserves the current and pre-icon OTA
runtimes. No native crypto dependency, reinstall, reset, migration, password
cache or lower work factor is involved.

## Root cause reproduced

Hermes uses the bundled PBKDF2 fallback when WebCrypto is unavailable. Its
`asyncLoop` yielded with a native timer every 10ms of work, but advanced its
CPU-budget timestamp using a delta measured **before** awaiting that timer.
A timer/frame delay >=10ms then counted toward the next budget, which could
cause nearly every remaining round to yield again.

A deterministic 200-iteration test with 16ms timer latency produces **191
yields before the patch** and approximately **20 after**. 100ms latency and a
backward clock are also covered. The timestamp is now reset after resuming;
the CPU work between yields is still bounded by the original 10ms budget.
This is a scheduling fix, not a replacement hash or an increase in thread
priority.

PBKDF2-HMAC-SHA256 remains **210,000 rounds**, 16 raw salt bytes decoded from
the existing hex salt, 32 derived bytes and the same UTF-8/password handling.
The corrected fallback, including real 16ms timers, is checked against Node's
PBKDF2 with Arabic/emoji input. Existing account/legacy/profile/chat tests
continue to exercise the unchanged vault and session logic.

These are automated scheduler and cryptographic correctness results on the
runner, **not measurements of total login latency on a physical phone**.
Network time and JavaScript hashing cost still depend on device/connection.

## Avoid repeated downloads, not authorization checks

The HTTP transport keeps one bounded, serialized Telegram document keyed by
immutable `file_id`. Concurrent reads of that file share the download and each
caller receives a fresh JSON object. Failed reads are evicted and the next file
replaces the previous cached document. There is no disk persistence, password
memoization or cross-provider cache.

Startup status and login can therefore reuse the same document rather than
repeat `getFile` plus download. Each operation still fetches the current chat
pin, validates the entire vault and verifies its session. A changed pin/file
forces a new download. Uploads do **not** seed the cache: commit confirmation
still fetches the actual remotely pinned document. Pre-upload/pre-pin conflict
checks, post-write revision/writeId verification and login's post-hash password
recheck remain intact. Tests cover real HTTP call counts, remote revocation,
failed-write isolation, mutation isolation, coalescing and memory bounds.

## Supplied UI

The attachments are Flutter/Dart; Nabd remains React Native/Expo. The existing
blue welcome/tabs/real form now includes a lightweight adaptation of the supplied
character which covers its eyes while a hidden password field is focused. No
idle animation loops, new native modules, Firebase switch, artificial 1.6/2s
waits or simulated success callbacks were imported. Loading and failure still
come from actual authentication. The slow-request copy no longer incorrectly
attributes password CPU work to the connection.

The release verifier requires `nabd-login-responsive-v1` in the actual exported
bundle and reports `uiRevision=login-responsive-v1`, while retaining the chat,
fonts, startup and compact single-tap update checks.

## Verification

- `npm run --prefix mobile typecheck`
- `node --test mobile/tests/*.test.cjs`
- `cd mobile && npx expo export --platform android --clear`
- `cd mobile && node scripts/verify-release-bundle.cjs`
- `cd mobile && node scripts/publish-legacy-update.cjs --check`

Production publication must also verify the source commit, update ID, runtime
and exported bundle hash for **both** cohorts. Cloud publication is not evidence
that a particular phone received the update. Any future native crypto module
requires a separate explanation/approval because it would require a new APK.
