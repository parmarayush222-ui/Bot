#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if command -v pkg >/dev/null 2>&1; then
  pkg install -y openjdk-21 wget unzip aapt2 >/dev/null
fi

export JAVA_HOME="${JAVA_HOME:-$PREFIX/opt/openjdk-21}"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$(command -v java || true)"
  [[ -n "$JAVA_BIN" ]] || { echo "Java 17 is required. Run: pkg install openjdk-21" >&2; exit 2; }
else
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Android Gradle Plugin 8.7.3 needs Gradle 8.9. Download the Java-based distribution
# if the system package is missing or not the compatible 8.9 release.
GRADLE_BIN=""
if command -v gradle >/dev/null 2>&1 && gradle --version 2>/dev/null | grep -qE '^Gradle 8\.9([[:space:]]|$)'; then
  GRADLE_BIN="$(command -v gradle)"
else
  CACHE="$HOME/.cache/bot-gradle"
  DIST="$CACHE/gradle-8.9"
  if [[ ! -x "$DIST/bin/gradle" ]]; then
    mkdir -p "$CACHE"
    command -v wget >/dev/null 2>&1 || { echo "wget is required to fetch Gradle 8.9." >&2; exit 2; }
    wget -O "$CACHE/gradle-8.9-bin.zip" https://services.gradle.org/distributions/gradle-8.9-bin.zip
    unzip -q -o "$CACHE/gradle-8.9-bin.zip" -d "$CACHE"
  fi
  GRADLE_BIN="$DIST/bin/gradle"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -d "$HOME/android-sdk" ]]; then
    export ANDROID_HOME="$HOME/android-sdk"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  else
    echo "No Android SDK found. Install an ARM-compatible Android SDK with platform 35 first, then export ANDROID_HOME=\$HOME/android-sdk." >&2
    echo "Standard Google Linux command-line tools may be x86_64-only and not run in Termux on ARM." >&2
    exit 2
  fi
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
  echo "Missing SDK platform 35 at $ANDROID_HOME/platforms/android-35/android.jar." >&2
  exit 2
fi

# Android Gradle Plugin's bundled aapt2 may target x86_64. Prefer an ARM-native build tool.
AAPT2=""
if [[ -x "$PREFIX/bin/aapt2" ]]; then
  AAPT2="$PREFIX/bin/aapt2"
else
  for candidate in "$ANDROID_HOME"/build-tools/*/aapt2; do
    if [[ -x "$candidate" ]]; then AAPT2="$candidate"; fi
  done
fi
if [[ -n "$AAPT2" ]]; then
  mkdir -p "$HOME/.gradle"
  touch "$HOME/.gradle/gradle.properties"
  if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then
    sed -i "s#^android.aapt2FromMavenOverride=.*#android.aapt2FromMavenOverride=$AAPT2#" "$HOME/.gradle/gradle.properties"
  else
    printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2" >> "$HOME/.gradle/gradle.properties"
  fi
fi

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
echo "Building Bot debug APK (NVIDIA NIM is the default provider)..."
"$GRADLE_BIN" --no-daemon --stacktrace assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "Gradle completed but APK was not found: $APK" >&2; exit 3; }
if [[ -d "$HOME/storage/downloads" ]]; then
  cp "$APK" "$HOME/storage/downloads/Bot-debug.apk"
  echo "Build complete: $HOME/storage/downloads/Bot-debug.apk"
else
  cp "$APK" "$ROOT/Bot-debug.apk"
  echo "Build complete: $ROOT/Bot-debug.apk"
fi
