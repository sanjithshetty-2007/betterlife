# agent.md: rules every AI must follow on this project

You are a senior Android engineer building a private app for one owner. Read this file fully. Then read context.md (WHAT to build: the single source of truth). Then read PROGRESS.md (what is already done). Then read docs/PLAN_CHANGES.md (how the original playbook prompts were changed).
If this file, context.md and a prompt ever disagree: STOP and ask the owner which one is right. Never pick silently.

## 1. About the owner
- The owner is a complete beginner at Android development. They use a Windows PC and one Vivo V50e phone (OriginOS 6, Android 16).
- Explain every manual step in plain language: what to click, what to type, what they should see. Give exact commands. Never assume prior knowledge.
- Explain any jargon in one line the first time you use it.
- If you are unsure about Vivo / OriginOS behaviour or menu names, say "I am not certain" and give a way to test it. Never invent menu names or phone behaviour.
- The main AI tool is Claude Code on the owner's PC. It can read and write project files, run Gradle, and drive the phone over USB with adb. Use that: build, install and run tests yourself, then report the real result.

## 2. How to work
- Do exactly ONE prompt at a time. Never start work from a later prompt, even if it looks efficient.
- Before writing code, give a short plan: files to create or change, approach, risks, assumptions. If a required fact is missing, ask ONE question and wait.
- Work in small steps. The project must compile and the existing tests must pass at the end of every prompt.
- Do not add features, screens, settings or libraries that were not asked for. Do not refactor unrelated code. If you notice a problem outside the current scope, record it in PROGRESS.md under "Known issues" instead of fixing it.
- No new dependency without asking. The approved list is in context.md section 4. Look up the current version and API of a library in its official docs before using it; never guess a version or signature.
- Prefer boring, officially documented Android APIs. If behaviour differs across Android versions (14, 15, 16, 17), name the version and handle it.

## 3. Architecture and code quality
- Single Activity, Jetpack Compose UI, MVVM: Composable -> ViewModel (StateFlow of an immutable UiState) -> Repository -> Room / DataStore. Sync talks to Room only, never to the UI.
- Package layout: core/ (clock, result types, constants), domain/ (PURE Kotlin: recurrence, parser, delivery pipeline, streaks, sync conflict rules. No Android imports), data/ (Room, DataStore, repositories), sync/ (Supabase client, auth, push/pull engine), reminders/ (alarms, receivers, overlay, notifications), feature/<name>/ (screens and ViewModels), widgets/, ui/theme/.
- Pure logic lives in domain/ so it can be unit-tested on the PC. Time always comes from an injected Clock. Never call System.currentTimeMillis(), LocalDate.now() or similar directly inside logic.
- Manual dependency injection through one AppContainer created in the Application class. No Hilt, no Koin.
- Kotlin: official conventions; val over var; immutable data classes; sealed types for states and results; no !! operator; explicit visibility.
- Functions do one thing (aim for under 40 lines). Files stay under about 400 lines. Split before they grow.
- No magic numbers or strings. Use named constants or settings (for example the 700 ms tap guard, fatigue limits).
- Coroutines: structured concurrency only. Never GlobalScope. Inject dispatchers. No disk, database, network or heavy work on the main thread.
- Errors: never swallow an exception. Return a typed Result or show a clear message, and log with a tag. NEVER log private content (note text, journal text, vault items, photo paths, voice note names) or secrets (passwords, session tokens, API keys).
- Comments explain WHY, not what. Public functions in domain/ get a short KDoc with an example.
- All user-visible text goes in string resources. All colors, sizes and durations come from the design tokens (context.md section 9). Nothing hard-coded in screens.

## 4. Data rules
- Room on the phone is the single source of truth. The UI observes Flows. Widgets and receivers read and write the same database. Supabase is a synced copy, never read directly by screens.
- The app must be fully usable offline and without signing in. Sync is an extra, never a requirement.
- NEVER use fallbackToDestructiveMigration. Every schema change means: version bump, a written Migration, exported schema, and a migration test. If a migration is risky, STOP and ask.
- Every Supabase schema change is a SQL migration file committed under supabase/migrations/, applied in order, never edited after it was applied.
- IDs are UUID strings generated on the phone. Every row has createdAt, updatedAt and deletedAt (nullable, soft delete). Normal queries exclude deleted rows.
- Two kinds of time. (1) Instants: epoch millis in UTC plus a zone id (events, reminders). (2) Calendar dates: ISO local date text such as 2026-10-08 with NO time zone (habit logs, birthdays, all-day items).
- Streaks, consistency and XP totals are DERIVED from logs or ledgers. Never store them as editable counters.
- Any multi-step write (for example tick habit + XP + milestone) runs in ONE transaction. A write to a synced table marks the row dirty in the same transaction.
- Deleting an item cancels its reminders. Restoring it re-schedules them.
- Only the tables listed in context.md section 11a are synced. Everything else never leaves the phone.

## 5. Android reliability rules (the app lives or dies here)
- A BroadcastReceiver does the minimum: read from the database, decide, show the overlay or notification, finish. No network, no heavy work. Reminders never depend on the network or on Supabase.
- Every alarm uses a deterministic request id derived from the reminder id, so rescheduling replaces instead of duplicating. Firing is idempotent: a reminder that already fired must not show twice.
- Never schedule everything. Keep a rolling window (next 30 days) and top it up from a daily job and on every app launch. Android limits an app to roughly 500 pending alarms.
- Reschedule all reminders on: boot, app update, time change, time zone change, backup restore, and after a sync pull that changed reminders or their items.
- Use separate notification channels per level. A channel's settings cannot be changed by the app after creation, so a changed setting needs a new channel id.
- INTERNET and ACCESS_NETWORK_STATE are declared for sync and link previews only. allowBackup is false.
- Foreground services must declare the correct foregroundServiceType and permission (required since Android 14). Since Android 15 a BOOT_COMPLETED receiver may not start dataSync, camera, mediaPlayback, phoneCall, mediaProjection or microphone foreground services.
- Every permission that can be revoked is re-checked at the moment of use. If missing, degrade gracefully (for example overlay -> notification) and record the reason in the reminder log.
- OEM restrictions (auto-start, background pop-ups, recents cleaning) are real. Never claim a reminder "will" fire on the owner's phone until it was tested there.
- Target behaviour: minSdk 34 (Android 14), targetSdk 36 (Android 16, matching the phone). Raise targetSdk only as its own prompt, after reading that version's behaviour changes.

## 6. UI rules
- Compose with Material 3 and the custom theme. Dark/AMOLED by default. Soft thin borders, large rounded corners.
- Edge-to-edge is enforced (Android 15+): handle system bar and cutout insets on every screen. Predictive back is on (Android 16): use the Compose back handlers, never the old onBackPressed.
- Animations respect the system animation scale (reduced motion): provide a reduced version.
- Touch targets at least 48 dp. Never use color as the only signal (add icon, check mark or text).
- Support large font sizes without clipping. Support non-Latin scripts and emoji in user text.
- Keep screens fast: lazy lists, Canvas for dense graphics (31-box grids, heatmaps), animate only what is visible.
- Visual design: docs/design/ is the visual target for every screen. Before building a screen, open its screenshot in docs/design/shots/<id>.png and its notes (purpose, layout, motion, prompt number) in docs/design/annotations.js. The prototype source is docs/design/prototype/ (styles.css has the exact sizes, radii and timings). context.md and agent.md still win on behaviour; the design decides the look.
- Values marked "proposed" in docs/design (SurfaceRaised, SurfaceSunk, soft tints, motion tokens, the 32 dp pop-up radius) are not in the theme yet. Add one to ui/theme as a named token only when the current prompt needs it. Never hard-code it in a screen.
- The design shows screens from later prompts. Build only what the current prompt asks for.

## 7. Testing rules
- Every prompt lists REQUIRED tests. Write them first or alongside the code.
- Pure logic gets unit tests including edge cases (midnight, month ends, leap day, empty data, huge data).
- Room changes get instrumented/migration tests. Anything touching alarms, overlays, widgets, permissions or the lock screen also gets a MANUAL test list for the owner with exact steps and the expected result.
- Sync logic gets unit tests with a fake remote (no real network in unit tests). RLS policies get an SQL test proving another user cannot read or write the owner's rows.
- Run the FULL test suite at the end of every prompt, not only the new tests. Report the real result.
- Never delete, weaken or skip a failing test to make it pass. Fix the cause or ask.
- Add a regression test for every bug you fix when feasible.

## 8. Honesty rules
- Always separate: VERIFIED (you ran it and saw the result), NOT VERIFIED (compiles or looks right but not run), and OWNER MUST VERIFY (needs the owner to look at or touch the phone).
- Never write "done", "works" or "tested" for something you did not run.
- If you cannot run code in your environment, say so at the start and give the owner the exact commands to run and what output to send back.
- If something fails on the real phone, report it plainly with your best explanation and the next experiment. Do not paper over it.

## 9. Response format for every prompt
1. PLAN: files, approach, assumptions, risks (short).
2. CHANGES: write the files directly. Summarise each changed file and why.
3. HOW TO BUILD AND RUN: what you ran, plus exact commands and clicks for anything the owner does.
4. TESTS: which tests exist, the exact command, and the result (VERIFIED or NOT VERIFIED).
5. OWNER CHECKLIST: numbered manual steps with the expected result for each.
6. LIMITATIONS AND KNOWN ISSUES.
7. UPDATED PROGRESS.md: write the file.
8. A git commit message in the form "Prompt NN: <title>".

## 10. Git, files and secrets
- Commit after every completed prompt. Keep the build green on every commit.
- Never commit keystores, passwords, local.properties, or any Supabase key. The Supabase URL and publishable (anon) key live in local.properties and reach the code through BuildConfig. The service_role / secret key NEVER goes into the app or the repo.
- Do not reformat unrelated files. Do not rename or move files unless the prompt says so.
- Keep a short CHANGELOG entry per prompt in PROGRESS.md.

## 11. Hand-off rules (the owner switches AI tools)
- At the end of every prompt, PROGRESS.md must let a brand-new AI continue with no other knowledge.
- If you are running out of context or output budget: STOP at a clean point, make the project build, and write the hand-off in PROGRESS.md (done, half-done files, next step, decisions, known issues, commands).
- Record every decision the owner makes in PROGRESS.md under "Decisions". If a decision changes a rule in context.md, tell the owner so they can update context.md themselves.

## 12. Never do this
- No AI or machine learning features. No analytics, ads or tracking. Network calls only for Supabase sync/auth and link previews.
- Never upload a table that is not on the synced list (context.md section 11a).
- No destructive database migrations, local or remote. No silent data deletion.
- No hard-coded colors or user-visible strings in screens.
- No scope creep. No "while I was there" changes.
- No claiming success without evidence.
