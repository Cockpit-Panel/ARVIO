const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');

function harness(fetcher, clock = { now: 100_000 }, http = {}, saved = storage()) {
  class Clock extends Date { static now() { return clock.now; } }
  const { SimklClient } = load('lib/simkl.ts', {
    './config': { config: { simklV2ClientId: 'public-v2-client', simklClientId: 'legacy-client' } },
    './storage': saved, './sync': {}, './http': http, './tmdb': {}
  }, { fetch: fetcher, Date: Clock });
  const client = new SimklClient(); client.setProfile('qa-profile');
  return { client, saved, clock };
}
const response = (value, status = 200) => new Response(JSON.stringify(value), { status });
const device = { user_code: 'ABCD-EFGH', verification_url: 'https://simkl.com/pin?user_code=ABCD-EFGH', expires_in: 900, interval: 5 };
const token = { access_token: 'simkl_session_same-grant', server_session: true, connection_id: 'same-grant' };
const old = { access_token: 'simkl_at_old', refresh_token: 'simkl_rt_old', connection_id: 'same-grant' };
const key = 'arvio.web.simkl.token:qa-profile';
function legacyStorage() { const saved = storage(); saved.saveStored(key, old); return saved; }

test('browser PIN flow sees only public approval details and a non-secret session marker', async () => {
  const requests = [];
  const h = harness(async (url, init) => { requests.push({ url, init, body: JSON.parse(init.body) }); return response(requests.length === 1 ? device : { token }); });
  const code = await h.client.beginPinAuth();
  assert.equal(code.verification_url, device.verification_url);
  assert.equal(requests[0].url, '/api/simkl/session');
  assert.equal(requests[0].body.action, 'start');
  assert.equal(requests[0].init.credentials, 'same-origin');
  assert.equal(requests[0].init.headers['x-simkl-profile'], 'qa-profile');
  assert.equal(await h.client.pollPinToken(code.user_code), null, 'cannot poll early');
  h.clock.now += 5000;
  const grant = await h.client.pollPinToken(code.user_code);
  h.client.setToken(grant);
  assert.deepEqual(h.saved.loadStored(key), token);
  assert.equal(JSON.stringify(requests).includes('code_verifier'), false);
  assert.equal(JSON.stringify(h.saved.loadStored(key)).includes('refresh_token'), false);
});

test('browser polling retains pending server-side authorization without extra immediate calls', async () => {
  let calls = 0;
  const h = harness(async () => { calls++; return response(calls === 1 ? device : { token: calls === 2 ? null : token }); });
  const code = await h.client.beginPinAuth(); h.clock.now += 5000;
  assert.equal(await h.client.pollPinToken(code.user_code), null);
  assert.equal(await h.client.pollPinToken(code.user_code), null);
  assert.equal(calls, 2);
  h.clock.now += 5000;
  assert.equal((await h.client.pollPinToken(code.user_code)).access_token, token.access_token);
});

test('raw V2 credentials cannot be accepted or persisted by the browser', async () => {
  const h = harness(async (_url, init) => response(JSON.parse(init.body).action === 'start' ? device : { token: old }));
  const code = await h.client.beginPinAuth(); h.clock.now += 5000;
  await assert.rejects(h.client.pollPinToken(code.user_code), /Invalid SIMKL session/);
  assert.throws(() => h.client.setToken(old), /remain on the server/);
  h.client.setProfile('another-profile');
  await assert.rejects(h.client.pollPinToken(code.user_code), /expired/);
  assert.equal(h.client.token, null);
});

test('existing browser V2 grants migrate once, sharing the request and preserving cache identity', async () => {
  const requests = [];
  const h = harness(async (_url, init) => { requests.push(JSON.parse(init.body)); await new Promise(setImmediate); return response({ token }); }, undefined, {}, legacyStorage());
  const scope = h.client.scope();
  h.client.snapshot = { scope, checkedAt: 100, complete: true };
  await Promise.all([h.client.restoreSession(), h.client.restoreSession(), h.client.restoreSession()]);
  assert.equal(requests.length, 1);
  assert.equal(requests[0].action, 'migrate');
  assert.equal(requests[0].refresh_token, old.refresh_token);
  assert.equal(h.client.scope(), scope);
  assert.equal(h.client.snapshot.checkedAt, 100, 'migration preserves the same account snapshot');
  assert.deepEqual(h.saved.loadStored(key), token);
});

test('V1 tokens still restore without being rewritten as V2 grants', () => {
  const h = harness();
  h.client.setToken({ access_token: 'legacy-v1-token' });
  h.client.setProfile('other'); h.client.setProfile('qa-profile');
  assert.equal(h.client.token.access_token, 'legacy-v1-token');
  assert.equal(h.client.token.refresh_token, undefined);
});

test('failed migration keeps the existing grant until server acknowledgement', async () => {
  const h = harness(async () => response({ error: 'temporarily_unavailable' }, 503), undefined, {}, legacyStorage());
  await assert.rejects(h.client.restoreSession(), /temporarily_unavailable/);
  assert.deepEqual(h.saved.loadStored(key), old);
});

test('disconnect during session restoration cannot silently reconnect the account', async () => {
  let finish;
  const h = harness((_url, init) => init.method === 'GET' ? new Promise(resolve => { finish = resolve; }) : response({ disconnected: true }));
  h.client.setToken(token);
  const refresh = h.client.restoreSession();
  await h.client.disconnect(); finish(response({ token })); await refresh;
  assert.equal(h.client.token, null);
  assert.equal(h.saved.loadStored('arvio.web.simkl.token:qa-profile', null), null);
});

function listsHarness(reader) {
  const simklClient = { token, isV2Connected: true, customListsRequest: reader };
  return load('lib/simklLists.ts', { './simkl': { simklClient } });
}
test('a profile switch during migration cannot send the old request through another account', async () => {
  let finish, reads = 0;
  const h = harness(() => new Promise(resolve => { finish = resolve; }), { now: 100_000 }, { jsonRequest: async () => { reads++; return {}; } }, legacyStorage());
  const request = h.client.customListsRequest('/lists/14462');
  h.client.setProfile('another-profile'); finish(response({ token }));
  await assert.rejects(request, /profile changed/);
  assert.equal(reads, 0);
});

test('server sessions restore after reload without credentials in browser requests', async () => {
  const reads = [];
  const h = harness(async () => response({ token }), undefined, { jsonRequest: async (url, init) => { reads.push({ url, init }); return {}; } });
  await h.client.restoreSession();
  await h.client.customListsRequest('/lists/14462');
  assert.equal(reads[0].init.headers['x-user-token'], undefined);
  assert.equal(reads[0].init.headers['x-simkl-connection'], token.connection_id);
  assert.equal(h.client.isV2Connected, true);
});

test('offline disconnect stays disconnected and retries the captured grant on restoration', async () => {
  let online = false;
  const requests = [];
  const h = harness(async (_url, init) => { requests.push(init); if (!online) throw new Error('offline'); return response({ disconnected: true }); });
  h.client.setToken(token);
  await assert.rejects(h.client.disconnect(), /offline/);
  assert.equal(h.client.isConnected, false);
  h.client.setProfile('other'); h.client.setProfile('qa-profile');
  online = true;
  await h.client.restoreSession();
  assert.equal(h.client.isConnected, false);
  assert.equal(JSON.parse(requests[1].body).action, 'disconnect');
  assert.equal(requests[1].headers['x-simkl-connection'], token.connection_id);
  assert.equal(h.saved.loadStored('arvio.web.simkl.disconnect:qa-profile', null), null);
});
test('SIMKL URL parsing rejects lookalike hosts, credentials, discovery pages and invalid IDs', () => {
  const lists = listsHarness();
  assert.equal(lists.parseSimklListUrl('simkl.com/5/list/14462/best-mindfucks/?sort=position').id, 14462);
  for (const url of ['https://simkl.com.evil.test/5/list/14462', 'https://evil.simkl.com/5/list/14462', 'https://simkl.com/lists/official', 'https://simkl.com/lists/0', 'https://user@simkl.com/lists/1']) assert.equal(lists.parseSimklListUrl(url), null);
});

test('SIMKL list API pages in owner order, caches navigation and never accepts premium_only', async () => {
  const requests = [];
  const lists = listsHarness(async path => { requests.push(path); return { id: 14462, name: 'Real list', media_type: 'tv',
    pagination: { total_pages: 2 }, items: [{ title: path.endsWith('page=1') ? 'Dark' : '1899', ids: { tmdb: path.endsWith('page=1') ? 70523 : 90669 } }] }; });
  const list = await lists.loadSimklCustomList('https://simkl.com/5/list/14462');
  assert.deepEqual(Array.from(list.items, x => x.title), ['Dark', '1899']);
  await lists.loadSimklCustomList('https://simkl.com/lists/14462');
  assert.equal(requests.length, 2);
  assert.ok(requests.every(path => !path.includes('sort=')));
  const free = listsHarness(async () => ({ error: 'premium_only', item: { title: 'Upgrade' } }));
  await assert.rejects(free.loadSimklCustomList('simkl.com/5/list/14462'), /SIMKL PRO or VIP/);
});

test('SIMKL search combines paginated personal/followed/shared and official indexes without fetching contents', async () => {
  const requests = [];
  const lists = listsHarness(async (path, options) => {
    requests.push(path);
    if (path === '/users/settings') { assert.equal(options.method, 'POST'); return { account: { id: '10' } }; }
    const own = path.startsWith('/lists/user/10');
    return { lists: [{ id: own ? 42 : 14462, name: own ? 'My Mindfuck List' : 'Best Mindfucks', description: { en: 'Localized description' }, updated_at: '2026-10-06' }], pagination: { total_pages: own ? 2 : 1 } };
  });
  const found = await lists.searchSimklCustomLists('mindfuck');
  assert.deepEqual(Array.from(found, list => list.id), [42, 14462]);
  assert.equal(requests.length, 5);
  assert.ok(requests.slice(1, 3).every(path => path.includes('followed=true&collaborants=true')));
  assert.ok(requests[3].includes('sort=updated&direction=desc'));
  assert.ok(requests[4].includes('sort=popularity&direction=desc'));
  await lists.searchSimklCustomLists('best'); assert.equal(requests.length, 5);
});
