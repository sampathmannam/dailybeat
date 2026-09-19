import { createDeleteAccountHandler } from "./handler.ts";
const now = 1900000000;
const jwt = (claims: object) => `header.${btoa(JSON.stringify({sub:"user-a",role:"authenticated", exp:now+3600, iat:now,...claims})).replaceAll("=", "")}.signature`;
const assert = (value: unknown) => { if (!value) throw new Error("Assertion failed"); };

for (const [name, claims, verified, status] of [
  ["recent password", { amr: [{method:"password",timestamp:now-10}] }, "user-a", 200],
  ["fresh refresh with old password", { amr: [{method:"password",timestamp:now-86400},{method:"token_refresh",timestamp:now}] }, "user-a", 401],
  ["refresh only", { amr: [{method:"token_refresh",timestamp:now}] }, "user-a", 401],
  ["future authentication", { amr: [{method:"password",timestamp:now+100}] }, "user-a", 401],
  ["missing amr", {}, "user-a", 401],
  ["forged recent token", { amr: [{method:"password",timestamp:now}] }, null, 401],
  ["identity mismatch", { amr: [{method:"password",timestamp:now}] }, "user-b", 401],
  ["expired token", { exp:now-1, amr: [{method:"password",timestamp:now}] }, "user-a", 401],
  ["malformed amr", { amr: [null, 123] }, "user-a", 401],
] as const) {
  Deno.test(name, async () => {
    const deleted: string[] = [];
    const handler = createDeleteAccountHandler({authenticate:async()=>verified,
      deleteUser:async id => {deleted.push(id); return true;}, now:()=>now});
    const result = await handler(new Request("https://example.com/delete", {method:"POST",headers:{authorization:`Bearer ${jwt(claims)}`}}));
    assert(result.status === status);
    assert(deleted.length === (status === 200 ? 1 : 0));
    if (deleted.length) assert(deleted[0] === "user-a");
  });
}
Deno.test("server failure returns no provider details", async()=>{
  const handler = createDeleteAccountHandler({authenticate:async()=>{throw new Error("private-secret");},deleteUser:async()=>{throw new Error("must not call");}});
  const result = await handler(new Request("https://example.com/delete",{method:"POST",headers:{authorization:"Bearer token"}}));
  assert(result.status===503); assert(!(await result.text()).includes("private-secret"));
});
