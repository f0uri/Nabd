"""Offline launcher control-flow tests, not Android/native verification."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/run-native-emulator.sh'


class LauncherTests(unittest.TestCase):
    def test_verbose_discovery_does_not_sigpipe_or_skip_probe(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk = root / 'sdk'

            def executable(name, text):
                path = sdk / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('#!/bin/bash\nset -eu\n' + text)
                path.chmod(0o755)

            executable('cmdline-tools/latest/bin/sdkmanager', 'exit 0\n')
            executable('cmdline-tools/latest/bin/avdmanager', '''
while [ "$#" -gt 0 ]; do
  if [ "$1" = --path ]; then mkdir -p "$2"; echo config > "$2/config.ini"; break; fi
  shift
done
''')
            # Large trailing output reproduces grep -q closing the pipe early.
            executable('emulator/emulator', '''
if [ "$1" = -list-avds ]; then
  test -s "$ANDROID_AVD_HOME/nabd-ci.ini"
  echo nabd-ci
  python3 -c 'print("INFO " * 100000)'
else
  touch "$RUNNER_TEMP/emulator-launched"
fi
''')
            executable('platform-tools/adb', 'echo 1\n')
            # Intercept only the smoke subprocess. The real gate is untouched.
            executable('platform-tools/bash', '''
test "$1" = mobile/scripts/smoke-android-apk.sh
test "$2" = fixture.apk
touch "$RUNNER_TEMP/probe-invoked"
''')
            env = {**os.environ, 'ANDROID_HOME': str(sdk), 'RUNNER_TEMP': str(root)}
            result = subprocess.run(['/bin/bash', str(SCRIPT), 'fixture.apk'],
                                    env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue((root / 'probe-invoked').exists())


if __name__ == '__main__':
    unittest.main()
