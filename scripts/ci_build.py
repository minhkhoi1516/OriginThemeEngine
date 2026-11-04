#!/usr/bin/env python3
"""Build OTE on an existing Android CI host; never connects to a phone.

Logs and a machine-readable outcome are retained even on failure. Linux-only
shell fixtures are explicitly skipped on macOS; they are not reported as passes.
This installs missing SDK packages but does not accept new SDK licenses, sign a
production release, upload artifacts, print environment values, or request SU.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time

import gradle_bootstrap

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "build/ci"


class Build:
    def __init__(self):
        self.env = os.environ.copy()
        self.results = []
        self.failed = False
        self.finished = False
        self.apk = None
        REPORTS.mkdir(parents=True, exist_ok=True)

    def record(self, name, status, **details):
        self.results.append(dict(name=name, status=status, **details))
        self.failed |= status == "failed"
        (REPORTS / "outcome.json").write_text(json.dumps({
            "status": "failed" if self.failed else "in_progress",
            "host": platform.system(),
            "steps": self.results,
            "apk": self.apk,
            "android_device_tested": False,
            "production_signing": False,
        }, indent=2) + "\n", encoding="utf-8")

    def run(self, name, command):
        print(f"\n--- {name} ---", flush=True)
        started = time.monotonic()
        with (REPORTS / f"{name}.log").open("w", encoding="utf-8") as log:
            with subprocess.Popen(command, cwd=ROOT, env=self.env,
                                  stdin=subprocess.DEVNULL,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", errors="replace") as process:
                for line in process.stdout:
                    log.write(line)
                    log.flush()
                    print(line, end="", flush=True)
                code = process.wait()
        self.record(name, "passed" if code == 0 else "failed", exit_code=code,
                    seconds=round(time.monotonic() - started, 2))
        return code == 0

    def execute(self):
        try:
            self.run("source-checks", [sys.executable, "scripts/validate_project.py"])
            self.run("handoff-checks", [sys.executable, "scripts/validate_handoff.py"])
            self.run("bootstrap-tests", [sys.executable, "scripts/test_gradle_bootstrap.py"])
            if platform.system() == "Linux" and Path("/proc/self/fd").is_dir():
                self.run("transaction-fixtures", [sys.executable, "scripts/test_device_transaction.py"])
                self.run("recovery-fixtures", [sys.executable, "scripts/test_device_recovery.py"])
                self.run("catalog-fixtures", [sys.executable, "scripts/test_catalog_discovery.py"])
            else:
                self.record("transaction-fixtures", "skipped",
                            reason="Requires Linux /proc/self/fd and GNU tools; not an Android device test.")
                self.record("recovery-fixtures", "skipped", reason="Requires the Linux transaction fixture.")
                self.record("catalog-fixtures", "skipped", reason="Requires GNU stat/readlink host fixtures.")

            self.env = gradle_bootstrap.require_jdk17()
            sdk_value = self.env.get("ANDROID_HOME") or self.env.get("ANDROID_SDK_ROOT")
            if not sdk_value or not Path(sdk_value).is_dir():
                raise RuntimeError("Set ANDROID_HOME to the CI host's installed Android SDK.")
            sdk = Path(sdk_value).resolve()
            self.env["ANDROID_HOME"] = self.env["ANDROID_SDK_ROOT"] = str(sdk)
            manager = shutil.which("sdkmanager")
            if not manager:
                candidate = sdk / "cmdline-tools/latest/bin/sdkmanager"
                if candidate.is_file():
                    manager = str(candidate)
            if not manager:
                raise RuntimeError("The CI host must provide Android command-line tools (sdkmanager).")
            # AGP 8.10 defaults to Build Tools 35; 36 supplies the inspected signing tool.
            # stdin is closed: an unaccepted SDK license fails instead of being accepted silently.
            if not self.run("android-sdk", [manager, f"--sdk_root={sdk}",
                                            "platforms;android-36", "build-tools;35.0.0", "build-tools;36.0.0"]):
                return 1
            gradle = [sys.executable, "scripts/gradle_bootstrap.py"]
            options = ["--no-daemon", "--stacktrace", "--console=plain"]
            if not self.run("assemble-debug", gradle + [":app:assembleDebug"] + options):
                return 1
            apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
            if not apk.is_file():
                raise RuntimeError("Gradle returned successfully but app-debug.apk is absent.")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            self.apk = {"path": str(apk.relative_to(ROOT)), "sha256": digest, "bytes": apk.stat().st_size,
                        "signing": "temporary CI debug identity; not a stable update key"}
            (REPORTS / "app-debug.apk.sha256").write_text(f"{digest}  app-debug.apk\n", encoding="utf-8")
            signing = sdk / "build-tools/36.0.0"
            self.run("apk-signature", [str(signing / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)])
            # Public tool only, for later owner-private signing. Never retain any keystore.
            shutil.copyfile(signing / "lib/apksigner.jar", REPORTS / "apksigner.jar")
            self.run("unit-tests-and-lint", gradle + [
                ":core:root:test", ":engine:pack:test", ":engine:png:test", ":data:testDebugUnitTest",
                ":engine:device:testDebugUnitTest", ":app:testDebugUnitTest", ":app:lintDebug",
            ] + options + ["--continue"])
            self.finished = True
            return 1 if self.failed else 0
        except (OSError, RuntimeError, ValueError) as error:
            # Do not dump the environment or credentials in a failure report.
            print(f"OTE CI stopped: {error}", file=sys.stderr, flush=True)
            self.record("build-environment", "failed", error=str(error))
            return 1
        finally:
            path = REPORTS / "outcome.json"
            if path.is_file():
                outcome = json.loads(path.read_text(encoding="utf-8"))
                outcome["status"] = "failed" if self.failed else "completed" if self.finished else "interrupted"
                outcome["apk"] = self.apk
                path.write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
            print("Build details: build/ci/outcome.json. Device validation is still required.", flush=True)


if __name__ == "__main__":
    raise SystemExit(Build().execute())
