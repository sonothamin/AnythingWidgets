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
- **Refactor an installed widget:** the widget's details page has a "Refactor with AI" row (hidden when the AI tab
  is off). It opens the AI tab with the installed spec as the "Current" version; each request is made on top of
  whichever version is showing, and the earlier ones stay available as v1, v2... Nothing is written until the
  person taps Save. Saving keeps the widget's id (so copies on the home screen update), bumps `version`, and
  marks it source `ai`, which detaches a registry widget from registry updates. Starting a refactor while another
  unsaved draft exists asks before replacing it. Params, mini-app state and appearance choices are keyed by id and
  carry over.
- **Prompt size:** the system prompt is built per request from a small core (about 1.3k tokens) plus only the
  sections the request needs: tap actions, conditions, widget settings (`params`), mini-app `state`, online
  `feeds`. When editing a widget, the sections its current spec already uses are included. If a provider rejects
  a request as too long (HTTP 413, context length, token limit), the same key is retried once with a minimal
  prompt (about 0.8k tokens) before falling through to the next key. Keep new spec features out of the core
  and put them in a section in `AiPrompt.kt`.

