/*
 * The pairing page (PLAN 15.6, 17.13).
 *
 * The only device this is ever opened on is a phone, held in one hand, having
 * just pointed a camera at a bike. Everything here is shaped by that: one
 * column, big targets, and the smallest possible number of decisions.
 *
 * The code arrives in the URL **fragment**, not the query string — a fragment
 * is never sent to the server and never lands in an access log. It is not a
 * credential (the bike keeps the secret), but a short-lived pairing code has no
 * business in somebody's web-server logs either.
 */

const el = (id) => document.getElementById(id);
const status = el('status');
const client = pelonotClient(status);

let code = (location.hash || '').replace(/^#/, '').trim().toUpperCase();
let mode = 'signin';

if (client) start();

async function start() {
  el('tab-signin').addEventListener('click', () => setMode('signin'));
  el('tab-signup').addEventListener('click', () => setMode('signup'));
  el('auth-form').addEventListener('submit', submit);
  el('code-go').addEventListener('click', () => {
    code = el('code').value.replace(/\s+/g, '').toUpperCase();
    if (code) describe();
  });
  el('confirm-go').addEventListener('click', hand0ver);
  el('not-me').addEventListener('click', () => {
    el('confirm').classList.add('hidden');
    status.textContent = 'Nothing was linked.';
  });

  client.auth.onAuthStateChange(() => route());

  if (code) {
    await describe();
  } else {
    el('device-card').classList.add('hidden');
    el('need-code').classList.remove('hidden');
  }
}

function setMode(next) {
  mode = next;
  el('tab-signin').setAttribute('aria-pressed', String(next === 'signin'));
  el('tab-signup').setAttribute('aria-pressed', String(next === 'signup'));
  el('auth-submit').textContent = next === 'signin' ? 'Sign in' : 'Create an account';
  el('password').setAttribute(
    'autocomplete',
    next === 'signin' ? 'current-password' : 'new-password'
  );
  el('auth-error').textContent = '';
}

/** What is asking, said before the rider commits to anything (15.6.5). */
async function describe() {
  el('need-code').classList.add('hidden');
  el('device-card').classList.remove('hidden');
  el('device-label').textContent = '…';
  el('device-code').textContent = code.replace(/(.{4})(.{4})/, '$1 $2');

  const { data, error } = await client.rpc('device_link_describe', { p_code: code });

  if (error || !data || data.status !== 'waiting') {
    el('device-label').textContent = 'That code has expired';
    el('device-code').textContent =
      'Codes last five minutes. Ask the bike for a new one.';
    return;
  }

  el('device-label').textContent = data.label;
  route();
}

async function route() {
  const { data } = await client.auth.getSession();
  const signedIn = Boolean(data.session);
  const known = el('device-label').textContent !== 'That code has expired';

  el('signed-out').classList.toggle('hidden', signedIn || !known);
  el('confirm').classList.toggle('hidden', !signedIn || !known);

  if (signedIn) {
    el('confirm-who').textContent = `Signed in as ${data.session.user.email}`;
  }
}

async function submit(event) {
  event.preventDefault();
  const email = el('email').value.trim().toLowerCase();
  const password = el('password').value;
  const error = el('auth-error');
  error.textContent = '';
  error.classList.add('error');
  el('auth-submit').disabled = true;

  try {
    if (mode === 'signin') {
      const { error: failure } = await client.auth.signInWithPassword({ email, password });
      if (failure) throw failure;
    } else {
      const { data, error: failure } = await client.auth.signUp({ email, password });
      if (failure) throw failure;
      if (!data.session) {
        error.classList.remove('error');
        error.textContent =
          `Check ${email} for a confirmation link, open it, then come back to ` +
          'this page and sign in.';
      }
    }
  } catch (failure) {
    error.textContent = failure.message || 'That did not work.';
  } finally {
    el('auth-submit').disabled = false;
  }
}

/**
 * The hand-off, preferred route first.
 *
 * The Edge Function mints the bike a session of its own; the SQL fallback hands
 * over this phone's refresh token, which works everywhere and costs the phone
 * its session (PLAN 15.6.9). The fallback is only reached when the function is
 * not deployed — a 404 — and never as a silent downgrade from an error that
 * might have meant something else.
 */
async function hand0ver() {
  el('confirm-go').disabled = true;
  status.classList.remove('error');
  status.textContent = 'Linking…';

  const { data } = await client.auth.getSession();
  const session = data.session;
  if (!session) {
    status.textContent = 'Sign in first.';
    el('confirm-go').disabled = false;
    return;
  }

  try {
    const response = await fetch(
      `${window.PELONOT_CONFIG.supabaseUrl}/functions/v1/link-device`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: window.PELONOT_CONFIG.supabaseAnonKey,
          Authorization: `Bearer ${session.access_token}`,
        },
        body: JSON.stringify({ code }),
      }
    );

    if (response.ok) return finish(false);

    if (response.status !== 404) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || `The server said ${response.status}.`);
    }
  } catch (failure) {
    // A network error reaching a function that may not exist is the same
    // situation as a 404 for our purposes, and falling through is the whole
    // point of having written 15.6.9 down in advance.
    if (failure && failure.message && !/Failed to fetch|NetworkError/.test(failure.message)) {
      status.classList.add('error');
      status.textContent = failure.message;
      el('confirm-go').disabled = false;
      return;
    }
  }

  // Fallback (15.6.9). Said out loud rather than done quietly, because it has a
  // cost the rider can feel: this phone loses its session.
  el('fallback-warning').classList.remove('hidden');

  const { data: claim, error } = await client.rpc('device_link_claim', {
    p_code: code,
    p_payload: { kind: 'refresh', token: session.refresh_token },
  });

  if (error || !claim || claim.status !== 'linked') {
    status.classList.add('error');
    status.textContent = error ? error.message : 'That code has expired.';
    el('confirm-go').disabled = false;
    return;
  }

  finish(true);
}

function finish(handedOwnSession) {
  status.textContent = '';
  el('confirm').classList.add('hidden');
  el('device-card').classList.add('hidden');
  el('done').classList.remove('hidden');
  if (handedOwnSession) {
    el('done-text').textContent =
      'The bike is signed in. This phone has been signed out, because it handed ' +
      'its own session over — sign in again here whenever you like.';
    // Stop using the token family we just gave away, rather than racing the
    // bike for it and having the server revoke both.
    client.auth.signOut();
  }
}
