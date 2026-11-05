const MAX_PROMPT_LENGTH = 1600;
const MAX_OUTPUT_TOKENS = 512;
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
  return new Response(JSON.stringify(body), { status, headers: corsHeaders(env) });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(env) });
    if (request.method === "GET") return jsonResponse({ success: true, data: { status: "ready" } }, 200, env);
    if (request.method !== "POST") return jsonResponse({ success: false, error: "Method not allowed" }, 405, env);
    if (!env.AI) return jsonResponse({ success: false, error: "Workers AI binding is missing" }, 500, env);

    let body;
    try {
      body = await request.json();
    } catch {
      return jsonResponse({ success: false, error: "Request body must be valid JSON" }, 400, env);
    }
    const prompt = typeof body?.prompt === "string" ? body.prompt.trim() : "";
    if (!prompt) return jsonResponse({ success: false, error: "prompt is required" }, 400, env);
    if (prompt.length > MAX_PROMPT_LENGTH) {
      return jsonResponse({ success: false, error: `prompt must be ${MAX_PROMPT_LENGTH} characters or fewer` }, 413, env);
    }

    try {
      const result = await env.AI.run(env.MODEL || DEFAULT_MODEL, {
        messages: [
          {
            role: "system",
            content: "You are a four-character automotive mechanic crew. Answer as exactly four short spoken lines, one per character, in this order and format: Pat: ...; Adam: ...; Steve: ...; Jesse: ... . Put each character on its own line. Pat is the lead: blunt, confident, rough biker-manager, occasional mild profanity, but responsible and safety-first. Adam is laid-back, polite country, calm and practical. Steve is loud, colorful redneck shop-hand with occasional mild profanity, funny but helpful. Jesse is a scattered, energetic teenager, informal and enthusiastic, but still useful. Give each a distinct take; do not merely repeat the previous line. Keep the entire answer brief and suitable to speak aloud, about 80-140 words total. This is general information, not a confirmed diagnosis. Prioritize safety and tell the user to stop driving for unsafe conditions. Never recommend dangerous work around running engines or energized systems. Do not output markdown, headings, or any text outside the four labeled lines."
          },
          { role: "user", content: prompt }
        ],
        max_tokens: MAX_OUTPUT_TOKENS
      });
      const answer = typeof result?.response === "string" ? result.response.trim() : "";
      if (!answer) return jsonResponse({ success: false, error: "The AI returned an empty response" }, 502, env);
      return jsonResponse({ success: true, data: { response: answer } }, 200, env);
    } catch (error) {
      console.error("Workers AI request failed", error);
      return jsonResponse({ success: false, error: "Assistant unavailable. The account may have reached its free usage allowance." }, 502, env);
    }
  }
};
