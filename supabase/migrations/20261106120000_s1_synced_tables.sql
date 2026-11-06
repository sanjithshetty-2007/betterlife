-- Prompt S1: the synced tables of Phase 1, mirroring the Room columns (context.md 11a).
-- Habits, habit_logs and pause_ranges are added by Prompt S3, once Prompt 14 has defined their columns.
--
-- Columns: the phone's own times stay epoch milliseconds (created_at, updated_at, deleted_at, bigint),
-- so last-write-wins compares exactly what the phone wrote. server_updated_at is set by the server on
-- every write and is the pull cursor (Prompt S2). user_id is filled from the signed-in user.
-- Nothing is ever hard-deleted by the app: a delete is deleted_at (a tombstone), so there is no delete policy.

-- Sets the pull cursor on every insert and update. search_path is pinned (security advisor).
create or replace function public.set_server_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.server_updated_at := now();
  return new;
end;
$$;

create table public.projects (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  color_hex text,
  archived boolean not null default false,
  sort_order integer not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.tasks (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  notes text,
  due_at_millis bigint,
  due_zone_id text,
  due_date date,
  priority text not null check (priority in ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
  project_id uuid,
  parent_task_id uuid,
  category text,
  recurrence_rule text,
  repeat_from_completion boolean not null default false,
  importance text not null check (importance in ('NORMAL', 'FULL_SCREEN', 'ALARM')),
  completed_at bigint,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.events (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  type text not null check (type in ('EVENT', 'MEETING', 'BIRTHDAY')),
  all_day boolean not null,
  start_at_millis bigint,
  end_at_millis bigint,
  zone_id text,
  start_date date,
  end_date date,
  birth_year integer,
  location text,
  notes text,
  color_category text,
  recurrence_rule text,
  importance text not null check (importance in ('NORMAL', 'FULL_SCREEN', 'ALARM')),
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

-- Reminder DEFINITIONS only (item, offset, level). Fire state (scheduled, fired, snoozed) stays on the phone.
create table public.reminders (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  item_type text not null check (item_type in ('TASK', 'EVENT', 'STANDALONE')),
  item_id uuid,
  offset_minutes integer not null check (offset_minutes >= 0),
  importance text not null check (importance in ('NORMAL', 'FULL_SCREEN', 'ALARM')),
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

-- "Remind me" items (Prompt 10).
create table public.standalone_reminders (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  notes text,
  at_millis bigint not null,
  zone_id text not null,
  done_at bigint,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

-- The same trigger, pull index and row-level security on every synced table.
do $$
declare
  t text;
begin
  foreach t in array array['projects', 'tasks', 'events', 'reminders', 'standalone_reminders'] loop
    execute format(
      'create trigger %1$s_server_updated_at before insert or update on public.%1$s
         for each row execute function public.set_server_updated_at()', t);
    execute format('create index %1$s_user_server_updated on public.%1$s (user_id, server_updated_at)', t);
    execute format('alter table public.%1$s enable row level security', t);
    -- Only the owner of a row can see, add or change it. (select auth.uid()) is evaluated once per query.
    execute format(
      'create policy "%1$s: read own rows" on public.%1$s for select to authenticated
         using ((select auth.uid()) = user_id)', t);
    execute format(
      'create policy "%1$s: insert own rows" on public.%1$s for insert to authenticated
         with check ((select auth.uid()) = user_id)', t);
    execute format(
      'create policy "%1$s: update own rows" on public.%1$s for update to authenticated
         using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', t);
    -- Signed-out clients get nothing at all; signed-in ones can never hard-delete.
    execute format('revoke all on public.%1$s from anon', t);
    execute format('revoke delete, truncate on public.%1$s from authenticated', t);
  end loop;
end;
$$;
