const { Pool } = require("pg");
const crypto = require("crypto");
const { connectLambda, getStore } = require("@netlify/blobs");

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,DELETE,OPTIONS",
  "access-control-allow-headers": "authorization,apikey,content-type,x-arvio-user-id,x-arvio-email,x-client-info,x-user-token"
};

let pool;

function getPool() {
  if (pool) return pool;
  const connectionString =
    process.env.NETLIFY_DB_URL ||
    process.env.NETLIFY_DATABASE_URL ||
    process.env.DATABASE_URL;
  if (!connectionString) {
    throw new Error("NETLIFY_DB_URL is not configured");
  }
  pool = new Pool({
    connectionString,
    max: Number(process.env.DB_POOL_MAX || 4),
    idleTimeoutMillis: 10_000,
    connectionTimeoutMillis: 8_000
  });
  return pool;
}

function json(statusCode, body) {
  return {
    statusCode,
    headers: JSON_HEADERS,
    body: JSON.stringify(body)
  };
}

function options(event) {
  return event.httpMethod === "OPTIONS" ? json(204, {}) : null;
}

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function parseBody(event) {
  if (!event.body) return {};
  const raw = event.isBase64Encoded
    ? Buffer.from(event.body, "base64").toString("utf8")
    : event.body;
  return JSON.parse(raw);
}

function appAnonKey() {
  return process.env.APP_ANON_KEY || process.env.SUPABASE_ANON_KEY || "";
}

function assertAppRequest(event) {
  const expected = appAnonKey();
  if (!expected) {
    throw new Error("APP_ANON_KEY is not configured");
  }
  const apiKey = String(event.headers.apikey || event.headers.Apikey || "").trim();
  const auth = event.headers.authorization || event.headers.Authorization || "";
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() || "";
  if (apiKey === expected || bearer === expected) return;
  const error = new Error("Unauthorized");
  error.statusCode = 401;
  throw error;
}

function supabaseConfig() {
  const supabaseUrl = (process.env.SUPABASE_URL || "").replace(/\/+$/, "");
  const anonKey = appAnonKey();
  const serviceRole = process.env.SUPABASE_SERVICE_ROLE_KEY || "";
  if (!supabaseUrl || !anonKey) {
    throw new Error("Supabase Auth verifier is not configured");
  }
  return { supabaseUrl, anonKey, serviceRole };
}

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function publicError(error, fallback = "Unexpected error") {
  if (!error) return fallback;
  if (typeof error === "string") return error;
  return error.message || fallback;
}

function parseAuthError(raw) {
  try {
    const data = JSON.parse(raw);
    return String(data.error_description || data.msg || data.message || data.error || raw);
  } catch {
    return raw || "Auth request failed";
  }
}

const EMAIL_RE = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,63}$/i;
const BLOCKED_EMAIL_DOMAINS = new Set([
  "10minutemail.com",
  "20minutemail.com",
  "dispostable.com",
  "emailondeck.com",
  "example.com",
  "example.net",
  "example.org",
  "fakeinbox.com",
  "getnada.com",
  "grr.la",
  "guerrillamail.biz",
  "guerrillamail.com",
  "guerrillamail.de",
  "guerrillamail.info",
  "guerrillamail.net",
  "guerrillamail.org",
  "invalid",
  "localhost",
  "maildrop.cc",
  "mailinator.com",
  "moakt.com",
  "sharklasers.com",
  "temp-mail.org",
  "tempmail.com",
  "tempmailo.com",
  "trashmail.com",
  "yopmail.com"
]);
const BLOCKED_EMAIL_DOMAIN_FRAGMENTS = [
  "10minutemail",
  "disposable",
  "fakeinbox",
  "guerrillamail",
  "maildrop",
  "mailinator",
  "tempmail",
  "temp-mail",
  "trashmail",
  "yopmail"
];
const BLOCKED_SIGNUP_LOCAL_PARTS = new Set([
  "asdf",
  "example",
  "fake",
  "invalid",
  "no-reply",
  "none",
  "noreply",
  "null",
  "qwerty",
  "test"
]);

function validateEmail(email, rejectDisposable = true) {
  const normalized = normalizeEmail(email);
  if (!normalized) return "Email is required";
  if (normalized.length > 254 || !EMAIL_RE.test(normalized)) return "Enter a valid email address";
  if ((normalized.match(/@/g) || []).length !== 1) return "Enter a valid email address";

  const [localPart, domain = ""] = normalized.split("@");
  if (!localPart || !domain) return "Use a real email address";
  if (localPart.length > 64 || localPart.startsWith(".") || localPart.endsWith(".") || localPart.includes("..")) {
    return "Enter a valid email address";
  }
  const labels = domain.split(".");
  if (labels.length < 2 || labels.some((part) => !part || part.length > 63)) return "Enter a valid email address";
  if (labels.some((part) => part.startsWith("-") || part.endsWith("-"))) return "Enter a valid email address";
  if (/^\d+$/.test(labels[labels.length - 1])) return "Enter a valid email address";

  const blockedDomain = BLOCKED_EMAIL_DOMAINS.has(domain) ||
    BLOCKED_EMAIL_DOMAIN_FRAGMENTS.some((fragment) => domain.includes(fragment));
  if (rejectDisposable && BLOCKED_SIGNUP_LOCAL_PARTS.has(localPart)) return "Use a real email address";
  if (rejectDisposable && blockedDomain) return "Use a real email address";
  if (
    rejectDisposable &&
    (
      domain.endsWith(".example") ||
      domain.endsWith(".invalid") ||
      domain.endsWith(".localhost") ||
      domain.endsWith(".local") ||
      domain.endsWith(".test")
    )
  ) {
    return "Use a real email address";
  }
  return "";
}

async function supabasePasswordToken(email, password) {
  const { supabaseUrl, anonKey } = supabaseConfig();
  const response = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: {
      apikey: anonKey,
      "content-type": "application/json"
    },
    body: JSON.stringify({ email, password })
  });
  const text = await response.text();
  if (!response.ok) {
    const error = new Error(parseAuthError(text));
    error.statusCode = response.status;
    throw error;
  }
  return JSON.parse(text);
}

async function createConfirmedSupabaseUser(email, password) {
  const { supabaseUrl, serviceRole } = supabaseConfig();
  if (!serviceRole) {
    throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  }
  const response = await fetch(`${supabaseUrl}/auth/v1/admin/users`, {
    method: "POST",
    headers: {
      apikey: serviceRole,
      authorization: `Bearer ${serviceRole}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      email,
      password,
      email_confirm: true,
      user_metadata: { provider: "email" }
    })
  });
  const text = await response.text();
  if (!response.ok) {
    const message = parseAuthError(text);
    const lower = message.toLowerCase();
    const alreadyExists = response.status === 409 ||
      response.status === 422 ||
      lower.includes("already") ||
      lower.includes("registered") ||
      lower.includes("exists");
    if (!alreadyExists) {
      const error = new Error("Unable to create account");
      error.statusCode = 400;
      throw error;
    }
    return { alreadyExists: true };
  }
  return text ? JSON.parse(text) : {};
}

async function refreshSupabaseSession(refreshToken) {
  const { supabaseUrl, anonKey } = supabaseConfig();
  const response = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: {
      apikey: anonKey,
      "content-type": "application/json"
    },
    body: JSON.stringify({ refresh_token: refreshToken })
  });
  const text = await response.text();
  if (!response.ok) {
    const error = new Error(parseAuthError(text));
    error.statusCode = response.status;
    throw error;
  }
  return JSON.parse(text);
}

function randomCode(length) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.randomBytes(length);
  return Array.from(bytes).map((byte) => alphabet[byte % alphabet.length]).join("");
}

function tvSessionStores(event) {
  connectLambda(event);
  return getStore("tv-auth-sessions");
}

function tvSessionKeys(session) {
  return {
    device: `device/${session.deviceCode}.json`,
    code: `code/${String(session.userCode || "").toUpperCase()}.json`
  };
}

async function saveTvSession(event, session) {
  const store = tvSessionStores(event);
  const keys = tvSessionKeys(session);
  await store.setJSON(keys.device, session, {
    metadata: {
      deviceCode: session.deviceCode,
      userCode: session.userCode,
      status: session.status,
      expiresAt: session.expiresAt
    }
  });
  await store.setJSON(keys.code, session, {
    metadata: {
      deviceCode: session.deviceCode,
      userCode: session.userCode,
      status: session.status,
      expiresAt: session.expiresAt
    }
  });
}

async function loadTvSessionByDevice(event, deviceCode) {
  const store = tvSessionStores(event);
  return getJSONOrNull(store, `device/${deviceCode}.json`);
}

async function loadTvSessionByCode(event, userCode) {
  const store = tvSessionStores(event);
  return getJSONOrNull(store, `code/${String(userCode || "").toUpperCase()}.json`);
}

function isTvSessionExpired(session) {
  return !session?.expiresAt || Date.now() > Date.parse(session.expiresAt);
}

function methodGuard(event, methods) {
  const method = event.httpMethod || "GET";
  if (methods.includes(method)) return null;
  return json(405, { error: "Method not allowed" });
}

function handlerError(event, error, fallback = "Unexpected error") {
  const status = error?.statusCode || error?.status || 500;
  return json(status, { error: publicError(error, fallback) });
}

async function handleCloudAuthEmail(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const body = parseBody(event);
    const email = normalizeEmail(body.email);
    const password = String(body.password || "");
    const emailError = validateEmail(email, true);
    if (emailError) return json(400, { error: emailError });
    if (password.length < 6) return json(400, { error: "Password must be at least 6 characters" });

    await createConfirmedSupabaseUser(email, password);
    const token = await supabasePasswordToken(email, password);
    return json(200, {
      access_token: token.access_token,
      refresh_token: token.refresh_token,
      expires_in: token.expires_in,
      token_type: token.token_type,
      user: token.user
    });
  } catch (error) {
    return handlerError(event, error, "Account creation failed");
  }
}

async function handleCloudAuthReset(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const { supabaseUrl, anonKey } = supabaseConfig();
    const body = parseBody(event);
    const email = normalizeEmail(body.email);
    const emailError = validateEmail(email, true);
    if (emailError) return json(400, { error: emailError });
    const redirectTo = String(body.redirect_to || "https://auth.arvio.tv/?mode=recovery").trim();
    const response = await fetch(`${supabaseUrl}/auth/v1/recover`, {
      method: "POST",
      headers: {
        apikey: anonKey,
        authorization: `Bearer ${anonKey}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ email, redirect_to: redirectTo })
    });
    const text = await response.text();
    if (!response.ok) return json(response.status, { error: parseAuthError(text) || "Password reset failed" });
    return json(200, { ok: true });
  } catch (error) {
    return handlerError(event, error, "Password reset failed");
  }
}

async function handleAuthRefresh(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    const body = parseBody(event);
    const refreshToken = String(body.refresh_token || "").trim();
    if (!refreshToken) return json(400, { error: "refresh_token is required" });
    const token = await refreshSupabaseSession(refreshToken);
    return json(200, {
      access_token: token.access_token,
      refresh_token: token.refresh_token,
      expires_in: token.expires_in,
      token_type: token.token_type,
      user: token.user
    });
  } catch (error) {
    return handlerError(event, error, "Session refresh failed");
  }
}

async function handleTvAuthStart(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const deviceCode = randomCode(32);
    const userCode = `${randomCode(4)}-${randomCode(4)}`;
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    const session = {
      deviceCode,
      userCode,
      status: "pending",
      createdAt: new Date().toISOString(),
      expiresAt
    };
    await saveTvSession(event, session);
    const verifyBase = (process.env.TV_AUTH_VERIFY_BASE_URL || process.env.SITE_URL || "https://auth.arvio.tv").replace(/\/+$/, "");
    return json(200, {
      device_code: deviceCode,
      user_code: userCode,
      verification_url: `${verifyBase}/?code=${encodeURIComponent(userCode)}`,
      verification_uri: `${verifyBase}/?code=${encodeURIComponent(userCode)}`,
      expires_in: 600,
      interval: 3
    });
  } catch (error) {
    return handlerError(event, error, "Failed to start TV auth");
  }
}

async function handleTvAuthStatus(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const body = parseBody(event);
    const deviceCode = String(body.device_code || "").trim();
    if (!deviceCode) return json(400, { error: "device_code is required" });
    const session = await loadTvSessionByDevice(event, deviceCode);
    if (!session) return json(200, { status: "expired", message: "Session not found" });
    if (isTvSessionExpired(session) && session.status === "pending") {
      await saveTvSession(event, { ...session, status: "expired", expiredAt: new Date().toISOString() });
      return json(200, { status: "expired", message: "Code expired" });
    }
    if (session.status === "approved" && session.accessToken && session.refreshToken) {
      await saveTvSession(event, {
        ...session,
        status: "consumed",
        consumedAt: new Date().toISOString(),
        accessToken: null,
        refreshToken: null
      });
      return json(200, {
        status: "approved",
        access_token: session.accessToken,
        refresh_token: session.refreshToken,
        email: session.userEmail || null
      });
    }
    if (session.status === "expired" || session.status === "consumed") {
      return json(200, { status: "expired", message: "Code expired" });
    }
    return json(200, { status: "pending" });
  } catch (error) {
    return handlerError(event, error, "Failed to poll TV auth status");
  }
}

async function handleTvAuthApprove(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const accessToken = bearerToken(event);
    if (!accessToken) return json(401, { error: "Missing user access token" });
    const identity = await verifySupabaseToken(accessToken);
    const body = parseBody(event);
    const code = String(body.code || "").trim().toUpperCase();
    const refreshToken = String(body.refresh_token || "").trim();
    if (!code || !refreshToken) return json(400, { error: "Missing required fields" });
    const session = await loadTvSessionByCode(event, code);
    if (!session || isTvSessionExpired(session) || session.status !== "pending") {
      return json(400, { error: "Invalid or expired code" });
    }
    await saveTvSession(event, {
      ...session,
      status: "approved",
      approvedAt: new Date().toISOString(),
      userId: identity.supabaseUserId,
      userEmail: identity.email,
      accessToken,
      refreshToken
    });
    return json(200, { ok: true });
  } catch (error) {
    return handlerError(event, error, "TV pairing failed");
  }
}

async function handleTvAuthComplete(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  const wrongMethod = methodGuard(event, ["POST"]);
  if (wrongMethod) return wrongMethod;
  try {
    assertAppRequest(event);
    const body = parseBody(event);
    const code = String(body.code || "").trim().toUpperCase();
    const email = normalizeEmail(body.email);
    const password = String(body.password || "");
    const intent = String(body.intent || body.action || "signin").trim().toLowerCase();
    if (!code || !email || !password) return json(400, { error: "Missing required fields" });
    const emailError = validateEmail(email, intent === "signup");
    if (emailError) return json(400, { error: emailError });
    const session = await loadTvSessionByCode(event, code);
    if (!session || isTvSessionExpired(session) || session.status !== "pending") {
      return json(400, { error: "Invalid or expired code" });
    }
    if (intent === "signup") {
      await createConfirmedSupabaseUser(email, password);
    }
    const token = await supabasePasswordToken(email, password);
    if (!token.access_token || !token.refresh_token || !token.user?.id) {
      throw new Error("Auth response incomplete");
    }
    await saveTvSession(event, {
      ...session,
      status: "approved",
      approvedAt: new Date().toISOString(),
      userId: token.user.id,
      userEmail: token.user.email || email,
      accessToken: token.access_token,
      refreshToken: token.refresh_token
    });
    return json(200, { ok: true });
  } catch (error) {
    const status = error?.statusCode === 400 ? 401 : (error?.statusCode || 500);
    return json(status, { error: status === 401 ? "Invalid email or password" : publicError(error, "TV pairing failed") });
  }
}

const TMDB_ALLOWED_PATHS = [
  "/trending/",
  "/movie/",
  "/tv/",
  "/search/",
  "/discover/",
  "/find/",
  "/genre/",
  "/person/",
  "/collection/",
  "/watch/providers",
  "/configuration"
];

async function handleTmdbProxy(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  try {
    assertAppRequest(event);
    const pathParam = event.queryStringParameters?.path || "";
    if (!pathParam) return json(400, { error: "Missing path parameter" });
    if (!TMDB_ALLOWED_PATHS.some((allowed) => pathParam.startsWith(allowed))) {
      return json(403, { error: "Path not allowed" });
    }
    const tmdbKey = process.env.TMDB_API_KEY || "";
    if (!tmdbKey) throw new Error("TMDB_API_KEY not configured");
    const tmdbUrl = new URL(`https://api.themoviedb.org/3${pathParam}`);
    tmdbUrl.searchParams.set("api_key", tmdbKey);
    Object.entries(event.queryStringParameters || {}).forEach(([key, value]) => {
      if (key !== "path" && value !== undefined && value !== null) tmdbUrl.searchParams.set(key, String(value));
    });
    const response = await fetch(tmdbUrl, {
      headers: {
        accept: "application/json",
        "accept-encoding": "identity;q=1, *;q=0",
        "cache-control": "max-age=300",
        "user-agent": "ARVIO-Netlify-TMDB-Proxy/1.0"
      }
    });
    const text = await response.text();
    return {
      statusCode: response.status,
      headers: {
        ...JSON_HEADERS,
        "cache-control": response.ok ? "public, max-age=3600, stale-while-revalidate=86400" : "no-store"
      },
      body: text
    };
  } catch (error) {
    return json(502, { error: errorMessage(error) });
  }
}

const TRAKT_ALLOWED_PATHS = [
  "/oauth/device/code",
  "/oauth/device/token",
  "/oauth/token",
  "/users/me",
  "/users/",
  "/sync/last_activities",
  "/sync/history",
  "/sync/watchlist",
  "/sync/watched",
  "/sync/playback",
  "/scrobble/",
  "/movies/",
  "/shows/",
  "/lists/",
  "/search/",
  "/calendars/"
];

async function handleTraktProxy(event) {
  const preflight = options(event);
  if (preflight) return preflight;
  try {
    assertAppRequest(event);
    const pathParam = event.queryStringParameters?.path || "";
    const method = String(event.queryStringParameters?.method || "GET").toUpperCase();
    if (!pathParam) return json(400, { error: "Missing path parameter" });
    if (!TRAKT_ALLOWED_PATHS.some((allowed) => pathParam.startsWith(allowed))) {
      return json(403, { error: "Path not allowed" });
    }
    const clientId = process.env.TRAKT_CLIENT_ID || "";
    const clientSecret = process.env.TRAKT_CLIENT_SECRET || "";
    if (!clientId || !clientSecret) throw new Error("Trakt credentials not configured");
    const traktUrl = new URL(`https://api.trakt.tv${pathParam}`);
    Object.entries(event.queryStringParameters || {}).forEach(([key, value]) => {
      if (key !== "path" && key !== "method" && value !== undefined && value !== null) {
        traktUrl.searchParams.set(key, String(value));
      }
    });

    let requestBody = undefined;
    if (method === "POST" || method === "DELETE") {
      let body = {};
      try {
        body = event.body
          ? JSON.parse(event.isBase64Encoded ? Buffer.from(event.body, "base64").toString("utf8") : event.body)
          : {};
      } catch {
        body = {};
      }
      if (pathParam.includes("/oauth/device/code")) {
        body.client_id = clientId;
      } else if (pathParam.includes("/oauth/device/token") || pathParam.includes("/oauth/token")) {
        body.client_id = clientId;
        body.client_secret = clientSecret;
      }
      requestBody = Object.keys(body).length > 0 ? JSON.stringify(body) : undefined;
    }

    const headers = {
      "content-type": "application/json",
      "trakt-api-key": clientId,
      "trakt-api-version": "2"
    };
    const userToken = event.headers["x-user-token"] || event.headers["X-User-Token"];
    if (userToken) headers.authorization = `Bearer ${userToken}`;

    const response = await fetch(traktUrl, { method, headers, body: requestBody });
    const text = await response.text();
    let data;
    try {
      data = text ? JSON.parse(text) : { status: response.status };
    } catch {
      data = text ? { raw: text } : { status: response.status };
    }
    return {
      statusCode: response.status,
      headers: {
        ...JSON_HEADERS,
        "cache-control": "no-store",
        "x-pagination-page": response.headers.get("x-pagination-page") || "",
        "x-pagination-limit": response.headers.get("x-pagination-limit") || "",
        "x-pagination-page-count": response.headers.get("x-pagination-page-count") || "",
        "x-pagination-item-count": response.headers.get("x-pagination-item-count") || ""
      },
      body: JSON.stringify(data)
    };
  } catch (error) {
    return json(502, { error: errorMessage(error) });
  }
}

function payloadMetrics(payload) {
  const root = typeof payload === "string" ? JSON.parse(payload) : payload;
  const profiles = Array.isArray(root.profiles) ? root.profiles : null;
  const profileCount = profiles ? profiles.length : null;
  const profileIds = new Set(
    (profiles || [])
      .map((profile) => profile && profile.id)
      .filter((id) => typeof id === "string" && id.length > 0)
  );
  const scopedKeys = [
    "profileSettingsById",
    "addonsByProfile",
    "catalogsByProfile",
    "hiddenPreinstalledByProfile",
    "hiddenAddonByProfile",
    "hiddenHomeServerByProfile",
    "iptvByProfile",
    "watchlistByProfile"
  ];
  const scopedCoverage = scopedKeys.reduce((total, key) => {
    const obj = root[key];
    if (!obj || typeof obj !== "object" || Array.isArray(obj)) return total;
    let count = 0;
    profileIds.forEach((profileId) => {
      if (Object.prototype.hasOwnProperty.call(obj, profileId)) count += 1;
    });
    return total + count;
  }, 0);

  const hasFullShape = scopedKeys.some((key) => Object.prototype.hasOwnProperty.call(root, key));
  const hasConfiguredState =
    (Array.isArray(root.addons) && root.addons.length > 0) ||
    Boolean(String(root.iptvM3uUrl || "").trim()) ||
    Object.values(root.addonsByProfile || {}).some((value) => Array.isArray(value) && value.length > 0) ||
    Object.values(root.watchlistByProfile || {}).some((value) => Array.isArray(value) && value.length > 0) ||
    Object.values(root.iptvByProfile || {}).some((value) => {
      if (!value || typeof value !== "object") return false;
      return Boolean(String(value.m3uUrl || "").trim()) ||
        Boolean(String(value.epgUrl || "").trim()) ||
        (Array.isArray(value.playlists) && value.playlists.length > 0) ||
        (Array.isArray(value.favoriteChannels) && value.favoriteChannels.length > 0) ||
        (Array.isArray(value.favoriteGroups) && value.favoriteGroups.length > 0);
    });

  let usefulProfiles = false;
  if (profileCount > 1) {
    usefulProfiles = true;
  } else if (profileCount === 1) {
    const profile = profiles[0] || {};
    usefulProfiles = !(
      String(profile.name || "").toLowerCase() === "profile 1" &&
      Number(profile.avatarId || 0) === 0 &&
      Number(profile.avatarImageVersion || 0) <= 0 &&
      !profile.isKidsProfile &&
      !profile.isLocked &&
      !String(profile.pin || "").trim()
    );
  }

  let restoreRank;
  if (profileCount !== null && profileCount <= 0) restoreRank = 0;
  else if (profileCount !== null && profileCount > 1 && hasFullShape) restoreRank = 80;
  else if (profileCount !== null && profileCount > 1) restoreRank = 70;
  else if ((usefulProfiles || hasConfiguredState) && hasFullShape) restoreRank = 50;
  else if (usefulProfiles || hasConfiguredState) restoreRank = 40;
  else if (profileCount === null && hasFullShape) restoreRank = 30;
  else if (profileCount === null) restoreRank = 20;
  else restoreRank = 10;

  return {
    payload: root,
    profileCount,
    scopedCoverage,
    restoreRank,
    payloadVersion: Number(root.version || 1),
    payloadUpdatedAt: Number(root.updatedAt || 0) > 0
      ? new Date(Number(root.updatedAt)).toISOString()
      : null
  };
}

function isExistingSnapshotRicher(existing, incoming) {
  if (!existing) return false;
  const existingRank = Number(existing.restore_rank ?? existing.restoreRank ?? 0);
  const existingProfilesRaw = existing.profile_count ?? existing.profileCount;
  const existingCoverage = Number(existing.scoped_coverage ?? existing.scopedCoverage ?? 0);

  if (existingRank > incoming.restoreRank) return true;
  if (existingRank < incoming.restoreRank) return false;

  const existingProfiles = existingProfilesRaw === null || existingProfilesRaw === undefined
    ? -1
    : Number(existingProfilesRaw);
  const incomingProfiles = incoming.profileCount === null || incoming.profileCount === undefined
    ? -1
    : Number(incoming.profileCount);
  if (existingProfiles > incomingProfiles) return true;
  if (existingProfiles < incomingProfiles) return false;

  return existingCoverage > incoming.scopedCoverage;
}

async function verifySupabaseToken(accessToken) {
  const supabaseUrl = (process.env.SUPABASE_URL || "").replace(/\/+$/, "");
  const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || "";
  if (!supabaseUrl || !supabaseAnonKey) {
    throw new Error("Supabase migration verifier is not configured");
  }
  const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: {
      apikey: supabaseAnonKey,
      authorization: `Bearer ${accessToken}`
    }
  });
  if (!response.ok) {
    throw new Error(`Supabase token rejected (${response.status})`);
  }
  const user = await response.json();
  const id = user.id || user.sub;
  const email = normalizeEmail(user.email);
  if (!id || !email) {
    throw new Error("Supabase token has no usable user identity");
  }
  return { supabaseUserId: id, email };
}

function bearerToken(event) {
  const auth = event.headers.authorization || event.headers.Authorization || "";
  const match = auth.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : "";
}

async function resolveIdentity(event) {
  const token = bearerToken(event);
  if (!token) {
    throw new Error("Missing Authorization bearer token");
  }
  return verifySupabaseToken(token);
}

function snapshotStores(event) {
  connectLambda(event);
  return {
    account: getStore("account-sync"),
    legacy: getStore("legacy-supabase-sync"),
    events: getStore("account-sync-events"),
    usage: getStore("app-usage")
  };
}

function snapshotKeys(identity) {
  const supabaseUserId = String(identity.supabaseUserId || "").trim();
  const email = normalizeEmail(identity.email);
  return {
    supabase: `supabase/${supabaseUserId}.json`,
    email: `email/${sha256(email)}.json`
  };
}

async function getJSONOrNull(store, key) {
  try {
    return await store.get(key, { type: "json", consistency: "strong" });
  } catch (error) {
    if (String(error?.message || "").includes("uncachedEdgeURL")) {
      return await store.get(key, { type: "json" });
    }
    if (error?.status === 404 || error?.name === "BlobNotFoundError") return null;
    throw error;
  }
}

async function loadSnapshotFromBlobs(event, identity) {
  const stores = snapshotStores(event);
  const keys = snapshotKeys(identity);
  const accountSnapshot = await getJSONOrNull(stores.account, keys.supabase) ||
    await getJSONOrNull(stores.account, keys.email);
  if (accountSnapshot) return { ...accountSnapshot, source: accountSnapshot.source || "netlify" };

  const legacySnapshot = await getJSONOrNull(stores.legacy, keys.supabase) ||
    await getJSONOrNull(stores.legacy, keys.email);
  if (!legacySnapshot) return null;

  const claimed = {
    ...legacySnapshot,
    source: "supabase_import_claimed",
    claimedAt: new Date().toISOString()
  };
  await saveSnapshotToBlobs(event, identity, claimed);
  return claimed;
}

async function saveSnapshotToBlobs(event, identity, snapshot) {
  const stores = snapshotStores(event);
  const keys = snapshotKeys(identity);
  const normalized = {
    payload: snapshot.payload,
    payloadVersion: snapshot.payloadVersion ?? snapshot.payload_version ?? 1,
    restoreRank: snapshot.restoreRank ?? snapshot.restore_rank ?? 0,
    profileCount: snapshot.profileCount ?? snapshot.profile_count ?? null,
    scopedCoverage: snapshot.scopedCoverage ?? snapshot.scoped_coverage ?? 0,
    payloadUpdatedAt: snapshot.payloadUpdatedAt ?? snapshot.payload_updated_at ?? null,
    source: snapshot.source || "netlify",
    updatedAt: snapshot.updatedAt || new Date().toISOString()
  };
  const metadata = {
    email: normalizeEmail(identity.email),
    supabaseUserId: identity.supabaseUserId,
    restoreRank: String(normalized.restoreRank),
    profileCount: String(normalized.profileCount ?? ""),
    updatedAt: normalized.updatedAt
  };
  await stores.account.setJSON(keys.supabase, normalized, { metadata });
  await stores.account.setJSON(keys.email, normalized, { metadata });
  return normalized;
}

async function appendSnapshotEvent(event, identity, snapshot) {
  const stores = snapshotStores(event);
  const cursor = Date.now();
  const keys = snapshotKeys(identity);
  await stores.events.setJSON(`supabase/${identity.supabaseUserId}/${cursor}.json`, {
    event_id: cursor,
    scope: "snapshot",
    profile_id: "",
    entity_key: "account",
    operation: "upsert",
    payload: snapshot.payload,
    item_version: cursor,
    created_at: new Date(cursor).toISOString()
  }, {
    metadata: {
      supabaseUserId: identity.supabaseUserId,
      email: normalizeEmail(identity.email),
      accountKey: keys.supabase
    }
  });
  return cursor;
}

async function getOrCreateAccount(client, identity) {
  const email = normalizeEmail(identity.email);
  const existing = await client.query(
    `SELECT *
       FROM public.arvio_accounts
      WHERE supabase_user_id = $1 OR email_normalized = $2
      ORDER BY CASE WHEN supabase_user_id = $1 THEN 0 ELSE 1 END
      LIMIT 1`,
    [identity.supabaseUserId, email]
  );
  if (existing.rows[0]) {
    const account = existing.rows[0];
    await client.query(
      `UPDATE public.arvio_accounts
          SET email = $2,
              email_normalized = $3,
              supabase_user_id = COALESCE(supabase_user_id, $1::uuid),
              updated_at = now(),
              last_seen_at = now()
        WHERE id = $4`,
      [identity.supabaseUserId, identity.email, email, account.id]
    );
    return { ...account, email: identity.email, email_normalized: email };
  }

  const inserted = await client.query(
    `INSERT INTO public.arvio_accounts (email, email_normalized, supabase_user_id, last_seen_at)
     VALUES ($1, $2, $3::uuid, now())
     RETURNING *`,
    [identity.email, email, identity.supabaseUserId]
  );
  return inserted.rows[0];
}

async function claimLegacySnapshotIfNeeded(client, account, identity) {
  const current = await client.query(
    `SELECT payload, payload_version, restore_rank, profile_count, scoped_coverage,
            payload_updated_at, updated_at, source
       FROM public.account_sync_snapshots
      WHERE account_id = $1`,
    [account.id]
  );
  if (current.rows[0]) return current.rows[0];

  const legacy = await client.query(
    `SELECT *
       FROM public.legacy_supabase_snapshots
      WHERE supabase_user_id = $1::uuid OR email_normalized = $2
      ORDER BY restore_rank DESC, profile_count DESC NULLS LAST, scoped_coverage DESC, payload_updated_at DESC NULLS LAST
      LIMIT 1`,
    [identity.supabaseUserId, normalizeEmail(identity.email)]
  );
  const row = legacy.rows[0];
  if (!row) return null;

  await client.query(
    `INSERT INTO public.account_sync_snapshots (
       account_id, payload, payload_version, restore_rank, profile_count,
       scoped_coverage, payload_updated_at, source, updated_at
     )
     VALUES ($1, $2::jsonb, $3, $4, $5, $6, $7, 'supabase_import', now())
     ON CONFLICT (account_id) DO NOTHING`,
    [
      account.id,
      JSON.stringify(row.payload),
      row.payload_version,
      row.restore_rank,
      row.profile_count,
      row.scoped_coverage,
      row.payload_updated_at
    ]
  );
  await client.query(
    `UPDATE public.legacy_supabase_snapshots
        SET claimed_account_id = $2,
            claimed_at = now()
      WHERE supabase_user_id = $1::uuid`,
    [identity.supabaseUserId, account.id]
  );

  return {
    payload: row.payload,
    payload_version: row.payload_version,
    restore_rank: row.restore_rank,
    profile_count: row.profile_count,
    scoped_coverage: row.scoped_coverage,
    payload_updated_at: row.payload_updated_at,
    updated_at: row.imported_at,
    source: "supabase_import"
  };
}

module.exports = {
  getPool,
  json,
  options,
  parseBody,
  payloadMetrics,
  isExistingSnapshotRicher,
  resolveIdentity,
  getOrCreateAccount,
  claimLegacySnapshotIfNeeded,
  normalizeEmail,
  sha256,
  snapshotStores,
  snapshotKeys,
  loadSnapshotFromBlobs,
  saveSnapshotToBlobs,
  appendSnapshotEvent,
  handleAuthRefresh,
  handleCloudAuthEmail,
  handleCloudAuthReset,
  handleTmdbProxy,
  handleTraktProxy,
  handleTvAuthApprove,
  handleTvAuthComplete,
  handleTvAuthStart,
  handleTvAuthStatus
};
