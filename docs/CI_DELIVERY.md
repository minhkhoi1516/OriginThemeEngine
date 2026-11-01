# GitHub-hosted Android builds

The Android workflow builds on GitHub-hosted Linux with JDK 17 and Android SDK 36. A local computer is not required to obtain its APK artifacts.

The workflow keeps build logs, host checks, Kotlin unit results and Android lint reports. An APK artifact may exist even when verification fails; inspect the run conclusion and reports before testing.

Public-repository CI debug signing keys are temporary. Final owner-delivered test APKs should be signed with an owner-private key kept outside Git and public CI caches. Export OTE restore points through SAF before any uninstall or data clear.

The workflow can also be started from Actions > OTE Android build > Run workflow. If Actions is disabled by account or repository policy, the repository owner must enable it; this source commit does not bypass that policy.
