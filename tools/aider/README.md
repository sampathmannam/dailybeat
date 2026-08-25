# Aider + repomix + Ollama workflow

Two modes, one set of tools. Pick by the task.

| Mode   | Model              | Cost          | Speed          | Best for |
|--------|--------------------|---------------|----------------|----------|
| Local  | Qwen3-4B (Ollama)  | Free          | Slow on 4GB    | Daily dev, TODO-loop, offline work |
| Cloud  | MiniMax-M3         | ~$0.05-0.30/t | Fast           | Big refactors, 1M-context questions |

## One-time setup

```powershell
# Done if you followed the scaffold:
pip install aider-chat
npm install -g repomix
winget install Ollama.Ollama            # or download from https://ollama.com/download
ollama pull qwen3:4b                   # ~1.5GB, one-time
ollama serve                            # leave running in a separate shell

# Cloud only:
$env:MINIMAX_API_KEY = "eyJ..."         # from https://platform.minimax.io
```

## Files in this scaffold

| File                          | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `.aider.conf.yml`             | Defaults to local. Override via `-Model` or `$env:AIDER_MODEL`. |
| `.aiderignore`                | Files Aider must never read.                               |
| `tools/aider/aider.ps1`       | Launcher: local OR cloud, repomix if needed.               |
| `tools/aider/agent-loop.ps1`  | Continuous agent: runs Aider on each TODO.md item.         |
| `tools/aider/README.md`       | This file.                                                 |

## Usage

### Interactive

```powershell
# Local:
.\tools\aider\aider.ps1 -Local -Mode ask

# Cloud (whole repo pre-loaded):
$env:MINIMAX_API_KEY = "eyJ..."
.\tools\aider\aider.ps1 -Mode ask
```

### One-shot task

```powershell
.\tools\aider\aider.ps1 -Local -Mode task   # prompted for the message
```

### Continuous agent (the "till end of project" mode)

1. Create `TODO.md` at the repo root:

   ```markdown
   # dailybeat — backlog

   <!-- test-command: pnpm test -->

   - [ ] Set up package.json with TypeScript + Vite
   - [ ] Add a CLI entry that takes a date and prints a beat
   - [ ] Add unit test for the date parser
   - [x] Initialize the repo
   ```

   The `<!-- test-command: ... -->` line is optional. If you omit it,
   the loop falls back to `tools/agent-test.ps1` / `.sh`, or runs
   without a test rail if neither exists.

2. Run the loop:

   ```powershell
   # Local (free):
   .\tools\aider\agent-loop.ps1 -Local

   # Cloud (faster, costs money):
   $env:MINIMAX_API_KEY = "eyJ..."
   .\tools\aider\agent-loop.ps1 -Cloud

   # Cap to first 5 tasks:
   .\tools\aider\agent-loop.ps1 -Local -MaxTasks 5

   # Stop on first failure:
   .\tools\aider\agent-loop.ps1 -Local -FailFast
   ```

3. The loop:
   - Reads `TODO.md`, finds every `- [ ]` line.
   - For each, runs Aider on that line as a single task.
   - Appends the test-command guard rail to the prompt.
   - On success: marks `- [x]`, commits.
   - On failure: marks `- [/] (failed: timestamp)`, moves on (or stops if `-FailFast`).
   - Each task's log: `%TEMP%\aider-task-N.log`.
   - Ctrl+C to stop; next run picks up where this one left off.

## Customizing the test rail

Pick whichever fits your project:

- **Inline** in TODO.md: `<!-- test-command: pnpm test -->`
- **Script** at `tools/agent-test.ps1` (PowerShell) or `tools/agent-test.sh` (bash). Exit 0 = pass, non-zero = fail.
- **None** — the loop just trusts Aider's verdict. Risky but fine for tiny changes.

## Switching model without editing the config

```powershell
# Lighter local model (more VRAM headroom):
$env:AIDER_MODEL = "ollama_chat/qwen3:1.7b"

# Code-specialized older model:
$env:AIDER_MODEL = "ollama_chat/qwen2.5-coder:3b"

# Or inline:
.\tools\aider\aider.ps1 -Model "ollama_chat/qwen3:1.7b" -Mode ask
```

## Constraints and ceilings

**Local (Qwen3-4B, RTX 2050 4GB):**
- 32K context. No repomix pre-load (the snapshot won't fit).
- Tree-sitter repo-map only (2048 tokens).
- Quality drops on multi-file refactors. If the model loops on a bug, switch to cloud.
- Don't run other GPU-heavy things while Aider is working.

**Cloud (M3):**
- 1M context. The repomix snapshot fits comfortably.
- The agent loop in cloud mode can rack up real money on long TODOs. Use `-MaxTasks`.

## What this is NOT

- Not a replacement for tests. The test rail is a sanity check, not QA.
- Not a CI step. Interactive / dev-loop only.
- Not a code generator. The model doesn't know your future APIs.
