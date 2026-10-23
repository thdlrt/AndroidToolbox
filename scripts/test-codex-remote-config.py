"""Windows host config migration; only temporary fixture directories are touched."""
import json
import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]

class HostConfigurationTest(unittest.TestCase):
    def test_existing_single_root_can_switch_to_all_drives_and_back(self):
        with tempfile.TemporaryDirectory(prefix='remote-config-',dir=ROOT/'.build') as folder:
            root=pathlib.Path(folder);allowed=root/'allowed workspace';allowed.mkdir()
            config=root/'remote.json';config.write_text(json.dumps({'port':65321,'listenHost':'127.0.0.1','workspaceRoot':'E:\\old','codexHome':'isolated-home'}),encoding='utf-8')
            def run(*args):
                result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'scripts/codex-remote.ps1'),'-Root',str(root),*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
                self.assertEqual(0,result.returncode,result.stderr.decode(errors='replace'))
                return result.stdout.decode('utf-8-sig')
            run('-Action','Configure','-AllDrives')
            saved=json.loads(config.read_text(encoding='utf-8'))
            self.assertTrue(saved['allDrives']);self.assertNotIn('workspaceRoot',saved)
            self.assertEqual(65321,saved['port']);self.assertEqual('isolated-home',saved['codexHome'])
            self.assertTrue((root/'codex-remote.ps1').exists())
            status=json.loads(run('-Action','Status'));self.assertTrue(status['allDrives']);self.assertIn('C:\\',status['workspaces'])
            run('-Action','Configure','-WorkspaceRoot',str(allowed))
            saved=json.loads(config.read_text(encoding='utf-8'));self.assertFalse(saved['allDrives']);self.assertEqual([str(allowed)],saved['workspaceRoots'])
            self.assertEqual([str(allowed)],json.loads(run('-Action','Status'))['workspaces'])

if __name__=='__main__':unittest.main()
