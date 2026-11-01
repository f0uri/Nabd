const loader = require('./load-source.cjs');

// Inspect actual component output without native bridges; this is not a visual
// device test. Effects/state are simulated only for disclosure/consent behavior.
module.exports = function renderUi(file, { mocks = {}, initialState = {} } = {}) {
  let cursor = 0, effectCursor = 0, pending = [];
  const state = new Map(Object.entries(initialState).map(([key, value]) => [Number(key), value]));
  const dependencies = new Map();
  const native = {
    StyleSheet: { create: (styles) => styles },
    Platform: { OS: 'android', select: (values) => values.android ?? values.default },
    ...Object.fromEntries(['ActivityIndicator','Modal','Pressable','ScrollView','Text','View','Image'].map((key) => [key, key])),
  };
  const theme = loader({mocks: {'react-native': native}})('theme/index.ts');
  const component = loader({ mocks: {
    react: {
      useState: (initial) => {
        const key = cursor++;
        if (!state.has(key)) state.set(key, typeof initial === 'function' ? initial() : initial);
        return [state.get(key), (value) => state.set(key, typeof value === 'function' ? value(state.get(key)) : value)];
      },
      useEffect: (effect, deps) => {
        const key = effectCursor++, before = dependencies.get(key);
        if (!before || deps.some((value, index) => value !== before[index])) pending.push(effect);
        dependencies.set(key, deps);
      },
    },
    'react-native': native,
    'react-native-safe-area-context': {SafeAreaView:'SafeAreaView'},
    '@expo/vector-icons': {Ionicons:'Icon'},
    '../theme': theme,
    '../../theme': theme,
    ...mocks,
  } })(file).default;
  return (props = {}) => {
    cursor = 0; effectCursor = 0; pending = [];
    const result = component(props);
    for (const effect of pending) effect();
    return result;
  };
};
module.exports.nodes = function nodes(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(module.exports.nodes);
  return [node, ...module.exports.nodes(node.props?.children)];
};
module.exports.text = function text(node) {
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(module.exports.text).join(' ');
  return node?.props ? module.exports.text(node.props.children) : '';
};
