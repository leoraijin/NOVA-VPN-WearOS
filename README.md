# NOVA VPN for Wear OS

This Wear OS companion controls NOVA VPN on a paired Android phone. It does not provide a separate VPN tunnel for watch applications.

Current version: **0.3.9** (experimental debug builds). This build is a phone-VPN remote for Wear OS: it controls the VPN running on the paired phone. It cannot select individual watch apps for routing on firmware where the system VPN service is unavailable.

The previous build's local watch VPN controls have been removed from the app and manifest because the tested One UI 8 firmware lacks the VPN management service required to start them. The main screen now presents the phone control. Companion-network diagnostic code remains in the source but is not exposed by the main screen.

ARMv7 is the required variant for the owner's device where ARM64 does not run. On the tested Watch Ultra / One UI 8 firmware, Android's standard VPN management service is absent, so per-app routing of arbitrary watch apps cannot be provided by this ordinary app.

## NOVA VPN tile / Galaxy Wearable Cards

After updating and opening NOVA on the watch, add **NOVA VPN** using **Galaxy Wearable → Карточки → Добавить карточки** or the watch's own tile picker. The card opens NOVA's controls for the VPN running on the phone.

The tile is a static shortcut with no periodic network checks. It does not claim to show live phone VPN status. The picker preview is a static design asset, not a device screenshot.

### Phone VPN control

The main button controls the VPN running on the paired phone. One phone node receives the command; a 12-second timeout reports missing response rather than silent success. An acknowledgement means the phone handled the request, not that HTTPS already works. Android may require opening the phone app to grant VPN consent or allow starting it from the background.

## Network status and previous diagnostics

- Watch app control requires a paired phone running NOVA VPN Android 0.2.20 or later.
- The watch displays status received from the phone. A command acknowledgement confirms handling, not a successful HTTPS connection.
- Legacy watch VPN implementation remains in the source but its service is not registered in this build.
- Previous 0.3.8 physical diagnostic: on Watch Ultra / One UI 8 paired with S24, HTTPS over an explicitly selected Bluetooth companion network succeeded and returned the same external address as phone Chrome while NOVA VPN was active. The watch's default network remained Wi-Fi. This confirmed that app-selected Bluetooth request only, not protection of every watch app.

## License

NOVA source is licensed under GPL-3.0-only; see LICENSE and THIRD_PARTY_NOTICES.md. Third-party dependencies retain their own licenses.

## Phone/watch synchronization

- Uses the official Wearable Data Layer API for paired Wear OS + Android devices.
- Receives status updates only while the watch app is visible; it removes the listener in the background and fetches the latest saved state when reopened.
- Repeated identical statuses are not given a new timestamp, avoiding redundant urgent sync events and unnecessary watch wakeups.
- The phone VPN is controlled only on the phone; watch status is not represented as a local watch tunnel.
- Arbitrary per-app routing on the watch is unavailable on the tested firmware because its system VPN management service is missing.
- Profiles are sent only to paired Wear devices, with a 90 KiB transfer limit. Never include profile/config data in public source or APK metadata.
- Data Layer requires the phone and watch APKs to use the same application ID and signing certificate. This project intentionally uses `com.nova.vpn.android` like the phone app. Do not publish signing keys.

## Build

From this folder, run:

```powershell
.\Build-WearOS.ps1 testDebugUnitTest assembleDebug
```

ARM debug APKs are written under `app\build\outputs\apk\debug\`. Pairing and phone commands require testing on a physical Wear OS watch paired with an Android device. The build targets Android SDK 35 and uses Wear Compose Material 3. Independent watch VPN operation is not offered in 0.3.9.

## Repository boundary

The intended public repository is `https://github.com/leoraijin/NOVA-VPN-WearOS`. Do not place Wear OS files or releases in the Windows or Android-phone repository. Never include VPN profiles, subscription URLs, credentials, or app-signing keystores.
