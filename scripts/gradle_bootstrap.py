#!/usr/bin/env python3
"""OTE's explicit Gradle bootstrap, not an official Gradle Wrapper binary.

Requires Python 3.9+, a JDK 17, and network access on the build machine.
Downloads Gradle 8.11.1 over official HTTPS, verifies SHA-256, and safely
extracts it into the project-local .tooling directory. No Android licenses
are accepted, SDK packages installed, signing keys read, or APKs published.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

VERSION = "8.11.1"
ROOT = Path(__file__).resolve().parents[1]
URL = f"https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip"
MAX_DOWNLOAD = 350 * 1024 * 1024
MAX_EXPANDED = 1024 * 1024 * 1024


def require_jdk17() -> dict[str, str]:
    env = os.environ.copy()
    suffix = ".exe" if os.name == "nt" else ""
    java_home = env.get("JAVA_HOME")
    if java_home:
        java = Path(java_home) / "bin" / ("java" + suffix)
        javac = Path(java_home) / "bin" / ("javac" + suffix)
    else:
        java = Path(shutil.which("java") or "__missing_java__")
        javac = Path(shutil.which("javac") or "__missing_javac__")
    if not java.is_file() or not javac.is_file():
        raise RuntimeError("A complete JDK 17 is required. Set JAVA_HOME to it; a JRE is insufficient.")
    for executable, args in [(java, ["-version"]), (javac, ["-version"])]:
        result = subprocess.run([str(executable), *args], capture_output=True, text=True, timeout=15)
        output = result.stdout + result.stderr
        if result.returncode or not re.search(r'(?:version\s+"|javac\s+)17(?:\.|["\s])', output):
            raise RuntimeError("The selected java and javac must both be version 17. See docs/BUILD.md.")
    if not java_home:
        env["JAVA_HOME"] = str(java.resolve().parent.parent)
    return env


def expected_sha256() -> str:
    explicit = os.environ.get("OTE_GRADLE_SHA256")
    if explicit:
        if not re.fullmatch(r"[0-9a-fA-F]{64}", explicit):
            raise RuntimeError("OTE_GRADLE_SHA256 must contain exactly 64 hexadecimal characters.")
        return explicit.lower()
    # Same official distribution service, HTTPS certificate verification stays on.
    with urllib.request.urlopen(URL + ".sha256", timeout=30) as response:
        text = response.read(4097)
    if len(text) > 4096:
        raise RuntimeError("Unexpected Gradle checksum response size.")
    checksum = text.decode("ascii").strip()
    if not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
        raise RuntimeError("Official Gradle SHA-256 response is not a valid digest.")
    return checksum.lower()


def download(destination: Path, expected: str) -> None:
    digest = hashlib.sha256()
    total = 0
    with urllib.request.urlopen(URL, timeout=60) as response, destination.open("xb") as output:
        while True:
            block = response.read(1024 * 1024)
            if not block:
                break
            total += len(block)
            if total > MAX_DOWNLOAD:
                raise RuntimeError("Gradle download exceeds the configured size limit.")
            digest.update(block)
            output.write(block)
        output.flush()
        os.fsync(output.fileno())
    if digest.hexdigest() != expected:
        raise RuntimeError("Gradle SHA-256 verification failed; downloaded content will not execute.")


def extract_verified(archive: Path, destination: Path) -> None:
    """Extract into a newly owned empty directory, refusing unsafe ZIP entries."""
    prefix = f"gradle-{VERSION}"
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        if len(entries) > 20000 or sum(e.file_size for e in entries) > MAX_EXPANDED:
            raise RuntimeError("Gradle archive exceeds extraction limits.")
        names: set[str] = set()
        for entry in entries:
            raw = entry.filename
            path = PurePosixPath(raw)
            mode = entry.external_attr >> 16
            if (not raw or "\\" in raw or "\0" in raw or ":" in raw
                    or path.is_absolute() or ".." in path.parts
                    or not path.parts or path.parts[0] != prefix
                    or stat.S_ISLNK(mode) or raw in names):
                raise RuntimeError("Unsafe or duplicate entry in Gradle archive.")
            names.add(raw)
        # No unvalidated entry has been written at this point.
        for entry in entries:
            target = destination.joinpath(*PurePosixPath(entry.filename).parts)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with z.open(entry) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output, 1024 * 1024)
        launcher = destination / prefix / "bin" / "gradle"
        if not launcher.is_file():
            raise RuntimeError("Gradle launcher was not found in the verified distribution.")
        if os.name != "nt":
            launcher.chmod(0o755)


def gradle_installation() -> Path:
    cache = ROOT / ".tooling" / "gradle"
    installed = cache / f"gradle-{VERSION}"
    marker = cache / f"gradle-{VERSION}.verified.json"
    launcher = installed / "bin" / ("gradle.bat" if os.name == "nt" else "gradle")
    if marker.is_file() and launcher.is_file():
        record = json.loads(marker.read_text(encoding="utf-8"))
        if record.get("version") == VERSION and re.fullmatch(r"[0-9a-f]{64}", record.get("sha256", "")):
            return launcher
    cache.mkdir(parents=True, exist_ok=True)
    if installed.exists():
        raise RuntimeError("An unverified Gradle directory already exists. Inspect .tooling/gradle before retrying.")
    checksum = expected_sha256()
    print(f"Downloading verified Gradle {VERSION} from the official distribution service…", flush=True)
    # TemporaryDirectory only cleans the directory created by this invocation.
    with tempfile.TemporaryDirectory(prefix="ote-gradle-", dir=cache) as scratch:
        temporary = Path(scratch)
        archive = temporary / "distribution.zip"
        download(archive, checksum)
        extraction = temporary / "extract"
        extraction.mkdir()
        extract_verified(archive, extraction)
        (extraction / f"gradle-{VERSION}").rename(installed)
    marker.write_text(json.dumps({"version": VERSION, "sha256": checksum}) + "\n", encoding="utf-8")
    return launcher


def main() -> int:
    environment = require_jdk17()
    launcher = gradle_installation()
    command = [str(launcher), *sys.argv[1:]]
    if os.name == "nt":
        # A trusted .bat launcher is required on Windows, with argument quoting
        # delegated to subprocess rather than interpolating shell command text.
        command = [environment.get("COMSPEC", "cmd.exe"), "/c", str(launcher), *sys.argv[1:]]
    return subprocess.call(command, cwd=ROOT, env=environment)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
        print(f"OTE build bootstrap: {error}", file=sys.stderr)
        raise SystemExit(1)
