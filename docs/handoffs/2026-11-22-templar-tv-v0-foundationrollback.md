# Templar TV Rollback Handoff

## Objective
Document rollback procedures for all Templar TV operations involving system configuration changes. Intended for safe reversal of HOME role, permissions, and configuration changes.

## User Intent
Templar TV becomes the normal HOME interface, but Google's stock launcher must remain available and HOME can be reassigned back to Google at any time. All operations must be reversible.

## Rollback Procedures

### HOME Role Reassignment
- **Current HOME**: `com.firestarterpro.templartv`
- **Restore Google Home**: `adb shell pm set-home com.google.android.tvlauncher`
- **Verify**: `adb shell dumpsys package com.google.android.tvlauncher | grep -E "home|default"`

### ADB-Granted Permissions
| Permission | Grant Command | Revoke Command |
|------------|--------------|----------------|
| QUERY_ALL_PACKAGES | `adb shell pm grant com.firestarterpro.templartv android.permission.QUERY_ALL_PACKAGES` | `adb shell pm revoke com.firestarterpro.templartv android.permission.QUERY_ALL_PACKAGES` |
| READ_TV_LISTINGS | `adb shell pm grant com.firestarterpro.templartv android.permission.READ_TV_LISTINGS` | `adb shell pm revoke com.firestarterpro.templartv android.permission.READ_TV_LISTINGS` |
| PACKAGE_USAGE_STATS | `adb shell pm grant com.firestarterpro.templartv android.permission.PACKAGE_USAGE_STATS` | `adb shell pm revoke com.firestarterpro.templartv android.permission.PACKAGE_USAGE_STATS` |
| WRITE_SECURE_SETTINGS | `adb shell pm grant com.firestarterpro.templartv android.permission.WRITE_SECURE_SETTINGS` | `adb shell pm revoke com.firestarterpro.templartv android.permission.WRITE_SECURE_SETTINGS` |
| SYSTEM_ALERT_WINDOW | `adb shell pm grant com.firestarterpro.templartv android.permission.SYSTEM_ALERT_WINDOW` | `adb shell pm revoke com.firestarterpro.templartv android.permission.SYSTEM_ALERT_WINDOW` |

### Notification-Listener Access
- **Grant**: `adb shell settings put secure enabled_notification_listeners com.firestarterpro.templartv/.service.VistaNotificationListenerService`
- **Revoke**: 
  1. `adb shell settings put secure enabled_notification_listeners ""`
  2. App settings → Notifications → Vista → Turn off

### Overlay Permission
- **Grant**: `adb shell appops set com.firestarterpro.templartv SYSTEM_ALERT_WINDOW allow`
- **Revoke**: `adb shell appops set com.firestarterpro.templartv SYSTEM_ALERT_WINDOW deny`

### Provider-Specific Configuration
- Remove any provider-specific ADB grants or configuration
- Reset provider cache: `adb shell content delete --uri android.content.Settings::secure --where key="templar_tv_provider_config"`

### Experimental Observers
- Disable observation services via Settings → Apps → Vista → Permissions
- Or revoke BIND_ACCESSIBILITY_SERVICE and BIND_NOTIFICATION_LISTENER_SERVICE permissions

### Database Migrations
- Watch history database is local-only by default
- To migrate or reset: Delete `%LOCAL_APPDATA%/com.firestarterpro.templartv/databases/watch_history.db`
- Or use the in-app "Clear watch history" option in Settings → Diagnostics

### Jellyfin Configuration
- Remove saved server connection details from Templar TV settings
- Reset server address, username, token, and library names
- Jellyfin configuration is stored locally and can be cleared via Settings → Jellyfin → Reset

## Restoration Steps (Google HOME)
1. Uninstall Templar TV APK (optional - Google Home remains installed)
2. Or reassign HOME role: `adb shell pm set-home com.google.android.tvlauncher`
3. Re-enable Google Now on Tap/Assistant if disabled
4. Verify HOME: Long-press home button should show Google launcher

## Risks
- If HOME role is set to an unavailable package, Android may reset to default
- Revoking SYSTEM_ALERT_WINDOW may cause quick-settings overlay to disappear
- Revoking PACKAGE_USAGE_STATS may affect "Jump back in" functionality
- Factory reset will restore original HOME but also delete local watch history

## Known Limitations
- Physical device verification required for complete rollback validation
- Emulator behavior may differ from physical TV behavior
- Some system permissions cannot be programmatically revoked without user interaction

## Next Steps
- Physical-device validation of rollback procedures
- Add rollback instructions to main PR documentation
- Test all permission revocation paths on physical Google TV Streamer