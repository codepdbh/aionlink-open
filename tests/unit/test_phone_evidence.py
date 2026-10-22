import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('evidence',Path(__file__).parents[2]/'scripts/collect_phone_evidence.py')
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)

class PhoneEvidenceTests(unittest.TestCase):
    def test_fixed_labels_do_not_include_log_contents(self):
        secret = 'private-token-DO-NOT-EXPORT'
        summary = evidence.classify_logs('NoSuchMethodError: No virtual method semCheckOpSystemAlertWindow '+secret)
        self.assertEqual(summary['missing_samsung_overlay_method'],1)
        self.assertNotIn(secret,str(summary))
    def test_permission_status_does_not_infer_missing_is_denied(self):
        result = evidence.permission_summary('  android.permission.BLUETOOTH_SCAN: granted=true\nQueries:\n private.app')
        self.assertIs(result['android.permission.BLUETOOTH_SCAN'],True)
        self.assertEqual(result['android.permission.MANAGE_USB'],'not_listed_as_granted')
        self.assertNotIn('private.app',str(result))
    def test_no_entries_does_not_imply_success(self):
        self.assertTrue(all(n == 0 for n in evidence.classify_logs('').values()))

if __name__ == '__main__': unittest.main()
