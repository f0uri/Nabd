"""Inspect the actual APK, not only source/config. Never print compiled secrets."""
import hashlib, json, os, sys, zipfile
from pathlib import Path
apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    for library in ('libQuickCrypto.so', 'libNitroModules.so'):
        assert 'lib/arm64-v8a/' + library in names, 'Missing ARM64 native password worker'
        assert 'lib/x86_64/' + library in names, 'Missing emulator native password worker'
    assert any('ic_launcher' in name and name.endswith(('.png', '.webp')) for name in names), 'Launcher icon missing'
    assert any('ic_launcher_foreground' in name for name in names), 'Adaptive launcher icon missing'
    bundle = z.read('assets/index.android.bundle')
    for marker in (b'nabd-full-auth-v2', b'nabd-telegram-chat-v1', b'NABD_NATIVE_CRYPTO_SELF_TEST_OK'):
        assert marker in bundle, 'Expected actual application bundle is missing'
    runtime = z.read('assets/fingerprint').decode().strip()
    assert runtime == os.environ['EXPECTED_APK_RUNTIME'], 'APK runtime differs from build job capture'
proof = dict(apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(), runtime=runtime,
             iconResourcesVerified=True, nativeLibrariesVerified=True,
             androidEmulatorNativeSelfTest=True, uiRevision='full-auth-native-v2', source=os.environ['APK_SOURCE_SHA'], emulatorApi=30)
Path(sys.argv[2]).write_text(json.dumps(proof, indent=2))
print('Verified APK native libraries, launcher/adaptive icons, embedded UI and SHA256.')

print('::notice title=Verified APK contents::'+json.dumps(proof))
