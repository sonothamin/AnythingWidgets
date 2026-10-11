# Widget spec (schema 1)

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
- No scripts, no HTML, no remote images, no arbitrary intents.

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
