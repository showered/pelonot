-- 18.13: the selected class and the best class of the same authored length.
-- Run after 009. The old return type must be dropped before adding a column.
-- The function remains the only cross-account view; workout rows stay private.
BEGIN;

DROP FUNCTION IF EXISTS public.class_leaderboard(text);

CREATE FUNCTION public.class_leaderboard(p_class_id text)
RETURNS TABLE (
    account_id       uuid,
    name             text,
    output_kj        double precision,
    duration_best_kj double precision,
    weight_kg        double precision,
    is_you           boolean
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    WITH target AS (
        SELECT duration_sec FROM public.class_templates WHERE id = p_class_id
    ), class_best AS (
        SELECT w.user_id, MAX(w.total_output_kj) AS output_kj
        FROM public.workouts w
        WHERE w.class_id = p_class_id
          AND w.user_id IS NOT NULL
          AND NOT w.hidden
          AND w.power_provenance = 'Measured'
        GROUP BY w.user_id
    ), duration_best AS (
        SELECT w.user_id, MAX(w.total_output_kj) AS duration_best_kj
        FROM public.workouts w
        JOIN public.class_templates c ON c.id = w.class_id
        JOIN target ON target.duration_sec = c.duration_sec
        WHERE w.user_id IS NOT NULL
          AND NOT w.hidden
          AND w.power_provenance = 'Measured'
        GROUP BY w.user_id
    )
    SELECT p.id, p.name, class_best.output_kj, duration_best.duration_best_kj,
           p.weight_kg, p.id = auth.uid()
    FROM class_best
    JOIN public.profiles p ON p.id = class_best.user_id
    LEFT JOIN duration_best ON duration_best.user_id = class_best.user_id
    WHERE auth.uid() IS NOT NULL;
$$;

REVOKE ALL ON FUNCTION public.class_leaderboard(text) FROM public;
GRANT EXECUTE ON FUNCTION public.class_leaderboard(text) TO authenticated;

-- The live target needs every rider's best at a length, including people who
-- have not ridden the selected class. A final total is returned, never samples.
CREATE FUNCTION public.duration_finish_targets(p_duration_sec integer)
RETURNS TABLE (
    account_id uuid,
    name       text,
    best_kj    double precision,
    is_you     boolean
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT p.id, p.name, MAX(w.total_output_kj)::double precision,
           p.id = auth.uid()
    FROM public.workouts w
    JOIN public.class_templates c ON c.id = w.class_id
    JOIN public.profiles p ON p.id = w.user_id
    WHERE auth.uid() IS NOT NULL
      AND c.duration_sec = p_duration_sec
      AND NOT w.hidden
      AND w.power_provenance = 'Measured'
    GROUP BY p.id, p.name;
$$;

REVOKE ALL ON FUNCTION public.duration_finish_targets(integer) FROM public;
GRANT EXECUTE ON FUNCTION public.duration_finish_targets(integer) TO authenticated;

COMMIT;
