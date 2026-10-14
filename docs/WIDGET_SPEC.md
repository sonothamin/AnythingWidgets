# Widget spec (schema 2)

Schema 2 adds options, conditions, mini-app state, more actions and optional online data. Schema 1 specs stay valid. Everything is still data: nothing in a spec is ever executed. Worked examples are in [`docs/examples/`](examples/).

Widgets are **data, not code**. The app downloads a JSON spec from the registry, validates it, and renders it with Jetpack Glance. Nothing from the registry is ever executed.

## Registry layout
```
index.json
widgets/<id>.json
```
`index.json` lists each widget: `id, name, version, spec (relative path), sha256, author?, description?, created?, categories?, tags?`.
Run `registry/publish.sh` to regenerate it (it computes the checksums). Bump `version` in a spec whenever you change it; installed copies update automatically (hourly, and when added).

The registry URL is `REGISTRY_URL` in `app/build.gradle.kts` (must be HTTPS and end with `/`).

## Spec
`schema, id, version, name, description?, author?, created?, categories?, tags?, root, compact?`
- `id`: `[a-z0-9][a-z0-9._-]{0,63}`
- `created`: optional creation date, `yyyy-MM-dd`.
- `categories`: optional, up to 5 labels. `tags`: optional, up to 10 labels. Each label is 1-24 characters.
- These optional fields (plus `author` and `description`) feed the widget details screen. Older apps ignore them.
- `compact`: optional layout used when the widget is narrower than ~180dp.

## Node
`type`: `row | column | box | text | spacer | progress`

| Field | Applies to | Notes |
|---|---|---|
| `children` | row, column, box | |
| `padding`, `gap`, `radius` | all (`gap` row/column) | dp, 0–48 (gap 0–32) |
| `fill` | all | `width`, `height`, `both` |
| `width`, `height` | all | dp, 1–400 |
| `grow` | children of row/column | takes remaining space |
| `justify` | row, column | main axis: `start`, `center`, `end` |
| `align` | row, column, box | cross axis; box: `top`, `center`, `bottom-end`, … |
| `background`, `color`, `track` | all | token or hex |
| `text`, `size`, `bold`, `maxLines`, `textAlign` | text | `size` 8–96 sp |
| `progress` | progress | template resolving to 0..1 |
| `action` | all | `open_app`, `open_url` (https), `refresh` |

**Colors**: Material tokens (`primary`, `onSurface`, `surfaceVariant`, `secondaryContainer`, …) follow the user's dynamic theme and light/dark mode. Prefer tokens; hex is `#RRGGBB` or `#AARRGGBB`.

## Bindings
Use `{{key}}` inside `text` and `progress`:
`date.weekday`, `date.weekday.short`, `date.day`, `date.month`, `date.month.long`, `date.year`, `date.dayOfYear`, `date.daysInYear`, `date.year.fraction`, `date.year.percent`, `time.short`, `battery.level`, `battery.fraction`.

Widgets can't tick every minute: Android limits background updates, so values refresh on the hourly sync, when the widget is added, and on a `refresh` tap. Avoid designs that depend on a live clock.

## Limits (enforced by the validator)
≤100 nodes, depth ≤10, text ≤200 chars, files ≤256 KB, https only. Unknown node types or actions reject the whole spec.

## Security model
- HTTPS only; each spec is verified against the SHA-256 in `index.json` before install.
- Specs are re-validated every time they're loaded from disk.
- No scripts, no HTML, no remote images. Taps can only trigger the fixed list of actions below, never an arbitrary intent.
- Online data (`feeds`) is off until the user allows it per widget, https only, public hosts only, and the URL can vary only through the widget's own `params`.

## Importing from a zip (development and bring-your-own widgets)
Settings -> Import widgets -> Choose zip. The zip uses the same layout as a registry:
```
index.json
widgets/<id>.json
```
Zipping the registry folder itself (so everything sits in one wrapper folder) also works. Only `.json` files are read.
- `index.json` is used as the list of spec paths; checksums are ignored (you chose the file). If it's missing, every `widgets/*.json` is imported.
- Every spec still passes full validation; invalid ones are skipped and reported.
- Same-id widgets are replaced, whatever their version. Imported widgets are never auto-updated from a registry.
- Limits: 300 files, 256 KB per file, 8 MB total, no `..` or absolute paths.

## Widget details screen
Tapping a widget in Widgets or Explore opens its details: a live preview (Small / Wide / Large, using this device's data), title, author, description, created and added dates, version, file size, categories, tags, which device data it reads, what tapping it does, and where it came from.
Widgets that aren't installed yet are previewed from a verified download; nothing is stored until you tap Install. Once installed, the screen also offers per-widget settings: automatic updates on/off, add to home screen, refresh, and remove.

## Schema 2

New top-level fields (all optional): `params`, `state`, `feeds`. New node fields: `visible`, `bind`. Use `"schema": 2`.

### Parameters: options the user edits
Shown in the widget's details screen under **Widget settings**. Values are stored per widget on the device, validated on every read, and read in layouts as `{{param.<id>}}`. Ids match `[a-z][A-Za-z0-9]{0,23}`; at most 12 params.

```json
"params": [
  {"id": "accent", "type": "color",  "label": "Accent", "default": "n.red"},
  {"id": "goal",   "type": "number", "label": "Goal", "default": 10, "min": 1, "max": 100, "step": 1, "unit": "reps"},
  {"id": "style",  "type": "choice", "label": "Style", "default": "dots",
   "choices": [{"value": "dots", "label": "Dots"}, {"value": "bar", "label": "Bar"}]},
  {"id": "title",  "type": "text",   "label": "Title", "default": "Hello", "maxLength": 20},
  {"id": "date",   "type": "date",   "label": "Date",  "default": "2026-12-25"},
  {"id": "seconds","type": "toggle", "label": "Show seconds", "default": false}
]
```

| type | stored as | extra keys |
|---|---|---|
| `toggle` | `true` / `false` | |
| `choice` | the choice `value` (2-16 choices, `[A-Za-z0-9_.-]{1,24}`) | `{{param.id.label}}`, `{{param.id.index}}` |
| `text` | string up to `maxLength` (default 60, max 80) | |
| `number` | number clamped to `min`..`max` (default 0..100) | `{{param.id.fraction}}` (0..1) |
| `color` | Material token, palette name or hex | |
| `date` | `yyyy-MM-dd` | `.days` (signed), `.daysUntil`, `.daysSince`, `.isPast`, `.isToday`, `.text` |

Params can drive a layout in three ways:
- **Anywhere text is templated**: `text`, `pool`, `progress`, `pattern`.
- **Colors**: `"color": "{{param.accent}}"` (also `background`, `track`, `accent`).
- **Numbers and bold**: `"bind": {"size": "{{param.fontSize}}", "radius": "{{param.corner}}"}`. Bindable: `size, padding, gap, radius, width, height, columns, rows, maxLines, bold`. Results are clamped to the normal limits; a value that is not a number is ignored.

### Conditions: `visible`
A node (and its children) is drawn only when its condition holds.
```
clause  = [!]key | key OP literal          OP = == != < <= > >=
visible = clause (&& clause)* (|| ...)*    max 160 chars
```
A bare key is true unless it is empty, `false`, `0`, `no` or `off`. Numbers compare numerically, anything else as text. Examples: `"param.seconds"`, `"!battery.charging"`, `"battery.level<20"`, `"feed.weather.ok && net.allowed"`. Use two sibling nodes with opposite conditions for if/else.

### State and actions: mini apps
`state` is data the widget changes itself when tapped, stored per widget on the device. Read it as `{{state.<id>}}`. At most 12 values.

```json
"state": [
  {"id": "count", "type": "number", "default": 0, "min": 0, "max": 99, "wrap": false},
  {"id": "done",  "type": "bool",   "default": false},
  {"id": "mood",  "type": "choice", "values": ["good", "ok", "bad"], "default": "ok"}
]
```
Extra keys: bool `{{state.id.bit}}` (`#` or `.`, ideal for `dotgrid` patterns), number `{{state.id.fraction}}` (with `min` and `max`), choice `{{state.id.index}}`. Add `"daily": true` to a state value and it goes back to its default on the next calendar day (for example an answer that was revealed).

Put an `action` on any node:

| action | does |
|---|---|
| `inc` / `dec` `{target, by?}` | change a number state (`wrap` loops between `min` and `max`) |
| `toggle {target}` | flip a bool state |
| `cycle {target}` | next value of a choice state |
| `set {target, value}` | store a fixed value |
| `reset {target?}` | back to the default (all state when no target) |

These redraw the widget immediately, and the preview in the app is playable.

### More actions (integrations)
`open_app`, `refresh`, `open_url {url}` (https), plus:

| action | fields | notes |
|---|---|---|
| `launch_app` | `package` | opens an installed app by package name; does nothing if it is not installed |
| `dial` | `number` | opens the dialer with the number filled in; may use `{{param.x}}` |
| `email` | `to`, `subject?` | opens a draft; may use `{{param.x}}` |
| `map` | `query` | opens a map search; may use `{{param.x}}` |
| `settings` | `screen` | `wifi bluetooth display battery sound location airplane storage apps date accessibility all` |
| `timer` | `seconds` (1-86400) | starts a timer in the system clock app |
| `alarms` | | opens the alarm list |

Nothing is called without the user tapping, and the system app always asks for the final confirmation (calls are only dialed, mail is only drafted).

### More device data
`battery.charging`, `date.iso`, `date.weekend`, `time.hour24`, `device.model`, `device.brand`, `device.android`, `network.type` (`WI-FI`, `MOBILE`, `ETHERNET`, `ONLINE`, `OFFLINE`), `network.online`, `storage.used.fraction`, `storage.used.percent`, `storage.free.gb`, `storage.total.gb`. `spec.id` and `net.allowed` are always present.

### Feeds: online data
A feed is a read-only JSON endpoint. It does nothing until the user turns on **Allow internet access** for that widget (the screen shows the host). At most 4 feeds.

```json
"feeds": [{
  "id": "weather", "ttl": 30,
  "url": "https://api.open-meteo.com/v1/forecast?latitude={{param.lat}}&longitude={{param.lon}}&current=temperature_2m",
  "fields": {"temp": "current.temperature_2m"}
}]
```
- `url`: https, a public hostname (no IPs, ports, credentials, `.local`/`.internal`), at most 400 chars. Only declared `{{param.x}}` values may appear, never in the host; they are URL-encoded.
- `fields`: name to dotted path, with `[0]` for array items (`daily.temperature_2m_max[0]`). Values are read as `{{feed.<id>.<name>}}`; numeric ones also get `.round`.
- `ttl`: minutes between refreshes, 15-1440 (up to 43200 for a list feed). Refreshes happen on the hourly sync, when the widget redraws and the data is older than `ttl`, when an option changes, and on a `refresh` tap.
- Always present: `{{feed.id.ok}}` (data exists), `{{feed.id.stale}}`, `{{feed.id.updated}}` (time of last fetch), plus `{{net.allowed}}` so a layout can ask the user to enable access. Failed fetches keep the last good data.
- Responses are capped at 256 KB, and no cookies or custom headers are sent.
- Cached data belongs to the URL it was fetched with: change a `{{param.x}}` and the old cache is ignored until the new fetch succeeds.
- A query pair in `url` whose value comes out empty or as `any` is left out of the request, so a `choice` param can offer "any" as a no-preference option.
- Every text field also gets `{{feed.id.name.short}}` (the part after a colon: `Entertainment: Music` becomes `Music`) and `{{feed.id.name.key}}` (that part in lowercase with dashes, up to 32 characters: `music`), handy in `visible` conditions such as `feed.fact.category.key==music`.
- HTML entities in text values (`&quot;`, `&#039;`) are decoded; tags are never interpreted.

#### List feeds: cache a batch, show one a day
Add `list` (dotted path to a JSON array) and optionally `keep` (1-50, default 30). `fields` are then read from each array element, up to `keep` elements are stored on the device, and the widget shows the next one each day (wrapping around). It refetches when `ttl` passes or the batch has been used up, and keeps working offline from the cache.
```json
"feeds": [{
  "id": "fact", "ttl": 43200, "list": "results", "keep": 30,
  "url": "https://opentdb.com/api.php?amount=30&type=multiple&category={{param.genre}}&difficulty={{param.difficulty}}",
  "fields": {"question": "question", "answer": "correct_answer", "category": "category"}
}]
```
Extra bindings: `{{feed.id.count}}` (items cached) and `{{feed.id.index}}` (1-based position of today's item).

### Glyph: text drawn with the app's fonts
`glyph` draws text as an image so it can use fonts Glance cannot: `font` is `dots` (dot-matrix, default), `sans`, `serif`, `oldstandard` or `mono`. It wraps at the widget width minus `inset` dp on each side (default 8) and keeps at most `maxLines` lines. Do not set `width` for wrapped text: it stretches the image to that width.
- `minSize`: shrink from `size` down to this size until the text fits `maxLines`, so short text is large and long text still fits.
- `dropcap`: 2 to 4. The first letter is drawn large in Old Standard over that many lines of the text beside it (start-aligned text only; ignored when the text starts with punctuation).

### Limits added in schema 2
At most 12 params, 12 state values, 4 feeds, 16 fields per feed, 10 `bind` entries per node. A state action must target an existing state of the right type, or the spec is rejected.
