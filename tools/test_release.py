import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.version = self.root / 'version.properties'
        self.original = 'versionCode=1\nversionName=1.0.0\n'
        self.version.write_text(self.original)
        (self.root / 'web').mkdir()
        output = self.root / 'app/build/outputs/apk/release'
        output.mkdir(parents=True)
        (output / 'app-release.apk').write_bytes(b'test artifact')
        (output / 'output-metadata.json').write_text(json.dumps({'elements': [{'outputFile': 'app-release.apk'}]}))
        self.commands = []
        self.args = argparse.Namespace(version='1.0.1', notes='Fixes for riders.')

    def tearDown(self):
        self.temp.cleanup()

    def run_command(self, *args, **kwargs):
        self.commands.append(args)
        if args == ('git', 'status', '--porcelain'):
            return ''
        if args[:3] == ('git', 'diff', '--name-only'):
            return 'version.properties'
        if args == ('git', 'rev-parse', 'HEAD'):
            return 'abc123'
        return ''

    def context(self):
        return patch.multiple(release, ROOT=self.root)

    def prepare(self):
        with self.context(), patch.object(release, 'repository', return_value='owner/repo'), \
                patch.object(release, 'run', side_effect=self.run_command), \
                patch.object(release, 'inspect_apk', return_value=(2, '1.0.1')):
            release.prepare(self.args)

    def test_prepare_has_no_network_or_live_manifest(self):
        self.prepare()
        self.assertIn('versionCode=2', self.version.read_text())
        manifest = json.loads((self.root / 'app/build/releases/1.0.1/update.json').read_text())
        self.assertEqual('https://github.com/owner/repo/releases/download/v1.0.1/pelonot-1.0.1.apk', manifest['url'])
        self.assertFalse((self.root / 'web/update.json').exists())
        self.assertFalse(any(cmd[0] == 'gh' for cmd in self.commands))

    def test_failed_signing_restores_version(self):
        with self.context(), patch.object(release, 'repository', return_value='owner/repo'), \
                patch.object(release, 'run', side_effect=self.run_command), \
                patch.object(release, 'inspect_apk', side_effect=ValueError('unsigned')):
            with self.assertRaises(ValueError):
                release.prepare(self.args)
        self.assertEqual(self.original, self.version.read_text())
        self.assertFalse((self.root / 'web/update.json').exists())

    def test_failed_upload_never_advertises_an_update(self):
        self.prepare()
        def fail_upload(*args, **kwargs):
            if args[:3] == ('gh', 'release', 'create'):
                raise subprocess.CalledProcessError(1, args)
            return self.run_command(*args, **kwargs)
        with self.context(), patch.object(release, 'repository', return_value='owner/repo'), \
                patch.object(release, 'run', side_effect=fail_upload), \
                patch.object(release, 'inspect_apk', return_value=(2, '1.0.1')):
            with self.assertRaises(subprocess.CalledProcessError):
                release.publish(self.args)
        self.assertFalse((self.root / 'web/update.json').exists())

    def test_changed_apk_is_refused_before_upload(self):
        self.prepare()
        (self.root / 'app/build/releases/1.0.1/pelonot-1.0.1.apk').write_bytes(b'changed')
        with self.context(), patch.object(release, 'repository', return_value='owner/repo'), \
                patch.object(release, 'run', side_effect=self.run_command), \
                patch.object(release, 'inspect_apk', return_value=(2, '1.0.1')):
            with self.assertRaises(ValueError):
                release.publish(self.args)
        self.assertFalse(any(cmd[0] == 'gh' for cmd in self.commands))

    def test_successful_upload_copies_exact_manifest(self):
        self.prepare()
        with self.context(), patch.object(release, 'repository', return_value='owner/repo'), \
                patch.object(release, 'run', side_effect=self.run_command), \
                patch.object(release, 'inspect_apk', return_value=(2, '1.0.1')):
            release.publish(self.args)
        self.assertEqual((self.root / 'app/build/releases/1.0.1/update.json').read_bytes(),
                         (self.root / 'web/update.json').read_bytes())
        self.assertTrue(any(cmd[:3] == ('gh', 'release', 'create') for cmd in self.commands))


if __name__ == '__main__':
    unittest.main()
