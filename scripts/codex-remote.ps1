param(
    [ValidateSet('Install','Configure','Start','Stop','Status','Info','Hub','Runner')][string]$Action='Status',
    [string]$Root=(Join-Path $env:LOCALAPPDATA 'AndroidToolbox/CodexRemote'),
    [ValidateRange(1024,65535)][int]$Port=3006,
    [string]$ListenHost='127.0.0.1',
    [string[]]$WorkspaceRoot=@(),
    [switch]$AllDrives,
    [string]$CodexHome=''
)
$ErrorActionPreference='Stop'
$Root=[IO.Path]::GetFullPath($Root)
$taskHapi=Join-Path $Root 'node_modules/@twsxtd/hapi-win32-x64/bin/hapi.exe'
$taskCodexBin=Join-Path $Root 'node_modules/@openai/codex-win32-x64/vendor/x86_64-pc-windows-msvc/bin'
$taskData=Join-Path $Root 'data'
$taskConfig=Join-Path $Root 'remote.json'
$taskLauncher=Join-Path $Root 'codex-remote.ps1'
function Read-Config { if(-not(Test-Path -LiteralPath $taskConfig)){throw 'Run Install first.'}; Get-Content -Raw -LiteralPath $taskConfig | ConvertFrom-Json }
function Workspace-Roots($Settings){
    if($Settings.allDrives){
        return @([IO.DriveInfo]::GetDrives() | Where-Object {$_.IsReady -and $_.DriveType -ne [IO.DriveType]::CDRom} | ForEach-Object {$_.RootDirectory.FullName})
    }
    if($Settings.workspaceRoots){return @($Settings.workspaceRoots)}
    if($Settings.workspaceRoot){return @($Settings.workspaceRoot)}
    throw 'No workspace directories configured. Run -Action Configure -AllDrives.'
}
function Resolve-WorkspaceRoots {
    if($AllDrives -and $WorkspaceRoot.Count){throw 'Use -AllDrives or -WorkspaceRoot, not both.'}
    return @($WorkspaceRoot | ForEach-Object {
        $taskDirectory=Get-Item -LiteralPath $_
        if(-not $taskDirectory.PSIsContainer){throw 'WorkspaceRoot must name a directory.'}
        $taskDirectory.FullName
    } | Select-Object -Unique)
}
if($Action -eq 'Configure'){
    if(-not $AllDrives -and -not $WorkspaceRoot.Count){throw 'Specify -AllDrives or -WorkspaceRoot.'}
    $taskSettings=Read-Config
    $taskRoots=@(Resolve-WorkspaceRoots)
    $taskSettings | Add-Member -NotePropertyName allDrives -NotePropertyValue ([bool]$AllDrives) -Force
    $taskSettings | Add-Member -NotePropertyName workspaceRoots -NotePropertyValue $taskRoots -Force
    if($taskSettings.PSObject.Properties['workspaceRoot']){$taskSettings.PSObject.Properties.Remove('workspaceRoot')}
    [IO.File]::WriteAllText($taskConfig,($taskSettings | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    if([IO.Path]::GetFullPath($PSCommandPath) -ne $taskLauncher){Copy-Item -LiteralPath $PSCommandPath -Destination $taskLauncher -Force}
    Write-Output 'Workspace access updated. Run -Action Start to refresh the runner; existing hub data is preserved.'
    exit
}
function Detached([string]$Role){
    # Win32_Process.Create starts from WmiPrvSE, outside the invoking terminal/app job.
    $taskStartup=New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{ShowWindow=[uint16]0}
    $taskCommand='powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+$taskLauncher+'" -Action '+$Role+' -Root "'+$Root+'"'
    $taskResult=Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine=$taskCommand;CurrentDirectory=$Root;ProcessStartupInformation=$taskStartup}
    if($taskResult.ReturnValue -ne 0){throw "Detached launch failed: $($taskResult.ReturnValue)"}
    return $taskResult.ProcessId
}
if($Action -eq 'Install'){
    if($Root.Contains('"') -or $Root.Contains("`n")){throw 'Invalid install path'}
    $taskRoots=@(Resolve-WorkspaceRoots)
    New-Item -ItemType Directory -Force -Path $Root,$taskData | Out-Null
    # Includes hub/runner credentials and logs. Keep these local to this Windows account.
    $taskSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $Root /inheritance:r /grant:r "*${taskSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Cannot protect the host data directory'}
    & npm.cmd install --prefix $Root --save-exact --ignore-scripts --no-audit --no-fund '@twsxtd/hapi@0.30.7' '@openai/codex@0.164.0' --registry=https://registry.npmmirror.com
    if($LASTEXITCODE -ne 0){throw 'HAPI installation failed; retry using the official npm registry if the mirror is unavailable.'}
    if(-not(Test-Path -LiteralPath $taskHapi)){throw 'HAPI Windows binary is missing'}
    $taskCodexVersion=& (Join-Path $taskCodexBin 'codex.exe') --version
    if($taskCodexVersion -ne 'codex-cli 0.164.0'){throw 'Unexpected Codex CLI version'}
    if($CodexHome){$CodexHome=[IO.Path]::GetFullPath($CodexHome)}
    $taskSaved=@{port=$Port;listenHost=$ListenHost;workspaceRoots=$taskRoots;allDrives=([bool]$AllDrives -or -not $taskRoots.Count);codexHome=$CodexHome}
    [IO.File]::WriteAllText($taskConfig,($taskSaved | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    if([IO.Path]::GetFullPath($PSCommandPath) -ne $taskLauncher){Copy-Item -LiteralPath $PSCommandPath -Destination $taskLauncher -Force}
    if(-not(Test-Path -LiteralPath (Join-Path $taskData 'settings.json'))){
        $taskBytes=New-Object byte[] 32;[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($taskBytes)
        $taskSeed=@{cliApiToken=[Convert]::ToBase64String($taskBytes).TrimEnd('=').Replace('+','-').Replace('/','_');apiUrl="http://127.0.0.1:$Port"} | ConvertTo-Json
        [IO.File]::WriteAllText((Join-Path $taskData 'settings.json'),$taskSeed,[Text.UTF8Encoding]::new($false))
    }
    Write-Output 'Installed HAPI 0.30.7 + isolated Codex CLI 0.164.0. Run -Action Start, then -Action Info.'
    exit
}
$taskSettings=Read-Config
$env:HAPI_HOME=$taskData
$env:HAPI_API_URL="http://127.0.0.1:$($taskSettings.port)"
$env:HAPI_LISTEN_HOST=$taskSettings.listenHost
$env:HAPI_LISTEN_PORT=[string]$taskSettings.port
$env:HAPI_ANDROID_PUSH='off';$env:HAPI_IOS_PUSH='off';$env:TELEGRAM_NOTIFICATION='false';$env:SERVERCHAN_NOTIFICATION='false'
$env:PATH="$taskCodexBin;$(Join-Path $Root 'node_modules/.bin');$env:PATH"
if($taskSettings.codexHome){$env:CODEX_HOME=$taskSettings.codexHome}
if($Action -eq 'Hub'){& $taskHapi hub --no-relay *> (Join-Path $taskData 'hub.log');exit $LASTEXITCODE}
if($Action -eq 'Runner'){
    $taskRunnerArgs=@('runner','start')
    foreach($taskWorkspace in @(Workspace-Roots $taskSettings)){$taskRunnerArgs+=@('--workspace-root',$taskWorkspace)}
    if($taskRunnerArgs.Count -eq 2){throw 'No mounted workspace drives are available.'}
    & $taskHapi @taskRunnerArgs *> (Join-Path $taskData 'runner-launch.log');exit $LASTEXITCODE
}
if($Action -eq 'Start'){
    $taskListener=Get-NetTCPConnection -LocalPort $taskSettings.port -State Listen -ErrorAction SilentlyContinue
    if($taskListener){
        $taskProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$($taskListener[0].OwningProcess)"
        if($taskProcess.ExecutablePath -ne $taskHapi){throw 'The selected port belongs to another application.'}
    }else{
        $taskPid=Detached 'Hub';Set-Content -LiteralPath (Join-Path $taskData 'hub-launch.pid') -Value $taskPid
        $taskReady=$false
        for($taskTry=0;$taskTry -lt 30;$taskTry++){
            try{$taskResponse=Invoke-WebRequest -UseBasicParsing -Uri "$env:HAPI_API_URL/" -TimeoutSec 2;if($taskResponse.StatusCode -eq 200){$taskReady=$true;break}}catch{}
            Start-Sleep -Milliseconds 500
        }
        if(-not $taskReady){throw 'HAPI did not start; inspect data/hub.log.'}
    }
    $null=Detached 'Runner'
    Write-Output "HAPI started on $($taskSettings.listenHost):$($taskSettings.port). Use -Action Info for the private token."
}elseif($Action -eq 'Stop'){
    & $taskHapi runner stop *> (Join-Path $taskData 'runner-stop.log')
    $taskListener=Get-NetTCPConnection -LocalPort $taskSettings.port -State Listen -ErrorAction SilentlyContinue
    foreach($taskProcessId in @($taskListener.OwningProcess | Select-Object -Unique)){
        $taskProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$taskProcessId"
        if($taskProcess.ExecutablePath -eq $taskHapi -and $taskProcess.CommandLine -match ' hub '){Stop-Process -Id $taskProcessId}
    }
    Write-Output 'Stopped this HAPI hub and runner.'
}elseif($Action -eq 'Info'){
    # Explicit user command only: never paste this output into logs, issues, or release assets.
    Write-Output "Port: $($taskSettings.port); bind: $($taskSettings.listenHost); workspaces: $((Workspace-Roots $taskSettings) -join ', ')"
    Write-Output ('Access token: '+(Get-Content -Raw -LiteralPath (Join-Path $taskData 'settings.json') | ConvertFrom-Json).cliApiToken)
}else{
    $taskListener=Get-NetTCPConnection -LocalPort $taskSettings.port -State Listen -ErrorAction SilentlyContinue
    [pscustomobject]@{running=[bool]$taskListener;port=$taskSettings.port;listenHost=$taskSettings.listenHost;workspaces=@(Workspace-Roots $taskSettings);allDrives=[bool]$taskSettings.allDrives;hapi='0.30.7';codex='0.164.0'} | ConvertTo-Json
}
