# NOVA VPN Wear OS — changes

## 0.3.6 — 2026-10-14 (experimental debug)

- Opening watch controls no longer auto-creates an idle VPN process. Explicit Connect still starts and binds the local service; reopening controls binds an already running service.
- Local profile decryption moves off the UI thread. Remove the redundant Data Layer listener for this watch's own status; profile reception updates the UI through its local revision flow.
- Suppress unchanged status publications and tile refreshes; preserve explicit profile refresh, command acknowledgements and retry after failed status delivery.
- Routine verification status uses non-urgent Data Layer batching; actual connection transitions and separate command acknowledgements retain immediate delivery.
- Coalesce identical default-interface updates from capability events. Network handover, link/interface change, loss and restoration remain delivered; no extra timer or polling.
- Idle service destruction no longer broadcasts a false disconnect; startup failure remains visible. Independent VPN and its one-shot HTTPS check are unchanged.
- Actual battery savings and watch VPN operation require physical Watch Ultra testing. This is not a confirmed fix for the previous native VPN crash. Owner: ARMv7 / 32-bit.
- Updates: GitHub Releases in this Wear OS repository.

## 0.3.5 — 2026-10-14 (experimental debug)

- Added a NOVA VPN tile to the Wear OS carousel / Galaxy Wearable Cards picker, with the purple shield, local tunnel status, profile readiness and an Open VPN action.
- The action opens local watch controls. It does not toggle the phone or start VPN from a background tile callback.
- Refresh only on local state/profile changes and tile entry. No timer, periodic HTTPS checks, profile transfer or polling is added.
- Persist only a fixed local status string; check that this app's own VPN service is foreground before displaying an active tunnel. A stale verified record after process exit is not shown as active.
- Included a static tile-picker preview. Debug build/protocol/layout checks do not replace physical Watch Ultra testing.
- Updates: GitHub Releases in this Wear OS repository; use ARMv7 on the owner's watch.

## 0.3.4 — 2026-10-14 (experimental debug)

- Advertise the profile receiver capability so Android 0.2.21 discovers the NOVA app rather than blindly sending to every connected node.
- Acknowledge the exact transfer only after encrypted storage and read-back succeed. Reject malformed/oversized profiles; preserve legacy raw messages from older phone versions.
- Use atomic encrypted storage with a process-wide lock. Notify the visible watch UI immediately and clear the stale missing-profile message after receipt; the watch does not auto-connect.
- Pair with Android 0.2.21 and tap Servers → Transfer to watch. No hardware transfer or watch VPN crash fix is claimed; install ARMv7 on the owner's watch.

## 0.3.3 — 2026-10-14 (diagnostic / experimental)

- The VPN core now runs in an app-private `:vpn` process instead of the activity process. This contains native-core failures; it is not a confirmed fix for the reported device VPN failure.
- Local state uses private Messenger IPC while the screen is visible, without phone dependency or periodic polling. Lost service connection reports failure instead of leaving the screen silently waiting.
- Failure feedback includes only a fixed startup phase, app version, ABI and recent system exit category. No configs, keys, endpoints, exception texts or native tombstones are stored or exported.
- The affected physical Watch Ultra is not connected to ADB; exact native/Java/system cause is still unconfirmed. Use the ARMv7 diagnostic APK and report the phase/reason shown if it fails.

## 0.3.2 — 2026-10-14

- Check the local profile before asking for VPN consent; missing/corrupt profile and unsupported permission activity are reported in the UI instead of throwing from the button handler.
- Catch local start/stop and permission launch failures. Show feedback above the associated button, rather than below the screen edge.
- Phone commands go to one node only, wait for an application acknowledgement and time out after 12 seconds. Pair with Android 0.2.20 for acknowledgement support.
- Keep independent local tunnels and optional explicit profile transfer. Physical Watch Ultra / One UI 8 crash reproduction and traffic tests remain unverified without device logs.

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
