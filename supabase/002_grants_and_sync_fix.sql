-- Pelonot — migration 002
-- Makes cloud sync capable of completing a request for the first time.
--
-- Run in the Supabase SQL Editor, after 'migration.sql'. Non-destructive:
-- no table is dropped and the 72 seeded class templates are left alone.
--
-- WHY THIS EXISTS
--
-- migration.sql creates three tables, enables RLS, and writes six policies.
-- It never GRANTs anything to the 'anon' role. RLS narrows access that a role
-- already has; it cannot confer any. So every request — read and write —
-- failed with 42501 'permission denied for table' before a single policy was
-- evaluated, and PostgREST returned 401. Verified against the live project:
--
--   GET  /rest/v1/profiles       -> 42501 permission denied for table profiles
--   POST /rest/v1/workouts       -> 42501 permission denied for table workouts
--
-- The tables themselves are fine (a genuinely missing table returns PGRST205),
-- which is how we know migration.sql ran and only the grants were absent.


-- 1. SCHEMA ACCESS ----------------------------------------------------------

GRANT USAGE ON SCHEMA public TO anon;


-- 2. GRANTS, NARROWED TO WHAT THE APP ACTUALLY DOES --------------------------
--
-- Deliberately not 'GRANT ALL'. Until phase 15 there is no auth.uid() to scope
-- policies against, so every policy in migration.sql is USING (true) — the
-- grant is the only thing limiting what a holder of the publishable key can
-- reach. Each grant below is one the app needs to make a call it actually
-- makes today; anything else stays denied.

-- Public data. World-readable by design.
GRANT SELECT ON public.class_templates TO anon;

-- Write-only. The app uploads rides and never reads them back, so withholding
-- SELECT means a leaked publishable key cannot enumerate anyone's training
-- history. Revisit in 15.3.2, which needs reads to restore a new device — by
-- which point auth.uid() exists to scope them properly.
GRANT INSERT ON public.workouts TO anon;

-- syncProfile upserts, and supabase-kt asks for the row back by default.
GRANT SELECT, INSERT, UPDATE ON public.profiles TO anon;


-- 3. THE POLICY THAT WAS MISSING ---------------------------------------------
--
-- profiles had policies for SELECT and UPDATE but none for INSERT, so an
-- upsert of a profile that did not exist yet was rejected even with the grant.

CREATE POLICY "Anon can insert profiles"
    ON public.profiles FOR INSERT
    WITH CHECK (true);

-- Drop the workouts SELECT policy rather than leave it as a USING (true) that
-- would silently start returning everyone's rides the moment someone adds a
-- SELECT grant without reading this file.
DROP POLICY IF EXISTS "Users can read own workouts" ON public.workouts;


-- 4. COLUMN NAME AGREEMENT ---------------------------------------------------
--
-- WorkoutDto serialises this field as 'recorded_at'; the table calls it
-- 'timestamp'. PostgREST rejects the insert for the unknown column.
--
-- Renaming the column rather than changing the DTO: 'timestamp' is a type name
-- and says nothing about which of a workout's several times it holds. The
-- local Room column keeps its own name — the DTO is the boundary and is
-- already the place that translates.

ALTER TABLE public.workouts RENAME COLUMN "timestamp" TO recorded_at;


-- 5. UPSERT NEEDS A CONFLICT TARGET ------------------------------------------
--
-- syncProfile upserts with no onConflict, so it conflicts on the primary key
-- 'id' — a UUID the DTO does not send, meaning every call inserts a new row
-- instead of updating the rider's. local_user_id is the natural key. It is
-- already UNIQUE in migration.sql; this is a no-op there and belt-and-braces
-- on a project where that constraint got lost.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.profiles'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute
               WHERE attrelid = 'public.profiles'::regclass
                 AND attname = 'local_user_id')
          ]
    ) THEN
        ALTER TABLE public.profiles ADD CONSTRAINT profiles_local_user_id_key
            UNIQUE (local_user_id);
    END IF;
END $$;


-- NOT DONE HERE, ON PURPOSE --------------------------------------------------
--
-- * workouts.user_id is still never populated — WorkoutDto carries no user id
--   at all, so a synced ride is anonymous. Fixing it properly needs the local
--   Int profile id mapped to the cloud profile UUID (PLAN 14.3), which in turn
--   wants auth. A first round trip does not need it; the column is nullable.
--
-- * class_templates.intervals_json is JSONB holding an array, while
--   ClassTemplateDto reads it as a String, so fetchClassTemplates throws. Only
--   the pull path is affected and nothing calls it yet (PLAN 14.5).
--
-- * Every remaining policy is USING (true). That is survivable only because
--   the grants above are narrow and the publishable key is not published.
--   PLAN 15.5 rewrites all of them against auth.uid(), and 14.10.4 says the
--   key is not checked in until that is done.
