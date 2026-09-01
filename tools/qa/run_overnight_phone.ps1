[CmdletBinding()]
param(
    [string]$Serial = "ZD2232FCR5",
    [ValidateRange(1, 12)]
    [int]$Hours = 8,
    [ValidateRange(15, 240)]
    [int]$IntervalMinutes = 120
)

$ErrorActionPreference = "Stop"
$qaPackage = "com.dailybeat.app.patrolgrid.qa"
$testPackage = "com.dailybeat.app.patrolgrid.qa.test"
$mainActivity = "$qaPackage/com.dailybeat.app.MainActivity"
$testRunner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $repoRoot "tmp\overnight-qa\$timestamp"
$reportPath = Join-Path $runDir "morning-report.md"
$cycles = [math]::Ceiling(($Hours * 60) / $IntervalMinutes)

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if (-not ((adb devices) -match "^$Serial\s+device$")) {
    throw "Connected test device $Serial is not available."
}

@(
    "# PatrolGrid overnight QA",
    "",
    "- Device: $Serial",
    "- Test package: $qaPackage",
    "- Started: $(Get-Date -Format o)",
    "- Planned cycles: $cycles",
    "- Guardrail: only the QA package is installed and exercised.",
    ""
) | Set-Content -Encoding utf8 $reportPath

Push-Location (Join-Path $repoRoot "android")
try {
    .\gradlew.bat :app:installDebug :app:installDebugAndroidTest | Tee-Object -FilePath (Join-Path $runDir "build-install.log")

    for ($cycle = 1; $cycle -le $cycles; $cycle++) {
        $cyclePrefix = "cycle-$cycle"
        $instrumentationLog = Join-Path $runDir "$cyclePrefix-instrumentation.log"
        $instrumentationOutput = & adb -s $Serial shell am instrument -w -r -e class "com.dailybeat.app.MainNavigationTest,com.dailybeat.app.OnboardingFlowTest" $testRunner 2>&1
        $instrumentationOutput | Tee-Object -FilePath $instrumentationLog | Out-Host

        $testPassed = $LASTEXITCODE -eq 0 -and ($instrumentationOutput -join "`n") -match "OK \(\d+ tests?\)"
        & adb -s $Serial shell am force-stop $qaPackage
        $startupOutput = & adb -s $Serial shell am start -W -n $mainActivity 2>&1
        $startupOutput | Set-Content -Encoding utf8 (Join-Path $runDir "$cyclePrefix-startup.log")
        $startupMs = ($startupOutput | Select-String "^TotalTime:" | ForEach-Object {
            [int](($_.Line -split ":", 2)[1].Trim())
        } | Select-Object -First 1)

        & adb -s $Serial shell dumpsys gfxinfo $qaPackage | Set-Content -Encoding utf8 (Join-Path $runDir "$cyclePrefix-gfxinfo.txt")
        & adb -s $Serial shell dumpsys meminfo $qaPackage | Set-Content -Encoding utf8 (Join-Path $runDir "$cyclePrefix-meminfo.txt")
        & adb -s $Serial logcat -d -t 2000 | Select-String "FATAL EXCEPTION|ANR in|$qaPackage" |
            Set-Content -Encoding utf8 (Join-Path $runDir "$cyclePrefix-errors.txt")

        @(
            "## Cycle $cycle",
            "",
            "- Instrumentation: $(if ($testPassed) { 'PASS' } else { 'FAIL' })",
            "- Cold start: $(if ($startupMs) { "$startupMs ms" } else { 'not captured' })",
            "- Evidence: $cyclePrefix-instrumentation.log, $cyclePrefix-startup.log, $cyclePrefix-gfxinfo.txt, $cyclePrefix-meminfo.txt, $cyclePrefix-errors.txt",
            ""
        ) | Add-Content -Encoding utf8 $reportPath

        if (-not $testPassed) {
            throw "Instrumentation failed during cycle $cycle. Review $instrumentationLog."
        }
        if ($cycle -lt $cycles) {
            Start-Sleep -Seconds ($IntervalMinutes * 60)
        }
    }
} finally {
    "No cleanup was executed." | Add-Content -Encoding utf8 $reportPath
    "- Finished: $(Get-Date -Format o)" | Add-Content -Encoding utf8 $reportPath
    Pop-Location
}

Write-Host "Overnight QA evidence: $runDir"
