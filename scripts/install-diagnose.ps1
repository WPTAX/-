param([Parameter(Mandatory=$true)][string]$Apk)
$ErrorActionPreference = 'Stop'
$resolvedApk = (Resolve-Path -LiteralPath $Apk).Path
Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256
adb devices
if ($LASTEXITCODE -ne 0) { throw 'adb is required from Android SDK platform-tools.' }
Write-Host 'Phone model / Android / API:'
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
Write-Host 'Install/update without deleting app data:'
adb install -r $resolvedApk
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Keep the INSTALL_FAILED_* error above. No uninstall was attempted.'
    exit 1
}
adb shell am start -W -n com.ironproject.woonghee.v42/com.ironproject.woonghee.MainActivity
