# NOVA VPN Wear OS — changes

## 0.3.1 — 2026-10-14

- Ported phone network fixes: libbox 1.14.1, gVisor, MTU 1500, physical-network socket binding and local bootstrap DNS.
- Existing saved Reality profiles receive missing uTLS/SNI defaults at runtime without changing the encrypted original or explicit TLS settings.
- Added one bounded HTTPS check through the actual proxy. Failure produces a neutral unconfirmed state, not a fatal VPN error; no continuous battery-consuming polling.
- Local watch status no longer depends on phone/Data Layer availability; corrected Data Layer status subscription URIs.
- Preserved optional encrypted profile transfer and separate phone/watch tunnel controls.
- Added regression tests and public source under GPL-3.0-only. Watch Ultra 2025 / One UI 8 hardware testing is still required.

Updates: https://github.com/leoraijin/NOVA-VPN-WearOS/releases

## 0.3.0

- Added an on-watch sing-box VPN tunnel; it runs independently of the phone tunnel.
- Added optional phone-to-watch profile transfer. It happens only after tapping the send icon in the phone's server list.
- The watch encrypts the received configuration with its own Android Keystore key; no profile is sent automatically.
- Kept phone VPN remote control as a separate action. Watch and phone VPN statuses are tracked separately.

## 0.2.1

- The watch listens for phone status changes only while the app is visible, and removes the listener as soon as it goes to the background.
- Removed an always-changing timestamp from status sync. Repeated identical VPN states no longer create needless urgent Data Layer updates/wakeups.
- Kept urgent delivery for genuine status changes and the one-time status refresh on opening the watch app.

## 0.2.0

- Added a watch-sized VPN status screen and connect/disconnect remote control for the paired Android phone.
- Sync is limited to connection state and a control command; no profile names, VPN configuration, or credentials are sent to the watch.
- Requires the phone and watch packages to share the same application ID and signing certificate.

## Update

Find source and future watch APK releases in the separate `NOVA-VPN-WearOS` GitHub repository.
