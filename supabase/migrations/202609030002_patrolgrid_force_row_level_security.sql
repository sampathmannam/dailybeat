-- Force row level security on every table in the public schema.
--
-- All 19 tables already ENABLE row level security, which is enough for PostgREST:
-- it connects as `authenticator` and switches to anon/authenticated/service_role,
-- none of which own these tables, so policies apply.
--
-- What ENABLE does not cover is the table owner. The 22 SECURITY DEFINER functions
-- in this schema run as owner and therefore bypass RLS entirely, which means the
-- ownership checks written inside those functions carry the whole isolation burden
-- on their own. A future function added without one of those checks would have no
-- second line of defence.
--
-- FORCE makes policies apply to the owner too, so a missing ownership check inside
-- a SECURITY DEFINER function fails closed instead of silently reading across
-- tenants.
--
-- Verified before shipping: with FORCE active on all 19 tables the pgTAP suite
-- passes 282/282, and tools/patrolgrid/supabase_e2e.py passes end to end, covering
-- track point ingestion, field updates, priority visits, the server-owned session
-- lifecycle, atomic assignment, supervisor review, duty-window enforcement, the
-- 365-day retention clock and the audit trail. Nothing in the workflow depended on
-- the owner bypass.

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
