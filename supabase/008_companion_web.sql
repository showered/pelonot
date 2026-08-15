-- Pelonot migration 008 — WHAT THE COMPANION WEB APP NEEDS
--
-- PLAN 17.4, 17.6, 17.7, 17.10, 17.11, 18.2, 18.3. Run after 007.
--
--
-- WHY THERE IS A MIGRATION AT ALL
-- ==============================
--
-- Phase 17's remaining items are almost all "the web app should let a rider do
-- X", and three of them need a column the cloud does not have: a bio (17.4), a
-- ride's title (the preamble — the bike's tablet is a bad place to type, so
-- anything needing a keyboard belongs here), and a way to say what other people
-- may see (17.7). The rest — the feed, kudos, a comment — need two small tables
-- and the same shape 007 chose: narrow `SECURITY DEFINER` functions rather than
-- relaxed policies.
--
-- **The policies on `profiles` and `workouts` are unchanged and stay unchanged.**
-- Everything another rider can see is the columns a function names, and nothing
-- a table grows afterwards. That is 007's decision and this file inherits it
-- rather than re-taking it.
--
--
-- 17.7 AND THE OWNER'S DECISION OF 4 AUGUST, RECONCILED
-- =====================================================
--
-- These look like they disagree and do not:
--
--   * 17.7 says **private by default** — nothing visible to anyone until the
--     rider opts in.
--   * The owner said everyone registered should see everyone's *scores*, which
--     007 built, and separately declined to close public sign-up (18.11.1):
--     *"Leave on public signup ... let's assume there's only valid signups."*
--
-- 18.11.1 states the accepted blast radius exactly: a stranger who registers
-- sees **display names, class ids, durations, output** — "not ride dates, not
-- RPE, not heart rate, not anyone's rows". An activity feed is ride dates by
-- construction. So a feed switched on for everybody would quietly widen a risk
-- the owner sized and accepted, without anybody deciding to.
--
-- Hence `profiles.share_activity`, **default false**. The leaderboard is
-- untouched and still shows everyone (the owner's decision); the feed shows
-- nobody until a rider turns their own sharing on (17.7's rule), and turning it
-- on is a sentence on one screen rather than a policy change. Nothing about the
-- exposure of the project changes on the day this migration runs, which is the
-- property a migration adding a social feature should have.
--
-- `workouts.hidden` is the per-ride half of the same control, and it is
-- deliberately *stronger* than the profile switch: a hidden ride leaves the
-- leaderboard and the ghost as well as the feed. A rider hiding one ride means
-- that ride, everywhere, not that ride in one of the three places it appears.


BEGIN;

-- 1. THE PROFILE COLUMNS THE WEB APP OWNS ------------------------------------
--
-- All four are nullable-or-defaulted and none is required by the bike. The
-- Android upsert sends exactly `id`, `name`, `ftp_watts` and `weight_kg`
-- (`ProfileDto`), and PostgREST's merge-duplicates upsert only writes the
-- columns in the payload — so a rider editing their bio on the web does not
-- lose it the next time the bike syncs. That is a property worth stating,
-- because the opposite is this project's most repeated defect (the
-- read-modify-write in 7.9, and 8.3d's finalise).

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS bio text;

ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_bio_length_check;

-- A cap, because the field is free text on a page anybody registered can read
-- and "no limit" is a decision too. 280 is a sentence or two about your riding.
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_bio_length_check
    CHECK (bio IS NULL OR length(bio) <= 280);

-- Units are the rider's preference and belong to the rider, not to the device
-- (PLAN 13). The bike does not read this yet; the web app does, and storing it
-- here rather than in `localStorage` is what makes it the same preference on a
-- phone and on a laptop.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS units text NOT NULL DEFAULT 'metric';

ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_units_check;

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_units_check
    CHECK (units IN ('metric', 'imperial'));

-- The maximum heart rate, so heart-rate zones can be drawn off the rider's own
-- number rather than an age formula (21.1.3, and 21.4.2's rule that any chart
-- showing a heart rate shows its zones). This is also the cloud half of 15.3.7
-- — a rider restoring onto a new tablet currently loses their zones, because
-- nothing carries this. Adding the column does not fix that on its own; it is
-- the thing the fix needs.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS max_hr_bpm integer;

ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_max_hr_check;

-- Bounds rather than clamping, the same rule the telemetry fence uses: an
-- impossible number is rejected, never quietly turned into a plausible one.
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_max_hr_check
    CHECK (max_hr_bpm IS NULL OR max_hr_bpm BETWEEN 100 AND 240);

-- The feed's gate — see the header. False by default, on purpose.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS share_activity boolean NOT NULL DEFAULT false;


-- 2. THE RIDE COLUMNS --------------------------------------------------------
--
-- A ride's own name. The bike has no keyboard worth the name and the web app
-- does; this is the first thing Phase 17's preamble asks for by name.
ALTER TABLE public.workouts
    ADD COLUMN IF NOT EXISTS title text;

ALTER TABLE public.workouts
    DROP CONSTRAINT IF EXISTS workouts_title_length_check;

ALTER TABLE public.workouts
    ADD CONSTRAINT workouts_title_length_check
    CHECK (title IS NULL OR length(title) <= 80);

-- Per-ride, and it hides the ride everywhere (see the header).
ALTER TABLE public.workouts
    ADD COLUMN IF NOT EXISTS hidden boolean NOT NULL DEFAULT false;


-- 3. KUDOS -------------------------------------------------------------------
--
-- 18.3: *"kudos, and nothing that requires typing during or just after a
-- ride"*. One row per rider per ride, which is what the primary key says and is
-- the whole of the deduplication — a double tap is the same row twice and the
-- insert says `ON CONFLICT DO NOTHING`.
--
-- RLS is on and **there is no policy**, deliberately: every read and write goes
-- through the functions below, exactly as `device_link` does in 004. A table
-- with RLS enabled and no policy is reachable by nobody except the definer.

CREATE TABLE IF NOT EXISTS public.kudos (
    workout_id uuid NOT NULL REFERENCES public.workouts(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workout_id, account_id)
);

ALTER TABLE public.kudos ENABLE ROW LEVEL SECURITY;

-- 005's lesson: Supabase's default privileges hand `anon` a full set of grants
-- on every new table in `public`, and no migration here ever asked for them.
REVOKE ALL ON TABLE public.kudos FROM anon, authenticated, public;


-- 4. ONE COMMENT -------------------------------------------------------------
--
-- 17.6 asks for "a light activity feed: friends' recent rides, kudos, a
-- comment. Deliberately not a full social network." So: a body, an author, a
-- ride, and nothing else — no threads, no replies, no mentions, no edit.
--
-- The moderation floor that actually matters at this scale is that **the rider
-- whose ride it is can delete any comment on it**, as well as the author
-- deleting their own. 18.8 asks for mute, block and report from the first
-- version that has a feed; with sign-up open (18.11.1) that argument is live,
-- and what is built here is the delete rather than the graph — the same
-- reasoning the owner used to throw out the friend graph in 007. If this
-- project ever has more than the four riders 18.11 was written for, 18.8 is the
-- item to build, not this comment to remove.

CREATE TABLE IF NOT EXISTS public.ride_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_id uuid NOT NULL REFERENCES public.workouts(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ride_comments_body_check CHECK (length(btrim(body)) BETWEEN 1 AND 280)
);

CREATE INDEX IF NOT EXISTS ride_comments_workout_idx
    ON public.ride_comments (workout_id, created_at);

ALTER TABLE public.ride_comments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.ride_comments FROM anon, authenticated, public;


-- 5. WHAT COUNTS AS SHARED ---------------------------------------------------
--
-- One definition, used by every function below, because the alternative is the
-- shape this project has been bitten by twice: seven queries each spelling out
-- the same predicate until one of them gets it half right (22.1.7). It is
-- `STABLE` and inlinable, so it costs nothing to call it from a join.
--
-- Your own rides are always yours to see. Everybody else's need both switches:
-- the rider sharing at all, and the ride not being hidden.

CREATE OR REPLACE FUNCTION public.ride_is_visible(
    p_owner uuid,
    p_hidden boolean,
    p_shares boolean
) RETURNS boolean
LANGUAGE sql
-- STABLE and not IMMUTABLE, which matters: it reads `auth.uid()`, and that is
-- a setting on the request rather than a constant. Declaring it immutable
-- invites the planner to fold it away and answer one rider's question with
-- another rider's answer.
STABLE
AS $$
    SELECT p_owner = auth.uid()
        OR (COALESCE(p_shares, false) AND NOT COALESCE(p_hidden, false));
$$;

-- A function's default ACL is EXECUTE to PUBLIC, which is the same default 005
-- had to take back on tables. It is harmless here — the answer depends only on
-- the arguments and the caller's own session — but "harmless by inspection" is
-- what the catalogue is for reading rather than reasoning about.
REVOKE ALL ON FUNCTION public.ride_is_visible(uuid, boolean, boolean) FROM public;
GRANT EXECUTE ON FUNCTION public.ride_is_visible(uuid, boolean, boolean) TO authenticated;


-- 6. THE FEED ----------------------------------------------------------------
--
-- 17.6, 18.2. Everybody's recent rides, newest first, with the two social
-- counts beside each one so the page is one request rather than one per row.
--
-- What it deliberately does not return: heart rate (007 stripped it from the
-- ghost for the same reason — a resting heart rate is a medical-shaped fact
-- rather than a sporting one), RPE, distance in any private sense, and the
-- sample series. A feed is a list of things that happened, not a window onto
-- somebody's rows.

CREATE OR REPLACE FUNCTION public.activity_feed(p_limit integer DEFAULT 40)
RETURNS TABLE (
    workout_id       uuid,
    account_id       uuid,
    name             text,
    is_you           boolean,
    title            text,
    class_id         text,
    class_title      text,
    recorded_at      timestamptz,
    duration_sec     integer,
    output_kj        double precision,
    distance_km      double precision,
    avg_power        double precision,
    power_provenance text,
    kudos_count      bigint,
    you_gave_kudos   boolean,
    comment_count    bigint
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        w.id,
        p.id,
        p.name,
        p.id = auth.uid(),
        w.title,
        w.class_id,
        c.title,
        w.recorded_at,
        w.duration_sec,
        w.total_output_kj,
        w.total_distance_km,
        w.avg_power,
        w.power_provenance,
        (SELECT count(*) FROM public.kudos k WHERE k.workout_id = w.id),
        EXISTS (SELECT 1 FROM public.kudos k
                 WHERE k.workout_id = w.id AND k.account_id = auth.uid()),
        (SELECT count(*) FROM public.ride_comments rc WHERE rc.workout_id = w.id)
    FROM public.workouts w
    JOIN public.profiles p ON p.id = w.user_id
    LEFT JOIN public.class_templates c ON c.id = w.class_id
    WHERE auth.uid() IS NOT NULL
      AND public.ride_is_visible(w.user_id, w.hidden, p.share_activity)
    ORDER BY w.recorded_at DESC
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 40), 1), 200);
$$;

REVOKE ALL ON FUNCTION public.activity_feed(integer) FROM public;
GRANT EXECUTE ON FUNCTION public.activity_feed(integer) TO authenticated;


-- 7. WHO ELSE IS HERE --------------------------------------------------------
--
-- 17.10 is a copy problem with a data-model cause: *the web app never implies a
-- household member is missing data when they have simply never signed in*. The
-- page cannot say that honestly without knowing who it can see, and this is
-- that list — everybody with an account, which by rule 2 of the connectivity
-- model is everybody the cloud has ever heard of.
--
-- `rides` and `last_ride` are only ever the *shared* ones, so a rider who has
-- not turned sharing on appears as a name and a bio with no numbers, which is
-- the honest rendering of somebody who is here and has not published anything.

CREATE OR REPLACE FUNCTION public.rider_directory()
RETURNS TABLE (
    account_id     uuid,
    name           text,
    bio            text,
    is_you         boolean,
    share_activity boolean,
    rides          bigint,
    last_ride      timestamptz,
    output_kj      double precision
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        p.id,
        p.name,
        p.bio,
        p.id = auth.uid(),
        p.share_activity,
        count(w.id),
        max(w.recorded_at),
        COALESCE(sum(w.total_output_kj), 0)::double precision
    FROM public.profiles p
    LEFT JOIN public.workouts w
           ON w.user_id = p.id
          AND public.ride_is_visible(w.user_id, w.hidden, p.share_activity)
    WHERE auth.uid() IS NOT NULL
    GROUP BY p.id, p.name, p.bio, p.share_activity
    ORDER BY (p.id = auth.uid()) DESC, p.name;
$$;

REVOKE ALL ON FUNCTION public.rider_directory() FROM public;
GRANT EXECUTE ON FUNCTION public.rider_directory() TO authenticated;


-- 8. WHICH CLASSES HAVE A BOARD ----------------------------------------------
--
-- `class_leaderboard` takes a class id and the web app has to get one from
-- somewhere. The class library is public and 72 rows long, so offering all of
-- it would be a picker where 60 entries answer "nobody has ridden this".
-- This returns the ones with a ranked ride on them, which is the same filter
-- the board itself applies — measured rather than guessed at, since the rule
-- (`power_provenance = 'Measured'`) rules out every simulated ride.

CREATE OR REPLACE FUNCTION public.leaderboard_classes()
RETURNS TABLE (
    class_id  text,
    title     text,
    category  text,
    riders    bigint,
    best_kj   double precision
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        w.class_id,
        COALESCE(c.title, w.class_id),
        COALESCE(c.category, ''),
        count(DISTINCT w.user_id),
        max(w.total_output_kj)::double precision
    FROM public.workouts w
    LEFT JOIN public.class_templates c ON c.id = w.class_id
    WHERE auth.uid() IS NOT NULL
      AND w.class_id IS NOT NULL
      AND w.user_id IS NOT NULL
      AND NOT w.hidden
      AND w.power_provenance = 'Measured'
    GROUP BY w.class_id, c.title, c.category
    ORDER BY count(DISTINCT w.user_id) DESC, max(w.total_output_kj) DESC;
$$;

REVOKE ALL ON FUNCTION public.leaderboard_classes() FROM public;
GRANT EXECUTE ON FUNCTION public.leaderboard_classes() TO authenticated;


-- 9. GIVING AND TAKING BACK KUDOS --------------------------------------------
--
-- Both return the new count, so the page never has to ask a second question to
-- redraw — and never has to guess, which is how two surfaces come to disagree.
--
-- The visibility check is inside the function rather than at the call site: a
-- ride you cannot see is a ride you cannot congratulate, and putting that in
-- the caller would make it a property of the page rather than of the endpoint.

CREATE OR REPLACE FUNCTION public.give_kudos(p_workout uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    visible boolean;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not signed in';
    END IF;

    SELECT public.ride_is_visible(w.user_id, w.hidden, p.share_activity)
      INTO visible
      FROM public.workouts w
      JOIN public.profiles p ON p.id = w.user_id
     WHERE w.id = p_workout;

    IF visible IS NOT TRUE THEN
        RAISE EXCEPTION 'no such ride';
    END IF;

    INSERT INTO public.kudos (workout_id, account_id)
    VALUES (p_workout, auth.uid())
    ON CONFLICT DO NOTHING;

    RETURN (SELECT count(*) FROM public.kudos k WHERE k.workout_id = p_workout);
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_kudos(p_workout uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not signed in';
    END IF;

    DELETE FROM public.kudos
     WHERE workout_id = p_workout AND account_id = auth.uid();

    RETURN (SELECT count(*) FROM public.kudos k WHERE k.workout_id = p_workout);
END;
$$;

REVOKE ALL ON FUNCTION public.give_kudos(uuid)   FROM public;
REVOKE ALL ON FUNCTION public.remove_kudos(uuid) FROM public;
GRANT EXECUTE ON FUNCTION public.give_kudos(uuid)   TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_kudos(uuid) TO authenticated;


-- 10. COMMENTS ---------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.ride_comments_for(p_workout uuid)
RETURNS TABLE (
    id         uuid,
    account_id uuid,
    name       text,
    is_you     boolean,
    body       text,
    created_at timestamptz,
    can_delete boolean
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        rc.id,
        rc.account_id,
        author.name,
        rc.account_id = auth.uid(),
        rc.body,
        rc.created_at,
        -- The author, or the rider whose ride it is. That second half is the
        -- moderation floor: it is your ride, so the last word on what is
        -- written under it is yours.
        rc.account_id = auth.uid() OR w.user_id = auth.uid()
    FROM public.ride_comments rc
    JOIN public.workouts w      ON w.id = rc.workout_id
    JOIN public.profiles owner  ON owner.id = w.user_id
    JOIN public.profiles author ON author.id = rc.account_id
    WHERE rc.workout_id = p_workout
      AND auth.uid() IS NOT NULL
      AND public.ride_is_visible(w.user_id, w.hidden, owner.share_activity)
    ORDER BY rc.created_at;
$$;

CREATE OR REPLACE FUNCTION public.add_ride_comment(p_workout uuid, p_body text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    visible boolean;
    new_id  uuid;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not signed in';
    END IF;

    SELECT public.ride_is_visible(w.user_id, w.hidden, p.share_activity)
      INTO visible
      FROM public.workouts w
      JOIN public.profiles p ON p.id = w.user_id
     WHERE w.id = p_workout;

    IF visible IS NOT TRUE THEN
        RAISE EXCEPTION 'no such ride';
    END IF;

    INSERT INTO public.ride_comments (workout_id, account_id, body)
    VALUES (p_workout, auth.uid(), btrim(p_body))
    RETURNING id INTO new_id;

    RETURN new_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_ride_comment(p_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    removed integer;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not signed in';
    END IF;

    DELETE FROM public.ride_comments rc
     USING public.workouts w
     WHERE rc.id = p_id
       AND w.id = rc.workout_id
       AND (rc.account_id = auth.uid() OR w.user_id = auth.uid());

    GET DIAGNOSTICS removed = ROW_COUNT;
    RETURN removed > 0;
END;
$$;

REVOKE ALL ON FUNCTION public.ride_comments_for(uuid)       FROM public;
REVOKE ALL ON FUNCTION public.add_ride_comment(uuid, text)  FROM public;
REVOKE ALL ON FUNCTION public.delete_ride_comment(uuid)     FROM public;
GRANT EXECUTE ON FUNCTION public.ride_comments_for(uuid)      TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_ride_comment(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_ride_comment(uuid)    TO authenticated;


-- 11. THE BOARD AND THE GHOST LEARN ABOUT `hidden` ---------------------------
--
-- Re-declared from 007 with one clause added each. Everything else is
-- byte-identical on purpose: this is the file 007's reader will diff against,
-- and a rewrite that also "tidied" something would make that diff a review.

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
        SELECT DISTINCT ON (w.user_id) w.user_id, w.total_output_kj
        FROM public.workouts w
        WHERE w.class_id = p_class_id
          AND w.user_id IS NOT NULL
          AND NOT w.hidden
          AND w.power_provenance = 'Measured'
        ORDER BY w.user_id, w.total_output_kj DESC, w.id
    )
    SELECT p.id, p.name, best.total_output_kj, p.weight_kg, p.id = auth.uid()
    FROM best
    JOIN public.profiles p ON p.id = best.user_id
    WHERE auth.uid() IS NOT NULL;
$$;

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
      AND NOT w.hidden
      AND w.power_provenance = 'Measured'
      AND auth.uid() IS NOT NULL
    ORDER BY w.total_output_kj DESC, w.id
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION public.class_leaderboard(text)     FROM public;
REVOKE ALL ON FUNCTION public.class_ghost(text, uuid)     FROM public;
GRANT EXECUTE ON FUNCTION public.class_leaderboard(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.class_ghost(text, uuid) TO authenticated;

COMMIT;


-- WHAT CHANGED FOR A RIDER WHO IS ALREADY HERE -------------------------------
--
-- Nothing, until they choose something. `share_activity` is false, so the feed
-- is empty for everybody on the day this runs; `hidden` is false, so every
-- board is exactly what it was; and the four new profile columns are null or
-- their defaults. The one thing a self-hoster should know is that the feed
-- looking empty is this working, not a broken query — the web app says so on
-- the page rather than leaving it to be discovered.
