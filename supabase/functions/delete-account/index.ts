import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function response(status: number, message: string): Response {
  return new Response(JSON.stringify({ message }), { status, headers: jsonHeaders });
}

function issuedRecently(jwt: string, nowSeconds = Math.floor(Date.now() / 1000)): boolean {
  try {
    const encoded = jwt.split(".")[1];
    if (!encoded) return false;
    const padded = encoded.replaceAll("-", "+").replaceAll("_", "/")
      .padEnd(Math.ceil(encoded.length / 4) * 4, "=");
    const payload = JSON.parse(atob(padded)) as { iat?: number };
    return typeof payload.iat === "number" &&
      payload.iat >= nowSeconds - 5 * 60 &&
      payload.iat <= nowSeconds + 60;
  } catch {
    return false;
  }
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return response(405, "POST required.");

  const authorization = request.headers.get("authorization") ?? "";
  const accessToken = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!accessToken || !issuedRecently(accessToken)) {
    return response(401, "Sign in again before deleting the account.");
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonymousKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonymousKey || !serviceRoleKey) {
    console.error("delete-account is missing a required Supabase environment secret");
    return response(503, "Account deletion is temporarily unavailable.");
  }

  const caller = createClient(supabaseUrl, anonymousKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error: callerError } = await caller.auth.getUser(accessToken);
  if (callerError || !data.user) return response(401, "The session is no longer valid.");

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { error: deleteError } = await admin.auth.admin.deleteUser(data.user.id, false);
  if (deleteError) {
    console.error("delete-account failed", deleteError.message);
    return response(500, "The account could not be deleted. Try again.");
  }

  return response(200, "Account deleted.");
});
