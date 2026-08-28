# DailyBeat UI Design (v2.0)

Figma MCP was unavailable during this build. Design follows Material 3 with an IPS / institutional palette suitable for field officers.

## Visual language

| Element | Value |
|---------|--------|
| Primary | Navy `#0D1B3E` — authority, formality |
| Secondary | Gold `#C9A227` — accent for labels and highlights |
| Background | `#F5F7FA` — low glare for long reading |
| Typography | Sans-serif hierarchy (headline 28/22, body 16/14) |

## Navigation (4 tabs)

1. **Today** — dashboard, quick add, event list, voice FAB
2. **Diary** — generate from logged or pasted events, edit, share PDF
3. **History** — scroll past diaries, tap to open that date
4. **Settings** — officer profile, capture toggles, model import, geofences

## First-run onboarding

Three steps: welcome → officer name → privacy/permissions summary.

## Accessibility

- Large tap targets on primary actions
- Error text uses theme `error` color
- Empty states with title + guidance text

## Architecture

- Jetpack Compose + Navigation Compose
- ViewModels per screen
- Room for events, diaries, places
- SharedPreferences for settings
- No cloud components
