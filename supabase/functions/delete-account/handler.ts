const headers = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const response = (status: number, message: string) => new Response(JSON.stringify({ message }), { status, headers });

/** Only inspect claims AFTER authenticate has verified this exact token with Supabase Auth. */
export function hasRecentPasswordAuthentication(jwt: string, userId: string, now = Math.floor(Date.now() / 1000)): boolean {
  try {
    const parts = jwt.split(".");
    if (parts.length !== 3 || jwt.length > 16384) return false;
    const encoded = parts[1].replaceAll("-", "+").replaceAll("_", "/");
    const claims = JSON.parse(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "=")));
    return claims.sub === userId && claims.role === "authenticated" &&
      typeof claims.exp === "number" && claims.exp > now &&
      Array.isArray(claims.amr) && claims.amr.some((entry: { method?: unknown; timestamp?: unknown }) =>
        entry?.method === "password" && typeof entry.timestamp === "number" &&
        Number.isSafeInteger(entry.timestamp) && entry.timestamp >= now - 300 && entry.timestamp <= now + 30);
  } catch { return false; }
}

export function createDeleteAccountHandler(deps: {
  authenticate: (token: string) => Promise<string | null>;
  deleteUser: (userId: string) => Promise<boolean>;
  now?: () => number;
}) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return response(405, "POST required.");
    const authorization = request.headers.get("authorization") ?? "";
    const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
    if (!token || token.length > 16384) return response(401, "Sign in again before deleting the account.");
    try {
      // The identity comes from the verified token, never a request body or decoded-only sub.
      const userId = await deps.authenticate(token);
      if (!userId || !hasRecentPasswordAuthentication(token, userId, deps.now?.())) {
        return response(401, "Sign in with your password again before deleting the account.");
      }
      if (!await deps.deleteUser(userId)) return response(500, "The account could not be deleted. Try again.");
      return response(200, "Account deleted.");
    } catch {
      // Never return/log provider messages, credentials or personally identifying fields.
      return response(503, "Account deletion is temporarily unavailable.");
    }
  };
}
