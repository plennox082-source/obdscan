# Cloudflare Worker for OBD Scan voice-only mechanic assistant

This Worker receives `{ "prompt": "..." }` and returns one AI response containing spoken lines for Pat, Adam, Steve, and Jesse. The Android app speaks those lines without displaying the conversation text.

## Cost and limits

- One AI inference provides all four characters; it does not make four separate model calls.
- The Worker limits prompts to 1,600 characters and asks for at most 512 output tokens. Responses finish automatically; playback can be stopped in the app.
- This code cannot alter your Cloudflare plan or billing settings, and a model choice cannot guarantee zero cost. Before deployment, verify in your Cloudflare dashboard that the account is on the Free plan and that paid billing/upgrade options are not enabled. If the free allowance is exhausted, requests may fail until it resets. Check the current dashboard and pricing page because allowances, model availability, and billing rules can change.
- These settings only relate to this Worker; they do not guarantee that other Cloudflare account services are free.

## Deploy

From this directory, install Node.js if needed, run `npx wrangler login`, complete Cloudflare authorization in your browser, then run `npx wrangler deploy`. Copy the HTTPS `workers.dev` URL into the app's Crew setup field. The `AI` binding is declared in `wrangler.toml`.

For a browser client, set `CORS_ORIGIN` to your exact site origin instead of `*`. Native Android requests do not use browser CORS. Do not put account secrets in the Android app or commit them to the repository.
