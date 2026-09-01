import fs from 'node:fs';
import { buildSeed } from './quote_seed_rules.mjs';

const [, , inputPath, outputPath, curatedPath] = process.argv;
if (!inputPath || !outputPath || !curatedPath) {
  console.error('Usage: node tools/rebuild_quotes_seed.mjs <input.json|jsonl> <output.json> <curated.json>');
  process.exit(2);
}

const input = fs.readFileSync(inputPath, 'utf8').trim();
const sourceRecords = input.startsWith('[')
  ? JSON.parse(input)
  : input.split(/\r?\n/).filter(line => line.trim()).flatMap(line => {
      try { return [JSON.parse(line)]; } catch { return []; }
    });
const curatedRecords = JSON.parse(fs.readFileSync(curatedPath, 'utf8'));
const output = buildSeed([...sourceRecords, ...curatedRecords]);

fs.writeFileSync(outputPath, JSON.stringify(output, null, 1) + '\n');
console.error(`Wrote ${output.length} unique English quotes to ${outputPath}`);
