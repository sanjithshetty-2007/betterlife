# Changing the database safely

BetterLife keeps everything in one SQLite database on the phone (`betterlife.db`), managed by **Room**. The **schema** is the list of tables and their columns. When a new prompt needs a new table or column, the schema changes, and the data already on your phone has to be carried over. That carrying-over step is a **migration**.

The one rule: **a schema change must never delete your data.** The app never uses Room's "destructive migration" option, which would wipe the database whenever a migration is missing. Without that option, a missing migration makes the app crash on start instead. That's loud, but your data is untouched and can be fixed with an update.

## Where things live

| What | Where |
|---|---|
| Tables (entities) | `app/src/main/java/com/sanjith/betterlife/data/db/entity/Entities.kt` |
| Database class, version number, migration list | `app/src/main/java/com/sanjith/betterlife/data/db/BetterLifeDatabase.kt` |
| Exported schema, one file per version | `app/schemas/com.sanjith.betterlife.data.db.BetterLifeDatabase/1.json`, `2.json`, … |
| Migration tests | `app/src/androidTest/java/com/sanjith/betterlife/data/db/MigrationTest.kt` |

The exported schema files are a record of exactly what each version looked like. They are committed to git and must never be edited by hand.

## Steps for every schema change

1. **Take a backup first.** Until Prompt 13's export exists, that means: don't install schema changes over data you care about.
2. **Change the entity** (add the column or table).
3. **Bump the version** in `@Database(version = …)` by one.
4. **Write the migration** in `BetterLifeDatabase.kt` and add it to `ALL_MIGRATIONS`, for example:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
       }
   }
   ```
   A new `NOT NULL` column always needs a `DEFAULT`, so existing rows get a value.
5. **Build once** (`gradlew.bat assembleDebug`). Room writes the new `2.json` into `app/schemas/…`.
6. **Add a migration test**: create the database at the old version, insert rows with SQL, run `helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)`, then check the rows are still there and the new column has its default. The existing `allMigrationsOpenOldDataWithRoom` test already walks every migration and needs no change.
7. **Run all tests on the phone**: `gradlew.bat connectedDebugAndroidTest`.
8. **Commit** the entity change, the migration, the new schema JSON and the test together.

If a migration is risky (renaming or splitting columns, changing types), the AI must stop and ask first (agent.md section 4).

## Supabase (from Prompt S1)

Server-side tables change through SQL files in `supabase/migrations/`, applied in order and never edited after being applied. A change to a synced table (tasks, projects, events, reminders, standalone reminders, and later habits, habit logs, pause ranges) needs **both**: a Room migration on the phone and a Supabase migration on the server.

Project: **betterlife**, Mumbai (ap-south-1), ref `qprtltwuuihbixbfgspm`.

| File | Applied | What |
|---|---|---|
| `20261106120000_s1_synced_tables.sql` | 2026-11-06 | projects, tasks, events, reminders, standalone_reminders; user_id from auth.uid(); server_updated_at trigger (pull cursor); (user_id, server_updated_at) index; row-level security read/insert/update own rows only; no delete (tombstones); nothing for anon |

Column rule: Room's camelCase becomes snake_case; the phone's times stay epoch milliseconds (`bigint`), dates stay `date`, enums stay their names as `text` with a check. `server_updated_at` exists only on the server.

The row-level security test is `supabase/tests/rls_test.sql`. Run it in the Supabase SQL editor after any policy change: it ends with `RLS test passed` and rolls everything back.

## Running the database tests

Connect the phone over USB with USB debugging on, then run:

```
gradlew.bat connectedDebugAndroidTest
```

This installs a small test app next to BetterLife Dev and runs the tests on the phone against throwaway databases. Your real `betterlife.db` is never touched. The project sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` in `gradle.properties`, so the debug app stays installed afterwards (otherwise Android Gradle uninstalls it, wiping its data and granted permissions).

## Version history

| Version | Prompt | Change |
|---|---|---|
| 1 | 5 | Projects, tasks, events, reminders, reminder log, sync cursors |
| 2 | 7 | reminders.scheduledFireAt, lastFiredFireAt |
| 3 | 8 | reminders.snoozeCount, snoozedUntil |
| 4 | 10 | New table standalone_reminders ("remind me" items) |
