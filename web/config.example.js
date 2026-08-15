// Where the cloud lives, for the companion web app (PLAN 17.14).
//
// Copy this file to `config.js` and fill it in. `config.js` is git-ignored, for
// exactly the reason `local.properties` is on the Android side: an endpoint is
// somebody's private household project and a key is theirs to publish or not.
//
// Both values are the same two the Android build reads — the *publishable*
// (anon) key from Project Settings → API. Never the service-role key, and never
// an `sbp_` personal access token: that one is account-wide and can delete
// every project on the account.
//
// **This project uses the `sb_publishable_…` form** (PLAN 17.16.3). Supabase
// issues two publishable keys for the same project — the newer `sb_publishable_`
// one and a legacy JWT beginning `eyJ` — and both work. Neither is a secret and
// this is tidiness rather than exposure, but **they revoke separately**, so a
// rotation that changes the one you remember leaves the other one live on the
// internet. One form, said here and in `cloud.properties`, and
// `web/check-deployed.sh` reports which form the host is actually serving.
//
// A page with no config says so on screen rather than failing in the console.
// This project's cloud defects have historically gone to die in a console
// nobody had open.

window.PELONOT_CONFIG = {
  supabaseUrl: 'https://YOUR-PROJECT-REF.supabase.co',
  supabaseAnonKey: 'sb_publishable_...',
};
