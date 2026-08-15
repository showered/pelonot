/*
 * The companion web app (PLAN Phase 17).
 *
 * Five views behind one sign-in: your rides, one ride, the leaderboard, the
 * other riders, and you. `link.html` is the other page and is deliberately
 * separate — it is opened by a QR code on a phone, once, and has nothing to do
 * with any of this.
 *
 * Three rules this file is built on, all of them learnt the hard way:
 *
 * - **Never call a Supabase method from inside `onAuthStateChange`.** auth-js
 *   runs the callback holding an exclusive Web Lock on the storage key, so a
 *   request made underneath it queues behind the thing waiting for it and the
 *   page never finishes — and only for a rider who is signed in, which is why
 *   it survived three sittings on the pairing page (15.6.14). The callback here
 *   writes the session down, redraws synchronously, and schedules any loading
 *   for the next tick.
 * - **`client.rpc(...)` is a thenable, not a promise.** It has `.then` and no
 *   `.catch`, so hanging a `.catch` on one throws a `TypeError` that looks
 *   exactly like the hang it was meant to fix. `await` inside a `try`.
 * - **It can only ever see riders with accounts** (17.10). A household profile
 *   with no account does not exist in the cloud — not as an empty row, not as a
 *   placeholder — so this app must never draw a housemate as somebody with no
 *   rides. What it says instead is on the Riders view, in words.
 *
 * `user_id` filtering on the rider's own rows is belt and braces: RLS already
 * restricts every row to `auth.uid()`, and the explicit filter means a policy
 * mistake shows up as *missing* rows rather than as somebody else's.
 */

const el = (id) => document.getElementById(id);

const status = el('status');
const client = pelonotClient(status);

let mode = 'signin';

/** The session, written down by the auth callback and never asked for again. */
let session = null;
let sessionKnown = false;

/** The rider's own profile row — FTP, units, sharing. Null until it loads. */
let profile = null;

/** The rides list, kept so the totals and the twelve weeks are one fetch. */
let rides = [];

/** The ride open in the detail view, and its decoded samples. */
let openRide = null;
let openSamples = [];

/**
 * Total output, or output per kilo — the board's own toggle.
 *
 * It is **kJ/kg and not W/kg**, and the label says so: the board's function
 * returns each rider's best total output and their weight, and dividing those
 * gives energy per kilo. W/kg would need an average power the function does not
 * return, and calling it watts because that is the phrase people know would be
 * a unit invented to match a habit (24.3.14 is the open question about which
 * figure the board should be on at all).
 */
let boardBasis = 'kj';

const VIEWS = ['rides', 'ride', 'board', 'riders', 'you'];

if (client) start();

function start() {
  // The browser restores the last scroll position on reload, which is right for
  // a document and wrong for this: the views are sections of one page, so a
  // reload on `#/you` came back 1,500 pixels down a screen that is 900 tall and
  // showed a field with nothing above it.
  // `scrollRestoration` alone did not do it — measured at 291 pixels down after
  // a reload with it set — because the restore happens after the document has
  // loaded rather than before this runs. So the belt as well: both, and the
  // page starts at the top either way.
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
  window.addEventListener('load', () => window.scrollTo(0, 0));
  wire();

  // Synchronous, and it must stay that way — see the header. It takes what it
  // is given, redraws, and lets the next tick do the asking.
  client.auth.onAuthStateChange((_event, next) => {
    const changed = (next && next.user.id) !== (session && session.user.id);
    session = next;
    sessionKnown = true;
    render();
    if (session && changed) setTimeout(loadEverything, 0);
  });

  window.addEventListener('hashchange', () => {
    // A view swapped underneath a scrolled window leaves the rider halfway down
    // a page they have not seen the top of — measured on the real page, going
    // from a ride's comments to *You* landed on the weight field. The views are
    // sections of one document rather than separate pages, so nothing does this
    // for us.
    window.scrollTo(0, 0);
    render();
    if (session) setTimeout(routeLoad, 0);
  });
}

function wire() {
  el('tab-signin').addEventListener('click', () => setMode('signin'));
  el('tab-signup').addEventListener('click', () => setMode('signup'));
  el('auth-form').addEventListener('submit', submit);
  el('sign-out').addEventListener('click', () => client.auth.signOut());
  el('back').addEventListener('click', () => go('rides'));

  for (const button of document.querySelectorAll('#nav button')) {
    button.addEventListener('click', () => go(button.dataset.view));
  }

  el('board-class').addEventListener('change', loadBoard);
  el('board-basis').addEventListener('click', () => {
    boardBasis = boardBasis === 'kj' ? 'wkg' : 'kj';
    el('board-basis').textContent =
      boardBasis === 'kj' ? 'Show output per kilo' : 'Show total output';
    loadBoard();
  });

  el('ride-save').addEventListener('click', saveRide);
  el('ride-kudos').addEventListener('click', toggleKudos);
  el('comment-send').addEventListener('click', postComment);
  el('you-units').addEventListener('change', switchWeightUnits);
  el('you-save').addEventListener('click', saveProfile);
  el('you-export').addEventListener('click', exportData);
}

/* --- Signing in ---------------------------------------------------------- */

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
    // A project with sign-up closed answers with a message about the *setting*,
    // which tells a rider standing at a bike nothing they can act on.
    error.textContent = /signups? not allowed/i.test(failure.message || '')
      ? 'This Pelonot is invitation only. Ask whoever set the bike up to add you.'
      : failure.message || 'That did not work.';
  } finally {
    el('auth-submit').disabled = false;
  }
}

/* --- Where we are -------------------------------------------------------- */

/** `#/board`, `#/ride/<uuid>` — the view name and its argument. */
function currentRoute() {
  const parts = (location.hash || '').replace(/^#\/?/, '').split('/');
  const name = VIEWS.includes(parts[0]) ? parts[0] : 'rides';
  return { name, arg: parts[1] || null };
}

function go(view, arg) {
  location.hash = arg ? `#/${view}/${arg}` : `#/${view}`;
}

/**
 * Draw whichever view the URL names. Synchronous by contract — it is called
 * from the auth callback, and anything that asks the network goes on the next
 * tick (see the header).
 */
function render() {
  const signedIn = Boolean(session);
  const route = currentRoute();

  el('signed-out').classList.toggle('hidden', !sessionKnown || signedIn);
  el('nav').classList.toggle('hidden', !signedIn);
  for (const view of VIEWS) {
    el(`view-${view}`).classList.toggle('hidden', !signedIn || view !== route.name);
  }
  // Prose is capped at the readable width; a set of tiles, cards or rows is
  // looked at rather than read and gets the content width (CLAUDE.md, 22.4).
  el('main').classList.toggle('wide', signedIn && route.name !== 'you');

  for (const button of document.querySelectorAll('#nav button')) {
    const active = button.dataset.view === route.name ||
      (route.name === 'ride' && button.dataset.view === 'rides');
    if (active) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  }

  if (!signedIn) {
    el('subtitle').textContent = 'Your rides, off the bike.';
    el('who').textContent = '';
    return;
  }

  el('who').textContent = session.user.email;
  el('subtitle').textContent = profile
    ? `${profile.name}'s rides, off the bike.`
    : 'Your rides, off the bike.';
}

/** Everything a freshly signed-in page needs, in one place. */
async function loadEverything() {
  await loadProfile();
  render();
  await loadRides();
  await routeLoad();
}

/** The one view's own data, on every navigation. */
async function routeLoad() {
  const route = currentRoute();
  if (route.name === 'ride' && route.arg) return showRide(route.arg);
  if (route.name === 'board') return loadBoardClasses();
  if (route.name === 'riders') return loadRiders();
  if (route.name === 'you') return fillProfileForm();
  if (route.name === 'rides' && !rides.length) return loadRides();
  return undefined;
}

function fail(message, error) {
  status.classList.add('error');
  status.textContent = `${message}: ${error.message || error}`;
}

function clearStatus() {
  status.classList.remove('error');
  status.textContent = '';
}

/** A change that saved and said nothing looks exactly like one that did not. */
function toast(message) {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();
  const node = document.createElement('div');
  node.className = 'toast';
  node.textContent = message;
  document.body.appendChild(node);
  setTimeout(() => node.remove(), 2600);
}

/* --- The rider's own profile --------------------------------------------- */

async function loadProfile() {
  const { data, error } = await client
    .from('profiles')
    .select('*')
    .eq('id', session.user.id)
    .maybeSingle();

  if (error) return fail('Could not read your profile', error);

  // A rider who signed up on this page and has never touched a bike has an
  // account and no profile row. That is a real state rather than an error: the
  // bike writes the row when it first syncs. Standing one up here means the
  // page has a name to show and a place to keep units — and the id is the auth
  // id by construction (003), so it cannot collide with anybody.
  if (!data) {
    const fallback = {
      id: session.user.id,
      name: (session.user.email || 'Rider').split('@')[0],
      ftp_watts: 150,
      weight_kg: 70,
    };
    const { data: created, error: failure } = await client
      .from('profiles').insert(fallback).select().maybeSingle();
    if (failure) return fail('Could not create your profile', failure);
    profile = created;
  } else {
    profile = data;
  }
  clearStatus();
  return undefined;
}

function unitsForRider() {
  return units(profile && profile.units);
}

/* --- Your rides ---------------------------------------------------------- */

async function loadRides() {
  const list = el('rides');
  list.innerHTML = '<p class="muted small">Loading…</p>';

  // The series is deliberately not selected here: a year of rides would be
  // megabytes of payload to draw a list of dates. Ride detail fetches its own.
  const { data, error } = await client
    .from('workouts')
    // `class_templates` is world-readable (15.5.3), so the class's *name* comes
    // back with the ride rather than the rider being shown `CLB-01`. The bike
    // never shows an id and neither should this.
    .select('id, class_id, title, hidden, duration_sec, total_output_kj, ' +
      'total_distance_km, avg_power, avg_cadence, avg_hr, power_provenance, ' +
      'recorded_at, class_templates(title, category)')
    .eq('user_id', session.user.id)
    .order('recorded_at', { ascending: false });

  if (error) {
    list.innerHTML = '';
    return fail('Could not read your rides', error);
  }

  clearStatus();
  rides = data;
  drawTotals();
  drawWeeks();

  el('empty').classList.toggle('hidden', rides.length > 0);
  el('weeks-card').classList.toggle('hidden', rides.length === 0);
  list.innerHTML = '';

  const unit = unitsForRider();
  for (const ride of rides) {
    const card = document.createElement('button');
    card.className = 'card tap';
    // **What a list row is for is *which ride*, not *how much energy*** —
    // 26.1.3. Time, when, and what it was; the kilojoules and the average watts
    // are one tap away on the ride itself, where the number is being read
    // rather than scanned past.
    card.innerHTML = `
      <div class="figure">${formatDuration(ride.duration_sec)}</div>
      <div class="small muted">${escapeHtml(formatDate(ride.recorded_at))}</div>
      <div class="small">${escapeHtml(rideName(ride))}</div>
      <div class="caption muted">${unit.distance(ride.total_distance_km || 0)}${
        ride.hidden ? ' · hidden' : ''
      }</div>`;
    card.addEventListener('click', () => go('ride', ride.id));
    list.appendChild(card);
  }
  return undefined;
}

/**
 * What to call a ride: the rider's own name for it, then the class's title,
 * then *Free ride*. The class **id** is never shown — `CLB-01` is a key, and a
 * rider looking for last Thursday's climb is not looking for a key.
 */
function rideName(ride) {
  return ride.title || (ride.class_templates && ride.class_templates.title) || 'Free ride';
}

function drawTotals() {
  const unit = unitsForRider();
  const since = Date.now() - 84 * 86400000;
  const recent = rides.filter((r) => new Date(r.recorded_at).getTime() >= since);
  const sum = (list, key) => list.reduce((total, r) => total + (r[key] || 0), 0);

  const tiles = [
    ['Rides', String(rides.length), 'all time'],
    ['Time', formatTotalTime(sum(rides, 'duration_sec')), 'all time'],
    ['Output', `${Math.round(sum(rides, 'total_output_kj')).toLocaleString()} kJ`, 'all time'],
    ['Distance', unit.distance(sum(rides, 'total_distance_km')), 'all time'],
    // 22.5: a week is the wrong window for somebody who rides once of them.
    ['Last 12 weeks', String(recent.length), recent.length === 1 ? 'ride' : 'rides'],
  ];

  el('totals').innerHTML = tiles.map(([label, value, note]) => `
    <div class="tile">
      <p class="figure-label">${escapeHtml(label)}</p>
      <p class="figure">${escapeHtml(value)}</p>
      <p class="caption muted">${escapeHtml(note)}</p>
    </div>`).join('');
}

function drawWeeks() {
  const weeks = [];
  const now = new Date();
  const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  // Monday, because a training week is a Monday everywhere except a calendar
  // widget.
  startOfWeek.setDate(startOfWeek.getDate() - ((startOfWeek.getDay() + 6) % 7));

  for (let i = 11; i >= 0; i -= 1) {
    const from = new Date(startOfWeek);
    from.setDate(from.getDate() - i * 7);
    const to = new Date(from);
    to.setDate(to.getDate() + 7);
    const inWeek = rides.filter((ride) => {
      const at = new Date(ride.recorded_at).getTime();
      return at >= from.getTime() && at < to.getTime();
    });
    weeks.push({
      label: from.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      kj: inWeek.reduce((total, ride) => total + (ride.total_output_kj || 0), 0),
      rides: inWeek.length,
    });
  }

  drawWeeklyBars(el('chart-weeks'), weeks);
  const ridden = weeks.filter((week) => week.rides).length;
  el('weeks-caption').textContent = ridden
    ? `Rode in ${ridden} of the last 12 weeks`
    : 'Nothing in the last twelve weeks';
}

/* --- One ride ------------------------------------------------------------ */

async function showRide(id) {
  el('ride-title').textContent = 'Loading…';
  el('ride-when').textContent = '';

  const { data, error } = await client
    .from('workouts')
    .select('*, class_templates(title, category)')
    .eq('id', id)
    .eq('user_id', session.user.id)
    .maybeSingle();

  if (error || !data) {
    el('ride-title').textContent = 'Could not open that ride';
    el('ride-when').textContent = error ? error.message
      : 'It is not one of yours, or it is no longer in the cloud.';
    el('ride-figures').innerHTML = '';
    return;
  }

  openRide = data;
  openSamples = decodeMetrics(data.metrics_payload);
  const unit = unitsForRider();

  el('ride-title').textContent = rideName(data);
  el('ride-when').textContent = formatDate(data.recorded_at);

  const figures = [
    ['Time', formatDuration(data.duration_sec)],
    ['Output', `${Math.round(data.total_output_kj)} kJ`],
    ['Distance', unit.distance(data.total_distance_km || 0)],
    ['Avg power', data.avg_power ? `${Math.round(data.avg_power)} W` : '—'],
    ['Avg cadence', data.avg_cadence ? `${Math.round(data.avg_cadence)} rpm` : '—'],
    // Null is unknown and 0 would be a rider with no pulse. The dash is the
    // only honest rendering of a ride nobody wore a strap for.
    ['Avg HR', data.avg_hr ? `${Math.round(data.avg_hr)} bpm` : '—'],
  ];
  el('ride-figures').innerHTML = figures
    .map(([label, value]) =>
      `<div><div class="figure-label">${label}</div>` +
      `<div class="figure">${escapeHtml(value)}</div></div>`)
    .join('');

  drawRideCharts();
  drawTimeInZone();

  el('ride-name').value = data.title || '';
  el('ride-hidden').checked = Boolean(data.hidden);
  el('ride-save-note').textContent = '';

  await loadSocial(data.id);
}

function drawRideCharts() {
  const ftp = profile && profile.ftp_watts;
  const maxHr = profile && profile.max_hr_bpm;

  drawTrace(el('chart-power'), openSamples, 'power', 'var(--power)',
    { zones: POWER_ZONES, reference: ftp });
  el('power-caption').textContent = ftp
    ? `Zones from your FTP of ${ftp} W`
    : 'No FTP on your profile, so no zones';
  el('power-zone-key').innerHTML = ftp ? zoneKeyHtml(POWER_ZONES, ftp, 'W') : '';

  drawTrace(el('chart-cadence'), openSamples, 'cadence', 'var(--cadence)');

  const hasHr = openSamples.some((s) => s.heartRate !== null && s.heartRate !== undefined);
  drawTrace(el('chart-hr'), openSamples, 'heartRate', 'var(--hr)',
    { zones: HR_ZONES, reference: maxHr });
  // 21.4.2: any chart showing a heart rate shows what its bands mean — and
  // 21.2.2: say which basis they are on, because %HRmax is not %HRR.
  el('hr-caption').textContent = !hasHr ? 'No strap was paired for this ride'
    : maxHr ? `Zones as % of your maximum of ${maxHr} bpm`
    : 'Add your maximum heart rate under You to see zones';
  el('hr-zone-key').innerHTML = hasHr && maxHr ? zoneKeyHtml(HR_ZONES, maxHr, 'bpm') : '';

  el('power-provenance').textContent =
    provenanceSentence(openSamples, openRide.power_provenance);

  // 23.4.3: anything drawing or exporting the trace says what resolution it is.
  // The line has the same shape, the same peak and the same axis either way,
  // which is exactly why it has to be said rather than left to be noticed.
  const detail = detailSeconds(openRide.metrics_payload);
  el('resolution').textContent = detail
    ? `This ride has been condensed: the highest and lowest watt of every ` +
      `${detail} seconds, not every second.`
    : '';
}

function zoneKeyHtml(zones, reference, unit) {
  return zones.map((zone) => {
    const low = Math.round(zone.lower * reference);
    const high = Number.isFinite(zone.upper) ? Math.round(zone.upper * reference) - 1 : null;
    const range = high === null ? `${low}+` : `${low}–${high}`;
    return `<span class="zone-chip"><span class="zone-swatch" ` +
      `style="background:var(--zone-${zone.key})"></span>` +
      `Z${zone.number} ${escapeHtml(zone.name)} · ${range} ${unit}</span>`;
  }).join('');
}

function drawTimeInZone() {
  const ftp = profile && profile.ftp_watts;
  const card = el('time-in-zone-card');
  if (!ftp || openSamples.length === 0) {
    card.classList.add('hidden');
    return;
  }
  card.classList.remove('hidden');

  const { totals, stopped, counted } = timeInZone(openSamples, POWER_ZONES, 'power', ftp);
  const bar = el('time-in-zone');
  bar.innerHTML = POWER_ZONES.map((zone) => {
    const seconds = totals.get(zone.key);
    const share = counted ? (seconds / counted) * 100 : 0;
    return share > 0
      ? `<span style="width:${share.toFixed(2)}%;background:var(--zone-${zone.key})"></span>`
      : '';
  }).join('');

  el('time-in-zone-key').innerHTML = POWER_ZONES.map((zone) => {
    const seconds = totals.get(zone.key);
    if (!seconds) return '';
    const share = Math.round((seconds / counted) * 100);
    return `<span class="zone-chip"><span class="zone-swatch" ` +
      `style="background:var(--zone-${zone.key})"></span>` +
      `Z${zone.number} ${formatDuration(seconds)} · ${share}%</span>`;
  }).join('');

  // **A stop is not Active Recovery** (19.1.2c). The stopped seconds are named
  // rather than drawn as a wedge in the bar, and the caption only appears when
  // there were any — a caption that is always there stops being read (21.4.1).
  el('time-in-zone-caption').textContent = stopped
    ? `Pedalling for ${formatDuration(counted)} of ${formatDuration(counted + stopped)}`
    : '';
}

async function saveRide() {
  if (!openRide) return;
  const button = el('ride-save');
  button.disabled = true;
  const title = el('ride-name').value.trim();

  // **One tap is one write** (7.9). Two coroutines doing read-modify-write on
  // one row ate each other's field on the Android side and left the rider's new
  // FTP undone with nothing on screen wrong; the web version of that mistake is
  // two `update` calls off one button. This is one.
  const { error } = await client
    .from('workouts')
    .update({ title: title || null, hidden: el('ride-hidden').checked })
    .eq('id', openRide.id)
    .eq('user_id', session.user.id);

  button.disabled = false;
  if (error) {
    el('ride-save-note').textContent = error.message;
    el('ride-save-note').classList.add('error');
    return;
  }

  openRide.title = title || null;
  openRide.hidden = el('ride-hidden').checked;
  const listed = rides.find((ride) => ride.id === openRide.id);
  if (listed) { listed.title = openRide.title; listed.hidden = openRide.hidden; }
  el('ride-title').textContent = rideName(openRide);
  el('ride-save-note').classList.remove('error');
  el('ride-save-note').textContent = 'Saved';
  toast('Ride saved');
}

/* --- Kudos and comments -------------------------------------------------- */

async function loadSocial(workoutId) {
  el('ride-comments').innerHTML = '';
  el('ride-kudos-count').textContent = '';
  el('social-note').textContent = profile && profile.share_activity
    ? 'Everyone here can see this ride and say something under it.'
    : 'Only you can see this ride. Turn sharing on under You to let the ' +
      'others see it and cheer.';

  const { data, error } = await client.rpc('ride_comments_for', { p_workout: workoutId });
  if (error) return;

  // The kudos count is not on the ride row, so it comes from the feed's own
  // answer — one request, and it is the same number the feed shows, which is
  // what stops two surfaces disagreeing about one ride.
  const { data: feed } = await client.rpc('activity_feed', { p_limit: 200 });
  const entry = (feed || []).find((item) => item.workout_id === workoutId);
  el('ride-kudos-count').textContent = entry
    ? `${entry.kudos_count} kudos` : '0 kudos';
  el('ride-kudos').textContent = entry && entry.you_gave_kudos ? '👏 Given' : '👏 Kudos';

  drawComments(data || []);
  return undefined;
}

function drawComments(comments) {
  const box = el('ride-comments');
  box.innerHTML = comments.map((comment) => `
    <div class="comment">
      <div class="small"><strong>${escapeHtml(comment.name)}</strong>
        <span class="caption muted">${escapeHtml(formatRelativeDay(comment.created_at))}</span>
      </div>
      <div class="small">${escapeHtml(comment.body)}</div>
      ${comment.can_delete
        ? `<button class="link" data-delete="${escapeHtml(comment.id)}">Delete</button>`
        : ''}
    </div>`).join('');

  for (const button of box.querySelectorAll('[data-delete]')) {
    button.addEventListener('click', async () => {
      const { error } = await client.rpc('delete_ride_comment', {
        p_id: button.dataset.delete,
      });
      if (!error && openRide) loadSocial(openRide.id);
    });
  }
}

async function toggleKudos() {
  if (!openRide) return;
  const giving = el('ride-kudos').textContent.includes('Kudos');
  const { error } = await client.rpc(giving ? 'give_kudos' : 'remove_kudos', {
    p_workout: openRide.id,
  });
  if (error) { toast(error.message); return; }
  await loadSocial(openRide.id);
}

async function postComment() {
  if (!openRide) return;
  const body = el('comment-body').value.trim();
  if (!body) return;
  el('comment-send').disabled = true;
  const { error } = await client.rpc('add_ride_comment', {
    p_workout: openRide.id,
    p_body: body,
  });
  el('comment-send').disabled = false;
  if (error) { toast(error.message); return; }
  el('comment-body').value = '';
  await loadSocial(openRide.id);
}

/* --- The board ----------------------------------------------------------- */

async function loadBoardClasses() {
  const picker = el('board-class');
  const { data, error } = await client.rpc('leaderboard_classes');
  if (error) return fail('Could not read the classes', error);

  if (!data.length) {
    picker.innerHTML = '<option>No class has a ranked ride yet</option>';
    el('board').innerHTML = '';
    el('board-note').textContent =
      'A ride is only ranked when the bike measured the watts. A simulated ' +
      'ride never is, which is why an emulator shows nothing here.';
    return undefined;
  }

  const wanted = picker.value;
  picker.innerHTML = data.map((row) =>
    `<option value="${escapeHtml(row.class_id)}">${escapeHtml(row.title)} · ` +
    `${row.riders} rider${row.riders === 1 ? '' : 's'}</option>`).join('');
  if (data.some((row) => row.class_id === wanted)) picker.value = wanted;
  return loadBoard();
}

async function loadBoard() {
  const classId = el('board-class').value;
  if (!classId) return;
  const board = el('board');
  board.innerHTML = '<p class="muted small">Loading…</p>';

  const { data, error } = await client.rpc('class_leaderboard', { p_class_id: classId });
  if (error) { board.innerHTML = ''; fail('Could not read the board', error); return; }

  const rows = [...data].sort((a, b) => value(b) - value(a));
  function value(row) {
    return boardBasis === 'kj' ? row.output_kj
      : row.weight_kg > 0 ? row.output_kj / row.weight_kg : 0;
  }

  board.innerHTML = rows.map((row, index) => `
    <div class="board-row${row.is_you ? ' you' : ''}">
      <span class="rank">${index + 1}</span>
      ${avatarHtml(row.name, row.account_id)}
      <span class="spacer"><strong>${escapeHtml(row.name)}</strong>${
        row.is_you ? ' <span class="caption muted">you</span>' : ''
      }</span>
      <span class="figure">${
        boardBasis === 'kj'
          ? `${Math.round(row.output_kj)} kJ`
          : `${value(row).toFixed(1)} kJ/kg`
      }</span>
    </div>`).join('');

  el('board-note').textContent = rows.length === 1
    ? 'Only your own effort so far. Everyone with an account appears here as ' +
      'soon as they have ridden this class on a bike that measures watts.'
    : '';
}

/* --- Riders and the feed ------------------------------------------------- */

async function loadRiders() {
  const feed = el('feed');
  feed.innerHTML = '<p class="muted small">Loading…</p>';

  const [{ data: items, error: feedError }, { data: riders, error: ridersError }] =
    await Promise.all([
      client.rpc('activity_feed', { p_limit: 40 }),
      client.rpc('rider_directory'),
    ]);

  if (feedError) { feed.innerHTML = ''; fail('Could not read the feed', feedError); return; }

  const unit = unitsForRider();
  el('feed-empty').classList.toggle('hidden', (items || []).length > 0);
  feed.innerHTML = (items || []).map((item) => `
    <div class="card feed-item">
      ${avatarHtml(item.name, item.account_id)}
      <div class="body">
        <div class="small"><strong>${escapeHtml(item.name)}</strong>
          <span class="caption muted">${escapeHtml(formatRelativeDay(item.recorded_at))}</span>
        </div>
        <div>${escapeHtml(item.title || item.class_title || item.class_id || 'Free ride')}</div>
        <div class="small muted">
          ${formatDuration(item.duration_sec)} ·
          ${Math.round(item.output_kj)} kJ ·
          ${unit.distance(item.distance_km || 0)}
        </div>
        <div class="row middle tight" style="margin-top:8px">
          <button class="secondary inline" data-kudos="${escapeHtml(item.workout_id)}"
                  data-given="${item.you_gave_kudos}">
            ${item.you_gave_kudos ? '👏 Given' : '👏 Kudos'}
          </button>
          <span class="caption muted">${item.kudos_count} · ${item.comment_count} comment${
            Number(item.comment_count) === 1 ? '' : 's'
          }</span>
          ${item.is_you
            ? `<button class="link" data-open="${escapeHtml(item.workout_id)}">Open</button>`
            : ''}
        </div>
      </div>
    </div>`).join('');

  for (const button of feed.querySelectorAll('[data-kudos]')) {
    button.addEventListener('click', async () => {
      const given = button.dataset.given === 'true';
      const { error } = await client.rpc(given ? 'remove_kudos' : 'give_kudos', {
        p_workout: button.dataset.kudos,
      });
      if (error) { toast(error.message); return; }
      loadRiders();
    });
  }
  for (const button of feed.querySelectorAll('[data-open]')) {
    button.addEventListener('click', () => go('ride', button.dataset.open));
  }

  if (ridersError) return;
  el('riders').innerHTML = (riders || []).map((rider) => `
    <div class="card">
      <div class="row middle tight">
        ${avatarHtml(rider.name, rider.account_id, 'large')}
        <div>
          <h3>${escapeHtml(rider.name)}${
            rider.is_you ? ' <span class="caption muted">you</span>' : ''
          }</h3>
          <p class="caption muted">${
            rider.share_activity
              ? `${rider.rides} shared ride${Number(rider.rides) === 1 ? '' : 's'}`
              : 'Not sharing their rides'
          }</p>
        </div>
      </div>
      ${rider.bio ? `<p class="small">${escapeHtml(rider.bio)}</p>` : ''}
      ${rider.share_activity && Number(rider.rides) > 0
        ? `<p class="caption muted">${Math.round(rider.output_kj).toLocaleString()} kJ · ` +
          `last rode ${escapeHtml(formatRelativeDay(rider.last_ride))}</p>`
        : ''}
    </div>`).join('');
}

/* --- You ----------------------------------------------------------------- */

function fillProfileForm() {
  if (!profile) return;
  const unit = unitsForRider();
  el('you-header').innerHTML = `${avatarHtml(profile.name, profile.id, 'large')}
    <div><h3>${escapeHtml(profile.name)}</h3>
    <p class="caption muted">${escapeHtml(session.user.email)}</p></div>`;
  el('you-name').value = profile.name || '';
  el('you-bio').value = profile.bio || '';
  el('you-ftp').value = profile.ftp_watts ?? '';
  el('you-weight').value = profile.weight_kg
    ? Number(unit.fromKg(profile.weight_kg).toFixed(1)) : '';
  el('weight-unit').textContent = unit.weightUnit;
  weightFieldUnits = profile.units || 'metric';
  el('you-maxhr').value = profile.max_hr_bpm ?? '';
  el('you-units').value = profile.units || 'metric';
  el('you-sharing').checked = Boolean(profile.share_activity);
  el('you-account').textContent =
    `Signed in as ${session.user.email}. This is the same account the bike uses.`;
  el('you-note').textContent = '';
}

/** Which unit the weight box is currently *displaying* — see below. */
let weightFieldUnits = 'metric';

/**
 * Convert the number in the weight box the moment the picker changes.
 *
 * **Without this, switching to pounds and pressing Save records the metric
 * number as pounds**: the field still holds 72, the save reads it in the newly
 * chosen unit and stores 32.6 kg. Nothing on screen looks wrong — the rider
 * typed nothing and the field never moved — which is the same family as 7.9,
 * where one tap wrote a stale copy of a field nobody had touched. So the
 * conversion happens where the choice is made, and by the time Save runs the
 * box and its label agree.
 */
function switchWeightUnits() {
  const next = el('you-units').value;
  const shown = parseFloat(el('you-weight').value);
  if (Number.isFinite(shown)) {
    const kilos = units(weightFieldUnits).toKg(shown);
    el('you-weight').value = Number(units(next).fromKg(kilos).toFixed(1));
  }
  weightFieldUnits = next;
  el('weight-unit').textContent = units(next).weightUnit;
}

async function saveProfile() {
  const note = el('you-note');
  const chosenUnits = el('you-units').value;
  const unit = units(weightFieldUnits);
  const name = el('you-name').value.trim();
  if (!name) { note.classList.add('error'); note.textContent = 'A name is needed.'; return; }

  const weightEntered = parseFloat(el('you-weight').value);
  const maxHrEntered = parseInt(el('you-maxhr').value, 10);

  // **The weight is read in the unit the box is currently showing**, which is
  // `weightFieldUnits` and not necessarily the one being saved — the picker
  // converts on change (see `switchWeightUnits`), so by here the two agree, and
  // reading `chosenUnits` directly is what would be wrong if it ever did not.
  const row = {
    name,
    bio: el('you-bio').value.trim() || null,
    ftp_watts: parseInt(el('you-ftp').value, 10) || profile.ftp_watts,
    weight_kg: Number.isFinite(weightEntered)
      ? Number(unit.toKg(weightEntered).toFixed(2)) : profile.weight_kg,
    // Absent means *not given* and never a default — a rider with no maximum
    // heart rate gets no zones, which is honest, rather than an age formula
    // nobody asked for (21.1.3).
    max_hr_bpm: Number.isFinite(maxHrEntered) ? maxHrEntered : null,
    units: chosenUnits,
    share_activity: el('you-sharing').checked,
  };

  el('you-save').disabled = true;
  // One tap, one write — 7.9 again, and the reason every field is in this
  // single object rather than in a call each.
  const { data, error } = await client
    .from('profiles').update(row).eq('id', profile.id).select().maybeSingle();
  el('you-save').disabled = false;

  if (error) {
    note.classList.add('error');
    note.textContent = error.message;
    return;
  }

  profile = data;
  note.classList.remove('error');
  note.textContent = 'Saved';
  toast('Saved');
  fillProfileForm();
  render();
  // The units and the FTP change what every other view says, so they are
  // redrawn rather than left to be stale until a reload.
  await loadRides();
}

async function exportData() {
  const button = el('you-export');
  button.disabled = true;
  const { data, error } = await client
    .from('workouts').select('*').eq('user_id', session.user.id)
    .order('recorded_at', { ascending: false });
  button.disabled = false;
  if (error) { toast(error.message); return; }

  const blob = new Blob([JSON.stringify({
    exported_at: new Date().toISOString(),
    profile,
    workouts: data,
  }, null, 2)], { type: 'application/json' });

  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `pelonot-${new Date().toISOString().slice(0, 10)}.json`;
  link.click();
  URL.revokeObjectURL(link.href);
  toast(`${data.length} rides downloaded`);
}
