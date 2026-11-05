"""Fast PR invariants. Source checks are not Android execution proof."""
from pathlib import Path
root=Path('native-android')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'REQUEST_INSTALL_PACKAGES' in manifest and 'FileProvider' in manifest
assert 'android:allowBackup="false"' in manifest
code='\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.kt'))
for forbidden in ('ReactActivity','ReactNativeHost','AuthMascot','window.open','sites.google.com'):
    assert forbidden not in code, 'Legacy runtime, mascot or sample redirect introduced'
# The official Instagram embed is the one WebView of the app and it lives in ui/Reels.kt only.
webviews = [p for p in (root/'app/src/main/java').rglob('*.kt') if 'android.webkit.WebView' in p.read_text()]
assert all(p.name == 'Reels.kt' for p in webviews), 'WebView is only allowed inside the Instagram embed player'
reels = root/'app/src/main/java/app/nabd/nativeapp/ui/Reels.kt'
assert 'instagram.com/reel/' in (root/'app/src/main/java/app/nabd/nativeapp/data/Reels.kt').read_text(), 'Instagram embed url missing'
assert 'reelEmbedUrl(ref.id)' in reels.read_text(), 'The player must load the official Instagram embed'
assert 'shouldOverrideUrlLoading' in reels.read_text(), 'The embed player must never hand a link to a browser'
for required in ('getPackageArchiveInfo','apkContentsSigners','MessageDigest','getUriForFile','canRequestPackageInstalls','withContext(Dispatchers.IO)','TELEGRAM_CONFLICT','OPEN_READONLY'):
    assert required in code, required
assert 'installIntent' in code and 'currentCoroutineContext().ensureActive()' in code
assert 'passwordHash' in code and '210000' in code
print('Native migration and update source invariants passed (not a device test).')
