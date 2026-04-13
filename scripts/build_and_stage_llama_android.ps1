param(
    [string]$LlamaCppPath,
    [string]$AndroidNdkPath = $env:ANDROID_NDK_HOME,
    [string]$AdbPath = "adb"
)

if ([string]::IsNullOrWhiteSpace($LlamaCppPath) -or -not (Test-Path -LiteralPath $LlamaCppPath)) {
    Write-Error "Provide -LlamaCppPath pointing to a local ggml-org/llama.cpp checkout."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($AndroidNdkPath) -or -not (Test-Path -LiteralPath $AndroidNdkPath)) {
    Write-Error "ANDROID_NDK_HOME or -AndroidNdkPath must point to a valid Android NDK."
    exit 1
}

$buildDir = Join-Path $LlamaCppPath "build-android"
$installDir = Join-Path $LlamaCppPath "install-android"
$toolchain = Join-Path $AndroidNdkPath "build\cmake\android.toolchain.cmake"
$cmakeBin = Join-Path $env:ANDROID_SDK_ROOT "cmake\3.22.1\bin\cmake.exe"
$ninjaBin = Join-Path $env:ANDROID_SDK_ROOT "cmake\3.22.1\bin\ninja.exe"

if (-not (Test-Path -LiteralPath $toolchain)) {
    Write-Error "Android toolchain file not found at $toolchain"
    exit 1
}

if (-not (Test-Path -LiteralPath $cmakeBin)) {
    Write-Error "CMake not found at $cmakeBin. Install Android SDK CMake 3.22.1 or update this script."
    exit 1
}

if (-not (Test-Path -LiteralPath $ninjaBin)) {
    Write-Error "Ninja not found at $ninjaBin. Install Android SDK CMake 3.22.1 or update this script."
    exit 1
}

& $cmakeBin `
  -G "Ninja" `
  -S $LlamaCppPath `
  -B $buildDir `
  "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
  "-DCMAKE_MAKE_PROGRAM=$ninjaBin" `
  -DANDROID_ABI=arm64-v8a `
  -DANDROID_PLATFORM=android-28 `
  -DGGML_OPENMP=OFF `
  -DGGML_LLAMAFILE=OFF

if ($LASTEXITCODE -ne 0) {
    Write-Error "CMake configure failed."
    exit 1
}

& $cmakeBin --build $buildDir --config Release -j 8
if ($LASTEXITCODE -ne 0) {
    Write-Error "Android llama.cpp build failed."
    exit 1
}

& $cmakeBin --install $buildDir --prefix $installDir --config Release
if ($LASTEXITCODE -ne 0) {
    Write-Error "Android llama.cpp install failed."
    exit 1
}

& $AdbPath shell "mkdir -p /data/local/tmp/llama.cpp"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create /data/local/tmp/llama.cpp on device."
    exit 1
}

& $AdbPath push $installDir /data/local/tmp/llama.cpp/
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to push llama.cpp install directory to device."
    exit 1
}

& $AdbPath shell "chmod 755 /data/local/tmp/llama.cpp/bin/llama-cli"
Write-Host "llama.cpp Android CLI staged to /data/local/tmp/llama.cpp"
