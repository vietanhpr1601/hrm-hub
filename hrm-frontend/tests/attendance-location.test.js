import test from 'node:test';
import assert from 'node:assert/strict';
import { attendanceSessionKey, createLocationRequestManager, requestFreshLocation, validateAttendanceLocation, validateLocationPolicy, getLocationMapUrl, LOCATION_OPTIONS } from '../src/utils/attendanceLocation.js';
import { summarizeAttendance, workplaceDistanceLabel } from '../src/utils/attendanceSummary.js';

const now = Date.parse('2026-10-03T05:00:00Z');
const policy = { maxAccuracyMeters: 100, targetAccuracyMeters: 20, acquisitionTimeoutMs: 25000 };
const position = (latitude = 0, longitude = 0, accuracy = 12, timestamp = now) => ({ coords: { latitude, longitude, accuracy }, timestamp });
const options = { policy, secureContext: true, now: () => now };
const flush = () => new Promise(resolve => setImmediate(resolve));

function browser(t) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let clock = now;
  const watches = [];
  const cleared = [];
  const geolocation = {
    watchPosition(success, failure, requestOptions) { watches.push({ success, failure, requestOptions }); return watches.length - 1; },
    clearWatch(id) { cleared.push(id); },
  };
  return {
    watches, cleared, geolocation,
    config: { ...options, now: () => clock, geolocation },
    tick(ms) { clock += ms; t.mock.timers.tick(ms); },
    emit(value, index = watches.length - 1) { watches[index].success(value); },
    error(code, index = watches.length - 1) { watches[index].failure({ code }); },
  };
}

test('uses fresh high accuracy watch and clears synchronous watch ID zero after an early precise fix', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let requestedOptions;
  const cleared = [];
  const result = await requestFreshLocation({ ...options, geolocation: {
    watchPosition(success, failure, requestOptions) { requestedOptions = requestOptions; success(position()); return 0; },
    clearWatch(id) { cleared.push(id); },
  } });
  assert.deepEqual(requestedOptions, { ...LOCATION_OPTIONS, enableHighAccuracy: true, maximumAge: 0, timeout: 25000 });
  assert.deepEqual(cleared, [0]);
  assert.deepEqual(result, { latitude: 0, longitude: 0, locationAccuracy: 12, locationCapturedAt: '2026-10-03T05:00:00.000Z' });
  assert.match(getLocationMapUrl(result), /query=0%2C0$/);
  t.mock.timers.tick(25000);
  assert.deepEqual(cleared, [0]);
});

test('keeps improving samples without accepting provisional fixes and stops at target', async t => {
  const gps = browser(t);
  const progress = [];
  let settled = false;
  const pending = requestFreshLocation({ ...gps.config, onSample: sample => progress.push(sample.locationAccuracy) }).then(result => { settled = true; return result; });
  gps.emit(position(10, 20, 120));
  gps.emit(position(11, 21, 35));
  gps.emit(position(12, 22, 70));
  await flush();
  assert.equal(settled, false);
  gps.emit(position(13, 23, 20));
  const accepted = await pending;
  assert.deepEqual(progress, [120, 35, 20]);
  assert.equal(accepted.latitude, 13);
  assert.equal(accepted.longitude, 23);
  assert.equal(accepted.locationAccuracy, 20);
  assert.deepEqual(gps.cleared, [0]);
  gps.emit(position(90, 90, 1));
  assert.deepEqual(progress, [120, 35, 20]);
});

test('accepts unchanged best coordinates at deadline even if a later fix is worse', async t => {
  const gps = browser(t);
  const pending = requestFreshLocation(gps.config);
  gps.emit(position(10.1234567, 106.9876543, 35.123, now));
  gps.emit(position(11, 107, 120, now + 1));
  gps.tick(24999);
  assert.deepEqual(gps.cleared, []);
  gps.tick(1);
  assert.deepEqual(await pending, { latitude: 10.1234567, longitude: 106.9876543, locationAccuracy: 35.123, locationCapturedAt: new Date(now).toISOString() });
  assert.deepEqual(gps.cleared, [0]);
});

test('accepts exact accuracy threshold but rejects any excess with the actual best estimate', async t => {
  const gps = browser(t);
  const accepted = requestFreshLocation(gps.config);
  gps.emit(position(1, 2, 100));
  gps.tick(25000);
  assert.equal((await accepted).locationAccuracy, 100);
  const denied = requestFreshLocation(gps.config);
  const rejected = assert.rejects(denied, error => error.code === 'LOCATION_INACCURATE' && /100\.1 m.*100 m/.test(error.message));
  gps.emit(position(1, 2, 350, now + 25000));
  gps.emit(position(3, 4, 100.01, now + 25001));
  gps.tick(25000);
  await rejected;
  assert.deepEqual(gps.cleared, [0, 1]);
});

test('ignores out of order callbacks and chooses the latest equally accurate valid fix', async t => {
  const gps = browser(t);
  const pending = requestFreshLocation(gps.config);
  gps.emit(position(1, 2, 35, now));
  gps.emit(position(3, 4, 35, now + 1));
  gps.emit(position(5, 6, 1, now - 1));
  gps.tick(25000);
  const result = await pending;
  assert.equal(result.latitude, 3);
  assert.equal(result.locationAccuracy, 35);
  assert.equal(result.locationCapturedAt, new Date(now + 1).toISOString());
});

test('ignores invalid coordinates, missing accuracy, stale and future samples without inventing GPS', async t => {
  const gps = browser(t);
  const invalid = [position(91, 0), position(0, -181), position(NaN, 10), position(null, 1), position(10, 10, -1), position(10, 10), position(10, 10, 5, now - 120001), position(10, 10, 5, now + 30001), { coords: position().coords }];
  invalid[5].coords.accuracy = undefined;
  for (const value of invalid) {
    const pending = requestFreshLocation(gps.config);
    const rejected = assert.rejects(pending, error => ['LOCATION_INVALID', 'LOCATION_STALE'].includes(error.code));
    gps.emit(value);
    t.mock.timers.tick(25000);
    await rejected;
  }
  assert.equal(getLocationMapUrl({ latitude: 300, longitude: 0 }), null);
  assert.equal(getLocationMapUrl({ latitude: null, longitude: null }), null);
});

test('recovers from stale and transient provider errors within the acquisition deadline', async t => {
  const gps = browser(t);
  const pending = requestFreshLocation(gps.config);
  gps.emit(position(1, 2, 5, now - 120001));
  gps.error(2);
  gps.error(3);
  gps.emit(position(10, 20, 10));
  assert.equal((await pending).latitude, 10);
  assert.deepEqual(gps.cleared, [0]);
});

test('rechecks freshness at deadline and chooses another sample if the former best expires', async t => {
  const gps = browser(t);
  const pending = requestFreshLocation(gps.config);
  gps.emit(position(1, 2, 25, now - 110000));
  gps.emit(position(3, 4, 40, now));
  gps.tick(25000);
  assert.equal((await pending).latitude, 3);
  const oldOnly = requestFreshLocation(gps.config);
  const rejected = assert.rejects(oldOnly, /quá thời gian/);
  gps.emit(position(5, 6, 25, now - 85000));
  gps.tick(25000);
  await rejected;
});

test('location freshness bounds and zero accuracy are respected before submission', () => {
  const location = { latitude: 10, longitude: 10, locationAccuracy: 0, locationCapturedAt: new Date(now).toISOString() };
  assert.equal(validateAttendanceLocation(location, now + 120000), location);
  assert.equal(validateAttendanceLocation(location, now - 30000), location);
  assert.throws(() => validateAttendanceLocation(location, now + 120001), /Vị trí đã cũ/);
  assert.throws(() => validateAttendanceLocation(location, now - 30001), /Vị trí đã cũ/);
});

test('permission denial fails immediately even with a provisional fix and clears the watch', async t => {
  const gps = browser(t);
  const pending = requestFreshLocation(gps.config);
  const rejected = assert.rejects(pending, /Quyền vị trí đang bị chặn/);
  gps.emit(position(1, 2, 35));
  gps.error(1);
  await rejected;
  assert.deepEqual(gps.cleared, [0]);
});

test('unavailable, provider timeout and silent providers finish within the global deadline', async t => {
  const gps = browser(t);
  for (const [code, message] of [[2, /Không xác định được vị trí/], [3, /quá thời gian/], [null, /quá thời gian/]]) {
    const pending = requestFreshLocation(gps.config);
    const rejected = assert.rejects(pending, message);
    if (code) gps.error(code);
    gps.tick(25000);
    await rejected;
  }
  assert.deepEqual(gps.cleared, [0, 1, 2]);
});

test('insecure, unsupported and throwing browser providers fail without a fabricated location', async t => {
  const gps = browser(t);
  await assert.rejects(requestFreshLocation({ ...gps.config, secureContext: false }), /HTTPS hoặc localhost/);
  assert.equal(gps.watches.length, 0);
  await assert.rejects(requestFreshLocation({ ...options, geolocation: {} }), /không hỗ trợ định vị/);
  await assert.rejects(requestFreshLocation({ ...gps.config, geolocation: { ...gps.geolocation, watchPosition() { throw Error('SecurityError'); } } }), /Kiểm tra quyền vị trí/);
});

test('cancellation clears watch zero and timers and ignores callbacks after close', async t => {
  const gps = browser(t);
  const controller = new AbortController();
  const progress = [];
  const pending = requestFreshLocation({ ...gps.config, signal: controller.signal, onSample: sample => progress.push(sample) });
  const rejected = assert.rejects(pending, error => error.code === 'LOCATION_CANCELLED');
  controller.abort();
  gps.emit(position());
  gps.tick(25000);
  await rejected;
  assert.deepEqual(gps.cleared, [0]);
  assert.deepEqual(progress, []);
});

test('synchronous cancellation inside a callback still clears the returned watch ID zero', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const controller = new AbortController();
  const cleared = [];
  await assert.rejects(requestFreshLocation({ ...options, signal: controller.signal, onSample: () => controller.abort(), geolocation: {
    watchPosition(success) { success(position(1, 2, 35)); return 0; },
    clearWatch(id) { cleared.push(id); },
  } }), error => error.code === 'LOCATION_CANCELLED');
  assert.deepEqual(cleared, [0]);
});

test('an already cancelled acquisition never opens a watch', async t => {
  const gps = browser(t);
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(requestFreshLocation({ ...gps.config, signal: controller.signal }), error => error.code === 'LOCATION_CANCELLED');
  assert.equal(gps.watches.length, 0);
});

test('each request loads authoritative policy anew including changed thresholds and timeout', async t => {
  const gps = browser(t);
  const manager = createLocationRequestManager();
  let loads = 0;
  const received = [];
  const config = { ...gps.config, loadPolicy: async () => ++loads === 1 ? policy : { maxAccuracyMeters: 10, targetAccuracyMeters: 5, acquisitionTimeoutMs: 1000 }, onPolicy: result => received.push(result) };
  const first = manager.request(config);
  await flush();
  gps.emit(position(1, 2, 100));
  gps.tick(25000);
  assert.equal((await first).latitude, 1);
  const second = manager.request(config);
  const rejected = assert.rejects(second, /12 m.*10 m/);
  await flush();
  gps.emit(position(3, 4, 12, now + 25000));
  gps.tick(1000);
  await rejected;
  assert.equal(loads, 2);
  assert.deepEqual(received[0], policy);
  assert.equal(received[1].maxAccuracyMeters, 10);
  assert.equal(gps.watches[1].requestOptions.timeout, 1000);
});

test('policy outages and malformed policies block GPS without a permissive fallback', async t => {
  const gps = browser(t);
  const manager = createLocationRequestManager();
  const invalidPolicies = [undefined, {}, { ...policy, maxAccuracyMeters: 0 }, { ...policy, maxAccuracyMeters: '100' }, { ...policy, targetAccuracyMeters: 110 }, { ...policy, targetAccuracyMeters: -1 }, { ...policy, acquisitionTimeoutMs: 999 }, { ...policy, acquisitionTimeoutMs: 60001 }, { ...policy, acquisitionTimeoutMs: 1000.5 }];
  for (const invalid of invalidPolicies) {
    assert.throws(() => validateLocationPolicy(invalid), error => error.code === 'LOCATION_POLICY');
    await assert.rejects(manager.request({ ...gps.config, loadPolicy: async () => invalid }), /cấu hình.*Lấy lại vị trí/);
  }
  await assert.rejects(manager.request({ ...gps.config, loadPolicy: async () => { throw Error('offline'); } }), /cấu hình.*Lấy lại vị trí/);
  assert.equal(gps.watches.length, 0);
});

test('closing during policy fetch cancels promptly and a late policy cannot start GPS', async t => {
  const gps = browser(t);
  const manager = createLocationRequestManager();
  let resolvePolicy;
  let policySignal;
  const pending = manager.request({ ...gps.config, loadPolicy: ({ signal }) => { policySignal = signal; return new Promise(resolve => { resolvePolicy = resolve; }); } });
  const rejected = assert.rejects(pending, error => error.code === 'LOCATION_CANCELLED');
  await flush();
  manager.cancel();
  await rejected;
  assert.equal(policySignal.aborted, true);
  resolvePolicy(policy);
  await flush();
  assert.equal(gps.watches.length, 0);
});

test('retry cancels the old watch and ignores its results without contaminating the new fix', async t => {
  const gps = browser(t);
  const manager = createLocationRequestManager();
  const config = { ...gps.config, loadPolicy: async () => policy };
  const first = manager.request(config);
  const rejected = assert.rejects(first, error => error.code === 'LOCATION_CANCELLED');
  await flush();
  const second = manager.request(config);
  await flush();
  gps.emit(position(10, 20), 1);
  gps.emit(position(30, 40), 0);
  await rejected;
  assert.equal((await second).latitude, 10);
  assert.deepEqual(gps.cleared, [0, 1]);
});

test('request identity changes for account, staff or authorization but stays stable for reordered data', () => {
  const user = { id: 'one', username: 'employee', staffId: 'staff-a', active: true, permissions: ['ATTENDANCE_SELF', 'LEAVE_SELF'], roles: [{ id: 'a', name: 'ROLE_USER' }, { id: 'b', name: 'ROLE_OTHER' }] };
  for (const change of [{ id: 'two' }, { staffId: 'staff-b' }, { permissions: [] }, { active: false }, { roles: [{ name: 'ROLE_ADMIN' }] }]) {
    assert.notEqual(attendanceSessionKey(user), attendanceSessionKey({ ...user, ...change }));
  }
  assert.equal(attendanceSessionKey(user), attendanceSessionKey({ ...user, fullName: 'Name', permissions: [...user.permissions].reverse(), roles: [...user.roles].reverse().map(role => ({ ...role, modifiedDate: 'new' })) }));
});

test('attendance summary uses backend totals and sums late or early minutes across shifts', () => {
  const summary = summarizeAttendance({ standardHours: 7, overtimeHours: 1.5, details: [
    { checkInTime: '2026-10-03T08:03:00', checkOutTime: '2026-10-03T12:00:00', lateMinutes: 3, earlyMinutes: 0 },
    { checkInTime: '2026-10-03T13:02:00', checkOutTime: '2026-10-03T18:00:00', lateMinutes: 2, earlyMinutes: 4 },
  ] });
  assert.equal(summary.checkIn, '2026-10-03T08:03:00');
  assert.equal(summary.checkOut, '2026-10-03T18:00:00');
  assert.equal(summary.totalHours, 8.5);
  assert.equal(summary.lateMinutes, 5);
  assert.equal(summary.earlyMinutes, 4);
});

test('missing attendance totals or office location are not replaced with fake measurements', () => {
  const summary = summarizeAttendance({}, [{ recordType: 'CHECK_IN', recordTime: '2026-10-03T08:00:00' }]);
  assert.equal(summary.checkOut, null);
  assert.equal(summary.totalHours, null);
  assert.equal(summary.lateMinutes, null);
  assert.equal(workplaceDistanceLabel({}), 'Chưa cấu hình');
  assert.equal(workplaceDistanceLabel({ workplaceName: 'Văn phòng' }), 'Chưa xác định');
  assert.equal(workplaceDistanceLabel({ distanceMeters: 0 }), '0 m');
  assert.equal(workplaceDistanceLabel({ distanceMeters: 1234 }), '1.23 km');
});
