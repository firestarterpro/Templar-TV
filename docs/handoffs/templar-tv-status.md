# Templar TV Foundation Build-Fix Status
**Date:** 2026-11-22
**Working Directory:** `C:\llm\jobs\templar-tv`
**Branch:** `templar-tv-v0-foundation`
**Target APK:** `app-release.apk`

## 🛠️ Build Environment
- **JDK:** `C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot` (Java 17)
- **Gradle:** 8.7
- **Kotlin:** 2.0.21
- **KSP:** 2.0.21-1.0.25
- **Room:** 2.6.1

## ✅ Completed Fixes
- [x] **Gradle DSL:** Removed invalid `tools { ... }` and `targetJavaVersion`.
- [x] **Room/KSP Setup:** Correctly configured `libs.versions.toml` and `app/build.gradle.kts`.
- [x] **JellyfinContract.kt:** Fixed syntax errors.
- [x] **WatchHistoryDao.kt:** Fixed `suspend` + `LiveData` conflict and parameter mismatch.
- [x] **WatchHistoryEntry.kt:** Manual `Parcelable` implementation for stability.
- [x] **WatchHistoryRepository.kt:** Implemented `CoroutineScope` for async DB writes.
- [x] **GoogleHomeHandoff.kt:** Fixed `Intent` imports and flags.

## 🚩 Current Blockers (Kotlin Compilation)

### 1. ObserverContract.kt
- **Error:** "Objects cannot have constructors" in `ObservationResult`.
- **Fix Required:** Convert `object Success` and `object UnknownSession` to `data class`.

### 2. UI Layer Reference Mismatches (The "Vista" vs "Templar" Gap)
A large volume of unresolved references exists in:
- `MainActivity.kt`
- `HomeScreen.kt`
- `QuickSettingsActivity.kt`
- `VistaNotificationListenerService.kt`
- `QuickSettingsPanel.kt`

**Missing Symbols:**
- `VistaSignals`
- `VistaTheme`
- `ThemeMode`
- `SettingsViewModel` / `HomeViewModel`
- `NotificationRepository` / `NotificationItem`

**Investigation Needed:** Check if these were renamed to `Templar...` or if they are missing from the current branch.

### 3. Resource References
- **Error:** `Unresolved reference 'R'` in `SearchScreen.kt`, `VistaTopBar.kt`.
- **Fix Required:** Ensure correct `com.firestarterpro.templartv.R` import.

## 👣 Breadcrumbs for Next Agent
1. **Fix `ObserverContract.kt` first** to clear structural errors.
2. **Search codebase for `Signals` and `Theme`** to see where the rebranding stopped. Use `grep` for "Signals" or "Theme".
3. **Update imports in `dev.drewett.vista` package** to match `com.firestarterpro.templartv` where applicable.
4. **Verify `R` imports** in UI files.
5. **Run build:** `& { $env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'; .\gradlew.bat :app:assembleRelease }`

## 📦 Success Criteria for Completion
- [ ] `assembleRelease` succeeds.
- [ ] APK App ID: `com.firestarterpro.templartv`
- [ ] APK Label: `Templar TV`
- [ ] SHA-256 and size recorded.
- [ ] Draft PR #2 updated.
