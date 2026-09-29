# Together V5

Android watch-together prototype for two people in different locations.

## Features
- Private 6-character rooms
- Local video playback with play/pause/position synchronization
- Text chat
- Online/browser link sharing (Chrome, Samsung Internet, Firefox, Edge, etc.)
- 3-2-1 coordinated browser start
- Configurable WebSocket server URL
- Server source included under `server/`

## Build an APK
1. Install Android Studio (current stable) and JDK 17.
2. Open the `TogetherV5` folder.
3. Let Gradle sync and install Android SDK 35 if prompted.
4. Choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will place the debug APK under `app/build/outputs/apk/debug/`.
6. Copy the APK to each Android phone and install it. Android may ask you to allow installs from the app used to open the APK.

## Internet setup required
The app needs a publicly reachable secure WebSocket (`wss://`) server for UAE ↔ India synchronization. Deploy the Node.js server in `server/` to a host that supports WebSockets, then enter its public `wss://...` address on the app home screen on both phones.

## Online movies / browsers
V5 can share/open a legal movie or streaming webpage in any installed Android browser and coordinate a 3-2-1 start. It cannot capture, rebroadcast, bypass DRM, or remotely control playback inside arbitrary browsers or services such as Netflix/Prime Video/JioHotstar.

## Voice
The Voice button remains a UI placeholder. Reliable live voice needs WebRTC plus signaling and a TURN service. It is not represented as working in this build.

## Security / production note
The included room server is a minimal prototype. Before public deployment, add authentication, rate limiting, TLS/WSS, room expiry, input limits, abuse protection, and production logging/monitoring.
