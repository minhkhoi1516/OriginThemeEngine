# Fingerprint transaction security scope

The writable adapter is restricted to the previously researched PD2453 profile.
It checks the model/SDK, APK/library/resource-description hashes, resource
ownership/mode/SELinux metadata, known current hash and app-owned snapshots.
It never executes scripts supplied by theme packs.

## Anchored transaction

The resource ancestors in the observed device profile are world-writable. Checking
their names before an absolute-path `mv` is therefore insufficient: another writer
could replace a target with a directory symlink or rename an ancestor in the gap.

`FixedTransaction` now requires a runtime that advertises `mv -T`, supports
`stat -L`, and preserves inherited directory file descriptors through its tools.
Unsupported behavior fails before publishing a resource. The transaction:

1. Opens the checked resource directory as inherited FD8 and compares its device/
   inode to the directory inspected before `cd`/open.
2. Resolves the target as `/proc/self/fd/8/animation.zip`.
3. Creates root-owned mode0700 lock/staging entries, opens them as FD7/FD9, and
   compares each opened inode with the corresponding checked entry.
4. Writes new lock/stage leaves only through the opened descriptors. Names must
   not already exist; noclobber remains enabled and per-leaf ownership flags track
   what this invocation attempted to create.
5. Uses `mv -f -T` for both publish and rollback, preventing a swapped destination
   directory symlink from turning the operation into a move *inside* that directory.
6. Rechecks the resource and entry inode identities. Unexpected names/inodes or
   unknown private-directory entries retain pending state instead of recursively
   deleting or forcing a replacement.

The stale-recovery program also uses `anchor_resource`/`anchor_ok` and opens lock/stage descriptors. It verifies journal token, dead PID, root-private mode, leaf hashes and matching directory entries before bounded cleanup. Eight separate recovery host cases cover same-token stale locks, live/foreign locks, committed targets, unknown bytes and symlink stages; this is not Android runtime proof.

## Deliberate limits

This is fail-closed hardening, not a complete proof against an arbitrary root
adversary or every filesystem race. In particular, shell name-check followed by
`rmdir` is not an atomic compare-and-unlink operation. A concurrent writer can
still cause denial of service, orphan/retain private staging, alter the public
resource independently, or race within the same verified directory. The cleanup
scope stays at fixed temporary names under the pinned resource directory; it does
not traverse replacement ancestors or delete unknown directory contents.

Another root process is outside the isolation model: it can access and change
root-private directories and the application itself. Persistent filesystem/mount
changes during an operation are also not established safe by these checks.
Timeout/cancellation does not prove a remote privileged process stopped. Recovery
must inspect its journal, pinned target and live lock PID before offering another
write.

The supported Android runtime still requires on-device verification of `mv -T`,
FD inheritance, `/proc/self/fd` access, `stat -L`, SELinux behavior and resource
reload. Host success must not be described as Android runtime success.

## Executed host regression

`scripts/test_device_transaction.py` extracts the actual Kotlin GUARD and
TRANSACTION raw strings. It uses real POSIX shells, open directory descriptors,
hashing, ZIP readers and rename operations inside a new `/tmp` directory. UID,
SELinux, model/SDK/settings and engine hashes are explicit mocks. Mutation wrappers
also refuse paths outside that private test directory.

The regressions include an old ZIP reader held open during publish, rollback and
failure paths, refused unknown inputs, unsupported runtime capabilities, and
hostile destination-symlink, stage-entry rename and resource-ancestor rename
injections. The generated report records the exact Kotlin source/profile hashes.

Current executed reports:21transaction cases,8recovery cases. A symlink leaf is
safely replaced by mv-T inside the anchored resource directory; an actual
directory destination is refused. No unrelated directory receives the payload.
