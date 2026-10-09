# Virtualization engine safety audit — 2026-10-09

## Scope

This is a source-code review of the pinned PrismSpace revision `990a414f4e0eea68ef50230cb09c3b02ec6d700b`. It is not a dynamic security test, and it does not establish that any app runs correctly inside CalcVault.

## Findings

### 1. The library is not a drop-in AAR

The `Pcore` module is an Android library that enables AIDL and prefab/native build integration, uses Java/Kotlin 17, and depends on separate `:black-reflection` and `:compiler` modules, coroutines, AppCompat, TOML parsing, and FreeReflection. The sample app has its own `Application` subclass that calls framework lifecycle hooks and `AppManager` during startup. Adding only the AAR to CalcVault would omit important setup.

### 2. Crash reporting has two distinct paths

The sample app's `CrashLogSubmitterImpl` reads pending engine crash logs and sends a scrubbed version to Firebase Crashlytics. Its regular expressions redact selected tokens, paths, and email addresses, but this does not guarantee that all personal or sensitive data is removed. The sample app calls this submitter asynchronously during startup.

The library's `LogSender.send(...)` currently returns without uploading logs, and its source logs that log upload is disabled. That is a useful safeguard in this revision, but it does not replace a complete review of every network/reporting path.

### 3. Global exception handlers change app-wide behavior

The `SimpleCrashFix` class installs a default uncaught-exception handler at class-load time. For several selected exception categories, the handler logs the issue and returns without delegating to the previous handler. That can suppress normal crash handling and leave a process in an unreliable state. The same class also attempts a ContextWrapper hook.

This is a significant host-app integration risk: a virtualization library should not be allowed to silently change CalcVault's global exception behavior without an explicit design and on-device testing.

### 4. Manifest and framework-hook footprint remains large

The already-built AAR includes a very large merged manifest with numerous permissions and proxy components, including exported components. Its manifest also allows cleartext traffic. None of these settings should be copied wholesale into CalcVault. A deliberate manifest allowlist and network-policy review would be required.

## Decision

**Do not integrate the PrismSpace AAR into CalcVault yet.** The isolated build and unit tests passing only prove that the candidate source can be built in CI. They do not prove it is safe, compatible with CodeAssist, or able to launch an app inside CalcVault.

## Safe next acceptance gate

Before any integration:

1. Review every crash handler, logging destination, permission, exported component, and network path.
2. Confirm the host build can include the necessary Kotlin, AIDL, native libraries, dependencies, and application-startup hooks without replacing CalcVault's existing startup behavior.
3. Keep the integration in a separate branch.
4. Use a harmless test APK and verify its first screen and interaction inside CalcVault.
5. Verify copied-app data separation and deletion behavior; keep the original app installed throughout testing.

No production app source, `main`, or `CloneRuntime.java` was changed as part of this audit.
