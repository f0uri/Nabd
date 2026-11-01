module.exports = {
  'expo-linear-gradient': { LinearGradient: 'LinearGradient' },
  'react-native-svg': {
    __esModule: true,
    default: 'Svg',
    ...Object.fromEntries(
      [
        'Circle',
        'ClipPath',
        'Defs',
        'Ellipse',
        'G',
        'Path',
        'RadialGradient',
        'Rect',
        'Stop',
      ].map((name) => [name, name]),
    ),
  },
};
