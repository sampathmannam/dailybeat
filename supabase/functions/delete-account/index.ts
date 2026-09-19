import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { createDeleteAccountHandler } from "./handler.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const anonymousKey = Deno.env.get("SUPABASE_ANON_KEY");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

Deno.serve(createDeleteAccountHandler({
  authenticate: async (accessToken) => {
    if (!supabaseUrl || !anonymousKey || !serviceRoleKey) throw new Error("Unavailable");
    const caller = createClient(supabaseUrl, anonymousKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await caller.auth.getUser(accessToken);
    return error ? null : data.user?.id ?? null;
  },
  deleteUser: async (userId) => {
    if (!supabaseUrl || !serviceRoleKey) throw new Error("Unavailable");
    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { error } = await admin.auth.admin.deleteUser(userId, false);
    return !error;
  },
}));
