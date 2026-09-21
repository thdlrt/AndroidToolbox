$ErrorActionPreference='Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskSource = "$taskRoot/.build/native-source/hev-socks5-tunnel-2.17.1"
& "$taskRoot/.build/android-ndk-r27c/ndk-build.cmd" -j8 "NDK_PROJECT_PATH=$taskSource" "APP_BUILD_SCRIPT=$taskSource/Android.mk" "NDK_APPLICATION_MK=$taskSource/Application.mk" "NDK_OUT=$taskRoot/.build/native-obj" "NDK_LIBS_OUT=$taskRoot/app/src/main/jniLibs" 'APP_MODULES=hev-socks5-tunnel'
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
