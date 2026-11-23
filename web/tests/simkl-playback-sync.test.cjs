const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');

const first = '2026-10-12T10:00:00Z';
const second = '2026-10-12T11:00:00Z';
const pause = (id, paused_at = '2026-10-12T09:00:00Z') => ({
  id, progress: 40, paused_at, movie: { title: `Movie ${id}`, ids: { tmdb: id } }
});

function fixture() {
  const disk = storage();
  const { SimklClient } = load('lib/simkl.ts', {
    './config': { config: {} }, './sync': {}, './storage': disk, './http': {}, './tmdb': {}
  });
  const state = { activities: { all: first, movies: { playback: first } }, rows: [pause(201), pause(202)], requests: [], failBefore: false, failActivities: false };
  const api = async request => {
    state.requests.push(request);
    if (request === '/sync/activities') {
      if (state.failActivities) throw new Error('activities offline');
      return structuredClone(state.activities);
    }
    const url = new URL(request, 'https://example.invalid');
    if (url.pathname === '/sync/playback') {
      if (state.failBefore && url.searchParams.has('date_to')) throw new Error('reconciliation offline');
      return structuredClone(state.rows.filter(row =>
        (!url.searchParams.has('date_from') || Date.parse(row.paused_at) >= Date.parse(url.searchParams.get('date_from'))) &&
        (!url.searchParams.has('date_to') || Date.parse(row.paused_at) < Date.parse(url.searchParams.get('date_to')))));
    }
    return {};
  };
  const create = () => {
    const client = new SimklClient(); client.setProfile('test-profile');
    client.setToken({ access_token: 'test-token' }); client.simkl = api;
    return client;
  };
  const client = create();
  const expire = () => { client.snapshot.checkedAt = 0; client.lastSnapshotFailureAt = 0; client.lastPlaybackFailureAt = 0; };
  const change = () => { state.activities = { all: second, movies: { playback: second } }; expire(); };
  return { client, state, create, expire, change };
}

test('unchanged activity makes zero playback requests on repeated and expired refreshes', async () => {
  const h = fixture();
  await h.client.continueWatching();
  const before = h.state.requests.length;
  await h.client.continueWatching();
  assert.equal(h.state.requests.length, before);
  h.expire();
  await h.client.continueWatching();
  assert.deepEqual(h.state.requests.slice(before), ['/sync/activities']);
});

test('library-only changes do not download playback when per-domain playback timestamps are unchanged', async () => {
  const h = fixture(); await h.client.continueWatching();
  const before = h.state.requests.length;
  h.state.activities.all = second; h.expire();
  await h.client.continueWatching();
  assert.equal(h.state.requests.slice(before).filter(p => p.includes('/sync/playback')).length, 0);
});

test('delta and older reconciliation preserve untouched titles and remove remote deletions', async () => {
  const h = fixture(); await h.client.continueWatching();
  h.state.rows = [pause(201), pause(203, second)]; h.change();
  const rows = await h.client.continueWatching();
  assert.deepEqual(Array.from(rows, row => row.movie.ids.tmdb).sort(), [201, 203]);
  assert.equal(h.state.requests.filter(p => p === '/sync/playback').length, 1);
  assert.ok(h.state.requests.some(p => p === `/sync/playback?date_from=${encodeURIComponent(first)}`));
  assert.ok(h.state.requests.some(p => p === `/sync/playback?date_to=${encodeURIComponent(first)}`));
});

test('a deletion-only update clears sessions even when the delta is empty', async () => {
  const h = fixture(); await h.client.continueWatching();
  h.state.rows = []; h.change();
  assert.equal((await h.client.continueWatching()).length, 0);
});

test('TV and anime playback timestamps trigger updates without a movie playback change', async () => {
  for (const domain of ['tv_shows', 'anime']) {
    const h = fixture(); await h.client.continueWatching();
    h.state.rows = [pause(203, second)];
    h.state.activities = { all: second, movies: { playback: first }, [domain]: { playback: second } };
    h.expire();
    assert.deepEqual(Array.from(await h.client.continueWatching(), row => row.movie.ids.tmdb), [203]);
    assert.equal(h.state.requests.filter(p => p.includes('date_from=')).length, 2, 'library and playback have independent delta requests');
  }
});

test('empty caches still discover new paused sessions through date_from', async () => {
  const h = fixture(); h.state.rows = [];
  assert.equal((await h.client.continueWatching()).length, 0);
  h.state.rows = [pause(203, second)]; h.change();
  assert.deepEqual(Array.from(await h.client.continueWatching(), row => row.movie.ids.tmdb), [203]);
  assert.equal(h.state.requests.filter(p => p === '/sync/playback').length, 1);
});

test('a session exactly on the checkpoint appears once, not in both date partitions', async () => {
  const h = fixture(); h.state.rows = [pause(201, first)];
  await h.client.continueWatching(); h.change();
  assert.deepEqual(Array.from(await h.client.continueWatching(), row => row.movie.ids.tmdb), [201]);
  assert.equal(h.client.playbackSnapshot.rows.length, 1);
});

test('failed reconciliation preserves its checkpoint and retries the same date_from', async () => {
  const h = fixture(); await h.client.continueWatching();
  h.change(); h.state.failBefore = true;
  await assert.rejects(h.client.continueWatching(), /reconciliation offline/);
  assert.equal(h.client.playbackSnapshot.activity, first);
  const before = h.state.requests.length;
  await assert.rejects(h.client.continueWatching(), /retry shortly/);
  assert.equal(h.state.requests.length, before);
  h.state.failBefore = false; h.client.lastPlaybackFailureAt = 0;
  await h.client.continueWatching();
  assert.equal(h.state.requests.filter(p => p === `/sync/playback?date_from=${encodeURIComponent(first)}`).length, 2);
});

test('persisted playback and checkpoint survive recreation without a full playback fetch', async () => {
  const h = fixture(); await h.client.continueWatching();
  const before = h.state.requests.length;
  const restarted = h.create();
  assert.equal((await restarted.continueWatching()).length, 2);
  assert.equal(h.state.requests.slice(before).filter(p => p.includes('/sync/playback')).length, 0);
});

test('concurrent Continue Watching refreshes share one playback request', async () => {
  const h = fixture();
  await Promise.all([h.client.continueWatching(), h.client.continueWatching()]);
  assert.equal(h.state.requests.filter(p => p === '/sync/playback').length, 1);
});

test('switching accounts cannot reuse playback sessions from the previous account', async () => {
  const h = fixture(); await h.client.continueWatching();
  h.client.setToken({ access_token: 'other-token' }); h.state.rows = [];
  assert.equal((await h.client.continueWatching()).length, 0);
  assert.equal(h.state.requests.filter(p => p === '/sync/playback').length, 2);
});

test('failed activities never falls through to downloading full playback data', async () => {
  const h = fixture(); h.state.failActivities = true;
  await assert.rejects(h.client.continueWatching(), /activities offline/);
  assert.deepEqual(h.state.requests, ['/sync/activities']);
});
