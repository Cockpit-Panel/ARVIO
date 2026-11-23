import { NextRequest, NextResponse } from "next/server";
import { simklRequestContext, simklSessions, simklSessionFailure } from "@/lib/server/simklSessionHttp";

export const runtime = "nodejs";

function envValue(value: string | undefined, fallback = "") {
  return value && !value.startsWith("$") ? value : fallback;
}

const SIMKL_REQUEST_RULES = [
  { path: /^\/lists\/(?:[1-9]\d*|user\/[1-9]\d*)$/, methods: new Set(["GET"]) },
  { path: /^\/oauth\/pin(?:\/[A-Za-z0-9-]+)?$/, methods: new Set(["GET"]) },
  { path: /^\/oauth\/token$/, methods: new Set(["POST"]) },
  { path: /^\/users\/settings$/, methods: new Set(["POST"]) },
  { path: /^\/scrobble\/(?:start|pause|stop|checkin)$/, methods: new Set(["POST"]) },
  { path: /^\/sync\/activities$/, methods: new Set(["GET"]) },
  { path: /^\/sync\/all-items(?:\/(?:movies|shows|anime|all)(?:\/(?:watching|plantowatch|hold|completed|dropped|all))?)?$/, methods: new Set(["GET"]) },
  { path: /^\/sync\/playback(?:\/(?:movies|episodes|shows|anime|all))?$/, methods: new Set(["GET"]) },
  { path: /^\/sync\/playback\/\d+$/, methods: new Set(["DELETE"]) },
  { path: /^\/sync\/(?:history|history\/remove|add-to-list)$/, methods: new Set(["POST"]) }
];

function isAllowedSimklRequest(path: string, method: string) {
  return SIMKL_REQUEST_RULES.some((rule) => rule.path.test(path) && rule.methods.has(method));
}

async function handler(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const netlifyBackendUrl = (
    process.env.NEXT_PUBLIC_NETLIFY_BACKEND_URL ??
    process.env.NETLIFY_BACKEND_URL ??
    "https://auth.arvio.tv/.netlify/functions"
  ).replace(/\/+$/, "");
  const appAnonKey = envValue(process.env.NEXT_PUBLIC_ARVIO_APP_ANON_KEY, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "");
  const isV2 = path[0] === "oauth2" || path[0] === "lists" || request.headers.get("x-user-token")?.startsWith("simkl_at_");
  const simklClientId = isV2
    ? process.env.NEXT_PUBLIC_SIMKL_V2_CLIENT_ID || process.env.SIMKL_V2_CLIENT_ID || (process.env.NEXT_PUBLIC_SELF_HOSTED === "true" ? "" : "ebde0a0712da059710ba65ba3efe1edb601c2e5ae8e23c048c8d6df58ed922c2")
    : process.env.NEXT_PUBLIC_SIMKL_CLIENT_ID || process.env.SIMKL_CLIENT_ID || "";
  const simklSecret = process.env.SIMKL_CLIENT_SECRET ?? "";
  const input = new URL(request.url);
  const method = request.method;
  const body = method === "GET" || method === "HEAD" ? undefined : await request.text();
  const normalizedPath = `/${path.join("/")}`;

  // AUTH V2 tokens are never accepted from or returned to browser JavaScript.
  if (path[0] === "oauth2" || request.headers.get("x-user-token")?.startsWith("simkl_at_")) {
    return NextResponse.json({ error: "session_required", message: "Reload ARVIO to use the secure SIMKL connection." }, { status: 409, headers: { "Cache-Control": "no-store" } });
  }

  if (!isAllowedSimklRequest(normalizedPath, method)) {
    return NextResponse.json({ error: "Path or method not allowed" }, { status: 403 });
  }

  if (request.headers.has("x-simkl-connection")) {
    try {
      const session = simklRequestContext(request);
      if (!session.id) return NextResponse.json({ error: "session_required" }, { status: 401, headers: { "Cache-Control": "no-store" } });
      const response = await simklSessions().request(session.id, session.profile, request.headers.get("x-simkl-connection")!, normalizedPath, method, input.searchParams, body);
      return new NextResponse(response.body, { status: response.status, headers: {
        "Content-Type": response.headers.get("content-type") || "application/json", "Cache-Control": "no-store", Pragma: "no-cache",
        ...(response.headers.get("retry-after") ? { "Retry-After": response.headers.get("retry-after")! } : {})
      } });
    } catch (error) { return simklSessionFailure(error); }
  }

  let target: URL;
  let headers: HeadersInit;

  const usesNetlifyProxy = !isV2 && process.env.NEXT_PUBLIC_SELF_HOSTED !== "true" && netlifyBackendUrl.startsWith("https://") && appAnonKey.length > 40;

  if (usesNetlifyProxy) {
    target = new URL(`${netlifyBackendUrl}/simkl-proxy`);
    target.searchParams.set("path", normalizedPath);
    target.searchParams.set("method", method);
    target.searchParams.set("client_id", simklClientId);
    target.searchParams.set("app-name", "arvio");
    target.searchParams.set("app-version", "2.0");
    input.searchParams.forEach((value, key) => {
      if (key !== "client_id" && key !== "client_secret" && key !== "app-name" && key !== "app-version") {
        target.searchParams.set(key, value);
      }
    });
    headers = {
      apikey: appAnonKey,
      Authorization: `Bearer ${appAnonKey}`,
      "user-agent": request.headers.get("user-agent") || "ARVIO/2.0 (Web)"
    };
    const userToken = request.headers.get("x-user-token");
    if (userToken) headers["x-user-token" as keyof HeadersInit] = userToken;
  } else if (simklClientId) {
    target = new URL(`https://api.simkl.com${normalizedPath}`);
    input.searchParams.forEach((value, key) => {
      if (key !== "client_id" && key !== "client_secret") target.searchParams.set(key, value);
    });
    target.searchParams.set("client_id", simklClientId);
    if (!target.searchParams.has("app-name") || target.searchParams.get("app-name") === "ARVIO") {
      target.searchParams.set("app-name", "arvio");
    }
    if (!target.searchParams.has("app-version")) {
      target.searchParams.set("app-version", "2.0");
    }
    headers = {
      "content-type": "application/json",
      "simkl-api-key": simklClientId,
      "user-agent": request.headers.get("user-agent") || "ARVIO/2.0 (Web)"
    };
    const userToken = request.headers.get("x-user-token");
    if (userToken) headers.Authorization = `Bearer ${userToken}`;
  } else {
    return NextResponse.json({ error: "Simkl proxy is not configured" }, { status: 500 });
  }

  if (["/scrobble/start", "/scrobble/stop", "/scrobble/checkin"].includes(normalizedPath)) target.searchParams.set("min_progress", "90");

  const parsedBody = body && normalizedPath === "/oauth/token" && simklSecret && !usesNetlifyProxy
    ? JSON.stringify({ ...JSON.parse(body), client_id: simklClientId, client_secret: simklSecret })
    : body;

  const response = await fetch(target, {
    method,
    headers,
    body: parsedBody,
    cache: "no-store"
  });

  const responseHeaders = new Headers();
  responseHeaders.set("content-type", response.headers.get("content-type") ?? "application/json");
  responseHeaders.set("cache-control", "no-store");
  responseHeaders.set("pragma", "no-cache");
  const retryAfter = response.headers.get("retry-after");
  if (retryAfter) {
    responseHeaders.set("retry-after", retryAfter);
  }

  return new NextResponse(response.body, {
    status: response.status,
    headers: responseHeaders
  });
}

export const GET = handler;
export const POST = handler;
export const DELETE = handler;
