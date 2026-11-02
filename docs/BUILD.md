# Build OTE

This source distribution targets Android SDK 36 and Java 17. It needs a **full
JDK 17**, not a JRE. The cloud executor used to author the project does not have
the required JDK 17, Android SDK or installed Gradle. Toolchain downloads are
unavailable in its current shell environment, so no APK compilation has been
verified there. Do not treat source checks or shell fixture tests as an APK build.
The owner's GitHub Actions account is disabled. An independent Codemagic build
is configured; see [mobile setup instructions](CODEMAGIC.md).

## Prepare a build machine

1. Install a JDK 17, Android Studio, and Python 3.9 or newer.
2. In Android Studio's SDK Manager, install **Android SDK Platform 36** and
   **Android SDK Build-Tools 36.0.0**. Review and accept the SDK licenses yourself.
3. Set `JAVA_HOME` to the JDK 17 installation. Set `ANDROID_HOME` to the SDK
   directory, or create an untracked `local.properties` with an `sdk.dir` entry.
   Do not commit machine paths, signing credentials, or keystores.
4. Preserve the network proxy/CA configuration required by your environment.
   Dependency resolution needs Google Maven, Maven Central, the Gradle Plugin
   Portal, and the official Gradle distribution service.

The Android Gradle Plugin version is 8.10.1 and the Gradle version is 8.11.1.
The Kotlin version is 2.1.21. Dependencies and SDK setup are declared in the
project's Gradle Kotlin DSL files.

## Run the build

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :core:root:test :engine:pack:test :engine:device:testDebugUnitTest :app:assembleDebug :app:lintDebug --stacktrace
```

Windows PowerShell (APK build/lint only; transaction/recovery shell fixtures need Linux or WSL with `/proc/self/fd` and GNU tools):

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --stacktrace
```

The debug APK, if compilation succeeds, is at
`app/build/outputs/apk/debug/app-debug.apk`. Install it through the device's
normal package installer. Building or opening OTE must not require root.
Test SU behavior separately on the intended device.

## Bootstrap provenance

The included `gradlew` and `gradlew.bat` are **custom bootstrap entrypoints**.
No official `gradle-wrapper.jar` binary was obtained in the restricted executor,
and no placeholder JAR is supplied. `scripts/gradle_bootstrap.py` downloads the
official Gradle 8.11.1 ZIP over HTTPS, obtains the official `.sha256` sidecar,
verifies the archive hash, rejects ZIP traversal/symlinks/duplicates/oversized
content, and caches the verified installation under `.tooling/gradle/`.
It does not install an Android SDK or accept licenses automatically.

For a separately reviewed digest, set `OTE_GRADLE_SHA256` to that 64-character
SHA-256 value before the initial bootstrap. Without this override, trust rests
on HTTPS and the official distribution/checksum service, as documented above.
Never paste an unverified digest from an unrelated download source.

If a prepared machine already has Gradle 8.11.1, it may run the same tasks using
`gradle` directly. To replace the custom entrypoints with the official wrapper:

```sh
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Review the generated wrapper files and configure the distribution checksum
before committing them. Android Studio can open the project after the machine
has a working Gradle distribution/wrapper; missing tools should be resolved
there rather than by pretending that an uncompiled APK exists.

## Release

`./gradlew :app:assembleRelease` prepares the release variant. Without a signing
configuration it is unsigned; it is not ready to install. To sign a release,
provide the environment variables documented in `app/build.gradle.kts`:
`OTE_KEYSTORE`, `OTE_STORE_PASSWORD`, `OTE_KEY_ALIAS`, `OTE_KEY_PASSWORD`.
Use your own private key. OTE does not include a shared production signing key.
Validate release R8 output and run the device checklist before distribution.

## Continuous integration

`.github/workflows/android.yml` builds the debug APK and runs unit/lint checks
on a prepared hosted runner. Uploading that workflow to a repository and running
it requires the repository owner's normal authorization. No CI run or uploaded
APK is claimed merely because the workflow file exists. `codemagic.yaml` uses
the provider's M2 machine with Java 17 and runs `scripts/ci_build.py`. Its reports
explicitly distinguish passed, failed and platform-skipped steps. Android root
or instrumentation tests are not run by this hosted workflow.

## Development APK update identity

The CI workflow caches its debug keystore only for private repositories to keep test APK
updates installable across runs. Public-repository CI debug keys are temporary. This is development signing, not production key
management. Keep the repository private. Loss of that cache changes the debug
signing key; Android then refuses an in-place update. Export OTE snapshots through
SAF before any uninstall/data-clear. Release signing must use owner-controlled
keystore secrets, never this development cache.

APK compilation runs before unit/lint checks so an APK artifact can be retained
for diagnosis even when checks fail. A retained APK is not a passing build; inspect
the workflow conclusion and reports. The agent will fix CI failures before calling
a build verified.

For public-repository builds, final test APK delivery can be re-signed on the
owner-controlled build workspace using an owner-private PKCS12 key and the
retained apksigner tool. Never commit that key or its password. Downloading a raw
CI debug artifact instead uses the runner debug identity and is not an update
identity guarantee.
