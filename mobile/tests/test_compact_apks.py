import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('compact', Path(__file__).resolve().parents[1] / 'scripts/verify-compact-apks.py')
compact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compact)


class CompactTests(unittest.TestCase):
    def test_dex_and_all_assets_must_match_across_architectures(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ('x86.apk', 'arm.apk', 'bad.apk')]
            for index, path in enumerate(paths):
                with zipfile.ZipFile(path, 'w') as z:
                    z.writestr('classes.dex', b'same dex')
                    z.writestr('assets/fingerprint', b'same runtime')
                    z.writestr('assets/index.android.bundle', b'changed code' if index == 2 else b'same code')
                    z.writestr(f'lib/{index}/lib.so', b'ABI-specific bytes')
            self.assertEqual(compact.shared_code(paths[0]), compact.shared_code(paths[1]))
            self.assertNotEqual(compact.shared_code(paths[0]), compact.shared_code(paths[2]))


if __name__ == '__main__':
    unittest.main()

class CompressionTests(unittest.TestCase):
    def test_recompression_preserves_payload_storage_and_service_entries(self):
        spec = importlib.util.spec_from_file_location('compress', Path(__file__).resolve().parents[1] / 'scripts/compress-apks.py')
        compress = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(compress)
        with tempfile.TemporaryDirectory() as directory:
            source, dest = Path(directory) / 'in.apk', Path(directory) / 'out.apk'
            with zipfile.ZipFile(source, 'w') as z:
                z.writestr('resources.arsc', b'resources', compress_type=zipfile.ZIP_STORED)
                z.writestr('lib/arm64-v8a/lib.so', b'native code' * 1000, compress_type=zipfile.ZIP_DEFLATED)
                z.writestr('META-INF/services/provider', b'keep service')
                z.writestr('META-INF/CERT.RSA', b'old signature')
                z.writestr('META-INF/MANIFEST.MF', b'old manifest')
            compress.recompress(source, dest)
            with zipfile.ZipFile(dest) as z:
                self.assertEqual(z.read('lib/arm64-v8a/lib.so'), b'native code' * 1000)
                self.assertEqual(z.getinfo('lib/arm64-v8a/lib.so').compress_type, zipfile.ZIP_DEFLATED)
                self.assertEqual(z.getinfo('resources.arsc').compress_type, zipfile.ZIP_STORED)
                self.assertEqual(z.read('META-INF/services/provider'), b'keep service')
                self.assertNotIn('META-INF/CERT.RSA', z.namelist())
                self.assertNotIn('META-INF/MANIFEST.MF', z.namelist())
