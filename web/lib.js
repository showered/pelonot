/*
 * The bits every page needs (PLAN 17.1).
 *
 * A classic script rather than an ES module, deliberately: a module `import`
 * from a `file://` page is blocked by CORS, and this app is supposed to open
 * with nothing installed. Serving it is one line of Python when that is wanted;
 * double-clicking it should also work.
 */

/**
 * The Supabase client, or null with the reason said on screen.
 *
 * The failure mode this exists to prevent is the project's oldest one: a cloud
 * problem reported only to a console nobody has open. An unconfigured page says
 * so where the rider is looking.
 */
function pelonotClient(statusElement) {
  const config = window.PELONOT_CONFIG;
  const unset = !config || !config.supabaseUrl || !config.supabaseAnonKey ||
    config.supabaseUrl.includes('YOUR-PROJECT-REF');

  if (unset) {
    if (statusElement) {
      statusElement.innerHTML =
        'This page has no cloud configured. Copy <code>config.example.js</code> ' +
        'to <code>config.js</code> and put your project URL and publishable key in it.';
      statusElement.classList.add('error');
    }
    return null;
  }

  if (!window.supabase || !window.supabase.createClient) {
    if (statusElement) {
      statusElement.textContent =
        'The Supabase library did not load. This page needs a network the first ' +
        'time it is opened.';
      statusElement.classList.add('error');
    }
    return null;
  }

  return window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);
}

/** Seconds as `1:04:30` or `12:05`. */
function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds));
  const hours = Math.floor(s / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;
  const mm = String(minutes).padStart(hours ? 2 : 1, '0');
  return hours
    ? `${hours}:${mm}:${String(seconds).padStart(2, '0')}`
    : `${mm}:${String(seconds).padStart(2, '0')}`;
}

/** Seconds as `3h 12m` — for a total rather than a stopwatch. */
function formatTotalTime(totalSeconds) {
  const minutes = Math.round(Math.max(0, totalSeconds) / 60);
  const hours = Math.floor(minutes / 60);
  return hours ? `${hours}h ${minutes % 60}m` : `${minutes}m`;
}

function formatDate(iso) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? 'unknown date'
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/**
 * `Today`, `Yesterday`, a weekday inside the last week, then a date.
 *
 * A feed is read for *when did this happen* relative to now, and "3 days ago"
 * answers that in a way "12 August" does not. Beyond a week it stops helping —
 * nobody counts 23 days — so it becomes a date again.
 */
function formatRelativeDay(iso) {
  const then = new Date(iso);
  if (Number.isNaN(then.getTime())) return 'unknown date';
  const midnight = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((midnight(new Date()) - midnight(then)) / 86400000);
  const time = then.toLocaleTimeString(undefined, { timeStyle: 'short' });
  if (days <= 0) return `Today, ${time}`;
  if (days === 1) return `Yesterday, ${time}`;
  if (days < 7) return `${then.toLocaleDateString(undefined, { weekday: 'long' })}, ${time}`;
  return then.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

/** Text into HTML. Every rider-authored string goes through this. */
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c]);
}

/* --- Units (PLAN 13) -------------------------------------------------------
 *
 * The rider's preference lives on their cloud profile rather than in this
 * browser, so it is the same answer on a phone and on a laptop. `metric` and
 * `imperial` are the two the column allows.
 *
 * Kilojoules are NOT converted and never will be: kJ is kJ everywhere, and it
 * is the one figure this app's whole audience already reads fluently — Total
 * Output is what Peloton's own leaderboard has ranked them on since the day
 * they bought the bike (26.1.3).
 */

const UNITS = {
  metric: {
    distance: (km) => `${km.toFixed(1)} km`,
    distanceValue: (km) => km.toFixed(1),
    distanceUnit: 'km',
    weight: (kg) => `${kg.toFixed(1)} kg`,
    weightUnit: 'kg',
    toKg: (value) => value,
    fromKg: (kg) => kg,
  },
  imperial: {
    distance: (km) => `${(km * 0.621371).toFixed(1)} mi`,
    distanceValue: (km) => (km * 0.621371).toFixed(1),
    distanceUnit: 'mi',
    weight: (kg) => `${(kg * 2.20462).toFixed(1)} lb`,
    weightUnit: 'lb',
    toKg: (value) => value / 2.20462,
    fromKg: (kg) => kg * 2.20462,
  },
};

function units(preference) {
  return UNITS[preference] || UNITS.metric;
}

/* --- Zones -----------------------------------------------------------------
 *
 * Transcribed from `domain/model/PowerZone.kt` and `HeartRateZone.kt`, with
 * the same two properties that matter: the bounds are **contiguous and
 * half-open**, so a rider at 55.5% of FTP is in a zone rather than in none, and
 * the two ramps are deliberately different — five heart-rate zones against
 * seven power ones, on different boundaries, because telling a rider that HR
 * zone 4 and power zone 4 are the same statement about them is false (21.2.1).
 */

const POWER_ZONES = [
  { key: 'p1', number: 1, name: 'Active Recovery', lower: 0.00, upper: 0.56 },
  { key: 'p2', number: 2, name: 'Endurance', lower: 0.56, upper: 0.76 },
  { key: 'p3', number: 3, name: 'Tempo', lower: 0.76, upper: 0.91 },
  { key: 'p4', number: 4, name: 'Lactate Threshold', lower: 0.91, upper: 1.06 },
  { key: 'p5', number: 5, name: 'VO2 Max', lower: 1.06, upper: 1.21 },
  { key: 'p6', number: 6, name: 'Anaerobic Capacity', lower: 1.21, upper: 1.51 },
  { key: 'p7', number: 7, name: 'Neuromuscular Power', lower: 1.51, upper: Infinity },
];

const HR_ZONES = [
  { key: 'h1', number: 1, name: 'Recovery', lower: 0.00, upper: 0.60 },
  { key: 'h2', number: 2, name: 'Aerobic', lower: 0.60, upper: 0.70 },
  { key: 'h3', number: 3, name: 'Tempo', lower: 0.70, upper: 0.80 },
  { key: 'h4', number: 4, name: 'Threshold', lower: 0.80, upper: 0.90 },
  { key: 'h5', number: 5, name: 'Maximum', lower: 0.90, upper: Infinity },
];

function zoneFor(zones, value, reference) {
  if (!reference || reference <= 0 || !value || value <= 0) return null;
  const fraction = value / reference;
  let found = null;
  for (const zone of zones) if (fraction >= zone.lower) found = zone;
  return found;
}

/**
 * Seconds in each zone, and the seconds that were not ridden.
 *
 * **A stop is not Active Recovery** (19.1.2c). The app settled this: one
 * definition of *was the rider pedalling*, `AutoPausePolicy.PEDALLING_RPM`,
 * and the seconds below it are counted separately rather than filed as the
 * easiest zone — which was the largest thing on the card until it was fixed.
 */
const PEDALLING_RPM = 1.0;

function timeInZone(samples, zones, key, reference) {
  const totals = new Map(zones.map((z) => [z.key, 0]));
  let stopped = 0;
  let counted = 0;

  for (const sample of samples) {
    if (key === 'power' && (sample.cadence ?? 0) < PEDALLING_RPM) {
      stopped += 1;
      continue;
    }
    const value = sample[key];
    if (value === null || value === undefined) continue;
    const zone = zoneFor(zones, value, reference);
    if (!zone) continue;
    totals.set(zone.key, totals.get(zone.key) + 1);
    counted += 1;
  }

  return { totals, stopped, counted };
}

/**
 * A ride's samples out of `metrics_payload`, in either shape it can be in
 * (PLAN 14.4.3).
 *
 * **An absent `v` means the pre-14.4 array-of-objects** — one row in the cloud
 * is still in that shape, and a reader that assumed columns would silently draw
 * it as an empty ride. The version travelling *inside* the payload rather than
 * beside it is what makes this decidable at all.
 *
 * Returns `[]` rather than throwing on anything unrecognised: an empty chart
 * with the ride's own totals above it is a worse page but an honest one, and
 * the totals are columns on the row rather than derived from here.
 */
function decodeMetrics(payload) {
  if (!payload) return [];

  // Pre-14.4: an array of {timestamp_sec, cadence, resistance, power, heart_rate}.
  if (Array.isArray(payload)) {
    return payload.map((sample) => ({
      t: sample.timestamp_sec ?? 0,
      cadence: sample.cadence ?? 0,
      resistance: sample.resistance ?? 0,
      power: sample.power ?? 0,
      heartRate: sample.heart_rate ?? null,
      measured: normaliseProvenance(sample.power_is_measured ?? null),
    }));
  }

  const t = payload.t || [];
  // Columns of different lengths mean the payload was truncated or written by
  // something that did not understand it, and sample 900's power would line up
  // with sample 900's cadence only by luck. Reject, do not repair — the same
  // rule the Android side applies in MetricsPayload.toMetrics.
  const columns = [payload.c, payload.r, payload.p, payload.hr, payload.pm];
  const ragged = columns.some((column) => column && column.length !== t.length);
  if (ragged) return [];

  return t.map((seconds, i) => ({
    t: seconds,
    cadence: payload.c ? payload.c[i] : 0,
    resistance: payload.r ? payload.r[i] : 0,
    power: payload.p ? payload.p[i] : 0,
    heartRate: payload.hr ? payload.hr[i] : null,
    // **`pm` is `1` and `0`, not `true` and `false`** — `MetricsPayload
    // .CompactBoolean` writes digits, because `true` across 2,700 samples is
    // 13 KB on a 49 KB payload and puts the ride over its budget. This file
    // compared it against `true` for as long as it has existed, so every real
    // ride's caption read *"this ride does not record where its watts came
    // from"* — a ride whose watts the board measured, saying it could not say.
    // Anything non-zero is measured, which is the same rule the Kotlin side
    // states for a future writer that emits JSON's own booleans.
    measured: normaliseProvenance(payload.pm ? payload.pm[i] : null),
  }));
}

function normaliseProvenance(value) {
  if (value === null || value === undefined) return null;
  return Boolean(value);
}

/**
 * What resolution a stored series is (PLAN 23.4.3, 23.4.14).
 *
 * A ride the rider asked the bike to condense holds the lowest and highest watt
 * of each ten seconds instead of every second. The line has the same shape, the
 * same peak and the same axis either way, which is exactly why anything drawing
 * it has to say which it is looking at rather than letting it pass as a full
 * trace. It travels inside the payload as `d` — the one-letter names are the
 * point of the columnar shape — and absent means intact.
 */
function detailSeconds(payload) {
  return payload && !Array.isArray(payload) ? (payload.d ?? null) : null;
}

/**
 * One trace, drawn straight into an SVG, with the zone bands behind it.
 *
 * **A gap in the series is drawn as a gap** (16.1.2). The bike stops recording
 * when the board goes quiet or the rider pauses, and a line joined across that
 * absence is a claim about seconds nobody measured. Any step of more than two
 * seconds starts a new sub-path.
 *
 * @param options.zones     the zone table to band the background with
 * @param options.reference FTP for power, maximum heart rate for a pulse —
 *                          absent means no bands, which is the honest answer
 *                          for a rider who has never given the app the number
 */
function drawTrace(svg, samples, key, colour, options = {}) {
  const width = 720;
  const height = svg.classList.contains('tall') ? 180 : 140;
  const pad = 6;
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.innerHTML = '';

  const points = samples
    .map((s) => ({ t: s.t, v: s[key] }))
    .filter((p) => p.v !== null && p.v !== undefined);
  if (points.length < 2) return false;

  const maxT = points[points.length - 1].t || 1;
  const maxV = Math.max(...points.map((p) => p.v)) || 1;

  const x = (t) => pad + (t / maxT) * (width - pad * 2);
  const y = (v) => height - pad - (v / maxV) * (height - pad * 2);
  const svgNode = (name) => document.createElementNS('http://www.w3.org/2000/svg', name);

  // The bands go down first so the trace is drawn over them, and they are
  // clipped to the top of the chart rather than to the zone above the data —
  // a band drawn past the axis says the rider was somewhere they were not.
  const { zones, reference } = options;
  if (zones && reference > 0) {
    for (const zone of zones) {
      const low = zone.lower * reference;
      const high = Number.isFinite(zone.upper) ? zone.upper * reference : maxV;
      if (low >= maxV) continue;
      const top = y(Math.min(high, maxV));
      const bottom = y(low);
      const band = svgNode('rect');
      band.setAttribute('x', '0');
      band.setAttribute('y', top.toFixed(1));
      band.setAttribute('width', String(width));
      band.setAttribute('height', Math.max(0, bottom - top).toFixed(1));
      band.setAttribute('fill', `var(--zone-${zone.key})`);
      band.setAttribute('opacity', '0.14');
      svg.appendChild(band);
    }
  }

  let d = '';
  let previousT = null;
  for (const p of points) {
    const broken = previousT !== null && p.t - previousT > 2;
    d += `${d === '' || broken ? 'M' : 'L'}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)} `;
    previousT = p.t;
  }

  const path = svgNode('path');
  path.setAttribute('d', d.trim());
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', colour);
  path.setAttribute('stroke-width', '2');
  path.setAttribute('stroke-linejoin', 'round');
  path.setAttribute('vector-effect', 'non-scaling-stroke');
  svg.appendChild(path);
  return true;
}

/**
 * A bar per week, for the last twelve of them.
 *
 * Twelve weeks rather than one, because 22.5 settled that a week is the wrong
 * window for somebody who rides once of them: an empty week says nothing about
 * a rider and a quarter says quite a lot.
 */
function drawWeeklyBars(svg, weeks) {
  const width = 720;
  const height = 120;
  const gap = 6;
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.innerHTML = '';
  if (!weeks.length) return;

  const max = Math.max(...weeks.map((w) => w.kj), 1);
  const barWidth = (width - gap * (weeks.length - 1)) / weeks.length;

  weeks.forEach((week, i) => {
    const barHeight = Math.max(2, (week.kj / max) * (height - 4));
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('x', (i * (barWidth + gap)).toFixed(1));
    rect.setAttribute('y', (height - barHeight).toFixed(1));
    rect.setAttribute('width', barWidth.toFixed(1));
    rect.setAttribute('height', barHeight.toFixed(1));
    rect.setAttribute('rx', '2');
    rect.setAttribute('fill', week.kj ? 'var(--accent)' : 'var(--surface-3)');
    const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
    title.textContent = `${week.label}: ${Math.round(week.kj)} kJ, ${week.rides} ride${week.rides === 1 ? '' : 's'}`;
    rect.appendChild(title);
    svg.appendChild(rect);
  });
}

/*
 * Avatars, and they are the same idea as the app's (`ProfileSelectorScreen`):
 * a deterministic colour and an initial, so a rider keeps one face without
 * anybody uploading a photograph. The palette is the app's five; what differs
 * is what indexes it — the app has a local profile id and the cloud has an
 * account uuid, so this hashes the uuid. Two surfaces can therefore give one
 * rider two colours, which is a thing to know rather than a defect to chase:
 * the id the app uses does not exist up here at all.
 */
const AVATAR_COLOURS = [
  'var(--zone-p2)', 'var(--zone-p3)', 'var(--zone-p4)',
  'var(--zone-p5)', 'var(--zone-p6)',
];

function avatarColour(id) {
  let hash = 0;
  for (const character of String(id)) hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  return AVATAR_COLOURS[hash % AVATAR_COLOURS.length];
}

function avatarHtml(name, id, extraClass = '') {
  const initial = (String(name || '?').trim()[0] || '?').toUpperCase();
  return `<div class="avatar ${extraClass}" style="background:${avatarColour(id)}">` +
    `${escapeHtml(initial)}</div>`;
}

/**
 * Where a ride's watts came from, in one sentence (16.1.6, 14.4.7).
 *
 * A modelled watt must never be presented as a measured one — the shipped power
 * curve scores RMSE 137 W against the real board — so anything showing a number
 * derived from it says so, including when the answer is "both" and when it is
 * "nobody wrote it down", which are different claims.
 */
function provenanceSentence(samples, rowProvenance) {
  if (samples.length === 0) {
    return rowProvenance === 'Measured' ? 'Power measured by the bike.'
      : rowProvenance === 'Modelled' ? 'Power estimated from cadence and resistance.'
      : 'No series was stored with this ride.';
  }
  const measured = samples.filter((s) => s.measured === true).length;
  const modelled = samples.filter((s) => s.measured === false).length;
  const unknown = samples.length - measured - modelled;
  if (modelled === 0 && unknown === 0) return 'Power measured by the bike.';
  if (measured === 0 && unknown === 0) return 'Power estimated from cadence and resistance.';
  if (unknown === samples.length) return 'This ride does not record where its watts came from.';
  return `Power measured for ${measured} of ${samples.length} samples; the rest is ` +
    'estimated or unrecorded.';
}
