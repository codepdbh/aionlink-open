$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$items = @(
    @{Repo='iBotPeaches/Apktool'; Tag='v3.0.3'; Asset='apktool_3.0.3.jar'; Hash='dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423'},
    @{Repo='skylot/jadx'; Tag='v1.5.6'; Asset='jadx-1.5.6.zip'; Hash='545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974'}
)
New-Item -ItemType Directory -Force (Join-Path $root 'tools') | Out-Null
foreach ($item in $items) {
    $target = Join-Path $root ('tools/' + $item.Asset)
    if (!(Test-Path -LiteralPath $target)) {
        $partial = "$target.partial"
        Invoke-WebRequest "https://github.com/$($item.Repo)/releases/download/$($item.Tag)/$($item.Asset)" -OutFile $partial
        if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLower() -ne $item.Hash) { throw "Integrity mismatch: $partial" }
        Move-Item -LiteralPath $partial -Destination $target
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower() -ne $item.Hash) { throw "Integrity mismatch: $target" }
    Write-Output "$($item.Asset): SHA-256 verified"
}
$jadx = Join-Path $root 'tools/jadx-1.5.6'
if (!(Test-Path -LiteralPath $jadx)) { Expand-Archive -LiteralPath (Join-Path $root 'tools/jadx-1.5.6.zip') -DestinationPath $jadx }
