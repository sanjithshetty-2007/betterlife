# PLAN_CHANGES.md: how the original Build Playbook was changed

The original playbook (Build_Playbook_agent_context_prompts.docx) still provides the 30 prompts. agent.md and context.md in this folder REPLACE the versions in the playbook. When you paste a playbook prompt, apply the edits for that prompt from section 3 below. New prompts S1, S2 and S3 are in section 4.

## 1. What changed and why

| Area | Original plan | Revised plan | Reason |
|---|---|---|---|
| Internet | Banned everywhere, audited in Prompt 30 | Allowed for Supabase auth, sync and link previews only | Owner decision |
| Backend | None, 100% local | Supabase (Mumbai project), offline-first sync | Owner decision |
| What syncs | Nothing | Tasks, projects, events, reminder definitions, habits, habit logs, pause ranges | Owner chose the low-sensitivity data only |
| Android versions | minSdk 31, targetSdk "latest" | minSdk 34, targetSdk 36 | Owner chose Android 14+. Pinning targetSdk to the phone's version (16) means no untested behaviour changes |
| Other phones | Vivo only | Runs on any Android 14+ phone; Vivo-only setup guides | Owner decision |
| Dev workflow | Copy-paste between chat AIs | Claude Code reads/writes files and drives the phone by USB | Claude Code can build, install and run tests itself |
| Backups | Local folder only | Local folder stays the real backup | Supabase only holds part of the data |

## 2. Detailed analysis

### 2.1 Things the original plan got right (keep them)
- Spike the risky parts first (Prompts 3 and 4). On Vivo, the overlay and alarm-after-swipe-away are what decide whether the app works. Sync changes nothing here.
- Room as source of truth, soft deletes, UUIDs, updatedAt on every row. These already make sync much easier: UUIDs are created on the phone and never clash, soft deletes become sync tombstones, and updatedAt gives last-write-wins.
- Pure domain logic with an injected Clock.

### 2.2 Risks introduced by Supabase, and how the plan handles them
1. **Reminders must never wait for the network.** Sync only writes Room; the scheduler reads Room. After a pull, changed reminders are rescheduled. Receivers never call Supabase.
2. **Sync loops and duplicate alarms.** A pulled row is written CLEAN (not DIRTY), so it is not pushed back. Alarm ids stay deterministic, so rescheduling after a pull replaces rather than duplicates.
3. **Celebrations or XP from pulled data.** Explicitly forbidden (context.md 5.4 and 5.9).
4. **Trash purge vs tombstones.** A row may only be hard-deleted locally after its tombstone was pushed. Otherwise the server copy would come back on the next pull.
5. **Secrets.** Only the publishable/anon key ships in the APK; it is safe because row-level security blocks every other user. The service_role key never leaves the Supabase dashboard.
6. **Free plan pausing.** Free projects pause after about a week with no activity. The periodic sync job keeps it awake while you use the phone. If you stop for a while, unpause it in the dashboard; the app keeps working offline meanwhile.
7. **Clock skew.** With one device and last-write-wins on phone-generated updatedAt, skew does not matter. If a second device is ever added, revisit this (use server time).
8. **Reminder fire state.** Only reminder definitions sync, not "fired/snoozed" state, so a restore on a new phone does not replay old alarms or skip future ones.
9. **Habit log identity.** One habit has at most one log per local date. On Supabase this is a unique (user_id, habit_id, local_date) constraint, so the same day ticked twice merges instead of duplicating.

### 2.3 Android 14 to 17 notes the original prompts did not mention
- **Android 14**: foreground services must declare a type plus a matching FOREGROUND_SERVICE_<TYPE> permission. USE_FULL_SCREEN_INTENT must be checked with canUseFullScreenIntent(). Context-registered receivers need an explicit exported flag. Exact-alarm permission SCHEDULE_EXACT_ALARM is denied by default for new installs (USE_EXACT_ALARM is the right choice here).
- **Android 15**: edge-to-edge is forced for targetSdk 35+, so every screen (including the pop-up and the quick-add dialog) must handle insets. BOOT_COMPLETED receivers can no longer start some foreground service types (dataSync, microphone, camera, mediaPlayback, ...). Apps holding SYSTEM_ALERT_WINDOW may only start a foreground service from the background while showing a visible overlay; an exact alarm still grants a short exemption, which Prompt 4 must confirm on the phone.
- **Android 16** (your phone): the edge-to-edge opt-out is gone and predictive back is on by default for targetSdk 36, so Back on the overlay must use the new back handling. Background job quotas are tighter, so sync must tolerate being delayed (it already retries).
- **Android 17**: when it ships, raise targetSdk in its own prompt after reading its behaviour changes. Do not let "latest stable" silently change it.

### 2.4 Things to keep an eye on
- The 30-prompt plan plus 3 sync prompts is months of work. The gates matter more than the order: do not start S1 until the Prompt 13 gate passes on the phone.
- Supabase is your second copy of tasks and habits, NOT a backup of notes, journal, photos or body data. Prompt 29's local backup remains essential; copy backups off the phone.

## 3. Edits to the existing prompts

Apply these when pasting each prompt. Where a prompt says something these edits contradict, the edit wins.

- **Prompt 1**: minSdk 34, targetSdk 36. App name BetterLife, package com.sanjith.betterlife (do not ask again). Do NOT add INTERNET yet (it arrives in S1). Enable edge-to-edge in MainActivity. Add `supabase.url` and `supabase.anonKey` placeholders to local.properties (git-ignored) and document them in README.md. Skip the "install Android Studio" guidance if it is already installed; Claude Code builds and installs over USB.
- **Prompt 2**: Use Android 14+ checks only (remove version branches below 34). Show the four Vivo manual rows only when Build.MANUFACTURER is vivo; other phones see only the generic rows. Drop "NOT INTERNET" from step 1.
- **Prompt 3**: The overlay must handle edge-to-edge insets and predictive back (Android 16). Record in docs/TESTING.md whether the foreground service could be started from the background on Android 16 (see 2.3).
- **Prompt 4**: Add a matrix row: "I) Android 15+ background foreground-service start from the exact alarm". Note in TESTING.md which FGS type was used.
- **Prompt 5**: Every entity on the synced list (context.md 11a) gets syncState (default DIRTY) and serverUpdatedAt columns from version 1, so no migration is needed later. Add a SyncCursor table (tableName, lastPulledAt). Repository writes to synced tables set DIRTY in the same transaction.
- **Prompt 7**: Add "after a sync pull" to the reschedule triggers (wired up in S2).
- **Prompt 13**: Regression checklist adds "airplane mode for a full day: everything works".
- **Prompt 14**: Habit, HabitLog and PauseRange get syncState and serverUpdatedAt in the version 2 migration. HabitLog gets a unique index on (habitId, localDate).
- **Prompt 21**: The 30-day purge must skip synced rows whose tombstone has not been pushed yet.
- **Prompt 22**: Replace "No network: save the URL..." with: save instantly, then a WorkManager job with a network constraint fetches og:title / og:image / <title> using Ktor (HTTPS only, 5-second timeout, max 512 KB HTML, max 1 MB image, image saved privately). Failure keeps the plain link. Tests use Ktor MockEngine.
- **Prompt 26**: XP rules ignore changes that arrived via sync pull.
- **Prompt 27**: Onboarding adds an optional "Sign in to sync" step (skippable) and says clearly which data goes to the cloud and which stays on the phone. Settings gets the Account and Sync section from context.md 11a. Year in review: "no network" stays true for that screen.
- **Prompt 28**: Signing out of Supabase does not lock or wipe local data. Vault content is never synced (already true: notes are local only).
- **Prompt 29**: After restore, mark all synced tables DIRTY and reset sync cursors so the server matches the restored data. Remove "DO NOT BUILD: cloud sync" (sync exists now) but keep "do not back up media to Supabase".
- **Prompt 30**: Audit "manifest has no INTERNET" becomes "network used only by the sync/ package and the link preview job; no other host contacted". Acceptance (j) becomes "with airplane mode on, every feature works and nothing errors". Add acceptance (o) "sign in, create a task, uninstall, reinstall, sign in: the task comes back" and (p) "another Supabase user cannot read the owner's rows (RLS SQL test)". Release: the release build reads the Supabase URL and anon key from local.properties too.

## 4. New prompts

### Prompt S1: Supabase project, schema, row-level security and sign-in
Paste this after the Prompt 13 GATE has passed.

```
PROMPT S1 of 3 (sync): Supabase project, schema, RLS, sign-in

GOAL: Connect the app to my Supabase project and let me sign in with email and password, without changing how the app works offline.

FIRST: ask me for the Mumbai project's ref. Write it into PROGRESS.md.

BUILD (context.md section 11a, agent.md sections 4, 10, 12):
1. supabase/migrations/ with SQL creating the synced tables (tasks, projects, events, reminders, habits, habit_logs, pause_ranges) mirroring the Room columns, plus user_id uuid default auth.uid(), updated_at, deleted_at, a unique (user_id, habit_id, local_date) on habit_logs, and indexes on (user_id, updated_at). A trigger sets updated_at to now() on the server for the pull cursor (server_updated_at); the phone's own updatedAt is kept in a separate column for last-write-wins.
2. Enable row-level security on every table with select/insert/update policies "user_id = auth.uid()". No delete policy. Run the Supabase security advisor and fix every warning.
3. An SQL test proving a second user cannot select, insert or update the owner's rows.
4. Add INTERNET and ACCESS_NETWORK_STATE to the manifest. Add supabase-kt (BOM, auth-kt, postgrest-kt) and the Ktor OkHttp engine, with versions looked up in the official docs.
5. A SupabaseClient in sync/ created in AppContainer from BuildConfig values that come from local.properties. Never commit keys.
6. Settings > Account: sign up, sign in, sign out, with clear errors (wrong password, no internet, email not confirmed). The session persists across restarts. The app works fully while signed out.

TESTS REQUIRED: SQL RLS test, unit tests for the auth state mapping (signed out, signing in, signed in, error), a test that the app starts and the reminder engine works with no network. Manual checklist.
DO NOT BUILD: the sync engine (S2), Google sign-in, any upload of local-only tables.
Follow the response format in agent.md section 9.
```

Your checklist:
- [ ] In the Supabase dashboard, the 7 tables exist and each shows "RLS enabled".
- [ ] Security advisor shows no warnings.
- [ ] Sign up, confirm your email, sign in on the phone; close and reopen the app: still signed in.
- [ ] Airplane mode on: the app opens and reminders still fire.
- [ ] Search the repo for your anon key and for "service_role": neither appears in committed files.

### Prompt S2: The sync engine for tasks, projects, events and reminders
```
PROMPT S2 of 3 (sync): Offline-first sync engine

GOAL: Tasks, projects, events and reminder definitions sync to Supabase automatically, and the phone keeps working perfectly offline.

BUILD (context.md section 11a):
1. Pure domain: a ConflictResolver (last write wins on the whole row by updatedAt; tombstones win ties) and a SyncPlan builder. Unit tested.
2. Push: upsert DIRTY rows in batches, then mark them CLEAN only if their updatedAt did not change meanwhile (no lost local edit).
3. Pull: fetch rows with server_updated_at after the table's SyncCursor, apply them through the repositories in one transaction per batch, written CLEAN, then advance the cursor.
4. Triggers: debounced push after local changes, pull on app open, a periodic WorkManager job with a network constraint and exponential backoff. Never in a receiver, never on the main thread.
5. After a pull that changed reminders or their items: reschedule them and refresh widgets. Pulled changes never award XP or fire celebrations.
6. First sign-in on a phone with existing local data: push everything local, then pull (nothing is lost on either side).
7. Sync status: Today screen indicator and Settings (last sync, pending count, Sync now, last error in plain words, "Server paused" when the project is paused). "Delete my cloud data" with a double confirm.
8. Errors: offline, expired session (refresh, else ask to sign in), server paused, partial batch failure. Never crash, never lose a local row.

TESTS REQUIRED: unit tests with a fake remote for push, pull, conflict (both directions), tombstones, cursor advance, edit during push, first sign-in merge, retry after failure; a test that a pulled reminder is rescheduled exactly once; a test that pulled rows are not pushed back. Manual checklist.
DO NOT BUILD: habit sync (S3), syncing any local-only table.
Follow the response format in agent.md section 9.
```

Your checklist:
- [ ] Create a task on the phone: within a minute it appears in the Supabase table editor.
- [ ] Edit that row in the dashboard (change the title and its updatedAt to now): pull to sync on the phone and the title changes.
- [ ] Airplane mode on, create 3 tasks, turn it off: all 3 upload.
- [ ] Delete a task: the row on Supabase gets deleted_at, it is not removed.
- [ ] Uninstall the debug app, reinstall, sign in: tasks and events come back and their reminders fire.
- [ ] Check `adb shell dumpsys alarm` after a sync: no duplicate alarms.

### Prompt S3: Habit sync
Paste this after the Prompt 17 GATE has passed.
```
PROMPT S3 of 3 (sync): Habit, habit log and pause range sync

GOAL: Habits, their daily logs and pause ranges sync with the S2 engine.

BUILD:
1. Add Habit, HabitLog and PauseRange to the sync engine (they already have syncState from Prompt 14).
2. HabitLog conflict: same habit and local date = same logical row; last write wins (a later untick beats an earlier tick).
3. Pulled habit logs: streaks and consistency recompute (they are derived). Milestones from pulled logs are recorded quietly, never as a full-screen celebration, and never award XP.
4. Widgets refresh after a pull that changes habits.

TESTS REQUIRED: unit tests for HabitLog merge (tick vs untick, both directions), pulled milestone is quiet, streak recompute after pull, pause range sync. Manual checklist.
DO NOT BUILD: anything else.
Follow the response format in agent.md section 9.
```

Your checklist:
- [ ] Tick a habit: the habit_logs row appears on Supabase.
- [ ] Reinstall and sign in: grids, streaks and consistency look identical, and no celebration pops up.
- [ ] Untick on the phone while offline, then go online: the server row updates.
