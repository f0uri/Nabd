"""Inspect the actual APK, not only source/config. Never print compiled secrets."""
import hashlib, json, os, sys, zipfile
from pathlib import Path
from apk_icons import verify_icons

def report_failure(kind, value, traceback):
    # All assertion messages here are fixed, credential-free descriptions.
    reason = str(value) if kind is AssertionError else kind.__name__
    print('::error title=APK content check::'+reason, flush=True)

sys.excepthook = report_failure
apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    abi = os.environ.get('EXPECTED_APK_ABI')
    assert abi in (None, 'arm64-v8a', 'armeabi-v7a', 'x86_64'), 'Unexpected APK architecture'
    abis = [abi] if abi else ['arm64-v8a', 'x86_64']
    for architecture in abis:
        for library in ('libQuickCrypto.so', 'libNitroModules.so'):
            assert f'lib/{architecture}/{library}' in names, 'Missing native password worker'
    if abi:
        packaged = {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')}
        assert packaged == {abi}, 'Single-architecture APK includes unrelated native libraries'
        assert all(z.getinfo(name).compress_type == zipfile.ZIP_DEFLATED for name in names if name.endswith('.so')), 'Native libraries are not download-compressed'
    verify_icons(apk, z)
    bundle = z.read('assets/index.android.bundle')
    for marker in (b'nabd-full-auth-v2', b'nabd-telegram-chat-v1', b'NABD_NATIVE_CRYPTO_SELF_TEST_OK'):
        assert marker in bundle, 'Expected actual application bundle is missing'
    runtime = z.read('assets/fingerprint').decode().strip()
    assert runtime == os.environ['EXPECTED_APK_RUNTIME'], 'APK runtime differs from build job capture'
proof = dict(apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(), runtime=runtime,
             iconResourcesVerified=True, nativeLibrariesVerified=True,
             androidEmulatorNativeSelfTest=os.environ.get('NABD_EMULATOR_SELF_TEST_PASSED') == 'true',
             architecture=abi or 'universal', uiRevision='full-auth-native-v2',
             source=os.environ['APK_SOURCE_SHA'], emulatorApi=30)
Path(sys.argv[2]).write_text(json.dumps(proof, indent=2))
print('Verified APK native libraries, launcher/adaptive icons, embedded UI and SHA256.')

print('::notice title=Verified APK contents::'+json.dumps(proof))
