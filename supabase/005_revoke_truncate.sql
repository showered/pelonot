-- Pelonot migration 005 — THE PRIVILEGES NOBODY GRANTED ON PURPOSE
--
-- PLAN 15.5.5. Run after 004.
--
--
-- WHAT THIS FIXES
-- ===============
--
-- `003` was careful about DML: it revoked everything from `anon` and granted
-- `authenticated` exactly SELECT, INSERT, UPDATE and DELETE. Checking the
-- result afterwards — with a query rather than by re-reading the file — turned
-- up privileges neither `002` nor `003` ever wrote:
--
--   class_templates  anon           REFERENCES, SELECT, TRIGGER, TRUNCATE
--   device_link      anon           REFERENCES, TRIGGER, TRUNCATE
--
-- They come from Supabase's own default privileges, which grant ALL on new
-- tables in `public` to `anon` and `authenticated`. `003`'s `REVOKE ALL` caught
-- them on `profiles` and `workouts`; `class_templates` predates it and
-- `device_link` was created after it, so both kept the full set.
--
--
-- WHY IT MATTERS, AND WHY IT IS NOT AN EMERGENCY
-- ==============================================
--
-- **TRUNCATE ignores row-level security.** No policy can stop it, because it is
-- a table-level operation rather than a row-level one — so `anon` holding
-- TRUNCATE on `class_templates` is, on paper, the whole 72-class library one
-- statement away from being emptied, and on `device_link` it is every pairing
-- in flight.
--
-- It is not reachable today: PostgREST speaks only SELECT, INSERT, UPDATE and
-- DELETE over HTTP, `anon` has no login of its own, and there is no SQL
-- endpoint. So this is a privilege that exists and cannot currently be used —
-- which is exactly the state 14.0 described the old `USING (true)` policies in:
-- "a loaded gun rather than a fired one". The response is the same. It costs
-- four lines to put down.
--
-- TRIGGER and REFERENCES go with it. Neither is reachable either, and neither
-- was ever intended: TRIGGER lets a role attach code to a table's writes, and
-- REFERENCES lets one point a foreign key at it, which is a way to learn
-- whether a row exists without being able to read it.


BEGIN;

REVOKE TRUNCATE, TRIGGER, REFERENCES
    ON public.class_templates, public.device_link, public.profiles, public.workouts
    FROM anon, authenticated;

COMMIT;


-- WHAT THIS DOES NOT DO ------------------------------------------------------
--
-- It does not change Supabase's default privileges, so the **next** table
-- created in `public` will arrive with the same full grant. That is deliberate:
-- altering the default is a project-wide change with consequences for tooling
-- that nobody here has tested, and every new table needs its grants written
-- deliberately anyway.
--
-- The rule it leaves behind instead, and it is the 15.5.5 rule restated:
-- **after any migration that creates a table, read `role_table_grants` back.**
-- What a migration granted and what a table ends up holding are different
-- questions, and only the second one is the answer.
