create table if not exists public.dailybeat_backups (
    user_id uuid primary key references auth.users(id) on delete cascade,
    snapshot jsonb not null check (jsonb_typeof(snapshot) = 'object'),
    client_updated_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.dailybeat_backups enable row level security;

revoke all on table public.dailybeat_backups from anon;
grant select, insert, update, delete on table public.dailybeat_backups to authenticated;

drop policy if exists "Users read their own DailyBeat backup" on public.dailybeat_backups;
create policy "Users read their own DailyBeat backup"
on public.dailybeat_backups for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "Users create their own DailyBeat backup" on public.dailybeat_backups;
create policy "Users create their own DailyBeat backup"
on public.dailybeat_backups for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "Users update their own DailyBeat backup" on public.dailybeat_backups;
create policy "Users update their own DailyBeat backup"
on public.dailybeat_backups for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "Users delete their own DailyBeat backup" on public.dailybeat_backups;
create policy "Users delete their own DailyBeat backup"
on public.dailybeat_backups for delete
to authenticated
using (auth.uid() = user_id);

create or replace function public.set_dailybeat_backup_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_dailybeat_backup_updated_at on public.dailybeat_backups;
create trigger set_dailybeat_backup_updated_at
before update on public.dailybeat_backups
for each row execute function public.set_dailybeat_backup_updated_at();
