#!/usr/bin/env bash
# Regenerates index.json (with SHA-256 checksums) from widgets/*.json. Run before every push to the registry.
set -euo pipefail
cd "$(dirname "$0")"
python3 check.py
python3 - <<'PY'
import hashlib, json, glob
entries = []
for path in sorted(glob.glob("widgets/*.json")):
    raw = open(path, "rb").read(); d = json.loads(raw)
    entries.append({k: d[k] for k in ("id", "name", "author", "description", "version", "created", "categories", "tags") if k in d} |
                   {"spec": path, "sha256": hashlib.sha256(raw).hexdigest()})
json.dump({"schema": 1, "widgets": entries}, open("index.json", "w"), indent=2)
open("index.json", "a").write("\n")
PY
echo "index.json updated"
