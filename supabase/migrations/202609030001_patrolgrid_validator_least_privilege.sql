-- Close the two route-geometry validators to unauthenticated callers.
--
-- Every other function in this schema is followed by an explicit
-- `revoke all on function ... from public`. These two were the only omissions, so
-- Postgres's default `EXECUTE TO PUBLIC` survived, `anon` inherited it, and
-- PostgREST published them as unauthenticated RPC endpoints.
--
-- No tenant data is reachable through them: both are pure `immutable` jsonb
-- validators that touch no table and are self-bounded at 256 KB and 10,000
-- positions. What they did offer an unauthenticated caller was a bounded CPU burn,
-- and an endpoint nobody intended to publish.
--
-- The check constraints on patrolgrid_missions and patrolgrid_route_templates call
-- these during row writes, and a check constraint runs as the writing role, so
-- `authenticated` and `service_role` keep EXECUTE.

revoke all on function public.patrolgrid_route_position_is_valid(jsonb) from public;
revoke all on function public.patrolgrid_route_position_is_valid(jsonb) from anon;
revoke all on function public.patrolgrid_route_geojson_is_valid(jsonb) from public;
revoke all on function public.patrolgrid_route_geojson_is_valid(jsonb) from anon;

grant execute on function public.patrolgrid_route_position_is_valid(jsonb)
    to authenticated, service_role;
grant execute on function public.patrolgrid_route_geojson_is_valid(jsonb)
    to authenticated, service_role;
