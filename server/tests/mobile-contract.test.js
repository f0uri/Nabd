import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse, validate, printSchema, buildASTSchema, visit } from 'graphql';
import { schema } from '../src/schema.js';

const source = readFileSync(new URL('../../mobile/src/api/client.ts', import.meta.url), 'utf8');
const constants = new Map([...source.matchAll(/const\s+([A-Z0-9_]+)\s*=\s*(?:\/\*\s*GraphQL\s*\*\/\s*)?`([\s\S]*?)`;/g)].map((match) => [match[1], match[2]]));
const expand = (text, depth = 0) => depth > 5 ? text : text.replace(/\$\{([A-Z0-9_]+)\}/g, (_, key) => expand(constants.get(key) ?? `MISSING_${key}`, depth + 1));

test('actual mobile GraphQL documents match the hosted server schema', () => {
  let count = 0;
  for (const [name, value] of constants) {
    const query = expand(value).replaceAll('__NABD_USERNAME__', 'username');
    if (!/\b(query|mutation|subscription)\b/.test(query)) continue;
    assert.deepEqual(validate(schema, parse(query)).map((error) => error.message), [], name);
    count++;
  }
  assert.ok(count > 20, 'Must validate actual operation documents, not only fragments');
});
test('legacy capability selection keeps old-server requests valid', () => {
  const legacy = buildASTSchema(visit(parse(printSchema(schema)), {
    FieldDefinition(node) { if (node.name.value === 'username') return null; },
    InputValueDefinition(node) { if (node.name.value === 'username') return null; },
  }));
  for (const [name, value] of constants) {
    if (name === 'UPDATE_USERNAME_MUTATION') continue; // Explicitly gated by the capability check.
    const query = expand(value).replaceAll('__NABD_USERNAME__', '');
    if (!/\b(query|mutation|subscription)\b/.test(query)) continue;
    assert.deepEqual(validate(legacy, parse(query)).map((error) => error.message), [], name);
  }
});
