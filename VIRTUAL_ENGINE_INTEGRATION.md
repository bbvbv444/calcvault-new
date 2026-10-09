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
