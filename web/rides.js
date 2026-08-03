/*
 * Ride history on the web (PLAN 17.2, 17.3).
 *
 * It reads exactly what the bike wrote and nothing else. Two consequences that
 * are worth stating rather than discovering:
 *
 * - **It can only ever see riders with accounts** (17.10). A household profile
 *   with no account does not exist in the cloud — not as an empty row, not as a
 *   placeholder — so this page must never draw a housemate as someone with no
 *   rides. It draws nobody but you, which is also what `003`'s policies allow.
 * - **`user_id` filtering is belt and braces.** RLS already restricts every row
 *   to `auth.uid()`; the explicit filter is here so that a policy mistake shows
 *   up as *missing* rows rather than as somebody else's.
 */

const el = (id) => document.getElementById(id);

const status = el('status');
const client = pelonotClient(status);

let mode = 'signin';

if (client) {
  wire();
  client.auth.onAuthStateChange((_event, session) => render(session));
  client.auth.getSession().then(({ data }) => render(data.session));
}

function wire() {
  el('tab-signin').addEventListener('click', () => setMode('signin'));
  el('tab-signup').addEventListener('click', () => setMode('signup'));
  el('auth-form').addEventListener('submit', submit);
  el('sign-out').addEventListener('click', () => client.auth.signOut());
  el('back').addEventListener('click', showList);
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

async function submit(event) {
  event.preventDefault();
  const email = el('email').value.trim().toLowerCase();
  const password = el('password').value;
  const error = el('auth-error');
  error.textContent = '';
  el('auth-submit').disabled = true;

  try {
    if (mode === 'signin') {
      const { error: failure } = await client.auth.signInWithPassword({ email, password });
      if (failure) throw failure;
    } else {
      const { data, error: failure } = await client.auth.signUp({ email, password });
      if (failure) throw failure;
      // Sign-up against a project with email confirmation on returns a user and
      // **no session**. Saying "check your email" is the whole of handling it;
      // treating it as success is how a rider concludes the app is broken.
      if (!data.session) {
        error.classList.remove('error');
        error.textContent =
          `Check ${email} for a confirmation link, then come back and sign in.`;
      }
    }
  } catch (failure) {
    error.classList.add('error');
    error.textContent = failure.message || 'That did not work.';
  } finally {
    el('auth-submit').disabled = false;
  }
}

function render(session) {
  const signedIn = Boolean(session);
  el('signed-out').classList.toggle('hidden', signedIn);
  el('signed-in').classList.toggle('hidden', !signedIn);
  el('ride').classList.add('hidden');
  if (!signedIn) return;

  el('who').textContent = `Signed in as ${session.user.email}`;
  loadRides(session.user.id);
}

async function loadRides(userId) {
  const list = el('rides');
  list.innerHTML = '<p class="muted small">Loading…</p>';

  // The series is deliberately not selected here: a year of rides would be
  // megabytes of payload to draw a list of dates. Ride detail fetches its own.
  const { data, error } = await client
    .from('workouts')
    .select('id, class_id, duration_sec, total_output_kj, total_distance_km, ' +
      'avg_power, avg_cadence, avg_hr, recorded_at')
    .eq('user_id', userId)
    .order('recorded_at', { ascending: false });

  if (error) {
    list.innerHTML = '';
    status.classList.add('error');
    status.textContent = `Could not read your rides: ${error.message}`;
    return;
  }

  status.textContent = '';
  status.classList.remove('error');
  el('empty').classList.toggle('hidden', data.length > 0);
  list.innerHTML = '';

  for (const ride of data) {
    const card = document.createElement('button');
    card.className = 'card tap';
    card.innerHTML = `
      <div class="figure">${formatDuration(ride.duration_sec)}</div>
      <div class="small muted">${formatDate(ride.recorded_at)}</div>
      <div class="small">
        ${Math.round(ride.total_output_kj)} kJ ·
        ${ride.total_distance_km.toFixed(1)} km${
          ride.avg_power ? ` · ${Math.round(ride.avg_power)} W avg` : ''
        }
      </div>`;
    card.addEventListener('click', () => showRide(ride.id));
    list.appendChild(card);
  }
}

function showList() {
  el('ride').classList.add('hidden');
  el('signed-in').classList.remove('hidden');
}

async function showRide(id) {
  el('signed-in').classList.add('hidden');
  el('ride').classList.remove('hidden');
  el('ride-title').textContent = 'Loading…';

  const { data, error } = await client
    .from('workouts')
    .select('*')
    .eq('id', id)
    .single();

  if (error) {
    el('ride-title').textContent = 'Could not read that ride';
    el('ride-when').textContent = error.message;
    return;
  }

  el('ride-title').textContent = data.class_id || 'Free ride';
  el('ride-when').textContent = formatDate(data.recorded_at);

  const figures = [
    ['Time', formatDuration(data.duration_sec)],
    ['Output', `${Math.round(data.total_output_kj)} kJ`],
    ['Distance', `${data.total_distance_km.toFixed(1)} km`],
    ['Avg power', data.avg_power ? `${Math.round(data.avg_power)} W` : '—'],
    ['Avg cadence', data.avg_cadence ? `${Math.round(data.avg_cadence)} rpm` : '—'],
    // Null is unknown and 0 would be a rider with no pulse. The dash is the
    // only honest rendering of a ride nobody wore a strap for.
    ['Avg HR', data.avg_hr ? `${Math.round(data.avg_hr)} bpm` : '—'],
  ];
  el('ride-figures').innerHTML = figures
    .map(([label, value]) =>
      `<div><div class="figure-label">${label}</div><div class="figure">${value}</div></div>`)
    .join('');

  const samples = decodeMetrics(data.metrics_payload);
  drawTrace(el('chart-power'), samples, 'power', 'var(--power)');
  drawTrace(el('chart-cadence'), samples, 'cadence', 'var(--cadence)');
  drawTrace(el('chart-hr'), samples, 'heartRate', 'var(--hr)');

  // 16.1.6 and 14.4.7. A modelled watt must never be presented as a measured
  // one — the shipped power curve scores RMSE 137 W against the real board — so
  // the caption says which this ride's numbers are, including when it is both.
  const measured = samples.filter((s) => s.measured === true).length;
  const modelled = samples.filter((s) => s.measured === false).length;
  const unknown = samples.length - measured - modelled;
  el('power-provenance').textContent =
    samples.length === 0 ? 'No series was stored with this ride.'
      : modelled === 0 && unknown === 0 ? 'Power measured by the bike.'
      : measured === 0 && unknown === 0 ? 'Power estimated from cadence and resistance.'
      : unknown === samples.length ? 'This ride does not record where its watts came from.'
      : `Power measured for ${measured} of ${samples.length} samples; the rest ` +
        'is estimated or unrecorded.';
}
