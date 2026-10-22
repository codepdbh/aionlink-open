param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr', [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk
Push-Location (Join-Path $root 'android')
try {
    & ./gradlew.bat :core:test :protocol-iccoa:test :transport-bluetooth:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console plain
    if ($LASTEXITCODE -ne 0) { throw "Android checks failed: $LASTEXITCODE" }
    $dest = Join-Path $root 'artifacts/builds'
    New-Item -ItemType Directory -Force $dest | Out-Null
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination "$dest/aionlink-open-debug.apk"
    Get-FileHash -LiteralPath "$dest/aionlink-open-debug.apk"
} finally { Pop-Location }
