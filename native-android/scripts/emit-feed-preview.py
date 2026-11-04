"""Expose synthetic native UI proof for review over the GitHub checks API.
No credentials, live account screenshots or arbitrary files are ever included.
"""
import base64, pathlib, sys
path=pathlib.Path('native-android/native-feed-ui.png')
if not path.exists():
    print('::notice title=Nabd feed preview::No completed native feed capture is available in this run.')
    sys.exit(0)
image=path.read_bytes()
assert image.startswith(b'\x89PNG\r\n\x1a\n')
if len(image)<=250000:
    encoded=base64.b64encode(image).decode('ascii')
    chunks=[encoded[i:i+40000] for i in range(0,len(encoded),40000)]
    for i,chunk in enumerate(chunks,1):
        print(f'::notice title=Nabd synthetic feed PNG {i}/{len(chunks)}::{chunk}')
else:
    print('::notice title=Nabd feed preview::Full-size screenshot is available in the device artifact.')
