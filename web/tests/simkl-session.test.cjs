const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const next = require('next/server');
const { load } = require('./load.cjs');
const service = load('lib/server/simklSession.ts');
const at = 'simkl_at_' + 'a'.repeat(32), rt = 'simkl_rt_' + 'r'.repeat(32);
const token = { access_token: at, refresh_token: rt, token_type: 'Bearer', scope: 'media:read media:write', expires_in: 604800 };
const device = { device_code: 'private-device', user_code: 'ABCD-EFGH', verification_uri_complete: 'https://simkl.com/pin?user_code=ABCD-EFGH', expires_in: 900, interval: 5 };
const json = (data, status = 200) => Response.json(data, { status });
class MemoryStore {
  entries = new Map(); counter = 0;
  async read(key) { return this.entries.get(key) ?? null; }
  async compareAndSet(key, data, etag) {
    if ((this.entries.get(key)?.etag ?? null) !== etag) return false;
    this.entries.set(key, { data, etag: String(++this.counter) }); return true;
  }
}
function harness(handler) {
  const store = new MemoryStore(), clock = { now: 1_000_000 }, requests = [];
  const fetcher = async (url, init) => {
    const request = { url: new URL(url), init, fields: new URLSearchParams(init.body) };
    requests.push(request);
    return handler ? handler(request, requests.length) : json(token);
  };
  const sessions = new service.SimklSessions(store, 'public-client', fetcher, () => clock.now);
  const id = service.newSimklSessionId();
  return { sessions, store, clock, requests, id, another: () => new service.SimklSessions(store, 'public-client', fetcher, () => clock.now),
    migrate: () => sessions.migrate(id, 'profile', { refresh_token: rt, connection_id: 'stable-connection' }) };
}
const read = (h, sessions = h.sessions, query = '') => sessions.request(h.id, 'profile', 'stable-connection', '/scrobble/stop', 'POST', new URLSearchParams(query), '{}');

test('server owns PKCE/device credentials and returns only public approval details', async () => {
  const h = harness(r => json(r.url.pathname === '/oauth2/device' ? device : token));
  const code = await h.sessions.start(h.id, 'profile');
  assert.equal(code.user_code, device.user_code);
  assert.equal(code.device_code, undefined);
  assert.equal(h.requests[0].fields.get('scope'), 'media:read media:write');
  assert.equal(h.requests[0].fields.get('code_challenge_method'), 'S256');
  assert.equal(await h.sessions.poll(h.id, 'profile', code.user_code), null);
  h.clock.now += 5000;
  const marker = await h.sessions.poll(h.id, 'profile', code.user_code);
  const verifier = h.requests[1].fields.get('code_verifier');
  assert.equal(crypto.createHash('sha256').update(verifier).digest('base64url'), h.requests[0].fields.get('code_challenge'));
  assert.equal(marker.server_session, true);
  assert.equal(marker.refresh_token, undefined);
  assert.equal(marker.access_token.includes(at), false);
  assert.equal(JSON.stringify([...h.store.entries]).includes(rt), false);
  assert.equal(JSON.stringify([...h.store.entries]).includes('private-device'), false);
});

test('server persists pending/slow_down poll intervals and rejects expired PINs', async () => {
  const h = harness((r, n) => json(n === 1 ? device : n === 2 ? { error: 'authorization_pending' } : n === 3 ? { error: 'slow_down' } : token, n === 2 || n === 3 ? 400 : 200));
  const code = await h.sessions.start(h.id, 'profile');
  h.clock.now += 5000;
  assert.equal(await h.sessions.poll(h.id, 'profile', code.user_code), null);
  h.clock.now += 5000;
  assert.equal(await h.sessions.poll(h.id, 'profile', code.user_code), null);
  h.clock.now += 5000;
  assert.equal(await h.another().poll(h.id, 'profile', code.user_code), null);
  assert.equal(h.requests.length, 3);
  h.clock.now += 5000;
  assert.equal((await h.sessions.poll(h.id, 'profile', code.user_code)).server_session, true);
  await assert.rejects(h.sessions.poll(h.id, 'profile', code.user_code), /expired_token/);
});

test('migration is idempotent, encrypted and scoped to a single profile/cookie', async () => {
  const h = harness();
  const marker = await h.migrate();
  await h.migrate();
  assert.equal(h.requests.length, 1);
  assert.equal(h.requests[0].fields.get('grant_type'), 'refresh_token');
  assert.equal((await h.another().restore(h.id, 'profile')).connection_id, marker.connection_id);
  assert.equal(await h.sessions.restore(service.newSimklSessionId(), 'profile'), null);
  await assert.rejects(h.sessions.restore(h.id, 'another-profile'), /session_required/);
  await assert.rejects(h.sessions.restore('../invalid', 'profile'), /session_required/);
  assert.ok([...h.store.entries.values()].every(entry => !entry.data.includes(rt) && !entry.data.includes(at)));
});

test('invalid token responses/scopes never become usable sessions', async () => {
  for (const invalid of [{ ...token, scope: 'media:read' }, { ...token, expires_in: 0 }, { ...token, token_type: 'Basic' }, { ...token, access_token: 'invalid' }]) {
    const h = harness(() => json(invalid));
    await assert.rejects(h.migrate(), /invalid_grant_permissions/);
    assert.equal(await h.sessions.restore(h.id, 'profile'), null);
  }
});

test('refresh across separate server instances has one owner and stable cache identity', async () => {
  const h = harness(async r => { await new Promise(setImmediate); return json(r.url.pathname === '/oauth2/token' ? token : { ok: true }); });
  await h.migrate(); h.clock.now += 604800_000;
  await Promise.all([read(h), read(h, h.another()), read(h, h.another())]);
  assert.equal(h.requests.filter(r => r.url.pathname === '/oauth2/token').length, 2, 'one migration plus one shared refresh');
  assert.equal((await h.sessions.restore(h.id, 'profile')).connection_id, 'stable-connection');
});

test('server forces 90 percent on start/stop/checkin, not pause, and strips credential query parameters', async () => {
  const h = harness(r => json(r.url.pathname === '/oauth2/token' ? token : { ok: true }));
  await h.migrate();
  for (const action of ['start', 'stop', 'checkin', 'pause']) {
    await h.sessions.request(h.id, 'profile', 'stable-connection', `/scrobble/${action}`, 'POST', new URLSearchParams('min_progress=50&access_token=leak&refresh_token=leak&client_secret=leak'), '{}');
    const r = h.requests.at(-1);
    assert.equal(r.url.searchParams.get('min_progress'), action === 'pause' ? '50' : '90');
    assert.equal(r.url.href.includes('leak'), false);
    assert.equal(r.init.headers.Authorization, `Bearer ${at}`);
  }
});

test('a rejected access token refreshes once and retries with the replacement', async () => {
  let grants = 0, reads = 0;
  const h = harness(r => r.url.pathname === '/oauth2/token' ? json({ ...token, access_token: grants++ ? 'simkl_at_' + 'b'.repeat(32) : at }) : json({}, ++reads === 1 ? 401 : 200));
  await h.migrate();
  assert.equal((await read(h)).status, 200);
  assert.equal(grants, 2); assert.equal(reads, 2);
  assert.notEqual(h.requests.at(-1).init.headers.Authorization, `Bearer ${at}`);
});

test('definitively revoked refresh grants clear the session; transient failures keep it', async () => {
  for (const reason of ['invalid_grant', 'temporarily_unavailable']) {
    let n = 0;
    const h = harness(() => ++n === 1 ? json(token) : json({ error: reason }, reason === 'invalid_grant' ? 400 : 503));
    await h.migrate(); h.clock.now += 604800_000;
    await assert.rejects(read(h));
    assert.equal(Boolean(await h.sessions.restore(h.id, 'profile')), reason !== 'invalid_grant');
  }
});

test('disconnect revokes only the captured grant and accepts an empty success body', async () => {
  const h = harness(r => r.url.pathname === '/oauth2/revoke' ? new Response(null, { status: 200 }) : json(token));
  await h.migrate();
  await assert.rejects(h.sessions.disconnect(h.id, 'profile', 'newer-connection'), /session_changed/);
  assert.equal(h.requests.length, 1);
  const result = await h.sessions.disconnect(h.id, 'profile', 'stable-connection');
  assert.equal(result.revoked, true);
  assert.equal(h.requests[1].fields.get('token'), rt);
  assert.equal(h.requests[1].fields.get('client_id'), 'public-client');
  assert.equal(await h.sessions.restore(h.id, 'profile'), null);
  await assert.rejects(read(h), /session_required/);
});

test('failed provider revocation is retained encrypted and retried without restoring the connection', async () => {
  let offline = true;
  const h = harness(r => { if (r.url.pathname === '/oauth2/revoke') { if (offline) throw new Error('offline'); return new Response(null); } return json(token); });
  await h.migrate();
  assert.equal((await h.sessions.disconnect(h.id, 'profile')).revoked, false);
  assert.equal(await h.sessions.restore(h.id, 'profile'), null);
  offline = false; h.clock.now += 60_000;
  assert.equal(await h.another().restore(h.id, 'profile'), null);
  assert.equal(h.requests.filter(r => r.url.pathname === '/oauth2/revoke').length, 2);
});

test('disconnect racing a refresh cannot resurrect a removed grant', async () => {
  let complete, started;
  const ready = new Promise(resolve => { started = resolve; });
  let n = 0;
  const h = harness(r => {
    if (r.url.pathname === '/oauth2/revoke') return new Response(null);
    if (++n === 2) { started(); return new Promise(resolve => { complete = resolve; }); }
    return json(token);
  });
  await h.migrate(); h.clock.now += 604800_000;
  const request = read(h); await ready;
  const disconnect = h.another().disconnect(h.id, 'profile', 'stable-connection');
  complete(json(token)); await request; await disconnect;
  assert.equal(await h.sessions.restore(h.id, 'profile'), null);
});

test('persistent file store survives instance replacement and rejects competing stale writes', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'arvio-simkl-session-'));
  const { FileSimklSessionStore } = load('lib/server/simklSessionStore.ts', { '@netlify/blobs': {} });
  try {
    const a = new FileSimklSessionStore(directory), b = new FileSimklSessionStore(directory);
    assert.equal(await a.compareAndSet('session', 'encrypted-a', null), true);
    const saved = await b.read('session');
    const writes = await Promise.all([a.compareAndSet('session', 'encrypted-b', saved.etag), b.compareAndSet('session', 'encrypted-c', saved.etag)]);
    assert.equal(writes.filter(Boolean).length, 1);
    assert.notEqual((await new FileSimklSessionStore(directory).read('session')).etag, saved.etag);
    assert.ok((await fs.readdir(directory)).every(file => !file.endsWith('.lock') && !file.endsWith('.tmp')));
  } finally {
    assert.equal(path.dirname(directory), os.tmpdir());
    await fs.rm(directory, { recursive: true, force: true });
  }
});

function httpHarness(handler, env = {}) {
  const h = harness(handler);
  const helpers = load('lib/server/simklSessionHttp.ts', { 'next/server': next, './simklSession': service,
    './simklSessionStore': { simklSessionStore: () => h.store } }, { process: { env } });
  // Keep the real HTTP/cookie validation, but use the deterministic provider in protocol tests.
  helpers.simklSessions = () => h.sessions;
  const route = load('app/api/simkl/session/route.ts', { 'next/server': next,
    '@/lib/server/simklSession': service, '@/lib/server/simklSessionHttp': helpers });
  return { ...h, helpers, route };
}
function request(action, headers = {}, cookie) {
  return new next.NextRequest('https://web.arvio.tv/api/simkl/session', {
    method: action ? 'POST' : 'GET', headers: { origin: 'https://web.arvio.tv', 'x-simkl-profile': 'profile', ...headers, ...(cookie ? { cookie } : {}) },
    body: action ? JSON.stringify(action) : undefined
  });
}

test('session route uses secure HttpOnly Strict cookies and returns no OAuth credentials', async () => {
  const h = httpHarness(r => json(r.url.pathname === '/oauth2/device' ? device : token));
  const response = await h.route.POST(request({ action: 'start' }));
  const cookie = response.headers.get('set-cookie');
  assert.match(cookie, /^__Host-arvio_simkl_/);
  assert.match(cookie, /HttpOnly/); assert.match(cookie, /Secure/); assert.match(cookie, /SameSite=strict/i);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const code = await response.json();
  assert.equal(code.device_code, undefined);
  h.clock.now += 5000;
  const poll = await h.route.POST(request({ action: 'poll', user_code: code.user_code }, {}, cookie.split(';')[0]));
  assert.equal((await poll.json()).token.server_session, true);
});

test('session route rejects cross-origin requests, missing profiles and malformed JSON', async () => {
  const h = httpHarness();
  assert.equal((await h.route.POST(request({ action: 'start' }, { origin: 'https://evil.test' }))).status, 403);
  assert.equal((await h.route.GET(request(null, { 'sec-fetch-site': 'cross-site' }))).status, 403);
  assert.equal((await h.route.GET(request(null, { 'x-simkl-profile': '' }))).status, 400);
  const malformed = new next.NextRequest('https://web.arvio.tv/api/simkl/session', { method: 'POST', headers: { 'x-simkl-profile': 'profile' }, body: '{' });
  assert.equal((await h.route.POST(malformed)).status, 400);
  assert.equal(h.requests.length, 0);
});

test('origin checks and Secure cookies use the public host/protocol behind Next and reverse proxies', async () => {
  const h = httpHarness();
  const local = new next.NextRequest('http://localhost:3000/api/simkl/session', { headers: {
    host: '127.0.0.1:3094', origin: 'http://127.0.0.1:3094', 'x-simkl-profile': 'profile'
  } });
  assert.equal(h.helpers.simklRequestContext(local).secure, false);
  const proxied = new next.NextRequest('http://localhost:3000/api/simkl/session', { headers: {
    host: 'internal:3000', origin: 'https://self.invalid', 'x-forwarded-host': 'self.invalid', 'x-forwarded-proto': 'https', 'x-simkl-profile': 'profile'
  } });
  assert.equal(h.helpers.simklRequestContext(proxied).secure, true);
  assert.match(h.helpers.simklRequestContext(proxied).cookie, /^__Host-/);
});

test('raw browser V2 OAuth endpoints/tokens are blocked and server proxy retains threshold/allowlist', async () => {
  const h = httpHarness(r => json(r.url.pathname === '/oauth2/token' ? token : { ok: true }));
  const marker = await h.migrate();
  const proxy = load('app/api/simkl/[...path]/route.ts', { 'next/server': next, '@/lib/server/simklSessionHttp': h.helpers }, { process: { env: {} } });
  const call = (segments, headers = {}) => proxy.POST(new next.NextRequest(`https://web.arvio.tv/api/simkl/${segments.join('/')}`, {
    method: 'POST', headers: { 'x-simkl-profile': 'profile', 'x-simkl-connection': marker.connection_id,
      cookie: `${service.simklCookieName('profile', true)}=${h.id}`, ...headers }, body: '{}'
  }), { params: Promise.resolve({ path: segments }) });
  assert.equal((await call(['oauth2', 'token'])).status, 409);
  assert.equal((await call(['scrobble', 'stop'], { 'x-user-token': at })).status, 409);
  assert.equal((await call(['delete', 'account'])).status, 403);
  assert.equal((await call(['scrobble', 'stop'])).status, 200);
  assert.equal(h.requests.at(-1).url.searchParams.get('min_progress'), '90');
});

test('Netlify sessions use durable strong reads/CAS and unsupported ephemeral hosting fails closed', async () => {
  const calls = [];
  const sdk = { getStore: options => { calls.push(options); return {
    getWithMetadata: async (_key, options) => { calls.push(options); return { data: 'ciphertext', etag: 'version' }; },
    set: async (_key, _data, options) => { calls.push(options); return { modified: true }; }
  }; } };
  const hosted = load('lib/server/simklSessionStore.ts', { '@netlify/blobs': sdk }, { process: { env: { NETLIFY: 'true' } } }).simklSessionStore();
  await hosted.read('key'); await hosted.compareAndSet('key', 'data', null); await hosted.compareAndSet('key', 'data', 'version');
  assert.equal(calls[0].consistency, 'strong'); assert.equal(calls[1].consistency, 'strong');
  assert.equal(calls[2].onlyIfNew, true); assert.equal(calls[3].onlyIfMatch, 'version');
  const unsupported = load('lib/server/simklSessionStore.ts', { '@netlify/blobs': sdk }, { process: { env: { VERCEL: '1' } } });
  assert.throws(() => unsupported.simklSessionStore(), /persistent/);
});
