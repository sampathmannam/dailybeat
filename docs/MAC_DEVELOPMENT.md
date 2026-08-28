# DailyBeat — MacBook / laptop development

Run DailyBeat **on your laptop** with Android Studio emulator or a USB-connected phone.

## One-time setup

```bash
git clone https://github.com/sampathmannam/dailybeat.git ~/github/dailybeat
cd ~/github/dailybeat

chmod +x scripts/*.sh
./scripts/mac_setup.sh
```

**Requirements**
- Android Studio (SDK + emulator)
- Java 17: `brew install openjdk@17`
- `adb` on PATH:

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

Add that line to `~/.zshrc` so it persists.

## Connect emulator or phone

### Emulator (recommended)
1. Open **Android Studio**
2. **Device Manager** → create/start a device (Pixel, API 34)
3. Wait until the emulator home screen appears

### Physical phone
1. Enable **Developer options** → **USB debugging**
2. Plug in USB, accept the debugging prompt on the phone

### Verify connection

```bash
adb devices
```

You should see something like `emulator-5554   device` or your phone ID.

## Run DailyBeat (build on laptop)

```bash
cd ~/github/dailybeat
git pull origin main
./scripts/mac_sync_and_run.sh
```

This pulls **v2.0.0** from `main`, builds, installs, grants permissions, and launches the app.

## Fast install (no build — download APK)

```bash
./scripts/mac_install_release_apk.sh
```

Downloads `app-release.apk` from GitHub Releases **v2.0.0**.

## Cursor on your Mac (local agent)

1. **File → Open Folder** → `~/github/dailybeat`
2. Use **Agent** with **local** execution (this machine)
3. Local agent can run `adb` on your emulator; cloud agents cannot

## v2 app tour (on your screen)

1. **Onboarding** → officer name → Get started
2. **Today** → manual event or mic FAB
3. **Diary** → Generate → edit → Share PDF
4. **History** → past days
5. **Settings** → GPS, places, model import

Optional mirror: `brew install scrcpy && scrcpy`

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `adb: more than one device` | `export DAILYBEAT_ADB_SERIAL=ZD2232FCR5` (phone) or `emulator-5554` |
| `Unable to locate a Java Runtime` | `brew install openjdk@17` then add to `~/.zshrc`: `export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"` |
| No Java needed | `./scripts/mac_install_release_apk.sh` installs pre-built APK |
| `adb: no devices` | Start emulator or check USB cable / debugging |
| `adb offline` | `adb kill-server && adb start-server` |
| Gradle fails | `cd android && ./gradlew clean assembleDebug` |
| Old UI | `git pull origin main` — you need v2.0.0 on `main` |

## GitHub

- Repo: https://github.com/sampathmannam/dailybeat
- Releases: https://github.com/sampathmannam/dailybeat/releases/tag/v2.0.0
