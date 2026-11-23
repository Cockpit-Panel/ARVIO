const crypto = require("crypto");
const { connectLambda, getStore } = require("@netlify/blobs");

const DAY_MS = 24 * 60 * 60 * 1000;
const JOB_RETENTION_MS = 14 * DAY_MS;
const JOB_TYPES = ["welcome", "reminder", "expired"];

function trialEmailStore(event) {
  connectLambda(event);
  return getStore("premium-trial-emails");
}

function encryptionKey() {
  const secret = process.env.ARVIO_AUTH_SECRET || "";
  if (secret.length < 32) throw new Error("ARVIO_AUTH_SECRET is not configured");
  return crypto.createHash("sha256").update(`${secret}:premium-trial-email:v1`).digest();
}

function sealEmail(email) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const encrypted = Buffer.concat([cipher.update(String(email), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [iv, tag, encrypted].map((part) => part.toString("base64url")).join(".");
}

function openEmail(sealed) {
  const [ivRaw, tagRaw, encryptedRaw] = String(sealed || "").split(".");
  if (!ivRaw || !tagRaw || !encryptedRaw) throw new Error("Invalid encrypted email");
  const decipher = crypto.createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(ivRaw, "base64url"));
  decipher.setAuthTag(Buffer.from(tagRaw, "base64url"));
  return Buffer.concat([
    decipher.update(Buffer.from(encryptedRaw, "base64url")),
    decipher.final()
  ]).toString("utf8");
}

async function getJSON(store, key) {
  try {
    return await store.get(key, { type: "json", consistency: "strong" });
  } catch (error) {
    if (String(error?.message || "").includes("uncachedEdgeURL")) {
      return store.get(key, { type: "json" }).catch(() => null);
    }
    if (error?.status === 404 || error?.name === "BlobNotFoundError") return null;
    throw error;
  }
}

async function listKeys(store, prefix) {
  const keys = [];
  let cursor;
  do {
    const page = await store.list({ prefix, cursor });
    keys.push(...(page.blobs || []).map((blob) => blob.key));
    cursor = page.next_cursor || page.nextCursor || undefined;
  } while (cursor);
  return keys;
}

function trialJobKey(accountKey, type) {
  return `jobs/${accountKey}/${type}.json`;
}

function trialEmailContent(type, expiresAt) {
  const webUrl = `https://web.arvio.tv/?utm_source=trial_email&utm_medium=email&utm_campaign=premium&utm_content=${type}`;
  const membershipUrl = process.env.KOFI_URL || process.env.NEXT_PUBLIC_KOFI_URL || "https://ko-fi.com/arvio/tiers";
  const end = new Intl.DateTimeFormat("en", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "UTC",
    timeZoneName: "short"
  }).format(new Date(expiresAt));
  if (type === "welcome") {
    return {
      subject: "Your 3-day ARVIO Web trial is active",
      text: `Your ARVIO Web trial is ready until ${end}. Open ARVIO Web on Windows, Mac, iPhone, iPad or another browser: ${webUrl}\n\nYour profiles, addons, libraries and watch progress stay connected through ARVIO Cloud. The Android app remains free.\n\nWant to keep browser access after the trial? Membership is $2.99/month: ${membershipUrl}`,
      html: `<p>Your <strong>3-day ARVIO Web trial</strong> is ready until ${end}.</p><p><a href="${webUrl}">Open ARVIO Web on Windows, Mac or mobile</a></p><p>Your profiles, addons, libraries and watch progress stay connected through ARVIO Cloud. The Android app remains free.</p><p><a href="${membershipUrl}">Keep Premium after your trial for $2.99/month</a>.</p>`
    };
  }
  if (type === "reminder") {
    return {
      subject: "Your ARVIO Web trial ends tomorrow",
      text: `Your ARVIO Web trial ends at ${end}. Continue using it here: ${webUrl}\n\nTo keep Windows, Mac, browser, iPhone and iPad access after the trial, membership is $2.99/month: ${membershipUrl}`,
      html: `<p>Your ARVIO Web trial ends at <strong>${end}</strong>.</p><p><a href="${webUrl}">Continue your trial</a></p><p>To keep Windows, Mac and mobile browser access after the trial, <a href="${membershipUrl}">membership is $2.99/month</a>.</p>`
    };
  }
  return {
    subject: "Your ARVIO Web trial has ended",
    text: `Your ARVIO Web trial has ended. Your ARVIO Cloud account and Android app are unaffected.\n\nKeep Windows, Mac and mobile browser access for $2.99/month: ${membershipUrl}\n\nThis is the final email for this trial.`,
    html: `<p>Your ARVIO Web trial has ended. Your ARVIO Cloud account and Android app are unaffected.</p><p><a href="${membershipUrl}">Keep Windows, Mac and mobile browser access for $2.99/month</a>.</p><p>This is the final email for this trial.</p>`
  };
}

async function queueTrialEmails() {
  // Retained as a safe no-op for older callers. Hosted Web no longer issues
  // trials, and queuing their service messages would promise unavailable access.
  return [];
}

async function deliverTrialEmailJob(_event, store, key, job) {
  if (!job || job.sentAt || job.status === "sent" || job.status === "suppressed" || !JOB_TYPES.includes(job.type)) return false;
  // Suppress even future-due/locked legacy jobs without decrypting addresses or
  // contacting the mail provider. Preserve the audit record until normal cleanup.
  await store.setJSON(key, {
    ...job,
    sealedEmail: null,
    status: "suppressed",
    reason: "trials_disabled",
    lockedUntil: null,
    updatedAt: new Date().toISOString()
  });
  return false;
}

async function runDueTrialEmails(event, limit = 50) {
  const numericLimit = Number(limit);
  const budget = Number.isFinite(numericLimit) ? Math.max(0, Math.min(500, Math.floor(numericLimit))) : 50;
  if (!budget) return { sent: 0, failed: 0, deleted: 0, suppressed: 0, processed: 0, scanned: 0 };
  const store = trialEmailStore(event);
  const keys = await listKeys(store, "jobs/");
  let sent = 0;
  let failed = 0;
  let deleted = 0;
  let suppressed = 0;
  let processed = 0;
  let scanned = 0;
  for (const key of keys) {
    if (processed >= budget) break;
    scanned += 1;
    const job = await getJSON(store, key);
    const completedAt = Date.parse(job?.sentAt || job?.updatedAt || "");
    if (["sent", "failed", "suppressed"].includes(job?.status) && Number.isFinite(completedAt) && Date.now() - completedAt > JOB_RETENTION_MS) {
      processed += 1;
      try { await store.delete(key); deleted += 1; }
      catch { failed += 1; }
      continue;
    }
    // Completed jobs do not consume the mutation budget; otherwise the first
    // already-suppressed page would starve the remaining legacy jobs forever.
    if (!job || job.sentAt || ["sent", "suppressed"].includes(job.status) || !JOB_TYPES.includes(job.type)) continue;
    processed += 1;
    try {
      if (await deliverTrialEmailJob(event, store, key, job)) sent += 1;
      else suppressed += 1;
    } catch (error) {
      failed += 1;
      console.error("trial email delivery failed", key, error);
    }
  }
  return { sent, failed, deleted, suppressed, processed, scanned };
}

function trialEmailSuppression() {
  return "trials_disabled";
}

module.exports = {
  JOB_TYPES,
  queueTrialEmails,
  runDueTrialEmails,
  _test: { sealEmail, openEmail, trialEmailContent, trialJobKey, trialEmailSuppression, deliverTrialEmailJob }
};
