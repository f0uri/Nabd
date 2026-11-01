// كيتحقق أن كل استعلامات التطبيق (mobile/src/api/client.ts) صحيحة على مخطط الخادم
import { readFileSync } from 'node:fs';
import { buildClientSchema, getIntrospectionQuery, parse, validate } from 'graphql';

const SRC = process.argv[2] ?? '/home/user/social-blue/mobile/src/api/client.ts';
const GQL = 'http://localhost:4000/graphql';

const res = await fetch(GQL, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ query: getIntrospectionQuery() }),
});
const schema = buildClientSchema((await res.json()).data);

const src = readFileSync(SRC, 'utf8');
const blocks = new Map();
const re = /const\s+([A-Z0-9_]+)\s*=\s*(?:\/\*\s*GraphQL\s*\*\/\s*)?`([\s\S]*?)`;/g;
let m;
while ((m = re.exec(src))) blocks.set(m[1], m[2]);

const interpolate = (text, depth = 0) =>
  depth > 5 ? text : text.replace(/\$\{([A-Z0-9_]+)\}/g, (_, name) => (blocks.has(name) ? interpolate(blocks.get(name), depth + 1) : `__MISSING_${name}__`));

let ok = 0;
const failures = [];
for (const [name, body] of blocks) {
  const doc = interpolate(body);
  if (!/\b(query|mutation|subscription)\b/.test(doc)) continue;
  try {
    const errors = validate(schema, parse(doc));
    if (errors.length) failures.push(`❌ ${name}: ${errors.map((e) => e.message).join(' · ')}`);
    else ok += 1;
  } catch (error) {
    failures.push(`❌ ${name}: ${error.message}`);
  }
}

console.log(`📄 ${ok} وثيقة صحيحة من ${blocks.size} ثابت`);
if (failures.length) {
  console.log(failures.join('\n'));
  process.exit(1);
}
console.log('✅ كل استعلامات التطبيق مطابقة للمخطط');
