# NOVA VPN for Wear OS

This is a standalone Wear OS VPN client paired with the separate NOVA Android phone client. The phone and watch can run independent VPN tunnels. A profile can optionally be copied from the phone by tapping **Servers → Передать на часы** below the chosen profile.

Current version: **0.3.8** (experimental debug builds). Pair with **Android 0.2.21+** for acknowledged profile transfer. The app includes an on-demand HTTPS test over the Bluetooth phone-companion network. It does not change the watch's default route or certify that other apps use the phone VPN.

The phone discovers the watch application's capability and sends one explicit transfer. The watch acknowledges only after encrypted storage and read-back succeed. The phone matches the response to the request and node; missing acknowledgement produces a timeout rather than success. The watch UI reloads the local profile and clears the stale missing-profile message. Transfer does not start either VPN.

The native VPN service runs in its own private process, with Messenger IPC for local status. If the process exits while the screen is bound, the screen reports a fixed phase and recent Android exit category. ARMv7 is the required variant for the owner's device where ARM64 does not run. On the tested Watch Ultra / One UI 8 firmware, Android's standard VPN management service is absent, so the independent watch tunnel cannot start.

## NOVA VPN tile / Galaxy Wearable Cards

After updating and opening NOVA on the watch, add **NOVA VPN** using **Galaxy Wearable → Карточки → Добавить карточки** or the watch's own tile picker. The purple card shows the local watch tunnel and profile status. **Открыть VPN** opens NOVA's local watch controls, where VPN consent and connect/disconnect are handled.

Tile updates are event-driven (local state/profile changes and tile entry), with no periodic network check or timer. The tile stores no hostname, profile, key or phone status. It checks this app's foreground VPN service before showing a saved active state. HTTPS status reflects the existing connection-time check, not continuous monitoring. The picker preview is a static design asset, not a device screenshot.

### Connect buttons

The upper button controls only the watch tunnel. It checks the local profile before asking for VPN permission and shows missing-profile/permission/start failures beside the button. If this watch OS cannot open its VPN consent activity, use the separate phone control instead; this is not proof of on-watch VPN compatibility.

The lower button controls only the phone. Install Android 0.2.20 or later for command acknowledgements. One phone node receives the command; a 12-second timeout reports missing response rather than silent success. An acknowledgement means the phone handled the request, not that HTTPS already works. Android may require opening the phone app to grant VPN consent or allow starting it from the background. Profiles are still transferred only by an explicit send action in the phone server list.

## Network fixes and status

- Uses libbox 1.14.1, the Android-tested gVisor TUN stack and MTU 1500.
- Runtime-only Reality defaults and physical-network DNS/bootstrap prevent proxy DNS recursion. Explicit TLS settings remain unchanged.
- Protected core sockets are bound to an available non-VPN network; the app itself is excluded from the watch TUN.
- One bounded, authenticated-loopback HTTPS check runs per connection through the selected concrete proxy. There is no periodic polling or speed test.
- A failed control request means **unconfirmed**, not a dead VPN. Selectors/direct fallback are never certified as proxy traffic. Fatal start errors remain errors.
- Watch status is local and works without the phone; pairing and profile transfer remain optional and explicit.
- **Check phone gateway** requests the Bluetooth companion network for this app only and performs one HTTPS request. It does not modify system network settings, disable Wi-Fi/LTE or guarantee that other watch applications use the VPN.
- Physical test: on Watch Ultra / One UI 8 paired with S24, HTTPS over the Bluetooth companion network succeeded and returned the same external address as phone Chrome while NOVA VPN was active. The watch's active default network remained Wi-Fi. This confirms the selected Bluetooth path, not system-wide watch protection.

## License

NOVA source is licensed under GPL-3.0-only; see LICENSE and THIRD_PARTY_NOTICES.md. Third-party dependencies retain their own licenses.

## Phone/watch synchronization

- Uses the official Wearable Data Layer API for paired Wear OS + Android devices.
- Receives status updates only while the watch app is visible; it removes the listener in the background and fetches the latest saved state when reopened.
- Repeated identical statuses are not given a new timestamp, avoiding redundant urgent sync events and unnecessary watch wakeups.
- The phone's server list has an explicit send action. The watch stores the received sing-box config encrypted with a watch-local Android Keystore key.
- The watch VPN asks for Android VPN consent on the watch itself. Phone VPN consent does not authorize a watch tunnel.
- A separate button can still control the phone tunnel; watch tunnel state and phone tunnel state are independent.
- Profiles are sent only to paired Wear devices, with a 90 KiB transfer limit. Never include profile/config data in public source or APK metadata.
- Data Layer requires the phone and watch APKs to use the same application ID and signing certificate. This project intentionally uses `com.nova.vpn.android` like the phone app. Do not publish signing keys.

## Build

From this folder, run:

```powershell
.\Build-WearOS.ps1 testDebugUnitTest assembleDebug
```

ARM debug APKs are written under `app\build\outputs\apk\debug\`. Pairing, profile transfer, VPN consent, and independent VPN operation require testing on a physical Wear OS watch paired with an Android device. The standalone build targets Android SDK 35 and uses Wear Compose Material 3.

## Repository boundary

The intended public repository is `https://github.com/leoraijin/NOVA-VPN-WearOS`. Do not place Wear OS files or releases in the Windows or Android-phone repository. Never include VPN profiles, subscription URLs, credentials, or app-signing keystores.
