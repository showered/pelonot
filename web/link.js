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

/*
 * Whether the server has told us what this code is pairing: null until it has
 * answered, then true or false. It is a variable rather than a reading of the
 * rendered text, and that is the bug it fixes (PLAN 17.16.5). `route()` used to
 * ask whether the device label said "That code has expired", and
 * `onAuthStateChange` fires its first event immediately — often while the label
 * still reads "…" — so an unknown code got the sign-in form anyway. The page
 * then said it could not find the bike and asked for a password underneath,
 * which is the exact thing 15.6.5 exists to prevent: nothing is asked for
 * before the rider is told what is asking.
 *
 * **What it gates is the hand-off, and only the hand-off** (17.16.6). 17.16.5
 * gated the sign-in form on it too, and that made an expired code a dead end:
 * the owner scanned a QR, landed here, and had no way to sign in at all. Those
 * are two different questions. *May a session leave this phone for that bike?*
 * has to know which bike, and that is 15.6.5. *May the rider sign in to
 * Pelonot on their own phone?* is the same sign-in `index.html` offers, to the
 * same project, and a five-minute pairing code makes it no safer.
 */
let described = null;

/** Set once the bike has been handed a session; nothing is redrawn after it. */
let finished = false;

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

  // Re-scanning the QR from an already-open page changes only the fragment,
  // and a hash change does not reload a document — so without this the page
  // would sit on the stale code it opened with. 17.16.5 found the trap and
  // nothing reached it; 17.16.6 makes it reachable, because a rider whose code
  // lapsed is now told to go and get another one.
  window.addEventListener('hashchange', () => {
    const next = (location.hash || '').replace(/^#/, '').trim().toUpperCase();
    if (next && next !== code) {
      code = next;
      describe();
    }
  });

  if (code) await describe();
  else route();
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
  described = null;
  el('device-card').classList.remove('hidden');
  el('device-caption').textContent = 'This will sign in';
  el('device-label').textContent = '…';
  el('device-code').textContent = code.replace(/(.{4})(.{4})/, '$1 $2');
  route();

  const { data, error } = await client.rpc('device_link_describe', { p_code: code });

  if (error || !data || data.status !== 'waiting') {
    described = false;
    // The caption goes too. Left alone it read "This will sign in" directly
    // above "That code has expired" — a promise about a device the page has
    // just said it cannot find.
    el('device-caption').textContent = 'Nothing to sign in';
    el('device-label').textContent = 'That code has expired';
    el('device-code').textContent =
      'Codes last five minutes. Ask the bike for a new one.';
    route();
    return;
  }

  described = true;
  el('device-label').textContent = data.label;
  route();
}

/**
 * Four sections, two independent questions (17.16.6).
 *
 * Signing in depends on the session and nothing else. Handing a session over
 * depends on the session *and* on the server having named the device — which
 * is where 15.6.5 lives, and the only place it needs to.
 */
async function route() {
  // Once the bike has it, this page has nothing left to offer. Without the
  // guard the fallback's own `signOut()` comes back through here and redraws
  // the sign-in form underneath the word "Done".
  if (finished) return;

  const { data } = await client.auth.getSession();
  const signedIn = Boolean(data.session);
  const known = described === true;

  el('signed-out').classList.toggle('hidden', signedIn);
  el('confirm').classList.toggle('hidden', !signedIn || !known);

  // The way forward whenever there is no bike to sign in: no code at all, or
  // one the server did not recognise. `described === null` is the second or
  // two before the first answer, and offering a retry box during it would be
  // asking the rider to fix something that is not yet broken.
  el('need-code').classList.toggle('hidden', described !== false && code !== '');

  // Who, said once. The device card above says which bike; this says which
  // account. Between them the confirm button has both halves of what it is
  // about to do, which is the whole of 15.6.5's requirement.
  const who = el('signed-in-as');
  who.classList.toggle('hidden', !signedIn);
  if (signedIn) who.textContent = `Signed in as ${data.session.user.email}`;
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
  finished = true;
  status.textContent = '';
  el('confirm').classList.add('hidden');
  el('device-card').classList.add('hidden');
  el('need-code').classList.add('hidden');
  el('signed-out').classList.add('hidden');
  el('signed-in-as').classList.add('hidden');
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
