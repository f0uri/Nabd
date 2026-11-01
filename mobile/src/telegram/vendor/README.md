Vendored ESM subset of @noble/hashes 1.8.0 (MIT, LICENSE included), from the
published npm tarball. Initial import changes: utils.js self-package crypto import points
to ./crypto.js; source-map footer references and one trailing space removed. No native modules/dependency
manifests changed. We use sha256 and pbkdf2Async; no randomBytes fallback.
Randomness is supplied by the already installed Expo Modules Core uuid.v4 API.
Review upstream security updates before extending this temporary implementation.

Additional Nabd scheduling-only patch: `utils.js` `nextTick` now yields through
`setTimeout(0)` instead of an already-resolved Promise, so asynchronous PBKDF2
returns control to React Native rendering/input. No hash/HMAC/PBKDF2 algorithm,
iteration count, salt encoding or output format was changed. Tests compare
outputs with Node crypto and verify event-loop progress during derivation.
