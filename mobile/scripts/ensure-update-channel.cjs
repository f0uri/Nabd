const { spawnSync } = require('node:child_process');

// Inspect successful responses before creating anything. Authentication/network errors
// must abort publication, never be mistaken for a missing channel.
function ensureProductionChannel(run) {
  const limit = 25; // eas-cli 27.2.0 channel:list maximum page size.
  for (let offset = 0; ; offset += limit) {
    const page = JSON.parse(run(['channel:list', '--json', '--non-interactive', '--limit', String(limit), '--offset', String(offset)])).currentPage;
    if (!Array.isArray(page)) throw new Error('Unexpected EAS channel list response.');
    if (page.some((channel) => channel.name === 'production')) return;
    if (page.length < limit) break;
  }
  // EAS creates/reuses a branch of the same name and links the new channel.
  // Do not use --branch: channel:create does not support it in this pinned CLI.
  run(['channel:create', 'production', '--non-interactive']);
}

function runEas(args) {
  const result = spawnSync('npx', ['--yes', 'eas-cli@27.2.0', ...args], {
    encoding: 'utf8', env: process.env, maxBuffer: 5 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.status !== 0) throw new Error('EAS channel setup failed; check the project configuration, token permissions and service availability.');
  return result.stdout;
}

if (require.main === module) {
  try { ensureProductionChannel(runEas); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { ensureProductionChannel };
