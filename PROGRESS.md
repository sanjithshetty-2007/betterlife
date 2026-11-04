# PROGRESS.md: live status (update at the end of EVERY prompt)

## Project facts
- App name: BetterLife
- Package id: com.sanjith.betterlife (debug: com.sanjith.betterlife.debug, label "BetterLife Dev")
- Mascot name: Blip (default)
- Supabase project ref: (fill in after the owner creates the Mumbai project)
- AI tools used so far: Claude Code (desktop app)

## Build environment (verified 2026-11-04)
- JDK: Android Studio's bundled JBR 25 (C:\Program Files\Android\Android Studio\jbr). JAVA_HOME is not set system-wide; set it per terminal (see README.md).
- Gradle 9.6.0 wrapper, AGP 9.4.1 (built-in Kotlin, so no kotlin-android plugin), Kotlin compose plugin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5 (Room Gradle plugin exports schemas to app/schemas).
- kotlinx-coroutines 1.11.0 and kotlinx-serialization-json 1.12.0 are declared in the app on purpose: device tests fail if the app ships older copies.
- `android.injected.androidTest.leaveApksInstalledAfterRun=true` keeps BetterLife Dev installed after device tests (otherwise its data and permissions are wiped).
- compileSdk 37, targetSdk 36, minSdk 34.
- Phone: Vivo V50e, adb model V2428, Android 16, connected over USB.

## Current position
- Last completed prompt: 6
- Open gates: Prompt 3 owner rows (3, 5, 7). Prompt 4 gate passed 2026-11-04 (row D run after Prompt 6).
- Phase: 1 (Reminder core)

## Done
- Prompt 1, Project scaffold and environment, 2026-11-04. VERIFIED: assembleDebug and testDebugUnitTest succeed; installDebug installed on the V50e; the app cold-starts in about 1.6 s with the themed screen; the merged manifest has no INTERNET and allowBackup=false.
- Prompt 2, Permissions and the Setup Checklist screen, 2026-11-04. VERIFIED: status mapping tested over all 32 combinations; on the phone the checklist shows 5 detectable + 4 Vivo rows and each Open settings button opens the right page with Back returning to the checklist. OWNER MUST VERIFY: rows turn green after granting, ticks survive a restart (docs/TESTING.md Prompt 2 table).
- Prompt 3, Overlay spike (GATE), 2026-11-04. VERIFIED: Path A overlay over Chrome and over the app; Back and Done remove it with no window left; Path B wakes the screen and shows the pop-up over the lock screen. OWNER MUST VERIFY: rows 3 (video app), 5 (rotation), 7 (tap within 0.5 s) and the back swipe gesture.
- Prompt 4, Alarm spike (GATE PASSED 2026-11-04 with setAlarmClock). VERIFIED: setAlarmClock rows A, B, C (AI over adb), D (owner, 15 min locked, fired 12:19:04.515 for 12:19:04) and E (owner rebooted); setExactAndAllowWhileIdle rows B and C. All fired within 1 s, including after a recents swipe killed the process and after a reboot. Still open (not needed for the gate): rows F to H for both types, A and D to E for setExactAndAllowWhileIdle, and the owner's choice of default alarm type.
- Prompt 5, Database foundation, 2026-11-04. VERIFIED: 53 unit tests (PC) and 16 device tests (V50e) pass: soft delete hides rows, restore brings back only the reminders deleted with the item, a failed reminder insert rolls back the whole create, unknown or deleted parent/project references are rejected, writes mark rows DIRTY, the version 1 schema accepts rows and opens through ALL_MIGRATIONS, settings round-trip and fall back to defaults on bad values. On the phone the seed button wrote 1,000 tasks + 200 events + 746 reminders in 213 ms with 1 janky frame of 104 and no ANR; Wipe removed exactly those 1,946 rows. Schema 1.json exported; no destructive migration anywhere.

- Prompt 6, Core domain logic, 2026-11-04. VERIFIED: 132 unit tests (PC) and 16 device tests (V50e) pass; release build compiles; no Android imports in domain/ and no direct system-time calls outside core/Clocks.kt. Covered by tests: every quick-add example from the prompt (fixed clock Thu 2026-10-08 18:00), the AM/PM answer path, emoji / Devanagari / Chinese / Arabic / Kannada / Tamil / empty / punctuation-only / 500-character input; monthly-on-31st through a full year and without drift; leap-day birthday 2026 to 2029; weekly with interval; weekdays; from-due-date vs from-completion; 30-day roll-forward of one overdue instance; rule text round trip and broken text; early reminders (past dropped, exact-now kept, midnight and month ends, all presets + custom, DST wall time, zone change); rollover at 02:59/03:00, month/year ends, leap day. On the phone the Time Machine jumped to Thu 5 Nov 01:00 and showed habit day 4 Nov, then reset. Fixed during testing: tags/projects in Indic scripts were not recognised (vowel signs are combining marks).

## Half-done work (exact files and what is missing)
(none)

## Next step
1. Owner tries 3 of their own sentences against the parser (docs/PARSER.md) and reports anything odd.
2. Prompt 7 (Delivery pipeline and scheduler), using the alarm-type decision above.

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
- 2026-11-04: Default alarm type: FULL_SCREEN and ALARM reminders use setAlarmClock (passed every Prompt 4 gate row; shows a status-bar alarm icon while pending). NORMAL reminders use setExactAndAllowWhileIdle. Prompt 7 implements this.
- 2026-11-04: Owner started Prompt 5 before Prompt 4 row D (screen off 15+ min) was run. Row D must still pass before Prompt 7 builds the real scheduler on top.

## Known issues
- Tooling: the Git Bash shell used by the AI collapses double backslashes inside heredocs. Write files that contain backslashes (local.properties, regexes) with the file editor, not shell heredocs. Low.
- Launcher icon is a placeholder (yellow antenna bulb) until the mascot is drawn (Prompt 16). Low.
- Setup checklist: the OriginOS 6 locations of "background pop-ups" and "show on lock screen" were NOT found. Row text says "not confirmed". The lock-screen pop-up and swipe-away/reboot alarms all worked with these rows unticked, so they may not be needed on this phone. Medium.
- Setup checklist: the recents-lock instruction is not confirmed on OriginOS 6. Low.
- Setup checklist: Vivo's notification settings page takes about 2 seconds to close after Back. Vivo behaviour. Low.
- After a force stop, the app receives BOOT_COMPLETED on its next start (seen on OriginOS 6). Not relied on; may be useful in Prompt 7. Info.
- OriginOS drops Log.d from logcat; use Log.i for anything that must be readable over adb. Low.
- Quick-add reads `sun`, `sat` and `wed` as weekdays ("buy sun cream" gets a Sunday date); the preview shows it. Revisit if it annoys in real use. Low.
- Repositories expose Room entities directly to callers for now; map to domain models when screens arrive (Prompt 9) if it keeps the UI cleaner. Low.

## Verified on the real phone
- 2026-11-04 (AI over adb): canScheduleExactAlarms() is true with USE_EXACT_ALARM alone; canUseFullScreenIntent() is true on a fresh USB install; Autostart was already Allowed; battery switch is App info > Battery usage > Background power control > Allow background power usage.
- 2026-11-04: recents swipe kills the process but does not force-stop the app (stopped=false), so exact alarms survive it.
- 2026-11-04: an alarm-clock alarm armed before a reboot was re-armed at boot and fired on time (owner rebooted).

## Commands
- Build: gradlew.bat assembleDebug
- Install on phone: gradlew.bat installDebug
- Unit tests: gradlew.bat testDebugUnitTest
- Device tests (phone connected): gradlew.bat connectedDebugAndroidTest
- Scheduled alarms: adb shell dumpsys alarm | findstr betterlife
- Debug log: adb shell run-as com.sanjith.betterlife.debug tail -n 20 files/debug_log.txt

## Changelog
- 2026-11-04: Plan revised for Supabase sync, internet, Android 14+ (agent.md, context.md, docs/PLAN_CHANGES.md).
- 2026-11-04: Prompt 01: Gradle project (version catalog, wrapper), debug suffix .debug and "BetterLife Dev" label, manifest without INTERNET and allowBackup=false, edge-to-edge MainActivity, BetterLifeApp + empty AppContainer, package folders, dark AMOLED theme from the design tokens, placeholder home screen, TestSetupTest, README, .gitignore, docs/TESTING.md.
- 2026-11-04: Prompt 02: reminder permissions in the manifest; pure buildSetupStatus mapper with tests; DeviceCapabilityChecker, ManualCheckStore (DataStore), SetupRepository; Setup checklist screen with status chips, why lines, Vivo directions, Open settings (in-task, App info fallback) and manual ticks; Navigation. Libraries: DataStore Preferences 1.2.1, Navigation Compose 2.10.2, lifecycle compose 2.11.0.
- 2026-11-04: Prompt 03: pure decidePresentation and isTapAllowed (700 ms) with tests; shared ReminderPopup composable; OverlayController (own lifecycle owner, single window, predictive back); FullScreenReminderActivity; ReminderNotifier; notification channels; DebugLog; debug Pop-up test screen; BuildConfig enabled.
- 2026-11-04: Prompt 04: pure triggerAfterMinutes/planRearm with tests; exact alarm scheduler for both kinds; alarm receiver -> systemExempted ReminderPopupService; boot and app-update re-arm with overdue handling; pending alarm in DataStore; DebugLog saved to a file; debug Alarm test screen.
- 2026-11-04: Prompt 05: Room 2.8.5 + KSP with exported schema v1; entities Project, Task, Event, Reminder, ReminderLog, SyncCursor (synced tables carry syncState/serverUpdatedAt from v1); LocalDate/ZoneId converters; DAOs with soft delete/restore; Task/Event/Reminder/ReminderLog repositories with transactions, DIRTY marking and reference validation; typed AppSettings + SettingsRepository with context.md defaults; debug SeedDataGenerator and Database screen; home debug entries as one list; unit and device tests; docs/DB_MIGRATIONS.md. Fixed during testing: unknown project/parent ids were accepted (null?.deletedAt == null).
- 2026-11-04: Prompt 06: Clock interface + FixedClock (domain/time), SystemClock and debug-only TimeMachineClock (core); habitDay rollover; RecurrenceRule (daily, weekdays, weekly, monthly, yearly, all with interval) + RecurrenceEngine (next, between with a 1,000 cap, skip, nextDue by RepeatMode, 30-day overdue roll-forward) + RecurrenceRuleText for the recurrenceRule column; EarlyReminderCalculator with ReminderOffsets presets; QuickAddParser (QuickAddWords, QuickAddResult) with AM/PM question; docs/PARSER.md; debug Time Machine screen; AppContainer now injects the clock into repositories while the debug log and test alarms keep real time.
