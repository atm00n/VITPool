# VITPool Android APK

Native Android app for real VITPool users. The package name is fixed as
`com.vitpool.app`; do not change it after users install an APK, or updates will
require uninstalling the previous app.

## Required Local Files

These files are intentionally not committed:

- `android/app/google-services.json`: download from Firebase after registering
  Android app `com.vitpool.app`.
- `android/keystore.properties`: copy from `keystore.properties.example` and
  point it at the release keystore.
- `android/release/vitpool-release.jks`: private signing keystore.

Generate a release keystore:

```powershell
keytool -genkeypair -v -keystore android\release\vitpool-release.jks -alias vitpool -keyalg RSA -keysize 4096 -validity 10000
```

Verify release-only inputs:

```powershell
powershell -ExecutionPolicy Bypass -File android\scripts\verify-release-inputs.ps1
```

Build APKs from Android Studio or from a Gradle-enabled shell:

```powershell
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Install the real-user release APK over USB debugging:

```powershell
adb install -r android\app\build\outputs\apk\release\app-release.apk
# or:
powershell -ExecutionPolicy Bypass -File android\scripts\install-release-apk.ps1
```

The same keystore must sign every future APK update.

## Firebase Backend (Spark Plan)

The Android APK is Spark-plan compatible. It uses Firebase Auth, Firestore
realtime listeners, and client-side Firestore transactions. Cloud Functions are
not required for the Spark build.

Deploy Firestore rules and indexes from `firebase/` after copying
`.firebaserc.example` to `.firebaserc` and setting your real Firebase project
ID:

```powershell
cd ..
npm run firebase:cli -- deploy --only firestore
```

For production hardening later, move matching and pool writes back behind
Cloud Functions on Blaze.
