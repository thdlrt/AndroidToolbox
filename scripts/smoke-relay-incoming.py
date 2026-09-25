"""Cross-UID upload verification using only synthetic files and a chosen emulator.

Start relay-test-server.py and configure the app with its fixture connection first.
Build/install :relay-fixture:assembleDebug on the same emulator before this script.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--package', choices=['net.lanbridge.android', 'net.lanbridge.android.debug'], default='net.lanbridge.android')
args = parser.parse_args()
if not args.serial.startswith('emulator-'): raise SystemExit('Only an explicitly selected test emulator is allowed')
adb = str(Path(os.getenv('ANDROID_HOME', ROOT / '.build/sdk')) / 'platform-tools/adb.exe')
report = ROOT / '.build/relay-validation-state.json'
prefix = str(time.time_ns())


def run(*command):
    return subprocess.check_output([adb, '-s', args.serial, *command], text=True, encoding='utf-8', errors='replace')


def state(): return json.loads(report.read_text('utf-8'))


def send(mode, names):
    values = ['--esa', 'names', ','.join(names)] if mode == 'multi' else ['--es', 'name', names[0]]
    run('shell', 'am', 'start', '-f', '0x10008000', '-n', 'net.lanbridge.relayfixture/.FixtureActivity',
        '--es', 'mode', mode, *values, '--es', 'target', args.package)


def wait(names):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        data = state()
        if all(data['files'].get('/dav/inbox/' + name) == 'synthetic relay fixture: ' + name + '\n' for name in names): return
        time.sleep(.3)
    raise AssertionError('Missing or incorrect upload: ' + repr(names))


before = sum(row[0] == 'PUT' for row in state()['requests'])
run('shell', 'am', 'force-stop', args.package)
for mode, suffixes in [('view', ['cold']), ('send', ['share']), ('multi', ['one', 'two']), ('clip', ['clip'])]:
    names = [prefix + '-' + suffix + '.txt' for suffix in suffixes]
    send(mode, names); wait(names)
slow, queued = prefix + '-slow-queue.txt', prefix + '-queued.txt'
send('view', [slow]); time.sleep(.8); send('clip', [queued])
run('shell', 'input', 'keyevent', 'KEYCODE_HOME')
wait([slow, queued])
assert sum(row[0] == 'PUT' for row in state()['requests']) - before == 7, 'Duplicate or missing upload'
print('PASS: cold/warm VIEW, SEND, SEND_MULTIPLE, ClipData-only, deduplication, queued/background upload, exact bytes')
