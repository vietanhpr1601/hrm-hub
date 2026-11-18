import test from 'node:test';
import assert from 'node:assert/strict';
import { createNotificationStore, notificationSessionIdentity } from '../src/store/createNotificationStore.js';
import { navigations } from '../src/navigationConfig.js';
import { filterNavigations } from '../src/utils/permissions.js';
import { canOpenNotification } from '../src/utils/notificationAccess.js';

const deferred = () => {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
};
const flatten = items => items.flatMap(item => [item, ...flatten(item.children || [])]);
const api = overrides => ({ getNotifications: async () => ({ content: [], totalElements: 0, totalPages: 0 }),
  getUnreadCount: async () => ({ unreadCount: 0 }), readNotification: async () => ({}), readAllNotifications: async () => ({}), ...overrides });

test('personal notifications support granted custom or empty roles and hide when permission is absent', () => {
  for (const user of [{ role: ['ROLE_USER'], permissions: ['NOTIFICATION_VIEW'] }, { role: ['CUSTOM'], permissions: ['NOTIFICATION_VIEW'] }, { roles: [], permissions: ['NOTIFICATION_VIEW'] }]) {
    const paths = flatten(filterNavigations(navigations, user)).map(item => item.path);
    assert.ok(paths.includes('/announcement/announcement'));
    assert.ok(!paths.includes('/administration/accounts'));
    assert.ok(!paths.includes('/leave-requests'));
  }
  for (const user of [null, { role: ['ROLE_USER'], permissions: [] }, { role: ['CUSTOM'], permissions: [] }, { active: false, role: ['ROLE_ADMIN'] }, { isDeleted: true, role: ['ROLE_USER'] }]) {
    assert.ok(!flatten(filterNavigations(navigations, user)).some(item => item.path === '/announcement/announcement'));
  }
});

const grantedSession = () => ({ accessToken: 'session', user: { id: 'account', staffId: 'staff', role: ['ROLE_USER'], permissions: ['NOTIFICATION_VIEW'] } });
const revokedSession = () => ({ ...grantedSession(), user: { ...grantedSession().user, permissions: [] } });

test('session synchronization requires notification permission and all denied store operations skip the API', async () => {
  const calls = [];
  const record = name => async () => { calls.push(name); return {}; };
  const store = createNotificationStore(api({ getNotifications: record('list'), getUnreadCount: record('count'), readNotification: record('read'), readAllNotifications: record('read-all') }));
  for (const session of [revokedSession(), { ...grantedSession(), accessToken: null }, { ...grantedSession(), user: null }, { ...grantedSession(), user: { ...grantedSession().user, active: false } }]) {
    store.getState().setSession(session);
    assert.equal(store.getState().identity, null);
    await store.getState().refreshCount();
    await store.getState().loadPage();
    assert.equal(await store.getState().markRead('one'), false);
    assert.equal(await store.getState().markAllRead(), false);
  }
  assert.deepEqual(calls, []);
  assert.equal(notificationSessionIdentity({ ...revokedSession(), user: { ...revokedSession().user, role: ['ROLE_ADMIN'] } }), 'account:staff');
  assert.equal(notificationSessionIdentity({ ...grantedSession(), accessToken: 'rotated-token' }), 'account:staff');
});

test('revocation clears visible inbox and unread badge immediately and regrant loads persisted notifications', async () => {
  let countCalls = 0;
  const store = createNotificationStore(api({
    getNotifications: async () => ({ content: [{ id: 'saved-notification' }], totalElements: 1, totalPages: 1 }),
    getUnreadCount: async () => { countCalls++; return { unreadCount: 1 }; },
  }));
  store.getState().setSession(grantedSession());
  await Promise.all([store.getState().loadPage(), store.getState().refreshCount()]);
  assert.equal(store.getState().items.length, 1);
  assert.equal(store.getState().unreadCount, 1);
  store.getState().setSession(revokedSession());
  assert.equal(store.getState().identity, null);
  assert.deepEqual(store.getState().items, []);
  assert.equal(store.getState().unreadCount, 0);
  await store.getState().refreshCount();
  assert.equal(countCalls, 1, 'a queued polling callback cannot fetch while permission is revoked');
  store.getState().setSession(grantedSession());
  await Promise.all([store.getState().loadPage(), store.getState().refreshCount()]);
  assert.deepEqual(store.getState().items, [{ id: 'saved-notification' }]);
  assert.equal(store.getState().unreadCount, 1);
});

test('late inbox and count responses remain invalid after revoke and regrant to the same identity', async () => {
  const list = deferred();
  const count = deferred();
  let fresh = false;
  const store = createNotificationStore(api({
    getNotifications: () => fresh ? Promise.resolve({ content: [{ id: 'fresh' }], totalElements: 1, totalPages: 1 }) : list.promise,
    getUnreadCount: () => fresh ? Promise.resolve({ unreadCount: 1 }) : count.promise,
  }));
  store.getState().setSession(grantedSession());
  const pending = Promise.all([store.getState().loadPage(), store.getState().refreshCount()]);
  store.getState().setSession(revokedSession());
  store.getState().setSession(grantedSession());
  fresh = true;
  await Promise.all([store.getState().loadPage(), store.getState().refreshCount()]);
  list.resolve({ content: [{ id: 'obsolete' }], totalElements: 7, totalPages: 3 });
  count.resolve({ unreadCount: 7 });
  await pending;
  assert.deepEqual(store.getState().items, [{ id: 'fresh' }]);
  assert.equal(store.getState().unreadCount, 1);
  assert.equal(store.getState().totalElements, 1);
});

test('pending read and read-all cannot refresh or open details after permission is revoked and regranted', async () => {
  for (const action of ['markRead', 'markAllRead']) {
    const mutation = deferred();
    let refreshes = 0;
    const store = createNotificationStore(api({ readNotification: () => mutation.promise, readAllNotifications: () => mutation.promise,
      getNotifications: async () => { refreshes++; return { content: [] }; } }));
    store.getState().setSession(grantedSession());
    const pending = store.getState()[action]('one');
    store.getState().setSession(revokedSession());
    store.getState().setSession(grantedSession());
    mutation.resolve({});
    assert.equal(await pending, false);
    assert.equal(refreshes, 0);
    assert.equal(store.getState().busyId, null);
    assert.equal(store.getState().actionError, '');
  }
});

test('notification detail links require both the inbox grant and the linked personal feature grant', () => {
  const attendance = { type: 'ATTENDANCE_APPROVED', referenceId: 'timesheet', referenceDate: '2026-10-08' };
  const leave = { type: 'LEAVE_APPROVED', referenceId: 'leave' };
  const base = grantedSession().user;
  assert.equal(canOpenNotification(base, attendance), false);
  assert.equal(canOpenNotification(base, leave), false);
  assert.equal(canOpenNotification({ ...base, permissions: ['NOTIFICATION_VIEW', 'ATTENDANCE_SELF'] }, attendance), true);
  assert.equal(canOpenNotification({ ...base, permissions: ['NOTIFICATION_VIEW', 'LEAVE_SELF'] }, leave), true);
  assert.equal(canOpenNotification({ ...base, permissions: ['LEAVE_SELF', 'ATTENDANCE_SELF'] }, attendance), false);
  assert.equal(canOpenNotification({ ...base, permissions: ['LEAVE_SELF'] }, leave), false);
  assert.equal(canOpenNotification({ ...base, staffId: null, permissions: ['NOTIFICATION_VIEW', 'LEAVE_SELF'] }, leave), false);
  assert.equal(canOpenNotification({ ...base, permissions: ['NOTIFICATION_VIEW', 'ATTENDANCE_SELF'] }, { ...attendance, referenceDate: null }), false);
});

test('logout, account switching and employee relinking discard stale inbox and badge responses', async () => {
  for (const nextIdentity of [null, 'other-account:staff-2', 'account:other-staff']) {
    const list = deferred();
    const count = deferred();
    const store = createNotificationStore(api({ getNotifications: () => list.promise, getUnreadCount: () => count.promise }));
    store.getState().setIdentity('account:staff-1');
    const pending = Promise.all([store.getState().loadPage(), store.getState().refreshCount()]);
    store.getState().setIdentity(nextIdentity);
    list.resolve({ content: [{ id: 'private-old-user-record' }], totalElements: 1, totalPages: 1 });
    count.resolve({ unreadCount: 8 });
    await pending;
    assert.deepEqual(store.getState().items, []);
    assert.equal(store.getState().unreadCount, 0);
    assert.equal(store.getState().loading, false);
    assert.equal(store.getState().identity, nextIdentity);
  }
});

test('switching from all to unread ignores the slower previous page response', async () => {
  const all = deferred();
  const store = createNotificationStore(api({ getNotifications: ({ unreadOnly }) => unreadOnly
    ? Promise.resolve({ content: [{ id: 'unread' }], totalElements: 1, totalPages: 1 }) : all.promise }));
  store.getState().setIdentity('account');
  const slow = store.getState().loadPage();
  await store.getState().loadPage({ unreadOnly: true });
  all.resolve({ content: [{ id: 'already-read' }], totalElements: 1, totalPages: 1 });
  await slow;
  assert.equal(store.getState().unreadOnly, true);
  assert.deepEqual(store.getState().items, [{ id: 'unread' }]);
});

test('read state and count come from persisted API data and unread pages return to a valid page', async () => {
  let records = [{ id: 'one', readAt: null }, { id: 'two', readAt: null }];
  const calls = [];
  const store = createNotificationStore(api({
    getNotifications: async ({ unreadOnly, pageIndex }) => {
      calls.push(pageIndex);
      const content = unreadOnly ? records.filter(item => !item.readAt) : records;
      return { content, totalElements: content.length, totalPages: content.length ? 1 : 0 };
    },
    getUnreadCount: async () => ({ unreadCount: records.filter(item => !item.readAt).length }),
    readNotification: async id => { records = records.map(item => item.id === id ? { ...item, readAt: '2026-10-06T00:00:00Z' } : item); },
    readAllNotifications: async () => { records = records.map(item => ({ ...item, readAt: '2026-10-06T00:00:00Z' })); },
  }));
  store.getState().setIdentity('account');
  await store.getState().loadPage({ pageIndex: 2, unreadOnly: true });
  assert.deepEqual(calls, [2, 1]);
  assert.equal(await store.getState().markRead('one'), true);
  assert.deepEqual(store.getState().items.map(item => item.id), ['two']);
  assert.equal(store.getState().unreadCount, 1);
  await store.getState().markAllRead();
  assert.deepEqual(store.getState().items, []);
  assert.equal(store.getState().pageIndex, 1);
  assert.equal(store.getState().unreadCount, 0);
  await store.getState().loadPage({ unreadOnly: false });
  assert.ok(store.getState().items.every(item => item.readAt));
});

test('failed marking does not pretend success or clear unread data', async () => {
  const store = createNotificationStore(api({
    getNotifications: async () => ({ content: [{ id: 'one', readAt: null }], totalElements: 1, totalPages: 1 }),
    getUnreadCount: async () => ({ unreadCount: 1 }),
    readNotification: async () => { throw new Error('Mất kết nối'); },
  }));
  store.getState().setIdentity('account');
  await store.getState().loadPage();
  await store.getState().refreshCount();
  assert.equal(await store.getState().markRead('one'), false);
  assert.equal(store.getState().items[0].readAt, null);
  assert.equal(store.getState().unreadCount, 1);
  assert.equal(store.getState().actionError, 'Mất kết nối');
  assert.equal(store.getState().busyId, null);
});

test('pending read operation cannot refresh or open details after a different account signs in', async () => {
  const mutation = deferred();
  let refreshes = 0;
  const store = createNotificationStore(api({ readNotification: () => mutation.promise,
    getNotifications: async () => { refreshes++; return { content: [] }; } }));
  store.getState().setIdentity('first');
  const pending = store.getState().markRead('private');
  assert.equal(await store.getState().markRead('private'), false);
  store.getState().setIdentity('second');
  mutation.resolve({});
  assert.equal(await pending, false);
  assert.equal(refreshes, 0);
  assert.equal(store.getState().busyId, null);
});
