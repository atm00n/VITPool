param(
    [string]$AndroidRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

$keystorePath = Join-Path $AndroidRoot "release\vitpool-release.jks"
$propertiesPath = Join-Path $AndroidRoot "keystore.properties"
$projectRoot = Resolve-Path (Join-Path $AndroidRoot "..")
$keytool = Join-Path $projectRoot ".tools\jdk-17\bin\keytool.exe"

if ((Test-Path $keystorePath) -and (Test-Path $propertiesPath)) {
    Write-Host "Release keystore already exists. Keeping existing signing credentials."
    exit 0
}

if (-not (Test-Path $keytool)) {
    throw "keytool not found at $keytool. Run scripts\setup_android_toolchain.ps1 first."
}

New-Item -ItemType Directory -Force (Split-Path $keystorePath -Parent) | Out-Null

function New-Password {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).Replace("+", "A").Replace("/", "B").Replace("=", "C")
}

$storePassword = New-Password
$keyPassword = $storePassword

& $keytool -genkeypair `
    -v `
    -keystore $keystorePath `
    -storepass $storePassword `
    -keypass $keyPassword `
    -alias vitpool `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname "CN=VITPool, OU=VITPool, O=VITPool, L=Pune, ST=Maharashtra, C=IN"

@"
storeFile=release/vitpool-release.jks
storePassword=$storePassword
keyAlias=vitpool
keyPassword=$keyPassword
"@ | Set-Content -LiteralPath $propertiesPath -Encoding ascii

Write-Host "Release keystore created at $keystorePath"
Write-Host "Preserve android\keystore.properties and android\release\vitpool-release.jks for future APK updates."
