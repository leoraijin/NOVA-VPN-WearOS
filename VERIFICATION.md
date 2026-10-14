# Verification — 0.3.1 (2026-10-14)

- `Build-WearOS.ps1 testDebugUnitTest assembleDebug --offline --no-daemon`: successful.
- 11 JUnit tests: 0 failures / 0 errors. Tests cover Reality defaults, explicit TLS preservation, TUN adaptation, bootstrap DNS, retained routes, neutral active state, authenticated loopback probe routing, response validation and cancellation.
- Both ARM64 and ARMv7 APK signatures verified with Android SDK apksigner. Signer matches Android phone 0.2.17 debug APK (SHA-256 certificate `7abeb400a6f16c0adf6c127027f2bdd205ce79f558c53f631d41083ecca24626`). Matching package/signature is a prerequisite, not proof of actual pairing.
- ARM64 APK manifest inspected with aapt: version 0.3.1/code 5, min SDK 26, target SDK 35, required watch feature, ARM64 native library.
- Existing PC VPN was not stopped or changed. No physical watch or paired-device traffic test was performed; Watch Ultra 2025 / One UI 8 still needs installation, consent, optional transfer, independent tunnel, Wi-Fi/LTE transition and real website testing.
- Build warnings: Android SDK XML/tool version mismatch, deprecated legacy notification constructor, native libbox symbols not stripped. Build completed successfully.

The one-shot HTTPS probe checks a selected concrete proxy, not every destination or app route. A non-answer is reported as unconfirmed, not as proof that the whole VPN is broken. Debug APKs are experimental, not production-signed releases.
