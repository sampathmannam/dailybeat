-- Force row level security on every table in the public schema.
--
-- READ THIS BEFORE RELYING ON IT: in the current Supabase deployment these
-- statements are INERT. They are kept because they cost nothing and become
-- effective the moment the ownership assumption below changes, but they are not
-- an active control today and must not be counted as one.
--
-- Why inert: every table and all 25 SECURITY DEFINER functions in this schema are
-- owned by `postgres`, and `postgres` holds the BYPASSRLS role attribute. BYPASSRLS
-- overrides FORCE ROW LEVEL SECURITY unconditionally, so policies still do not
-- apply to the definer. Verified directly: an INSERT as `postgres` into
-- patrolgrid_retention_runs -- which has FORCE set and zero policies, so it must
-- fail with 42501 under an effective FORCE -- succeeds.
--
-- An earlier version of this file claimed FORCE meant "a missing ownership check
-- inside a SECURITY DEFINER function fails closed instead of silently reading
-- across tenants". That claim was wrong. The ownership checks inside those 25
-- functions remain the ONLY thing preventing cross-tenant access, exactly as
-- before this migration. Adding a SECURITY DEFINER function without one is still
-- a single point of failure.
--
-- Note also how the wrong claim survived review: the pgTAP suite and the
-- end-to-end RPC exercise were both run with FORCE enabled and both passed, which
-- was taken as evidence the control worked. They passed because the control does
-- nothing. A green suite after enabling a control shows only that nothing broke.
--
-- To make this an actual control, the schema objects must be owned by a role
-- without BYPASSRLS, and the tables that are currently written only by the definer
-- (patrolgrid_audit_events and the three retention tables, which have no INSERT
-- policy at all) would then need explicit owner-scoped policies or those writes
-- will fail closed.

alter table public.patrolgrid_subdivisions force row level security;
alter table public.patrolgrid_memberships force row level security;
alter table public.patrolgrid_units force row level security;
alter table public.patrolgrid_unit_members force row level security;
alter table public.patrolgrid_route_templates force row level security;
alter table public.patrolgrid_route_template_priorities force row level security;
alter table public.patrolgrid_missions force row level security;
alter table public.patrolgrid_priority_locations force row level security;
alter table public.patrolgrid_assignments force row level security;
alter table public.patrolgrid_sessions force row level security;
alter table public.patrolgrid_track_points force row level security;
alter table public.patrolgrid_priority_visits force row level security;
alter table public.patrolgrid_field_updates force row level security;
alter table public.patrolgrid_reviews force row level security;
alter table public.patrolgrid_audit_events force row level security;
alter table public.patrolgrid_retention_holds force row level security;
alter table public.patrolgrid_retention_hold_reviews force row level security;
alter table public.patrolgrid_retention_runs force row level security;
alter table public.dailybeat_backups force row level security;
