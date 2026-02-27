# NuggetVPN Mobile

NuggetVPN Mobile is the Android port of [NuggetVPN](https://github.com/Rigby-Foundation/NuggetVPN) — a modern, lightweight VPN client powered by [sing-box](https://sing-box.sagernet.org/). Built natively with Kotlin and Jetpack Compose.

## Features

- **Native Android**: Built with Kotlin + Jetpack Compose + Material You (dynamic colors).
- **Protocol Support**:
  - **VLESS**: Reality, TLS, XTLS-Vision, uTLS fingerprinting.
  - **VMess**: WebSocket, gRPC, HTTP transports.
  - **Trojan**: TLS and WebSocket.
  - **Shadowsocks**: Standard and 2022 ciphers.
  - **Hysteria/Hysteria2**: QUIC-based protocols.
  - **TUIC**: UDP relay protocol.
  - **WireGuard**: Built-in WireGuard support.
  - **SOCKS5**: Proxy chaining.
- **Custom Configs**: Paste full sing-box JSON configurations directly.
- **Profile Management**:
  - Import via subscription URL.
  - Add manually via proxy links (`vless://`, `ss://`, etc.).
  - Persistent local storage.
- **Split Tunneling**: Route by apps, domains, or both.
- **Proxy Chaining**: Chain multiple proxies together.
- **Real-time Stats**: Upload/download speed and traffic counters.
- **Real-time Logging**: View sing-box logs directly in the app.
- **Ping Testing**: Concurrent TCP ping to all profiles.

## Prerequisites

- **Android Studio** (Hedgehog or newer)
- **JDK 21**
- **Android SDK** (API 35 / compileSdk 35, minSdk 26)
- **Go 1.22+** (for building libbox)

## Building

### 1. Build libbox.aar

NuggetVPN Mobile uses the sing-box library (libbox) for its VPN core. You must build it from source:

```bash
# Install sagernet's gomobile fork (NOT the standard gomobile)
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.8
gomobile init

# Clone sing-box
git clone https://github.com/SagerNet/sing-box.git
cd sing-box
git checkout v1.12.22

# Build libbox.aar
gomobile bind -v \
  -androidapi 26 \
  -javapkg=io.nekohasekai \
  -libname=box \
  -tags "with_gvisor,with_quic,with_wireguard,with_ech,with_utls,with_clash_api" \
  ./experimental/libbox
```

### 2. Place libbox.aar

```bash
cp libbox.aar /path/to/NuggetVPN-Mobile/app/libs/
```

### 3. Build the APK

```bash
cd NuggetVPN-Mobile
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Signed Release Build

Create `keystore.properties` in the project root:

```properties
storeFile=path/to/your.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then:

```bash
./gradlew assembleRelease
```

## CI/CD

GitHub Actions workflows are included:

- **`test.yml`**: Builds on every push/PR — compiles libbox from source, builds signed APK, uploads as artifact.
- **`release.yml`**: Triggered on version tags (`v*`) — builds signed release APK and creates a GitHub Release.

### Required Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

## Project Structure

```
app/src/main/java/org/rigbyfoundation/nuggetvpn/
├── NuggetVpnApp.kt           # Application class, sing-box setup
├── MainActivity.kt            # Compose entry point
├── data/
│   ├── models/Models.kt       # Data classes
│   ├── network/NetworkClient.kt  # HTTP client
│   └── repository/            # Profile & settings storage
├── vpn/
│   ├── NuggetVpnService.kt   # Android VPN service + sing-box integration
│   ├── SingBoxPlatform.kt    # PlatformInterface implementation
│   └── ConfigBuilder.kt      # sing-box JSON config generator
├── viewmodel/MainViewModel.kt # App state management
├── util/
│   ├── ProxyParser.kt        # Protocol link parser
│   ├── PingManager.kt        # TCP ping
│   └── Extensions.kt         # Formatting utilities
└── ui/
    ├── theme/                 # Material You + NuggetVPN colors
    ├── navigation/            # Bottom nav
    ├── screens/               # 6 screens
    └── components/            # Reusable UI components
```

## License

This project is licensed under the [GPL-3.0 License](LICENSE).
