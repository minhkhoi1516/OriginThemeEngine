#!/usr/bin/env python3
"""Run actual recovery shell from DeviceEngine.kt in a private mocked fixture."""
from pathlib import Path
import json,os,hashlib,subprocess,tempfile
from test_device_transaction import Fixture,kotlin_raw,sha,TX_ID,SOURCE,PROJECT

ENGINE=PROJECT/'engine/device/src/main/kotlin/dev/origin/ote/device/DeviceEngine.kt'

def main():
    cases=[]
    guard=kotlin_raw(SOURCE.read_text(),'GUARD')
    recover=kotlin_raw(ENGINE.read_text(),'RECOVER').replace('{{AFTER}}','{{WANTED}}')
    with tempfile.TemporaryDirectory(prefix='ote-recovery-',dir='/tmp') as tmp:
        f=Fixture(Path(tmp),guard,recover)
        stat=f.commands/'stat'
        code=stat.read_text().replace("elif fmt=='%u:%g:%a':", "elif fmt=='%u:%a':\n        print('0:'+format(st.st_mode & 0o7777,'o'))\n    elif fmt=='%u:%g:%a':")
        stat.write_text(code);stat.chmod(0o755)
        def seed(token=TX_ID,pid=2147483647):
            f.lock.mkdir(mode=0o700)
            for name,value in [('token',token),('pid',str(pid))]:
                p=f.lock/name;p.write_text(value);p.chmod(0o600)
            f.stage.mkdir(mode=0o700)
            for name,data in [('next.zip',f.modified),('undo.zip',f.original)]:
                p=f.stage/name;p.write_bytes(data);p.chmod(0o777)
        f.run(ok=True);assert f.target.read_bytes()==f.original;f.clean();cases.append('no lock: recovery leaves target unchanged')
        seed();f.run(ok=True);assert f.target.read_bytes()==f.original;f.clean();cases.append('same-token stale lock: cleans verified leaves without writing target')
        seed();f.target.write_bytes(f.modified);f.run(ok=True);assert f.target.read_bytes()==f.modified;f.clean();cases.append('already-published target: recovery preserves committed bytes')
        f.reset();seed(token='f'*32);f.run(ok=False,contains='foreign_lock');assert f.lock.exists() and f.stage.exists();cases.append('foreign lock preserved')
        f.reset();seed(pid=os.getpid());f.run(ok=False,contains='transaction_still_running');assert f.lock.exists() and f.stage.exists();cases.append('live process lock preserved')
        f.reset();seed();(f.stage/'next.zip').write_bytes(b'unknown');f.run(ok=False,contains='stage_file_unknown');assert (f.stage/'next.zip').read_bytes()==b'unknown' and f.lock.exists();cases.append('unknown staging bytes preserved')
        f.reset();seed();f.target.write_bytes(b'foreign-resource');f.run(ok=False,contains='recovery_unknown_target');assert f.target.read_bytes()==b'foreign-resource' and f.stage.exists();cases.append('unknown active resource refuses cleanup')
        f.reset();seed();f.stage.rename(f.resource/'.renamed-stage');outside=Path(tmp)/'outside';outside.mkdir();(outside/'next.zip').write_bytes(b'untouched');f.stage.symlink_to(outside,target_is_directory=True)
        f.run(ok=False,contains='stage_metadata');assert (outside/'next.zip').read_bytes()==b'untouched';cases.append('symlink staging target is not followed for deletion')
        # f.reset uses owned fixture rmtree only; do not run it with this linked stage.
    report={'passed':len(cases),'cases':cases,'device_engine_sha256':sha(ENGINE.read_bytes()),'transaction_source_sha256':sha(SOURCE.read_bytes()),'scope':'Actual extracted recovery shell, real POSIX process/FD/filesystem operations; Android root/metadata/profile identity mocked. No Android or production paths modified.'}
    dest=PROJECT/'build/reports/device-recovery.json';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
