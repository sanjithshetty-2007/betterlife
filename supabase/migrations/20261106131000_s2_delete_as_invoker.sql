-- Prompt S2: "Delete my cloud data" without SECURITY DEFINER (security advisor lint 0029).
-- The function now runs as the signed-in user. Deleting is still impossible through the normal API:
-- the delete policies only allow it while this function has set a transaction-local flag, and clients
-- cannot set that flag themselves (set_config is not exposed by the API).

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
  perform set_config('betterlife.deleting_my_data', 'off', true);
end;
$$;

revoke all on function public.delete_my_cloud_data() from public, anon;
grant execute on function public.delete_my_cloud_data() to authenticated;

do $$
declare
  t text;
begin
  foreach t in array array['projects', 'tasks', 'events', 'reminders', 'standalone_reminders'] loop
    execute format('grant delete on public.%1$s to authenticated', t);
    execute format(
      'create policy "%1$s: delete own rows only via delete_my_cloud_data" on public.%1$s for delete to authenticated
         using ((select auth.uid()) = user_id and current_setting(''betterlife.deleting_my_data'', true) = ''on'')', t);
  end loop;
end;
$$;
