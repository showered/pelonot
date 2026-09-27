-- 15.3.7: a new tablet can restore the inputs behind heart-rate zones and
-- the rider's original FTP estimate. Only the birth year leaves the tablet.
-- Run after 010. Existing profiles keep null for answers never given.
BEGIN;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS birth_year integer,
    ADD COLUMN IF NOT EXISTS fitness_level text;

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_birth_year_check
    CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 2100);

ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_fitness_level_check
    CHECK (fitness_level IS NULL OR fitness_level IN
        ('new_to_this', 'occasional', 'regular'));

-- 008 allowed 100..240, while the bike rejects numbers outside 120..230.
-- The project has no existing non-null maximum; align the endpoint with the
-- input the bike can actually use rather than restoring a silent non-zone.
ALTER TABLE public.profiles DROP CONSTRAINT profiles_max_hr_check;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_max_hr_check
    CHECK (max_hr_bpm IS NULL OR max_hr_bpm BETWEEN 120 AND 230);

COMMIT;
