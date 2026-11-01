"""Resource inspection tests; do not substitute for actual APK/native checks."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('apk_icons', Path(__file__).resolve().parents[1] / 'scripts/apk_icons.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
DUMP = '''
Package name=app.nabd.social id=7f
  type mipmap id=0x0f entryCount=2
    resource 0x7f0f0000 mipmap/ic_launcher
      (mdpi-v4) (file) res/a.webp type=WEBP
      (anydpi-v26) (file) res/b.xml type=XML
    resource 0x7f0f0001 app.nabd.social:mipmap/ic_launcher_foreground
      (mdpi-v4) (file) res/c.webp type=WEBP
    resource 0x7f100001 string/app_name
      () "Nabd"
'''


class IconTests(unittest.TestCase):
    def test_shortened_paths_resolve_by_resource_name(self):
        self.assertEqual(module.icon_paths(DUMP, {'res/a.webp', 'res/b.xml', 'res/c.webp'}),
                         (['res/a.webp', 'res/b.xml'], ['res/c.webp']))

    def test_missing_packaged_foreground_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'foreground'):
            module.icon_paths(DUMP, {'res/a.webp', 'res/b.xml'})

    def test_unrelated_images_do_not_count_as_launcher(self):
        with self.assertRaisesRegex(AssertionError, 'launcher'):
            module.icon_paths(DUMP.replace('mipmap/ic_launcher\n', 'drawable/other\n'),
                              {'res/a.webp', 'res/b.xml', 'res/c.webp'})


if __name__ == '__main__':
    unittest.main()
