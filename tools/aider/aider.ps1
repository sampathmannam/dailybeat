#requires -Version 5.1
<#
.SYNOPSIS
  Aider launcher. One script, two modes (local Ollama or cloud M3).

.DESCRIPTION
  Local mode (default if -Local): runs against Ollama at
  http://localhost:11434. Free, offline, fits RTX 2050 4GB.

  Cloud mode (default otherwise): builds a repomix snapshot of the
  repo, then launches Aider with the whole repo pre-loaded. Best for
  questions that need full 1M-token context. Requires
  $env:MINIMAX_API_KEY.

.PARAMETER Mode
  - ask   : launch Aider interactively (default).
  - task  : launch Aider one-shot with a message (you'll be prompted).

.PARAMETER Local
  Use the local Ollama model. No API key, no repomix pack.

.PARAMETER Model
  Override the model. Defaults:
    Local:  ollama_chat/qwen3:4b
    Cloud:  openai/MiniMax-M3

.EXAMPLE
  .\tools\aider\aider.ps1 -Local -Mode ask
  # Free, offline, against Qwen3-4B.

.EXAMPLE
  $env:MINIMAX_API_KEY = "eyJ..."
  .\tools\aider\aider.ps1 -Mode ask
  # Cloud: builds snapshot, drops into Aider against M3.

.NOTES
  Requires:
    - aider installed (pip install aider-chat)
    - repomix installed (npm install -g repomix)        [cloud only]
    - ollama installed and serving                       [local only]
    - ollama pull qwen3:4b                               [local, first time]
#>

[CmdletBinding()]
param(
    [ValidateSet('ask', 'task')]
    [string]$Mode = 'ask',

    [switch]$Local,

    [string]$Model
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot  = Resolve-Path (Join-Path $ScriptDir '..\..')
Set-Location $RepoRoot

# --- Resolve model ---
if ($Model) {
    $resolvedModel = $Model
} elseif ($Local) {
    $resolvedModel = 'ollama_chat/qwen3:4b'
} else {
    $resolvedModel = 'openai/MiniMax-M3'
}
$isLocal = $resolvedModel -like 'ollama*'

# --- Sanity checks ---
if (-not (Get-Command aider -ErrorAction SilentlyContinue)) {
    throw "aider not on PATH. Run: pip install aider-chat"
}
if ($Local -or $isLocal) {
    if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
        throw "ollama not on PATH. Install from https://ollama.com/download"
    }
    try {
        Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 3 -ErrorAction Stop | Out-Null
    } catch {
        throw "ollama is installed but not serving. Start it: ollama serve"
    }
    $modelName = $resolvedModel -replace '^ollama(_chat)?/', ''
    $tags = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 5
    if (-not ($tags.models | Where-Object { $_.name -eq $modelName -or $_.name -like "$modelName*" })) {
        Write-Host "==> First-time setup: pulling $modelName (one-time, ~1.5GB)..." -ForegroundColor Yellow
        & ollama pull $modelName
        if ($LASTEXITCODE -ne 0) { throw "ollama pull failed" }
    }
} else {
    if (-not (Get-Command repomix -ErrorAction SilentlyContinue)) {
        throw "repomix not on PATH. Run: npm install -g repomix"
    }
    if (-not $env:MINIMAX_API_KEY) {
        throw "Cloud mode needs `$env:MINIMAX_API_KEY. Get a key from https://platform.minimax.io"
    }
}

# --- Cloud prep: repomix pack ---
if (-not ($Local -or $isLocal)) {
    Write-Host "==> Building repomix snapshot..." -ForegroundColor Cyan
    & repomix --config repomix.config.json 2>&1 | Select-Object -Last 15
    if ($LASTEXITCODE -ne 0) { throw "repomix failed (exit $LASTEXITCODE)" }
    if (-not (Test-Path "$RepoRoot\repomix-output.xml")) {
        throw "repomix ran but did not produce repomix-output.xml"
    }
    $packedSize = (Get-Item "$RepoRoot\repomix-output.xml").Length
    Write-Host "==> Snapshot: $([math]::Round($packedSize/1KB, 1)) KB" -ForegroundColor Green
}

# --- Launch Aider ---
Write-Host "==> Aider: model=$resolvedModel" -ForegroundColor Cyan

switch ($Mode) {
    'ask' {
        if ($Local -or $isLocal) {
            & aider --model $resolvedModel
        } else {
            & aider --read repomix-output.xml --model $resolvedModel
        }
    }
    'task' {
        $msg = Read-Host "Task message"
        if ([string]::IsNullOrWhiteSpace($msg)) { throw "Empty message." }
        if ($Local -or $isLocal) {
            & aider --model $resolvedModel --message $msg --auto-commits
        } else {
            & aider --read repomix-output.xml --model $resolvedModel --message $msg --auto-commits
        }
    }
}
