# Bot feature map

## Voice and AI
- Tap-to-talk speech recognition and Android TTS responses.
- Optional foreground wake-word listener (disabled by default).
- NVIDIA NIM is the default provider. Gemini and other OpenAI-compatible APIs remain configurable.
- NVIDIA/OpenAI-compatible chat completions can return tool calls for supported models.
- Selectable installed Android TTS voices.

## Android actions
- Open installed apps by visible launcher name.
- Search Google, YouTube and Instagram.
- Resolve a saved contact (with permission) or use a supplied phone number.
- Open the phone dialer after confirmation.
- Prepare SMS, WhatsApp and email drafts after confirmation.
- Create a reminder after confirmation. Reminders are persisted and re-armed after reboot/app update; tapping the reminder opens Bot so the user can continue the request.

## Screen awareness
- Accessibility service can read visible text and accessible descriptions; password fields are skipped.
- Accessibility actions: click a visible text target, scroll, type into the focused non-password editable field, and press Back.
- Android 11+ screenshot analysis uses the Accessibility screenshot API. It runs only after the user taps Analyze Screen, then uploads the image to the selected provider. Choose a model with vision support.

## User controls and safety
- Confirm before calls, message/email drafts, scheduling, screen clicks, screen typing, and Back.
- Never automatically presses Send in SMS/WhatsApp/email apps or places calls.
- API keys are encrypted using AndroidX EncryptedSharedPreferences.
- Accessibility, Contacts, microphone, and notification permissions are user-granted.

## Not provided by a generic API key alone
- Silent WhatsApp sending (WhatsApp controls this; Bot opens a prefilled draft).
- Authenticated Gmail sending (requires OAuth and a separate mail integration).
- Guaranteed exact-alarm delivery across all OEM battery settings.
- Screenshot access to apps that mark their windows secure.
