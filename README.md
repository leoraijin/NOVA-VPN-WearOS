# NOVA VPN for Wear OS

This is a standalone Wear OS VPN client paired with the separate NOVA Android phone client. The phone and watch can run independent VPN tunnels. A profile can optionally be copied from the phone; nothing is transferred until the user taps the send icon next to a server on the phone.

Current client version: **0.3.2** (experimental debug builds).

## Connect buttons

The upper button controls only the watch tunnel. It checks the local profile before asking for VPN permission and shows missing-profile/permission/start failures beside the button. If this watch OS cannot open its VPN consent activity, use the separate phone control instead; this is not proof of on-watch VPN compatibility.

The lower button controls only the phone. Install Android 0.2.20 or later for command acknowledgements. One phone node receives the command; a 12-second timeout reports missing response rather than silent success. An acknowledgement means the phone handled the request, not that HTTPS already works. Android may require opening the phone app to grant VPN consent or allow starting it from the background. Profiles are still transferred only by an explicit send action in the phone server list.

## Network fixes and status

- Uses libbox 1.14.1, the Android-tested gVisor TUN stack and MTU 1500.
- Runtime-only Reality defaults and physical-network DNS/bootstrap prevent proxy DNS recursion. Explicit TLS settings remain unchanged.
- Protected core sockets are bound to an available non-VPN network; the app itself is excluded from the watch TUN.
- One bounded, authenticated-loopback HTTPS check runs per connection through the selected concrete proxy. There is no periodic polling or speed test.
- A failed control request means **unconfirmed**, not a dead VPN. Selectors/direct fallback are never certified as proxy traffic. Fatal start errors remain errors.
- Watch status is local and works without the phone; pairing and profile transfer remain optional and explicit.
- Physical Watch Ultra 2025 / One UI 8 installation and real traffic have not yet been verified for this build. A successful build or unit test is not a hardware VPN test.

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
