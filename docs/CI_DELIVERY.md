# GitHub-hosted Android builds

The Android workflow builds on GitHub-hosted Linux with JDK 17 and Android SDK 36. A local computer is not required to obtain its APK artifacts.

The workflow keeps build logs, host checks, Kotlin unit results and Android lint reports. An APK artifact may exist even when verification fails; inspect the run conclusion and reports before testing.

Public-repository CI debug signing keys are temporary. Final owner-delivered test APKs should be signed with an owner-private key kept outside Git and public CI caches. Export OTE restore points through SAF before any uninstall or data clear.

The workflow can also be started from Actions > OTE Android build > Run workflow.
The owner has confirmed the account-level message “GitHub Actions is currently
disabled for your account. Please reach out to GitHub Support for assistance.”
This restriction requires GitHub Support; changing repository workflow settings
does not resolve it. No GitHub Actions run has built an APK for this project.

`codemagic.yaml` now provides an independent, manual Android build using the same
repository. See [phone setup instructions](CODEMAGIC.md). It is configured for
Personal-plan M2 machines and does not enable paid billing or automatic builds.
The owner subsequently supplied a Codemagic log showing a successful debug APK
build and APK Signature Scheme v2 verification. The verification phase failed
during JUnit initialization of RootManagerTest. The test-signature correction
is awaiting a fresh build; this does not establish a fully passing pipeline or
Android device validation. See `verification/codemagic-first-build.json`.
