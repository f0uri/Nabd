// Read-only diagnostic. Never prints raw credentials, chat IDs, EAS responses,
// downloaded account JSON, native error messages or signed artifact URLs.
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
function command(executable, args, cwd = process.cwd()) {
  const r = spawnSync(executable, args, { cwd, env: process.env, encoding: 'utf8', timeout: 240000, maxBuffer: 20 * 1024 * 1024 });
  if (r.error || r.status !== 0) throw Error('COMMAND_FAILED');
  return r.stdout;
}
function emit(section, result) {
  // Only whitelisted fields supplied by this module reach annotations.
  const text = JSON.stringify({ section, ...result });
  console.log(`::notice title=Nabd deployment audit::${text.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')}`);
}
async function telegramCall(token, method, payload = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch(`https://api.telegram.org/bot${token}/${method}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: controller.signal, redirect: 'error' });
    const data = await response.json();
    if (!response.ok || !data.ok) throw Error('TELEGRAM_REJECTED');
    return data.result;
  } catch { throw Error('TELEGRAM_CHECK_FAILED'); }
  finally { clearTimeout(timer); }
}
async function inspectBot() {
  const e = process.env;
  const tokenKeys = ['AUDIT_NABD_TOKEN', 'AUDIT_LEGACY_TOKEN', 'AUDIT_PUBLIC_TOKEN'];
  const chatKeys = ['AUDIT_NABD_CHAT_VAR', 'AUDIT_NABD_CHAT_SECRET', 'AUDIT_LEGACY_CHAT_VAR', 'AUDIT_LEGACY_CHAT_SECRET', 'AUDIT_PUBLIC_CHAT_SECRET'];
  const mode = e.AUDIT_DATA_SOURCE || 'auto';
  emit('configuration', { mode: ['auto', 'graphql', 'mock', 'telegram'].includes(mode) ? mode : 'invalid', present: Object.fromEntries([...tokenKeys, ...chatKeys].map((key) => [key, !!e[key]])), currentWorkflowWouldIncludeBot: mode === 'telegram' && !!e.AUDIT_NABD_TOKEN && !!e.AUDIT_NABD_CHAT_VAR });
  const token = tokenKeys.map((key) => e[key]).find(Boolean);
  const chatId = chatKeys.map((key) => e[key]).find(Boolean);
  if (!token || !chatId) { emit('bot', { checked: false, reason: 'NO_READABLE_CONFIGURATION' }); return; }
  try {
    const bot = await telegramCall(token, 'getMe');
    const chat = await telegramCall(token, 'getChat', { chat_id: chatId });
    emit('bot', { checked: true, identityValid: !!bot?.is_bot && String(bot.id) === token.split(':')[0], chatMatches: String(chat?.id) === chatId, destinationPrivate: chat.type === 'private' || (!chat.username && !chat.active_usernames?.length), nabdSnapshotPinned: chat.pinned_message?.document?.file_name === 'nabd-vault.json' && chat.pinned_message?.caption === 'nabd-direct-telegram-v1', otherMessagePinned: !!chat.pinned_message && chat.pinned_message?.document?.file_name !== 'nabd-vault.json' });
  } catch { emit('bot', { checked: false, reason: 'BOT_OR_CHAT_CHECK_FAILED' }); }
}
async function inspectExpo() {
  const { resolveRuntimeVersionAsync } = require('expo-updates/utils/build/resolveRuntimeVersionAsync');
  const local = await resolveRuntimeVersionAsync(process.cwd(), 'android', {}, { workflowOverride: 'managed' });
  emit('local-runtime', { runtime: local.runtimeVersion, project: require('../app.config.js')().extra?.eas?.projectId });
  if (!process.env.EXPO_TOKEN) { emit('expo', { checked: false, reason: 'NO_EXPO_TOKEN' }); return; }
  const eas = (args) => JSON.parse(command('npx', ['--yes', 'eas-cli@27.2.0', ...args, '--json', '--non-interactive']));
  try {
    const view = eas(['channel:view', 'production', '--limit', '25']);
    const channel = view.currentPage;
    if (!channel || !Array.isArray(channel.updateBranches)) throw Error('BAD_RESPONSE');
    emit('channel', { checked: true, paused: !!channel.isPaused, branches: channel.updateBranches.map((b) => b.name).filter((name) => /^[\w./-]{1,100}$/.test(name)) });
    for (const branch of channel.updateBranches.slice(0, 3)) {
      const updates = eas(['update:list', '--branch', branch.name, '--platform', 'android', '--limit', '10']);
      emit('published-updates', { branch: /^[\w./-]{1,100}$/.test(branch.name) ? branch.name : 'other', updates: (updates.currentPage || []).slice(0, 10).map((u) => ({ id: u.group, runtime: u.runtimeVersion, createdAt: u.createdAt, compatibleWithLocal: u.runtimeVersion === local.runtimeVersion })) });
    }
  } catch { emit('expo', { checked: false, reason: 'EXPO_QUERY_FAILED' }); }
}
function inspectApk(runId) {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'nabd-apk-audit-'));
  try {
    command('gh', ['run', 'download', String(runId), '--repo', process.env.GITHUB_REPOSITORY, '--name', 'nabd-test-apk', '--dir', temp]);
    const python = `import zipfile,pathlib,json\np=next(pathlib.Path(${JSON.stringify(temp)}).rglob('*.apk'))\nwith zipfile.ZipFile(p) as z:\n r={'fingerprint':None,'embeddedRuntime':None,'project':None}\n for n in z.namelist():\n  if n=='assets/fingerprint':r['fingerprint']=z.read(n).decode().strip()\n  if n=='assets/app.manifest':\n   m=json.loads(z.read(n));r['embeddedRuntime']=m.get('runtimeVersion');r['project']=m.get('extra',{}).get('eas',{}).get('projectId') or m.get('extra',{}).get('expoClient',{}).get('extra',{}).get('eas',{}).get('projectId')\n print(json.dumps(r))`;
    const result = JSON.parse(command('python3', ['-c', python]));
    const hex = (v) => typeof v === 'string' && /^[a-f0-9]{32,64}$/.test(v) ? v : null;
    emit('actual-apk', { runId, checked: true, fingerprint: hex(result.fingerprint), embeddedRuntime: hex(result.embeddedRuntime), linkedProjectMatches: result.project ? result.project === require('../app.config.js')().extra?.eas?.projectId : null });
  } catch { emit('actual-apk', { runId, checked: false, reason: 'ARTIFACT_UNAVAILABLE_OR_UNREADABLE' }); }
  finally { fs.rmSync(temp, { recursive: true, force: true }); }
}
async function inspectBuildFingerprintLogs(runId) {
  try {
    const logs = command('gh', ['run', 'view', String(runId), '--repo', process.env.GITHUB_REPOSITORY, '--log']);
    let old;
    for (const line of logs.split('\n')) {
      const start = line.indexOf('{"sources":[');
      if (start < 0) continue;
      try { const parsed = JSON.parse(line.slice(start)); if (parsed.hash && parsed.sources) old = parsed; } catch {}
    }
    if (!old) throw Error('NO_FINGERPRINT');
    const fp = require('expo/fingerprint');
    const current = await fp.createFingerprintAsync(process.cwd(), { platforms: ['android'], silent: true });
    const changes = fp.diffFingerprints(old, current).filter((d) => (d.addedSource || d.removedSource || d.afterSource).hash !== null);
    const differences = changes.map((d) => {
      const source = d.addedSource || d.removedSource || d.afterSource;
      const entry = { op: d.op, source: source.id || source.filePath, reasons: source.reasons };
      if (d.op === 'changed' && source.type === 'contents') {
        try {
          const before = JSON.parse(d.beforeSource.contents), after = JSON.parse(d.afterSource.contents);
          const paths = [];
          function visit(a,b,prefix) {
            if (JSON.stringify(a) === JSON.stringify(b)) return;
            if (a && b && typeof a === 'object' && typeof b === 'object' && !Array.isArray(a) && !Array.isArray(b)) {
              for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) visit(a[k],b[k],prefix ? prefix+'.'+k : k);
            } else paths.push(prefix);
          }
          visit(before,after,''); entry.changedFields = paths;
        } catch {}
      }
      return entry;
    });
    emit('build-fingerprint-diff', { checked: true, runId, actualBuildRuntime: old.hash, currentRuntime: current.hash, differences });
  } catch { emit('build-fingerprint-diff', { checked: false, runId, reason: 'BUILD_SOURCE_LOG_UNAVAILABLE' }); }
}
async function inspectExpoEnvironment() {
  if (!process.env.EXPO_TOKEN) return;
  for (const scope of ['project', 'account']) {
    try {
      const text = command('npx', ['--yes', 'eas-cli@27.2.0', 'env:list', 'production', '--scope', scope, '--format', 'short']);
      const values = require('dotenv').parse(text);
      const names = Object.keys(values).filter((key) => /(?:TELEGRAM|BOT|CHAT|DATA_SOURCE)/.test(key));
      emit('expo-environment', { scope, checked: true, relevantNames: names, existingPublishUsesEnvironment: false });
    } catch { emit('expo-environment', { scope, checked: false, reason: 'ENV_METADATA_QUERY_FAILED' }); }
  }
}
async function inspectGradleDrift() {
  const fp = require('expo/fingerprint');
  const before = await fp.createFingerprintAsync(process.cwd(), { platforms: ['android'], silent: true, debug: true });
  let buildCompleted = false;
  try {
    process.env.EXPO_NO_GIT_STATUS = '1'; process.env.CI = '1';
    command('npx', ['expo', 'prebuild', '--platform', 'android', '--no-install', '--clean']);
    command('bash', ['gradlew', 'help', '--no-daemon', '--max-workers=2'], path.join(process.cwd(),'android'));
    buildCompleted = true;
  } catch {}
  const after = await fp.createFingerprintAsync(process.cwd(), { platforms: ['android'], silent: true, debug: true });
  const pluginPath = 'node_modules/expo-updates/expo-updates-gradle-plugin';
  const leaves = (item) => !item ? [] : item.children ? item.children.flatMap(leaves) : [item.path];
  const oldFiles = new Set(leaves(before.sources.find((s) => s.filePath === pluginPath)?.debugInfo));
  const newFiles = leaves(after.sources.find((s) => s.filePath === pluginPath)?.debugInfo).filter((file) => !oldFiles.has(file));
  emit('gradle-drift', { checked: true, buildCompleted, before: before.hash, after: after.hash, addedPluginFiles: newFiles });
}
async function main() {
  await inspectBot();
  await inspectExpo();
  await inspectExpoEnvironment();
  const runId = /^\d+$/.test(process.env.AUDIT_APK_RUN || '') ? Number(process.env.AUDIT_APK_RUN) : 41267667174;
  await inspectBuildFingerprintLogs(runId);
  if (process.env.AUDIT_TRACE_GRADLE === 'true') await inspectGradleDrift();
  inspectApk(runId);
  await inspectBuildFingerprintLogs(41291077546);
  inspectApk(41291077546); // The foundational OTA-enabled APK the user installed.
}
if (require.main === module) main().catch(() => { emit('audit', { checked: false, reason: 'UNEXPECTED_DIAGNOSTIC_FAILURE' }); process.exitCode = 1; });
module.exports = { inspectBot, telegramCall };
