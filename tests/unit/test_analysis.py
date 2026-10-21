import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('analysis', Path(__file__).parents[2]/'scripts/analyze_apks.py')
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)

class StaticAnalysisTests(unittest.TestCase):
    def test_archive_inventory_without_execution(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder)/'synthetic.zip'
            with zipfile.ZipFile(apk, 'w') as z:
                z.writestr('classes.dex', b'fixture-not-a-real-dex')
                z.writestr('lib/arm64-v8a/libfixture.so', b'fixture-not-executable')
                z.writestr('assets/test.json', '{}')
            result = analysis.inventory(apk)
            self.assertEqual(result['dex'], ['classes.dex'])
            self.assertEqual(len(result['native_libraries']), 1)
            self.assertEqual(result['native_libraries'][0]['sha256'], __import__('hashlib').sha256(b'fixture-not-executable').hexdigest())
    def test_manifest_privilege_and_components(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder)/'manifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.fixture" android:sharedUserId="android.uid.system"><uses-sdk android:minSdkVersion="29"/><uses-permission android:name="android.permission.BLUETOOTH"/><application><service android:name=".LocalService" android:exported="false"/></application></manifest>')
            result = analysis.manifest_metadata(manifest)
            self.assertEqual(result['sharedUserId'], 'android.uid.system')
            self.assertEqual(len(result['components']['service']), 1)
            self.assertEqual(result['sdk'][analysis.ANDROID+'minSdkVersion'], '29')
    def test_malformed_archive_fails_explicitly(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder)/'bad.apk'; apk.write_bytes(b'not a zip')
            with self.assertRaises(zipfile.BadZipFile): analysis.inventory(apk)

if __name__ == '__main__': unittest.main()
