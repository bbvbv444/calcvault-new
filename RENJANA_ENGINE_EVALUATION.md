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
