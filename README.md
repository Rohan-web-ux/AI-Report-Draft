# AI Report Draft — Gemini Android App

Native Android single-page report drafting app.

## Features
- Direct Google Gemini API — no Puter
- Gemini 3.8 Flash primary model
- Gemini 3.5 Flash-Lite fallback if the primary model is unavailable
- Urdu / Roman Urdu / broken English → professional English report
- Voice input via Android speech recognizer
- Political, Religious, Security, Search Operation, Road Accident, Crime / Incident,
  Recovery / Seizure, Law & Order, Missing Person, Protest / Demonstration, Miscellaneous
- Auto date/time
- Optional AI-generated subject
- Copy / WhatsApp / Android share sheet (Google Drive can be selected there)
- Local saved reports
- API key is entered by the user and saved only in app-private preferences

## Step 1 — Get a Gemini API key
Open the app and press **Get Free Key**, or open Google AI Studio:
https://aistudio.google.com/app/apikey

Create/copy an API key, paste it into the app, then press **Save / Test Key**.

Google's current Gemini API uses the `x-goog-api-key` request header.

## Step 2 — Build APK with Android Studio
1. Install Android Studio.
2. Open this project folder.
3. Let Gradle sync.
4. `Build` → `Build App Bundle(s) / APK(s)` → `Build APK(s)`.
5. Debug APK is normally created at:
   `app/build/outputs/apk/debug/app-debug.apk`

## Easiest free APK build — GitHub Actions
This project already contains:
`.github/workflows/build-apk.yml`

1. Create a new GitHub repository.
2. Upload all project files, including the hidden `.github` folder.
3. Open the repository → **Actions**.
4. Open **Build Android APK** → **Run workflow**.
5. When it finishes, download the `AI-Report-Draft-APK` artifact.
6. Extract it and install `app-debug.apk` on Android.

You do NOT put your Gemini API key in GitHub. The user pastes the key inside the installed app.

## Security note
Do not hard-code a private Gemini key into a public APK. A hard-coded key can be extracted from the APK.
This app instead asks the device user to paste their own Gemini key and stores it in the app's private SharedPreferences.

For organizational or sensitive reports, confirm that your policy permits sending the entered text to Google's Gemini API.
