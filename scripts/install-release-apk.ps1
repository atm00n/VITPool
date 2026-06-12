param(
    [string]$AndroidRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$apk = Join-Path $AndroidRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) {
    Write-Error "Release APK not found: $apk"
    exit 1
}

adb install -r $apk
