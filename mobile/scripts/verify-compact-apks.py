"""Fail closed on ABI, size, shared code, signature or actual-content differences."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

BASELINE_SHA256 = 'c4eb074807c33e3ea5ae294dc19a3e625cf8038c0d3f070140941bf4b61e20bf'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def shared_code(apk):
    with zipfile.ZipFile(apk) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist()
                if name.endswith('.dex') or name.startswith('assets/')}


def signing_certificate(apk):
    tools = sorted((Path(os.environ['ANDROID_HOME']) / 'build-tools').glob('*/apksigner'))
    assert tools, 'APK signature inspector missing'
    output = subprocess.run([str(tools[-1]), 'verify', '--verbose', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True).stdout
    certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', output)
    assert len(certs) == 1, 'Expected one verified APK signing certificate'
    return certs[0].lower()


def main(directory, baseline):
    directory, baseline = Path(directory), Path(baseline)
    assert sha(baseline) == BASELINE_SHA256, 'Unexpected size/signing baseline APK'
    temporary = Path(os.environ['RUNNER_TEMP'])
    probe = json.loads((temporary / 'nabd-apk-proof.json').read_text())
    emulator = directory / 'app-x86_64-release.apk'
    assert probe['androidEmulatorNativeSelfTest'] and probe['architecture'] == 'x86_64', 'Real emulator native test is required'
    assert probe['apkSha256'] == sha(emulator), 'Emulator proof does not describe this build'
    assert probe['runtime'] == os.environ['EXPECTED_APK_RUNTIME'], 'Wrong tested runtime'
    assert probe['source'] == os.environ['APK_SOURCE_SHA'], 'Wrong tested source'
    code = shared_code(emulator)
    certificate = signing_certificate(baseline)
    assert signing_certificate(emulator) == certificate, 'Emulator signing certificate changed'
    phones = {}
    for abi in ('arm64-v8a', 'armeabi-v7a'):
        apk = directory / f'app-{abi}-release.apk'
        assert apk.stat().st_size < baseline.stat().st_size * 0.5, 'Compact phone APK did not reduce download size by at least 50 percent'
        assert shared_code(apk) == code, 'Phone APK code/assets differ from emulator-tested payload'
        assert signing_certificate(apk) == certificate, 'Upgrade signing certificate changed; refusing to risk user data'
        proof_file = temporary / f'nabd-{abi}-proof.json'
        env = {**os.environ, 'EXPECTED_APK_ABI': abi, 'NABD_EMULATOR_SELF_TEST_PASSED': 'false'}
        subprocess.run([sys.executable, str(Path(__file__).with_name('verify-apk-content.py')), str(apk), str(proof_file)],
                       env=env, check=True)
        phone = json.loads(proof_file.read_text())
        phone.update(sizeBytes=apk.stat().st_size, reductionPercent=round(100 * (1 - apk.stat().st_size / baseline.stat().st_size), 1),
                     sharedCodeAndAssetsMatchEmulator=True, upgradeSigningCertificateMatches=True)
        phones[abi] = phone
        print(f'::notice title=Compact APK size::{abi}: {phone["sizeBytes"]} bytes, {phone["reductionPercent"]}% smaller')
    proof = dict(source=probe['source'], runtime=probe['runtime'], baselineSha256=BASELINE_SHA256,
                 baselineSizeBytes=baseline.stat().st_size, emulator=probe, phoneVariants=phones,
                 physicalPhoneEndToEndTested=False)
    (temporary / 'nabd-compact-proof.json').write_text(json.dumps(proof, indent=2))


if __name__ == '__main__':
    try:
        main(*sys.argv[1:])
    except Exception as error:
        # Only our fixed assertion reasons. Never forward arbitrary SDK/app logs.
        reason = str(error) if isinstance(error, AssertionError) else type(error).__name__
        print('::error title=Compact APK gate::' + reason, flush=True)
        sys.exit(1)
