const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const context = vm.createContext({ module: { exports: {} } });
for (const file of ['lib.js', 'insights.js']) {
  vm.runInContext(fs.readFileSync(path.join(__dirname, file), 'utf8'), context, {
    filename: file,
  });
}
const { aggregateInsights, insightStart } = context.module.exports;
const decode = vm.runInContext('decodeMetrics', context);
const powerZones = vm.runInContext('POWER_ZONES', context);
const heartZones = vm.runInContext('HR_ZONES', context);

function ride(overrides = {}) {
  return {
    id: 'ride-1', recorded_at: '2026-09-21T10:00:00Z', duration_sec: 120,
    total_output_kj: 80, total_distance_km: 4, avg_power: 110,
    power_provenance: 'Measured', class_templates: { category: 'Climb' },
    metrics_payload: { v: 2, t: [0, 1, 2], c: [80, 80, 0], r: [30, 30, 30],
      p: [110, 170, 0], hr: [120, 150, null], pm: [1, 1, 1],
      w: { ftp: 200, mhr: 200 } },
    ...overrides,
  };
}

function aggregate(rows) {
  return aggregateInsights(rows, '12w', new Date('2026-09-28T12:00:00Z'),
    decode, powerZones, heartZones);
}

test('ride totals include modelled rides, while power records and zones require measured watts', () => {
  const measured = ride();
  const modelled = ride({ id: 'ride-2', total_output_kj: 200,
    power_provenance: 'Modelled', metrics_payload: { v: 2, t: [0], c: [90],
      r: [30], p: [500], pm: [0], w: { ftp: 200 } } });
  const result = aggregate([measured, modelled]);
  assert.equal(result.rows.length, 2);
  assert.equal(result.output, 280);
  assert.equal(result.biggest.id, 'ride-1');
  assert.equal(result.peakPower.watts, 170);
  assert.equal(result.powerSeconds.get('p1'), 1);
  assert.equal(result.powerSeconds.get('p3'), 1);
  assert.equal(result.powerSeconds.get('p7'), 0);
  assert.equal(result.heartSeconds.get('h2'), 1);
  assert.equal(result.heartSeconds.get('h3'), 1);
  assert.equal(result.coverage.measured, 1);
});

test('condensed rides use saved seconds, not the two trace points', () => {
  const condensed = ride({ metrics_payload: { v: 2, d: 10, t: [0, 10],
    c: [80, 100], r: [30, 40], p: [110, 170], hr: [120, 150],
    pm: [1, 1], w: { ftp: 200, mhr: 200, dist: {
      ftp_watts: 200, max_hr_bpm: 200,
      seconds_by_zone: { Z1: 8, Z3: 12 },
      seconds_by_hr_zone: { H2: 5, H3: 15 },
      seconds_by_cadence_band: { 8: 14, 10: 6 },
    } } } });
  const result = aggregate([condensed]);
  assert.equal(result.powerSeconds.get('p1'), 8);
  assert.equal(result.powerSeconds.get('p3'), 12);
  assert.equal(result.heartSeconds.get('h2'), 5);
  assert.equal(result.heartSeconds.get('h3'), 15);
  assert.equal(result.cadenceSeconds.get(8), 14);
  assert.equal(result.cadenceSeconds.get(10), 6);
  assert.equal(result.coverage.condensed, 1);
});

test('missing ride FTP and maximum do not invent zone comparisons', () => {
  const noBasis = ride({ metrics_payload: { v: 2, t: [0], c: [80],
    r: [30], p: [150], hr: [150], pm: [1] } });
  const result = aggregate([noBasis]);
  assert.equal(result.coverage.powerZones, 0);
  assert.equal(result.coverage.heartZones, 0);
  assert.equal([...result.powerSeconds.values()].reduce((a, b) => a + b), 0);
  assert.equal([...result.heartSeconds.values()].reduce((a, b) => a + b), 0);
});

test('range boundaries start on Monday or the first of the twelfth month', () => {
  const today = new Date(2026, 8, 28);
  assert.equal(insightStart('12w', today).getDay(), 1);
  assert.equal(insightStart('12m', today).getFullYear(), 2025);
  assert.equal(insightStart('12m', today).getMonth(), 9);
  assert.equal(insightStart('all', today), null);
});
