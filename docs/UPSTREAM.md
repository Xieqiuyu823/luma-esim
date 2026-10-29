# Source provenance

OpenEUICC upstream: https://gitea.angry.im/PeterCxy/OpenEUICC

Base commit: `055d1e2cacb60e693e433cfbfa5fa977cac6c76b`

Native components:

- lpac base: `50cdf15ab40942b00f8819a1c2ca19997f1a6d34` (vendored so the local profile-owner metadata change is included in this repository)
- cJSON: `6d9f2443ab071f86e5d9b43025a40929ec41c46c`

Luma changes: original launcher mark, separate package identities, shared blue day/night theme, compact device/profile dashboard, read-only preview, profile detail sheet, switch confirmation, empty-state recovery, country flags, and regression tests. The existing 16 KiB native linker flag was moved after `CLEAR_VARS` so it is actually applied. Version 0.1.1 additionally reads optional profileOwner PLMN metadata through lpac/JNI, requests it using tagList with a basic-list fallback for older cards, and releases the original native profile list head after copying its data. Profile switching and downloading remain upstream.

Country flags and the offline MCC table have separate upstream sources and licenses; see [FLAGS.md](FLAGS.md). The modified lpac source is included directly in this repository and source archive; cJSON remains a submodule.

Design reference only: [NekokoLPA2](https://github.com/iebb/NekokoLPA2), especially reader/EID headers, compact cards, profile switches and highlighted active state. No NekokoLPA source, mascot, operator artwork, or backend code is included in this fork. The distributable APKs use the separately published [9eSIM Community Key](https://github.com/9esim/9eSIMCommunityKey); its keystore is not included in this source archive. It is a shared signing identity, not an endorsement or affiliation.
