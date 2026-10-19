# Building Bot on Android with Termux

This project can be built from Termux only if your phone has a compatible Android SDK toolchain. **Termux by itself is not the Android SDK**, and Google's standard Linux command-line tools may be x86_64-only. On ARM phones, you need an ARM-compatible SDK/build-tools setup.

## 1. Install basic packages

```bash
pkg update && pkg upgrade -y
pkg install -y git unzip wget openjdk-17 gradle aapt2
java -version
gradle --version
```

Android Gradle Plugin 8.7.3 is designed for Gradle 8.9 and JDK 17. If the Termux `gradle` package is newer than 8.9, use a compatible Gradle 8.9 distribution that runs on your device rather than assuming the system Gradle will work.

## 2. Set up the SDK

Install or provide an ARM-compatible Android SDK with platform 35 and build-tools 35.0.0. Then set the path (change this if your SDK lives elsewhere):

```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

Verify the platform exists:

```bash
test -f "$ANDROID_HOME/platforms/android-35/android.jar" && echo "SDK platform found"
```

If that check fails, stop and fix the SDK before building. A failed SDK install cannot be solved by retrying Gradle.

## 3. Extract the project

Download the project ZIP to Android Downloads, then:

```bash
termux-setup-storage
mkdir -p "$HOME/projects"
unzip "$HOME/storage/downloads/Bot-NVIDIA-Termux.zip" -d "$HOME/projects"
cd "$HOME/projects/BotAndroid"
```

If your downloaded ZIP has a different name or location, update the path.

## 4. Build

```bash
bash build-termux.sh
```

The script checks for the SDK and Gradle, sets `local.properties`, and attempts to use a native `aapt2` when available. If the build fails, preserve the full error output. Common causes are missing platform 35, incompatible Gradle/JDK versions, and x86_64-only build tools.

Expected output:

`app/build/outputs/apk/debug/app-debug.apk`

If storage is available, the helper copies it to `~/storage/downloads/Bot-debug.apk`.

## 5. Install and grant permissions

Install the debug APK using Android's package installer. On first run:

1. In Controls, save an NVIDIA NIM API profile and a model ID supported by your account.
2. Enable Bot's Accessibility service in Android Settings to use screen text, screenshot analysis, taps, scrolling and text entry.
3. Grant Contacts permission if you want contact-name resolution.
4. Grant notifications if you want scheduled reminder notifications.
5. Keep tap-to-talk as the normal voice entry mode. Wake-word listening is optional and can be restricted by the phone's battery settings.

Screenshot analysis uploads the screenshot to your configured provider only when you tap **Analyze screen**. Choose a vision-capable model. Never use it on a screen containing sensitive data unless you are comfortable sharing that image with the provider.
