# CalcVault isolated Gradle host spike

This folder is a disposable build-path experiment on branch `prismspace-gradle-host-spike`.

## Purpose

Confirm that GitHub Actions can build a minimal Android application from a separate Gradle project while leaving CalcVault's existing CodeAssist-native project untouched.

## Result

- GitHub Actions run: https://github.com/bbvbv444/calcvault-new/actions/runs/37942163616
- Result: successful.
- Output artifact: `calcvault-gradle-host-spike-debug-apk`
- Artifact expires after 7 days (2026-10-16).
- The APK is a tiny screen that says this is a build-path test only.

## What this proves

The minimal Android Gradle project can be built by the CI environment with JDK 17, Gradle 8.9, Android SDK 35, Java 8 source compatibility, and min SDK 24.

## What this does not prove

- It does not prove CodeAssist can open or build this Gradle project on the user's phone.
- It does not build the existing CalcVault app.
- It does not integrate PrismSpace or any other virtual-app engine.
- It does not run copied apps inside CalcVault.

## Safety boundaries

- Do not copy this folder over the existing `app` project.
- Do not merge this experiment into `main`.
- Do not modify `CloneRuntime.java` as part of this experiment.
- Keep original apps installed until a future virtual copy is proven to work independently on-device.
