# QA and acceptance

## Evidence labels

- Source implementation is not compilation proof.
- The owner later reported a successful baseline build and supplied UI screenshots; final log/artifact not independently reviewed. The redesign branch needs a fresh compile/test run. See `verification/device-ui-smoke.json` and `docs/redesign/IMPLEMENTATION.vi.md`.
- Host shell fixtures use real filesystem/rename/ZIP with Android identity and SELinux mocked, not real device QA.
- OriginOS runtime/UI, GIF conversion performance, permission dialogs, Room migrations and native pack rendering require actual build/device testing.

## Commands

`./gradlew :core:root:test :engine:pack:test :engine:png:test :data:testDebugUnitTest :engine:device:testDebugUnitTest :app:assembleDebug`

`./gradlew :app:lintDebug`

`python3 scripts/validate_project.py`

`python3 scripts/test_device_transaction.py`

`python3 scripts/test_device_recovery.py`

## Device acceptance order

1. Install debug APK, decline/no SU: browse and Studio still work; no root dialog on launch.
2. Switch EN/VI/JA and all three styles/light/dark; verify recreation and persisted settings.
3. Generate Soft Pulse and preview; import PNG ZIP, GIF and original .itz. Check duration/color/transparency. Import malformed packs/traversal/oversized archives; library must remain unchanged.
4. Export/import a transformed pack; verify same frame dimensions, metadata, alpha, count and checksums.
5. Check root manually, reject SU, then grant SU: actual status follows result. Verify a different device/APK hash refuses write.
6. Create/export a snapshot. Confirm current resource hash/metadata unchanged by backup.
7. Select None in system fingerprint effects, apply a small procedural or known test pack. Select the owned effect in UI, preview first and then one real unlock with known PIN fallback.
8. Restore previous snapshot and compare SHA/owner/mode/context. Do not equate restore with cache purge.
9. Simulate temp-root loss, timeout, disk full, chcon/rename errors using fixtures first. Pending journal must not be reported successful, and unknown target/lock must not be deleted.
10. Measure memory/frame pacing and battery on phone. No claims from pack file size or nominal FPS alone.
11. Test process death while transaction pending; verify goal-aware restore reconciliation, stale-PID lock check, and refusal of foreign locks.
12. AOD write action must say unsupported; do not test mutation using an unimplemented adapter.

No shell script from an imported pack may be executed. No boot/system/vendor/vbmeta or biometric enrollment writes are in scope.

Redesign acceptance and PC commands: [implementation](redesign/IMPLEMENTATION.vi.md), [PC handoff](redesign/PC_HANDOFF.vi.md).
