-- 18.13: live finish targets for the selected class and its authored length.
-- Run after 009. The static class leaderboard remains class-only.
-- Workout rows stay private; the result carries final totals, never samples.
BEGIN;

-- The live target needs every rider's best at a length, including people who
-- have not ridden the selected class. A final total is returned, never samples.
CREATE FUNCTION public.duration_finish_targets(p_class_id text)
RETURNS TABLE (
    account_id uuid,
    name       text,
    best_kj    double precision,
    class_best_kj double precision,
    is_you     boolean
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT p.id, p.name, MAX(w.total_output_kj)::double precision,
           MAX(w.total_output_kj) FILTER (WHERE w.class_id = p_class_id)::double precision,
           p.id = auth.uid()
    FROM public.workouts w
    JOIN public.class_templates c ON c.id = w.class_id
    JOIN public.class_templates selected ON selected.id = p_class_id
        AND selected.duration_sec = c.duration_sec
    JOIN public.profiles p ON p.id = w.user_id
    WHERE auth.uid() IS NOT NULL
      AND NOT w.hidden
      AND w.power_provenance = 'Measured'
    GROUP BY p.id, p.name;
$$;

REVOKE ALL ON FUNCTION public.duration_finish_targets(text) FROM public;
GRANT EXECUTE ON FUNCTION public.duration_finish_targets(text) TO authenticated;

COMMIT;
