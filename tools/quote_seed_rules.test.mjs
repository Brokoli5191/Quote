import assert from 'node:assert/strict';
import test from 'node:test';
import { buildSeed, isProbablyEnglish, MAX_TAGS } from './quote_seed_rules.mjs';

test('language check accepts short English quotes and rejects common non-English examples', () => {
  assert.equal(isProbablyEnglish('Stay hungry. Stay foolish.'), true);
  assert.equal(isProbablyEnglish('Resist much, obey little.'), true);
  assert.equal(isProbablyEnglish('Das Leben ist schön.'), false);
  assert.equal(isProbablyEnglish('La vie est belle.'), false);
  assert.equal(isProbablyEnglish('La vida es bella.'), false);
  assert.equal(isProbablyEnglish('أجمل حب هو الذي نعثر عليه أثناء بحثنا عن شيء آخر'), false);
});

test('seed builder caps tags and deduplicates quote text across authors', () => {
  const seed = buildSeed([
    { quote: 'The only thing we have to fear is fear itself.', author: 'First', tags: ['one', 'two', 'three', 'four', 'five', 'six'] },
    { quote: 'The only thing we have to fear is fear itself!', author: 'Second', tags: ['duplicate'] },
  ]);

  assert.equal(seed.length, 1);
  assert.equal(seed[0].tags.length, MAX_TAGS);
  assert.equal(seed[0].author, 'First');
});
