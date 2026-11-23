import { NextRequest, NextResponse } from "next/server";
import { SimklSessionError, SimklSessions, simklCookieName, validSimklSessionId } from "./simklSession";
import { simklSessionStore } from "./simklSessionStore";

export function simklRequestContext(request: NextRequest) {
  const url = new URL(request.url);
  // Next can use an internal hostname while a reverse proxy serves the public origin.
  const host = (request.headers.get("x-forwarded-host") || request.headers.get("host") || url.host).split(",")[0].trim();
  const protocol = (request.headers.get("x-forwarded-proto") || url.protocol.slice(0, -1)).split(",")[0].trim();
  if (!["http", "https"].includes(protocol)) throw new SimklSessionError("cross_origin_request", 403);
  const origin = new URL(`${protocol}://${host}`).origin;
  if ((request.headers.get("origin") && request.headers.get("origin") !== origin) ||
      request.headers.get("sec-fetch-site") === "cross-site") throw new SimklSessionError("cross_origin_request", 403);
  const profile = request.headers.get("x-simkl-profile");
  if (!profile || profile.length > 128 || /[\x00-\x1f]/.test(profile)) throw new SimklSessionError("profile_required");
  const secure = origin.startsWith("https://");
  const cookie = simklCookieName(profile, secure);
  const id = request.cookies.get(cookie)?.value;
  return { profile, secure, cookie, id: validSimklSessionId(id) ? id : undefined };
}

export function simklSessions() {
  const clientId = process.env.SIMKL_V2_CLIENT_ID || process.env.NEXT_PUBLIC_SIMKL_V2_CLIENT_ID ||
    (process.env.NEXT_PUBLIC_SELF_HOSTED === "true" ? "" : "ebde0a0712da059710ba65ba3efe1edb601c2e5ae8e23c048c8d6df58ed922c2");
  return new SimklSessions(simklSessionStore(), clientId);
}

export function simklSessionFailure(error: unknown) {
  const known = error instanceof SimklSessionError;
  return NextResponse.json({ error: known ? error.reason : "session_unavailable", message: known ? error.reason : "SIMKL connection is temporarily unavailable. Please retry." }, {
    status: known ? error.status : 503,
    headers: { "Cache-Control": "no-store", Pragma: "no-cache", ...(known && error.retryAfter ? { "Retry-After": error.retryAfter } : {}) }
  });
}
