# Validation — Luma eSIM 0.1.3

The system and unprivileged APKs were built in debug and release variants, together with debug and release Magisk modules, using JDK 21, Gradle 9.5.0, AGP 9.3.0, SDK 37 and NDK 26.1.10909125.

All APKs, including both APKs embedded in the Magisk modules, pass `apksigner verify`, use the public 9eSIM Community Key (SHA-1 `D1C0F48B370E74D4EA4770ED4C3CD70A3198D31F`), and pass 16 KB ZIP alignment checks. Package versionCode is 4 and versionName is 0.1.3 (with build suffixes for debug variants). The daily-use Magisk package is the release module. The source archive excludes the keystore and signing configuration.

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| app-common | 23 | 0 | 0 | 0 |
| app | 15 | 0 | 0 | 0 |
| app-unpriv | 1 | 0 | 0 | 0 |
| libs/lpac-jni | 1 | 0 | 0 | 0 |

The slot-resolution tests verify that only the switched logical slot is selected, MEP port indexes disambiguate active subscriptions, and missing or ambiguous mappings are skipped. Android 12 and later use the subscription-scoped privileged telephony API; older Android versions use the compatibility setter.

Robolectric uses simulated eUICCs. Physical APDU, modem refresh and roaming behavior remain to be verified on a target phone/card; device makers can differ in telephony behavior. The light/dark screenshots are rendered from the actual production UI in read-only preview mode.
