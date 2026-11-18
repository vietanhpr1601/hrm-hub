import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawn, execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = path.join(root, '.runtime');
const stateFile = path.join(runtime, 'processes.json');
const backend = path.join(root, 'hrm-backend');
const frontend = path.join(root, 'hrm-frontend');
const jar = path.join(backend, 'target', 'hrm-0.0.1-SNAPSHOT.jar');
const vite = path.join(frontend, 'node_modules', 'vite', 'bin', 'vite.js');
const sourceConfig = fs.readFileSync(path.join(backend, 'src/main/resources/application.yaml'), 'utf8');
const setting = (name) => process.env[name] ?? sourceConfig.match(new RegExp('\\$\\{' + name + ':([^}]*)\\}'))?.[1];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const loadState = () => fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile, 'utf8')) : {};
const saveState = (state) => fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));

function databaseConfig() {
  const jdbc = setting('SPRING_DATASOURCE_URL');
  if (!jdbc?.startsWith('jdbc:postgresql://')) throw new Error('SPRING_DATASOURCE_URL must be a PostgreSQL JDBC URL.');
  const url = new URL(jdbc.slice(5));
  const database = decodeURIComponent(url.pathname.slice(1));
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(database)) throw new Error('Use a simple PostgreSQL database name (letters, digits, underscore).');
  return { jdbc, database, host: url.hostname, port: url.port || '5432', username: setting('SPRING_DATASOURCE_USERNAME'), password: setting('SPRING_DATASOURCE_PASSWORD') };
}

function findPsql() {
  if (process.env.PSQL_PATH && fs.existsSync(process.env.PSQL_PATH)) return process.env.PSQL_PATH;
  const base = path.join(process.env.ProgramFiles || 'C:\\Program Files', 'PostgreSQL');
  if (fs.existsSync(base)) {
    for (const version of fs.readdirSync(base).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }))) {
      const candidate = path.join(base, version, 'bin', 'psql.exe');
      if (fs.existsSync(candidate)) return candidate;
    }
  }
  throw new Error('PostgreSQL client not found. Set PSQL_PATH to the full path of psql.exe.');
}

function setupDatabase() {
  const config = databaseConfig();
  const psql = findPsql();
  const query = (database, sql) => execFileSync(psql, ['-X', '-w', '-h', config.host, '-p', config.port, '-U', config.username, '-d', database, '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql], {
    encoding: 'utf8', windowsHide: true, timeout: 15000,
    env: { ...process.env, PGPASSWORD: config.password, PGCONNECT_TIMEOUT: '5' },
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
  if (query('postgres', `SELECT 1 FROM pg_database WHERE datname = '${config.database}'`) !== '1') {
    query('postgres', `CREATE DATABASE "${config.database}" ENCODING 'UTF8'`);
    console.log(`Created PostgreSQL database ${config.database}.`);
  }
  const tableCount = query(config.database, "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'");
  // Upgrade only the email event key, retaining every previous payment message.
  query(config.database, fs.readFileSync(path.join(root, 'docs/database/20261026_payroll_email_events.sql'), 'utf8'));
  console.log(`PostgreSQL ready: ${config.host}:${config.port}/${config.database} (${tableCount} tables; existing data preserved).`);
  return config;
}

function occupied(port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    socket.setTimeout(1000);
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => { socket.destroy(); resolve(false); });
    socket.once('timeout', () => { socket.destroy(); resolve(false); });
  });
}

function processInfo(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return null;
  const command = `Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}' | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress`;
  const result = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], { encoding: 'utf8', windowsHide: true }).trim();
  return result ? JSON.parse(result) : null;
}

function isManaged(record) {
  if (!record) return false;
  const info = processInfo(record.pid);
  return !!info && info.CreationDate === record.created && info.CommandLine?.includes(record.marker);
}

async function launch(name, executable, args, cwd, env, marker) {
  const logFile = path.join(runtime, `${name}.log`);
  const out = fs.openSync(logFile, 'a');
  let child;
  try {
    child = spawn(executable, args, { cwd, env, detached: true, windowsHide: true, stdio: ['ignore', out, out] });
    await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
  } finally { fs.closeSync(out); }
  child.unref();
  let info;
  for (let attempt = 0; attempt < 5 && !info; attempt++) { info = processInfo(child.pid); if (!info) await sleep(200); }
  if (!info?.CommandLine?.includes(marker)) throw new Error(`${name} did not start. See ${logFile}`);
  const state = loadState();
  state[name] = { pid: child.pid, created: info.CreationDate, marker, logFile };
  saveState(state);
  return state[name];
}

async function waitFor(url, expectedStatus, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (response.status === expectedStatus) return;
    } catch { /* Server still starting. */ }
    await sleep(500);
  }
  throw new Error(`Startup timed out: ${url}. Check .runtime logs.`);
}

async function start() {
  const config = setupDatabase();
  if (!fs.existsSync(jar) || !fs.existsSync(vite)) throw new Error('Build/dependencies missing. Run .\\build.cmd, then .\\start.cmd.');
  const state = loadState();
  for (const [name, port] of [['backend', 8080], ['frontend', 5173]]) {
    if (await occupied(port) && !isManaged(state[name])) throw new Error(`Port ${port} is used by another process. Stop the old ${name} terminal with Ctrl+C, then run start.cmd again.`);
  }
  const secretFile = path.join(runtime, 'jwt-secret');
  if (!fs.existsSync(secretFile)) fs.writeFileSync(secretFile, crypto.randomBytes(48).toString('hex'));
  if (!(await occupied(8080))) {
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java.exe') : 'java.exe';
    await launch('backend', java, ['-jar', jar, '--server.address=127.0.0.1', '--server.port=8080', '--spring.jpa.show-sql=false', '--debug=false'], backend, {
      ...process.env, SPRING_PROFILES_ACTIVE: '',
      SPRING_DATASOURCE_URL: config.jdbc, SPRING_DATASOURCE_USERNAME: config.username, SPRING_DATASOURCE_PASSWORD: config.password,
      JWT_SECRET: process.env.JWT_SECRET || fs.readFileSync(secretFile, 'utf8'),
    }, jar);
  }
  await waitFor('http://127.0.0.1:8080/api/auth/getCurrentUser', 401, 90000);
  if (!(await occupied(5173))) {
    await launch('frontend', process.execPath, [vite, '--host', '127.0.0.1', '--port', '5173', '--strictPort'], frontend, {
      ...process.env, VITE_API_ENDPOINT: '/api', BROWSER: 'none',
    }, vite);
  }
  await waitFor('http://127.0.0.1:5173/', 200, 30000);
  console.log('HRM is running: http://127.0.0.1:5173');
  console.log('API: http://127.0.0.1:8080/api');
  console.log('Logs: .runtime/backend.log and .runtime/frontend.log');
  console.log('Stop: .\\stop.cmd | Status: .\\status.cmd');
}

async function stop() {
  const state = loadState();
  for (const name of ['frontend', 'backend']) {
    if (isManaged(state[name])) { process.kill(state[name].pid); console.log(`Stopped ${name}.`); }
    else if (state[name]) console.log(`${name} is already stopped; no other process was changed.`);
    delete state[name];
    saveState(state);
  }
  console.log('PostgreSQL remains running; database contents are preserved.');
}

try {
  fs.mkdirSync(runtime, { recursive: true });
  switch (process.argv[2]) {
    case 'database': setupDatabase(); break;
    case 'start': await start(); break;
    case 'stop': await stop(); break;
    case 'status':
      for (const [name, port] of [['PostgreSQL', Number(databaseConfig().port)], ['Backend', 8080], ['Frontend', 5173]]) console.log(`${name} port ${port}: ${await occupied(port) ? 'listening' : 'stopped'}`);
      console.log('Start: .\\start.cmd | Stop: .\\stop.cmd');
      break;
    default: throw new Error('Usage: node scripts/local.mjs start|stop|status|database');
  }
} catch (error) {
  console.error(error.message);
  if (error.stderr) console.error(String(error.stderr).trim());
  process.exitCode = 1;
}
