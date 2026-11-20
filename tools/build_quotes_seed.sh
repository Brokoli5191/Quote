#!/usr/bin/env bash
# Offline build-time script (NOT shipped in the app).
# Downloads the Abirate/english_quotes dataset and emits the Android raw seed.
#
# Rules shared with the standalone validator:
# - English quotes only
# - no quote longer than 100 characters
# - no more than five cleaned tags
# - no duplicate quote text, even if punctuation/case/author differ
# - locally sourced additions from tools/curated_quotes.json are merged in
# - tags come from tools/quote_tags.json (3-5 from tools/tag_vocabulary.json); the category is
#   frozen from the source tags first
# - texts listed in TEXT_OVERRIDES (quote_seed_rules.mjs) are replaced by their corrections, and
#   quotes by BLOCKED_AUTHORS are dropped; to apply new corrections without downloading the
#   dataset again, run tools/apply_text_overrides.mjs
#
# Usage: bash tools/build_quotes_seed.sh
# Requires: curl, node.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/res/raw/quotes_seed.json"
SRC_URL="https://huggingface.co/datasets/Abirate/english_quotes/resolve/main/quotes.jsonl"
CURATED="$SCRIPT_DIR/curated_quotes.json"
QUOTE_TAGS="$SCRIPT_DIR/quote_tags.json"
BUILDER="$SCRIPT_DIR/rebuild_quotes_seed.mjs"
VALIDATOR="$SCRIPT_DIR/validate_quotes_seed.mjs"
TMP_JSONL="$(mktemp)"
TMP_OUT="$(mktemp "$OUT.XXXXXX")"
trap 'rm -f "$TMP_JSONL" "$TMP_OUT"' EXIT

echo "Downloading $SRC_URL ..."
curl -fsSL -m 120 -o "$TMP_JSONL" "$SRC_URL"
echo "Downloaded $(wc -l < "$TMP_JSONL") lines."

# Build and validate a temp file first so a failed run never leaves a broken seed behind.
node "$BUILDER" "$TMP_JSONL" "$TMP_OUT" "$CURATED" "$QUOTE_TAGS"
node "$VALIDATOR" "$TMP_OUT"
chmod 644 "$TMP_OUT"
mv "$TMP_OUT" "$OUT"
echo "Done."
