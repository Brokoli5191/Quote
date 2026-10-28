const WINDOWS_1252 = new Map([
  ['€', 0x80], ['‚', 0x82], ['ƒ', 0x83], ['„', 0x84], ['…', 0x85],
  ['†', 0x86], ['‡', 0x87], ['ˆ', 0x88], ['‰', 0x89], ['Š', 0x8a],
  ['‹', 0x8b], ['Œ', 0x8c], ['Ž', 0x8e], ['‘', 0x91], ['’', 0x92],
  ['“', 0x93], ['”', 0x94], ['•', 0x95], ['–', 0x96], ['—', 0x97],
  ['˜', 0x98], ['™', 0x99], ['š', 0x9a], ['›', 0x9b], ['œ', 0x9c],
  ['ž', 0x9e], ['Ÿ', 0x9f],
]);

const ENGLISH_MARKERS = new Set(`
  a about above after again against all am an and any are as at away be because been
  before being below between both but by can cannot could did do does doing down during
  each even ever every few for from further get give go had has have having he her here
  hers herself him himself his how i if in into is it its itself just know let life like
  made make many may me more most must my myself never no nor not now of off on once only
  or other our ours ourselves out over own people same see she should so some such than
  that the their theirs them themselves then there these they thing think this those through
  time to too under until up upon us very want was we were what when where which while who
  whom why will with without work world would you your yours yourself yourselves
`.trim().split(/\s+/));

const ENGLISH_CONTENT_WORDS = new Set(`
  act adventure baby believe blubber brave change courage create curiosity dream dreams
  education failure faith fear foolish freedom future happy happiness hope hungry impossible
  knowledge later laters learn learning love nitwit obey oddment optimism progress resist
  science stars stay strength success truth tweak wisdom
`.trim().split(/\s+/));

const FOREIGN_MARKERS = new Set(`
  aber als auch auf aus avec bei cela cette dans das dass dein deine der des die du el
  ella en eres es esta et für haben ich il ist la las le les los mais mit nicht nous oder
  para pas por que qui sein sind sobre son su sur una uno un und une vous yo
`.trim().split(/\s+/));

const NON_LATIN_LETTER = /[\p{Script=Arabic}\p{Script=Armenian}\p{Script=Bengali}\p{Script=Cyrillic}\p{Script=Devanagari}\p{Script=Georgian}\p{Script=Greek}\p{Script=Han}\p{Script=Hangul}\p{Script=Hebrew}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Thai}]/u;

export const MAX_TAGS = 5;
export const MAX_QUOTE_LENGTH = 100;

export function repairMojibake(value) {
  let normalized = String(value || '')
    .replace(/Ã¢â‚¬â„¢/g, '’').replace(/Ã¢â‚¬Ëœ/g, '‘')
    .replace(/Ã¢â‚¬Å“/g, '“').replace(/Ã¢â‚¬ï¿½/g, '”')
    .replace(/Ã¢â‚¬â€œ/g, '–').replace(/Ã¢â‚¬â€/g, '—')
    .replace(/Ã¢â‚¬Â¦/g, '…').replace(/Ã¢â‚¬Â²/g, '′')
    .replace(/Ã¢â‚¬/g, '—')
    // E2 80 9D (”) loses its last byte as Windows-1252 has no 0x9D, leaving a replacement char.
    .replace(/â€\uFFFD/g, '”');

  if (!/[ÃƒÃ‚Ã¢Ã˜Ã™]/.test(normalized)) return repairMojibakeSegments(normalized);
  const bytes = [];
  for (const char of normalized) {
    const code = char.codePointAt(0);
    const byte = WINDOWS_1252.get(char) ?? (code <= 0xff ? code : null);
    if (byte === null) return repairMojibakeSegments(normalized);
    bytes.push(byte);
  }
  const repaired = Buffer.from(bytes).toString('utf8');
  return repaired.includes('\uFFFD') ? repairMojibakeSegments(normalized) : repaired;
}

const MOJIBAKE_CONTINUATION = '[\\u0080-\\u00bf' + [...WINDOWS_1252.keys()].join('') + ']';
const MOJIBAKE_SEGMENT = new RegExp(`[\\u00c2-\\u00f4]${MOJIBAKE_CONTINUATION}{1,3}`, 'g');

// Repairs UTF-8 sequences that were decoded as Windows-1252 inside otherwise intact text,
// e.g. "ideasâ€”perhaps" next to genuine curly quotes. Runs a few times for double encoding.
function repairMojibakeSegments(value) {
  let text = value;
  for (let pass = 0; pass < 3; pass++) {
    const next = text.replace(MOJIBAKE_SEGMENT, segment => {
      const bytes = [...segment].map(char => WINDOWS_1252.get(char) ?? char.codePointAt(0));
      const length = bytes[0] >= 0xf0 ? 4 : bytes[0] >= 0xe0 ? 3 : 2;
      if (bytes.length < length) return segment;
      const decoded = Buffer.from(bytes.slice(0, length)).toString('utf8');
      if (decoded.includes('\uFFFD') || [...decoded].length !== 1) return segment;
      return decoded + segment.slice(length);
    });
    if (next === text) break;
    text = next;
  }
  return text;
}

const STRAIGHT_QUOTE = '"';
const OPENING_CONTEXT = /[\s([{\u2014\u2013\-\/“„]/;

// Pairs double quotation marks. Curly („ “ ”) and straight (") marks are tracked separately, so a
// curly pair around the whole text is not confused with straight marks inside it. A straight mark
// right after a word or punctuation closes; one at the start or after a space/bracket/dash opens.
// Only the first straight mark may be an unmatched closer (text that starts mid-quote); a later
// one with nothing open is treated as an opener with a missing space before it.
export function pairQuoteMarks(text) {
  const pairs = new Map();
  const unmatchedClose = [];
  const curly = [];
  const straight = [];
  let seenStraight = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === '„' || (char === '“' && text[curly.at(-1)] !== '„')) {
      curly.push(i);
    } else if (char === '”' || char === '“') {
      if (curly.length) pairs.set(curly.pop(), i);
      else unmatchedClose.push(i);
    } else if (char === STRAIGHT_QUOTE) {
      const prev = text[i - 1];
      const next = text[i + 1];
      if (/\d/.test(prev ?? '') && straight.length === 0) continue; // inch mark, e.g. 12"
      const prevIsCloser = prev === STRAIGHT_QUOTE && !straight.includes(i - 1);
      const opensByContext = prev === undefined || OPENING_CONTEXT.test(prev) || prevIsCloser;
      const isolated = opensByContext && (next === undefined || /\s/.test(next));
      const opens = isolated || (!opensByContext && seenStraight)
        ? straight.length === 0
        : opensByContext;
      seenStraight = true;
      if (opens) straight.push(i);
      else if (straight.length) pairs.set(straight.pop(), i);
      else unmatchedClose.push(i);
    }
  }
  const unmatchedOpen = [...curly, ...straight].sort((a, b) => a - b);
  return { pairs, unmatchedOpen, unmatchedClose };
}

function closingMarkFor(mark) {
  if (mark === '„') return '“';
  return mark === '“' ? '”' : STRAIGHT_QUOTE;
}

function openingMarkFor(mark) {
  return mark === STRAIGHT_QUOTE ? STRAIGHT_QUOTE : '“';
}

// Removes a quotation-mark pair only when it wraps the entire text.
function stripWrappingQuotes(text) {
  let result = text;
  while (result.length > 1 && pairQuoteMarks(result).pairs.get(0) === result.length - 1) {
    result = result.slice(1, -1).trim();
  }
  return result;
}

// Closes unmatched opening marks at the end and opens unmatched closing marks at the start.
export function balanceQuoteMarks(text) {
  const { unmatchedOpen, unmatchedClose } = pairQuoteMarks(text);
  if (unmatchedOpen.length === 0 && unmatchedClose.length === 0) return text;
  const prefix = unmatchedClose.map(index => openingMarkFor(text[index])).reverse().join('');
  const suffix = unmatchedOpen.map(index => closingMarkFor(text[index])).reverse().join('');
  return prefix + text + suffix;
}

export function cleanText(value) {
  const text = repairMojibake(value)
    .replace(/\uFFFD/g, '')
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/([.!?,”])(?=“)/g, '$1 ')
    .trim();
  return spaceAfterClosingQuotes(stripWrappingQuotes(balanceQuoteMarks(stripWrappingQuotes(text))));
}

// "Hi?"She said → "Hi?" She said: only after closing straight marks that end a sentence or clause.
function spaceAfterClosingQuotes(text) {
  const { pairs, unmatchedClose } = pairQuoteMarks(text);
  const closers = new Set([...pairs.values(), ...unmatchedClose]);
  let result = '';
  for (let i = 0; i < text.length; i++) {
    result += text[i];
    if (
      text[i] === STRAIGHT_QUOTE && closers.has(i) && /[.!?,]/.test(text[i - 1] ?? '') &&
      /[\p{L}"]/u.test(text[i + 1] ?? '')
    ) result += ' ';
  }
  return result;
}

export function cleanAuthor(value) {
  return cleanText(value).replace(/,+$/, '').trim();
}

export function quoteKey(value) {
  return cleanText(value)
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[\p{M}\p{P}\p{S}]+/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function isProbablyEnglish(value) {
  const text = cleanText(value);
  if (!text || text.includes('\uFFFD') || NON_LATIN_LETTER.test(text)) return false;

  const words = (text.toLowerCase().match(/[a-z]+(?:['’][a-z]+)?/g) || [])
    .map(word => word.replace('’', "'"));
  if (words.length === 0) return false;

  const englishHits = words.filter(word =>
    ENGLISH_MARKERS.has(word) ||
    ENGLISH_CONTENT_WORDS.has(word) ||
    /(?:n't|'re|'ve|'ll|'d|'m|'s)$/.test(word)
  ).length;
  const foreignHits = words.filter(word => FOREIGN_MARKERS.has(word)).length;

  if (foreignHits >= 2 && foreignHits > englishHits) return false;
  if (englishHits > 0) return true;
  return words.length <= 3 && words.some(word => ENGLISH_CONTENT_WORDS.has(word));
}

export function cleanTags(rawTags, author) {
  const authorTokens = new Set(
    author.toLowerCase().split(/[^a-z0-9]+/).filter(token => token.length > 1)
  );
  const seen = new Set();
  const tags = [];
  for (const rawTag of Array.isArray(rawTags) ? rawTags : []) {
    const tag = String(rawTag).toLowerCase().trim();
    if (
      !tag ||
      seen.has(tag) ||
      tag.startsWith('misattributed') ||
      tag === 'attributed-no-source' ||
      authorTokens.has(tag)
    ) continue;
    seen.add(tag);
    tags.push(tag);
    if (tags.length === MAX_TAGS) break;
  }
  return tags;
}

export function buildSeed(records) {
  const seenQuotes = new Set();
  const quotes = [];

  for (const record of records) {
    const quote = cleanText(record.quote);
    const author = cleanAuthor(record.author);
    if (!quote || !author || quote.length > MAX_QUOTE_LENGTH || !isProbablyEnglish(quote)) continue;

    const key = quoteKey(quote);
    if (!key || seenQuotes.has(key)) continue;
    seenQuotes.add(key);
    quotes.push({ quote, author, tags: cleanTags(record.tags, author) });
  }
  return quotes;
}
