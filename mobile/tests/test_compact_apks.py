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

class CertificateTests(unittest.TestCase):
    def test_new_sdk_ranges_and_duplicate_same_certificate_are_accepted(self):
        cert = 'a' * 64
        output = f'V2 Signer: certificate SHA-256 digest: {cert}\nV3.1 Signer: certificate SHA-256 digest: {cert}\nSigner #1 certificate SHA-256 digest: {cert}\nSigner (minSdkVersion=33, maxSdkVersion=2147483647) #1 certificate SHA-256 digest: {cert}\n'
        self.assertEqual(compact.certificate_digest(output), cert)

    def test_distinct_signers_and_missing_digest_fail_closed(self):
        for output in ('nothing', 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64 + '\nSigner #2 certificate SHA-256 digest: ' + 'b' * 64):
            with self.assertRaises(AssertionError):
                compact.certificate_digest(output)

class ReuseTests(unittest.TestCase):
    def test_reuse_fails_closed_on_any_source_runtime_config_or_abi_difference(self):
        import json
        import os
        import subprocess
        script = Path(__file__).resolve().parents[1] / 'scripts/reuse-compact-apks.py'
        for scenario in ('same', 'source', 'runtime', 'config', 'abi'):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                folder = root / 'mobile/android/app/build/outputs/apk/release'
                folder.mkdir(parents=True)
                (root / 'nabd-compact-diff.txt').write_text('mobile/app.json' if scenario == 'source' else 'mobile/scripts/example.py')
                (root / 'nabd-runtime.json').write_text(json.dumps({'runtime': 'a' * 40}))
                for abi in ('arm64-v8a', 'armeabi-v7a', 'x86_64'):
                    with zipfile.ZipFile(folder / f'app-{abi}-release.apk', 'w') as z:
                        z.writestr('assets/fingerprint', ('b' if scenario == 'runtime' else 'a') * 40)
                        z.writestr('assets/index.android.bundle', 'synthetic_fixture -123 https://fixture.test/graphql')
                        z.writestr(f'lib/{"wrong" if scenario == "abi" else abi}/lib.so', b'fixture')
                env = {**os.environ, 'RUNNER_TEMP': str(root), 'GITHUB_OUTPUT': str(root / 'output'), 'GITHUB_SHA': 'c' * 40,
                       'EXPO_PUBLIC_DATA_SOURCE': 'telegram', 'EXPO_PUBLIC_TELEGRAM_BOT_TOKEN': 'other' if scenario == 'config' else 'synthetic_fixture',
                       'EXPO_PUBLIC_TELEGRAM_CHAT_ID': '-123', 'EXPO_PUBLIC_API_URL': 'https://fixture.test/graphql'}
                result = subprocess.run(['/usr/bin/python3', str(script)], env=env, cwd=root, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0)
                self.assertIn('reuse=' + str(scenario == 'same').lower(), (root / 'output').read_text())
                self.assertEqual(len(list(folder.glob('*.apk'))), 3 if scenario == 'same' else 0)
                self.assertNotIn('synthetic_fixture', result.stdout)


if __name__ == '__main__':
    unittest.main()
