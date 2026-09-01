#!/usr/bin/env bash
# Offline build-time script (NOT shipped in the app).
# Downloads the Abirate/english_quotes dataset and emits the Android raw seed.
#
# Rules shared with the standalone validator:
# - English quotes only
# - no quote longer than 90 characters
# - no more than five cleaned tags
# - no duplicate quote text, even if punctuation/case/author differ
# - locally sourced additions from tools/curated_quotes.json are merged in
#
# Usage: bash tools/build_quotes_seed.sh
# Requires: curl, node.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/res/raw/quotes_seed.json"
SRC_URL="https://huggingface.co/datasets/Abirate/english_quotes/resolve/main/quotes.jsonl"
CURATED="$SCRIPT_DIR/curated_quotes.json"
BUILDER="$SCRIPT_DIR/rebuild_quotes_seed.mjs"
VALIDATOR="$SCRIPT_DIR/validate_quotes_seed.mjs"
TMP_JSONL="$(mktemp)"
trap 'rm -f "$TMP_JSONL"' EXIT

echo "Downloading $SRC_URL ..."
curl -sSL -m 120 -o "$TMP_JSONL" "$SRC_URL"
echo "Downloaded $(wc -l < "$TMP_JSONL") lines."

node "$BUILDER" "$TMP_JSONL" "$OUT" "$CURATED"
node "$VALIDATOR" "$OUT"
echo "Done."
