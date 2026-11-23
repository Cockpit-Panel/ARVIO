const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const { evaluateEntitlement } = require('../netlify/functions/_entitlements');

function fixture({ paid = false, failRead = false, linkedPaid = false } = {}) {
  const data = new Map(); let emails = 0; let writes = 0;
  const store = {
    get: async key => data.get(key) || null,
    setJSON: async (key, value) => { writes++; data.set(key, value); },
    list: async ({ prefix }) => ({ blobs: [...data.keys()].filter(key => key.startsWith(prefix)).map(key => ({ key })) }),
    delete: async key => data.delete(key)
  };
  const backend = { sha256: x => x, normalizeEmail: x => x || '', privacyHash: (_p, x) => x, sendTransactionalEmail: async () => { emails++; return { id: 'fixture' }; } };
  const module = { exports: {} };
  const record = { status: 'active', source: paid ? 'kofi' : 'trial', expiresAt: '2099-01-01', linkedFrom: linkedPaid ? 'billing@example.test' : null };
  vm.runInNewContext(fs.readFileSync(require.resolve('../netlify/functions/_trial-emails'), 'utf8'), {
    module, exports: module.exports, Buffer, Date, Intl, console,
    process: { env: { ARVIO_AUTH_SECRET: 'test-secret-longer-than-thirty-two-characters' } },
    require: name => name === 'crypto' ? crypto : name === '@netlify/blobs' ? { connectLambda() {}, getStore: () => store } : name === './_backend' ? backend : name === './_premium-funnel' ? { recordPremiumEvent: async () => {} } : {
      entitlementsStore: () => store,
      readEntitlement: async (_store, email) => { if (failRead) throw Error('Temporary store failure'); return email === 'billing@example.test' ? { ...record, source: 'kofi' } : record; },
      evaluateEntitlement
    }
  });
  const api = module.exports._test;
  const job = { type: 'reminder', expiresAt: '2099-01-01', dueAt: '2000-01-01', status: 'pending', sealedEmail: api.sealEmail('test@example.test') };
  return { api, run: module.exports.runDueTrialEmails, job, store, data, emailCount: () => emails, writes: () => writes };
}

test('paid trial users get no queued reminder and the job cannot be resent', async () => {
  for (const setup of [{ paid: true }, { linkedPaid: true }]) {
    const f = fixture(setup);
    assert.equal(await f.api.deliverTrialEmailJob({}, f.store, 'job', f.job), false);
    assert.equal(f.emailCount(), 0);
    assert.equal(f.data.get('job').status, 'suppressed');
    assert.equal(f.data.get('job').sealedEmail, null);
    await f.api.deliverTrialEmailJob({}, f.store, 'job', f.data.get('job'));
    assert.equal(f.emailCount(), 0);
  }
});

test('retired trial jobs are suppressed without reading entitlement storage', async () => {
  const f = fixture({ failRead: true });
  assert.equal(await f.api.deliverTrialEmailJob({}, f.store, 'job', f.job), false);
  assert.equal(f.emailCount(), 0);
  assert.equal(f.data.get('job').status, 'suppressed');
  assert.equal(f.data.get('job').reason, 'trials_disabled');
  assert.equal(f.data.get('job').sealedEmail, null);
});

test('all legacy trial job types are retired without decrypting or sending email', async () => {
  const f = fixture();
  for (const type of ['welcome', 'reminder', 'expired']) {
    const job = { ...f.job, type, sealedEmail: 'not-decryptable', dueAt: '2099-01-01', status: 'sending', lockedUntil: '2099-01-01' };
    assert.equal(await f.api.deliverTrialEmailJob({}, f.store, type, job), false);
    assert.equal(f.emailCount(), 0);
    assert.equal(f.data.get(type).sealedEmail, null);
    assert.equal(f.data.get(type).reason, 'trials_disabled');
    assert.equal(f.data.get(type).lockedUntil, null);
    assert.equal(f.data.get(type).expiresAt, job.expiresAt);
    assert.equal(f.data.get(type).attempts, job.attempts);
    const writes = f.writes();
    await f.api.deliverTrialEmailJob({}, f.store, type, f.data.get(type));
    assert.equal(f.writes(), writes);
  }
});

test('retiring legacy jobs respects the per-run mutation budget and makes progress past completed jobs', async () => {
  const f = fixture();
  f.data.set('jobs/old-completed', { ...f.job, sealedEmail: null, status: 'suppressed', updatedAt: new Date().toISOString() });
  for (let index = 0; index < 8; index++) f.data.set(`jobs/pending-${index}`, { ...f.job, dueAt: '2099-01-01' });
  for (const expected of [3, 3, 2]) {
    const writes = f.writes();
    const result = await f.run({}, 3);
    assert.equal(result.processed, expected);
    assert.equal(result.suppressed, expected);
    assert.equal(result.sent, 0);
    assert.equal(result.failed, 0);
    assert.equal(f.writes() - writes, expected);
  }
  const writes = f.writes();
  assert.equal((await f.run({}, 3)).processed, 0);
  assert.equal(f.writes(), writes);
  assert.equal(f.emailCount(), 0);
  assert.equal([...f.data.values()].filter(job => job.status === 'pending').length, 0);
});

test('deletions and failed suppression attempts also consume the bounded worker budget', async () => {
  const f = fixture();
  f.data.set('jobs/expired', { ...f.job, status: 'sent', sentAt: '2000-01-01', sealedEmail: null });
  f.data.set('jobs/pending', f.job);
  const removed = await f.run({}, 1);
  assert.equal(removed.processed, 1);
  assert.equal(removed.deleted, 1);
  assert.equal(removed.suppressed, 0);
  assert.equal(f.data.get('jobs/pending').status, 'pending');
  const originalSet = f.store.setJSON;
  f.store.setJSON = async () => { throw Error('Fixture storage failure'); };
  const failed = await f.run({}, 1);
  assert.equal(failed.processed, 1);
  assert.equal(failed.failed, 1);
  assert.equal(failed.suppressed, 0);
  assert.equal(f.emailCount(), 0);
  f.store.setJSON = originalSet;
  assert.equal((await f.run({}, 1)).suppressed, 1);
  assert.equal((await f.run({}, 0)).processed, 0);
});

test('repeated browser events do not create repeated Blob writes', async () => {
  const records = new Map(); let writes = 0;
  const store = { get: async key => records.get(key), setJSON: async (key, value, options) => {
    if (options?.onlyIfNew && records.has(key)) return { modified: false };
    writes++; records.set(key, value); return { modified: true };
  } };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(require.resolve('../netlify/functions/_premium-funnel'), 'utf8'), {
    module, exports: module.exports, Date, Map, Set,
    require: name => name === '@netlify/blobs' ? { connectLambda() {}, getStore: () => store } : name === './_premium-measurement' ? require('../netlify/functions/_premium-measurement') : { privacyHash: (_p, x) => x }
  });
  const record = module.exports.recordPremiumEvent;
  for (let i = 0; i < 20; i++) await record({}, { email: 'fixture@example.test', eventName: 'playback_started', metadata: { source: String(i) }, occurredAt: '2026-09-08' });
  assert.equal(writes, 1);
  assert.equal([...records.values()][0].metadata.source, '0');
  await record({}, { email: 'fixture@example.test', eventName: 'playback_started', occurredAt: '2026-09-09' });
  assert.equal(writes, 2);
});
