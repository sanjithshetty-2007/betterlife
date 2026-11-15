-- Prompt S3: habit sync. habits, habit_logs and pause_ranges mirror the Room tables of Prompt 14
-- (database version 5), with the same user_id, server_updated_at, pull index and row-level security
-- as the Phase 1 tables (20261106120000_s1_synced_tables.sql). Reminders may now point at habits.

create table public.habits (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  color_hex text not null,
  avatar text,
  category text not null check (category in ('HEALTH', 'SELF_CARE', 'OTHER')),
  essential boolean not null default false,
  archived boolean not null default false,
  reminder_time time,
  sort_order integer not null default 0,
  start_date date not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

-- One row per habit and habit day (an untick is a tombstone on that row). Two phones that ticked the
-- same day with different ids meet on (user_id, habit_id, local_date): the app upserts on that key.
create table public.habit_logs (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  habit_id uuid not null,
  local_date date not null,
  status text not null check (status in ('DONE', 'KEPT', 'PAUSED')),
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now(),
  constraint habit_logs_one_per_day unique (user_id, habit_id, local_date)
);

create table public.pause_ranges (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  start_date date not null,
  end_date date,
  reason text not null check (reason in ('PAUSE', 'VACATION', 'SICK')),
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

-- Habit reminders (one daily reminder per habit, offset 0) can now be synced.
alter table public.reminders drop constraint reminders_item_type_check;
alter table public.reminders add constraint reminders_item_type_check
  check (item_type in ('TASK', 'EVENT', 'STANDALONE', 'HABIT'));

-- Last write wins on the server too, for the habit tables: an update carrying an OLDER updated_at
-- than the stored row is skipped (on a tie, a tombstone is kept). This protects the owner's edits
-- when a reinstalled phone re-seeds the four starter habits (fixed ids, "never edited" timestamp).
-- It runs before the server_updated_at trigger (triggers fire in name order), so a skipped row
-- keeps its server time.
create or replace function public.keep_newer_row()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.updated_at < old.updated_at
     or (new.updated_at = old.updated_at and old.deleted_at is not null and new.deleted_at is null) then
    return null;
  end if;
  return new;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array['habits', 'habit_logs', 'pause_ranges'] loop
    execute format(
      'create trigger %1$s_keep_newer before update on public.%1$s
         for each row execute function public.keep_newer_row()', t);
    execute format(
      'create trigger %1$s_server_updated_at before insert or update on public.%1$s
         for each row execute function public.set_server_updated_at()', t);
    execute format('create index %1$s_user_server_updated on public.%1$s (user_id, server_updated_at)', t);
    execute format('alter table public.%1$s enable row level security', t);
    execute format(
      'create policy "%1$s: read own rows" on public.%1$s for select to authenticated
         using ((select auth.uid()) = user_id)', t);
    execute format(
      'create policy "%1$s: insert own rows" on public.%1$s for insert to authenticated
         with check ((select auth.uid()) = user_id)', t);
    execute format(
      'create policy "%1$s: update own rows" on public.%1$s for update to authenticated
         using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', t);
    execute format('revoke all on public.%1$s from anon', t);
    execute format('revoke truncate on public.%1$s from authenticated', t);
    execute format('grant select, insert, update, delete on public.%1$s to authenticated', t);
    execute format(
      'create policy "%1$s: delete own rows only via delete_my_cloud_data" on public.%1$s for delete to authenticated
         using ((select auth.uid()) = user_id and current_setting(''betterlife.deleting_my_data'', true) = ''on'')', t);
  end loop;
end;
$$;

-- "Delete my cloud data" now also removes the habit tables (same flag-gated delete as S2).
create or replace function public.delete_my_cloud_data()
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if (select auth.uid()) is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  perform set_config('betterlife.deleting_my_data', 'on', true);
  delete from public.reminders where user_id = (select auth.uid());
  delete from public.standalone_reminders where user_id = (select auth.uid());
  delete from public.tasks where user_id = (select auth.uid());
  delete from public.events where user_id = (select auth.uid());
  delete from public.projects where user_id = (select auth.uid());
  delete from public.habit_logs where user_id = (select auth.uid());
  delete from public.pause_ranges where user_id = (select auth.uid());
  delete from public.habits where user_id = (select auth.uid());
  perform set_config('betterlife.deleting_my_data', 'off', true);
end;
$$;

revoke all on function public.delete_my_cloud_data() from public, anon;
grant execute on function public.delete_my_cloud_data() to authenticated;
