# AI widget generator

Optional tab (Settings > AI > Show AI tab) that turns a description into a widget spec.

- **Providers:** OpenAI, Gemini (OpenAI-compatible endpoint), Claude (Messages API), Ollama and any other
  OpenAI-compatible server (`/chat/completions`). Presets only prefill address and model.
- **Key manager:** Settings > AI keys. The list order is the priority; a request goes to the first enabled key
  and falls through to the next on any failure (network, HTTP error, or an answer that isn't a valid spec).
- **Storage:** keys are encrypted with an Android Keystore key and never leave the device except to the address
  configured for that key. Plain `http://` is only accepted for Ollama/Custom entries (LAN servers).
- **Safety:** output goes through the same `SpecCodec.parseSpec` validation as any downloaded widget. It is data
  only. Installed AI widgets use source `ai` and are not touched by registry sync.
- **JSON mode:** per-key toggle for OpenAI-compatible servers (`response_format: json_object`); turn it off if
  a server rejects it.
