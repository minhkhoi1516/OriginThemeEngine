"""Run OTE's actual discovery shell against temporary host fixtures, without SU.

This verifies enumeration/quoting/read-only behavior, not Android SELinux or root.
"""
import base64
import hashlib
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest

TEMPLATE = Path(__file__).resolve().parents[1] / 'core/root/src/main/resources/ote-catalog.sh'


class CatalogDiscoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ote-catalog-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.aod = self.root / "aod's samples"
        self.fp = self.root / 'fingerprint'
        self.aod.mkdir()
        self.fp.mkdir()

    def scan(self):
        script = TEMPLATE.read_text().replace('@AOD_ROOT@', shlex.quote(str(self.aod)))
        script = script.replace('@FP_ROOT@', shlex.quote(str(self.fp)))
        return subprocess.run(['/bin/sh', '-c', script], text=True, capture_output=True, timeout=10)

    @staticmethod
    def items(output):
        return [(parts[1], int(parts[2]), base64.b64decode(parts[3]).decode())
                for line in output.splitlines() if (parts := line.split('\t'))[0] == 'ITEM']

    def test_lists_unicode_and_shell_metacharacters_literally(self):
        path = self.aod / "动态 $HOME `x` ' sample.itz"
        path.write_bytes(b'fixture')
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([('AOD', 7, str(path))], self.items(result.stdout))

    def test_limits_discovery_to_observed_layout(self):
        (self.aod / 'not-a-theme.zip').write_bytes(b'x')
        nested = self.aod / 'nested'; nested.mkdir()
        (nested / 'ignored.itz').write_bytes(b'x')
        content = self.fp / '进化' / 'content'; content.mkdir(parents=True)
        (content / 'animation.zip').write_bytes(b'example')
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([('FINGERPRINT', 7, str(content / 'animation.zip'))], self.items(result.stdout))

    def test_skips_symlink_files_and_parent_directories(self):
        target = self.root / 'outside'; target.mkdir()
        (target / 'animation.zip').write_bytes(b'x')
        (self.aod / 'link.itz').symlink_to(target / 'animation.zip')
        (self.fp / 'style').mkdir()
        (self.fp / 'style' / 'content').symlink_to(target, target_is_directory=True)
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.items(result.stdout))

    def test_rejects_symlink_scan_root(self):
        self.aod.rmdir()
        self.aod.symlink_to(self.fp, target_is_directory=True)
        result = self.scan()
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('END\t', result.stdout)

    def test_distinguishes_unavailable_root_from_complete_scan(self):
        self.aod.rmdir()
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('ROOT\tAOD\tUNAVAILABLE\n', result.stdout)
        self.assertTrue(result.stdout.endswith('END\t0\t0\n'))

    def test_limit_is_explicit(self):
        for number in range(100):
            (self.aod / f'{number:03}.itz').write_bytes(b'x')
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(96, len(self.items(result.stdout)))
        self.assertTrue(result.stdout.endswith('END\t96\t1\n'))

    def test_discovery_preserves_bytes_mode_and_mtime(self):
        path = self.aod / 'original.itz'; path.write_bytes(b'original resource')
        before = (hashlib.sha256(path.read_bytes()).digest(), path.stat().st_mode, path.stat().st_mtime_ns)
        result = self.scan()
        self.assertEqual(0, result.returncode, result.stderr)
        after = (hashlib.sha256(path.read_bytes()).digest(), path.stat().st_mode, path.stat().st_mtime_ns)
        self.assertEqual(before, after)
        self.assertEqual([path], list(self.aod.iterdir()))


if __name__ == '__main__':
    unittest.main()
