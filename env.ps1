$taskRoot = $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:/Programe/Android Studio/jbr' }
$env:ANDROID_HOME = Join-Path $taskRoot '.build/sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $taskRoot '.build/android-home'
$env:ANDROID_AVD_HOME = Join-Path $taskRoot '.build/avd'
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.build/gradle-home'
$env:TEMP = Join-Path $taskRoot '.build/tmp'
$env:TMP = $env:TEMP
foreach ($taskPath in @($env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME,$env:GRADLE_USER_HOME,$env:TEMP)) { New-Item -ItemType Directory -Force -Path $taskPath | Out-Null }
$env:PATH = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:PATH"
