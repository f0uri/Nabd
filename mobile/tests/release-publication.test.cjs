const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  publishUpdate,
  verifyPublication,
} = require('../scripts/publish-update.cjs');
const { verifyReleaseBundle } = require('../scripts/verify-release-bundle.cjs');
const runtime = 'a'.repeat(40),
  commit = 'b'.repeat(40);
const entry = {
  id: '019a0000-1234-7000-8000-123456789abc',
  platform: 'android',
  runtimeVersion: runtime,
  gitCommitHash: commit,
};
const markers =
  'nabd-glass-one-tap-v1 nabd-auto-connect-v2 nabd-icons-ready-v2 nabd-telegram-chat-v1 nabd-chat-v1: nabd-login-responsive-v1';
function bundleFixture(run) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nabd-release-'));
  const dist = path.join(root, 'dist');
  fs.mkdirSync(dist);
  const assets = ['Ionicons', 'MaterialCommunityIcons'].map((family) => {
    const source = require.resolve(
      `@expo/vector-icons/build/vendor/react-native-vector-icons/Fonts/${family}.ttf`,
    );
    fs.copyFileSync(source, path.join(dist, `${family}.ttf`));
    return { ext: 'ttf', path: `${family}.ttf` };
  });
  fs.writeFileSync(
    path.join(dist, 'metadata.json'),
    JSON.stringify({
      fileMetadata: { android: { bundle: 'index.hbc', assets } },
    }),
  );
  fs.writeFileSync(path.join(dist, 'index.hbc'), markers);
  return Promise.resolve()
    .then(() => run(root, dist))
    .finally(() => fs.rmSync(root, { recursive: true, force: true }));
}
test('publication metadata must attest the actual Android runtime, source and update ID', () => {
  assert.equal(
    verifyPublication(JSON.stringify([entry]), runtime, commit).updateId,
    entry.id,
  );
  for (const patch of [
    { runtimeVersion: 'c'.repeat(40) },
    { gitCommitHash: 'd'.repeat(40) },
    { platform: 'ios' },
    { isRollBackToEmbedded: true },
    { id: 'invalid' },
  ])
    assert.throws(
      () =>
        verifyPublication(
          JSON.stringify([{ ...entry, ...patch }]),
          runtime,
          commit,
        ),
      /does not match/,
    );
  assert.throws(() => verifyPublication('{}', runtime, commit), /metadata/);
  assert.throws(() => verifyPublication('not-json', runtime, commit));
});
test('export verification requires new UI markers and rejects both encodings of obsolete warnings', async () => {
  await bundleFixture(async (root, dist) => {
    const proof = verifyReleaseBundle(dist, true);
    assert.equal(proof.uiRevision, 'login-responsive-v1');
    assert.match(proof.bundleSha256, /^[a-f0-9]{64}$/);
    fs.writeFileSync(
      path.join(dist, 'index.hbc'),
      markers.replace('nabd-login-responsive-v1', ''),
    );
    assert.throws(() => verifyReleaseBundle(dist, true), /Responsive login/);
    fs.writeFileSync(path.join(dist, 'index.hbc'), 'stale bundle');
    assert.throws(() => verifyReleaseBundle(dist, true), /absent/);
    fs.writeFileSync(
      path.join(dist, 'index.hbc'),
      'nabd-glass-one-tap-v1 nabd-icons-ready-v2',
    );
    assert.throws(() => verifyReleaseBundle(dist, true), /startup/);
    for (const encoding of ['utf8', 'utf16le']) {
      fs.writeFileSync(
        path.join(dist, 'index.hbc'),
        Buffer.concat([
          Buffer.from(markers),
          Buffer.from('فهمت المخاطر وباغي نكمل التجربة', encoding),
        ]),
      );
      assert.throws(() => verifyReleaseBundle(dist, true), /Obsolete/);
    }
    fs.writeFileSync(
      path.join(dist, 'metadata.json'),
      JSON.stringify({
        fileMetadata: { android: { bundle: '../outside.hbc' } },
      }),
    );
    assert.throws(() => verifyReleaseBundle(dist), /Invalid bundle path/);
  });
});
test('publication exports and verifies first, then uploads exactly that bundle without rebuilding', async () => {
  await bundleFixture(async (root) => {
    const order = [];
    let reported;
    const proof = await publishUpdate({
      root,
      expectedRuntime: runtime,
      commit,
      exportBundle: () => order.push('export'),
      verifyBundle: () => {
        order.push('verify');
        return { uiRevision: 'login-responsive-v1' };
      },
      report: (p) => {
        reported = p;
      },
      run: (args) => {
        order.push('publish');
        assert.ok(args.includes('--skip-bundler'));
        assert.ok(args.includes('--json'));
        assert.ok(args.includes('production'));
        return JSON.stringify([entry]);
      },
    });
    assert.deepEqual(order, ['export', 'verify', 'publish']);
    assert.equal(proof, reported);
    assert.equal(proof.runtime, runtime);
    let uploads = 0;
    await assert.rejects(
      publishUpdate({
        root,
        expectedRuntime: runtime,
        commit,
        exportBundle: () => {},
        verifyBundle: () => {
          throw Error('old UI');
        },
        report: () => {},
        run: () => {
          uploads++;
        },
      }),
      /old UI/,
    );
    assert.equal(uploads, 0);
  });
});

test('release verification rejects a missing or stale shipped icon font', async () => {
  await bundleFixture(async (root, dist) => {
    assert.equal(verifyReleaseBundle(dist, true).iconFontsVerified, true);
    fs.writeFileSync(
      path.join(dist, 'MaterialCommunityIcons.ttf'),
      'synthetic wrong font',
    );
    assert.throws(() => verifyReleaseBundle(dist, true), /absent or stale/);
  });
});
