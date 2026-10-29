# Profile country flags

The profile card uses the optional `profileOwner.mccMnc` value returned by the eUICC. It decodes the standard three-byte PLMN, validates its decimal digits and optional MNC filler, and maps the MCC to an ISO country/region code. The UI uses the same metadata in the profile details and screen-reader description.

This identifies the home network region, not a roaming plan's destination or coverage. ICCID prefixes, profile nicknames, phone locale and current registration are not used to guess a country. Missing, malformed, unmapped or international MCCs use a globe. The preview has explicitly marked example CN, JP and GB metadata.

The native reader requests the optional owner field along with the existing profile fields. A card that rejects this request is retried with the original basic-list request. No owner data is required for profile management to continue.

The MCC table is derived from [AOSP MccTable.java](https://android.googlesource.com/platform/frameworks/opt/telephony/+/307806916047d68377ff74674f95ca499243e068/src/java/com/android/internal/telephony/MccTable.java), commit `307806916047d68377ff74674f95ca499243e068`, under Apache-2.0. Shared MCCs use the table's region and are not individually resolved by carrier MNC.

Flag images are rendered from the 4:3 SVGs in [flag-icons](https://github.com/lipis/flag-icons), commit `ac7e0eb3259eb62aa6100c435607e2fe03896a97`, under the MIT License. They are bundled locally and do not require network access. Their license and the AOSP license are included in `app-common/src/main/assets/licenses/` and in the APK.
