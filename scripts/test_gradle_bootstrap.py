"""Offline extraction/checksum guard tests, not Android/Gradle compilation."""
import io
from pathlib import Path
import stat
import tempfile
import unittest
from unittest import mock
import zipfile

import gradle_bootstrap as bootstrap


class BootstrapSafetyTests(unittest.TestCase):
    def run_extract(self, entries):
        with tempfile.TemporaryDirectory(prefix="ote-bootstrap-test-") as tmp:
            root = Path(tmp)
            archive = root / "archive.zip"
            with zipfile.ZipFile(archive, "w") as z:
                for name, content in entries:
                    z.writestr(name, content)
            dest = root / "out"
            dest.mkdir()
            bootstrap.extract_verified(archive, dest)
            return (dest / f"gradle-{bootstrap.VERSION}" / "bin" / "gradle").read_text()

    def test_valid_minimal_launcher(self):
        self.assertEqual("fixture", self.run_extract([(f"gradle-{bootstrap.VERSION}/bin/gradle", "fixture")]))

    def test_traversal_fails_before_writes(self):
        with self.assertRaisesRegex(RuntimeError, "Unsafe"):
            self.run_extract([(f"gradle-{bootstrap.VERSION}/../escape", "bad")])

    def test_drive_path_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "Unsafe"):
            self.run_extract([("C:/escape", "bad")])

    def test_unix_symlink_rejected(self):
        link = zipfile.ZipInfo(f"gradle-{bootstrap.VERSION}/bin/link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaisesRegex(RuntimeError, "Unsafe"):
            self.run_extract([(link, "../../escape")])

    def test_expanded_size_cap(self):
        with mock.patch.object(bootstrap, "MAX_EXPANDED", 1):
            with self.assertRaisesRegex(RuntimeError, "limits"):
                self.run_extract([(f"gradle-{bootstrap.VERSION}/bin/gradle", "fixture")])

    def test_reviewed_checksum_override_validated(self):
        with mock.patch.dict(bootstrap.os.environ, {"OTE_GRADLE_SHA256": "not a digest"}):
            with self.assertRaisesRegex(RuntimeError, "64 hexadecimal"):
                bootstrap.expected_sha256()
        with mock.patch.dict(bootstrap.os.environ, {"OTE_GRADLE_SHA256": "A" * 64}):
            self.assertEqual("a" * 64, bootstrap.expected_sha256())


if __name__ == "__main__":
    unittest.main()
