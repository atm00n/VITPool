param(
    [string]$AndroidRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$requiredFiles = @(
    "$AndroidRoot\app\google-services.json",
    "$AndroidRoot\keystore.properties"
)

$missing = @()
foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        $missing += $file
    }
}

if ($missing.Count -gt 0) {
    Write-Error ("Missing release inputs:`n" + ($missing -join "`n"))
    exit 1
}

$keystoreProps = Get-Content "$AndroidRoot\keystore.properties" | Where-Object { $_ -match "^storeFile=" }
if (-not $keystoreProps) {
    Write-Error "keystore.properties must include storeFile=..."
    exit 1
}

$storeFile = $keystoreProps -replace "^storeFile=", ""
$storePath = Join-Path $AndroidRoot $storeFile
if (-not (Test-Path $storePath)) {
    Write-Error "Release keystore does not exist: $storePath"
    exit 1
}

Write-Host "Release inputs are present."
