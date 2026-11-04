"""Inspect the actual candidate; never print compiled configuration or credentials."""
import hashlib, json, os, pathlib, re, shutil, subprocess, zipfile
root = pathlib.Path('native-android')
apk = root / 'app/build/outputs/apk/release/app-release.apk'
tools = pathlib.Path(os.environ['ANDROID_HOME']) / 'build-tools'
signer = sorted(tools.glob('*/apksigner'))[-1]
aapt = sorted(tools.glob('*/aapt'))[-1]
cert = subprocess.run([str(signer), 'verify', '--print-certs', str(apk)], capture_output=True, text=True, check=True).stdout
expected = 'fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c'
assert set(re.findall(r'certificate SHA-256 digest: ([a-f0-9]{64})', cert)) == {expected}, 'Upgrade signing certificate changed'
badging = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], capture_output=True, text=True, check=True).stdout
config=(root/'app/build.gradle.kts').read_text()
version_code=int(re.search(r'versionCode = (\d+)',config).group(1))
version_name=re.search(r'versionName = "([^"]+)"',config).group(1)
assert "name='app.nabd.social'" in badging and f"versionCode='{version_code}'" in badging, 'Unexpected package or version'
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    assert not any('index.android.bundle' in n or 'libreactnative' in n or 'libhermes' in n for n in names), 'Not a native-only APK'
    assert any(n.endswith('.dex') for n in names), 'Compiled Kotlin/Android code missing'
proof = dict(source=os.environ['GITHUB_SHA'], packageName='app.nabd.social', versionCode=version_code,
             nativeKotlin=True, reactNativeBundlePresent=False, signingCertificateSha256=expected,
             apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(), size=apk.stat().st_size,
             candidateOnly="candidate" in version_name, physicalPhoneAccountMigrationTested=False)
(root / 'build-proof.json').write_text(json.dumps(proof, indent=2))
artifact=root/'artifact'; artifact.mkdir(exist_ok=True)
shutil.copyfile(apk, artifact/'app-release.apk')
(artifact/'build-proof.json').write_text(json.dumps(proof, indent=2))
print('::notice title=Kotlin native APK proof::'+json.dumps(proof))
