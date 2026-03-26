Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wikiSource = (Resolve-Path (Join-Path $repoRoot "..\\Emaki Plugin Wiki")).Path
$wikiTarget = Join-Path $repoRoot "wiki"

if (-not (Test-Path $wikiSource)) {
    throw "Wiki source folder not found: $wikiSource"
}

New-Item -ItemType Directory -Force -Path $wikiTarget | Out-Null

& robocopy $wikiSource $wikiTarget /MIR /FFT /NFL /NDL /NJH /NJS /NP
$robocopyExitCode = $LASTEXITCODE

if ($robocopyExitCode -gt 7) {
    throw "Wiki sync failed. Robocopy exit code: $robocopyExitCode"
}

Write-Host "Wiki synced to $wikiTarget"
