const { test } = require('node:test');
const assert = require('node:assert/strict');
const { FEEDS, fetchFeed, parseEspn, parseMlb, mergeFixtures } = require('../netlify/functions/_sports-supplement');
const { getMetadata } = require('../netlify/functions/_sports-metadata');
const now = Date.parse('2026-09-16T12:00:00Z');
const feed = FEEDS[0];
const game = { id: '123', date: '2026-09-16T10:00:00-04:00', status: { type: { state: 'in' } },
  competitors: [{ homeAway: 'home', team: { id: '1', displayName: 'Alpha', logo: 'https://a.espncdn.com/i/teamlogos/nfl/500/a.png' } },
    { homeAway: 'away', team: { id: '2', displayName: 'Beta', logo: 'https://evil.test/b.png' } }] };
const payload = { events: [{ competitions: [game] }] };
const fixture = () => parseEspn(payload, feed, now)[0];
function store() {
  const records = new Map(); let revision = 0;
  return { records, getWithMetadata: async key => structuredClone(records.get(key)) ?? null,
    setJSON: async (key, data, options) => {
      const old = records.get(key);
      if ((options.onlyIfNew && old) || (options.onlyIfMatch !== undefined && options.onlyIfMatch !== old?.etag)) return { modified: false };
      const etag = String(++revision); records.set(key, { data: structuredClone(data), etag }); return { modified: true, etag };
    } };
}
test('ESPN preserves absolute time, namespaces IDs, sanitizes badges, and never invents channels', () => {
  const row = fixture();
  assert.equal(row.startsAt, Date.parse('2026-09-16T14:00:00Z'));
  assert.equal(row.id, 'espn:nfl:123'); assert.equal(row.status, 'live');
  assert.ok(row.homeBadge); assert.equal(row.awayBadge, null); assert.deepEqual(row.broadcasters, []);
  assert.equal(parseEspn({ events: [{ competitions: [{ ...game, timeValid: false }] }] }, feed, now).length, 0);
  assert.equal(parseEspn({ events: [{ competitions: [{ ...game, date: '2026-09-16T14:00:00' }] }] }, feed, now).length, 0);
});
test('terminal and postponed states never become live', () => {
  for (const [status, expected] of [[{ state: 'post', completed: true }, 'finished'], [{ name: 'STATUS_POSTPONED', state: 'pre' }, 'postponed']]) {
    assert.equal(parseEspn({ events: [{ competitions: [{ ...game, status: { type: status } }] }] }, feed, now)[0].status, expected);
  }
});
test('MLB excludes TBD games and keeps separate doubleheaders', () => {
  const g = { gamePk: 12, gameDate: '2026-09-16T14:00:00Z', teams: { home: { team: { name: 'A' } }, away: { team: { name: 'B' } } }, status: { abstractGameState: 'Final' } };
  const rows = parseMlb({ dates: [{ games: [g, { ...g, gamePk: 13, gameDate: '2026-09-16T19:00:00Z' }, { ...g, status: { startTimeTBD: true } }] }] }, FEEDS.at(-1), now);
  assert.equal(rows.length, 2); assert.equal(rows[0].status, 'finished'); assert.notEqual(rows[0].id, rows[1].id);
});
test('duplicates enrich missing badges without losing primary art, status or broadcasters', () => {
  const original = { ...fixture(), id: '42', background: 'official.jpg', status: 'scheduled', broadcasters: [{ name: 'Verified' }], homeBadge: null };
  const supplemental = { ...fixture(), homeBadge: 'home.png', awayBadge: 'away.png' };
  const merged = mergeFixtures([original], [supplemental, { ...supplemental, id: 'espn:nfl:124', startsAt: supplemental.startsAt + 4 * 3600_000 }]);
  assert.equal(merged.length, 2); assert.equal(merged[0].id, '42'); assert.equal(merged[0].background, 'official.jpg');
  assert.equal(merged[0].homeBadge, 'home.png'); assert.equal(merged[0].status, 'scheduled');
  assert.equal(merged[0].broadcasters.length, 1); assert.equal(original.homeBadge, null);
});
test('matching keeps qualifiers and leagues separate and swaps badges when sides reverse', () => {
  const a = { ...fixture(), homeBadge: null, awayBadge: null };
  const b = { ...fixture(), homeTeam: 'Beta', awayTeam: 'Alpha', homeBadge: 'beta.png', awayBadge: 'alpha.png' };
  const out = mergeFixtures([a], [b, { ...b, qualifier: 'women' }, { ...b, league: 'Other' }]);
  assert.equal(out.length, 3); assert.equal(out[0].homeBadge, 'alpha.png');
});
test('daily scoreboard requests are bounded, deduplicated, and reject a partial refresh', async () => {
  const urls = [];
  const result = await fetchFeed({ now, fetcher: async url => { urls.push(url); return new Response(JSON.stringify(payload)); } }, feed);
  assert.equal(urls.length, 4); assert.ok(urls.every(url => /dates=\d{8}&/.test(url))); assert.equal(result.events.length, 1);
  await assert.rejects(fetchFeed({ now, fetcher: async () => new Response('{}', { status: 429 }) }, feed));
  await assert.rejects(fetchFeed({ now, fetcher: async () => new Response('x'.repeat(4 * 1024 * 1024 + 1)) }, feed));
});
test('background refresh is shared and viewer reads cause zero supplemental upstream requests', async () => {
  const shared = store(); let calls = 0;
  const deps = { store: shared, now, supplemental: true, fetcher: async url => {
    calls++; return new Response(JSON.stringify(url.includes('statsapi') ? { dates: [] } : payload));
  } };
  await Promise.all(Array.from({ length: 10 }, () => getMetadata({ ...deps, refreshSupplemental: true })));
  assert.equal(calls, 25, 'six daily feeds plus one MLB window, shared across refreshers');
  const before = calls;
  const result = await getMetadata({ ...deps, now: now + 6 * 60_000 });
  assert.ok(result.events.length); assert.equal(calls, before);
  await getMetadata({ ...deps, refreshSupplemental: true, now: now + 6 * 60_000, fetcher: async () => { throw new Error('offline'); } });
  assert.ok((await getMetadata({ ...deps, now: now + 7 * 60_000 })).events.length, 'failed feeds retain their last complete slate');
  assert.equal(await getMetadata({ ...deps, now: now + 25 * 3600_000 }), null, 'expired schedules are not retained forever');
});
