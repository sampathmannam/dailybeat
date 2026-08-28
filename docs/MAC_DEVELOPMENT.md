# DailyBeat — MacBook development

Use this when developing on your Mac with the Android emulator (not Cursor cloud).

## One-time setup

```bash
# Clone from GitHub (into your preferred folder)
git clone https://github.com/sampathmannam/dailybeat.git ~/github/dailybeat
cd ~/github/dailybeat
git checkout cursor/android-skeleton-cc46

# Or run the automated setup script
chmod +x scripts/mac_setup.sh
./scripts/mac_setup.sh
```

Requirements on Mac:
- Android Studio (SDK + emulator)
- Java 17 (`brew install openjdk@17`)
- `adb` on PATH (Android Studio → SDK Manager → Platform Tools)

## Daily workflow

```bash
cd ~/github/dailybeat
./scripts/mac_sync_and_run.sh    # pull latest from GitHub, build, install on emulator
```

Start the emulator first: **Android Studio → Device Manager → Play**.

## Drive the app on your Mac screen

```bash
./scripts/mac_emulator_demo.sh
```

Optional mirror: `brew install scrcpy && scrcpy`

## Local Cursor agent (runs on Mac, not cloud)

1. **File → Open Folder** → `~/github/dailybeat`
2. Open **Agent / Composer** (`Cmd+I`)
3. Choose **Local** execution (this machine) — not Cloud / not cursor.com/agents
4. Ask the agent to build, `adb install`, and test on your emulator

Cloud agent chats (URL `cursor.com/agents/bc-…`) cannot access your Mac `adb`.

## GitHub sync

| Who | Action |
|-----|--------|
| Cloud / CI agent | `git push` to `cursor/android-skeleton-cc46` |
| You on Mac | `git pull` before each dev session |

```bash
git pull origin cursor/android-skeleton-cc46
```

## Build APKs on Mac

```bash
cd android
./gradlew assembleDebug assembleRelease
```

APKs:
- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`
