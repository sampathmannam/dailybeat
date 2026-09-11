-- Owner-only RLS (202608310001) stops one user reading another's backup. It does not stop an
-- authenticated user writing an unbounded snapshot into their own row: the column only checked
-- jsonb_typeof = 'object'. A single 2 GB upload is then a storage- and cost-abuse vector that
-- RLS cannot see, because the row is legitimately the caller's own.
--
-- Cap the snapshot at 12 MB of JSON text — comfortably above a real whole-day capture, and aligned
-- with the client's existing 12 MB response ceiling in SupabaseBackupClient. octet_length on the
-- text form measures the logical payload the client sent, not the TOAST-compressed on-disk size.
alter table public.dailybeat_backups
    drop constraint if exists dailybeat_backups_snapshot_size;

alter table public.dailybeat_backups
    add constraint dailybeat_backups_snapshot_size
    check (octet_length(snapshot::text) <= 12582912) not valid;

-- not valid above: the constraint applies to every future insert and update without forcing a
-- full-table rewrite; validate separately so a pre-existing oversized row cannot block deployment.
alter table public.dailybeat_backups
    validate constraint dailybeat_backups_snapshot_size;
