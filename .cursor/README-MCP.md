# Cursor MCP — Figma

DailyBeat UI is already implemented in code (`docs/DESIGN.md`). Figma MCP is optional for design iterations.

## Remote Figma (recommended by Figma)

This repo includes `.cursor/mcp.json` pointing at Figma's official remote server.

**On your Mac (local Cursor):**

1. `git pull origin main` (use **main**, not `cursor/android-skeleton-cc46`)
2. Open folder in Cursor → **Settings → Tools & MCP**
3. Enable **Figma** → complete **OAuth in browser** (one-time, must be you)
4. Or install via chat: `/add-plugin figma` then Connect in MCP settings

**Cloud agents:** OAuth must succeed in the cloud agent environment; if Figma shows `error` in MCP status, use local Cursor with OAuth or the desktop server below.

## Desktop Figma MCP (local Mac only)

If remote OAuth fails in Cursor (known issue), use Figma desktop app:

1. Figma desktop → Dev Mode → enable MCP → copy URL (usually `http://127.0.0.1:3845/mcp`)
2. Add to your **user** MCP config or replace entry in `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "figma-desktop": {
      "url": "http://127.0.0.1:3845/mcp"
    }
  }
}
```

## Using with an agent

Once connected, paste a frame URL:

> Use this Figma frame as the design reference for the Diary screen: https://www.figma.com/design/...

No Figma file is required — the app ships with Material 3 UI on `main` v2.0.0.

## Security

Do **not** commit Figma personal access tokens or API keys into this repo. Use OAuth or local desktop MCP only.
