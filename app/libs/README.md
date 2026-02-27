# sing-box Library (libbox)

Place the `libbox.aar` file in this directory. CI builds it automatically.

## Local build (for development)

### Prerequisites
- **Go 1.22+**: https://go.dev/dl/
- **Android SDK + NDK** (NDK via Android Studio → SDK Manager → SDK Tools)

### Build steps

```bash
# 1. Install sagernet's gomobile fork (NOT the default gomobile!)
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.8
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.8
gomobile init

# 2. Set ANDROID_HOME if not already set
export ANDROID_HOME=$HOME/Library/Android/sdk   # macOS
# export ANDROID_HOME=$HOME/Android/Sdk          # Linux

# 3. Clone sing-box
git clone https://github.com/SagerNet/sing-box
cd sing-box
git checkout v1.12.22  # or latest version

# 4. Build the AAR
gomobile bind -v -androidapi 26 -javapkg=io.nekohasekai -libname=box \
  -tags "with_gvisor,with_quic,with_wireguard,with_ech,with_utls,with_clash_api" \
  ./experimental/libbox/

# 5. Copy to project
cp libbox.aar /path/to/NuggetVPN-Mobile/app/libs/
```
