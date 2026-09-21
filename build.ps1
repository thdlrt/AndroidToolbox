param([switch]$DebugOnly)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path '.build/gradle-9.5.0/bin/gradle.bat')) { throw '先运行 python scripts/bootstrap.py 准备 E 盘构建环境。' }
    if (-not (Test-Path 'app/src/main/jniLibs/arm64-v8a/libhev-socks5-tunnel.so')) { & ./scripts/build-native.ps1 }
    if ($DebugOnly) {
        & '.build/gradle-9.5.0/bin/gradle.bat' :app:assembleDebug :app:testDebugUnitTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed' }
        return
    }
    New-Item -ItemType Directory -Force '.signing','outputs' | Out-Null
    $taskPasswordFile = Join-Path $PSScriptRoot '.signing/password.dpapi'
    if (-not (Test-Path $taskPasswordFile)) {
        $taskBytes = New-Object byte[] 32
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($taskBytes)
        $taskPassword = [Convert]::ToBase64String($taskBytes)
        ConvertTo-SecureString $taskPassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $taskPasswordFile
    }
    $taskSecure = Get-Content -LiteralPath $taskPasswordFile | ConvertTo-SecureString
    $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
    try { $env:LANBRIDGE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
    if (-not (Test-Path '.signing/lanbridge.jks')) {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore '.signing/lanbridge.jks' -storepass:env LANBRIDGE_STORE_PASSWORD -keypass:env LANBRIDGE_STORE_PASSWORD -alias lanbridge -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LanBridge Android, O=Personal, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed' }
    }
    & '.build/gradle-9.5.0/bin/gradle.bat' :app:testDebugUnitTest :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed' }
    $taskVersion = (Select-String -Path 'app/build.gradle' -Pattern "versionName '([^']+)'" ).Matches.Groups[1].Value
    $taskApk = Join-Path $PSScriptRoot "outputs/AndroidToolbox-$taskVersion.apk"
    Copy-Item 'app/build/outputs/apk/release/app-release.apk' $taskApk -Force
    & '.build/sdk/build-tools/36.0.0/apksigner.bat' verify --verbose $taskApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    (Get-FileHash -Algorithm SHA256 -LiteralPath $taskApk).Hash.ToLowerInvariant() | Set-Content "$taskApk.sha256"
    Write-Output "APK: $taskApk"
} finally {
    Remove-Item Env:LANBRIDGE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
