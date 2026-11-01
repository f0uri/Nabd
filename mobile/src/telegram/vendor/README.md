Vendored ESM subset of @noble/hashes 1.8.0 (MIT, LICENSE included), from the
published npm tarball. Only change: utils.js self-package crypto import points
to ./crypto.js; source-map footer references and one trailing space removed. No native modules/dependency
manifests changed. We use sha256 and pbkdf2Async; no randomBytes fallback.
Randomness is supplied by the already installed Expo Modules Core uuid.v4 API.
Review upstream security updates before extending this temporary implementation.
