# CalcVault virtual-app engine integration status

## Current state

The current virtual runtime is a preparation prototype, not a complete Android app virtualization engine. It can copy an APK into CalcVault's private files, inspect it, create a per-copy directory, and resolve a launch Activity class. It cannot attach that Activity to Android's ActivityThread or execute the app lifecycle.

The Open Copy action intentionally does not send the APK to Android's package installer. Until a real runtime is integrated, it displays a clear message instead of claiming the copy is running.

## Candidate reviewed: TeguFy/blackbox-android

Source: https://github.com/TeguFy/blackbox-android

The project describes itself as a no-root virtualization engine. It is source code rather than a verified ready-to-use AAR in this repository. The repository tree contains native/AIDL source files, and the only published release asset found was a standalone debug APK, not a reusable AAR. Its Bcore module requires an Android Gradle build, NDK, AIDL, reflection hooks, proxy components, and Java 21 configuration. This CalcVault CodeAssist module currently declares Java 8 and uses `app/module.toml`, so dropping a few Java files into the existing source tree would not integrate the engine.

### Security review required before importing

The candidate has crash-reporting code that calls `BlackBoxCore.sendLogs(...)` from a global uncaught-exception handler. Before embedding this code in CalcVault, the logging/reporting path and all network destinations must be reviewed and disabled unless explicitly approved. Do not include this candidate as-is in a user-facing build.

## Decision from the next engine-source check

The product name “Hider No Root” refers to an end-user app, not a verified developer SDK that CalcVault can call. I did not verify an official public source repository or embeddable engine package for that exact product, so CalcVault must not pretend that app itself can be dropped into the project as a library.

The BlackBox family remains a possible source-based route, but its core requires a conventional Android Gradle build, Java/Kotlin and AIDL integration, native NDK libraries, and framework hooks. A similarly named Maven artifact is not proof of a compatible app-virtualization engine; package metadata and implementation must match before use. Do not add a random AAR based only on its name.

### Gate before engine code is imported

- Identify a specific engine source revision and confirm its license and all network/reporting behavior.
- Confirm whether the current CodeAssist project can package its AIDL and native libraries. If it cannot, the project needs a separately planned Gradle-based integration/build path before engine source is imported.
- Keep engine integration isolated from the existing calculator/vault features.
- Do not turn on any device-spoofing, hidden-hook, or anti-detection extras merely to make the first harmless test app launch.
- First acceptance test: a harmless test APK displays its real first screen inside CalcVault; basic interaction works; its data stays separate; deleting the virtual copy removes only that copy; the original app remains installed.

## Second candidate check: legacy VirtualApp

Repository reviewed: https://github.com/lockseal/VirtualApp_aslody

This is a historical fork of asLody's VirtualApp. Its README says the public GitHub source stopped being updated in December 2017 and that the actively updated commercial source is separate. The repository has a large native/AIDL surface and no GitHub releases found in the release listing checked. It is therefore **not selected for direct integration**: its age and lack of a current maintained release create substantial compatibility and security uncertainty on modern Android.

This check does not establish that no suitable engine exists anywhere. It means the two candidates checked so far are not ready to drop into this CodeAssist project safely. The next viable path is to choose a maintained engine with auditable source and a build path that can actually produce a compatible library, then prove it with a harmless test APK before connecting it to CalcVault.

## Third candidate check: PrismSpace

Repository reviewed: https://github.com/mhmdwaelanwr/PrismSpace

PrismSpace is a more recent research project with a separate `Pcore` Android library module, AIDL, Java/Kotlin, and native C/C++ code. Its repository has an Apache-2.0 LICENSE and a GitHub Actions Android build workflow. Its own README explicitly describes it as experimental and says it should not be treated as a security boundary until independently reviewed. The inspected build files require JDK 17, Android SDK 35, and NDK 29.0.13846066; `Pcore` uses Java/Kotlin 17 and native build integration.

**Decision: promising for a separate build experiment, not ready to copy into CalcVault.** It is not a prebuilt AAR, it is a full research codebase with substantial AIDL/native/framework work, and CalcVault currently uses CodeAssist's `app/module.toml` setup with Java 8. The host app also declares a Firebase Crashlytics dependency, which must be reviewed for data collection before reusing any of this code. I have since verified that PrismSpace's latest listed GitHub Actions run completed successfully and that its build job passed the configured tests/probes and the requested debug APK, test APK, and `Pcore` build tasks. This is CI evidence only: I have not personally built it, obtained a build artifact, or verified a real clone launch on a device, and I have not imported its code into CalcVault.

### CI/build-output check (follow-up)

I inspected PrismSpace's current `.github/workflows/android.yml`. The workflow is configured to run on pushes and pull requests to `main`, and manually, using JDK 17, Android SDK 35, and NDK `29.0.13846066`. It runs the engine unit tests and native bring-up probes, then requests these build tasks: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:Pcore:assembleDebug`. The latest listed run (run 28, updated 2026-09-16) completed with a `success` conclusion. Its build job reports success for the engine helper tests, native bring-up probes, and the step that builds the debug APK, test APK, and `Pcore` module. However, the run has no published workflow artifacts, and the GitHub Releases page currently returns no published releases. So the source has passed its own CI build tasks, but there is no verified ready-made engine package available to download from the run or Releases. I have not obtained or tested the generated AAR/APK myself.

### Crash-log transmission finding

A follow-up source search found a concrete reporting path in PrismSpace: `App.kt` calls `CrashLogSubmitter.submitPendingCrashLogs(...)` during app startup, and `CrashLogSubmitterImpl.kt` reads pending engine crash `.log` files, scrubs some patterns, and passes the contents to `FirebaseCrashlytics.recordException(...)` with package and timestamp keys. That means this code is designed to send pending engine crash-log content through Firebase Crashlytics; scrubbing is not a guarantee that every sensitive value is removed. Before any reuse, this path must be excluded or explicitly redesigned and reviewed. I have not run the app or observed a network transmission, so this is a source-code finding, not a live traffic test.

### Safest next experiment

Build and inspect PrismSpace independently in its own disposable fork/workflow first, without changing CalcVault. Confirm the build output and license/security posture, then determine whether its engine module can be exported as a compatible library and what host initialization, proxy components, manifest declarations, native ABIs, and minimum SDK it requires. Only then plan a dedicated integration branch. Do not merge its full app or its permissions directly into CalcVault.

## Required integration sequence

1. Confirm CodeAssist can build and package the candidate engine's Java/AIDL and JNI libraries in this project format, or use a verified compatible prebuilt AAR whose source and license can be audited.
2. Audit and disable unsolicited crash/log uploads and inspect all network calls.
3. Integrate the engine's Application initialization, proxy activities/services/providers, reflection hooks, and native libraries.
4. Replace the current preparation-only launch bridge with the engine's documented virtual install and launch APIs.
5. Route the existing saved APK into the engine's private virtual package store and maintain a stable mapping between CalcVault clone IDs and engine package/user IDs.
6. Keep each clone's data isolated; ensure Delete Copy removes only that virtual instance.
7. Test on-device with a harmless test APK first, then test app restart, permission handling, storage, WebView, notifications, and failure recovery.
8. Only after tests pass, consider testing original-app removal. Never make uninstalling the original a prerequisite for the first test.

## Safety rules

- Do not modify `CloneRuntime.java` without explicit permission.
- Do not change the default `main` branch for this work.
- Do not claim a copied app runs until its actual UI is displayed and basic interactions work on a device.
- Do not tell the user to uninstall the original app before independent-copy behavior has been demonstrated.
- A successful CodeAssist sync is not proof of a successful build or runtime test.


## Isolated AAR build workflow added

A separate GitHub Actions workflow has now been added at `.github/workflows/build-prismspace-engine.yml` on the `clone-engine-prototype` branch. It checks out PrismSpace at the exact reviewed source commit `990a414f4e0eea68ef50230cb09c3b02ec6d700b`, uses JDK 17 / Android SDK 35 / NDK 29, runs the `Pcore` unit tests and builds only the `Pcore` debug AAR. If successful, it uploads the AAR as a short-lived (7-day) workflow artifact for inspection. The workflow does not add PrismSpace source or dependencies to CalcVault's app, does not change `CloneRuntime.java`, and does not alter `main`.

The workflow completed successfully on 2026-10-09 (run 37940675567). Both :Pcore:testDebugUnitTest and :Pcore:assembleDebug completed, and GitHub uploaded the prismspace-pcore-debug-aar artifact (2,704,028 bytes; expires 2026-10-16). This proves the pinned Pcore module builds in CI; it does not prove the library is safe or that a cloned app runs inside CalcVault.


### CalcVault isolated AAR inspection (2026-10-09)

Downloaded and inspected the workflow artifact without adding it to CalcVault. The ZIP contains Pcore-debug.aar (2,712,294 bytes), classes.jar, a 1,258,938-byte Android manifest, and native libraries for ARM64 and ARMv7. The library exposes PrismEngineFacade methods for engine initialization, virtual install, launch, app data clearing, stopping, and status checks, so it has a real engine-facing API surface rather than being only a placeholder.

**Integration blockers identified:**
- The AAR manifest contains 314 declared permissions, 151 activities, 101 services, and 52 providers. Many proxy components are marked exported. Merging this manifest directly into CalcVault would create an unnecessarily broad permission/component surface and could cause conflicts or security exposure.
- The manifest sets android:usesCleartextTraffic="true", which permits unencrypted network traffic for the app unless overridden. This must be understood and tightened before any integration.
- The library uses Android framework hooks and native code; an AAR by itself is not sufficient. We still need to validate its runtime initialization, manifest-merger strategy, dependency requirements, and behavior on the target phone.
- The source has a previously identified startup crash-log reporting path through Firebase Crashlytics. This AAR was not dynamically tested for network traffic, and we must not assume that sensitive log data is fully scrubbed.
- The current CalcVault CodeAssist project declares Java 8 and uses app/module.toml; this AAR was built with JDK 17/SDK 35 and may require Kotlin/AndroidX dependencies and manifest/component wiring not yet present in CalcVault.

**Decision: do not integrate this raw AAR yet.** Keep it as an isolated candidate and do not replace the current runtime or merge its manifest wholesale. Next, inspect its build metadata and source initialization path to identify the smallest safe integration surface, and test whether the current project can support that surface. A real on-device test with a harmless APK remains mandatory before claiming clone functionality. The original app must remain installed during testing.


### Initialization and dependency audit follow-up

The pinned Pcore source shows a deeper host-app requirement: its sample app defines a custom `Application` class whose `attachBaseContext()` calls `PrismSpaceCore` lifecycle hooks and `AppManager`, while `onCreate()` pre-warms the engine. The facade's `initEngine()` alone is not the complete setup. CalcVault would need an audited equivalent startup sequence and the engine's proxy components and internal services, while preserving CalcVault's existing Application behavior. Blindly adding the AAR is not enough.

The Pcore module is an Android library using Kotlin, Android AIDL, NDK build integration, Java 17/Kotlin 17, and project modules `:black-reflection` and `:compiler`, plus coroutines, AppCompat, TOML parsing, and FreeReflection. These are additional compatibility/build requirements beyond simply copying the AAR into a `libs` folder. Its source also installs crash-monitoring and exception-handling helpers during `PrismSpaceCore` class loading; those code paths need review before enabling it in the host app.

**Next technical gate:** determine whether CalcVault's current CodeAssist `app/module.toml` project can safely adopt a Gradle-based Kotlin/AIDL/JNI library and required Application/manifest setup without disrupting vault features. Until that is verified, do not add the AAR to the app or change existing runtime code.


### Current CodeAssist project-format compatibility check (2026-10-09)

Checked the repository tree and `.platform/workspace.json`: CalcVault is configured with `buildSystem: native`, a single `app` module, and `app/module.toml`; there is no Gradle root build file or Gradle wrapper in the branch. PrismSpace Pcore is a Gradle Android library that depends on two other Gradle modules and Kotlin/AIDL/JNI build steps. Therefore, the current workspace cannot consume the built AAR through the ordinary Gradle dependency workflow as-is, and the CI workflow that builds Pcore does not build CalcVault.

**Recommendation:** don't convert the existing CodeAssist project in-place just to test this candidate. That could break the current app and vault features. If a future experiment is needed, first make a separate branch from `clone-engine-prototype` and prototype a minimal Gradle-based host/build path without touching the existing `app/module.toml` project; verify whether CodeAssist can open/build that path before migrating anything. The other route is to identify an engine that explicitly supports this native CodeAssist project format, but no such drop-in engine has been verified yet.
