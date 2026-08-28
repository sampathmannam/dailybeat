# DailyBeat UI — Mobbin-inspired redesign (v2.1.0)

Design patterns aligned with top journal/productivity apps on [Mobbin](https://mobbin.com) (Day One, Reflect, Notion mobile): warm canvas, soft cards, timeline events, extended FAB.

Mobbin MCP was not available in the cloud build session; this redesign applies common Mobbin catalog patterns. On your Mac with `/add-plugin mobbin` connected, search:

- `journal app home screen`
- `daily log timeline`
- `note editor mobile`
- `settings grouped list`

## Visual system

| Token | Value | Use |
|-------|-------|-----|
| Canvas | `#F8F7F4` | Screen background |
| Ink | `#0F172A` | Primary text |
| Navy | `#1E3A5F` | Buttons, selected nav |
| Gold | `#E8A317` | FAB, accents |
| Card radius | 20dp | Surfaces |
| Field radius | 16dp | Inputs |

## Components

- **Metric pills** — today stats (events / diary saved)
- **Timeline event cards** — color bar by type (manual/voice/gps/call)
- **Extended FAB** — “Voice note” on Today
- **Grouped settings** — capture, model, places
- **Outlined + filled nav icons** — selected tab state

## MCP (local Cursor)

`.cursor/mcp.json` includes Mobbin (`https://api.mobbin.com/mcp`). Authenticate via Cursor → Customize → Mobbin → Authenticate.
