# Bot — NVIDIA NIM-first Android assistant

Bot is a Kotlin/Jetpack Compose Android assistant designed to be built on a computer or from a suitably configured Termux Android SDK toolchain. NVIDIA NIM is the default AI provider. Gemini and other OpenAI-compatible endpoints are optional profiles.

## Features in this source package

- Tap-to-talk speech input using Android speech recognition; optional foreground wake-word listener.
- Spoken replies using Android TextToSpeech, including choosing an installed system voice.
- NVIDIA NIM OpenAI-compatible chat completions with tool calling; configurable model ID, base URL and encrypted API-key storage.
- Optional Gemini `generateContent` text/image requests.
- Tool-planned app launching, Google/YouTube/Instagram search, call-dialer opening, SMS drafts, WhatsApp prefilled drafts, email drafts, reminders, accessibility taps/scrolling/text entry/Back.
- Contact-name resolution for call/SMS/WhatsApp drafts when Contacts permission is granted; ambiguous matches are rejected instead of guessed.
- Accessibility-based visible-text reading (password fields excluded), plus user-triggered screenshot analysis on Android 11+ through the enabled Accessibility service.
- Confirmation prompt before call, message, email, schedule, screen text entry, screen tap, and Back actions.
- Reminder schedules stored locally and restored after reboot/app replacement. Android battery and notification settings can still affect delivery.
- NVIDIA is the default provider; Gemini/OpenAI-compatible endpoints are optional.

## Important behavior and limitations

- This repository is source code, not a prebuilt/verified APK. Run a build and test it on your phone before relying on it.
- The selected NVIDIA model must support function calling for AI-planned actions. For screenshot analysis it must also accept image inputs; the default text model may not support vision. Choose a compatible vision model in Settings when needed.
- Screenshot analysis uploads the current screenshot to the configured AI provider after you tap **Analyze screen**. Do not use it on sensitive screens. Apps using Android's secure-window flag may prevent screenshots.
- Accessibility screen reading/actions require the user to enable Bot's Accessibility service manually in Android Settings. Android restricts what an accessibility service can inspect or operate on.
- Contacts lookups require the user to grant Contacts permission. If multiple phone numbers match, Bot asks for a more specific recipient rather than choosing silently.
- Calls open the dialer; Bot does not place a call automatically. SMS, WhatsApp and email open drafts for review in the target app; Bot does not silently send messages. Direct email sending requires a separately implemented authenticated mail API, and direct SMS sending has Android permission/default-app constraints.
- Wake-word listening can be restricted by Android/OEM battery management. Tap-to-talk is the default.
- API keys are stored encrypted on-device. Cloud requests still send the prompt and any explicitly shared screen context/image to the selected provider.

## Configure NVIDIA NIM

1. Open Bot → Controls → AI Profiles.
2. Keep **NVIDIA NIM** selected.
3. Enter your NVIDIA API key from the NVIDIA API catalog.
4. Set the exact currently available model ID for your NIM endpoint. Default base URL: `https://integrate.api.nvidia.com/v1`.
5. Save the profile. Model availability and tool/vision support vary; verify against the model page for your account.

## Build

The project targets Android SDK 35, minSdk 26, Java 17, Android Gradle Plugin 8.7.3, and Gradle 8.9. Standard builds:

```bash
gradle --version
gradle --no-daemon assembleDebug
```

For Termux notes, see [PHONE-ONLY-BUILD.md](PHONE-ONLY-BUILD.md). The Termux script expects an ARM-compatible Android SDK, Java 17 and compatible Gradle. Stock Android command-line SDK downloads may be x86_64-only and fail on ARM Termux; the script cannot manufacture a compatible SDK if none is installed.

APK output after a successful build:

`app/build/outputs/apk/debug/app-debug.apk`
