# CalcVault Virtual-App Engine Evaluation — Renjana

Date: 2026-10-10

## Status

**Not approved for direct integration.** This is a source review, not a successful build or device test. No engine code has been copied into CalcVault.

## What was reviewed

Public repository: https://github.com/josskixg/renjana

Files reviewed:
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `RenjanaApplication.kt`
- `WrapperActivity.kt`
- `InstanceManager.kt`
- `PlayIntegrityBypass.kt`
- `SignatureSpoof.kt`
- `LogcatCapture.kt`
- `ROADMAP.md`

## Findings

1. **It is a complete Kotlin/Compose application, not a drop-in library.** It targets Android API 29+, uses Java/Kotlin 17, Compose, KSP, Room and the Pine hook framework. CalcVault's current CodeAssist project uses a native module definition and Java 8, so integration would require a major build-system and application-lifecycle migration.
2. **The project includes security-evasion features that must not be imported into CalcVault.** Its source explicitly describes Play Integrity verdict spoofing, signature spoofing and Frida/detection evasion. This evaluation does not approve or integrate those modules.
3. **Its manifest is not safe to merge wholesale.** It requests broad package visibility, external-storage management, install-package permission, overlay permission, foreground services and cleartext traffic. Each permission and network setting would need a separate necessity and privacy review.
4. **The wrapper activity itself says it provides zero isolation and is deprecated.** That means the existence of a wrapper class alone is not proof of a safe working guest-app runtime; the stub lifecycle and every relevant Android service would need independent testing.
5. **The documentation is inconsistent.** The README advertises completed features while ROADMAP.md describes planning-stage virtualization and lists major unresolved challenges. These claims have not been independently verified.
6. **Build and runtime are not verified here.** No Renjana APK was built or installed as part of this review, and no cloned guest app was demonstrated.

## Decision

Do not merge Renjana's app, manifest, hooks or dependencies into CalcVault as-is. Do not change `main` or `CloneRuntime.java`.

A responsible next step is a clean, isolated feasibility spike on this branch: first verify the candidate's build in a no-secrets test environment, then test only a harmless sample guest app and basic storage separation. Keep anti-detection, signature spoofing and integrity-bypass modules excluded. Do not call cloning complete until a guest app actually launches and its data is demonstrably isolated.

## Current CalcVault evidence

The separate diagnostic workflow at https://github.com/bbvbv444/calcvault-new/actions/runs/37960548325 successfully built, installed and launched a *diagnostic CalcVault package* in a GitHub Android emulator. That validates the diagnostic build path only; it does not validate Renjana or the actual CalcVault guest-app runtime.


## CalcVault prototype review on this branch

The current prototype files were also inspected:

- `VirtualActivityExecutor.java` explicitly resolves the launch Activity class but deliberately stops before calling third-party Activity lifecycle methods.
- `VirtualActivityBridge.java` says Android framework attachment is required before a foreign Activity can be hosted.
- `VirtualActivitySession.java` prepares the copied APK, class loader, application object and virtual context, but preparation is not proof of a working app session.
- `VirtualActivityProxy.java` displays “Virtual runtime prepared”; that status must not be confused with a successfully running guest app.
- `VirtualAppContainer.java` creates private directories and keeps the APK copy; it does not install a guest app into Android's package manager.

Therefore the blocker is not simply adding a library dependency. A real guest runtime needs a compatible Activity/component lifecycle and Android service/resource/package handling, followed by device tests. A normal JVM class-loader test cannot prove this works.

## Acceptance tests before a release claim

- Build the isolated CalcVault branch successfully.
- Install and launch the APK on an emulator and a real supported Android device.
- Import a harmless sample APK and launch its actual UI from inside CalcVault.
- Confirm the sample's files/preferences are private to its instance and do not leak into another instance.
- Confirm delete-copy removes only that instance's private data and never uninstalls the original app.
- Test process death, relaunch, back navigation and permissions.
- Keep all anti-detection, signature spoofing and integrity-bypass code excluded.
- Keep `main` and `CloneRuntime.java` unchanged until the user explicitly approves any future merge.

## Separate candidate build audit — VirtualApp_16

This section records a separate CI job and must not be confused with the Renjana source review above.

Candidate repository: https://github.com/f0restw0w/VirtualApp_16  
Build workflow: https://github.com/bbvbv444/calcvault-new/actions/runs/38054945439  
Workflow artifact: `virtualapp-candidate-debug-apks` (temporary CI artifact; expires 2026-10-15).

### What the successful workflow proves

- GitHub Actions checked out the candidate repository into a temporary `candidate/` directory.
- It installed JDK 17, Android SDK API 34 and NDK 21.4.7075529.
- It ran the candidate's `./gradlew --no-daemon assembleDebug` and uploaded the resulting APK files.
- The workflow did not copy candidate engine source into CalcVault and did not change CalcVault's `CloneRuntime.java`.

### What it does not prove

- The APK has **not** been inspected or launched on a device as part of this audit.
- No sample guest app has been demonstrated running inside this engine in our test.
- No independent data-isolation, delete-copy, process-death or relaunch test has been performed.
- A green build is not evidence that this engine can be integrated into CalcVault as-is.

### Integration and compatibility risks found in source

- VirtualApp_16 is a full host app plus a substantial Java/native engine library, not a ready-to-drop-in CalcVault plug-in.
- Its README warns that it is highly unstable and not recommended for production.
- Its own `docs/KNOWN-ISSUES.md` records issues including hidden-API bypass failures on newer Android versions, background activity launch restrictions, signature verification failures for some apps, SELinux denials, and incomplete Google Play Services behavior.
- Its app manifest requests broad permissions including package visibility and all-files access. These must not be copied into CalcVault wholesale; each permission needs a justified review.
- Its core relies on hidden Android APIs and Java/native hooks, so device and OS-version testing is mandatory.

### Current decision

Keep VirtualApp_16 as an unintegrated research candidate. Do not merge its app, manifest, permissions, hooks or native libraries into CalcVault yet. The next safe engineering task is a read-only integration map of its initialization, service/process proxies, manifest components and native ABI files against CalcVault's current app structure. Only after that review should we decide whether a minimal isolated integration experiment is technically reasonable.

The acceptance tests above remain required. Do not claim cloning works until a harmless guest APK actually launches from CalcVault and separate-instance data isolation is demonstrated.


## VirtualApp_16 integration feasibility — deeper source audit

Reviewed at pinned candidate commit `b3c634ad7941765df3da84a207aca94b7861afae`:
- `docs/KNOWN-ISSUES.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILD.md`
- root `build.gradle`, `settings.gradle`
- `app/build.gradle`, `lib/build.gradle`
- host and library Android manifests
- `XApp.java` startup sequence

### Findings

1. **The host application is the engine bootstrap.** The candidate's `XApp.attachBaseContext()` sets engine flags and calls `VirtualCore.get().startup(base)`; `onCreate()` then calls `VirtualCore.initialize(...)`. Adding only the `lib` directory to CalcVault would not initialize the runtime or provide its host UI/services.
2. **It is a multi-module Gradle + native build, not compatible as a simple CodeAssist library drop-in.** The candidate uses root Gradle/Android Gradle Plugin 7.4.2, modules `:app` and `:lib`, JDK 17, NDK 21.4.7075529 and ndkBuild. CalcVault currently uses CodeAssist's `app/module.toml`, Java 8, compile SDK 36, min SDK 24 and target SDK 36. The project layouts/build tooling do not line up as a direct dependency.
3. **Native binaries are part of the runtime.** The candidate compiles `libva++.so` and hook/IO-redirection code for multiple ABIs. Copying Java sources alone would leave native entry points missing; copying native files without matching build and startup integration would also be unsafe.
4. **The manifests and component model are large.** The candidate declares its own Application, launcher/settings/install/share activities, receiver(s), virtual process/service infrastructure and broad permissions. Those cannot safely be pasted into CalcVault's existing manifest without a deliberate component-by-component merge and permission review.
5. **Known issues directly affect reliability on modern Android.** The candidate documentation calls out hidden-API bypass risk on Android 15–16, background launch restrictions on Android 14+, SELinux denials, some signature-check failures, dynamic-code-loading restrictions, and incomplete GMS push/Maps/Play Integrity support. Its stated successful emulator tests do not replace testing on the user's Android phone.
6. **CalcVault's current blocker remains framework attachment.** The prototype currently copies and inspects an APK and loads classes, but its execution boundary still reports that framework attachment is required. This is a fundamental runtime/lifecycle gap, not something a small launcher-only patch can resolve.

### Decision after deeper audit

**Do not embed VirtualApp_16 into the current CalcVault module yet.** A direct merge would require major changes to the app bootstrap, manifest, Gradle/module structure, native build and process/component architecture, risking existing calculator/vault features. The safer next step is to keep the candidate isolated and plan a minimal proof-of-concept in a separate test target before considering any CalcVault integration. No guest-app launch or data-isolation claim is approved until demonstrated in a test.

### User-impact / installation status

No new installation is requested at this stage. The candidate APK has only been built in GitHub Actions; this audit has not yet established that installing it would help solve the previous “App not installed” issue on the user's device. Keep CalcVault's main branch and `CloneRuntime.java` unchanged.
