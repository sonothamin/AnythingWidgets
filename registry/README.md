# AnythingWidgets registry

Starter widgets plus the `index.json` the app reads. Host this folder over HTTPS (it must contain `index.json` at its root), or zip it and use **Settings -> Import widgets**.

```
index.json
widgets/<id>.json
```

After adding or editing a widget, run `./publish.sh` to regenerate `index.json` (it recomputes the SHA-256 checksums). Bump a widget's `version` when you change it so installed copies update.

Format reference: [`docs/WIDGET_SPEC.md`](../docs/WIDGET_SPEC.md).
