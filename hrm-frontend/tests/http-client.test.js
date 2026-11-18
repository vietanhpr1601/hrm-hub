import test from 'node:test';
import assert from 'node:assert/strict';
import { AxiosError } from 'axios';
import { createHttpClient } from '../src/services/createHttpClient.js';

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};
const ok = (config) => ({ status: 200, data: { ok: true }, config, headers: {} });
const unauthorized = (config) => new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, { status: 401, config });
const refreshed = (accessToken = 'new-access', refreshToken) => ({ data: { status: 200, data: { accessToken, refreshToken } } });

const setup = (refreshTokens = async () => refreshed()) => {
  let session = { user: { id: 'user-1' }, accessToken: 'old-access', refreshToken: 'refresh-1' };
  const calls = { refresh: 0, expired: 0, loading: 0 };
  const client = createHttpClient({
    baseURL: 'http://example.invalid/api',
    getSession: () => session,
    setTokens: (accessToken, refreshToken) => { session = { ...session, accessToken, refreshToken }; },
    clearSession: () => { session = { user: null, accessToken: null, refreshToken: null }; },
    refreshTokens: async (token) => { calls.refresh++; return refreshTokens(token); },
    onSessionExpired: () => { calls.expired++; },
    startLoading: () => { calls.loading++; },
    stopLoading: () => { calls.loading--; },
  });
  return { client, calls, getSession: () => session, setSession: (value) => { session = value; } };
};

test('relative API base does not duplicate the prefix used by services', async () => {
  const { client } = setup();
  client.defaults.baseURL = '/api';
  client.defaults.adapter = async config => {
    assert.equal(client.getUri(config), '/api/auth/login');
    assert.equal(config.headers.has('Authorization'), false);
    return ok(config);
  };
  await client.post('/api/auth/login', { username: 'test', password: 'unused' });
});

test('background notification requests never change the foreground loading counter', async () => {
  const harness = setup();
  const foreground = deferred();
  harness.client.defaults.adapter = async config => {
    if (config.url === '/staff') return foreground.promise.then(() => ok(config));
    if (config.url === '/notifications/error') throw new AxiosError('Failed', 'ERR_BAD_RESPONSE', config, null, { status: 500, config });
    return ok(config);
  };
  const pending = harness.client.get('/staff');
  await Promise.resolve();
  assert.equal(harness.calls.loading, 1);
  await harness.client.get('/notifications/unread-count', { skipGlobalLoading: true });
  assert.equal(harness.calls.loading, 1);
  await assert.rejects(harness.client.get('/notifications/error', { skipGlobalLoading: true }));
  assert.equal(harness.calls.loading, 1);
  foreground.resolve();
  await pending;
  assert.equal(harness.calls.loading, 0);
});

test('concurrent 401s refresh once and retry all requests with the new token', async () => {
  const refresh = deferred();
  const started = deferred();
  const harness = setup(() => { started.resolve(); return refresh.promise; });
  let requests = 0;
  harness.client.defaults.adapter = async (config) => {
    requests++;
    if (config.headers.get('Authorization') !== 'Bearer new-access') throw unauthorized(config);
    return ok(config);
  };
  const pending = Promise.all([harness.client.get('/staff'), harness.client.get('/recruitments'), harness.client.get('/leave')]);
  await started.promise;
  refresh.resolve(refreshed());
  await pending;
  assert.equal(harness.calls.refresh, 1);
  assert.equal(requests, 6);
  assert.equal(harness.getSession().refreshToken, 'refresh-1');
  assert.equal(harness.calls.loading, 0);
});

test('missing refresh token rejects repeated 401s instead of leaving the queue pending', { timeout: 2000 }, async () => {
  const harness = setup();
  harness.setSession({ user: { id: 'user-1' }, accessToken: 'old-access', refreshToken: null });
  harness.client.defaults.adapter = async (config) => { throw unauthorized(config); };
  await assert.rejects(harness.client.get('/staff'));
  await assert.rejects(harness.client.get('/recruitments'));
  assert.equal(harness.calls.refresh, 0);
  assert.equal(harness.calls.expired, 1);
  assert.equal(harness.calls.loading, 0);
});

test('failed login and logout never trigger refresh or recursive logout', async () => {
  const harness = setup();
  const headers = [];
  harness.client.defaults.adapter = async (config) => {
    headers.push(config.headers.get('Authorization'));
    throw unauthorized(config);
  };
  await assert.rejects(harness.client.post('/auth/login'));
  await assert.rejects(harness.client.post('/auth/logout'));
  assert.deepEqual(headers, [undefined, 'Bearer old-access']);
  assert.equal(harness.calls.refresh, 0);
  assert.equal(harness.calls.expired, 0);
});

test('refresh failure rejects every waiter and a later login can refresh normally', async () => {
  let shouldFail = true;
  const harness = setup(async () => {
    if (shouldFail) throw new Error('Refresh rejected');
    return refreshed();
  });
  harness.client.defaults.adapter = async (config) => {
    if (config.headers.get('Authorization') !== 'Bearer new-access') throw unauthorized(config);
    return ok(config);
  };
  const failures = await Promise.allSettled([harness.client.get('/staff'), harness.client.get('/recruitments')]);
  assert.ok(failures.every((result) => result.status === 'rejected'));
  assert.equal(harness.calls.expired, 1);
  shouldFail = false;
  harness.setSession({ user: { id: 'user-2' }, accessToken: 'old-access-2', refreshToken: 'refresh-2' });
  await harness.client.get('/staff');
  assert.equal(harness.calls.refresh, 2);
  assert.equal(harness.calls.loading, 0);
});

test('a retried request returning 401 expires the session without a refresh loop', async () => {
  const harness = setup();
  let requests = 0;
  harness.client.defaults.adapter = async (config) => { requests++; throw unauthorized(config); };
  await assert.rejects(harness.client.get('/staff'));
  assert.equal(requests, 2);
  assert.equal(harness.calls.refresh, 1);
  assert.equal(harness.calls.expired, 1);
});

test('malformed refresh response clears the session without sending an undefined token', async () => {
  const harness = setup(async () => ({ data: { status: 200, data: {} } }));
  let requests = 0;
  harness.client.defaults.adapter = async (config) => { requests++; throw unauthorized(config); };
  await assert.rejects(harness.client.get('/staff'));
  assert.equal(requests, 1);
  assert.equal(harness.getSession().accessToken, null);
});

test('logout during refresh cannot resurrect the cleared session', async () => {
  const refresh = deferred();
  const started = deferred();
  const harness = setup(() => { started.resolve(); return refresh.promise; });
  harness.client.defaults.adapter = async (config) => { throw unauthorized(config); };
  const pending = assert.rejects(harness.client.get('/staff'));
  await started.promise;
  harness.setSession({ user: null, accessToken: null, refreshToken: null });
  refresh.resolve(refreshed());
  await pending;
  assert.equal(harness.getSession().accessToken, null);
  assert.equal(harness.calls.expired, 0);
});

test('a failed refresh from an old login does not clear the newly signed-in account', async () => {
  const refresh = deferred();
  const started = deferred();
  const harness = setup(() => { started.resolve(); return refresh.promise; });
  harness.client.defaults.adapter = async (config) => { throw unauthorized(config); };
  const pending = assert.rejects(harness.client.get('/staff'));
  await started.promise;
  const newSession = { user: { id: 'user-2' }, accessToken: 'account-2-access', refreshToken: 'refresh-2' };
  harness.setSession(newSession);
  refresh.reject(new Error('Old session expired'));
  await pending;
  assert.deepEqual(harness.getSession(), newSession);
  assert.equal(harness.calls.expired, 0);
});

test('a late 401 reuses a token that another request has already refreshed', async () => {
  const delayed = deferred();
  const slowStarted = deferred();
  const harness = setup();
  harness.client.defaults.adapter = async (config) => {
    if (config.headers.get('Authorization') === 'Bearer new-access') return ok(config);
    if (config.url === '/slow') {
      slowStarted.resolve();
      await delayed.promise;
    }
    throw unauthorized(config);
  };
  const slow = harness.client.get('/slow');
  await slowStarted.promise;
  await harness.client.get('/fast');
  delayed.resolve();
  await slow;
  assert.equal(harness.calls.refresh, 1);
  assert.equal(harness.calls.expired, 0);
});

test('a late 401 from another account is rejected without retrying as the current user', async () => {
  const delayed = deferred();
  const started = deferred();
  const harness = setup();
  let requests = 0;
  harness.client.defaults.adapter = async (config) => {
    requests++;
    started.resolve();
    await delayed.promise;
    throw unauthorized(config);
  };
  const pending = assert.rejects(harness.client.post('/staff', { name: 'Old account action' }));
  await started.promise;
  const newSession = { user: { id: 'user-2' }, accessToken: 'account-2-access', refreshToken: 'refresh-2' };
  harness.setSession(newSession);
  delayed.resolve();
  await pending;
  assert.equal(requests, 1);
  assert.equal(harness.calls.refresh, 0);
  assert.deepEqual(harness.getSession(), newSession);
});

test('a rotated refresh token is saved for subsequent requests', async () => {
  const harness = setup(async () => refreshed('new-access', 'refresh-rotated'));
  harness.client.defaults.adapter = async (config) => {
    if (config.headers.get('Authorization') !== 'Bearer new-access') throw unauthorized(config);
    return ok(config);
  };
  await harness.client.get('/staff');
  assert.equal(harness.getSession().refreshToken, 'refresh-rotated');
});

test('non-authentication errors do not refresh or sign the user out', async () => {
  const harness = setup();
  harness.client.defaults.adapter = async (config) => {
    throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, { status: 403, config });
  };
  await assert.rejects(harness.client.get('/staff'));
  assert.equal(harness.calls.refresh, 0);
  assert.equal(harness.calls.expired, 0);
  assert.equal(harness.getSession().accessToken, 'old-access');
});
