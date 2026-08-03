-- Pelonot migration 006 — THE ONE TABLE THE SERVER SIDE MAY TOUCH
--
-- PLAN 15.6.4, 15.5.5. Run after 005.
--
--
-- WHAT THIS FIXES
-- ===============
--
-- The `link-device` Edge Function answered `500 permission denied for table
-- device_link` on its first real call — using the **service-role key**, which
-- is supposed to be the key that can do anything.
--
-- Reading `role_table_grants` back (the 15.5.7 habit, again) showed why:
-- `service_role` holds `REFERENCES, TRIGGER, TRUNCATE` on every table in
-- `public` and no `SELECT`, `INSERT`, `UPDATE` or `DELETE` on any of them —
-- including `class_templates`, which predates every migration in this
-- repository. So this was never granted here and never taken away here; the
-- project has simply always been in this state, and nothing had ever asked the
-- service role to do anything until now.
--
--
-- THE DECISION: GRANT IT `device_link` AND NOTHING ELSE
-- ====================================================
--
-- The obvious fix is the Supabase convention — `GRANT ALL ON ALL TABLES IN
-- SCHEMA public TO service_role` — and it is worth *not* taking, because the
-- state this project is accidentally in is better than the default one.
--
-- `service_role` bypasses row-level security entirely. A key holding DML on
-- `profiles` and `workouts` is a key that can read every rider's training
-- history and rewrite anybody's row, and the only thing standing between that
-- and a rider is a secret sitting in a function's environment. Nothing
-- server-side needs it: the app talks to Postgres as `authenticated`, under
-- policies verified from a second account (15.5.4), and the only thing that
-- genuinely cannot work that way is the pairing hand-off — because the whole
-- point of `device_link` is that **nobody** may read it, so the one writer has
-- to be outside RLS.
--
-- So the grant is exactly one table wide. If a later feature needs the service
-- role elsewhere, that is a decision with a name and a migration, rather than
-- a blanket already in place.


BEGIN;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_link TO service_role;

COMMIT;


-- WHY THIS IS NOT A HOLE IN 15.6's SECURITY MODEL -----------------------------
--
-- `device_link` still has RLS on and no policy, so `anon` and `authenticated`
-- reach it only through the four `SECURITY DEFINER` functions. What changes is
-- that the Edge Function — which already proves the caller's identity with
-- `auth.getUser(token)` before doing anything, and claims the pairing row
-- *before* minting a credential — can now write the row it verified it should.
--
-- The rows it writes live at most five minutes and delete themselves on
-- collection. There is no table of which accounts signed in on which bikes,
-- deliberately (see 004): that is a log nobody asked for and it would be the
-- most sensitive thing in the schema.
