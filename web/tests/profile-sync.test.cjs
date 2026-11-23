// Saving profiles from one device must not delete profiles that device does not know.
//
// The web app used to replace the account's whole profile list with the list it held. A
// browser that had cached the list on an earlier visit, before another device added a
// profile, deleted that profile from the account as soon as a profile was picked.
const test = require('node:test');
const assert = require('node:assert/strict');
const { load, storage } = require('./load.cjs');

const same = (actual, expected) => assert.deepEqual(JSON.parse(JSON.stringify(actual)), expected);

function setup(initial) {
  let stored = initial;
  const cloud = load('lib/cloud.ts', {
    './config': { config: { netlifyBackendUrl: 'https://backend.invalid' }, hasNetlifyBackendUrl: () => true },
    './homeserver': { serializeHomeServerConnectionJson: () => '[]', parseHomeServerConnectionJson: () => [] },
    './iptv': load('lib/iptv.ts', { './http': {}, './storage': storage() }),
    './mediaImages': { tmdbImageUrl: () => '' },
    './http': {
      jsonRequest: async (_url, options) => {
        if (options?.method === 'POST') { stored = JSON.parse(options.body).payload; return { accepted: true }; }
        return { payload: structuredClone(stored) };
      }
    }
  });
  return { cloud, read: () => stored };
}

const auth = {
  session: { userId: 'u1', email: 'me@arvio.example', accessToken: 'test' },
  isNetlifySession: true, accessToken: async () => 'test'
};

// Added on a TV, with fields this web build does not model.
const kids = { id: 'k1', name: 'Kids', isKidsProfile: true, kidsAgeLevel: 'LITTLE_KIDS' };

test('a stale browser list keeps profiles another device added', async () => {
  const { cloud, read } = setup({ profiles: [{ id: 'p1', name: 'Main' }, kids], activeProfileId: 'p1' });
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main', lastUsedAt: 5 }], 'p1');
  same(read().profiles, [{ id: 'p1', name: 'Main', lastUsedAt: 5 }, kids]);
  assert.equal(read().activeProfileId, 'p1');
});

test('a profile deleted here leaves the account and is not brought back', async () => {
  const { cloud, read } = setup({ profiles: [{ id: 'p1', name: 'Main' }, kids], activeProfileId: 'p1' });
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main' }], 'p1', ['k1']);
  same(read().profiles, [{ id: 'p1', name: 'Main' }]);
  same(read().deletedProfileIds, ['k1']);

  // Another browser still holding the deleted profile saves its list afterwards.
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main' }, kids], 'p1');
  same(read().profiles, [{ id: 'p1', name: 'Main' }]);
  same((await cloud.pullCloudProfiles(auth)).profiles, [{ id: 'p1', name: 'Main' }]);
});

test('profiles created here are added and the active profile stays valid', async () => {
  const { cloud, read } = setup({ profiles: [{ id: 'p1', name: 'Main' }], activeProfileId: 'p1' });
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main' }, { id: 'p2', name: 'New' }], 'gone');
  same(read().profiles, [{ id: 'p1', name: 'Main' }, { id: 'p2', name: 'New' }]);
  assert.equal(read().activeProfileId, 'p1');
});

test('a stale copy does not overwrite newer kids settings or PIN protection', async () => {
  const newer = { ...kids, kidsAgeLevel: 'TEENS', parentPin: 'hash-new', isLocked: true, pin: 'pin-new', updatedAt: 2000, lastUsedAt: 1000 };
  const { cloud, read } = setup({ profiles: [{ id: 'p1', name: 'Main' }, newer], activeProfileId: 'p1' });
  const stale = { ...kids, updatedAt: 1000, lastUsedAt: 5000 };
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main' }, stale], 'p1');
  same(read().profiles[1], { ...newer, lastUsedAt: 5000 });

  // This browser's own newer edit still wins.
  await cloud.saveCloudProfiles(auth, [{ id: 'p1', name: 'Main' }, { ...newer, kidsAgeLevel: 'OLDER_KIDS', updatedAt: 3000 }], 'p1');
  assert.equal(read().profiles[1].kidsAgeLevel, 'OLDER_KIDS');
});
