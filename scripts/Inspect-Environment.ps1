$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'artifacts/reports'
New-Item -ItemType Directory -Force $output | Out-Null
$sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$jdk = 'C:\Program Files\Android\Android Studio\jbr'
$result = [ordered]@{
    Timestamp = (Get-Date -Format o)
    JavaOnPath = ((& java --version) | Out-String)
    BuildJava = ((& "$jdk/bin/java.exe" --version) | Out-String)
    Python = (& python --version | Out-String)
    Git = (& git --version | Out-String)
    Adb = (& "$sdk/platform-tools/adb.exe" version | Out-String)
    Sdk = $sdk
    Platforms = @(Get-ChildItem "$sdk/platforms" | Select-Object -ExpandProperty Name)
    BuildTools = @(Get-ChildItem "$sdk/build-tools" | Select-Object -ExpandProperty Name)
    Ndk = @(Get-ChildItem "$sdk/ndk" | Select-Object -ExpandProperty Name)
    OriginalApks = @(Get-ChildItem -LiteralPath $root -Filter '*.apk' | ForEach-Object { @{Name=$_.Name;Size=$_.Length;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
}
$result | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 "$output/environment.json"
$result | ConvertTo-Json -Depth 8
