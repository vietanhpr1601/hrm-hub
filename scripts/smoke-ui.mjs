// Read-only HR route smoke test using the Chrome already installed on Windows.
// No npm dependency, user browser profile, or business record is modified.
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = path.join(root, '.runtime');
const base = 'http://127.0.0.1:5173';
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const chromePath = process.env.HRM_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const config = fs.readFileSync(path.join(root, 'hrm-backend/src/main/resources/application.yaml'), 'utf8');
const setting = name => process.env[name] || config.match(new RegExp('\\$\\{' + name + ':([^}]+)\\}'))?.[1];
const credentials = { username: setting('ADMIN_USERNAME'), password: setting('ADMIN_PASSWORD') };
const checks = [];
const errors = [];
const failedRequests = [];
const isReactWarning = entry => entry.type === 'console'
  && (entry.message === 'Warning: Failed %s type: %s%s'
    || entry.message.startsWith('Warning: %s: Support for defaultProps will be removed'));
let browser;
let socket;
let id = 0;
let activeRoute = '/login';
const pending = new Map();

function check(label, passed) {
  checks.push({ label, passed: !!passed });
  console.log(`${passed ? 'PASS' : 'FAIL'} ${label}`);
}

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const requestId = ++id;
    const timer = setTimeout(() => { pending.delete(requestId); reject(new Error(`CDP timeout: ${method}`)); }, 20000);
    pending.set(requestId, { resolve, reject, timer });
    socket.send(JSON.stringify({ id: requestId, method, params }));
  });
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error('Browser evaluation failed');
  return result.result?.value;
}

async function waitUntil(expression) {
  for (let i = 0; i < 60; i++) {
    if (await evaluate(expression)) return true;
    await sleep(250);
  }
  return false;
}

try {
  if (!credentials.username || !credentials.password) throw new Error('Admin credentials unavailable');
  fs.mkdirSync(runtime, { recursive: true });
  const profile = fs.mkdtempSync(path.join(runtime, 'hr-ui-profile-'));
  browser = spawn(chromePath, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000', 'about:blank'],
  { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  const port = await new Promise((resolve, reject) => {
    let output = '';
    const timer = setTimeout(() => reject(new Error('Chrome startup timeout')), 20000);
    browser.once('error', error => { clearTimeout(timer); reject(error); });
    browser.stderr.on('data', chunk => {
      output += chunk;
      const match = output.match(/DevTools listening on ws:\/\/127\.0\.0\.1:(\d+)\//);
      if (match) { clearTimeout(timer); resolve(Number(match[1])); }
    });
  });
  const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then(res => res.json());
  const target = targets.find(item => item.type === 'page');
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const request = pending.get(message.id);
      pending.delete(message.id);
      clearTimeout(request.timer);
      if (message.error) request.reject(new Error(message.error.message)); else request.resolve(message.result);
    }
    // Store only error categories; never print response bodies, tokens, or HR records.
    if (message.method === 'Runtime.exceptionThrown') errors.push({ type: 'runtime', route: activeRoute, message: message.params.exceptionDetails.text });
    if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') {
      errors.push({ type: 'console', route: activeRoute, message: String(message.params.args[0]?.value || 'console.error').slice(0, 1200) });
    }
    if (message.method === 'Network.responseReceived') {
      const { url, status } = message.params.response;
      if (status >= 400 && /^http:\/\/(localhost|127\.0\.0\.1):(5173|8080)\//.test(url)) failedRequests.push(new URL(url).pathname + ': ' + status);
    }
  });
  await send('Runtime.enable');
  await send('Network.enable');
  await send('Page.enable');
  await send('Page.navigate', { url: base + '/login' });
  if (!await waitUntil('!!document.querySelector("input[name=username]")')) throw new Error('Login form not rendered');
  for (const name of ['username', 'password']) {
    await evaluate(`(() => { const input = document.querySelector('input[name=${name}]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(credentials[name])}); input.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  }
  // The existing shared TextField commits its Formik value after a 500 ms debounce.
  await sleep(750);
  await evaluate('document.querySelector("button[type=submit]").click()');
  const loggedIn = await waitUntil('location.pathname === "/dashboard" && document.body.innerText.includes("Tổng nhân viên")');
  check('UI login and HR dashboard', loggedIn);
  if (!loggedIn) throw new Error('UI login failed');
  const routes = [
    ['/dashboard', 'Tổng nhân viên'],
    ['/administration/accounts', 'Tài khoản'],
    ['/staff/all', 'Hồ sơ nhân viên'],
    ['/department', 'Phòng ban'],
    ['/category/staff/position', 'vị trí'],
    ['/time-sheet-detail', 'chấm công'],
    ['/check-inout-result', 'chấm công'],
    ['/time-sheet-summary', 'Thống kê'],
    ['/my-leave', 'nghỉ phép'],
    ['/leave-requests', 'nghỉ phép'],
    ['/leave-balance', 'phép năm'],
    ['/salary/salary-item', 'Khoản lương'],
    ['/salary/salary-period', 'Kỳ lương'],
    ['/salary/payrolls', 'Bảng lương'],
    ['/recruitments', 'tuyển dụng'],
  ];
  for (const [route, label] of routes) {
    activeRoute = route;
    const before = failedRequests.length;
    await send('Page.navigate', { url: base + route });
    const rendered = await waitUntil(`location.pathname === ${JSON.stringify(route)} && document.body.innerText.toLowerCase().includes(${JSON.stringify(label.toLowerCase())}) && !document.body.innerText.includes('Bạn không có quyền truy cập') && !document.body.innerText.includes('Trang này đang được phát triển')`);
    await sleep(700);
    check('UI ' + route, rendered && failedRequests.length === before);
    check('No removed menu on ' + route, await evaluate('!Array.from(document.querySelectorAll("a[href]")).some(a => /^\\/(projects|tasks)(\\/|$)/.test(a.getAttribute("href"))) && !/Dự án|Kanban/.test(document.body.innerText)'));
  }
  for (const route of ['/projects', '/projects/00000000-0000-0000-0000-000000000001/view', '/projects/00000000-0000-0000-0000-000000000001/edit', '/tasks']) {
    await send('Page.navigate', { url: base + route });
    check('Removed route redirects: ' + route, await waitUntil('location.pathname === "/dashboard" && document.body.innerText.includes("Tổng nhân viên")'));
  }
  await sleep(500);
  const screenshot = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(runtime, 'hr-only-dashboard.png'), Buffer.from(screenshot.data, 'base64'));
  check('No browser runtime / import errors', errors.filter(entry => !isReactWarning(entry)).length === 0);
  check('No failed local HTTP requests', failedRequests.length === 0);
} catch (error) {
  check('Browser smoke completed', false);
  console.error(error.message.replace(/\bBearer\s+\S+/g, '[redacted]'));
  if (socket?.readyState === WebSocket.OPEN) {
    const screenshot = await send('Page.captureScreenshot', { format: 'png' }).catch(() => null);
    if (screenshot) fs.writeFileSync(path.join(runtime, 'hr-only-ui-failure.png'), Buffer.from(screenshot.data, 'base64'));
  }
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    await send('Browser.close').catch(() => {});
    socket.close();
  }
  if (browser && browser.exitCode === null) browser.kill();
  for (const request of pending.values()) clearTimeout(request.timer);
  const report = { checks, errorCount: errors.filter(entry => !isReactWarning(entry)).length,
    reactWarningCount: errors.filter(isReactWarning).length, consoleDiagnostics: errors, failedRequests };
  fs.writeFileSync(path.join(runtime, 'hr-only-ui-results.json'), JSON.stringify(report, null, 2));
  const failed = checks.filter(item => !item.passed).length;
  console.log(`UI checks: ${checks.length}; passed: ${checks.length - failed}; failed: ${failed}`);
  console.log(`React prop/defaultProps warnings: ${report.reactWarningCount} (recorded separately, not suppressed in the app).`);
  if (failed) process.exitCode = 1;
}
