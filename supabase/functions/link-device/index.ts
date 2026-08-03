// Pelonot Edge Function — link-device (PLAN 15.6.4)
//
// The phone calls this with its own access token and a pairing code. It gives
// the BIKE a session of its own rather than a copy of the phone's, and that is
// the whole reason it exists.
//
// Why not just pass the phone's refresh token across? Because this project has
// refresh-token rotation on: two devices in one token family means the first to
// refresh invalidates the other, and a detected reuse can revoke the family and
// sign out both. So the bike needs an independent session, and the only way to
// mint one for a user who is already authenticated elsewhere is the admin API.
//
// What that costs is the service-role key, which is why this is a function on
// the server rather than a call from either device. The key reaches neither
// the phone nor the bike; the phone sends a JWT it already has, and the bike
// collects a one-time OTP that is useless after one use and after five minutes.
//
// Deploy:
//   supabase functions deploy link-device --project-ref <ref>
//
// It needs no secrets configured: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY
// are injected into every function by the platform.
//
// If it cannot be deployed, the app still works — `link.html` falls back to
// PLAN 15.6.9, which uses only SQL and is honest about its cost.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.4';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, 'Content-Type': 'application/json' },
  });

Deno.serve(async (request: Request) => {
  if (request.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (request.method !== 'POST') return json({ error: 'POST only' }, 405);

  const url = Deno.env.get('SUPABASE_URL')!;
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

  const bearer = request.headers.get('Authorization') ?? '';
  const token = bearer.replace(/^Bearer\s+/i, '');
  if (!token) return json({ error: 'sign in first' }, 401);

  let code: string;
  try {
    ({ code } = await request.json());
  } catch {
    return json({ error: 'expected {code}' }, 400);
  }
  if (!code) return json({ error: 'expected {code}' }, 400);

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // The caller's own token decides who this is. Never a user id from the
  // request body: that would let anybody pair any account they knew the id of.
  const { data: caller, error: whoError } = await admin.auth.getUser(token);
  if (whoError || !caller?.user?.email) {
    return json({ error: 'that session is not valid' }, 401);
  }

  // Claim the pairing FIRST. If the code is expired or already taken, no OTP is
  // minted at all — minting one and then failing to store it would leave a live
  // credential for the rider's account lying in a log for an hour.
  const { data: pairing, error: pairingError } = await admin
    .from('device_link')
    .select('code, label')
    .eq('code', code.toUpperCase())
    .gt('expires_at', new Date().toISOString())
    .is('claimed_at', null)
    .maybeSingle();

  if (pairingError) return json({ error: pairingError.message }, 500);
  if (!pairing) return json({ status: 'expired' }, 410);

  // A magic-link generation returns `email_otp` — a short code the bike can
  // verify with the anon key, giving it a fresh session and its own refresh
  // token family. Nothing is emailed: `generateLink` mints, it does not send.
  const { data: link, error: linkError } = await admin.auth.admin.generateLink({
    type: 'magiclink',
    email: caller.user.email,
  });

  if (linkError || !link?.properties?.email_otp) {
    return json({ error: linkError?.message ?? 'could not mint a sign-in code' }, 500);
  }

  const { error: writeError } = await admin
    .from('device_link')
    .update({
      claimed_at: new Date().toISOString(),
      claimed_by: caller.user.id,
      payload: {
        kind: 'otp',
        email: caller.user.email,
        otp: link.properties.email_otp,
      },
    })
    .eq('code', pairing.code)
    .is('claimed_at', null);

  if (writeError) return json({ error: writeError.message }, 500);

  return json({ status: 'linked', label: pairing.label });
});
