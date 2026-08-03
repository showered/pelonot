-- Pelonot migration 003 — WHO A ROW BELONGS TO
--
-- PLAN 14.2.1, 14.2.8, 15.5.1, 15.5.2, 15.5.3.
--
-- Run this in the Supabase SQL Editor after 002. Read the whole file before
-- running it: step 1 DELETEs rows, and step 6 makes the anon key unable to
-- reach profiles or workouts at all. Both are deliberate; both are explained.
--
--
-- WHY THIS EXISTS
-- ===============
--
-- The cloud had no idea who anything belonged to. Two separate holes:
--
-- 1. WorkoutDto carried no user_id. The column existed, was nullable, and was
--    never sent — so every ride this app has ever uploaded arrived anonymous.
--    Nothing failed: the insert returned 201 and the log said "Synced". An
--    anonymous ride cannot be restored onto a second bike, cannot appear on a
--    leaderboard, and is invisible to any policy written against auth.uid().
--
-- 2. profiles was keyed by local_user_id, UNIQUE, and it was the upsert's
--    onConflict target. local_user_id is a PER-DEVICE AUTOINCREMENT. Bike A's
--    first profile is 1 and bike B's first profile is 1, so the second tablet
--    to sync would not have collided harmlessly — it would have UPDATED the
--    first rider's row, overwriting their name, weight and FTP with its own.
--    That is not an edge case reachable by an unusual sequence. It is the
--    first thing that happens on the day a second bike signs in.
--
-- Both were survivable only because the connectivity model gates every call
-- behind an account (23.1) and nothing sets auth_user_id yet, so in practice
-- zero riders have been through this path. That is the entire reason this
-- migration is cheap: workouts holds one test row and profiles holds test
-- rows, and the same change costs a backfill once real histories are up here.
-- It is the 14.4 argument — change the shape while the cloud is empty —
-- applied to identity instead of to the payload.
--
--
-- THE DECISION: profiles.id IS the auth user id
-- =============================================
--
-- 14.2.1 originally asked to "carry the rider through: local user_id (Int) ->
-- cloud profiles.id (UUID)", storing the cloud id back on the tablet. That is
-- a two-step sync — insert the profile, read its generated id, save it locally
-- — and it can half-fail, leaving a tablet that has a cloud profile and does
-- not know its name.
--
-- It is also unnecessary. Rule 2 of the connectivity model says a profile is
-- in the cloud IF AND ONLY IF it has an account, so a cloud profile and an
-- auth user are 1:1 BY CONSTRUCTION. There is no such thing as a cloud profile
-- without an account and there never will be. So the auth user id is already a
-- perfectly good primary key, the app knows it at the moment of signing in,
-- and no round trip is needed to learn it.
--
-- What it buys, beyond removing a step: every RLS policy below is one line.
--
-- local_user_id is DROPPED rather than kept as informational. The cloud has no
-- use for which slot on which tablet a profile occupies — a restore onto a new
-- bike gets a new local id anyway — and a column that looks like an identity
-- and is not one is how this went wrong the first time.


BEGIN;

-- 1. CLEAR OUT THE ROWS THAT PREDATE CONSENT ---------------------------------
--
-- Every profile row currently up here was written by the defect in the fourth
-- row of the connectivity model's table: UserRepository.save upserted name,
-- weight and FTP on every create, rename and edit, for riders who had never
-- signed in to anything. Those rows should not exist. They also cannot survive
-- step 2, because their ids are gen_random_uuid() values with no auth user
-- behind them.
--
-- Deleting them is the cleanup, not a casualty of it.
--
-- Workouts are kept: the one real row is 14.4.5's measurement subject (it is
-- the pre-14.4 payload shape, which is exactly what that item wants to size).
-- Its user_id is already NULL and stays NULL, so it breaks no constraint. It
-- becomes invisible to PostgREST under step 5, which is correct — an
-- unattributed ride belongs to nobody and must not be readable by anybody. The
-- Management API can still see it.

DELETE FROM public.profiles;


-- 2. THE PRIMARY KEY BECOMES THE ACCOUNT -------------------------------------

ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_local_user_id_key;

ALTER TABLE public.profiles
    DROP COLUMN IF EXISTS local_user_id;

ALTER TABLE public.profiles
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_id_fkey
    FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- ON DELETE CASCADE, and note it is the OPPOSITE of the local rule.
--
-- On the tablet, deleting a profile keeps its rides (workouts.user_id is ON
-- DELETE SET NULL) because a household member leaving does not mean their
-- training history should evaporate off the bike. In the cloud, deleting the
-- ACCOUNT is the rider asking for their data to be gone — that is what 15.4.3
-- means and what GDPR requires — so it must take the rows with it.
--
-- Same-looking constraint, opposite requirement, and getting it backwards in
-- either direction is a serious bug. Local: keep. Cloud: erase.


-- 3. A RIDE HAS AN OWNER -----------------------------------------------------
--
-- The column already exists from migration.sql and already references
-- profiles(id). What changes is the delete action, for the reason in step 2:
-- SET NULL would leave an orphan that no auth.uid() matches, invisible to
-- every policy and therefore undeletable by the rider it belonged to. A
-- "delete my cloud data" button (15.4.2) that leaves unreachable rows behind
-- has not deleted the rider's cloud data.

ALTER TABLE public.workouts
    DROP CONSTRAINT IF EXISTS workouts_user_id_fkey;

ALTER TABLE public.workouts
    ADD CONSTRAINT workouts_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

-- Idempotency for the whole sync path (15.3.3). The local workout id is a
-- client-generated UUID and is already the primary key, so a retry, a
-- re-install or a first-sign-in backfill that overlaps an earlier upload
-- cannot double a ride — but only if the client upserts rather than inserts.
-- The constraint is here; WorkoutSyncWorker's use of it is 14.2.4/15.3.1.

CREATE INDEX IF NOT EXISTS workouts_user_id_recorded_at_idx
    ON public.workouts (user_id, recorded_at DESC);


-- 4. THE OLD POLICIES GO -----------------------------------------------------
--
-- All of them are USING (true). 14.0 recorded them as "a loaded gun rather
-- than a fired one" — survivable only because the grants were narrow and the
-- key was not published. Both of those protections are about to change: 15.1
-- gives real riders real sessions, and 14.10.4 publishes a key once this file
-- has run. USING (true) with a published key and a broad grant is every
-- rider's history readable by anyone who installs the app.

DROP POLICY IF EXISTS "Anyone can read class templates" ON public.class_templates;
DROP POLICY IF EXISTS "Users can read own profile"      ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile"    ON public.profiles;
DROP POLICY IF EXISTS "Anon can insert profiles"        ON public.profiles;
DROP POLICY IF EXISTS "Users can read own workouts"     ON public.workouts;
DROP POLICY IF EXISTS "Users can insert own workouts"   ON public.workouts;


-- 5. POLICIES AGAINST auth.uid() ---------------------------------------------
--
-- 15.5.1, 15.5.2. One line each, which is what step 2's key choice bought.
--
-- WITH CHECK as well as USING on every write, because they are different
-- questions: USING says which existing rows you may touch, WITH CHECK says
-- what the row is allowed to look like afterwards. A policy with only USING
-- lets a rider UPDATE their own row and set its id to somebody else's.

CREATE POLICY "A rider reads their own profile"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (id = auth.uid());

CREATE POLICY "A rider creates their own profile"
    ON public.profiles FOR INSERT
    TO authenticated
    WITH CHECK (id = auth.uid());

CREATE POLICY "A rider updates their own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

CREATE POLICY "A rider deletes their own profile"
    ON public.profiles FOR DELETE
    TO authenticated
    USING (id = auth.uid());

CREATE POLICY "A rider reads their own workouts"
    ON public.workouts FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "A rider records their own workouts"
    ON public.workouts FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "A rider updates their own workouts"
    ON public.workouts FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "A rider deletes their own workouts"
    ON public.workouts FOR DELETE
    TO authenticated
    USING (user_id = auth.uid());

-- 15.5.3: the class library is public data and stays world-readable. It is the
-- same 72 rows for everybody and it is the update channel of 23.2.3.
--
-- Read-only to everyone, including authenticated riders: a class is authored
-- by classlibrary/build.py and shipped in the APK, and nothing in the app has
-- any business writing one. No INSERT, UPDATE or DELETE policy exists for it
-- at all, which under RLS means nobody but the service role can write it.

CREATE POLICY "The class library is public"
    ON public.class_templates FOR SELECT
    TO anon, authenticated
    USING (true);


-- 6. GRANTS FOLLOW THE POLICIES ----------------------------------------------
--
-- RLS narrows access a role already has; it cannot confer any. 002 granted DML
-- to anon because there was no other role to grant to. Now there is.
--
-- THE CONSEQUENCE, STATED PLAINLY: after this runs, the anon key can read the
-- class library and NOTHING ELSE. No profile, no workout, no insert. Which
-- means 14.1.6 — the round-trip sighting — can no longer be driven by setting
-- auth_user_id by hand on the tablet, because the app would still be sending
-- the anon key with no session behind it.
--
-- That is the right trade and worth being explicit about. A round trip proved
-- with a key that bypasses RLS proves nothing about the path a real rider
-- takes; it proves the path a real rider does NOT take. 14.1.6 gets verified
-- once, properly, on top of 15.1.1's auth-kt session.

REVOKE ALL ON public.profiles   FROM anon;
REVOKE ALL ON public.workouts   FROM anon;
GRANT  SELECT ON public.class_templates TO anon;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.workouts TO authenticated;
GRANT SELECT ON public.class_templates TO authenticated;

-- DELETE is granted on both, which 002 did not grant on anything. It is what
-- 15.4.2 ("delete my cloud data", local record untouched) and 15.4.3 (account
-- deletion end to end) are built out of, and a rider who cannot delete their
-- own data does not really have an account, they have a submission.

COMMIT;


-- WHAT THIS FILE DELIBERATELY DOES NOT DO ------------------------------------
--
-- * It does not verify itself. 15.5.4 says every policy is checked FROM A
--   SECOND ACCOUNT rather than by reading the SQL, and that is the one place
--   in this project where being wrong is a breach rather than a bug. Nothing
--   above may be treated as done until two real sessions have been pointed at
--   each other's rows and bounced. Reading this file is not that check, and
--   neither is running it successfully.
--
-- * It does not publish a key. 14.10.4 is still open and still the owner's
--   decision — not because RLS is unfinished after this, but for its second
--   reason: a shared endpoint is a bill, roughly 13,000 rides of free tier
--   before it fails for everyone at once including the riders whose only
--   backup it was. cloud.properties stays empty and CloudConfigFenceTest keeps
--   failing the build if it does not.
--
-- * It does not add friendships (17.5) or any cross-rider visibility. Every
--   policy above is "your own rows and nobody else's", which is the correct
--   floor to build a friend graph on top of and the wrong thing to relax
--   speculatively in advance of one.
