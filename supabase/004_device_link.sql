-- Pelonot migration 004 — SIGNING IN BY SCANNING A CODE
--
-- PLAN 15.6. Run after 003.
--
--
-- WHY THIS EXISTS
-- ===============
--
-- The bike's tablet is the worst keyboard in the house: 1280 dp of glass at
-- arm's length, no password manager, an on-screen keyboard that eats half the
-- screen, and a shared room. A phone is the opposite of all four. So the bike
-- shows a code, the rider scans it, they sign in on the device built for
-- signing in, and the bike is handed a session.
--
-- Supabase has no device authorization grant, so this builds one out of three
-- things it does have: a table with row-level security, SECURITY DEFINER
-- functions (the standard way to let an unauthenticated caller touch exactly
-- one row of a locked table), and — for the good version of the hand-off — the
-- admin API's ability to mint a one-time OTP for a user who is already
-- authenticated somewhere else.
--
--
-- THE THING THIS MUST NOT DO
-- ==========================
--
-- The naive version is the phone handing the bike its own refresh token. This
-- project has refresh-token rotation ON (with a 10-second reuse interval), so
-- two devices sharing one token family means the first to refresh invalidates
-- the other — and a detected reuse can revoke the family and sign out BOTH.
--
-- So the bike ends up with a session of its own. `device_link.payload` carries
-- whichever hand-off the deployment supports (see the `kind` field below), and
-- the app understands both, so the weaker fallback needs no schema change.
--
--
-- THE SECURITY MODEL, IN FOUR LINES
-- =================================
--
-- * The code is NOT the credential. The bike invents a 32-byte device secret,
--   sends only its SHA-256, and displays the code. So a code photographed off
--   the bike's screen from across the room cannot collect the session — that
--   needs the secret, which never leaves the tablet.
-- * The row lives five minutes and is deleted the moment it is collected.
-- * The table has RLS on and NOT ONE POLICY. No role can read or write it
--   directly; every access is a function call. A leaked anon key cannot
--   enumerate pending pairings.
-- * Nothing in an unclaimed row identifies a rider. Until somebody claims it,
--   it is a random string and a device label.


BEGIN;

CREATE TABLE IF NOT EXISTS public.device_link (
    -- Short, unambiguous and typeable: the QR is the fast path, not the only
    -- one. A rider whose camera app will not scan gets to type eight
    -- characters instead of being stuck.
    code        text PRIMARY KEY,

    -- SHA-256 of the device secret, hex. Never the secret itself: a table that
    -- held it would turn a database leak into a session handover.
    secret_hash text        NOT NULL,

    -- What the bike calls itself, shown to the rider on their phone before
    -- they commit (15.6.5). A pairing flow that does not name the device being
    -- paired is a phishing primitive — the QR is a URL and anyone can print a
    -- URL.
    label       text        NOT NULL,

    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,

    claimed_at  timestamptz,
    claimed_by  uuid REFERENCES auth.users(id) ON DELETE CASCADE,

    -- The hand-off itself, written only at the moment of claiming:
    --   {"kind":"otp",     "email":"…", "otp":"123456"}   preferred
    --   {"kind":"refresh", "token":"…"}                    fallback (15.6.9)
    payload     jsonb
);

CREATE INDEX IF NOT EXISTS device_link_expires_idx ON public.device_link (expires_at);

-- RLS on, and deliberately no policy of any kind. Under RLS that means no role
-- but the service role can touch this table, which is exactly right: the three
-- functions below are the only doors and each one is narrower than a policy
-- could be.
ALTER TABLE public.device_link ENABLE ROW LEVEL SECURITY;


-- 1. THE BIKE ASKS FOR A CODE -------------------------------------------------

CREATE OR REPLACE FUNCTION public.device_link_begin(
    p_secret_hash text,
    p_label       text
) RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
-- Pinned, and this is not decoration: a SECURITY DEFINER function without a
-- fixed search_path is the classic Postgres privilege-escalation footgun — a
-- caller who can create objects can shadow a name this body resolves.
SET search_path = public, pg_temp
AS $$
DECLARE
    v_alphabet CONSTANT text := '23456789ABCDEFGHJKMNPQRSTVWXYZ';
    v_code     text := '';
    v_ttl      CONSTANT interval := interval '5 minutes';
    i          int;
BEGIN
    IF p_secret_hash IS NULL OR length(p_secret_hash) <> 64 THEN
        RAISE EXCEPTION 'a device secret hash is required';
    END IF;

    -- 15.6.10. This is the one table in the project an unauthenticated stranger
    -- can cause a write to, so it tidies up after itself on every call and
    -- refuses to grow without bound.
    DELETE FROM public.device_link WHERE expires_at < now();

    IF (SELECT count(*) FROM public.device_link WHERE claimed_at IS NULL) > 200 THEN
        RAISE EXCEPTION 'too many pairings are already in flight; try again shortly';
    END IF;

    -- No I, L, O, U, 0 or 1 — the characters a rider reads off a screen and
    -- types wrongly. 30^8 is about 6.5e11, against a five-minute window.
    FOR i IN 1..8 LOOP
        v_code := v_code || substr(
            v_alphabet,
            1 + floor(random() * length(v_alphabet))::int,
            1
        );
    END LOOP;

    INSERT INTO public.device_link (code, secret_hash, label, expires_at)
    VALUES (v_code, p_secret_hash, coalesce(nullif(p_label, ''), 'a Pelonot bike'),
            now() + v_ttl);

    RETURN json_build_object('code', v_code, 'expires_at', now() + v_ttl);
END;
$$;


-- 2. THE PHONE ASKS WHAT IT IS ABOUT TO SIGN INTO -----------------------------
--
-- Read-only, and returns the label and nothing else. It deliberately does not
-- say whether a code has already been claimed by somebody else — that is not
-- the phone's business and it would make this an oracle for guessing codes.

CREATE OR REPLACE FUNCTION public.device_link_describe(p_code text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_label      text;
    v_expires_at timestamptz;
BEGIN
    SELECT label, expires_at INTO v_label, v_expires_at
    FROM public.device_link
    WHERE code = upper(p_code)
      AND expires_at > now()
      AND claimed_at IS NULL;

    IF NOT FOUND THEN
        RETURN json_build_object('status', 'expired');
    END IF;

    RETURN json_build_object('status', 'waiting', 'label', v_label,
                             'expires_at', v_expires_at);
END;
$$;


-- 3. THE BIKE COLLECTS THE HAND-OFF -------------------------------------------
--
-- Single use: the row is deleted the moment it is read. A hand-off that could
-- be collected twice is a hand-off that can be collected by somebody else.

CREATE OR REPLACE FUNCTION public.device_link_poll(
    p_code   text,
    p_secret text
) RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_row public.device_link%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM public.device_link WHERE code = upper(p_code);

    IF NOT FOUND OR v_row.expires_at < now() THEN
        RETURN json_build_object('status', 'expired');
    END IF;

    -- A wrong secret is answered 'pending', not 'denied'. Saying "denied" would
    -- confirm that this code exists and is claimed, which is precisely the
    -- question an attacker holding a photographed code wants answered. The real
    -- device generated the secret and always has it, so it never sees this.
    IF encode(sha256(convert_to(p_secret, 'UTF8')), 'hex') <> v_row.secret_hash THEN
        RETURN json_build_object('status', 'pending');
    END IF;

    IF v_row.claimed_at IS NULL OR v_row.payload IS NULL THEN
        RETURN json_build_object('status', 'pending');
    END IF;

    DELETE FROM public.device_link WHERE code = v_row.code;

    RETURN json_build_object('status', 'linked', 'payload', v_row.payload);
END;
$$;


-- 4. THE PHONE HANDS OVER -----------------------------------------------------
--
-- The fallback path (15.6.9), usable with no Edge Function deployed. The phone
-- writes a hand-off it can produce on its own, and the ONLY such thing that
-- does not break rotation is a refresh token it is prepared to stop using
-- itself — so the page says so, plainly, and signs itself out afterwards.
--
-- The preferred path does not use this function at all: the Edge Function in
-- `supabase/functions/link-device` verifies the caller and writes an
-- {"kind":"otp"} payload with the service role, which gives the bike a session
-- of its own and leaves the phone's alone.

CREATE OR REPLACE FUNCTION public.device_link_claim(
    p_code    text,
    p_payload jsonb
) RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_updated int;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'sign in before linking a bike';
    END IF;

    UPDATE public.device_link
    SET claimed_at = now(),
        claimed_by = auth.uid(),
        payload    = p_payload
    WHERE code = upper(p_code)
      AND expires_at > now()
      AND claimed_at IS NULL;

    GET DIAGNOSTICS v_updated = ROW_COUNT;

    IF v_updated = 0 THEN
        RETURN json_build_object('status', 'expired');
    END IF;

    RETURN json_build_object('status', 'linked');
END;
$$;


-- 5. WHO MAY CALL WHAT --------------------------------------------------------
--
-- The grants are the whole access-control story here, because the table itself
-- is reachable by nobody. `begin` and `poll` are the bike's, which is
-- unauthenticated by definition — it is trying to become authenticated.
-- `describe` and `claim` require a session, which is the point of them.

REVOKE ALL ON FUNCTION public.device_link_begin(text, text)    FROM public;
REVOKE ALL ON FUNCTION public.device_link_poll(text, text)     FROM public;
REVOKE ALL ON FUNCTION public.device_link_describe(text)       FROM public;
REVOKE ALL ON FUNCTION public.device_link_claim(text, jsonb)   FROM public;

GRANT EXECUTE ON FUNCTION public.device_link_begin(text, text) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.device_link_poll(text, text)  TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.device_link_claim(text, jsonb) TO authenticated;

-- `describe` is granted to `anon` as well, and that is a decision rather than
-- an oversight.
--
-- The first version had it authenticated-only, which reads as the safer choice
-- and is the wrong one, because of *when* the phone needs the answer: it opens
-- the pairing page having just scanned a code, and it must be told which device
-- it is about to sign in **before** it asks anybody for a password. A rider who
-- has to authenticate first in order to find out what they are authenticating
-- into has already given up the protection 15.6.5 exists for — naming the
-- device is the anti-phishing measure, and a measure that only fires after the
-- password is typed is not one.
--
-- What it exposes is bounded by the fact that you cannot ask without the code:
-- one device label, for one unclaimed pairing, for five minutes. It says
-- nothing about who is pairing, because until somebody claims it there is
-- nobody — and a claimed code returns 'expired' to this function exactly like
-- an unknown one, so it is not an oracle for what has been claimed.

GRANT EXECUTE ON FUNCTION public.device_link_describe(text) TO anon, authenticated;

COMMIT;


-- WHAT THIS FILE DELIBERATELY DOES NOT DO -------------------------------------
--
-- * It does not let anyone read `device_link`. Not `anon`, not `authenticated`,
--   not the rider who created the row. Every question anybody is allowed to ask
--   about a pairing is one of the four functions above, and each returns the
--   least it can.
--
-- * It does not keep a record of pairings. A completed hand-off deletes its own
--   row, so there is no table of which accounts signed in on which bikes and
--   when. That is a log nobody asked for and it would be the most sensitive
--   thing in the schema.
--
-- * It does not verify itself. Like `003`, the check that matters is two real
--   devices and a bad actor's-eye view: a code with no secret must collect
--   nothing (15.6.2), and a second poll of a collected code must return
--   nothing.
