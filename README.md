# Hyundai Pleos

## Description
Android application for driver profile mangement and vehicle health monitoring: warnings, distance, range, battery, GPS-tagged incidents, and comfort modes. Vehicle data and controls use the Pleos Vehicle SDK.

## Context
Developped for Hyundai Motor Group Vehicle App Development Contest.

## Moduless

- `automotive`: embedded Android Automotive OS application.
- `mobile`: Android mobile application.
- `shared`: shared in-car application components.

The `automotive` and `mobile` modules currently use the same `applicationId` (`com.example.hyundaipleos`), so they cannot be installed side by side under that ID.

## Required versions

- Android Studio compatible with Android Gradle Plugin **9.4.1**
- JDK **17 or newer** to run Gradle
- Gradle Wrapper **9.6.0** (included)
- Android SDK Platform **37** (compile/target SDK)
- Minimum Android SDK: **28**
- Kotlin **2.2.10**, Jetpack Compose BOM **2026.02.01**
- Pleos Vehicle SDK **2.3.2**

An internet connection is required for the first sync to download Gradle and dependencies, including artifacts from the Pleos Maven repository configured in `settings.gradle.kts`.

## Open and run

1. Open the project root in Android Studio and let Gradle sync.
2. Select `automotive` or `mobile` from the run configuration list.
3. Run on a compatible device. Live data and Pleos controls require a compatible vehicle or Pleos environment and the relevant vehicle/GPS permissions.

## Build APKs

From the project root:

```bash
# Test APKs
./gradlew :automotive:assembleDebug
./gradlew :mobile:assembleDebug

# Release builds (unsigned unless signing is configured)
./gradlew :automotive:assembleRelease
./gradlew :mobile:assembleRelease
```

On Windows, replace `./gradlew` with `gradlew.bat`. APKs are written to `<module>/build/outputs/apk/<variant>/`. To distribute an APK to users, create a **signed release** with **Build → Generate Signed Bundle / APK…** in Android Studio and keep the signing key safe.

## Pleos setup

The Pleos Maven repository is declared in `settings.gradle.kts`, and the SDK dependency is declared in both app modules. `VehicleDataController` reads vehicle properties, applies supported controls, and collects location for incidents. Live access depends on the permissions granted by the Pleos environment and the features supported by the vehicle.

## Screenshots

<img width="2560" height="1440" alt="a" src="https://github.com/user-attachments/assets/fa3e71ef-3c1f-4d45-b420-d2240a8e25a3" />
<img width="2560" height="1440" alt="b" src="https://github.com/user-attachments/assets/051b8e26-39bf-4334-a8f8-fd60e657a2c0" />
<img width="2560" height="1440" alt="c" src="https://github.com/user-attachments/assets/d9bf886d-e2c1-4949-b1f3-03925fea6780" />
<img width="2560" height="1440" alt="d" src="https://github.com/user-attachments/assets/9a4b6894-bd1a-4fd3-aafd-6185f10573a4" />



