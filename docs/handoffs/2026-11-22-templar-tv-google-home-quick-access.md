# Templar TV Google Home quick access

Date: 2026-11-22

## Change
Quick settings now has an Open Google Home action, a visible guide for switching the HOME launcher back to Google TV Home, a graceful message when Google Home cannot be opened, and Templar TV labels for reload and version.

The Google Home action uses the installed app launch intent first and retains the existing paired-device action as a fallback. It does not change the HOME role, permissions, packages, or device settings. The rollback guide explains the manual settings path and leaves the stock launcher installed.

## Files
- app/src/main/java/dev/drewett/vista/ui/quicksettings/QuickSettingsPanel.kt
- app/src/main/java/com/firestarterpro/templartv/google/GoogleHomeHandoff.kt

## Rollback
Restore those two tracked source files with:
git -C "C:\Templar Archives\Robotics Facility\Templar TV" restore -- app/src/main/java/dev/drewett/vista/ui/quicksettings/QuickSettingsPanel.kt app/src/main/java/com/firestarterpro/templartv/google/GoogleHomeHandoff.kt

The handoff can be moved to the Recycle Bin if the change is reverted.

## Verification
No build or tests were run, as requested. Confirm the behavior on the TV before relying on the rollback path.