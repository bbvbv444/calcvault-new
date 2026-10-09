# CalcVault existing-source Gradle build test

This is a separate experiment branch and a separate Gradle root. It maps the existing `app/src/main` Java, manifest, resources, AIDL, assets, and native-library folders into a disposable test build.

It does not replace the CodeAssist/native project, does not change `main`, and does not edit `CloneRuntime.java`. The test APK uses a different application ID so it cannot overwrite the existing CalcVault installation.

A successful build only proves that Gradle can package the existing source. It does **not** mean virtual app cloning works or that PrismSpace has been integrated. Any compile errors should be reviewed before making further changes.
