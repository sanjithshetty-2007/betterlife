# context.md: single source of truth for the app

If anything in a chat conflicts with this file, THIS FILE wins. Only the owner edits it. If you find a conflict or a gap, ask the owner.

## 1. What we are building
BetterLife: a private Android app for one person, a personal tracker and "second brain". Modules: reminders and full-screen alarms, calendar, tasks, habit grids, body tracking (weight, calories, progress photos), notes and capture, home screen widgets, and a mascot character with rewards and honest, positive feedback. The signature feature is an animated full-screen reminder that appears OVER any other app. Tasks, events and habits sync to the owner's Supabase project. Look and feel: modern, Gen Z, fun, animated, motivating. No AI features.

## 2. Owner, device, distribution
- Owner: complete beginner at Android development. Windows PC with Android Studio. Builds with Claude Code, phone connected by USB cable (adb).
- Main device: Vivo V50e running OriginOS 6 (Android 16 based). OriginOS aggressively restricts background apps.
- Supported Android versions: 14, 15, 16 and later (minSdk 34). targetSdk 36. Must run correctly on any phone brand with Android 14+, but only the Vivo is tested and only Vivo gets brand-specific setup instructions; other brands get the standard Android checks.
- Android only. Personal use only, one user. NEVER published on the Play Store, so restricted permissions (draw over apps, exact alarms, full-screen intents) may be used freely.
- Delivered as an APK installed over USB. Updates must install over the old version without losing data (same signing key and package id).
- App name: BetterLife. Package id: com.sanjith.betterlife (debug build: com.sanjith.betterlife.debug, label "BetterLife Dev").
- Vivo Dynamic Island / Origin Island integration: dropped. Do not build it.
- Mascot name: undecided (default "Blip", editable in Settings).

## 3. Hard constraints
- Offline-first. Every feature works without internet and without signing in. The network is used ONLY for: Supabase auth, Supabase sync of the tables in section 11a, and link previews for shared links. No analytics, ads, crash reporting or other network calls.
- Reminders never depend on the network.
- No AI or machine learning. Text parsing and feedback are plain rules.
- Never lose data: no destructive migrations (local or Supabase), no silent deletes (soft delete with 30-day Trash).
- Photos and voice notes live in private app storage (never in the phone gallery) and are never uploaded.
- Reliability of reminders comes before every other feature.

## 4. Tech stack and approved dependencies
- Kotlin, Jetpack Compose, Material 3, single Activity, MVVM, repositories, StateFlow.
- minSdk 34, targetSdk 36, compileSdk = latest stable.
- Approved libraries ONLY: AndroidX core/lifecycle/activity-compose/navigation-compose, Room (+KSP), Preferences DataStore, WorkManager, Glance (widgets), androidx.biometric, kotlinx-coroutines, kotlinx-serialization-json, Coil (image loading), supabase-kt (BOM, auth-kt, postgrest-kt only), Ktor client with the OkHttp engine (required by supabase-kt; also used for link previews). Testing: JUnit4, kotlinx-coroutines-test, Room testing, AndroidX test (runner, ext), Compose UI test, Ktor MockEngine.
- Manual dependency injection (AppContainer). No Hilt/Koin. No chart library: charts are drawn with Compose Canvas. No Lottie: the mascot is drawn with Compose. No HTML parsing library: link previews read og:title / og:image / <title> with a small tested parser.

## 5. Features by module
### 5.1 Reminders and the full-screen alarm (core)
- Importance levels: NORMAL (notification), FULL_SCREEN (animated pop-up over apps), ALARM (full-screen, never held by a pause unless chosen, exempt from fatigue). FULL_SCREEN and ALARM both ring (below).
- Pop-up: fades/scales in, shows the mascot, title, time, and buttons Done, Snooze, Reschedule. While unanswered it rings the owner's alarm sound in a loop on the alarm stream at full volume (so also on silent; a setting lets FULL_SCREEN follow the ringer), with strong vibration, the screen woken and held at full brightness. After the ring time (default 5 min) it snoozes itself (default 10 min) and rings again, repeating until answered; an event never comes back past its end (that becomes a MISSED entry plus one normal notification). A phone call delays it the same way. Repeats are never downgraded by fatigue and do not count as ignored or as new interruptions. Buttons ignore taps for 700 ms after appearing. An info button "Why am I seeing this?" shows the item, due time, rule that fired (for example "1 hour before"), importance level, and a link to edit.
- Snooze options: 10 min, 30 min, 1 hour, Tonight (default 20:00), Tomorrow (default 09:00), Pick time. After 3 snoozes of one item, offer "Move to tomorrow instead?". Events cannot be snoozed past their end time.
- Early reminders: any event, deadline task, birthday or standalone reminder can have several offsets: at time, 5 min, 10 min, 30 min, 1 hour, 3 hours, 1 day, 2 days, 1 week, custom. Defaults (editable): birthdays and deadlines = 1 day before + on the day; normal events = at time. Each offset has its own importance level.
- Showing over apps: a full-screen-intent notification only launches full screen when the phone is LOCKED or the screen is OFF. When the owner is using an app, the app must show its OWN OVERLAY WINDOW (application-overlay window type, draw-over-apps permission). Both paths are required.
- Delivery pipeline (single, ordered, logged). Every firing runs: (1) item still valid? else drop (ITEM_COMPLETED / ITEM_DELETED); (2) capability check, degrade overlay -> notification if needed (PERMISSION_MISSING); (3) in a phone call? -> normal notification (IN_CALL); (4) Pause active? hold (PAUSED); (5) Quiet Hours active? hold (QUIET_HOURS), held items come as ONE digest afterwards; (6) fatigue rules; (7) deliver and log.
- Fatigue defaults: max 3 full-screen interruptions per hour (extras become normal notifications); reminders within 10 minutes merge into one grouped pop-up; ignored 3 times in a row -> temporary 24 hour downgrade (never silent: logged and the owner is told once; can be turned off). ALARM level is exempt unless the owner chooses otherwise.
- Pause ("I am busy"): 30 min, 1 hour, 3 hours, until tonight. Affects NORMAL and FULL_SCREEN reminders of tasks and habits. Calendar events, birthdays and ALARM level are exempt by default (toggle). Held reminders arrive as one digest. A scheduled alarm guarantees the pause ends.
- Quiet Hours: user-defined, may cross midnight (for example 22:00 to 07:00).
- Reminder Center: list of all reminders with status (Scheduled, Fired, Completed, Snoozed with count, Rescheduled, Dismissed, Missed, Held, Failed) and a REASON CODE: QUIET_HOURS, PAUSED, FATIGUE_GROUPED, FATIGUE_DOWNGRADED, IN_CALL, PERMISSION_MISSING, ITEM_COMPLETED, ITEM_DELETED, DEVICE_OFF, EXACT_ALARM_DENIED. Keep 60 days.
- Reminder Health: green/yellow/red for exact alarms, notifications, channels not downgraded, overlay, full-screen intent, battery, Vivo auto-start, Vivo background pop-ups, lock-screen display, recents lock (the four Vivo rows only shown on Vivo phones); a "Fix all" walkthrough; a Test Reminder in 10 seconds; a second test that fires after the app was swiped away. Re-checked on every launch with a banner if something turned red.
- Scheduling: exact alarms that work in Doze (setExactAndAllowWhileIdle); ALARM level uses setAlarmClock. Rolling 30-day window. After boot, missed reminders show as ONE summary and are listed in the Reminder Center; early reminders for still-future events arrive as normal notifications. Phone calls detected via AudioManager mode (no extra permission).

### 5.2 Calendar
Events, meetings, birthdays (yearly, all-day, fire at a set time default 09:00, year may be unknown, Feb 29 fires on Feb 28 in non-leap years). Views: day, week, month, agenda. Color categories. Repeat rules daily/weekly/monthly/yearly/custom. Tapping a day shows its events and tasks together. Two overlapping timed EVENTS show a small conflict badge (tasks never count as conflicts).

### 5.3 Tasks
Title, notes, due date/time, priority, project, subtasks, category, recurrence, reminders. Smart quick-add (rule-based parser) with an always-visible preview before saving. Views: Today, Upcoming, Overdue, Inbox. Morning check-in (pick top 3) and evening wrap-up. HABIT vs RECURRING TASK: a habit tracks consistency on a grid (creatine, meditation); a recurring task creates separate task instances with due dates ("Submit weekly report"). They never mix. Recurring tasks never pile up: a task ignored for 30 days keeps ONE overdue instance and rolls forward. Option per task: repeat from due date (default) or from completion date.

### 5.4 Habit grids
- Simple done / not-done per day. Starting habits: creatine, meditation, skincare routine, haircare routine. Add, rename, recolor, reorder, archive, delete (to Trash). Each habit: color, avatar/icon, category (Health, Self-care, Other), Essential flag, optional daily reminder time.
- Monthly grid: 31 boxes per habit, filled with the habit's color when done (also shows a check mark). Extra boxes in short months and boxes before the habit existed are greyed. Yearly heatmap per habit.
- Day rollover hour: default 03:00 (ticking at 01:00 counts for the previous day). Only today can be ticked or cleared; past days are a read-only record (owner, 2026-11-08), future days not editable.
- Habit log status per local date: DONE, KEPT (a Minimum Day), PAUSED.
- Streak = consecutive days ending today or yesterday (a streak stays alive while today is still pending). KEPT days count; PAUSED days neither break nor count. Best streak is kept. A broken streak shows "Best: 37. Restarting." never a bare 0.
- Consistency % = completed scheduled habit-days over the last 30 days, excluding paused days and days before the habit existed. Under 7 days of data shows "New". The dashboard leads with consistency and shows the streak beside it.
- Celebrations at 7, 10, 15 and 30 days: full-screen animation and confetti with the owner's custom messages. Fire ONCE per milestone per streak (a milestone ledger table). Past days cannot be ticked, so a backfill cannot create a milestone on the phone; a milestone from pulled logs shows a quiet banner only. Habit logs arriving through a sync pull never trigger a full-screen celebration (quiet banner at most).
- Pause / vacation / sick mode (date range): pauses habits without hurting consistency and quiets reminders. Default ON.
- Comeback mode: after 3 or more missed days on a habit, or 2 or more days without opening the app: gentle restart mission (2 habits + one important task), special mascot animation, bigger reward. Never list or shame missed days.
- Minimum Day: one tap picks 3 things by rule: most important task (Today ranking), one habit (Essential first, else the one with the longest streak at risk), one Self-care habit. Completing them is a KEPT day: does not break streaks and counts as success in consistency. Mascot says "That's enough for today."
- Evening nudge at a set time if any habit is not done yet.
- NOT included: number habits, "x times a week" habits, per-day notes, separate supplement tracker, countdown timers. Streak freeze and planned rest days: UNDECIDED, do not build.

### 5.5 Today screen (home)
Greeting with the name entered at onboarding (never hard-coded), mascot, summary of important tasks, habits remaining, next event, progress %, quick capture buttons (Task, Note, Voice), a small sync status indicator (synced / pending / offline / signed out; never blocks anything). Ranking rule: (1) ALARM or FULL_SCREEN reminders due now; (2) overdue high-priority tasks; (3) events starting within 2 hours; (4) today's tasks by priority then time; (5) remaining habits, time-of-day aware. Items at the same time collapse into one row ("2:00 PM, 2 items").

### 5.6 Body tracking
Daily weight (kg) with a trend line = 7-DAY moving average over entries within the last 7 DAYS (not the last 7 entries). Daily calories (multiple entries per day sum to the day) with daily graph, weekly average graph ("n of 7 days logged", unlogged days ignored, never zero-filled), optional goal line, this week vs last week. Validate absurd values with a confirm. Week start day is a setting (Monday default). Weekly progress photos: capture or import, private storage, downscaled and compressed, EXIF-corrected, thumbnails, side-by-side compare and swipe slider. SENSITIVE METRICS RULE: never negative feedback about weight or calories, never reward a number, reward only consistent logging. Body data is LOCAL ONLY (never synced).

### 5.7 Second brain
Notes with tags, pinned, photos, checklists. Voice notes (title + tags, no transcription). Quick capture from a widget and a persistent notification with buttons (task, note, voice). Share-to-app (high priority): sharing text, a link or images from any app saves to the Inbox without unlocking and without showing content; duplicate links detected; shared images copied immediately into private storage. Link previews: a shared link is saved instantly offline; when online, a background job fetches the page title and preview image (og:title / og:image / <title>, size and time limits, HTTPS only, image stored privately) and fills them in; failure leaves the plain link. Inbox collects all unprocessed items; each offers Keep as note, Turn into task, Archive, Delete. Links between items. Related items (up to 5, ranked explicit link, same project, shared tags; no text similarity). Journal (daily entry, optional prompt). Global search (SQLite full-text, Unicode-aware, vault excluded). Private vault behind a second lock. All second-brain data is LOCAL ONLY (never synced).

### 5.8 Widgets (Glance)
Habit widget (tick boxes), task widget (see, tick, delete; a + opens a small quick-add popup because widgets cannot show a keyboard), calendar widget (month grid with dots or agenda), quick-capture widget. The database is the single source of truth. Widgets refresh at the day rollover hour and after a sync pull changes their data. A setting hides details on widgets (counts only), default ON. Vault items never appear in widgets, search, notifications or share lists.

### 5.9 Rewards and feedback (no AI)
XP is an append-only ledger (reversible). XP is awarded once per item per day, undo removes it, a daily cap stops tick/untick farming, important tasks weigh more. Rewards for what matters: overdue tasks, high-priority tasks, comebacks (bigger than a streak), all habits done in a day, consistent weight/photo logging (never the number), weekly review. Changes arriving through a sync pull never award XP. Feedback from the owner's own data with plain rules: needs enough data (for example 4 weeks before "you miss Sundays"), at most one insight per day, rotated wording, skip paused days, kind and specific. Weekly report card: wins first, one honest weak spot, one small suggestion. Dashboard: daily score, consistency, best weekday, most skipped habit. Year in review.

## 6. Mascot
Round alien blob, maroon body, big glossy eyes, stubby arms, one antenna with a glowing yellow bulb (the mood light). Moods: cheering, proud, curious (idle), sleepy (late night), disappointed (missed habits: sits down, bulb dim; never guilt-trips; perks up instantly). Mood reflects the last 24 to 72 hours only. Five PERMANENT evolution stages from total consistent days (never goes down): 1 glowing egg/pod, 2 baby blob, 3 teen with bigger antenna and small legs, 4 adult with cape or glowing markings, 5 legend with full glow and aura. Drawn with Compose Canvas. Appears on Today, pop-ups, celebrations, report card and widgets (small).

## 7. Time and date rules
- Instants = UTC epoch millis + zone id. Calendar dates = ISO local date text with no zone.
- Day rollover hour 03:00 (setting) for habits, Today and daily score.
- Quick-add parser rules: always show a preview; a weekday equal to today means next week; a time already passed today with no date means tomorrow (shown clearly); ambiguous "at 5" asks AM or PM; day/month order is day first; support 12 and 24 hour input; never crash on odd text (emoji, non-Latin scripts).
- Recurrence: monthly on the 31st falls to the last day of shorter months; every weekday; skip this occurrence; edit this / this and following / all.

## 8. Data model (Room)
Separate tables: Task, Event (includes birthdays), Habit, Note, JournalEntry, plus Reminder (itemType, itemId, offset, importance, state), ReminderLog (append-only, with reason code), Attachment (itemType, itemId, file), Link (fromType, fromId, toType, toId), Tag + join table, HabitLog (habitId, localDate, status), MilestoneLedger, PauseRange, XpLedger (append-only), WeightEntry, CalorieEntry, ProgressPhoto, CelebrationMessage, LinkPreview. Every row: UUID id, createdAt, updatedAt, deletedAt. Synced tables (section 11a) also have syncState (CLEAN / DIRTY) and serverUpdatedAt. Settings in Preferences DataStore. Polymorphic references (itemType + itemId) are validated in the repository layer.

## 9. Design system
- Mood: modern Gen Z, fun, rounded, soft thin borders, layered cards, avatars, lots of motion. Dark/AMOLED default.
- Tokens: background 1A0A0D, surface 2A1116, cherry 7A1F2B, accent yellow E0A800, text F5E9E0 (hex). Extra category colors stay in a warm family. Large corner radii (16 to 28 dp), 1 dp soft borders.
- Motion: tick boxes pop and fill, cards bounce in, progress rings animate, confetti on celebrations. All motion has a reduced-motion version.
- Contrast: check yellow on maroon. Never rely on color alone.
- Edge-to-edge layouts with correct insets; predictive back supported.

## 10. Permissions
POST_NOTIFICATIONS, USE_EXACT_ALARM (verify canScheduleExactAlarms at runtime; fall back to SCHEDULE_EXACT_ALARM + settings deep link), SYSTEM_ALERT_WINDOW, USE_FULL_SCREEN_INTENT (verify canUseFullScreenIntent at runtime), RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE plus the matching FOREGROUND_SERVICE_<TYPE> permission for each service type used (for example FOREGROUND_SERVICE_MICROPHONE for voice notes), VIBRATE, RECORD_AUDIO, CAMERA only if the camera capture path needs it, USE_BIOMETRIC, INTERNET, ACCESS_NETWORK_STATE. Battery optimisation exemption request. Vivo manual settings: auto-start, background pop-ups, lock-screen display, lock in recents.

## 11. Backup, security and data safety
- Biometric lock (device-credential fallback) for the whole app and a second lock for the vault. Quick capture from widget, notification and share sheet is WRITE-ONLY (adds without unlocking, reads nothing).
- Local backup stays the real backup (Supabase only holds part of the data): auto-backup to a user-chosen folder (Storage Access Framework) on a schedule, plus manual export/import and CSV export. Backup manifest: format version, app version, schema version, creation time, item counts, size, checksums. Safe database copy (VACUUM INTO or checkpoint then copy). Never restore blindly: verify checksum, show a preview of counts, make a safety backup of current data first. Restore REPLACES data. Refuse a backup from a newer schema. After restore: reschedule all alarms and mark synced tables for a full re-sync. Show last successful and last failed backup. Never delete the last good backup. Keep last 7. Optional passphrase-encrypted backups (default off) with a clear no-recovery warning.
- Signing: always the same keystore. Back up the keystore and passwords in two places. Debug builds use the .debug id suffix so they never replace real data. Uninstalling the app deletes all local data; only synced tables survive in Supabase (say so in onboarding).

## 11a. Supabase sync
- Project: a NEW Supabase project in the Mumbai region (ap-south-1), created by the owner. Its project ref is recorded in PROGRESS.md. (The existing Tokyo project "sanjithshetty-2007's Project" is NOT used.)
- Auth: email + password (Supabase Auth). Sign-in is optional; the app works fully signed out. One account, the owner's.
- Synced tables ONLY: Task, Project, Event, Reminder (definitions: item, offset, importance; NOT fire state), Habit, HabitLog, PauseRange. Everything else is local only: ReminderLog, notes, journal, tags, links, attachments, voice notes, photos, weight, calories, XP, milestones, celebration messages, settings, vault.
- Security: every Supabase table has user_id (default auth.uid()) and row-level security enabled with policies allowing select/insert/update only where user_id = auth.uid(). No delete policy: deletes sync as soft-delete tombstones (deleted_at). The app ships only the publishable (anon) key. No end-to-end encryption (owner decision).
- Model: offline-first outbox. Local writes set syncState DIRTY in the same transaction. Push: upsert dirty rows, then mark CLEAN. Pull: fetch rows whose server updated_at is newer than the last pull cursor. Conflict rule: last write wins on the whole row by updatedAt. Tombstones are pushed before the 30-day Trash purge removes a row locally.
- Timing: automatic when online. Push shortly after changes (debounced), pull on app open, plus a periodic WorkManager job with a network constraint and retry with backoff. Never on the main thread, never inside a reminder receiver.
- After a pull: reschedule affected reminders, refresh widgets, never fire celebrations or XP for pulled changes.
- Free-plan note: Supabase pauses free projects after about a week with no activity; the periodic sync job keeps it active while the phone is used. If the project is paused, sync shows "Server paused" and the app keeps working offline.
- Settings: account (sign in / out), last sync time, pending changes count, "Sync now", and "Delete my cloud data" (asks twice, deletes only server rows).

## 12. Out of scope (do not build)
Workout logs, body measurements, water and sleep tracking, a separate supplement tracker, number habits, "x times per week" habits, per-day habit notes, countdown timers, streak freeze and rest days (undecided), focus timer, routines, goals (undecided), any AI, syncing data not listed in section 11a, Google sign-in, multi-user / sharing, a web dashboard, tablet layouts, brand-specific setup guides for non-Vivo phones, Play Store release, Vivo Dynamic Island.

## 13. Open decisions and defaults
Mascot name Blip. Rollover hour 03:00. Snooze Tonight 20:00 and Tomorrow 09:00. Fatigue 3 per hour and 10 minute grouping. Widget detail hiding ON. Pause/vacation mode ON. Encrypted backups OFF. Keep 7 backups. Sync automatic when online. Source code on the phone: view-only via a private repository (not part of the app).

## 14. Phase map
Phase 0 Foundation: Prompt 1. Phase 1 Reminder core: Prompts 2 to 13. Phase 1S Cloud sync: Prompts S1 and S2. Phase 2 Habits and Today: 14 to 17, then S3 (habit sync). Phase 3 Widgets and calendar: 18 to 20. Phase 4 Second brain: 21 to 23. Phase 5 Body tracking: 24 and 25. Phase 6 Polish and safety: 26 to 30. The prompts live in the owner's playbook; changes to them and the new S prompts are in docs/PLAN_CHANGES.md; PROGRESS.md records which one is current.
