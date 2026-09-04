create or replace function public.patrolgrid_submit_review(
    target_mission uuid,
    target_expected_version integer,
    target_outcome text,
    target_notes text
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    mission_record public.patrolgrid_missions;
    review_status text;
    new_version integer;
begin
    if auth.uid() is null then
        raise exception 'Authentication is required';
    end if;

    if target_expected_version is null or target_expected_version < 1 then
        raise exception 'Expected mission version must be positive';
    end if;

    if target_outcome is null
       or target_outcome not in ('approved', 'needs_context', 'technically_inconclusive') then
        raise exception 'Invalid review outcome';
    end if;

    if target_notes is not null and char_length(target_notes) > 4000 then
        raise exception 'Review notes cannot exceed 4000 characters';
    end if;

    select * into mission_record
    from public.patrolgrid_missions mission
    where mission.id = target_mission
    for update;

    if not found then
        raise exception 'Mission is unavailable';
    end if;

    if not exists (
        select 1
        from public.patrolgrid_memberships membership
        where membership.subdivision_id = mission_record.subdivision_id
          and membership.user_id = auth.uid()
          and membership.role = 'supervisor'
          and membership.status = 'active'
    ) then
        raise exception 'PatrolGrid supervisor access required';
    end if;

    if mission_record.status not in ('needs_review', 'completed') then
        raise exception 'Mission is not ready for review';
    end if;

    if mission_record.version <> target_expected_version then
        raise exception 'Mission version conflict';
    end if;

    review_status := case
        when target_outcome = 'needs_context' then 'needs_review'
        else 'completed'
    end;

    insert into public.patrolgrid_reviews (
        mission_id,
        reviewer_id,
        outcome,
        notes
    ) values (
        mission_record.id,
        auth.uid(),
        target_outcome,
        coalesce(target_notes, '')
    );

    update public.patrolgrid_missions
    set status = review_status
    where id = mission_record.id
    returning version into new_version;

    return new_version;
end;
$$;

drop policy if exists "Supervisors create reviews"
on public.patrolgrid_reviews;

revoke insert on public.patrolgrid_reviews from authenticated;

revoke all on function public.patrolgrid_submit_review(uuid, integer, text, text) from public;
grant execute on function public.patrolgrid_submit_review(uuid, integer, text, text) to authenticated;
