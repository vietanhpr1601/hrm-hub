// Run with: node scripts/smoke-api.mjs
// Optional: SMOKE_API_BASE, ADMIN_USERNAME, ADMIN_PASSWORD.
// Only loopback requests are allowed. Business records and credentials are never logged.
import { readFile } from 'node:fs/promises';

const results = [];
let accessToken;

const isPage = (data) => Array.isArray(data?.content) && Number.isInteger(data.totalElements);
const isObject = (data) => data !== null && typeof data === 'object' && !Array.isArray(data);
const isHrDashboard = (data) => isObject(data?.kpiSummary)
  && Number.isInteger(data.kpiSummary.totalStaff)
  && Array.isArray(data.departmentDistribution)
  && isObject(data.recruitmentPipeline)
  && !('projectOverview' in data)
  && !('activeProjects' in data.kpiSummary)
  && !('nearDeadlineProjects' in data.kpiSummary)
  && data.recentActivities.every(item => !['PROJECT', 'TASK'].includes(item.type));
const paging = { pageIndex: 1, pageSize: 5, keyword: '' };

function localApiBase(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)
      || !['127.0.0.1', '[::1]', 'localhost'].includes(url.hostname)
      || url.username || url.password || url.search || url.hash
      || url.pathname.replace(/\/+$/, '') !== '/api') {
    throw new Error('CONFIG');
  }
  if (url.hostname === 'localhost') url.hostname = '127.0.0.1';
  return url.href.replace(/\/+$/, '');
}

async function credentials() {
  let config = '';
  if (!process.env.ADMIN_USERNAME || !process.env.ADMIN_PASSWORD) {
    config = await readFile(new URL('../hrm-backend/src/main/resources/application.yaml', import.meta.url), 'utf8');
  }
  const value = (name) => process.env[name] || config.match(new RegExp(`\\$\\{${name}:([^}]+)\\}`))?.[1];
  const username = value('ADMIN_USERNAME');
  const password = value('ADMIN_PASSWORD');
  if (!username || !password) throw new Error('CONFIG');
  return { username, password };
}

async function main() {
  const base = localApiBase(process.env.SMOKE_API_BASE || 'http://127.0.0.1:8080/api');
  const loginCredentials = await credentials();
  console.log(`API smoke: ${base} (read-only business checks)`);

  async function check(label, path, { method = 'GET', body, token = accessToken, expected = 200, validate } = {}) {
    try {
      const response = await fetch(`${base}${path}`, {
        method,
        redirect: 'error',
        signal: AbortSignal.timeout(15000),
        headers: {
          Accept: 'application/json',
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
      const envelope = await response.json().catch(() => null);
      const passed = response.status === expected
        && envelope?.status === expected
        && (!validate || validate(envelope?.data));
      results.push({ label, passed });
      console.log(`${passed ? 'PASS' : 'FAIL'} ${label}: HTTP ${response.status}${!passed && response.status === expected ? ' (response contract mismatch)' : ''}`);
      return passed ? envelope?.data : undefined;
    } catch {
      results.push({ label, passed: false });
      console.log(`FAIL ${label}: request failed or timed out`);
      return undefined;
    }
  }

  await check('Protected route rejects anonymous access', '/dashboard/summary', { token: null, expected: 401 });
  const login = await check('Login', '/auth/login', {
    method: 'POST',
    body: loginCredentials,
    token: null,
    validate: (data) => typeof data?.accessToken === 'string' && typeof data?.refreshToken === 'string',
  });
  if (!login) {
    console.log('Authenticated checks stopped because login did not succeed.');
    return;
  }
  accessToken = login.accessToken;

  try {
    await check('Current user', '/auth/getCurrentUser', { validate: (data) => isObject(data) && Array.isArray(data.roles) });
    await check('Invalid refresh token rejected', '/auth/refresh', {
      method: 'POST', body: { refreshToken: 'invalid-smoke-token' }, token: null, expected: 401,
    });
    const refreshed = await check('Refresh session', '/auth/refresh', {
      method: 'POST', body: { refreshToken: login.refreshToken }, token: null,
      validate: (data) => typeof data?.accessToken === 'string' && data.accessToken.length > 0,
    });
    if (refreshed) {
      accessToken = refreshed.accessToken;
      await check('Current user with refreshed session', '/auth/getCurrentUser', { validate: isObject });
    }

    const listChecks = [
      ['HR-only dashboard', '/dashboard/summary', isHrDashboard],
      ['Department options', '/departments/all', Array.isArray],
      ['Position options', '/positions/all', Array.isArray],
      ['Staff department options', '/staffs/departments', Array.isArray],
      ['Staff position options', '/staffs/positions', Array.isArray],
      ['User options', '/users/unpaginated', Array.isArray],
      ['Roles', '/roles', Array.isArray],
      ['Payrolls', '/payrolls', Array.isArray],
      ['Periods', '/periods', Array.isArray],
      ['Salary items', '/salary-items', Array.isArray],
    ];
    for (const [label, path, validate] of listChecks) {
      await check(label, path, { validate });
    }

    const searchChecks = [
      ['Departments search', '/departments/paging'],
      ['Positions search', '/positions/paging'],
      ['Staff search', '/staffs/paging'],
      ['Users search', '/users/paging'],
      ['Recruitments search', '/recruitments/paging'],
      ['Candidates search', '/candidates/paging'],
      ['Shifts search', '/shifts/paging'],
      ['Timesheets search', '/timesheets/paging'],
      ['Periods search', '/periods/paging'],
      ['Leave requests search', '/leave-requests/paging'],
      ['Leave balances search', `/leave-requests/balance/paging?year=${new Date().getFullYear()}`],
    ];
    for (const [label, path] of searchChecks) {
      await check(label, path, { method: 'POST', body: paging, validate: isPage });
    }
    for (const path of ['/projects', '/tasks', '/projects/paging', '/tasks/kanban']) {
      await check('Removed API ' + path, path, {
        ...(path.endsWith('/paging') || path.endsWith('/kanban') ? { method: 'POST', body: paging } : {}),
        expected: 404,
      });
    }
  } finally {
    await check('Logout', '/auth/logout', { method: 'POST' });
    accessToken = undefined;
  }
}

try {
  await main();
} catch {
  console.error('FAIL smoke setup: require a loopback /api URL and admin credentials from environment or application.yaml.');
  process.exitCode = 1;
} finally {
  const failed = results.filter((result) => !result.passed).length;
  console.log(`Checks: ${results.length}; passed: ${results.length - failed}; failed: ${failed}`);
  if (failed || results.length === 0) process.exitCode = 1;
}
