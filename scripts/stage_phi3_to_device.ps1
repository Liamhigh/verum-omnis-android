param(
    [string]$AdbPath = "adb",
    [string]$ModelPath = "C:\Users\Gary\Downloads\exeriment\Phi-3-mini-4k-instruct-q4.gguf",
    [string]$PackageName = "com.verum.omnis"
)

if (-not (Test-Path -LiteralPath $ModelPath)) {
    Write-Error "Phi-3 model not found at $ModelPath"
    exit 1
}

$devices = & $AdbPath devices
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb devices failed"
    exit 1
}

$deviceCount = ($devices | Select-String "`tdevice$").Count
if ($deviceCount -lt 1) {
    Write-Error "No connected Android device found."
    exit 1
}

$remoteDir = "/sdcard/Android/data/$PackageName/files/models"
$remoteFile = "$remoteDir/Phi-3-mini-4k-instruct-q4.gguf"
$runtimeModelDir = "/data/local/tmp/llama.cpp/models"
$runtimeModelFile = "$runtimeModelDir/Phi-3-mini-4k-instruct-q4.gguf"

& $AdbPath shell "mkdir -p $remoteDir"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create remote model directory."
    exit 1
}

& $AdbPath push $ModelPath $remoteFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to push Phi-3 model to device."
    exit 1
}

& $AdbPath shell "mkdir -p $runtimeModelDir"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create runtime model directory."
    exit 1
}

& $AdbPath push $ModelPath $runtimeModelFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to push Phi-3 runtime model mirror to device."
    exit 1
}

Write-Host "Phi-3 model staged on device:"
Write-Host $remoteFile
Write-Host $runtimeModelFile
