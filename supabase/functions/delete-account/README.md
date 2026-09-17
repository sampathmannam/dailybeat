# Delete-account Edge Function

This function is the only account-deletion path. It validates the caller with Supabase Auth,
requires an access token issued within the last five minutes, and uses the server-provided
`SUPABASE_SERVICE_ROLE_KEY` only inside the Edge Function. The Android app must reauthenticate the
user immediately before calling it. Never add the service-role key to Gradle, the APK, CI logs, or
repository secrets used by client builds.

Deploy with JWT verification enabled:

```sh
supabase functions deploy delete-account --project-ref mrhffxtuzxqzcqicchoj
```

The built-in Supabase environment variables are sufficient; do not create duplicate secrets.
