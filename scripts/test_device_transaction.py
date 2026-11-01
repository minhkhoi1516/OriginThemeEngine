#!/usr/bin/env python3
"""Execute the actual Kotlin fixed-transaction shell in a private POSIX fixture.

No Android device, SU, system files or production hashes are modified. UID,
SELinux, model and Settings observations are explicit host mocks. File hashing,
shell control flow, snapshots, ZIP readers and same-filesystem rename are real.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import textwrap
import zipfile


PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "engine/device/src/main/kotlin/dev/origin/ote/device/FixedTransaction.kt"
PROFILE = PROJECT / "engine/device/src/main/kotlin/dev/origin/ote/device/VerifiedProfile.kt"
CONTEXT = "u:object_r:vivo_data_file:s0:c512,c768"
TX_ID = "0123456789abcdef0123456789abcdef"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def kotlin_raw(source: str, name: str) -> str:
    match = re.search(r"private\s+val\s+" + re.escape(name) + r'\s*=\s*"""(.*?)"""', source, re.S)
    if not match:
        raise AssertionError(f"Could not extract actual {name} Kotlin raw string")
    # Kotlin raw strings escape literal dollars through ${'$'} interpolation.
    text = re.sub(r"\$\{\s*'\$'\s*\}", "$", match.group(1))
    return textwrap.dedent(text).strip("\n")


def zip_bytes(label: str) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("animation.xml", f'<animation name="{label}" count="1"/>')
        z.writestr("content.txt", label * 500)
    return output.getvalue()


class Fixture:
    def __init__(self, root: Path, guard: str, transaction: str):
        self.root = root
        self.guard = guard
        self.transaction = transaction
        self.commands = root / "commands"
        self.commands.mkdir()
        self.data = root / "data"
        self.resource = self.data / "bbkcore/bbkthemeres/novoland/103/进化/content"
        self.resource.mkdir(parents=True)
        self.target = self.resource / "animation.zip"
        self.description = self.resource.parent / "description.xml"
        self.apk = root / "system/app/FingerprintUI/FingerprintUI.apk"
        self.library = root / "system/app/FingerprintUI/lib/arm64/libvkrenderer.so"
        self.library.parent.mkdir(parents=True)
        self.apk.write_bytes(b"host-only APK byte-pin fixture\n")
        self.library.write_bytes(b"host-only library byte-pin fixture\n")
        self.description.write_bytes(b"host-only unchanged effect description\n")
        self.description.chmod(0o777)
        self.original = zip_bytes("original")
        self.modified = zip_bytes("modified")
        self.snapshots = root / "app-private/snapshot"
        self.snapshots.mkdir(parents=True)
        self.payload = self.snapshots / "payload.zip"
        self.undo = self.snapshots / "before.zip"
        self.config = root / "fixture.json"
        self.update_config()
        self._create_wrappers()
        self.reset()

    @property
    def lock(self) -> Path:
        return self.resource / ".ote-lock"

    @property
    def stage(self) -> Path:
        return self.resource / (".ote-stage-" + TX_ID)

    def update_config(self, **changes):
        config = dict(model="V2453A", sdk="36", style="100", chcon_fail=False,
                      mv_fail=False, post_context_fail=False, fired=False,
                      free_kb="999000", concurrent_source_change=False,
                      mv_t_supported=True, swap_target_directory=False,
                      rename_ancestor=False, rename_stage=False, fd_supported=True)
        if self.config.exists():
            config.update(json.loads(self.config.read_text()))
        config.update(changes)
        self.config.write_text(json.dumps(config))

    def _create_wrappers(self):
        # Mutations are additionally confined to this fixture by wrapper checks,
        # even if a future change accidentally renders an external path.
        code = r'''#!/usr/bin/python3
import json,os,pathlib,subprocess,sys
ROOT = pathlib.Path(__ROOT__)
CFG = ROOT/'fixture.json'
c = json.loads(CFG.read_text())
tool=pathlib.Path(sys.argv[0]).name
args=sys.argv[1:]
target=ROOT/'data/bbkcore/bbkthemeres/novoland/103/进化/content/animation.zip'
def within(p):
    real=pathlib.Path(os.path.realpath(p))
    return real == ROOT or ROOT in real.parents
def store(): CFG.write_text(json.dumps(c))
if tool=='getprop':
    print(c['model'] if args[0]=='ro.product.model' else c['sdk'] if args[0]=='ro.build.version.sdk' else '')
elif tool=='settings': print(c['style'])
elif tool=='df':
    print('Filesystem 1024-blocks Used Available Capacity Mounted-on')
    print('fixture 1000000 1000 '+c['free_kb']+' 1% '+str(ROOT))
elif tool=='stat':
    p=pathlib.Path(args[-1]); fmt=args[-2]
    assert within(p), 'stat outside fixture'
    if not c['fd_supported'] and str(p).startswith('/proc/self/fd/'):
        sys.exit(1)
    st=os.stat(p,follow_symlinks=('-L' in args))
    resolved=p.resolve()
    if fmt=='%d:%i':
        print(str(st.st_dev)+':'+str(st.st_ino))
    elif fmt=='%u:%g:%a':
        mode=format(st.st_mode & 0o7777,'o')
        private=p.is_dir() and (resolved.name.startswith('.ote-lock') or resolved.name.startswith('.ote-stage-'))
        print(('0:0:' if private else '10255:10255:')+mode)
    else: raise AssertionError('new stat format not modeled: '+fmt)
elif tool=='ls':
    p=pathlib.Path(args[-1]); assert within(p)
    bad = c['post_context_fail'] and not c['fired'] and p.resolve()==target.resolve() and p.read_bytes()==(ROOT/'app-private/snapshot/payload.zip').read_bytes()
    if bad: c['fired']=True; store()
    print(('u:object_r:wrong_type:s0' if bad else 'u:object_r:vivo_data_file:s0:c512,c768')+' '+str(p))
else:
    if tool=='mv' and args==['--help']:
        print('usage: mv -f -T SOURCE DEST' if c['mv_t_supported'] else 'usage: mv SOURCE DEST')
        sys.exit(0)
    if tool in ('chown','chmod','chcon'): paths=args[1:]
    elif tool=='mkdir':
        paths=[];skip=False
        for arg in args:
            if skip: skip=False;continue
            if arg=='-m': skip=True;continue
            if arg.startswith('-'):continue
            paths.append(arg)
    else: paths=[a for a in args if not a.startswith('-')]
    assert paths and all(within(p) for p in paths), 'fixture refused external mutation'
    if tool=='chcon': sys.exit(1 if c['chcon_fail'] else 0)
    if tool=='chown':
        if c['concurrent_source_change'] and paths[-1].endswith('/next.zip'):
            target.write_bytes(b'concurrent foreign bytes'); target.chmod(0o777)
        if c['rename_stage'] and not c['fired'] and paths[-1].endswith('/next.zip'):
            c['fired']=True; store()
            stage=target.parent/'.ote-stage-0123456789abcdef0123456789abcdef'
            stage.rename(stage.with_name(stage.name+'-renamed'))
            unrelated=ROOT/'unrelated-directory';unrelated.mkdir(exist_ok=True)
            stage.symlink_to(unrelated,target_is_directory=True)
        sys.exit(0)
    if tool=='mv' and c['mv_fail']:sys.exit(1)
    if tool=='mv' and args[-2].endswith('/next.zip') and not c['fired']:
        if c['swap_target_directory']:
            c['fired']=True;store()
            unrelated=ROOT/'unrelated-directory';unrelated.mkdir(exist_ok=True)
            target.unlink()
            if c['swap_target_directory']=='plain': target.mkdir()
            else: target.symlink_to(unrelated,target_is_directory=True)
        elif c['rename_ancestor']:
            c['fired']=True;store()
            target.parent.rename(target.parent.with_name('content-renamed'))
            unrelated=ROOT/'unrelated-directory';unrelated.mkdir(exist_ok=True)
            target.parent.symlink_to(unrelated,target_is_directory=True)
    os.execv('/usr/bin/'+tool,[tool]+args)
'''.replace("__ROOT__", repr(str(self.root)))
        for name in ["getprop", "settings", "df", "stat", "ls", "mkdir", "chown", "chmod", "chcon", "mv", "rm", "rmdir"]:
            path = self.commands / name
            path.write_text(code)
            path.chmod(0o755)

    def reset(self):
        # These directories are exclusively allocated inside this new fixture.
        for path in self.resource.iterdir():
            if path.name.startswith(".ote-"):
                if path.is_symlink() or path.is_file(): path.unlink()
                else: shutil.rmtree(path)
        if self.target.is_symlink():
            self.target.unlink()
        self.target.write_bytes(self.original)
        self.target.chmod(0o777)
        self.payload.write_bytes(self.modified)
        self.undo.write_bytes(self.original)
        for path in [self.payload, self.undo]: path.chmod(0o600)
        self.update_config(model="V2453A", sdk="36", style="100", chcon_fail=False,
                           mv_fail=False, post_context_fail=False, fired=False,
                           free_kb="999000", concurrent_source_change=False,
                           mv_t_supported=True, swap_target_directory=False,
                           rename_ancestor=False, rename_stage=False, fd_supported=True)

    def render(self, *, applying=True, before=None, wanted=None, payload=None, undo=None):
        # Rewrite literal Android parent guards BEFORE inserting temp profile paths.
        guard = self.guard.replace("PATH=/system/bin:/system/xbin", f"PATH={self.commands}:/usr/bin:/bin")
        guard = guard.replace("/data", str(self.data))
        parameters = dict(DIR=str(self.resource), TARGET=str(self.target), DESC=str(self.description),
                          APK=str(self.apk), LIB=str(self.library), CONTEXT=CONTEXT,
                          DESC_HASH=sha(self.description.read_bytes()), APK_HASH=sha(self.apk.read_bytes()),
                          LIB_HASH=sha(self.library.read_bytes()), ID=TX_ID,
                          BEFORE=before or sha(self.original), WANTED=wanted or sha(self.modified),
                          PAYLOAD=str(payload or self.payload), UNDO=str(undo or self.undo))
        script = guard + "\n" + self.transaction
        for key, value in parameters.items():
            script = script.replace("{{"+key+"}}", shlex.quote(value))
        script = script.replace("{{REQUIRED_KB}}", "2048").replace("{{APPLYING}}", "1" if applying else "0")
        assert "{{" not in script, "Unmodeled production placeholder"
        assert not re.search(r"(?<![\w/])/(data|system|vendor|product|dev/block)(?:/|\b)", script), "Unmapped production path"
        return script

    def run(self, *, ok, contains=None, **kwargs):
        script = self.root / "rendered-transaction.sh"
        script.write_text(self.render(**kwargs))
        result = subprocess.run(["/bin/sh", str(script)], capture_output=True, text=True, timeout=15)
        assert (result.returncode == 0) == ok, (result.returncode, result.stdout, result.stderr)
        if contains:
            assert contains in result.stdout+result.stderr, (contains, result.stdout, result.stderr)
        return result

    def clean(self):
        assert not self.lock.exists() and not self.stage.exists(), "Private staging/lock leaked"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=PROJECT/"build/reports/device-transactions.json")
    args = parser.parse_args()
    if os.name != "posix":
        raise SystemExit("This real-shell host fixture needs POSIX; no Android test was performed.")
    kotlin = SOURCE.read_text()
    guard, transaction = kotlin_raw(kotlin, "GUARD"), kotlin_raw(kotlin, "TRANSACTION")
    profile = PROFILE.read_text()
    assert 'MODEL = "V2453A"' in profile and "SDK = 36" in profile, "Review fixture model when profile changes"
    cases = []
    with tempfile.TemporaryDirectory(prefix="ote-device-transaction-", dir="/tmp") as folder:
        f = Fixture(Path(folder), guard, transaction)
        old_inode = f.target.stat().st_ino
        with zipfile.ZipFile(f.target) as old_reader:
            f.run(ok=True, contains="OTE_COMMIT_OK")
            assert f.target.read_bytes() == f.modified and f.target.stat().st_ino != old_inode
            assert old_reader.testzip() is None and "original" in old_reader.read("animation.xml").decode()
        with zipfile.ZipFile(f.target) as current:
            assert current.testzip() is None and "modified" in current.read("animation.xml").decode()
        f.clean();cases.append("atomic apply: old ZIP reader remains valid, new reader sees replacement")

        f.update_config(style="2003000011")
        f.run(ok=True, applying=False, before=sha(f.modified), wanted=sha(f.original),
              payload=f.undo, undo=f.payload, contains="OTE_COMMIT_OK")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("restore may return verified original while effect remains selected")

        f.reset();f.target.write_bytes(b"unknown current ZIP")
        f.run(ok=False, contains="source_changed")
        assert f.target.read_bytes() == b"unknown current ZIP"
        f.clean();cases.append("unknown source hash is refused without replacing it")

        f.reset();f.update_config(style="2003000011")
        f.run(ok=False, contains="select_none_first")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("apply refuses active effect before creating staging")

        f.reset();f.payload.write_bytes(b"corrupted payload")
        f.run(ok=False, contains="payload_mismatch")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("corrupted payload is refused before creating staging")

        f.reset();f.undo.write_bytes(b"corrupted undo")
        f.run(ok=False, contains="payload_mismatch")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("corrupted undo snapshot is refused before writes")

        f.reset();f.update_config(chcon_fail=True)
        f.run(ok=False, contains="prepare_failed")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("injected chcon failure leaves source unchanged and removes owned staging/lock")

        f.reset();f.update_config(mv_fail=True)
        f.run(ok=False, contains="rename_failed")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("injected rename failure preserves source and cleans staging/lock")

        f.reset();f.update_config(post_context_fail=True)
        f.run(ok=False, contains="metadata_mismatch")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("injected post-rename context failure atomically rolls back then cleans")

        f.reset();f.lock.mkdir(mode=0o700)
        (f.lock/"token").write_text("foreign-token")
        (f.lock/"pid").write_text("12345")
        f.run(ok=False, contains="transaction_busy")
        assert (f.lock/"token").read_text() == "foreign-token" and (f.lock/"pid").read_text() == "12345"
        assert not f.stage.exists() and f.target.read_bytes() == f.original
        cases.append("busy lock is preserved and not removed by unsuccessful acquisition")

        f.reset();f.stage.mkdir(mode=0o700)
        (f.stage/"foreign.txt").write_text("not owned by invocation")
        f.run(ok=False, contains="stage_exists")
        assert (f.stage/"foreign.txt").read_text() == "not owned by invocation"
        assert not f.lock.exists() and f.target.read_bytes() == f.original
        cases.append("preexisting staging directory is preserved; only newly acquired lock is cleaned")

        f.reset();f.update_config(concurrent_source_change=True)
        f.run(ok=False, contains="source_changed")
        assert f.target.read_bytes() == b"concurrent foreign bytes"
        f.clean();cases.append("source changed during staging is preserved, not overwritten by rollback")

        f.reset();f.update_config(free_kb="1")
        f.run(ok=False, contains="insufficient_space")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("insufficient free space refuses transaction before staging")

        f.reset();f.update_config(sdk="35")
        f.run(ok=False, contains="device_mismatch")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("mismatched Android SDK fails the actual profile guard")

        f.reset();f.update_config(mv_t_supported=False)
        f.run(ok=False, contains="mv_no_target_directory_unsupported")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("runtime without verified mv -T fails before lock/stage creation")

        f.reset();f.update_config(fd_supported=False)
        f.run(ok=False, contains="directory_fd_unsupported")
        assert f.target.read_bytes() == f.original
        f.clean();cases.append("runtime without inherited FD/stat-L support fails before lock/stage creation")

        f.reset();f.update_config(swap_target_directory=True)
        f.run(ok=True, contains="OTE_COMMIT_OK")
        unrelated=f.root/"unrelated-directory"
        assert unrelated.is_dir() and not list(unrelated.iterdir()), "payload escaped through destination directory symlink"
        assert not f.target.is_symlink() and f.target.read_bytes()==f.modified
        f.clean()
        cases.append("hostile target symlink is replaced as a leaf by mv -T; unrelated directory remains untouched")

        f.reset();f.update_config(swap_target_directory="plain")
        f.run(ok=False,contains="rename_failed")
        assert f.target.is_dir() and not list(f.target.iterdir())
        assert f.lock.exists() and f.stage.exists()
        cases.append("hostile actual directory at destination refuses file rename and retains pending undo")
        f.target.rmdir()

        f.reset();f.update_config(rename_stage=True)
        f.run(ok=False, contains="anchor_entry_changed")
        unrelated=f.root/"unrelated-directory"
        assert not list(unrelated.iterdir()), "staging writes escaped the opened private directory"
        assert f.target.read_bytes() == f.original
        moved_stage=f.stage.with_name(f.stage.name+"-renamed")
        assert f.stage.is_symlink() and moved_stage.is_dir() and f.lock.exists()
        assert (moved_stage/"next.zip").read_bytes() == f.modified
        assert (moved_stage/"undo.zip").read_bytes() == f.original
        cases.append("stage-entry rename/symlink cannot redirect FD9 writes; changed entry refuses publish and cleanup")

        f.reset();f.update_config(rename_ancestor=True)
        moved_resource=f.resource.with_name("content-renamed")
        try:
            f.run(ok=False, contains="parent_mismatch")
            assert not list((f.root/"unrelated-directory").iterdir()), "ancestor symlink redirected target writes"
            assert f.resource.is_symlink() and (moved_resource/"animation.zip").read_bytes() == f.original
            assert (moved_resource/".ote-lock").exists(), "changed ancestor should retain pending metadata"
            cases.append("resource ancestor rename cannot redirect FD8 publish/rollback; original inode restored and pending retained")
        finally:
            if f.resource.is_symlink():
                f.resource.unlink()
                moved_resource.rename(f.resource)

        f.reset();twin=f.root/"linked-resource.zip";twin.write_bytes(f.original)
        f.target.unlink();f.target.symlink_to(twin)
        f.run(ok=False, contains="metadata_mismatch")
        assert f.target.is_symlink() and twin.read_bytes() == f.original
        cases.append("symlink target is rejected without replacing or following it for writes")

    report = dict(passed=len(cases), cases=cases,
                  source_sha256=sha(kotlin.encode()), profile_sha256=sha(profile.encode()),
                  scope="Actual extracted Kotlin GUARD/TRANSACTION shell, real POSIX hashing/rename/ZIP/snapshots. Android UID/SELinux/settings/model/APK/library identity are host fixtures. No Android device or real system writes.")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
