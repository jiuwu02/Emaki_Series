Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wikiSourcePath = Join-Path $repoRoot "..\\Emaki Plugin Wiki"
$wikiTarget = Join-Path $repoRoot "wiki"

New-Item -ItemType Directory -Force -Path $wikiTarget | Out-Null

if (-not (Test-Path $wikiSourcePath)) {
    Write-Host "External wiki source not found. Keeping existing repository wiki at $wikiTarget"
    exit 0
}

$wikiSource = (Resolve-Path $wikiSourcePath).Path

& robocopy $wikiSource $wikiTarget /MIR /FFT /NFL /NDL /NJH /NJS /NP
$robocopyExitCode = $LASTEXITCODE

if ($robocopyExitCode -gt 7) {
    throw "Wiki sync failed. Robocopy exit code: $robocopyExitCode"
}

Write-Host "Wiki synced to $wikiTarget"
