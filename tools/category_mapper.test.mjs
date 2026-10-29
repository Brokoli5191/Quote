import assert from 'node:assert/strict';
import test from 'node:test';
import { CATEGORIES, mapCategory } from './category_mapper.mjs';

test('exact category name wins in category order, not tag order', () => {
  assert.equal(mapCategory(['books', 'humor'], 'So many books, so little time.'), 'Humor');
  assert.equal(mapCategory(['Love', 'LIFE'], ''), 'Life');
  assert.equal(mapCategory(['  wisdom  '], ''), 'Wisdom');
});

test('exact synonym is used in tag order', () => {
  assert.equal(mapCategory(['romance', 'funny'], ''), 'Love');
  assert.equal(mapCategory(['funny', 'romance'], ''), 'Humor');
  assert.equal(mapCategory(['independence'], ''), 'Freedom');
});

test('synonym substring matches compound tags', () => {
  assert.equal(mapCategory(['self-love'], ''), 'Love');
  assert.equal(mapCategory(['books-reading'], ''), 'Books');
  // "inspirational-attitude" contains "inspiration" before any other synonym.
  assert.equal(mapCategory(['inspirational-attitude'], ''), 'Inspirational');
});

test('falls back to keywords in the text, then Uncategorized', () => {
  assert.equal(mapCategory(['sin'], 'Worry does not empty tomorrow of its sorrow, it empties today of its strength.'), 'Courage');
  assert.equal(mapCategory([], 'The world is a book and those who do not travel read only one page.'), 'Books');
  // "lifelong" contains "life", which precedes "love" in synonym order.
  assert.equal(mapCategory([], 'A lifelong love.'), 'Life');
  assert.equal(mapCategory(['simon'], 'I am a badass.'), 'Uncategorized');
  assert.equal(mapCategory(undefined, undefined), 'Uncategorized');
});

test('category list matches the Kotlin mapper', () => {
  assert.equal(CATEGORIES.length, 22);
  assert.equal(CATEGORIES.at(-1), 'Uncategorized');
});

test('mirrors CategoryMapperTest.kt', () => {
  assert.equal(mapCategory(['love', 'romance'], 'irrelevant'), 'Love');
  assert.equal(mapCategory(['hope'], 'irrelevant'), 'Optimism');
  assert.equal(mapCategory(['mortality'], 'x'), 'Death');
  assert.equal(mapCategory(['zzz-nonsense'], 'abcd efgh ijkl'), 'Uncategorized');
  assert.equal(mapCategory([], 'a piece of true wisdom'), 'Wisdom');
});
