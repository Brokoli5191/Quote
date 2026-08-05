#!/usr/bin/env bash
# Offline build-time script (NOT shipped in the app).
# Downloads the public-domain-ish Abirate/english_quotes dataset (Goodreads,
# ~2508 tagged quotes) and emits app/src/main/res/raw/quotes_seed.json in the
# shape the seeder expects: [{"quote": "...", "author": "...", "tags": [...]}].
#
# Cleans smart quotes, drops empty rows and quotes longer than 90 chars, dedups
# on (text, author), and strips noise tags (misattributed-*, attributed-no-source,
# author-name tokens).
#
# Usage: bash tools/build_quotes_seed.sh
# Requires: curl, node.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/res/raw/quotes_seed.json"
SRC_URL="https://huggingface.co/datasets/Abirate/english_quotes/resolve/main/quotes.jsonl"
TMP_JSONL="$(mktemp)"
TMP_JS="$(mktemp --suffix=.js)"
trap 'rm -f "$TMP_JSONL" "$TMP_JS"' EXIT

echo "Downloading $SRC_URL ..."
curl -sSL -m 120 -o "$TMP_JSONL" "$SRC_URL"
echo "Downloaded $(wc -l < "$TMP_JSONL") lines."

cat > "$TMP_JS" <<'NODE'
const fs = require('fs');
const [,, inPath, outPath] = process.argv;
const lines = fs.readFileSync(inPath, 'utf8').split('\n').filter(l => l.trim());

const clean = s => (s || '')
  .replace(/[“”„″"]/g, '')  // smart + straight double quotes
  .replace(/\s+/g, ' ')
  .trim();

const cleanAuthor = value => {
  let author = clean(value);
  const knownMojibake = new Map([
    ['Ø£Ø­ÙØ§Ù… Ù…Ø³ØªØºØ§Ù†Ù…ÙŠ', 'Ahlam Mosteghanemi'],
    ['Ø£Ø­Ù…Ø¯ Ø®Ø§ÙØ¯ ØªÙˆÙ�ÙŠÙ‚', 'Ahmed Khaled Towfik'],
  ]);
  if (knownMojibake.has(author)) return knownMojibake.get(author);
  // The source occasionally contains UTF-8 decoded as Latin-1 (for example MiÃ©ville).
  if (/[ÃÂØÙ]/.test(author)) {
    const repaired = Buffer.from(author, 'latin1').toString('utf8');
    if (!repaired.includes('\uFFFD')) author = repaired;
  }
  return author.replace(/,+$/, '').trim();
};

const seen = new Set();
const out = [];
for (const line of lines) {
  let o;
  try { o = JSON.parse(line); } catch { continue; }
  const text = clean(o.quote);
  const author = cleanAuthor(o.author);
  if (!text || !author) continue;
  if (text.length > 90) continue; // keep quotes short enough to look good on the Daily screen
  const key = (text + '|' + author).toLowerCase();
  if (seen.has(key)) continue;
  seen.add(key);

  const authorTokens = new Set(
    author.toLowerCase().split(/[^a-z0-9]+/).filter(t => t.length > 1)
  );
  const tags = Array.isArray(o.tags) ? o.tags
    .map(t => String(t).toLowerCase().trim())
    .filter(t =>
      t &&
      !t.startsWith('misattributed') &&
      t !== 'attributed-no-source' &&
      !authorTokens.has(t)
    ) : [];

  out.push({ quote: text, author, tags });
}

fs.writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
console.error(`Wrote ${out.length} quotes to ${outPath}`);
NODE

node "$TMP_JS" "$TMP_JSONL" "$OUT"
echo "Done."
