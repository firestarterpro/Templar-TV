& {
    # Templar TV combined build installer (2026-11-25). Windows PowerShell 5.1. Targets ONLY the Streamer.
    # Everything printed below is also copied to the clipboard when the script ends.
    $adb      = 'C:\Android\Sdk\platform-tools\adb.exe'
    $dev      = '192.168.0.166:5555'
    $pkg      = 'com.firestarterpro.templartv'
    $listener = 'com.firestarterpro.templartv/com.firestarterpro.templartv.service.VistaNotificationListenerService'
    $apk      = 'C:\Templar Archives\Robotics Facility\Templar-TV-combine\app\build\outputs\apk\release\app-release.apk'
    $expected = '94623C86DAEAE2E7C3160C1C8767D14C266225E66FD1C65D367E59C325E29326'
    $before   = 'C:\Templar Archives\Robotics Facility\Templar-TV-combine\rollback\templar-before-combined.apk'
    $out = & {
        "== Templar TV combined build install  $(Get-Date -Format 's')"
        "== adb connect $dev"
        & $adb connect $dev
        $state = (& $adb -s $dev get-state) 2>&1
        "device state: $state"
        if ("$state".Trim() -ne 'device') { "ABORT: $dev is not connected as a device. Nothing installed."; return }
        if (-not (Test-Path -LiteralPath $apk)) { "ABORT: APK not found at $apk. Nothing installed."; return }
        $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
        "APK:      $apk"
        "SHA-256:  $hash"
        "Expected: $expected"
        if ($hash -ne $expected) { "ABORT: SHA-256 mismatch. Nothing installed."; return }
        "SHA-256 OK."
        "== Installed before:"
        & $adb -s $dev shell "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime'"
        "== adb install -r"
        & $adb -s $dev install -r $apk
        "== Notification listener (MediaSession observer)"
        & $adb -s $dev shell cmd notification allow_listener $listener
        & $adb -s $dev shell "settings get secure enabled_notification_listeners"
        "== Installed now:"
        & $adb -s $dev shell "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime'"
        "== HOME role holder (unchanged by this script):"
        & $adb -s $dev shell cmd role get-role-holders android.app.role.HOME
        ""
        "SHA-256 of installed APK file: $hash"
        "ROLLBACK (pick one):"
        "  Previous Templar build:  & '$adb' -s $dev install -r '$before'"
        "  Remove Templar entirely: & '$adb' -s $dev uninstall $pkg"
        "  Listener off:            & '$adb' -s $dev shell cmd notification disallow_listener $listener"
        "  HOME back to Google:     Settings > Apps > Default apps > Home app > Google TV  (or: & '$adb' -s $dev shell cmd role remove-role-holder android.app.role.HOME $pkg)"
        "  Google's launcher (launcherx) is never disabled or changed by this script."
    } 2>&1 | ForEach-Object { $line = "$_"; Write-Host $line; $line }
    ($out -join "`r`n") | Set-Clipboard
    Write-Host '(Output copied to the clipboard.)'
}
