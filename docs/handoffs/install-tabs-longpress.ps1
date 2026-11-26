& {
    # Templar TV tabs + long-press fix (2026-11-26). Windows PowerShell 5.1. Builds on the PC (JDK 17), installs ONLY on the Streamer.
    # Output is copied to the clipboard when the script ends.
    $adb  = 'C:\Android\Sdk\platform-tools\adb.exe'
    $dev  = if ($env:TEMPLAR_STREAMER) { $env:TEMPLAR_STREAMER } else { Read-Host 'Streamer adb address (ip:5555)' }
    $pkg  = 'com.firestarterpro.templartv'
    $tree = 'C:\Templar Archives\Robotics Facility\Templar-TV-combine'
    $br   = 'claude/project-thread-jv7f8v'
    $jdk  = 'C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'
    $before = Join-Path $tree 'rollback\templar-before-combined.apk'
    $out = & {
        "== Templar TV tabs + long-press install  $(Get-Date -Format 's')"
        $env:JAVA_HOME = $jdk
        Set-Location -LiteralPath $tree
        git fetch origin $br 2>&1
        git checkout -B $br "origin/$br" 2>&1
        "HEAD: $(git rev-parse --short HEAD)"
        & .\gradlew.bat :app:assembleRelease --console=plain 2>&1 | Select-Object -Last 15
        $apk = Join-Path $tree 'app\build\outputs\apk\release\app-release.apk'
        if (-not (Test-Path -LiteralPath $apk)) { "ABORT: build produced no APK. Nothing installed."; return }
        "SHA-256: $((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash)"
        & $adb connect $dev
        $state = (& $adb -s $dev get-state) 2>&1
        if ("$state".Trim() -ne 'device') { "ABORT: $dev not connected. Nothing installed."; return }
        & $adb -s $dev install -r $apk
        & $adb -s $dev shell "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime'"
        "ROLLBACK (previous build): & '$adb' -s $dev install -r '$before'"
        "ROLLBACK (remove Templar):  & '$adb' -s $dev uninstall $pkg"
        "HOME back to Google: Settings > Apps > Default apps > Home app > Google TV"
    } 2>&1 | ForEach-Object { $l = "$_"; Write-Host $l; $l }
    ($out -join "`r`n") | Set-Clipboard
    Write-Host '(Output copied to the clipboard.)'
}
