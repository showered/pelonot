-- Pelonot migration 009 — ACTIVITY SHARING IS OPT-OUT
--
-- The owner changed the product decision on 23 September 2026: on this small
-- invited network, opening the bike should show that the other riders have
-- ridden without each person first discovering a web-only switch. The feed is
-- still the narrow 008 function: no heart rate, RPE, sample series, or direct
-- table access reaches another rider. `workouts.hidden` remains the stronger,
-- per-ride escape hatch.

BEGIN;

-- New cloud profiles share their ordinary rides unless they choose otherwise.
ALTER TABLE public.profiles
    ALTER COLUMN share_activity SET DEFAULT true;

-- Existing profiles were created under 008's opposite default. Leaving them
-- false would make an update silently preserve a behaviour the owner has just
-- rejected, so this migration deliberately changes those rows as well.
UPDATE public.profiles
SET share_activity = true
WHERE share_activity = false;

COMMIT;
