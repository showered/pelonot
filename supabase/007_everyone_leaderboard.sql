-- Pelonot migration 007 — LEADERBOARDS ACROSS BIKES, WITHOUT A FRIEND GRAPH
--
-- PLAN 18.5, 18.11, 15.5.6. Run after 006.
--
--
-- THE OWNER'S DECISION, 3 August 2026
-- ===================================
--
-- Verbatim: *"If this application were to scale to millions of users then we
-- would need to add proper follow, unfollow, block, all that kind of stuff. But
-- in this case, for now, there will only be 3 or 4 users! So I think everyone
-- should just have visibility over everyone's scores for now. Leaderboards and
-- ghosts should contain ALL registered users, in addition to ALL household
-- users."*
--
-- An earlier draft of this file built a `friendships` table with a request /
-- accept / block lifecycle. It was applied, and it is dropped below — the
-- friend graph was the right answer to a question nobody had asked. Four people
-- who already know each other do not need to send each other requests, and the
-- graph would have been three tables of ceremony around a fact everyone in the
-- household already agrees on.
--
--
-- WHAT "EVERYONE" IS COUPLED TO, AND IT IS NOT OPTIONAL
-- =====================================================
--
-- "Everyone registered" is a safe rule exactly as long as **registering is not
-- open to the public**, and those two settings live in different places:
--
--   * this file makes every account visible to every other account;
--   * Supabase's `disable_signup` decides who can become an account.
--
-- Hosting the companion web app publishes the anon key — that is what the key
-- is for — so with signup open, *anyone who finds the URL* can create an
-- account and land on the family's leaderboard. The two settings have to be
-- read together or the second one quietly undoes the first.
--
-- **So: turn public sign-up off** (Authentication → Providers → Email →
-- "Allow new users to sign up"), and add the household's accounts by
-- invitation. PLAN 18.11.1 carries this and it is a prerequisite rather than a
-- nice-to-have.
--
--
-- WHAT THIS STILL REFUSES TO DO
-- =============================
--
-- Visibility is granted through **two narrow functions**, not by relaxing the
-- policies on `workouts` and `profiles`. Those stay "your own rows and nobody
-- else's" (15.5.6), because "everyone can see everyone's *scores*" is not the
-- same sentence as "everyone can read everyone's rows", and the difference is
-- email addresses, ride dates, RPE ratings and every other column a table grows
-- later. A function returns the columns it names and no column added after it.


BEGIN;

-- 0. THE FRIEND GRAPH THAT WAS NOT NEEDED ------------------------------------
--
-- Created minutes earlier in this same sitting and dropped on the owner's
-- instruction. Safe: it never held a row that mattered, and no other object
-- references it.

DROP FUNCTION IF EXISTS public.class_leaderboard(text);
DROP TABLE IF EXISTS public.friendships;


-- 1. A SUMMARY OF WHERE THE WATTS CAME FROM ----------------------------------
--
-- 14.4.7 put provenance **per sample** inside the payload on purpose: a board
-- that drops out mid-ride is `Mixed` precisely because the samples disagree,
-- and a scalar would have to pick a side. That argument is about the payload
-- and still holds.
--
-- This is a different thing: the reduction the app already computes *from*
-- those samples, stored so it can be **queried** without opening 47 KB of JSON
-- per rider per board. The leaderboard needs it because 24.4.2 and 18.7 both
-- turn on it — a modelled watt must never be ranked against a measured one,
-- since `PowerModel` scores RMSE 137 W against the real board.
--
-- Nullable, and null means *nobody wrote it down* — the same claim
-- `power_is_measured` makes locally, and the state every row uploaded before
-- today is in. Leaving those unranked is the honest treatment.

ALTER TABLE public.workouts
    ADD COLUMN IF NOT EXISTS power_provenance text;

ALTER TABLE public.workouts
    DROP CONSTRAINT IF EXISTS workouts_power_provenance_check;

ALTER TABLE public.workouts
    ADD CONSTRAINT workouts_power_provenance_check
    CHECK (power_provenance IS NULL
           OR power_provenance IN ('Measured', 'Modelled', 'Mixed', 'Unknown'));

CREATE INDEX IF NOT EXISTS workouts_class_leaderboard_idx
    ON public.workouts (class_id, power_provenance, total_output_kj DESC);


-- 2. THE BOARD ---------------------------------------------------------------
--
-- Every registered rider's best measured effort at one class. Five columns: a
-- board and nothing else.
--
-- `SECURITY DEFINER` because it reads rows the caller's own policies forbid,
-- which is exactly why it is written this narrowly, and `search_path` is pinned
-- for the usual reason.
--
-- It takes no "whose board?" argument. `auth.uid()` comes from the session, so
-- there is no way to ask this for somebody else — and requiring a session at
-- all is what keeps the anon key from reading the household's numbers.

CREATE OR REPLACE FUNCTION public.class_leaderboard(p_class_id text)
RETURNS TABLE (
    account_id uuid,
    name       text,
    output_kj  double precision,
    weight_kg  double precision,
    is_you     boolean
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    WITH best AS (
        -- One row per rider: their **best** effort, which is what a leaderboard
        -- means. `DISTINCT ON` needs its ORDER BY to agree, and `id` is the
        -- tiebreak so the answer is stable rather than whichever row the
        -- planner happened to reach first.
        SELECT DISTINCT ON (w.user_id) w.user_id, w.total_output_kj
        FROM public.workouts w
        WHERE w.class_id = p_class_id
          AND w.user_id IS NOT NULL
          AND w.power_provenance = 'Measured'
        ORDER BY w.user_id, w.total_output_kj DESC, w.id
    )
    SELECT p.id, p.name, best.total_output_kj, p.weight_kg, p.id = auth.uid()
    FROM best
    JOIN public.profiles p ON p.id = best.user_id
    -- No session, no board. A `SECURITY DEFINER` function granted to
    -- `authenticated` cannot normally be reached without one, but saying it
    -- here means the rule survives somebody widening the grant later.
    WHERE auth.uid() IS NOT NULL;
$$;

REVOKE ALL ON FUNCTION public.class_leaderboard(text) FROM public;
GRANT EXECUTE ON FUNCTION public.class_leaderboard(text) TO authenticated;


-- 3. THE GHOST ---------------------------------------------------------------
--
-- The owner asked for ghosts as well as boards: another rider's trace drawn
-- behind your own, which 24.3.1 already does for a housemate off a Room query.
-- This is the same thing for a rider on another bike.
--
-- It returns **one ride's series** rather than a rider's history: you name the
-- account and the class, and you get their best measured effort at it. Not
-- their other classes, not their dates, not their heart rate — the payload's
-- `hr` column is stripped, because a leaderboard's worth of visibility was what
-- was agreed and a resting heart rate is a medical-shaped fact rather than a
-- sporting one.

CREATE OR REPLACE FUNCTION public.class_ghost(p_class_id text, p_account_id uuid)
RETURNS TABLE (
    account_id uuid,
    name       text,
    output_kj  double precision,
    metrics    jsonb
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT p.id, p.name, w.total_output_kj, (w.metrics_payload - 'hr')
    FROM public.workouts w
    JOIN public.profiles p ON p.id = w.user_id
    WHERE w.class_id = p_class_id
      AND w.user_id = p_account_id
      AND w.power_provenance = 'Measured'
      AND auth.uid() IS NOT NULL
    ORDER BY w.total_output_kj DESC, w.id
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION public.class_ghost(text, uuid) FROM public;
GRANT EXECUTE ON FUNCTION public.class_ghost(text, uuid) TO authenticated;

COMMIT;


-- WHAT ANY REGISTERED RIDER CAN NOW SEE, STATED PLAINLY ----------------------
--
-- Of every other registered rider: their display name, their weight, their best
-- total output at a named class, and — if they ask for it by account id — the
-- cadence, resistance and power series of that one ride.
--
-- What is still not reachable by anybody:
--
-- * **A `workouts` or `profiles` row.** Both policies are unchanged and still
--   "your own rows and nobody else's", so email addresses, ride dates, RPE
--   ratings and every column added in future are not exposed by this change.
-- * **Heart rate.** Stripped from the ghost payload above, deliberately.
-- * **Anything at all without a session.** The anon key reaches neither
--   function, so publishing it with the web app publishes no rider's numbers.
--
-- And the check that matters, because 15.5.4 is the rule here too: point a
-- **second account** at both functions rather than reading this file.
