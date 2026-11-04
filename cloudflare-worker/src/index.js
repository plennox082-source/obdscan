const MAX_PROMPT_LENGTH = 4000;
const MAX_OUTPUT_TOKENS = 2048;
const DEFAULT_MODEL = "@cf/meta/llama-3.2-1b-instruct";

function corsHeaders(env) {
  return {
    "Access-Control-Allow-Origin": env.CORS_ORIGIN || "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Content-Type": "application/json; charset=utf-8",
    "Vary": "Origin"
  };
}

function jsonResponse(body, status, env) {
  return new Response(JSON.stringify(body), {
    status,
    headers: corsHeaders(env)
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(env) });
    }

    if (request.method === "GET") {
      return jsonResponse({ success: true, data: { status: "ready" } }, 200, env);
    }

    if (request.method !== "POST") {
      return jsonResponse({ success: false, error: "Method not allowed" }, 405, env);
    }

    if (!env.AI) {
      return jsonResponse({ success: false, error: "Cloudflare Workers AI binding is missing" }, 500, env);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return jsonResponse({ success: false, error: "Request body must be valid JSON" }, 400, env);
    }

    const prompt = typeof body?.prompt === "string" ? body.prompt.trim() : "";
    if (!prompt) {
      return jsonResponse({ success: false, error: "prompt is required" }, 400, env);
    }
    if (prompt.length > MAX_PROMPT_LENGTH) {
      return jsonResponse({ success: false, error: `prompt must be ${MAX_PROMPT_LENGTH} characters or fewer` }, 413, env);
    }

    try {
      const result = await env.AI.run(env.MODEL || DEFAULT_MODEL, {
        messages: [
          {
            role: "system",
            content: "You are Pat, a helpful automotive troubleshooting assistant. Give thorough, well-organized answers with practical next steps, while staying concise when the question is simple. Explain uncertainty and never claim a diagnosis is certain from symptoms alone. Prioritize safety: tell the user to stop driving when a condition could make the vehicle unsafe, and recommend a qualified mechanic for high-risk repairs. Do not instruct users to perform unsafe work around a running engine or energized vehicle systems."
          },
          { role: "user", content: prompt }
        ],
        max_tokens: MAX_OUTPUT_TOKENS
      });

      const answer = typeof result?.response === "string" ? result.response.trim() : "";
      if (!answer) {
        return jsonResponse({ success: false, error: "The AI returned an empty response" }, 502, env);
      }

      return jsonResponse({ success: true, data: { response: answer } }, 200, env);
    } catch (error) {
      console.error("Workers AI request failed", error);
      return jsonResponse({ success: false, error: "Assistant service is temporarily unavailable" }, 502, env);
    }
  }
};
