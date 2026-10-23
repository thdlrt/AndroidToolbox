param(
    [ValidateSet('Install','Enable','Disable','Status','QR')][string]$Action='Status',
    [string]$Root=(Join-Path $env:LOCALAPPDATA 'AndroidToolbox/CodexWebUI')
)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7 (pwsh).'}
$Root=[IO.Path]::GetFullPath($Root)
$PinnedCommit='269d797196edfd1db134d12c4dda2d9f716cc1db'
$DataRoot=[IO.Path]::GetFullPath($(if($env:CODEX_WEBUI_DATA_DIR){$env:CODEX_WEBUI_DATA_DIR}else{Join-Path $env:LOCALAPPDATA 'CodexWebUI'}))
$Launcher=Join-Path $DataRoot 'desktop-bridge/runtime/windows-v3/codex-webui-bridge.exe'
$Patch=Join-Path $PSScriptRoot 'codex-webui-lan.patch'
function Check-Exit([string]$Operation){if($LASTEXITCODE -ne 0){throw "$Operation failed (exit $LASTEXITCODE)."}}
if($Action -eq 'Install'){
    if([int](& node -p 'process.versions.node.split(".")[0]') -lt 20){throw 'Node.js 20+ is required.'}
    if(!(Test-Path -LiteralPath $Patch)){throw 'Download codex-webui-lan.patch beside this script.'}
    if(!(Test-Path -LiteralPath $Root)){
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Root) | Out-Null
        & git -c http.sslBackend=schannel -c http.version=HTTP/1.1 clone --depth 1 https://github.com/DusuWorks/codex-webui.git $Root
        Check-Exit 'Clone Codex WebUI'
    }
    if(!(Test-Path -LiteralPath (Join-Path $Root '.git'))){throw 'The destination is not a Git checkout; choose an empty folder.'}
    $Head=(& git -C $Root rev-parse HEAD).Trim();Check-Exit 'Read checkout version'
    if($Head -ne $PinnedCommit){
        $Changes=& git -C $Root status --porcelain;Check-Exit 'Inspect local changes'
        if($Changes){throw 'Checkout has local changes. Preserve them and choose another installation folder.'}
        & git -C $Root -c http.sslBackend=schannel -c http.version=HTTP/1.1 fetch --depth 1 origin $PinnedCommit;Check-Exit 'Fetch tested version'
        & git -C $Root checkout --detach $PinnedCommit;Check-Exit 'Select tested version'
    }
    & git -C $Root apply --reverse --check $Patch 2>$null
    if($LASTEXITCODE -ne 0){
        & git -C $Root apply --check $Patch;Check-Exit 'Check LAN compatibility patch'
        & git -C $Root apply $Patch;Check-Exit 'Apply LAN compatibility patch'
    }
    Push-Location $Root
    try{
        & npm.cmd install --no-audit --no-fund;Check-Exit 'Install dependencies'
        & npm.cmd run setup;Check-Exit 'Initialize WebUI'
        & npm.cmd test;Check-Exit 'Validate WebUI'
        # Verify every recursive installer target is beneath the explicitly selected data root.
        $Runtime=[IO.Path]::GetFullPath((Join-Path $DataRoot 'desktop-bridge/runtime'))
        if(!$Runtime.StartsWith($DataRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid runtime installation boundary'}
        & pwsh -NoProfile -File scripts/install-desktop-bridge.ps1 -DataDir $DataRoot;Check-Exit 'Install Desktop launcher'
    }finally{Pop-Location}
    Write-Output 'Installed without changing the running Desktop app. When ready, run -Action Enable, then fully exit and reopen Codex Desktop.'
    return
}
if($Action -eq 'Status'){
    $Active=[Environment]::GetEnvironmentVariable('CODEX_CLI_PATH','User')
    [pscustomobject]@{Repository=$Root;Installed=(Test-Path -LiteralPath $Launcher);EnabledForNextLaunch=($Active -eq $Launcher);Listening=[bool](Get-NetTCPConnection -State Listen -LocalPort 9526 -ErrorAction SilentlyContinue);DataDirectory=$DataRoot}
    return
}
if(!(Test-Path -LiteralPath (Join-Path $Root 'server/desktop-broker.js'))){throw 'Run -Action Install first, or pass the existing -Root.'}
if($Action -eq 'QR'){
    & node (Join-Path $Root 'scripts/token-manager.js') qr default
    Check-Exit 'Generate private connection QR'
    return
}
$Installer=Join-Path $Root 'scripts/install-desktop-bridge.ps1'
if($Action -eq 'Enable'){
    & pwsh -NoProfile -File $Installer -DataDir $DataRoot -Activate;Check-Exit 'Enable future Desktop bridge'
}else{
    & pwsh -NoProfile -File $Installer -DataDir $DataRoot -Disable;Check-Exit 'Restore previous Desktop launcher'
}
Write-Output 'Fully exit and reopen Codex Desktop when safe. This script does not terminate chats or start a second app-server.'
