"""Read-only ADB evidence for the two project-related apps, stored locally in private/.

Does not launch apps, clear logs, enable Bluetooth/HCI, change permissions or connect
to a receiver. Prints fixed diagnostic labels only, not raw logs or identifiers.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = ('com.samsung.android.carlink', 'org.aionlink.open')
WATCHED_PERMISSIONS = (
    'android.permission.BLUETOOTH_SCAN', 'android.permission.BLUETOOTH_CONNECT',
    'android.permission.NEARBY_WIFI_DEVICES', 'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.MANAGE_ACTIVITY_TASKS', 'android.permission.MANAGE_USB',
    'android.permission.MODIFY_AUDIO_ROUTING', 'android.permission.INJECT_EVENTS')

def classify_logs(log):
    # Labels are factual text matches; no assertion of causal attribution/version.
    checks = {
        'missing_samsung_overlay_method': 'NoSuchMethodError: No virtual method semCheckOpSystemAlertWindow',
        'privileged_task_permission_denied': 'requires android.permission.MANAGE_ACTIVITY_TASKS',
        'samsung_restriction_permission_denied': 'requires com.samsung.android.permission.SEM_APP_RESTRICTION',
        'iccoa_services_reported_open': 'onOpenResult: true',
        'iccoa_ble_scan_started': 'ICCOA-BLEService: Starting scan',
        'fatal_exception_present': 'FATAL EXCEPTION',
    }
    return {name: log.count(text) for name, text in checks.items()}

def permission_summary(package_dump):
    # Do not include package queries, other installed apps or data-directory paths.
    result = {}
    for name in WATCHED_PERMISSIONS:
        match = re.search(r'^\s*'+re.escape(name)+r': granted=(true|false)', package_dump, re.M)
        result[name] = match.group(1) == 'true' if match else 'not_listed_as_granted'
    return result

def capture(serial, destination):
    def adb(*args):
        proc = subprocess.run(['adb','-s',serial,*args],capture_output=True,timeout=60)
        if proc.returncode:
            raise RuntimeError('ADB read failed; reconnect and authorize the selected phone')
        return proc.stdout.decode('utf-8',errors='replace')
    summary = {'captured_utc': datetime.now(timezone.utc).isoformat(),
               'sdk': adb('shell','getprop','ro.build.version.sdk').strip(), 'apps': {}}
    for package in PACKAGES:
        uid_output = adb('shell','cmd','package','list','packages','-U',package)
        match = re.search(r'^package:'+re.escape(package)+r' uid:(\d+)\s*$',uid_output,re.M)
        if not match:
            summary['apps'][package] = {'installed':False}; continue
        uid = match.group(1)
        # No -t global tail: it can discard the app's older entries before UID filtering.
        log = adb('logcat','-d','--uid='+uid,'-b','main','-b','system','-b','crash','-v','threadtime')
        path = destination/(package+'.log')
        path.write_text(log,encoding='utf-8')
        dump = adb('shell','dumpsys','package',package)
        version = re.search(r'^\s*versionName=(\S+)',dump,re.M)
        info = {'installed':True,'versionName':version.group(1) if version else None,
                'log_lines':len(log.splitlines()),'log_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                'observations':classify_logs(log),'permissions':permission_summary(dump)}
        summary['apps'][package] = info
    (destination/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    return summary

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True,help='Owner-authorized phone from adb devices -l')
    args = parser.parse_args()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    destination = ROOT/'private'/('phone-evidence-'+stamp)
    destination.mkdir(parents=True)
    summary = capture(args.serial,destination)
    print(json.dumps(summary,ensure_ascii=False,indent=2))
    print('Private local evidence:',destination.relative_to(ROOT))
