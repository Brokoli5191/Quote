import { mapCategory } from './category_mapper.mjs';

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
const DIALOGUE_END = '.!?,”';
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
  const text = spaceBeforeOpeningQuotes(
    repairMojibake(value)
      .replace(/\uFFFD/g, '')
      .replace(/\u00a0/g, ' ')
      .replace(/\s+/g, ' '),
  ).trim();
  return spaceAfterClosingQuotes(stripWrappingQuotes(balanceQuoteMarks(stripWrappingQuotes(text))));
}

// “Why?”“Because” → “Why?” “Because”: the space goes only before a “ that opens a quote. In German
// „…“ the same mark closes, so „Real.“ keeps it next to the text, and the space that earlier
// versions put there („Real. “) is taken out again.
function spaceBeforeOpeningQuotes(text) {
  if (!text.includes('“')) return text;
  const closers = new Set(pairQuoteMarks(text).pairs.values());
  const result = [];
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === '“') {
      const last = result.at(-1);
      if (!closers.has(i)) {
        if (last !== undefined && DIALOGUE_END.includes(last)) result.push(' ');
      } else if (last === ' ' && result.length >= 2 && DIALOGUE_END.includes(result.at(-2))) {
        result.pop();
      }
    }
    result.push(char);
  }
  return result.join('');
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

// Source spelling (after the generic fixes in canonicalAuthor) -> display name. Covers typos,
// inverted names, missing diacritics, full-name variants and joined co-author credits.
export const AUTHOR_ALIASES = new Map([
  ['Alexandre Dumas-fils', 'Alexandre Dumas fils'],
  ['Alfred Lord Tennyson', 'Alfred, Lord Tennyson'],
  ['Alfred Tennyson', 'Alfred, Lord Tennyson'],
  ['Anais Nin', 'Anaïs Nin'],
  ['Andre Gide', 'André Gide'],
  ['Anne Bronte', 'Anne Brontë'],
  ['Arthur William Edgar O\'Shaughnessy', 'Arthur O\'Shaughnessy'],
  ['Carl Gustav Jung', 'Carl Jung'],
  ['Cathy Guiswite', 'Cathy Guisewite'],
  ['Charlotte Bronte', 'Charlotte Brontë'],
  ['Confucious', 'Confucius'],
  ['Corrie Ten Boom', 'Corrie ten Boom'],
  ['Desiderius Erasmus Roterodamus', 'Desiderius Erasmus'],
  ['Edgar Allen Poe', 'Edgar Allan Poe'],
  ['Francois Mauriac', 'François Mauriac'],
  ['Friedrich Wilhelm Nietzsche', 'Friedrich Nietzsche'],
  ['Gabriel Garcia Marquez', 'Gabriel García Márquez'],
  ['Haruki Marukami', 'Haruki Murakami'],
  ['Hemingway, Ernest', 'Ernest Hemingway'],
  ['Henry Ward Beecherr', 'Henry Ward Beecher'],
  ['Herman Hesse', 'Hermann Hesse'],
  ['Honore de Balzac', 'Honoré de Balzac'],
  ['J.K. rowling', 'J.K. Rowling'],
  ['JK Rowling', 'J.K. Rowling'],
  ['Lance Armstrong Sally Jenkins', 'Lance Armstrong and Sally Jenkins'],
  ['Lao Tsu', 'Lao Tzu'],
  ['Lewis Caroll', 'Lewis Carroll'],
  ['Lucius Annaeus Seneca', 'Seneca'],
  ['Marcus Tullius Cicero', 'Cicero'],
  ['Markus Herz', 'Marcus Herz'],
  ['Mary Wollstonecraft Shelley', 'Mary Shelley'],
  ['Mawlana Jalal-al-Din Rumi', 'Rumi'],
  ['Moliere', 'Molière'],
  ['Mother Theresa', 'Mother Teresa'],
  ['Nikolai V. Gogol', 'Nikolai Gogol'],
  ['Oscar Wilde (attributed to)', 'Oscar Wilde'],
  ['P.C. Cast Kristin Cast', 'P.C. Cast and Kristin Cast'],
  ['Pedro Calderon de la Barca', 'Pedro Calderón de la Barca'],
  ['Robert Marley', 'Bob Marley'],
  ['Rumi Jalalud-Din', 'Rumi'],
  ['Salvador Dali', 'Salvador Dalí'],
  ['Sir Francis Bacon', 'Francis Bacon'],
  ['St. Francis Of Assisi', 'St. Francis of Assisi'],
  ['Thomas A. Edison', 'Thomas Edison'],
  ['Tolstoy Leo', 'Leo Tolstoy'],
  ['Victor hugo', 'Victor Hugo'],
  ['Vincent Willem van Gogh', 'Vincent van Gogh'],
  ['Viktor E. Frankl', 'Viktor Frankl'],
  ['William Shakespear', 'William Shakespeare'],
  ['Winston S. Churchill', 'Winston Churchill'],
]);

// Generic spelling fixes first ("douglas adams", "J. K. Rowling", "J.K.Rowling",
// "Martin Luther King, Jr."), then the alias table.
export function canonicalAuthor(value) {
  let name = String(value || '').replace(/\s+/g, ' ').trim();
  if (name === name.toLowerCase()) {
    name = name.replace(/(^|[\s.-])(\p{Ll})/gu, (match, before, letter) => before + letter.toUpperCase());
  }
  name = name
    .replace(/(?<=(?:^|[\s.])\p{Lu})\.\s+(?=\p{Lu}\.)/gu, '.')
    .replace(/(\b\p{Lu}\.)(?=\p{Lu}\p{Ll})/gu, '$1 ')
    .replace(/,\s*(Jr|Sr)\.?$/u, ' $1.');
  return AUTHOR_ALIASES.get(name) ?? name;
}

// Key under which spelling variants of one author collide (case, diacritics, punctuation, spacing).
export function authorKey(value) {
  return String(value || '')
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '');
}

export function cleanAuthor(value) {
  return canonicalAuthor(cleanText(value).replace(/,+$/, '').trim());
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

// Authors whose quotes are never shipped, even if the source dataset carries them.
export const BLOCKED_AUTHORS = new Set(['Adolf Hitler']);

// Hand corrections for source texts, keyed by the cleaned source text. The source dataset lost the
// line breaks of multi-line quotes, so verse lines and dialogue turns ran into each other
// ("bornAnd once"), and some texts lack end punctuation or carry stray marks. A correction only
// changes spacing, punctuation and letter case, never the letters themselves (checked in the
// tests), so installed apps can still match the corrected quote to the old row.
export const TEXT_OVERRIDES = new Map([
  ['Sometimes people are beautiful.Not in looks.Not in what they say.Just in what they are.', 'Sometimes people are beautiful. Not in looks. Not in what they say. Just in what they are.'],
  ['Love looks not with the eyes, but with the mind,And therefore is winged Cupid painted blind.', 'Love looks not with the eyes, but with the mind, and therefore is winged Cupid painted blind.'],
  ['If I had a flower for every time I thought of you...I could walk through my garden forever.', 'If I had a flower for every time I thought of you... I could walk through my garden forever.'],
  ['′Classic′ - a book which people praise and don\'t read.', '\'Classic\' – a book which people praise and don\'t read.'],
  ['Tis better to have loved and lostThan never to have loved at all.', '\'Tis better to have loved and lost than never to have loved at all.'],
  ['Music expresses that which cannot be put into words and that which cannot remain silent', 'Music expresses that which cannot be put into words and that which cannot remain silent.'],
  ['“Do you hate people?” “I don\'t hate them...I just feel better when they\'re not around.”', '“Do you hate people?” “I don\'t hate them... I just feel better when they\'re not around.”'],
  ['Creativity is knowing how to hide your sources', 'Creativity is knowing how to hide your sources.'],
  ['If you don\'t know where you\'re going, any road\'ll take you there', 'If you don\'t know where you\'re going, any road\'ll take you there.'],
  ['You can love someone so much...But you can never love people as much as you can miss them.', 'You can love someone so much... but you can never love people as much as you can miss them.'],
  ['I refuse to answer that question on the grounds that I don\'t know the answer', 'I refuse to answer that question on the grounds that I don\'t know the answer.'],
  ['Hold fast to dreams,For if dreams dieLife is a broken-winged bird,That cannot fly.', 'Hold fast to dreams, for if dreams die, life is a broken-winged bird that cannot fly.'],
  ['Peace begins with a smile..', 'Peace begins with a smile.'],
  ['Unless someone like you cares a whole awful lot,Nothing is going to get better. It\'s not.', 'Unless someone like you cares a whole awful lot, nothing is going to get better. It\'s not.'],
  ['I have decided to stick to love...Hate is too great a burden to bear.', 'I have decided to stick to love... Hate is too great a burden to bear.'],
  ['What\'s meant to be will always find a way', 'What\'s meant to be will always find a way.'],
  ['The mind is its own place, and in itself can make a heaven of hell, a hell of heaven..', 'The mind is its own place, and in itself can make a heaven of hell, a hell of heaven.'],
  ['Of all sad words of tongue or pen, the saddest are these, \'It might have been.', 'Of all sad words of tongue or pen, the saddest are these: \'It might have been.\''],
  ['For you, a thousand times over', 'For you, a thousand times over.'],
  ['And, in the endThe love you takeis equal to the love you make.', 'And, in the end, the love you take is equal to the love you make.'],
  ['"Wow," Thalia muttered. "Apollo is hot." "He\'s the sun god," I said."That\'s not what I meant."', '"Wow," Thalia muttered. "Apollo is hot." "He\'s the sun god," I said. "That\'s not what I meant."'],
  ['Fear doesn\'t shut you down; it wakes you up', 'Fear doesn\'t shut you down; it wakes you up.'],
  ['Maybe \'okay\' will be our \'always', 'Maybe \'okay\' will be our \'always.\''],
  ['Life is for the living.Death is for the dead.Let life be like music. And death a note unsaid.', 'Life is for the living. Death is for the dead. Let life be like music. And death a note unsaid.'],
  ['Do you think I\'m pretty?I think you\'re beautifulBeautiful?You are so beautiful, it hurts sometimes.', 'Do you think I\'m pretty? I think you\'re beautiful. Beautiful? You are so beautiful, it hurts sometimes.'],
  ['I love you. Remember. They cannot take it', 'I love you. Remember. They cannot take it.'],
  ['You pierce my soul. I am half agony, half hope...I have loved none but you.', 'You pierce my soul. I am half agony, half hope... I have loved none but you.'],
  ['but for my own part, if a book is well written, I always find it too short.', 'But for my own part, if a book is well written, I always find it too short.'],
  ['HopeSmiles from the threshold of the year to come, Whispering \'it will be happier\'...', 'Hope smiles from the threshold of the year to come, whispering \'it will be happier\'...'],
  ['What would men be without women? Scarce, sir...mighty scarce.', 'What would men be without women? Scarce, sir... mighty scarce.'],
  ['Life is to be enjoyed, not endured', 'Life is to be enjoyed, not endured.'],
  ['Let everything happen to youBeauty and terrorJust keep goingNo feeling is final', 'Let everything happen to you: beauty and terror. Just keep going. No feeling is final.'],
  ['You\'re off to Great Places!Today is your day!Your mountain is waiting,So... get on your way!', 'You\'re off to Great Places! Today is your day! Your mountain is waiting, so... get on your way!'],
  ['I wantTo do with you what spring does with the cherry trees.', 'I want to do with you what spring does with the cherry trees.'],
  ['As if you were on fire from within.The moon lives in the lining of your skin.', 'As if you were on fire from within. The moon lives in the lining of your skin.'],
  ['You see things; you say, \'Why?\' But I dream things that never were; and I say \'Why not?', 'You see things; you say, \'Why?\' But I dream things that never were; and I say, \'Why not?\''],
  ['‎Though nobody can go back and make a new beginning... Anyone can start over and make a new ending.', 'Though nobody can go back and make a new beginning... anyone can start over and make a new ending.'],
  ['Tonight I can write the saddest linesI loved her, and sometimes she loved me too.', 'Tonight I can write the saddest lines: I loved her, and sometimes she loved me too.'],
  ['Success is getting what you want, happiness is wanting what you get', 'Success is getting what you want, happiness is wanting what you get.'],
  ['"How do you spell \'love\'?" - Piglet"You don\'t spell it...you feel it." - Pooh', '"How do you spell \'love\'?" – Piglet. "You don\'t spell it... you feel it." – Pooh.'],
  ['‎Life is a shipwreck, but we must not forget to sing in the lifeboats.', 'Life is a shipwreck, but we must not forget to sing in the lifeboats.'],
  ['Happiness [is] only real when shared', 'Happiness [is] only real when shared.'],
  ['you can, you should, and if you’re brave enough to start, you will.', 'You can, you should, and if you’re brave enough to start, you will.'],
  ['"Was it hard?" I ask.Letting go?"Not as hard as holding on to something that wasn\'t real."', '"Was it hard?" I ask. "Letting go?" "Not as hard as holding on to something that wasn\'t real."'],
  ['“Stay gold, Ponyboy. Stay gold . . .” The pillow seemed to sink a little, and Johnny died.', '“Stay gold, Ponyboy. Stay gold...” The pillow seemed to sink a little, and Johnny died.'],
  ['love the life you live.live the life you love.', 'Love the life you live. Live the life you love.'],
  ['Knowledge speaks, but wisdom listens', 'Knowledge speaks, but wisdom listens.'],
  ['A truth that\'s told with bad intentBeats all the lies you can invent.', 'A truth that\'s told with bad intent beats all the lies you can invent.'],
  ['Do I contradict myself?Very well then I contradict myself,(I am large, I contain multitudes.)', 'Do I contradict myself? Very well then I contradict myself, (I am large, I contain multitudes.)'],
  ['being alone never felt right. sometimes it felt good, but it never felt right.', 'Being alone never felt right. Sometimes it felt good, but it never felt right.'],
  ['I cannot teach anybody anything. I can only make them think', 'I cannot teach anybody anything. I can only make them think.'],
  ['You yourself, as much as anybody in the entire universe, deserve your love and affection', 'You yourself, as much as anybody in the entire universe, deserve your love and affection.'],
  ['For whatever we lose (like a you or a me),It\'s always our self we find in the sea.', 'For whatever we lose (like a you or a me), it\'s always our self we find in the sea.'],
  ['let me live, love, and say it well in good sentences', 'Let me live, love, and say it well in good sentences.'],
  ['silence is the language of god, all else is poor translation.', 'Silence is the language of God, all else is poor translation.'],
  ['Reading brings us unknown friends', 'Reading brings us unknown friends.'],
  ['This is the way the world endsNot with a bang but a whimper.', 'This is the way the world ends: not with a bang but a whimper.'],
  ['As it has been said:Love and a coughcannot be concealed.Even a small cough.Even a small love.', 'As it has been said: love and a cough cannot be concealed. Even a small cough. Even a small love.'],
  ['Forgive, O Lord, my little jokes on TheeAnd I\'ll forgive Thy great big one on me.', 'Forgive, O Lord, my little jokes on Thee, and I\'ll forgive Thy great big one on me.'],
  ['Do not be afraid; our fateCannot be taken from us; it is a gift.', 'Do not be afraid; our fate cannot be taken from us; it is a gift.'],
  ['Animals are my friends...and I don\'t eat my friends.', 'Animals are my friends... and I don\'t eat my friends.'],
  ['They say time heals all wounds, but that presumes the source of the grief is finite', 'They say time heals all wounds, but that presumes the source of the grief is finite.'],
  ['One wordFrees us of all the weight and pain of life:That word is love.', 'One word frees us of all the weight and pain of life: that word is love.'],
  ['The love of learning, the sequestered nooks,And all the sweet serenity of books', 'The love of learning, the sequestered nooks, and all the sweet serenity of books.'],
  ['Some people have a way with words, and other people...oh, uh, not have way.', 'Some people have a way with words, and other people... oh, uh, not have way.'],
  ['There is a crack in everything.That\'s how the light gets in.', 'There is a crack in everything. That\'s how the light gets in.'],
  ['If you have good friends, no matter how much life is sucking , they can make you laugh.', 'If you have good friends, no matter how much life is sucking, they can make you laugh.'],
  ['The only real prison is fear, and the only real freedom is freedom from fear', 'The only real prison is fear, and the only real freedom is freedom from fear.'],
  ['The earth has its music for those who will listen', 'The earth has its music for those who will listen.'],
  ['You only live twice:Once when you are bornAnd once when you look death in the face', 'You only live twice: once when you are born, and once when you look death in the face.'],
  ['"I don\'t go out with strangers," I said."Good thing I do. I\'ll pick you up at five."', '"I don\'t go out with strangers," I said. "Good thing I do. I\'ll pick you up at five."'],
  ['Heard melodies are sweet, but those unheard, are sweeter', 'Heard melodies are sweet, but those unheard, are sweeter.'],
  ['I know how devastated you must be to miss me, but leave a message, and I\'ll try to ease your agony', 'I know how devastated you must be to miss me, but leave a message, and I\'ll try to ease your agony.'],
  ['Whoever is careless with the truth in small matters cannot be trusted with important matters', 'Whoever is careless with the truth in small matters cannot be trusted with important matters.'],
  ['All human wisdom is contained in these two words - Wait and Hope', 'All human wisdom is contained in these two words: Wait and Hope.'],
  ['Sometimes you lose a battle. But mischief always wins the war', 'Sometimes you lose a battle. But mischief always wins the war.'],
  ['Music is ... A higher revelation than all Wisdom & Philosophy', 'Music is... a higher revelation than all wisdom & philosophy.'],
  ['Sometimes it takes a good fall to really know where you stand', 'Sometimes it takes a good fall to really know where you stand.'],
  ['I don\'t hate you.. I just don\'t like that you exist', 'I don\'t hate you. I just don\'t like that you exist.'],
  ['sex is the consolation you have when you can\'t have love', 'Sex is the consolation you have when you can\'t have love.'],
  ['Whatever you can do or dream you can, begin it.Boldness has genius, power and magic in it!', 'Whatever you can do or dream you can, begin it. Boldness has genius, power and magic in it!'],
  ['Silence is so freaking loud', 'Silence is so freaking loud.'],
]);

// A correction may restore spaces and punctuation that the source dropped, so a corrected text
// may exceed MAX_QUOTE_LENGTH by this much. The length rule selects source quotes, not corrections.
export const OVERRIDE_LENGTH_SLACK = 5;

// Letters and digits only, lower case: equal for a source text and its correction.
export function compactQuoteKey(value) {
  return String(value || '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
}

// Signs that a text lost a line break or a space, or never got its end punctuation.
export function textDefects(text) {
  const defects = [];
  if (/\p{Ll}\p{Lu}/u.test(text)) defects.push('lower-case letter directly followed by an upper-case one');
  if (/\p{L}[,;:!?]\p{L}/u.test(text)) defects.push('no space after a comma, semicolon, colon or mark');
  if (/\p{Ll}\.\p{Lu}/u.test(text)) defects.push('no space after a full stop');
  if (/\p{L}\.{2,3}\p{L}/u.test(text) || /(?<!\.)\.\.(?!\.)/.test(text)) defects.push('ellipsis without a space or a doubled full stop');
  if (/\p{Ll}[.!?]["“]\p{Lu}/u.test(text)) defects.push('no space before an opening quotation mark');
  if (/[​‎‏⁠﻿]/.test(text)) defects.push('invisible formatting character');
  if (/\s[,;:!?]/.test(text)) defects.push('space before punctuation');
  if (/^\p{Ll}/u.test(text)) defects.push('starts with a lower-case letter');
  if (!/[.!?…"”’')\]]$/.test(text)) defects.push('no end punctuation');
  return defects;
}

export function buildSeed(records) {
  const seenQuotes = new Set();
  const quotes = [];

  for (const record of records) {
    const sourceQuote = cleanText(record.quote);
    const author = cleanAuthor(record.author);
    if (!sourceQuote || !author || sourceQuote.length > MAX_QUOTE_LENGTH || !isProbablyEnglish(sourceQuote)) continue;
    if (BLOCKED_AUTHORS.has(author)) continue;
    const quote = TEXT_OVERRIDES.get(sourceQuote) ?? sourceQuote;

    const key = quoteKey(quote);
    if (!key || seenQuotes.has(key)) continue;
    seenQuotes.add(key);
    quotes.push({ quote, author, tags: cleanTags(record.tags, author) });
  }
  return quotes;
}

// Replaces the cleaned source tags with the curated tags from tools/quote_tags.json. The category
// is computed from the source tags first, so curated tags never move a quote to another category.
// Quotes without curated tags keep their source tags (the validator then asks for curated ones).
export function applyQuoteTags(seed, quoteTags) {
  return seed.map(item => ({
    quote: item.quote,
    author: item.author,
    tags: quoteTags[item.quote] ?? item.tags,
    category: item.category ?? mapCategory(item.tags, item.quote),
  }));
}
