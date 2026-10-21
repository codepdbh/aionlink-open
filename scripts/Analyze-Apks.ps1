param([switch]$SkipJadx)
$ErrorActionPreference = 'Stop'
$argsList = @((Join-Path $PSScriptRoot 'analyze_apks.py'))
if ($SkipJadx) { $argsList += '--skip-jadx' }
& python @argsList
if ($LASTEXITCODE -ne 0) { throw "APK analysis failed: $LASTEXITCODE" }
