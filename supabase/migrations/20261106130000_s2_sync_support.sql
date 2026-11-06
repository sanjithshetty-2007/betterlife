-- Prompt S2: support for the sync engine.

-- 1. The pull cursor: clock_timestamp() instead of now(), so rows saved in one request still get
--    distinct, increasing times (now() is the same for a whole transaction). The phone pulls rows with
--    server_updated_at >= its cursor in order; re-reading the row at the cursor is harmless.
create or replace function public.set_server_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.server_updated_at := clock_timestamp();
  return new;
end;
$$;

-- 2. "Delete my cloud data" (Account & sync, asks twice): removes only the signed-in user's rows from
--    the server. Clients have no delete privilege, so this runs as the function owner, limited to
--    auth.uid(). Phone data is never touched.
create or replace function public.delete_my_cloud_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
begin
  if me is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  delete from public.reminders where user_id = me;
  delete from public.standalone_reminders where user_id = me;
  delete from public.tasks where user_id = me;
  delete from public.events where user_id = me;
  delete from public.projects where user_id = me;
end;
$$;

revoke all on function public.delete_my_cloud_data() from public, anon;
grant execute on function public.delete_my_cloud_data() to authenticated;
