# Journey Beat Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace DailyBeat's visually unbalanced folded-note launcher artwork with the approved, optically centered Journey Beat adaptive icon and verify it on Android hardware.

**Architecture:** Keep the existing Android adaptive-icon declarations and navy background resource. Replace only the foreground and monochrome vector geometry, then enforce the approved colors, safe-zone anchors, and removal of the old folded-note paths with repository tests.

**Tech Stack:** Android adaptive icons, VectorDrawable XML, Python pytest contract tests, Android Gradle Plugin, Android lint, ADB, Motorola launcher.

## Global Constraints

- The adaptive canvas remains exactly `108 × 108` units.
- Visible foreground artwork is optically centered at `(54, 54)` inside a `58 × 58` optical box.
- Use navy `#0B1633`, cream `#FFF7E8`, amber `#F4A629`, and coral `#FF6B4A` only.
- Do not add text, gradients, shadows, photographic texture, dependencies, or raster launcher assets.
- Keep the existing regular, round, and monochrome adaptive-icon declarations.
- Do not change in-app navigation icons, application behavior, data, cloud configuration, or branding copy.
- Ship the icon with the next production-readiness release, not as an icon-only release.

---

## File Structure

- `android/app/src/main/res/drawable/ic_launcher_foreground.xml`: multicolor Journey Beat foreground used by regular and round adaptive icons.
- `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`: one-color Journey Beat outline used by Android themed icons.
- `scripts/tests/test_release_pipeline.py`: resource contract that prevents the old folded-note artwork or unsafe geometry from returning.

### Task 1: Lock the Journey Beat resource contract

**Files:**
- Modify: `scripts/tests/test_release_pipeline.py`
- Test: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Consumes: UTF-8 Android vector resources at the two exact drawable paths.
- Produces: `test_launcher_icon_uses_centered_journey_beat_vectors()` as a release-pipeline invariant.

- [ ] **Step 1: Write the failing icon contract test**

Append this test:

```python
def test_launcher_icon_uses_centered_journey_beat_vectors():
    foreground = (
        ROOT / "android/app/src/main/res/drawable/ic_launcher_foreground.xml"
    ).read_text(encoding="utf-8")
    monochrome = (
        ROOT / "android/app/src/main/res/drawable/ic_launcher_monochrome.xml"
    ).read_text(encoding="utf-8")

    assert 'android:viewportWidth="108"' in foreground
    assert 'android:viewportHeight="108"' in foreground
    assert 'android:pathData="M54,25' in foreground
    assert 'android:pathData="M35,53' in foreground
    assert 'android:fillColor="#FFF7E8"' in foreground
    assert 'android:strokeColor="#F4A629"' in foreground
    assert 'android:fillColor="#FF6B4A"' in foreground
    assert "M30,20" not in foreground
    assert "M69,88" not in foreground

    assert 'android:viewportWidth="108"' in monochrome
    assert 'android:pathData="M54,25' in monochrome
    assert 'android:pathData="M35,53' in monochrome
    assert "M30,20" not in monochrome
```

- [ ] **Step 2: Run the test and verify the old icon fails it**

Run:

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py::test_launcher_icon_uses_centered_journey_beat_vectors -q
```

Expected: FAIL because the current foreground contains `M30,20` and does not contain the new centered Journey Beat paths.

- [ ] **Step 3: Commit the red contract test**

```powershell
git add scripts/tests/test_release_pipeline.py
git commit -m "test(icon): define Journey Beat resource contract"
```

### Task 2: Replace the adaptive foreground and monochrome vectors

**Files:**
- Modify: `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Test: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Consumes: existing `@color/ic_launcher_background` and adaptive declarations in `mipmap-anydpi-v26`.
- Produces: centered multicolor and monochrome `108 × 108` VectorDrawables with matching pin and pulse geometry.

- [ ] **Step 1: Replace the multicolor foreground**

Use this complete resource:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFF7E8"
        android:pathData="M54,25 C38,25 25,37 25,52 C25,70 54,83 54,83 C54,83 83,70 83,52 C83,37 70,25 54,25 Z" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#F4A629"
        android:strokeWidth="5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M35,53 L44,53 49,43 56,64 62,49 68,53 76,53" />
    <path
        android:fillColor="#FF6B4A"
        android:pathData="M54,47 A5,5 0,1 0,54,57 A5,5 0,1 0,54,47" />
</vector>
```

- [ ] **Step 2: Replace the themed monochrome vector**

Use this complete resource:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="6"
        android:strokeLineJoin="round"
        android:pathData="M54,25 C38,25 25,37 25,52 C25,70 54,83 54,83 C54,83 83,70 83,52 C83,37 70,25 54,25 Z" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M35,53 L44,53 49,43 56,64 62,49 68,53 76,53" />
</vector>
```

- [ ] **Step 3: Run the icon contract test**

Run:

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py::test_launcher_icon_uses_centered_journey_beat_vectors -q
```

Expected: `1 passed`.

- [ ] **Step 4: Run all repository release tests**

Run:

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests -q
```

Expected: all tests pass.

- [ ] **Step 5: Commit the vector replacement**

```powershell
git add android/app/src/main/res/drawable/ic_launcher_foreground.xml android/app/src/main/res/drawable/ic_launcher_monochrome.xml
git commit -m "fix(icon): center Journey Beat adaptive mark"
```

### Task 3: Build and verify the icon on Android hardware

**Files:**
- Verify only: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Verify only: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: compiled debug APK and connected Motorola device `ZD2232FCR5`.
- Produces: visual and launch evidence; no additional production code.

- [ ] **Step 1: Compile resources, lint, and assemble the APK**

Run:

```powershell
Push-Location android
.\gradlew.bat --no-daemon :app:lintDebug :app:assembleDebug
Pop-Location
```

Expected: `BUILD SUCCESSFUL` with no resource or adaptive-icon errors.

- [ ] **Step 2: Install only the isolated QA package on the phone**

Run:

```powershell
$env:ANDROID_SERIAL='ZD2232FCR5'
Push-Location android
.\gradlew.bat :app:installDebug
Pop-Location
Remove-Item Env:ANDROID_SERIAL
```

Expected: `Installed on 1 device`; production application data remains untouched because debug uses `com.dailybeat.app.qa`.

- [ ] **Step 3: Restart the Motorola launcher to invalidate its icon cache**

Run:

```powershell
adb -s ZD2232FCR5 shell am force-stop com.motorola.launcher3
adb -s ZD2232FCR5 shell input keyevent KEYCODE_HOME
```

Expected: Motorola launcher returns with the updated QA icon available in the app drawer.

- [ ] **Step 4: Inspect the icon under the real launcher mask**

Open the app drawer, locate DailyBeat QA, and capture a screenshot. Verify:

- cream pin is visually centered within the navy mask;
- padding appears equal on left/right and top/bottom;
- no pulse segment or pin tip is clipped;
- the coral center remains visible at launcher size;
- the mark is distinct from generic notes, maps, and heart-monitor icons.

- [ ] **Step 5: Verify installed-app launch**

Run:

```powershell
adb -s ZD2232FCR5 logcat -c
adb -s ZD2232FCR5 shell am force-stop com.dailybeat.app.qa
adb -s ZD2232FCR5 shell am start -W -n com.dailybeat.app.qa/com.dailybeat.app.MainActivity
adb -s ZD2232FCR5 shell pidof com.dailybeat.app.qa
adb -s ZD2232FCR5 logcat -d -b crash '*:E'
```

Expected: `Status: ok`, a live process ID, and no crash entry for `com.dailybeat.app.qa`.

- [ ] **Step 6: Record completion without cutting an icon-only release**

Run:

```powershell
git status --short
git log -3 --oneline
```

Expected: clean worktree with the design, test, and icon commits present. Do not tag or publish until the production-readiness blockers are addressed.
