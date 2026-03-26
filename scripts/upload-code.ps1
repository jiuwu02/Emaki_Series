Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$syncScript = Join-Path $PSScriptRoot "sync-wiki.ps1"

& $syncScript

Push-Location $repoRoot
try {
    if (-not (Test-Path (Join-Path $repoRoot ".git"))) {
        throw "Git repository is not initialized in $repoRoot"
    }

    & git add -A

    $pendingChanges = & git status --short
    if (-not $pendingChanges) {
        Write-Host "No changes to commit."
        exit 0
    }

    & git commit -m "上传代码"
    & git push origin HEAD
}
finally {
    Pop-Location
}
