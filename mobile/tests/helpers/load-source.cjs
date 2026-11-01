const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
// Node inspects real UI props; native rendering is verified by the APK job.
const nativeUiMocks = require('./native-ui-mocks.cjs');
module.exports = function loader({ env = {}, mocks = {}, globals = {} } = {}) {
  const cache = new Map();
  const context = vm.createContext({
    process: { env },
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    AbortController,
    Uint8Array,
    Uint32Array,
    Int32Array,
    ArrayBuffer,
    TextEncoder,
    Response,
    fetch,
    ...globals,
  });
  function load(file) {
    let filename = path.resolve(__dirname, '../../src', file);
    if (!fs.existsSync(filename))
      filename = ['.ts', '.tsx', '.js']
        .map((ext) => filename + ext)
        .find(fs.existsSync);
    if (!filename) throw Error(`Missing test import: ${file}`);
    if (cache.has(filename)) return cache.get(filename).exports;
    const module = { exports: {} };
    cache.set(filename, module);
    const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      fileName: filename,
      compilerOptions: {
        allowJs: true,
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2021,
        esModuleInterop: true,
        jsx: ts.JsxEmit.ReactJSX,
      },
    }).outputText;
    const requireLocal = (name) =>
      Object.hasOwn(mocks, name)
        ? mocks[name]
        : Object.hasOwn(nativeUiMocks, name)
          ? nativeUiMocks[name]
          : name.startsWith('.')
            ? load(path.resolve(path.dirname(filename), name))
            : require(name);
    vm.runInContext(`(function(require,module,exports){${code}\n})`, context, {
      filename,
    })(requireLocal, module, module.exports);
    return module.exports;
  }
  return load;
};
