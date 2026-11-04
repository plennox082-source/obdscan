# Cloudflare Worker for OBD Scan voice assistant

This Worker accepts `{ "prompt": "..." }` and returns the response shape used by the Android app:

```json
{"success":true,"data":{"response":"..."}}
```

## Deploy

1. Install Node.js and Wrangler, then sign in to Cloudflare: `npx wrangler login`.
2. In this directory, deploy with `npx wrangler deploy`.
3. Copy the deployed HTTPS `workers.dev` URL into the app's Pat assistant screen.
4. Confirm Workers AI is available for your Cloudflare account. The Wrangler config defines an `AI` binding and defaults to `@cf/meta/llama-3.1-8b-instruct`.
5. If you also call the Worker from a browser page, change `CORS_ORIGIN` in `wrangler.toml` from `*` to the exact origin of that page before deploying. Native Android requests do not require browser CORS.

Do not put API keys or secrets in the Android app or commit them to this repository. The app creates the short Adam/Steve/Jesse comments locally after Pat's Worker response; those comments do not make separate model/API calls.
