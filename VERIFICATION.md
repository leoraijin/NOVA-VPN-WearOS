# Verification — 0.3.8 experimental (2026-10-14)

Debug ARMv7/ARM64 builds and 45 unit tests passed, with zero failures or errors. The gateway test accepts only an HTTP 200 Cloudflare trace containing an address field. It selects a Bluetooth companion `Network`, validates TLS normally, uses bounded timeouts, and stores or logs no response body or public IP.

Physical test on Samsung Watch Ultra SM-L705F / Android 16 / One UI 8 paired with Samsung S24: Bluetooth companion network connected. HTTPS from the watch over the selected Bluetooth network succeeded. The returned public address was compared locally by SHA-256 with Chrome's trace on the phone while NOVA VPN was active; it matched and the raw address was not retained. The phone VPN remained active. The watch's system default remained Wi-Fi throughout the test.

This proves an app-selected Bluetooth path through the phone VPN for this request. It does not prove other watch apps use that network. The tested firmware has no standard `vpn_management` service, so an independent system VPN tunnel on this watch remains unavailable through the ordinary app API. No Wi-Fi/LTE setting was changed and no PC VPN was interrupted. Debug builds are experimental, not production-signed.

## Previous verification — 0.3.7 experimental
Debug build and 44 unit tests passed with zero failures/errors. The launcher assets, permission stage diagnostics, source-level VPN permission checks and profile-preserving failure path were verified. Physical Watch Ultra testing had not yet taken place for 0.3.7.

## Previous verification — 0.3.4 experimental

Offline debug build and 28 unit tests passed. Profile protocol tests cover exact UTF-8/escaped JSON round-trip, invalid/oversized profiles, request-matched acknowledgements and unknown/stale replies. Packaged ARMv7 resources include the advertised profile capability. The phone and ARMv7 watch APKs have the same package ID and signing certificate as their previous builds.

The receiver saves atomically, reads the encrypted profile back before acknowledging, and notifies the local visible UI. The phone waits at most 25 seconds and requires acknowledgement from the selected node. Invalid messages are rejected and legacy raw phone messages remain accepted. No profile is transferred automatically and no VPN starts on transfer.

No physical devices are connected to ADB. Real Data Layer discovery/delivery, process lifecycle, Bluetooth interruptions, Keystore failures and the reported watch VPN crash require hardware verification. These tests establish the wire format and packaged prerequisites, not real paired-device delivery or working watch VPN.

## Previous verification — 0.3.3 diagnostic

The reported device crash cannot be diagnosed from a screenshot or a successful build. `adb devices -l` returned no connected devices. No physical Watch Ultra test, native crash reproduction, or VPN traffic test was performed for this version.

Implemented private-process service isolation and event-driven IPC; a fixed startup phase and recent exit category are displayed on service disconnection. System traces, native tombstones and exception messages are deliberately not exported because they may contain secrets. Source/build/manifest/unit checks do not prove that the user's VPN works. This release is diagnostic, not a certified stable VPN fix.

Offline debug build and 23 unit tests passed. New tests cover local state relay, preserved failure feedback and the non-exported private-process manifest contract. Actual Binder death, system VPN permission UI and ARMv7 native execution still require device testing.

## Previous verification — 0.3.2

- Offline debug build succeeded; 20 unit tests passed. New tests exercise missing/corrupt profiles, unsupported consent UI, security/start/stop exceptions and unchanged valid profile delivery.
- Command reply tests cover request/node matching, stale responses, invalid JSON, oversized payloads and unknown states. UI command timeout is bounded to 12 seconds and has no polling.
- ARM64 APK signature verified; matches the prior phone/watch debug signer. Both ARM watch APKs built.
- Physical watch crash logs were not available. This fixes identified unsafe handlers; it does not establish the exact exception seen on the user's watch or certify independent VPN support on Watch Ultra / One UI 8. Paired-device command delivery and real traffic still require hardware testing.
- Update both watch 0.3.2 and phone 0.2.20 for application acknowledgements. Legacy phone clients can still toggle, but will time out without an acknowledgement.

## Previous verification — 0.3.1

- `Build-WearOS.ps1 testDebugUnitTest assembleDebug --offline --no-daemon`: successful.
- 11 JUnit tests: 0 failures / 0 errors. Tests cover Reality defaults, explicit TLS preservation, TUN adaptation, bootstrap DNS, retained routes, neutral active state, authenticated loopback probe routing, response validation and cancellation.
- Both ARM64 and ARMv7 APK signatures verified with Android SDK apksigner. Signer matches Android phone 0.2.17 debug APK (SHA-256 certificate `7abeb400a6f16c0adf6c127027f2bdd205ce79f558c53f631d41083ecca24626`). Matching package/signature is a prerequisite, not proof of actual pairing.
- ARM64 APK manifest inspected with aapt: version 0.3.1/code 5, min SDK 26, target SDK 35, required watch feature, ARM64 native library.
- Existing PC VPN was not stopped or changed. No physical watch or paired-device traffic test was performed; Watch Ultra 2025 / One UI 8 still needs installation, consent, optional transfer, independent tunnel, Wi-Fi/LTE transition and real website testing.
- Build warnings: Android SDK XML/tool version mismatch, deprecated legacy notification constructor, native libbox symbols not stripped. Build completed successfully.

The one-shot HTTPS probe checks a selected concrete proxy, not every destination or app route. A non-answer is reported as unconfirmed, not as proof that the whole VPN is broken. Debug APKs are experimental, not production-signed releases.
