"""Fast PR invariants. Source checks are not Android execution proof."""
from pathlib import Path
root=Path('native-android')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'REQUEST_INSTALL_PACKAGES' in manifest and 'FileProvider' in manifest
assert 'android:allowBackup="false"' in manifest
code='\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.kt'))
for forbidden in ('android.webkit.WebView','ReactActivity','ReactNativeHost','AuthMascot','window.open','sites.google.com'):
    assert forbidden not in code, 'Legacy runtime, mascot or sample redirect introduced'
for required in ('getPackageArchiveInfo','apkContentsSigners','MessageDigest','getUriForFile','canRequestPackageInstalls','withContext(Dispatchers.IO)','TELEGRAM_CONFLICT','OPEN_READONLY'):
    assert required in code, required
assert 'installIntent' in code and 'currentCoroutineContext().ensureActive()' in code
assert 'passwordHash' in code and '210000' in code
print('Native migration and update source invariants passed (not a device test).')
