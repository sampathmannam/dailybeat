alter table public.patrolgrid_field_updates
drop constraint patrolgrid_field_updates_category_check;

alter table public.patrolgrid_field_updates
add column review_id uuid
references public.patrolgrid_reviews(id) on delete restrict;

create index patrolgrid_field_updates_review
on public.patrolgrid_field_updates(review_id)
where review_id is not null;

alter table public.patrolgrid_field_updates
add constraint patrolgrid_field_updates_category_check
check (
    category in (
        'observation',
        'operational_deviation',
        'safety_event',
        'review_context'
    )
);

alter table public.patrolgrid_field_updates
add constraint patrolgrid_field_updates_review_link_check
check (
    (category = 'review_context' and review_id is not null)
    or (category <> 'review_context' and review_id is null)
);

drop policy if exists "Patrol records field updates"
on public.patrolgrid_field_updates;

create policy "Patrol records field updates"
on public.patrolgrid_field_updates for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and occurred_at <= now() + interval '5 minutes'
    and (
        (
            category in ('observation', 'operational_deviation', 'safety_event')
            and exists (
                select 1
                from public.patrolgrid_sessions session
                where session.mission_id = patrolgrid_field_updates.mission_id
                  and session.user_id = auth.uid()
                  and patrolgrid_field_updates.occurred_at >= session.started_at - interval '5 minutes'
                  and (
                      session.ended_at is null
                      or patrolgrid_field_updates.occurred_at <= session.ended_at + interval '5 minutes'
                  )
            )
        )
        or (
            category = 'review_context'
            and exists (
                select 1
                from public.patrolgrid_missions mission
                where mission.id = patrolgrid_field_updates.mission_id
                  and mission.status = 'needs_review'
                  and exists (
                      select 1
                      from public.patrolgrid_reviews review
                      where review.id = patrolgrid_field_updates.review_id
                        and review.id = (
                          select latest_review.id
                          from public.patrolgrid_reviews latest_review
                          where latest_review.mission_id = mission.id
                          order by latest_review.reviewed_at desc,
                                   latest_review.created_at desc,
                                   latest_review.id desc
                          limit 1
                      )
                        and review.outcome = 'needs_context'
                        and patrolgrid_field_updates.occurred_at >= review.reviewed_at - interval '5 minutes'
                        and patrolgrid_field_updates.occurred_at <= review.reviewed_at + interval '30 days 5 minutes'
                        and now() <= review.reviewed_at + interval '30 days 5 minutes'
                  )
            )
        )
    )
);

create or replace function public.patrolgrid_bump_review_context_version()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    new_version integer;
begin
    if new.category <> 'review_context' then
        return new;
    end if;

    update public.patrolgrid_missions mission
    set updated_at = now()
    where mission.id = new.mission_id
      and mission.status = 'needs_review'
      and new.user_id = auth.uid()
      and new.occurred_at <= now() + interval '5 minutes'
      and exists (
          select 1
          from public.patrolgrid_assignments assignment
          join public.patrolgrid_memberships membership
            on membership.subdivision_id = mission.subdivision_id
           and membership.user_id = assignment.user_id
           and membership.role = 'patrol'
           and membership.status = 'active'
          where assignment.mission_id = mission.id
            and assignment.user_id = auth.uid()
      )
      and exists (
          select 1
          from public.patrolgrid_reviews review
          where review.id = new.review_id
            and review.id = (
              select latest_review.id
              from public.patrolgrid_reviews latest_review
              where latest_review.mission_id = mission.id
              order by latest_review.reviewed_at desc,
                       latest_review.created_at desc,
                       latest_review.id desc
              limit 1
          )
            and review.outcome = 'needs_context'
            and new.occurred_at >= review.reviewed_at - interval '5 minutes'
            and new.occurred_at <= review.reviewed_at + interval '30 days 5 minutes'
            and now() <= review.reviewed_at + interval '30 days 5 minutes'
      )
    returning mission.version into new_version;

    if not found then
        raise exception 'Mission no longer accepts review context';
    end if;

    return new;
end;
$$;

revoke all on function public.patrolgrid_bump_review_context_version() from public;

create trigger patrolgrid_bump_review_context_version
after insert on public.patrolgrid_field_updates
for each row execute function public.patrolgrid_bump_review_context_version();

create trigger patrolgrid_audit_field_update
after insert on public.patrolgrid_field_updates
for each row execute function public.patrolgrid_write_audit_event();
