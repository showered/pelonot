/*
 * The bits both pages need (PLAN 17.1).
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

function formatDate(iso) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? 'unknown date'
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
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
      measured: sample.power_is_measured ?? null,
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
    measured: payload.pm ? payload.pm[i] : null,
  }));
}

/**
 * One trace, drawn straight into an SVG.
 *
 * **A gap in the series is drawn as a gap** (16.1.2). The bike stops recording
 * when the board goes quiet or the rider pauses, and a line joined across that
 * absence is a claim about seconds nobody measured. Any step of more than two
 * seconds starts a new sub-path.
 */
function drawTrace(svg, samples, key, colour) {
  const width = 720;
  const height = 140;
  const pad = 6;
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.innerHTML = '';

  const points = samples
    .map((s) => ({ t: s.t, v: s[key] }))
    .filter((p) => p.v !== null && p.v !== undefined);
  if (points.length < 2) return;

  const maxT = points[points.length - 1].t || 1;
  const maxV = Math.max(...points.map((p) => p.v)) || 1;

  const x = (t) => pad + (t / maxT) * (width - pad * 2);
  const y = (v) => height - pad - (v / maxV) * (height - pad * 2);

  let d = '';
  let previousT = null;
  for (const p of points) {
    const broken = previousT !== null && p.t - previousT > 2;
    d += `${d === '' || broken ? 'M' : 'L'}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)} `;
    previousT = p.t;
  }

  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', d.trim());
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', colour);
  path.setAttribute('stroke-width', '2');
  path.setAttribute('stroke-linejoin', 'round');
  path.setAttribute('vector-effect', 'non-scaling-stroke');
  svg.appendChild(path);
}
