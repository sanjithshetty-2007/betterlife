-- Prompt S1 row-level security test. Run in the SQL editor (or with the Supabase MCP execute_sql).
-- Everything happens in one transaction that is rolled back, so no test user or row is left behind.
-- Any failed check raises an exception that starts with "RLS FAIL"; success ends with "RLS test passed".
begin;

insert into auth.users (id, email, aud, role) values
  ('00000000-0000-4000-8000-00000000000a', 'rls-owner@example.invalid', 'authenticated', 'authenticated'),
  ('00000000-0000-4000-8000-00000000000b', 'rls-other@example.invalid', 'authenticated', 'authenticated');

-- The owner writes one row in every synced table.
set local role authenticated;
set local request.jwt.claims = '{"sub": "00000000-0000-4000-8000-00000000000a", "role": "authenticated"}';
insert into public.projects (id, name, created_at, updated_at) values ('10000000-0000-4000-8000-000000000001', 'P', 1, 1);
insert into public.tasks (id, title, priority, importance, created_at, updated_at)
  values ('10000000-0000-4000-8000-000000000002', 'T', 'NONE', 'NORMAL', 1, 1);
insert into public.events (id, title, type, all_day, importance, created_at, updated_at)
  values ('10000000-0000-4000-8000-000000000003', 'E', 'EVENT', true, 'NORMAL', 1, 1);
insert into public.reminders (id, item_type, item_id, offset_minutes, importance, created_at, updated_at)
  values ('10000000-0000-4000-8000-000000000004', 'TASK', '10000000-0000-4000-8000-000000000002', 0, 'NORMAL', 1, 1);
insert into public.standalone_reminders (id, title, at_millis, zone_id, created_at, updated_at)
  values ('10000000-0000-4000-8000-000000000005', 'S', 1, 'Asia/Kolkata', 1, 1);

do $$
begin
  if (select count(*) from public.tasks) <> 1 then raise exception 'RLS FAIL: the owner cannot read their own task'; end if;
  if (select user_id from public.tasks) <> '00000000-0000-4000-8000-00000000000a' then
    raise exception 'RLS FAIL: user_id was not filled from the signed-in user';
  end if;
  if (select server_updated_at from public.tasks) is null then raise exception 'RLS FAIL: server_updated_at not set'; end if;
  -- The owner cannot hard-delete either (deletes are tombstones).
  begin
    delete from public.tasks;
    raise exception 'RLS FAIL: delete was allowed';
  exception when insufficient_privilege then null;
  end;
end;
$$;

-- A second signed-in user sees nothing, changes nothing and cannot write rows for the owner.
set local request.jwt.claims = '{"sub": "00000000-0000-4000-8000-00000000000b", "role": "authenticated"}';
do $$
declare
  t text;
  n integer;
begin
  foreach t in array array['projects', 'tasks', 'events', 'reminders', 'standalone_reminders'] loop
    execute format('select count(*) from public.%I', t) into n;
    if n <> 0 then raise exception 'RLS FAIL: another user can read %', t; end if;
    execute format('update public.%I set updated_at = 2', t);
    get diagnostics n = row_count;
    if n <> 0 then raise exception 'RLS FAIL: another user can update %', t; end if;
  end loop;
  begin
    insert into public.tasks (id, user_id, title, priority, importance, created_at, updated_at)
      values ('10000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-00000000000a', 'X', 'NONE', 'NORMAL', 1, 1);
    raise exception 'RLS FAIL: another user inserted a row for the owner';
  exception when insufficient_privilege then null;
  end;
end;
$$;

-- Signed out (anon) gets nothing at all.
set local role anon;
do $$
begin
  begin
    perform 1 from public.tasks;
    raise exception 'RLS FAIL: anon can read tasks';
  exception when insufficient_privilege then null;
  end;
end;
$$;

reset role;
select 'RLS test passed' as result;
rollback;
