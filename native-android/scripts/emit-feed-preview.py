"""Expose synthetic native UI proof for review over the GitHub checks API.

Only the explicitly synthetic feed fixture is ever emitted: no live account,
no credentials and no arbitrary file from the workspace. GitHub truncates
check annotation messages, so the preview is downscaled to a compact JPEG and
split into small chunks that survive that limit.
"""
import base64, pathlib, sys

SOURCE = pathlib.Path('native-android/native-feed-ui.png')
MAX_CHUNKS = 8
CHUNK = 3600


def shrink(image):
    """Return compact JPEG bytes fitting MAX_CHUNKS annotation messages."""
    from PIL import Image
    budget = MAX_CHUNKS * CHUNK
    for width, quality in ((380, 60), (320, 55), (260, 50), (200, 45)):
        ratio = width / image.width
        scaled = image.convert('RGB').resize((width, max(1, round(image.height * ratio))))
        out = pathlib.Path('native-android/device/feed-preview.jpg')
        out.parent.mkdir(exist_ok=True)
        scaled.save(out, format='JPEG', quality=quality, optimize=True)
        encoded = base64.b64encode(out.read_bytes()).decode('ascii')
        if len(encoded) <= budget:
            return encoded
    return None


def main():
    if not SOURCE.exists():
        print('::notice title=Nabd feed preview::No completed native feed capture is available in this run.')
        return 0
    try:
        from PIL import Image
        image = Image.open(SOURCE)
        image.load()
    except Exception:
        print('::notice title=Nabd feed preview::Preview encoder unavailable; the full PNG is in the device artifact.')
        return 0
    encoded = shrink(image)
    if encoded is None:
        print('::notice title=Nabd feed preview::Preview too large for check notices; the full PNG is in the device artifact.')
        return 0
    chunks = [encoded[i:i + CHUNK] for i in range(0, len(encoded), CHUNK)]
    print(f'::notice title=Nabd feed preview::Synthetic feed {image.width}x{image.height}, {len(chunks)} preview chunk(s), JPEG preview.')
    for i, chunk in enumerate(chunks, 1):
        print(f'::notice title=Nabd synthetic feed preview {i}/{len(chunks)}::{chunk}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
