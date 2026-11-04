# Root runtime boundary

`core:root` contains the JVM `RootManager`, independent of Compose and Android
context. Creating or observing it does not request SU. The app must call `probe`
or a device-adapter operation from an explicit user action.

## Root evidence

Every `execute` starts `su -c` with an OTE-owned shell prelude. That prelude runs
`/system/bin/id -u`, requires exactly `0`, and emits a per-invocation nonce before
the trusted command. A process that exits zero without the nonce is not accepted
as a successful root command. A previously successful request is never used as
authorization for a later request: temporary root is checked again each time.

`probe` reads `su -v` and `su -V` after obtaining UID0 evidence. Product labels
such as Magisk, KernelSU/Next, APatch, BakaSU and ReSukiSU are reported only when
the returned strings name them. Numeric version codes alone remain `Unknown`.
Unknown framework does not imply missing root; UID verification decides that.

The `RootState.detail` strings are machine codes for the UI to localize. Manager
names are product identifiers. Raw command output is returned to the fixed-path
adapter but is not logged or placed in root state. No environment, account,
serial, secret or root-manager configuration dump is performed.

## Execution, failures and cancellation

Stdout and stderr are consumed concurrently. Each retained capture is limited to
64 KiB by default, plus a fixed truncation marker. Excess output continues to be
drained so the child cannot deadlock on full pipes. The supplied timeout covers
the local process wait. Captures poll only available bytes and do not wait
indefinitely for an inherited pipe descriptor held by another child.

The manager attempts to stop and close its local SU client on timeout or coroutine
cancellation. **This is not proof that a privileged daemon-side command or child
has stopped.** Timeout returns exit124 and `timedOut=true`; cancellation propagates
`CancellationException`. Both invalidate the available state and explicitly mark
operation state unknown. The device adapter must inspect its journal and fixed
target before offering another write. No command is automatically retried.

A nonzero payload exit after UID proof means that operation failed; root can
still be available. A launch failure or missing UID evidence cannot report
success. Root identity still does not establish access to a particular file or
SELinux permission: device adapters must verify those separately.

## Trusted input only

`execute` accepts code from OTE's own verified adapters. It must never receive
scripts, manifest fields or commands from imported theme packs. Every interpolated
argument must use `RootManager.quote`, which performs POSIX single-argument
quoting and rejects NUL. The module does not disable SELinux, mount filesystems,
flash partitions, modify boot images, install persistent root, or implement an
exploit. Specific writes remain the device adapter's audited responsibility.

## Host test scope

`RootManagerTest` uses real local POSIX processes and explicit mock SU/UID
launchers. It covers demand-only construction, UID rejection before payload,
temporary-root loss, independent command failure, bounded concurrent output,
timeout, cancellation, launch errors, false exit-zero claims, argument quoting
and evidence-based manager identification. These fixtures do not prove Android
SU behavior, framework compatibility or SELinux access on the phone.

Build dependencies: `core:model`, Kotlin coroutines-core; tests use
`kotlin-test-junit` / JUnit4. The root coordinator owns the Gradle configuration.

## Product scope and readiness

The internal class name RootManager denotes a privileged transport, not a root-management product. OTE has no SU policy editor, module manager, kernel flasher or root installation feature. The user-facing page is Device and recovery.

RootReadinessChecker stops immediately if probe fails and checks root state again after compatibility and status reads. ROOT_CHECK history uses VERIFIED only for UID0 plus profile verification. Unsupported profiles and unavailable SU receive separate outcomes; prior empty SUCCESS entries are displayed as uncertain without rewriting the database. IOException launch reasons retain numeric errno categories only, never exception paths/messages.

DeviceCatalogReader is an explicit read-only discovery action. It lists bounded metadata at observed download roots, rejects malformed/truncated responses and does not grant write eligibility. The shell resource is owned by OTE, never loaded from a theme pack. New fixtures are host evidence only; Android root/SELinux behavior awaits testing.
