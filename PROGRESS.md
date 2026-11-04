# PROGRESS.md: live status (update at the end of EVERY prompt)

## Project facts
- App name: BetterLife
- Package id: com.sanjith.betterlife (debug: com.sanjith.betterlife.debug, label "BetterLife Dev")
- Mascot name: Blip (default)
- Supabase project ref: (fill in after the owner creates the Mumbai project)
- AI tools used so far: Claude Code (desktop app)

## Build environment (verified 2026-11-04)
- JDK: Android Studio's bundled JBR 25 (C:\Program Files\Android\Android Studio\jbr). JAVA_HOME is not set system-wide; set it per terminal (see README.md).
- Gradle 9.6.0 wrapper, AGP 9.4.1 (built-in Kotlin, so no kotlin-android plugin), Kotlin compose plugin 2.4.21, Compose BOM 2026.09.00.
- compileSdk 37, targetSdk 36, minSdk 34.
- Phone: Vivo V50e, adb model V2428, Android 16, connected over USB.

## Current position
- Last completed prompt: 3 (gate rows 2 and 4 passed; owner still runs rows 3, 5, 7)
- Current prompt in progress: 4 (code done and committed; GATE waits for the owner's matrix rows, especially E reboot)
- Current prompt in progress: none
- Phase: 1 (Reminder core)

## Done
- Prompt 1, Project scaffold and environment, 2026-11-04. VERIFIED: assembleDebug and testDebugUnitTest succeed (1 test, 0 failures); installDebug installed on the V50e (Android 16); the app cold-starts in about 1.6 s and shows the themed screen; the snackbar appears; the merged manifest has no INTERNET and allowBackup=false.

- Prompt 2, Permissions and the Setup Checklist screen, 2026-11-04. VERIFIED: 9 unit tests pass (8 for the status mapping, covering all 32 combinations of detectable results); on the phone, the checklist shows 5 detectable + 4 Vivo rows; Notifications, Exact alarms, Display over other apps, Full-screen notifications and App info buttons open the right pages and Back returns to the checklist. OWNER MUST VERIFY: rows turn green after granting, ticks survive a restart (docs/TESTING.md Prompt 2 table).

## Half-done work (exact files and what is missing)
(none)

- Prompt 3, Overlay spike (GATE), 2026-11-04. VERIFIED: 26 unit tests pass (11 decision-function tests over all 32 input combinations, 6 tap-guard tests); on the V50e, Path A overlay shows over Chrome and over the app itself, Back and Done remove it with no window left; Path B wakes the screen and shows the pop-up over the lock screen, Done closes it and clears the notification. OWNER MUST VERIFY: matrix rows 3 (video app), 5 (rotation), 7 (tap within 0.5 s) and the back swipe gesture in docs/TESTING.md.

## Half-done: Prompt 4 gate
Code complete: TestAlarmScheduler (both alarm kinds), TestAlarmReceiver, RearmReceiver (boot + app update), PendingAlarmStore, TestAlarmController, file-backed DebugLog, ReminderPopupService (systemExempted), Debug: alarm test screen. VERIFIED by the AI over adb with setExactAndAllowWhileIdle: row B (Chrome) and row C (swiped away, process killed) both fire on time and show the overlay. VERIFIED 2026-11-04 (owner rebooted, AI read the log): row E with setAlarmClock, re-armed at boot and fired on time over the lock screen. Missing: rows A, D, F, G, H, plus E for setExactAndAllowWhileIdle and A to C for setAlarmClock, then the owner picks the default alarm type (docs/TESTING.md Prompt 4 table).

## Next step
Owner fills in the Prompt 4 table (rows A, C and E matter most). When A to E pass for at least one alarm type, Prompt 4 is done; then Prompt 5 (Database foundation).

## Decisions (made by the owner)
- 2026-11-04: Single user (owner only), sideloaded APK, never on the Play Store.
- 2026-11-04: Internet allowed. Supabase is used for backup and sync; the phone's Room database stays the source of truth and the app works offline.
- 2026-11-04: Only tasks, projects, events, reminder definitions, habits, habit logs and pause ranges sync. Notes, journal, body data, photos, voice notes, XP and vault stay local only.
- 2026-11-04: minSdk 34 (Android 14 and up). targetSdk 36. Vivo-specific setup guides only; other brands get the generic Android checks.
- 2026-11-04: Sync devices: the Vivo V50e only for now.
- 2026-11-04: Sign-in: email + password (Supabase Auth). Optional; the app works signed out.
- 2026-11-04: No end-to-end encryption; security comes from row-level security.
- 2026-11-04: Conflicts: last write wins on the whole row.
- 2026-11-04: Sync timing: automatic when online.
- 2026-11-04: Supabase: a NEW project in Mumbai (ap-south-1), created by the owner. The Tokyo project is not used.
- 2026-11-04: Link previews for shared links: yes.
- 2026-11-04: Build all 30 prompts in order, with sync prompts S1, S2 after Prompt 13 and S3 after Prompt 17.
- 2026-11-04: Development: Claude Code on the PC, phone connected by USB, adb installs and tests.

## Known issues
- Tooling: the Git Bash shell used by the AI collapses double backslashes inside heredocs. Write files that contain backslashes (local.properties, regexes) with the file editor, not shell heredocs. Low.
- Launcher icon is a placeholder (yellow antenna bulb) until the mascot is drawn (Prompt 16). Low.
- Setup checklist: the OriginOS 6 locations of "background pop-ups" and "show on lock screen" were NOT found (not in App info > Permissions). Row text points to Permission manager / Special app access > Turn screen on and says "not confirmed". Prompt 3's gate will show whether these settings matter. Medium.
- Setup checklist: the recents-lock instruction is not confirmed on OriginOS 6. Low.
- Setup checklist: Vivo's notification settings page takes about 2 seconds to close after Back. Vivo behaviour, not the app. Low.
- RESOLVED in Prompt 4: the pop-up service is now systemExempted (no time limit) instead of shortService.
- Prompt 4: after a force stop, the app receives BOOT_COMPLETED on its next start (seen on OriginOS 6). Not relied on; may be useful in Prompt 7. Info.
- OriginOS drops Log.d from logcat; use Log.i for anything that must be readable over adb. Low.
- Path B worked even though the Vivo "background pop-ups" / "show on lock screen" rows are still unticked, so those settings may not be needed for the pop-up on this phone. Recheck after Prompt 4's swipe-away and reboot tests. Info.

## Verified on the real phone
- 2026-11-04 (checked by the AI over adb): canScheduleExactAlarms() is true with USE_EXACT_ALARM alone; canUseFullScreenIntent() is true on a fresh USB install; Autostart was already Allowed; battery switch is App info > Battery usage > Background power control > Allow background power usage.

## Commands
- Build: gradlew.bat assembleDebug
- Install on phone: gradlew.bat installDebug
- Unit tests: gradlew.bat testDebugUnitTest
- Device tests (phone connected): gradlew.bat connectedDebugAndroidTest
- Scheduled alarms: adb shell dumpsys alarm | findstr betterlife

## Changelog
- 2026-11-04: Plan revised for Supabase sync, internet, Android 14+ (agent.md, context.md, docs/PLAN_CHANGES.md).
- 2026-11-04: Prompt 01: Gradle project (version catalog, wrapper), debug suffix .debug and "BetterLife Dev" label, manifest without INTERNET and allowBackup=false, edge-to-edge MainActivity, BetterLifeApp + empty AppContainer, package folders, dark AMOLED theme from the design tokens, placeholder home screen, TestSetupTest, README, .gitignore, docs/TESTING.md.
- 2026-11-04: Prompt 02: reminder permissions in the manifest; pure buildSetupStatus mapper (domain/setup) with tests; DeviceCapabilityChecker, ManualCheckStore (DataStore), SetupRepository in AppContainer; Setup checklist screen with status chips, why lines, Vivo directions, Open settings (in-task, App info fallback) and manual ticks; re-check on resume; Navigation (home, setup). Libraries added: DataStore Preferences 1.2.1, Navigation Compose 2.10.2, lifecycle viewmodel-compose and runtime-compose 2.11.0. README: real Developer options path.
- 2026-11-04: Prompt 03: pure decidePresentation (domain/delivery) and isTapAllowed (700 ms) with tests; shared ReminderPopup composable (fade + scale, reduced-motion variant); OverlayController with its own lifecycle/saved-state owner, single window, Back via OnBackInvokedCallback; FullScreenReminderActivity (showWhenLocked, turnScreenOn); ReminderNotifier; notification channels (full-screen, normal, background work); DebugLog; debug-only Pop-up test screen and PopupSpikeService (shortService); BuildConfig enabled to hide debug entries in release.
- 2026-11-04: Prompt 04 (gate pending owner rows): pure triggerAfterMinutes/planRearm with tests; exact alarm scheduler for setExactAndAllowWhileIdle and setAlarmClock; alarm receiver -> systemExempted ReminderPopupService (moved from the debug package, now also used by the Prompt 3 test with a 10 s delay); boot and app-update re-arm with overdue handling; pending alarm in DataStore; DebugLog saved to a file; Debug: alarm test screen; FOREGROUND_SERVICE_SYSTEM_EXEMPTED permission.
