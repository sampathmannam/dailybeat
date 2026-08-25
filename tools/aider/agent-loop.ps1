#requires -Version 5.1
<#
.SYNOPSIS
  Continuous agent loop: drives Aider through TODO.md until every
  unchecked item is done, or until the user stops it.

.DESCRIPTION
  Reads TODO.md from the repo root. For each `- [ ]` line, runs Aider
  on that line as a single task, commits the result, and moves to the
  next. On failure, marks the item `- [/]` and continues (or stops
  with -FailFast).

  Why this and not a true autonomous agent?
  Aider is a pair-programmer, not an agent loop. This wrapper is the
  thin loop on top. For 4B local models, you get the most reliable
  "runs continuously" behavior by feeding it one well-scoped task at
  a time and letting it commit before the next.

  Per-task "did the change break anything?" check is delegated to a
  project-supplied hook at `tools/agent-test.ps1` (PowerShell) or
  `tools/agent-test.sh` (bash). If neither exists, the loop just
  commits the model's changes and moves on. This keeps the loop
  stack-agnostic.

.PARAMETER TodoPath
  Path to TODO.md. Default: ./TODO.md (repo root).

.PARAMETER Local
  Use the local Ollama model (default if neither -Local nor -Cloud).

.PARAMETER Cloud
  Force the cloud M3 model. Requires $env:MINIMAX_API_KEY.

.PARAMETER MaxTasks
  Stop after this many successful tasks, even if more remain.
  Useful for capping a long unattended run.

.PARAMETER FailFast
  If set, stop the loop on the first task that exits non-zero.
  Default is to mark failed and continue.

.EXAMPLE
  .\tools\aider\agent-loop.ps1 -Local -MaxTasks 5
  # Process up to 5 TODO items with the local Qwen3-4B.

.EXAMPLE
  $env:MINIMAX_API_KEY = "eyJ..."
  .\tools\aider\agent-loop.ps1 -Cloud -FailFast
  # Cloud model, stop on first failure.

.NOTES
  TODO.md format:
    # Project title
    - [ ] First task
    - [x] Already-done task (skipped)
    - [/] In-progress (skipped, will be retried after manual fix)
    - [ ] Second task

  Optional header directive (top of TODO.md):
    <!-- test-command: pnpm test -->

  This sets the test command inline. If the directive is missing, the
  loop falls back to `tools/agent-test.ps1` or `tools/agent-test.sh`.
#>

[CmdletBinding()]
param(
    [string]$TodoPath = 'TODO.md',
    [switch]$Local,
    [switch]$Cloud,
    [int]$MaxTasks = 0,
    [switch]$FailFast
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot  = Resolve-Path (Join-Path $ScriptDir '..\..')
Set-Location $RepoRoot

$TodoFull = Join-Path $RepoRoot $TodoPath
if (-not (Test-Path $TodoFull)) {
    throw "TODO file not found: $TodoFull"
}

# --- Sanity checks ---
if (-not (Get-Command aider -ErrorAction SilentlyContinue)) {
    throw "aider not on PATH. Run: pip install aider-chat"
}
if (-not $Cloud) {
    if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
        throw "ollama not on PATH. Install from https://ollama.com/download"
    }
    try {
        Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 3 -ErrorAction Stop | Out-Null
    } catch {
        throw "ollama is installed but not serving. Start it with: ollama serve"
    }
}
if ($Cloud -and -not $env:MINIMAX_API_KEY) {
    throw "Cloud mode needs `$env:MINIMAX_API_KEY. Get a key from https://platform.minimax.io"
}

# --- Resolve model ---
if ($Cloud) {
    $model = 'openai/MiniMax-M3'
    $cloudMode = $true
} else {
    $model = if ($env:AIDER_MODEL) { $env:AIDER_MODEL } else { 'ollama_chat/qwen3:4b' }
    if ($model -notlike 'ollama*') {
        Write-Warning "AIDER_MODEL is '$model' but Local mode expects an ollama* model. Falling back to ollama_chat/qwen3:4b."
        $model = 'ollama_chat/qwen3:4b'
    }
    $cloudMode = $false
}

# --- Verify model is pulled ---
if ($model -like 'ollama*') {
    $modelName = $model -replace '^ollama(_chat)?/', ''
    $tags = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 5
    if (-not ($tags.models | Where-Object { $_.name -eq $modelName -or $_.name -like "$modelName*" })) {
        Write-Host "Model '$modelName' not pulled yet. Pulling now (one-time ~1.5GB)..." -ForegroundColor Yellow
        & ollama pull $modelName
        if ($LASTEXITCODE -ne 0) { throw "ollama pull failed" }
    }
}

# --- Resolve test command ---
$testCommand = $null
# 1. Inline directive in TODO.md: <!-- test-command: ... -->
$todoRaw = Get-Content $TodoFull -Raw -Encoding UTF8
$dirMatch = [regex]::Match($todoRaw, '<!--\s*test-command:\s*([^>]+?)\s*-->')
if ($dirMatch.Success) {
    $testCommand = $dirMatch.Groups[1].Value.Trim()
    Write-Host "==> Test command (from TODO.md): $testCommand" -ForegroundColor DarkCyan
} elseif (Test-Path "$RepoRoot\tools\agent-test.ps1") {
    $testCommand = "& '$RepoRoot\tools\agent-test.ps1'"
    Write-Host "==> Test command: tools/agent-test.ps1" -ForegroundColor DarkCyan
} elseif (Test-Path "$RepoRoot\tools\agent-test.sh") {
    $testCommand = "bash '$RepoRoot\tools\agent-test.sh'"
    Write-Host "==> Test command: tools/agent-test.sh" -ForegroundColor DarkCyan
} else {
    Write-Host "==> No test command configured. The loop will trust Aider's verdict." -ForegroundColor DarkYellow
    Write-Host "    (Add a `<!-- test-command: pnpm test -->` line to TODO.md," -ForegroundColor DarkYellow
    Write-Host "     or create tools/agent-test.ps1 to enable.)" -ForegroundColor DarkYellow
}

Write-Host "==> Agent loop: model=$model, todo=$TodoPath" -ForegroundColor Cyan
if ($MaxTasks -gt 0) { Write-Host "    Max tasks this run: $MaxTasks" -ForegroundColor Cyan }
Write-Host ""

# --- Parse TODO ---
$pattern = '^[ \t]*-\s\[\s\]\s+(.+)$'
$items = [regex]::Matches($todoRaw, $pattern, 'Multiline') | ForEach-Object { $_.Groups[1].Value.Trim() }

if (-not $items -or $items.Count -eq 0) {
    Write-Host "No unchecked items in $TodoPath. Agent loop has nothing to do." -ForegroundColor Yellow
    return
}
Write-Host "Found $($items.Count) pending task(s)." -ForegroundColor Green
Write-Host ""

# --- Loop ---
$done = 0
foreach ($task in $items) {
    if ($MaxTasks -gt 0 -and $done -ge $MaxTasks) {
        Write-Host "Hit MaxTasks=$MaxTasks. Stopping." -ForegroundColor Yellow
        break
    }

    $shortTask = if ($task.Length -gt 60) { $task.Substring(0, 57) + '...' } else { $task }
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "Task $($done + 1)/$($items.Count): $shortTask" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan

    $testRail = if ($testCommand) {
@"

After making your changes, run the project test command:
    $testCommand
and fix anything that breaks. Only mark the task done (in TODO.md) if
the tests pass. If you cannot complete the task, leave a note in your
final commit message explaining what's blocking.
"@
    } else {
@"

If you have a way to verify your change (running a build, a quick test,
or even reading the file back to spot syntax errors), do so before
committing. If you cannot verify, commit anyway and note the risk in
the commit message.
"@
    }

    $prompt = "$task$testRail"

    $aiderArgs = @(
        '--model', $model,
        '--message', $prompt,
        '--auto-commits',
        '--no-stream'
    )
    if ($cloudMode) { $aiderArgs += '--map-tokens', '8192' } else { $aiderArgs += '--map-tokens', '2048' }

    $logPath = Join-Path $env:TEMP "aider-task-$($done + 1).log"
    Write-Host "  log: $logPath" -ForegroundColor DarkGray

    & aider @aiderArgs 2>&1 | Tee-Object -FilePath $logPath | Select-Object -Last 40
    $exit = $LASTEXITCODE

    if ($exit -eq 0) {
        $todoRaw = $todoRaw -replace [regex]::Escape("- [ ] $task"), "- [x] $task"
        [System.IO.File]::WriteAllText($TodoFull, $todoRaw, [System.Text.Encoding]::UTF8)
        Write-Host "  -> committed" -ForegroundColor Green
        $done++
    } else {
        Write-Host "  -> failed (exit $exit). Marked as in-progress for retry." -ForegroundColor Red
        $todoRaw = $todoRaw -replace [regex]::Escape("- [ ] $task"), "- [/] $task (failed: $(Get-Date -Format 'yyyy-MM-dd HH:mm'))"
        [System.IO.File]::WriteAllText($TodoFull, $todoRaw, [System.Text.Encoding]::UTF8)
        if ($FailFast) { throw "Task failed and -FailFast was set. Last log: $logPath" }
    }
    Write-Host ""
}

Write-Host "==> Agent loop complete. Processed $done task(s)." -ForegroundColor Green
