# DeepSeek Cloud-Only Integration Implementation Plan

**Goal:** Make DailyBeat use DeepSeek as its only diary-generation provider and publish a verified Android release.

**Architecture:** Reuse the existing OpenAI-compatible `CloudLlmClient`, add DeepSeek as a first-class provider with its official chat-completions endpoint, and fail clearly when cloud configuration or connectivity is unavailable. The API key remains runtime user configuration in encrypted app storage and is never committed or embedded in the APK.

**Tech Stack:** Kotlin, Android Compose, OkHttp, encrypted SharedPreferences, Gradle, GitHub Actions, DeepSeek OpenAI-compatible API.

## Global Constraints

- No local-model or deterministic fallback for diary generation.
- Never commit or embed a DeepSeek API key.
- Use `deepseek-chat` by default.
- Keep required CI tests fail-fast; do not use `continue-on-error`.
- Release only after fresh build and CI evidence.

### Task 1: DeepSeek provider

Files:
- Modify `android/app/src/main/java/com/dailybeat/app/data/settings/AppSettings.kt`
- Modify `android/app/src/main/java/com/dailybeat/app/cloud/CloudLlmClient.kt`
- Modify `android/app/src/main/java/com/dailybeat/app/cloud/ReportGenerator.kt`
- Modify `android/app/build.gradle.kts`

Implement the provider enum, defaults, DeepSeek endpoint, and cloud-only failure behavior. Keep the existing encrypted runtime key entry flow.

Verification:
- `android/gradlew.bat testDebugUnitTest`
- Static inspection confirms no `localGenerator.generateForDay` call remains in report generation.

### Task 2: Regression tests and documentation

Files:
- Create or modify cloud provider unit tests under `android/app/src/test/java/com/dailybeat/app/cloud/`
- Modify `README.md`, `CHANGELOG.md`, and `docs/RELEASE.md`

Test DeepSeek request selection, missing-key failure, disabled-cloud failure, and no-fallback failure. Document runtime setup without a real key.

Verification:
- `android/gradlew.bat testDebugUnitTest`
- `python -m pytest scripts/tests -q`

### Task 3: CI and release

Files:
- Modify `.github/workflows/ci.yml`
- Modify `android/app/build.gradle.kts`

Use the ARM64 emulator matching the MediaPipe native library, build debug and release APKs, run instrumentation tests, upload checksums, and bump the version to 3.3.0.

Verification:
- Push a feature branch.
- Open a PR.
- Confirm unit, instrumentation, and Python tests pass.
- Confirm APK artifacts and SHA-256 checksums exist.
- Merge only after all required checks pass and create the release.
