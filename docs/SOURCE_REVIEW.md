# Source integration review

Review scope: the Kotlin/Gradle source tree authored for OTE MVP, inspected on
2026-11-01. **This is a source review, not a compiler, dependency-resolution, APK,
lint or Android runtime result.** No working Android/JDK/Gradle toolchain was
available to this reviewer. Device shell-transaction tests are reported separately.

## Cross-module contracts checked

The actual call sites were compared against their declarations, rather than only
against the initial design document:

| Callers | Implementation checked | Source result |
| --- | --- | --- |
| Compose screens | `OteViewModel` actions/state | All referenced actions/state properties have declarations. |
| `OteViewModel` | `ThemeRepository` | Import, GIF/PNG import, preset creation, export, delete, favorite, preferences, logging and lookup APIs match. |
| `OteViewModel` | `DeviceEngine` | Device info, compatibility, status, apply, backup, restore and backup export APIs match. |
| `OteViewModel`, `DeviceEngine` | `RootManager` | `state`, `probe`, `execute`, `quote` and `RootResult.succeeded` match. |
| Repository/studio/device engine | `PackCodec` | `importPack`, `exportPack`, `readManifest`, `validateDirectory` and model types match. |
| Hilt providers and constructors | Application, app/data modules, view model, repository and engine | Required root/codec/database/context bindings are declared. No root request occurs from construction. |

No further definite unresolved cross-module name or signature mismatch was found
by inspection. This does not establish that Kotlin, KSP, Room, Hilt or Compose
compilation will pass; only a real build can establish that.

## Findings corrected during review

The coordinator made these changes; they were subsequently checked in the source:

1. `ThemeRepository` and `DeviceEngine` explicitly import
   `kotlinx.serialization.decodeFromString` for their generic decode calls.
2. `StudioExporter` reads a source frame into a bounded immutable byte array,
   verifies its SHA-256, and decodes that same verified byte array. It no longer
   relies solely on the library's previously stored metadata before rendering.
3. Studio output is fully validated and exported to a private ready pack before
   opening the SAF output stream. A pack validation failure therefore cannot
   first truncate the selected destination document.
4. The two JVM modules that expose `api(...)` dependencies (`core:model` and
   `engine:pack`) now explicitly apply `java-library`, rather than relying on
   implicit configuration behavior of the Kotlin plugin.

The pack codec also leaves caller-owned SAF streams open while closing its ZIP
deflater, bounds compressed export size, rehashes frames during export, and closes
its import staging ZIP before deleting it. The last item avoids a Windows open-file
deletion failure.

## Android API and build declarations

- Android modules declare compile SDK 36, with app target SDK 36 and minimum SDK 33.
- Framework APIs used by the reviewed code are within that minimum: per-app
  `LocaleManager` is API 33; Java NIO filesystem APIs, `Process.isAlive`, timed
  process waiting and forced destruction are available by API 26. `AtomicFile`,
  `StatFs.availableBytes`, Bitmap/Canvas and the SAF APIs used are older.
- No source reference to a nonexistent MIUIX library or root SDK was found. The
  MIUIX-inspired/glass components are Compose implementations. Glass does not claim
  to sample a real blurred backdrop.
- Compose compiler plugin and Kotlin versions are declared together at 2.1.21.
  AGP, KSP, Hilt, Room and AndroidX coordinates remain **unresolved/uncompiled in
  this review**; source inspection is not proof that remote artifacts are reachable.
- A minimal `src/main/AndroidManifest.xml` for each Android library was recommended
  to make library manifest inputs explicit. This was a build-hardening recommendation,
  not a compiler-observed failure.

## Offline checks observed

`python scripts/validate_project.py` completed eight structural/resource/policy
checks during this review. At that point it reported 109 matching EN/VI/JA string
keys/placeholders, parseable XML, existing UI string references, a local-only
manifest, Compose/Hilt entry points and a parseable pack schema. These checks do
not compile Kotlin or exercise Android classes.

## Remaining concrete host portability limitation

The JVM root tests invoke `/bin/sh`, `head`, `tr`, `/dev/zero` and executable POSIX
fixture scripts. Several pack/data tests create filesystem symlinks. Consequently,
the complete host-test command is not portable to a default Windows installation,
even when JDK 17 and the Android SDK are installed. Use Linux/macOS, WSL with its own
JDK/SDK configuration, or the Linux CI runner for those host tests. Windows may
build the Android variants separately. The build instructions should distinguish
that requirement from a Gradle/Kotlin compilation error.

## Verification still required

Resolve dependencies and run the documented unit/lint/debug-build tasks on a
prepared toolchain, followed by the Android graphics/SAF instrumentation test and
the device checklist. The fingerprint transaction remains a new app integration;
successful earlier shell trials do not by themselves verify APK UID/SELinux
access, lifecycle cancellation or root-manager dialogs. AOD writes and advanced
studio features marked unsupported remain unsupported.
