/*
 * The companion app's detailed view. It reads the rider's own cloud rides only
 * when opened, in pages, so the ordinary ride list stays quick. Summary
 * figures use workout columns; sample-derived figures use the ride's own
 * recorded FTP/maximum and the saved distributions of condensed rides.
 */

const INSIGHTS_PAGE_SIZE = 50;
const insightsCache = new Map();
let insightsRange = '12m';
let insightsMetric = 'output';
let insightsModel = null;
let insightsRequest = 0;

function clearInsights() {
  insightsRequest += 1;
  insightsCache.clear();
  insightsModel = null;
}

function insightStart(range, today = new Date()) {
  if (range === 'all') return null;
  if (range === '12w') {
    const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7) - 77);
    return monday;
  }
  return new Date(today.getFullYear(), today.getMonth() - 11, 1);
}

function wireInsights() {
  for (const button of document.querySelectorAll('[data-insights-range]')) {
    button.addEventListener('click', () => {
      insightsRange = button.dataset.insightsRange;
      for (const option of document.querySelectorAll('[data-insights-range]')) {
        option.setAttribute('aria-pressed', String(option === button));
      }
      loadInsights();
    });
  }
  for (const button of document.querySelectorAll('[data-insights-metric]')) {
    button.addEventListener('click', () => {
      insightsMetric = button.dataset.insightsMetric;
      for (const option of document.querySelectorAll('[data-insights-metric]')) {
        option.setAttribute('aria-pressed', String(option === button));
      }
      if (insightsModel) drawInsightTrend(insightsModel);
    });
  }
  el('insights-refresh').addEventListener('click', () => loadInsights(true));
}

async function loadInsights(refresh = false) {
  if (!session) return;
  const userId = session.user.id;
  const range = insightsRange;
  const cacheKey = `${userId}:${range}`;
  const request = ++insightsRequest;
  const note = el('insights-status');
  const content = el('insights-content');
  note.classList.remove('error');
  if (refresh) insightsCache.delete(cacheKey);

  let rows = insightsCache.get(cacheKey);
  if (!rows) {
    content.classList.add('hidden');
    note.textContent = 'Loading your ride details…';
    rows = [];
    const lower = insightStart(range);
    for (let from = 0; ; from += INSIGHTS_PAGE_SIZE) {
      let query = client.from('workouts')
        .select('id, title, class_id, recorded_at, duration_sec, total_output_kj, ' +
          'total_distance_km, avg_power, avg_cadence, avg_hr, power_provenance, ' +
          'rpe_rating, metrics_payload, class_templates(title, category)')
        .eq('user_id', userId)
        .order('recorded_at', { ascending: false })
        .order('id', { ascending: false })
        .range(from, from + INSIGHTS_PAGE_SIZE - 1);
      if (lower) query = query.gte('recorded_at', lower.toISOString());
      const { data, error } = await query;
      if (request !== insightsRequest || !session || session.user.id !== userId) return;
      if (error) {
        note.classList.add('error');
        note.textContent = `Could not load your ride details: ${error.message}. Try Refresh.`;
        return;
      }
      rows.push(...(data || []));
      note.textContent = `Loading your ride details… ${rows.length} rides`;
      if (!data || data.length < INSIGHTS_PAGE_SIZE) break;
    }
    insightsCache.set(cacheKey, rows);
  }
  if (request !== insightsRequest || !session || session.user.id !== userId ||
      currentRoute().name !== 'insights') return;

  insightsModel = aggregateInsights(rows, range, new Date(), decodeMetrics,
    POWER_ZONES, HR_ZONES);
  if (!rows.length) {
    content.classList.add('hidden');
    note.textContent = 'No backed-up rides in this range yet. Try a wider range.';
    return;
  }
  note.textContent = `${rows.length.toLocaleString()} backed-up rides · ${
    range === '12w' ? '12 weeks' : range === '12m' ? 'year' : 'all time'}`;
  content.classList.remove('hidden');
  drawInsights(insightsModel);
}

function insightNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 0;
}

function insightDay(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${
    String(date.getDate()).padStart(2, '0')}`;
}

function insightMonth(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function insightMonday(date) {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  return insightDay(monday);
}

function insightBuckets(rows, range, today, measuredRides = null) {
  const byWeek = range === '12w';
  const first = insightStart(range, today) ||
    (rows.length ? new Date(rows[0].recorded_at) : new Date(today));
  const start = byWeek ? new Date(`${insightMonday(first)}T00:00:00`)
    : new Date(first.getFullYear(), first.getMonth(), 1);
  const buckets = [];
  const cursor = new Date(start);
  while (cursor <= today && buckets.length < 600) {
    const key = byWeek ? insightDay(cursor) : insightMonth(cursor);
    buckets.push({ key, date: new Date(cursor), output: 0, time: 0, rides: 0,
      measuredPowerTotal: 0, measuredPowerRides: 0 });
    if (byWeek) cursor.setDate(cursor.getDate() + 7);
    else cursor.setMonth(cursor.getMonth() + 1);
  }
  const lookup = new Map(buckets.map((bucket) => [bucket.key, bucket]));
  for (const ride of rows) {
    const date = new Date(ride.recorded_at);
    const bucket = lookup.get(byWeek ? insightMonday(date) : insightMonth(date));
    if (!bucket) continue;
    bucket.output += insightNumber(ride.total_output_kj);
    bucket.time += insightNumber(ride.duration_sec);
    bucket.rides += 1;
    if ((measuredRides ? measuredRides.has(ride.id) :
        ride.power_provenance === 'Measured') && insightNumber(ride.avg_power)) {
      bucket.measuredPowerTotal += insightNumber(ride.avg_power);
      bucket.measuredPowerRides += 1;
    }
  }
  return buckets;
}

function insightZone(zones, value, reference) {
  if (!reference || !value) return null;
  const fraction = value / reference;
  let found = null;
  for (const zone of zones) if (fraction >= zone.lower) found = zone.key;
  return found;
}

function addStoredZones(target, stored, prefix) {
  if (!stored || typeof stored !== 'object') return false;
  let any = false;
  for (const [key, seconds] of Object.entries(stored)) {
    const normal = String(key).toLowerCase();
    if (!new RegExp(`^${prefix}[1-7]$`).test(normal)) continue;
    const targetKey = `${prefix === 'z' ? 'p' : 'h'}${normal.slice(1)}`;
    if (!target.has(targetKey)) continue;
    const amount = insightNumber(seconds);
    target.set(targetKey, (target.get(targetKey) || 0) + amount);
    any = any || amount > 0;
  }
  return any;
}

function aggregateInsights(input, range, today, decode, powerZones, heartZones) {
  const rows = input.filter((ride) => !Number.isNaN(new Date(ride.recorded_at).getTime()))
    .sort((a, b) => new Date(a.recorded_at) - new Date(b.recorded_at));
  const powerSeconds = new Map(powerZones.map((zone) => [zone.key, 0]));
  const heartSeconds = new Map(heartZones.map((zone) => [zone.key, 0]));
  const cadenceSeconds = new Map();
  const categories = new Map();
  const measuredRides = new Set();
  const days = new Map();
  const weekdays = Array(7).fill(0);
  const coverage = { measured: 0, condensed: 0, samples: 0, powerZones: 0,
    heartZones: 0, heartSamples: 0 };
  let duration = 0;
  let output = 0;
  let distance = 0;
  let longest = null;
  let biggest = null;
  let highestAverage = null;
  let peakPower = null;

  for (const ride of rows) {
    const date = new Date(ride.recorded_at);
    const seconds = insightNumber(ride.duration_sec);
    const kj = insightNumber(ride.total_output_kj);
    duration += seconds;
    output += kj;
    distance += insightNumber(ride.total_distance_km);
    const day = insightDay(date);
    const aggregate = days.get(day) || { output: 0, rides: 0 };
    aggregate.output += kj;
    aggregate.rides += 1;
    days.set(day, aggregate);
    weekdays[(date.getDay() + 6) % 7] += 1;
    const category = (ride.class_templates && ride.class_templates.category) || 'Free ride';
    const cat = categories.get(category) || { rides: 0, time: 0 };
    cat.rides += 1;
    cat.time += seconds;
    categories.set(category, cat);
    if (!longest || seconds > insightNumber(longest.duration_sec)) longest = ride;

    const payload = ride.metrics_payload;
    const facts = payload && !Array.isArray(payload) ? (payload.w || {}) : {};
    const dist = facts.dist || {};
    const condensed = Boolean(payload && !Array.isArray(payload) && payload.d);
    const samples = payload ? decode(payload) : [];
    if (samples.length) coverage.samples += 1;
    if (condensed) coverage.condensed += 1;
    if (samples.some((sample) => insightNumber(sample.heartRate))) coverage.heartSamples += 1;
    const measured = ride.power_provenance == null
      ? samples.length > 0 && samples.every((sample) => sample.measured === true)
      : ride.power_provenance === 'Measured';
    if (measured) {
      measuredRides.add(ride.id);
      coverage.measured += 1;
      if (!biggest || kj > insightNumber(biggest.total_output_kj)) biggest = ride;
      if (insightNumber(ride.avg_power) && (!highestAverage ||
          insightNumber(ride.avg_power) > insightNumber(highestAverage.avg_power))) {
        highestAverage = ride;
      }
      for (const sample of samples) {
        const watts = insightNumber(sample.power);
        if (watts && (!peakPower || watts > peakPower.watts)) peakPower = { ride, watts };
      }
    }

    const ftp = insightNumber(facts.ftp) || insightNumber(dist.ftp_watts);
    const maxHr = insightNumber(facts.mhr) || insightNumber(dist.max_hr_bpm);
    let countedPower = false;
    let countedHeart = false;
    if (condensed) {
      if (measured && ftp) countedPower = addStoredZones(powerSeconds, dist.seconds_by_zone, 'z');
      if (maxHr) countedHeart = addStoredZones(heartSeconds, dist.seconds_by_hr_zone, 'h');
      for (const [band, count] of Object.entries(dist.seconds_by_cadence_band || {})) {
        const index = Number(band);
        if (!Number.isInteger(index) || index < 0 || index > 30) continue;
        cadenceSeconds.set(index, (cadenceSeconds.get(index) || 0) + insightNumber(count));
      }
    } else {
      for (const sample of samples) {
        const cadence = insightNumber(sample.cadence);
        if (cadence >= PEDALLING_RPM) {
          const band = Math.floor(cadence / 10);
          cadenceSeconds.set(band, (cadenceSeconds.get(band) || 0) + 1);
        }
        if (measured && ftp && cadence >= PEDALLING_RPM) {
          const key = insightZone(powerZones, insightNumber(sample.power), ftp);
          if (key) {
            powerSeconds.set(key, powerSeconds.get(key) + 1);
            countedPower = true;
          }
        }
        if (maxHr) {
          const key = insightZone(heartZones, insightNumber(sample.heartRate), maxHr);
          if (key) {
            heartSeconds.set(key, heartSeconds.get(key) + 1);
            countedHeart = true;
          }
        }
      }
    }
    if (countedPower) coverage.powerZones += 1;
    if (countedHeart) coverage.heartZones += 1;
  }

  const durations = rows.map((ride) => insightNumber(ride.duration_sec)).sort((a, b) => a - b);
  const middle = Math.floor(durations.length / 2);
  const medianDuration = durations.length
    ? (durations.length % 2 ? durations[middle] : (durations[middle - 1] + durations[middle]) / 2)
    : 0;
  return { rows, range, today, duration, output, distance, medianDuration,
    days, weekdays, categories, powerSeconds, heartSeconds, cadenceSeconds,
    coverage, longest, biggest, highestAverage, peakPower,
    buckets: insightBuckets(rows, range, today, measuredRides) };
}

function insightSvgNode(name, attributes = {}) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', name);
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, String(value));
  return node;
}

function insightSvgSetup(element, width, height, label) {
  element.innerHTML = '';
  element.setAttribute('viewBox', `0 0 ${width} ${height}`);
  element.setAttribute('aria-label', label);
  element.style.width = `${width}px`;
}

function insightSvgText(element, x, y, value, anchor = 'start') {
  const label = insightSvgNode('text', { x, y, 'text-anchor': anchor, class: 'insights-axis' });
  label.textContent = value;
  element.appendChild(label);
}

function insightSvgTitle(element, value) {
  const title = insightSvgNode('title');
  title.textContent = value;
  element.appendChild(title);
}

function insightRideTitle(ride) {
  return ride.title || (ride.class_templates && ride.class_templates.title) || 'Free ride';
}

function insightCompact(value) {
  return Math.round(value).toLocaleString();
}

function drawInsights(model) {
  const unit = unitsForRider();
  const stats = [
    ['Rides', insightCompact(model.rows.length), 'backed up'],
    ['Ride time', formatTotalTime(model.duration), 'as recorded'],
    ['Output', `${insightCompact(model.output)} kJ`, 'as recorded'],
    ['Distance', unit.distance(model.distance), 'as recorded'],
    ['Active days', insightCompact(model.days.size), 'distinct dates'],
    ['Median ride', formatDuration(model.medianDuration), 'middle ride length'],
  ];
  el('insights-kpis').innerHTML = stats.map(([label, value, detail]) => `
    <div class="tile">
      <p class="figure-label">${escapeHtml(label)}</p>
      <p class="figure">${escapeHtml(value)}</p>
      <p class="caption muted">${escapeHtml(detail)}</p>
    </div>`).join('');
  drawInsightTrend(model);
  drawInsightCalendar(model);
  drawInsightPowerTrend(model);
  drawInsightWeekdays(model);
  drawInsightZones(el('insights-power-zones'), POWER_ZONES, model.powerSeconds);
  drawInsightZones(el('insights-hr-zones'), HR_ZONES, model.heartSeconds);
  el('insights-power-zones-note').textContent = model.coverage.powerZones
    ? `${model.coverage.powerZones} measured rides with the ride's own FTP. ` +
      'Stopped seconds are excluded.'
    : 'No measured rides with a recorded FTP and usable zone counts in this range.';
  el('insights-hr-zones-note').textContent = model.coverage.heartZones
    ? `${model.coverage.heartZones} rides with a recorded maximum heart rate. ` +
      'Seconds without a heart-rate reading are excluded.'
    : 'No rides with a recorded maximum heart rate and usable zone counts in this range.';
  drawInsightCadence(model);
  drawInsightCategories(model);
  drawInsightRecords(model);
  drawInsightCoverage(model);
}

function insightBucketLabel(bucket, range) {
  return bucket.date.toLocaleDateString(undefined, range === '12w'
    ? { month: 'short', day: 'numeric' } : { month: 'short', year: '2-digit' });
}

function drawInsightTrend(model) {
  const { buckets, range } = model;
  const svg = el('insights-trend');
  const width = Math.max(700, buckets.length * 46 + 78);
  const height = 220;
  const left = 58;
  const right = 14;
  const top = 22;
  const bottom = 36;
  const graphHeight = height - top - bottom;
  const graphWidth = width - left - right;
  const metric = insightsMetric;
  const max = Math.max(1, ...buckets.map((bucket) => bucket[metric]));
  const values = { output: 'Output', time: 'Ride time', rides: 'Rides' };
  el('insights-trend-title').textContent = values[metric];
  insightSvgSetup(svg, width, height, `${values[metric]} over time`);
  for (let i = 0; i <= 3; i += 1) {
    const value = max * (3 - i) / 3;
    const y = top + (graphHeight * i) / 3;
    svg.appendChild(insightSvgNode('line', { x1: left, y1: y, x2: width - right,
      y2: y, class: 'insights-gridline' }));
    insightSvgText(svg, left - 8, y + 4, metric === 'time'
      ? `${Math.round(value / 3600)}h` : insightCompact(value), 'end');
  }
  const step = graphWidth / Math.max(1, buckets.length);
  buckets.forEach((bucket, index) => {
    const value = bucket[metric];
    const barHeight = value ? Math.max(3, value / max * graphHeight) : 0;
    const x = left + index * step + step * 0.18;
    const rect = insightSvgNode('rect', { x, y: top + graphHeight - barHeight,
      width: Math.max(2, step * 0.64), height: barHeight,
      rx: 3, class: 'insights-trend-bar' });
    insightSvgTitle(rect, `${insightBucketLabel(bucket, range)}: ${
      insightCompact(bucket.output)} kJ, ${formatTotalTime(bucket.time)}, ` +
      `${bucket.rides} ${bucket.rides === 1 ? 'ride' : 'rides'}`);
    svg.appendChild(rect);
    const labelEvery = buckets.length > 30 ? 6 : buckets.length > 14 ? 3 : 1;
    if (index % labelEvery === 0 || index === buckets.length - 1) {
      insightSvgText(svg, x + step * 0.32, height - 12,
        insightBucketLabel(bucket, range), 'middle');
    }
  });
  el('insights-trend-note').textContent = range === '12w'
    ? 'One bar per week, Monday to Sunday.' : 'One bar per calendar month.';
}

function drawInsightCalendar(model) {
  const today = new Date(model.today.getFullYear(), model.today.getMonth(), model.today.getDate());
  const start = model.range === 'all'
    ? new Date(today.getFullYear(), today.getMonth() - 11, 1)
    : insightStart(model.range, today);
  const monday = new Date(start);
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  const totalDays = Math.round((today - monday) / 86400000) + 1;
  const weeks = Math.ceil(totalDays / 7);
  const width = Math.max(680, 42 + weeks * 15);
  const svg = el('insights-calendar');
  insightSvgSetup(svg, width, 148, 'Calendar of ride days and recorded output');
  const max = Math.max(1, ...[...model.days.values()].map((day) => day.output));
  let lastMonth = -1;
  const cursor = new Date(monday);
  for (let i = 0; i < totalDays; i += 1) {
    const week = Math.floor(i / 7);
    const weekday = i % 7;
    if (cursor >= start && cursor.getMonth() !== lastMonth) {
      lastMonth = cursor.getMonth();
      insightSvgText(svg, 42 + week * 15, 15,
        cursor.toLocaleDateString(undefined, { month: 'short' }));
    }
    const data = model.days.get(insightDay(cursor));
    const level = data ? Math.max(1, Math.ceil((data.output / max) * 4)) : 0;
    const rect = insightSvgNode('rect', { x: 42 + week * 15, y: 28 + weekday * 15,
      width: 12, height: 12, rx: 2, class: `insights-heat-${level}` });
    insightSvgTitle(rect, `${cursor.toLocaleDateString(undefined, { dateStyle: 'full' })}: ` +
      `${data ? data.rides : 0} ${data && data.rides === 1 ? 'ride' : 'rides'}, ` +
      `${data ? insightCompact(data.output) : 0} kJ`);
    svg.appendChild(rect);
    cursor.setDate(cursor.getDate() + 1);
  }
  insightSvgText(svg, 34, 38, 'Mon', 'end');
  insightSvgText(svg, 34, 83, 'Thu', 'end');
  insightSvgText(svg, 34, 128, 'Sun', 'end');
  el('insights-calendar-caption').textContent = model.range === 'all'
    ? 'Last 12 months shown' : model.range === '12w' ? 'Last 12 weeks' : 'Last 12 calendar months';
}

function drawInsightPowerTrend(model) {
  const points = model.buckets.filter((bucket) => bucket.measuredPowerRides > 0);
  const svg = el('insights-power-trend');
  const width = Math.max(440, model.buckets.length * 34 + 58);
  const height = 165;
  insightSvgSetup(svg, width, height, 'Average power on measured rides by period');
  if (!points.length) {
    el('insights-power-trend-note').textContent = 'No measured rides with average power in this range.';
    return;
  }
  const max = Math.max(...points.map((point) => point.measuredPowerTotal /
    point.measuredPowerRides), 1);
  const left = 42;
  const top = 15;
  const graphHeight = 115;
  for (let i = 0; i <= 2; i += 1) {
    const y = top + (graphHeight * i) / 2;
    svg.appendChild(insightSvgNode('line', { x1: left, y1: y, x2: width - 10,
      y2: y, class: 'insights-gridline' }));
    insightSvgText(svg, left - 6, y + 4, `${Math.round(max * (2 - i) / 2)} W`, 'end');
  }
  const step = (width - left - 12) / Math.max(1, model.buckets.length);
  model.buckets.forEach((bucket, index) => {
    if (!bucket.measuredPowerRides) return;
    const average = bucket.measuredPowerTotal / bucket.measuredPowerRides;
    const dot = insightSvgNode('circle', { cx: left + index * step + step / 2,
      cy: top + graphHeight - (average / max) * graphHeight,
      r: 4, class: 'insights-power-dot' });
    insightSvgTitle(dot, `${insightBucketLabel(bucket, model.range)}: ` +
      `${Math.round(average)} W mean of ${bucket.measuredPowerRides} measured ` +
      `${bucket.measuredPowerRides === 1 ? 'ride' : 'rides'}`);
    svg.appendChild(dot);
  });
  insightSvgText(svg, left, height - 10, insightBucketLabel(model.buckets[0], model.range));
  insightSvgText(svg, width - 10, height - 10,
    insightBucketLabel(model.buckets[model.buckets.length - 1], model.range), 'end');
  el('insights-power-trend-note').textContent =
    'Mean of ride averages in each period; measured rides only. A change in ride mix can move these points.';
}

function drawInsightWeekdays(model) {
  const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const max = Math.max(1, ...model.weekdays);
  el('insights-weekdays').innerHTML = model.weekdays.map((count, index) => `
    <div class="insights-weekday" title="${names[index]}: ${count} rides">
      <span class="insights-weekday-value">${count || '—'}</span>
      <span class="insights-weekday-track"><span style="height:${
        count ? Math.max(5, count / max * 100) : 0}%"></span></span>
      <span class="caption muted">${names[index]}</span>
    </div>`).join('');
}

function drawInsightZones(container, zones, counts) {
  const total = [...counts.values()].reduce((sum, seconds) => sum + seconds, 0);
  if (!total) {
    container.innerHTML = '<p class="small muted">No comparable zone seconds yet.</p>';
    return;
  }
  container.innerHTML = `<div class="insights-zone-stack" aria-label="Zone proportions">${
    zones.map((zone) => {
      const seconds = counts.get(zone.key) || 0;
      return seconds ? `<span style="width:${seconds / total * 100}%;` +
        `background:var(--zone-${zone.key})" title="${escapeHtml(zone.name)}: ` +
        `${formatTotalTime(seconds)}"></span>` : '';
    }).join('')}</div>` + zones.map((zone) => {
      const seconds = counts.get(zone.key) || 0;
      const share = Math.round(seconds / total * 100);
      return `<div class="insights-zone-row">
        <span class="insights-zone-name"><span class="zone-swatch" ` +
        `style="background:var(--zone-${zone.key})"></span>` +
        `${zone.key.toUpperCase()} · ${escapeHtml(zone.name)}</span>
        <span>${formatTotalTime(seconds)}</span><span class="muted">${share}%</span>
      </div>`;
    }).join('');
}

function drawInsightCadence(model) {
  const container = el('insights-cadence');
  const bands = [...model.cadenceSeconds.entries()]
    .filter(([, seconds]) => seconds > 0).sort((a, b) => a[0] - b[0]);
  if (!bands.length) {
    container.innerHTML = '<p class="small muted">No cadence samples in this range.</p>';
    el('insights-cadence-note').textContent = '';
    return;
  }
  const max = Math.max(...bands.map(([, seconds]) => seconds));
  const first = bands[0][0];
  const last = bands[bands.length - 1][0];
  const counts = new Map(bands);
  container.innerHTML = '<div class="insights-cadence-scroll"><div class="insights-cadence-bars">' +
    Array.from({ length: last - first + 1 }, (_, offset) => {
      const band = first + offset;
      const seconds = counts.get(band) || 0;
      return `<div class="insights-cadence-band" title="${band * 10}–${band * 10 + 9} ` +
        `rpm: ${formatTotalTime(seconds)}">
        <span class="insights-cadence-plot"><span style="height:${
          seconds ? Math.max(3, seconds / max * 100) : 0}%"></span></span>
        <span class="caption muted">${band * 10}</span>
      </div>`;
    }).join('') + '</div></div>';
  el('insights-cadence-note').textContent =
    'Seconds by 10 rpm band. Coasting is excluded; condensed rides use saved counts.';
}

function drawInsightCategories(model) {
  const categories = [...model.categories.entries()].sort((a, b) => b[1].time - a[1].time);
  const max = Math.max(1, ...categories.map(([, value]) => value.time));
  el('insights-categories').innerHTML = categories.map(([name, value]) => `
    <div class="insights-category">
      <div><strong>${escapeHtml(name)}</strong><span class="muted">${
        value.rides} ${value.rides === 1 ? 'ride' : 'rides'} · ${formatTotalTime(value.time)}</span></div>
      <span class="insights-category-track"><span style="width:${
        value.time / max * 100}%"></span></span>
    </div>`).join('');
}

function drawInsightRecords(model) {
  const records = [
    ['Most output', model.biggest,
      model.biggest ? `${insightCompact(model.biggest.total_output_kj)} kJ` : null,
      'measured watts'],
    ['Longest ride', model.longest,
      model.longest ? formatDuration(model.longest.duration_sec) : null,
      'all rides'],
    ['Highest average', model.highestAverage,
      model.highestAverage ? `${Math.round(model.highestAverage.avg_power)} W` : null,
      'measured watts'],
    ['Peak second', model.peakPower && model.peakPower.ride,
      model.peakPower ? `${Math.round(model.peakPower.watts)} W` : null,
      'measured watts'],
  ];
  el('insights-records').innerHTML = records.map(([label, ride, value, basis]) => ride
    ? `<a class="insights-record" href="#/ride/${encodeURIComponent(ride.id)}">
        <span class="figure-label">${label}</span>
        <strong>${escapeHtml(value)}</strong>
        <span>${escapeHtml(insightRideTitle(ride))}</span>
        <span class="caption muted">${escapeHtml(formatDate(ride.recorded_at))} · ${basis}</span>
      </a>`
    : `<div class="insights-record"><span class="figure-label">${label}</span>
        <strong>—</strong><span class="caption muted">No ${basis} yet</span></div>`).join('');
}

function drawInsightCoverage(model) {
  const { coverage } = model;
  const facts = [
    ['Measured power', coverage.measured,
      'Only these rides set power records and contribute to power zones.'],
    ['Detailed samples', coverage.samples,
      'A ride with no samples still counts toward totals and history.'],
    ['Condensed', coverage.condensed,
      'Zone and cadence charts use saved counts, never the thinned trace as seconds.'],
    ['Power-zone basis', coverage.powerZones,
      'Measured rides with their own FTP and countable seconds.'],
    ['Heart-rate basis', coverage.heartZones,
      'Rides with their own maximum heart rate and countable seconds.'],
    ['Heart-rate samples', coverage.heartSamples,
      'Rides with at least one recorded pulse.'],
  ];
  el('insights-coverage').innerHTML = facts.map(([label, count, detail]) => `
    <div><span class="figure">${count.toLocaleString()}</span>
      <strong>${label}</strong><span class="caption muted">${detail}</span></div>`).join('');
}

if (typeof document !== 'undefined') wireInsights();
if (typeof module !== 'undefined') module.exports = {
  insightStart, insightBuckets, aggregateInsights, insightZone,
};
