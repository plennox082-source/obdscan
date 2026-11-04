# Cloudflare Worker for OBD Scan voice assistant

This Worker accepts `{ "prompt": "..." }` and returns the response shape used by the Android app:

```json
{"success":true,"data":{"response":"..."}}
```

The default model is `@cf/meta/llama-3.2-1b-instruct`, selected as a lightweight text model to help conserve Workers AI usage. The Worker requests up to 2,048 output tokens; actual output length depends on model limits and the daily account allowance. Longer outputs consume more of that allowance.

## Cost note

This configuration is intended to use Cloudflare Workers AI's included free daily allowance where available. Free quotas, model availability, billing rules, and usage rates can change and depend on your Cloudflare account. Check your account's Workers AI usage and the current Cloudflare pricing page before relying on it as zero-cost. Do not enable paid billing or overages unless you choose to.

## Deploy

1. Install Node.js and Wrangler, then sign in to Cloudflare: `npx wrangler login`.
2. From this directory, deploy with `npx wrangler deploy`.
3. Copy the deployed HTTPS `workers.dev` URL into the app's Pat assistant screen.
4. Confirm Workers AI is available for your Cloudflare account. The Wrangler config defines an `AI` binding and defaults to `@cf/meta/llama-3.2-1b-instruct`.
5. If you also call the Worker from a browser page, change `CORS_ORIGIN` in `wrangler.toml` from `*` to the exact origin of that page before deploying. Native Android requests do not require browser CORS.

Do not put API keys or secrets in the Android app or commit them to this repository. The app creates the short Adam/Steve/Jesse comments locally after Pat's Worker response; those comments do not make separate model/API calls.
