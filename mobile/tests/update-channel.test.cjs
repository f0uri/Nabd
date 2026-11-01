const { test } = require('node:test');
const assert = require('node:assert/strict');
const { ensureProductionChannel } = require('../scripts/ensure-update-channel.cjs');

test('existing production channels are left untouched', () => {
  const calls = [];
  ensureProductionChannel((args) => { calls.push(args); return JSON.stringify({ currentPage: [{ name: 'production' }] }); });
  assert.equal(calls.length, 1);
});
test('a missing production channel is created using supported EAS flags', () => {
  const calls = [];
  ensureProductionChannel((args) => { calls.push(args); return JSON.stringify({ currentPage: [] }); });
  assert.deepEqual(calls[1], ['channel:create', 'production', '--non-interactive']);
});
test('all channel pages are searched before deciding the channel is absent', () => {
  const calls = [];
  ensureProductionChannel((args) => {
    calls.push(args);
    return JSON.stringify({ currentPage: calls.length === 1 ? Array.from({ length: 25 }, (_, i) => ({ name: `channel-${i}` })) : [{ name: 'production' }] });
  });
  assert.equal(calls.length, 2);
  assert.equal(calls[1].at(-1), '25');
});
test('service/authentication errors do not trigger creation', () => {
  let calls = 0;
  assert.throws(() => ensureProductionChannel(() => { calls++; throw new Error('Access denied'); }), /Access denied/);
  assert.equal(calls, 1);
});
test('malformed responses abort setup', () => {
  assert.throws(() => ensureProductionChannel(() => '{}'), /Unexpected EAS/);
});
test('channel creation errors are not ignored', () => {
  assert.throws(() => ensureProductionChannel((args) => {
    if (args[0] === 'channel:create') throw new Error('Creation denied');
    return JSON.stringify({ currentPage: [] });
  }), /Creation denied/);
});
