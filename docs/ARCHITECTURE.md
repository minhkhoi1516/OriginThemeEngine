# Origin Theme Engine 0.1 architecture

This is a source implementation of the MVP, not a statement that the complete master-prompt product or on-device QA is finished.

## Modules

- `core:model`: platform-neutral domain models and versioned pack contract.
- `engine:pack`: pure JVM parser/validator/exporter, no Android or root.
- `core:root`: pure JVM demand-only SU process boundary, no UI or pack dependency.
- `data`: Android SAF, GIF/image conversion, Room and DataStore repositories. App-private UUID storage.
- `engine:device`: verified device profile, native PNG8 builder, private snapshot/journal and fixed resource transaction.
- `app`: Hilt composition root, Compose design system, navigation, feature screens and MVVM.

The requested module tree is consolidated into seven Gradle modules to keep the MVP build manageable. Feature screens and localization are packages/resources within app; splitting those into separate Gradle libraries is an organizational change rather than extra functionality. Dependencies flow toward model/pack/root. DeviceEngine is an Android integration facade over repository and root; it does not claim a perfectly platform-free domain use-case layer.

## Real user flow

1. Start without SU; browse/import/preview/export locally.
2. SAF copies bounded inputs into private cache; validate ZIP and PNG/checksums; commit library entry to Room only after verified assets exist.
3. Studio transforms real pixels on Android Canvas and exports a validated pack. Imported per-frame durations preview as supplied; Studio FPS export normalizes frame delays.
4. User explicitly checks root; UID0 attestation happens inside each actual SU shell. Recognize manager only from explicit version evidence. Unknown forks remain unknown.
5. Apply FP requires the verified profile and selected effect None (100) via system UI. Builder creates palette PNG8; app captures current bytes, validates snapshot and persists a pending journal before any resource replacement.
6. A fixed root transaction stages next/undo privately beside the resource, checks owner/mode/context/hash/inode and free space, then renames atomically. Existing ZIP readers retain their old inode.
7. Postcheck verifies bytes/metadata. Failure attempts rollback only while target and undo match known hashes. Timeout/cancellation is uncertain; journal remains pending until explicit restore/reconciliation.
8. Restore means **previous snapshot**, not necessarily Vivo factory/default. The first snapshot may be an already-applied Alya/Angel mod. No default graphics or licensed artwork are embedded in APK.

## Supported profile evidence

Only V2453A SDK36 with the exact provided FingerprintUI APK, native library, description hashes and measured resource metadata. See `VerifiedProfile.kt` and the prior research ledger. Android build string alone does not prove compatibility. Exact profile readback is required for every transaction. Other devices get local library/editor features, no mutation adapter.

AOD pattern4 ITZ import converts the animation for preview. Original clock/battery/layout metadata is not installed. AOD writing is explicitly disabled in this MVP: the old shell experiment overwrote many resources in place, and this app has not verified a multi-file atomic activation contract. No theme store ownership/signature bypass is implemented.

## State and failure boundaries

- Source packs never provide commands. Root shell templates are compiled app code with constant destination paths.
- App-private snapshots are mandatory for app operations. This does not reintroduce the obsolete external Download backup path used by old shell tests.
- Export snapshots through SAF before uninstalling. Android app data/snapshots disappear when the app is removed or data cleared.
- Root timeout cannot guarantee a privileged child stopped. Fixed lock and journal block blind retries; only a same-token stale lock with a dead PID and verified known files can be cleaned.
- If lock ownership/token/metadata is incomplete or unexpected, automatic recovery refuses to delete it. Diagnostics/manual review is needed; the app must not erase unknown files.
- Root may be lost after reboot. No boot persistence, framework installation, mount, overlay, APK edits, biometric enrollment/authentication or hardware calibration.
- GPU/render cache reload remains a user-observed behavior. Hash verified does not imply a frame was rendered or that battery/FPS are measured.
